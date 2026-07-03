package com.shaderstudio.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.HardwareRenderer
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.shaderstudio.app.shaders.ShaderEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_DIMENSION = 2560

suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width
            val h = info.size.height
            val largest = max(w, h)
            if (largest > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / largest
                decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Renders [src] through the effect's RuntimeShader at full resolution.
 * Prefers a GPU pass (HardwareRenderer + ImageReader); falls back to the
 * software raster pipeline if the GPU path is unavailable.
 */
suspend fun applyShaderToBitmap(
    src: Bitmap,
    effect: ShaderEffect,
    params: List<Float>,
    time: Float,
): Bitmap = withContext(Dispatchers.Default) {
    val agsl = effect.agsl ?: return@withContext src
    val w = src.width
    val h = src.height
    val shader = RuntimeShader(agsl)
    shader.setInputShader("uImage", BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    shader.setFloatUniform("uResolution", w.toFloat(), h.toFloat())
    shader.setFloatUniform("uTime", time)
    effect.params.forEachIndexed { i, p ->
        shader.setFloatUniform("uParam${i + 1}", params.getOrElse(i) { p.default })
    }
    val paint = Paint().apply { this.shader = shader }

    try {
        renderOnGpu(w, h, paint)
    } catch (t: Throwable) {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        out
    }
}

private fun renderOnGpu(w: Int, h: Int, paint: Paint): Bitmap {
    val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
    ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1, usage).use { reader ->
        val renderer = HardwareRenderer()
        try {
            val node = RenderNode("shaderExport")
            node.setPosition(0, 0, w, h)
            val canvas = node.beginRecording(w, h)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            node.endRecording()
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage() ?: error("ImageReader produced no frame")
            image.use {
                val buffer = it.hardwareBuffer ?: error("No hardware buffer")
                val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, null)
                    ?: error("Could not wrap hardware buffer")
                val result = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                buffer.close()
                result
            }
        } finally {
            renderer.destroy()
        }
    }
}

suspend fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    val name = "ShaderStudio_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Shader Studio")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext false
    try {
        val ok = resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
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
