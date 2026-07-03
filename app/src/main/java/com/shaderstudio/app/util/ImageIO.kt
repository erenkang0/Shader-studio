package com.shaderstudio.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.shaderstudio.app.shaders.LayerCompositor
import com.shaderstudio.app.shaders.LayerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Effects render at the photo's own resolution. 16384 px covers the maximum
 * GPU texture size of modern Android 14+ devices (a 4K photo renders at 4K,
 * an 8K photo at 8K); larger sources are gently downscaled, and decode
 * retries at smaller caps if memory runs out.
 */
private const val MAX_DIMENSION = 16384

/** Chosen export container. */
enum class ExportFormat { JPEG, PNG, GIF, MP4 }

/** Target longest-edge in pixels; ORIGINAL keeps the photo's own size. */
enum class ExportResolution(val maxEdge: Int) {
    ORIGINAL(0),
    UHD_4K(3840),
    QHD_2K(2560),
    FHD_1080(1920),
}

/** Compression strength for lossy formats. */
enum class ExportQuality { HIGH, MAX }

data class ExportOptions(
    val format: ExportFormat = ExportFormat.JPEG,
    val resolution: ExportResolution = ExportResolution.ORIGINAL,
    val quality: ExportQuality = ExportQuality.HIGH,
)

suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    for (cap in intArrayOf(MAX_DIMENSION, 8192, 4096, 2048)) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return@withContext ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val w = info.size.width
                val h = info.size.height
                val largest = max(w, h)
                if (largest > cap) {
                    val scale = cap.toFloat() / largest
                    decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
                }
            }
        } catch (e: OutOfMemoryError) {
            // retry with the next smaller cap
        } catch (e: Exception) {
            return@withContext null
        }
    }
    null
}

/** Longest-edge target size for [res] applied to a [w]x[h] source (never upscales). */
internal fun targetSize(w: Int, h: Int, res: ExportResolution): Pair<Int, Int> {
    val cap = res.maxEdge
    val largest = max(w, h)
    if (cap == 0 || largest <= cap) return w to h
    val scale = cap.toFloat() / largest
    return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
}

private fun scaledSource(src: Bitmap, w: Int, h: Int): Bitmap =
    if (src.width == w && src.height == h) src
    else Bitmap.createScaledBitmap(src, w, h, true)

/**
 * Renders a layer stack over a source photo through one generated AGSL program.
 * Built once, then [render] only updates the time uniform — cheap enough to
 * drive GIF/video frame sequences. Falls back to a software raster pass if the
 * GPU pipeline is unavailable. The same generated program drives the live
 * preview, so exports match the preview pixel-for-pixel.
 */
