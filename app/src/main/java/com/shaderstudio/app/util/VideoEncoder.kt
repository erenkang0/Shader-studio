package com.shaderstudio.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.provider.MediaStore
import com.shaderstudio.app.shaders.LayerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Renders an MP4 (H.264) clip of the layer stack with MediaCodec + MediaMuxer
 * and publishes it to Movies/Shader Studio. Frames come from [FrameRenderer]
 * so the video matches the live preview.
 */
object VideoExport {

    private const val DURATION_SECONDS = 6
    private const val FPS = 30
    private const val I_FRAME_INTERVAL = 1

    /** Longest edge for the encoded video, kept H.264-friendly (even dims). */
    private fun videoEdge(res: ExportResolution): Int = when (res) {
        ExportResolution.ORIGINAL, ExportResolution.QHD_2K -> 2560
        ExportResolution.UHD_4K -> 3840
        ExportResolution.FHD_1080 -> 1920
    }

    private fun bitrate(res: ExportResolution, quality: ExportQuality): Int {
        val base = when (res) {
            ExportResolution.UHD_4K -> 40_000_000
            ExportResolution.ORIGINAL, ExportResolution.QHD_2K -> 18_000_000
            ExportResolution.FHD_1080 -> 10_000_000
        }
        return if (quality == ExportQuality.MAX) (base * 1.6f).toInt() else base
    }

    suspend fun exportVideo(
        context: Context,
        src: Bitmap,
        layers: List<LayerSpec>,
        options: ExportOptions,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.Default) {
        val edge = videoEdge(options.resolution)
        val largest = max(src.width, src.height)
        val scale = if (largest > edge) edge.toFloat() / largest else 1f
        // H.264 requires even dimensions.
        val w = max(2, ((src.width * scale).toInt()) and 1.inv())
        val h = max(2, ((src.height * scale).toInt()) and 1.inv())

        val totalFrames = DURATION_SECONDS * FPS
        val tmp = File(context.cacheDir, "shaderstudio_${System.currentTimeMillis()}.mp4")

        val ok = try {
            encode(src, layers, w, h, totalFrames, tmp, options, onProgress)
        } catch (t: Throwable) {
            tmp.delete()
            false
        }
        if (!ok) {
            tmp.delete()
            return@withContext false
        }
        val published = withContext(Dispatchers.IO) { publish(context, tmp) }
        tmp.delete()
        published
    }

    private fun encode(
        src: Bitmap,
        layers: List<LayerSpec>,
        w: Int,
        h: Int,
        totalFrames: Int,
        outFile: File,
        options: ExportOptions,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate(options.resolution, options.quality))
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val renderer = FrameRenderer(src, layers, w, h)

        try {
            var frameIndex = 0
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        if (frameIndex >= totalFrames) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, ptsUs(frameIndex),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val t = DURATION_SECONDS.toFloat() * frameIndex / totalFrames
                            val frame = renderer.render(t)
                            val image = codec.getInputImage(inIndex)
                            if (image != null) {
                                fillYuv420(image, frame, w, h)
                            }
                            if (frame !== src) frame.recycle()
                            codec.queueInputBuffer(inIndex, 0, w * h * 3 / 2, ptsUs(frameIndex), 0)
                            frameIndex++
                            onProgress(frameIndex.toFloat() / totalFrames)
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIndex >= 0) {
                    val encoded = codec.getOutputBuffer(outIndex)
                    if (encoded != null && bufferInfo.size > 0 && muxerStarted &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
            return true
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            codec.release()
            try { if (muxerStarted) muxer.stop() } catch (_: Throwable) {}
            try { muxer.release() } catch (_: Throwable) {}
            renderer.close()
        }
    }

    private fun ptsUs(frame: Int): Long = frame.toLong() * 1_000_000L / FPS

    /** Converts an ARGB frame into the codec's YUV420 (BT.601) input image. */
    private fun fillYuv420(image: android.media.Image, frame: Bitmap, w: Int, h: Int) {
        val argb = IntArray(w * h)
        frame.getPixels(argb, 0, w, 0, 0, w, h)
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixStride = vPlane.pixelStride

        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = argb[y * w + x]
                val r = (c ushr 16) and 0xff
                val g = (c ushr 8) and 0xff
                val b = c and 0xff
                val yy = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(y * yRowStride + x * yPixStride, yy.coerceIn(0, 255).toByte())
                if (y and 1 == 0 && x and 1 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvRow = y / 2
                    val uvCol = x / 2
                    uBuf.put(uvRow * uRowStride + uvCol * uPixStride, u.coerceIn(0, 255).toByte())
                    vBuf.put(uvRow * vRowStride + uvCol * vPixStride, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }

    private fun publish(context: Context, file: File): Boolean {
        val name = "ShaderStudio_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Shader Studio")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return false
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