class FrameRenderer(
    src: Bitmap,
    layers: List<LayerSpec>,
    val width: Int,
    val height: Int,
) : AutoCloseable {

    private val source: Bitmap = scaledSource(src, width, height)
    private val ownsSource: Boolean = source !== src
    private val active = layers.filter { it.opacity > 0f }
    private val shader = RuntimeShader(LayerCompositor.generateSource(active.map { it.effect }))
    private val paint = Paint().apply { this.shader = this@FrameRenderer.shader }

    private val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2, usage)
    private val renderer = HardwareRenderer()
    private val node = RenderNode("shaderExport")
    private var gpuReady = false

    val isPassThrough: Boolean = active.isEmpty()

    init {
        shader.setInputShader("uImage", BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        shader.setFloatUniform("uResolution", width.toFloat(), height.toFloat())
        active.forEachIndexed { i, layer ->
            layer.effect.params.forEachIndexed { j, p ->
                shader.setFloatUniform("uL${i}P${j + 1}", layer.params.getOrElse(j) { p.default })
            }
            shader.setFloatUniform(
                "uL${i}Center",
                layer.centerX.coerceIn(0f, 1f),
                layer.centerY.coerceIn(0f, 1f),
            )
            shader.setIntUniform("uL${i}Mode", layer.blend.ordinal)
            shader.setFloatUniform("uL${i}Opacity", layer.opacity.coerceIn(0f, 1f))
            shader.setIntUniform("uL${i}Mask", layer.maskType.ordinal)
            shader.setFloatUniform("uL${i}MaskPos", layer.maskX.coerceIn(0f, 1f), layer.maskY.coerceIn(0f, 1f))
            shader.setFloatUniform("uL${i}MaskSize", layer.maskSize.coerceIn(0.02f, 1.5f))
            shader.setFloatUniform("uL${i}MaskAngle", layer.maskAngle)
            shader.setFloatUniform("uL${i}MaskFeather", layer.maskFeather.coerceIn(0f, 1f))
            shader.setFloatUniform("uL${i}MaskInvert", if (layer.maskInvert) 1f else 0f)
        }
        try {
            node.setPosition(0, 0, width, height)
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            gpuReady = true
        } catch (t: Throwable) {
            gpuReady = false
        }
    }

    /** Renders one frame at [time] seconds. Caller owns the returned bitmap. */
    fun render(time: Float): Bitmap {
        if (isPassThrough) return source.copy(Bitmap.Config.ARGB_8888, false)
        shader.setFloatUniform("uTime", time)
        if (gpuReady) {
            try {
                return renderGpu()
            } catch (t: Throwable) {
                gpuReady = false
            }
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return out
    }

    private fun renderGpu(): Bitmap {
        val canvas = node.beginRecording(width, height)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        node.endRecording()
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        val image = reader.acquireNextImage() ?: error("ImageReader produced no frame")
        image.use {
            val buffer = it.hardwareBuffer ?: error("No hardware buffer")
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, null)
                ?: error("Could not wrap hardware buffer")
            val result = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
            buffer.close()
            return result
        }
    }

    override fun close() {
        try {
            renderer.destroy()
        } catch (_: Throwable) {
        }
        reader.close()
        if (ownsSource) source.recycle()
    }
}

/**
 * Renders the layer stack over [src] at the requested export resolution.
 * A 4K photo with ORIGINAL resolution yields a 4K result.
 */
suspend fun applyLayerStackToBitmap(
    src: Bitmap,
    layers: List<LayerSpec>,
    time: Float,
    resolution: ExportResolution = ExportResolution.ORIGINAL,
): Bitmap = withContext(Dispatchers.Default) {
    val (w, h) = targetSize(src.width, src.height, resolution)
    FrameRenderer(src, layers, w, h).use { it.render(time) }
}

/** Saves a still image (JPEG or PNG) to the gallery under Pictures/Shader Studio. */
suspend fun exportStill(
    context: Context,
    bitmap: Bitmap,
    options: ExportOptions,
): Boolean = withContext(Dispatchers.IO) {
    val png = options.format == ExportFormat.PNG
    val ext = if (png) "png" else "jpg"
    val mime = if (png) "image/png" else "image/jpeg"
    val name = "ShaderStudio_${System.currentTimeMillis()}.$ext"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Shader Studio")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext false
    try {
        val q = if (options.quality == ExportQuality.MAX) 100 else 92
        val ok = resolver.openOutputStream(uri)?.use { stream ->
            if (png) bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            else bitmap.compress(Bitmap.CompressFormat.JPEG, q, stream)
        } ?: false
        if (!ok) {
            resolver.delete(uri, null, null)
            return@withContext false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

/** Procedural demo image so the editor can be tried without picking a photo. */
fun demoBitmap(): Bitmap {
    val w = 1440
    val h = 1920
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val bg = Paint().apply {
        shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF101632.toInt(), 0xFF3A1D6E.toInt(), 0xFFB84A18.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

    fun orb(cx: Float, cy: Float, radius: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(color, color and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, p)
    }
    orb(w * 0.30f, h * 0.28f, w * 0.42f, 0xFF19C2FF.toInt())
    orb(w * 0.78f, h * 0.52f, w * 0.50f, 0xFFFF8A3D.toInt())
    orb(w * 0.42f, h * 0.80f, w * 0.46f, 0xFFB36BFF.toInt())

    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = 0xCCFFFFFF.toInt()
    }
    canvas.drawCircle(w * 0.62f, h * 0.38f, w * 0.20f, ring)
    ring.strokeWidth = 6f
    ring.color = 0x66FFFFFF
    canvas.drawCircle(w * 0.38f, h * 0.66f, w * 0.28f, ring)

    return bmp
}
