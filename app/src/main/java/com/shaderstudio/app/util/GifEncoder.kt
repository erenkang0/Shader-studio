package com.shaderstudio.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import com.shaderstudio.app.shaders.LayerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Renders an animated GIF89a of the layer stack and writes it to the gallery.
 * Colour quantization uses NeuQuant and pixel data is LZW-compressed — both are
 * the long-established public-domain algorithms by Anthony Dekker and Kevin
 * Weiner, ported to Kotlin.
 */
object GifExport {

    private const val FRAMES = 48
    private const val FPS = 16
    private const val LOOP_SECONDS = FRAMES.toFloat() / FPS

    /** Longest edge for the GIF frames — small enough for a shareable file. */
    private fun gifEdge(res: ExportResolution): Int = when (res) {
        ExportResolution.ORIGINAL, ExportResolution.FHD_1080 -> 720
        ExportResolution.QHD_2K -> 900
        ExportResolution.UHD_4K -> 1080
    }

    suspend fun exportGif(
        context: Context,
        src: Bitmap,
        layers: List<LayerSpec>,
        options: ExportOptions,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.Default) {
        val edge = gifEdge(options.resolution)
        val cap = ExportResolution.ORIGINAL.let {
            val largest = max(src.width, src.height)
            if (largest <= edge) src.width to src.height
            else {
                val scale = edge.toFloat() / largest
                max(1, (src.width * scale).toInt() and 1.inv()) to
                    max(1, (src.height * scale).toInt() and 1.inv())
            }
        }
        val (w, h) = cap
        val delayCentis = (100f / FPS).toInt()

        val baos = ByteArrayOutputStream(1 shl 20)
        val writer = GifWriter(baos)
        val renderer = FrameRenderer(src, layers, w, h)
        try {
            var started = false
            for (i in 0 until FRAMES) {
                val t = LOOP_SECONDS * i / FRAMES
                val frame = renderer.render(t)
                val pixels = IntArray(w * h)
                frame.getPixels(pixels, 0, w, 0, 0, w, h)
                if (frame !== src) frame.recycle()
                if (!started) {
                    writer.start(w, h)
                    started = true
                }
                writer.addFrame(pixels, w, h, delayCentis)
                onProgress((i + 1).toFloat() / FRAMES)
            }
            writer.finish()
        } finally {
            renderer.close()
        }

        val bytes = baos.toByteArray()
        withContext(Dispatchers.IO) { saveBytesToGallery(context, bytes) }
    }

    private fun saveBytesToGallery(context: Context, bytes: ByteArray): Boolean {
        val name = "ShaderStudio_${System.currentTimeMillis()}.gif"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Shader Studio")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}

/** Streams GIF89a blocks; one local palette per frame, looping forever. */
private class GifWriter(private val out: OutputStream) {
    private var wrote = false

    fun start(w: Int, h: Int) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        // Logical screen descriptor, no global colour table.
        writeShort(w); writeShort(h)
        out.write(0x70)  // colour resolution bits, no GCT
        out.write(0); out.write(0)
        // NETSCAPE2.0 loop extension (0 = forever).
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(1); writeShort(0); out.write(0)
        wrote = true
    }

    fun addFrame(argb: IntArray, w: Int, h: Int, delayCentis: Int) {
        val n = w * h
        val rgb = ByteArray(n * 3)
        var j = 0
        for (i in 0 until n) {
            val c = argb[i]
            rgb[j++] = (c ushr 16).toByte()
            rgb[j++] = (c ushr 8).toByte()
            rgb[j++] = c.toByte()
        }
        val nq = NeuQuant(rgb, rgb.size, 10)
        val palette = nq.process()  // BGR-ordered 256*3
        val indexed = ByteArray(n)
        var k = 0
        for (i in 0 until n) {
            val b = rgb[k++].toInt() and 0xff
            val g = rgb[k++].toInt() and 0xff
            val r = rgb[k++].toInt() and 0xff
            indexed[i] = nq.map(b, g, r).toByte()
        }

        // Graphic control extension (frame delay).
        out.write(0x21); out.write(0xF9); out.write(4)
        out.write(0); writeShort(delayCentis); out.write(0); out.write(0)
        // Image descriptor with local colour table.
        out.write(0x2C); writeShort(0); writeShort(0); writeShort(w); writeShort(h)
        out.write(0x87)  // local colour table, 256 entries
        // Local colour table: convert NeuQuant BGR -> RGB.
        var p = 0
        for (i in 0 until 256) {
            out.write(palette[p + 2].toInt() and 0xff)
            out.write(palette[p + 1].toInt() and 0xff)
            out.write(palette[p].toInt() and 0xff)
            p += 3
        }
        LzwEncoder(w, h, indexed, 8).encode(out)
    }

    fun finish() {
        if (wrote) out.write(0x3B)  // trailer
        out.flush()
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xff)
        out.write((v shr 8) and 0xff)
    }
}

/**
 * NeuQuant neural-net colour quantizer (Anthony Dekker, 1994), public domain.
 * Operates on a BGR byte buffer and produces a 256-colour palette + lookup.
 */
private class NeuQuant(private val pic: ByteArray, private val lengthCount: Int, sample: Int) {
    private val netsize = 256
    private val prime1 = 499
    private val prime2 = 491
    private val prime3 = 487
    private val prime4 = 503
    private val minpicturebytes = 3 * prime4
    private val maxnetpos = netsize - 1
    private val netbiasshift = 4
    private val ncycles = 100
    private val intbiasshift = 16
    private val intbias = 1 shl intbiasshift
    private val gammashift = 10
    private val betashift = 10
    private val beta = intbias shr betashift
    private val betagamma = intbias shl (gammashift - betashift)
    private val initrad = netsize shr 3
    private val radiusbiasshift = 6
    private val radiusbias = 1 shl radiusbiasshift
    private val initradius = initrad * radiusbias
    private val radiusdec = 30
    private val alphabiasshift = 10
    private val initalpha = 1 shl alphabiasshift
    private var alphadec = 0
    private val radbiasshift = 8
    private val radbias = 1 shl radbiasshift
    private val alpharadbshift = alphabiasshift + radbiasshift
    private val alpharadbias = 1 shl alpharadbshift

    private val samplefac = sample
    private val network = Array(netsize) { IntArray(4) }
    private val netindex = IntArray(256)
    private val bias = IntArray(netsize)
    private val freq = IntArray(netsize)
    private val radpower = IntArray(initrad)

    init {
        for (i in 0 until netsize) {
            val p = network[i]
            val v = (i shl (netbiasshift + 8)) / netsize
            p[0] = v; p[1] = v; p[2] = v
            freq[i] = intbias / netsize
            bias[i] = 0
        }
    }

    fun process(): ByteArray {
        learn()
        unbiasnet()
        inxbuild()
        return colorMap()
    }

    private fun colorMap(): ByteArray {
        val map = ByteArray(netsize * 3)
        val index = IntArray(netsize)
        for (i in 0 until netsize) index[network[i][3]] = i
        var k = 0
        for (i in 0 until netsize) {
            val j = index[i]
            map[k++] = network[j][0].toByte()
            map[k++] = network[j][1].toByte()
            map[k++] = network[j][2].toByte()
        }
        return map
    }

    private fun inxbuild() {
        var previouscol = 0
        var startpos = 0
        for (i in 0 until netsize) {
            val p = network[i]
            var smallpos = i
            var smallval = p[1]
            for (j in i + 1 until netsize) {
                val q = network[j]
                if (q[1] < smallval) { smallpos = j; smallval = q[1] }
            }
            val q = network[smallpos]
            if (i != smallpos) {
                var t = q[0]; q[0] = p[0]; p[0] = t
                t = q[1]; q[1] = p[1]; p[1] = t
                t = q[2]; q[2] = p[2]; p[2] = t
                t = q[3]; q[3] = p[3]; p[3] = t
            }
            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) shr 1
                for (j in previouscol + 1 until smallval) netindex[j] = i
                previouscol = smallval
                startpos = i
            }
        }
        netindex[previouscol] = (startpos + maxnetpos) shr 1
        for (j in previouscol + 1 until 256) netindex[j] = maxnetpos
    }

    fun map(b: Int, g: Int, r: Int): Int {
        var bestd = 1000
        var best = -1
        var i = netindex[g]
        var j = i - 1
        while (i < netsize || j >= 0) {
            if (i < netsize) {
                val p = network[i]
                var dist = p[1] - g
                if (dist >= bestd) i = netsize else {
                    i++
                    if (dist < 0) dist = -dist
                    var a = p[0] - b; if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - r; if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) { bestd = dist; best = p[3] }
                    }
                }
            }
            if (j >= 0) {
                val p = network[j]
                var dist = g - p[1]
                if (dist >= bestd) j = -1 else {
                    j--
                    if (dist < 0) dist = -dist
                    var a = p[0] - b; if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - r; if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) { bestd = dist; best = p[3] }
                    }
                }
            }
        }
        return best
    }

    private fun unbiasnet() {
        for (i in 0 until netsize) {
            network[i][0] = network[i][0] shr netbiasshift
            network[i][1] = network[i][1] shr netbiasshift
            network[i][2] = network[i][2] shr netbiasshift
            network[i][3] = i
        }
    }

    private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
        var lo = i - rad; if (lo < -1) lo = -1
        var hi = i + rad; if (hi > netsize) hi = netsize
        var j = i + 1
        var k = i - 1
        var m = 1
        while (j < hi || k > lo) {
            val a = radpower[m++]
            if (j < hi) {
                val p = network[j++]
                try {
                    p[0] -= (a * (p[0] - b)) / alpharadbias
                    p[1] -= (a * (p[1] - g)) / alpharadbias
                    p[2] -= (a * (p[2] - r)) / alpharadbias
                } catch (e: Exception) { }
            }
            if (k > lo) {
                val p = network[k--]
                try {
                    p[0] -= (a * (p[0] - b)) / alpharadbias
                    p[1] -= (a * (p[1] - g)) / alpharadbias
                    p[2] -= (a * (p[2] - r)) / alpharadbias
                } catch (e: Exception) { }
            }
        }
    }

    private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
        val p = network[i]
        p[0] -= (alpha * (p[0] - b)) / initalpha
        p[1] -= (alpha * (p[1] - g)) / initalpha
        p[2] -= (alpha * (p[2] - r)) / initalpha
    }

    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE
        var bestbiasd = bestd
        var bestpos = -1
        var bestbiaspos = bestpos
        for (i in 0 until netsize) {
            val n = network[i]
            var dist = n[0] - b; if (dist < 0) dist = -dist
            var a = n[1] - g; if (a < 0) a = -a
            dist += a
            a = n[2] - r; if (a < 0) a = -a
            dist += a
            if (dist < bestd) { bestd = dist; bestpos = i }
            val biasdist = dist - (bias[i] shr (intbiasshift - netbiasshift))
            if (biasdist < bestbiasd) { bestbiasd = biasdist; bestbiaspos = i }
            val betafreq = freq[i] shr betashift
            freq[i] -= betafreq
            bias[i] += betafreq shl gammashift
        }
        freq[bestpos] += beta
        bias[bestpos] -= betagamma
        return bestbiaspos
    }

    private fun learn() {
        if (lengthCount < minpicturebytes) alphadec = 1 else alphadec = 30 + ((samplefac - 1) / 3)
        val p = pic
        var pix = 0
        val lim = lengthCount
        val samplepixels = lengthCount / (3 * samplefac)
        var delta = samplepixels / ncycles
        if (delta == 0) delta = 1
        var alpha = initalpha
        var radius = initradius
        var rad = radius shr radiusbiasshift
        if (rad <= 1) rad = 0
        for (i in 0 until rad) radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad))

        val step: Int
        if (lengthCount < minpicturebytes) step = 3
        else if (lengthCount % prime1 != 0) step = 3 * prime1
        else if (lengthCount % prime2 != 0) step = 3 * prime2
        else if (lengthCount % prime3 != 0) step = 3 * prime3
        else step = 3 * prime4

        var i = 0
        while (i < samplepixels) {
            val b = (p[pix].toInt() and 0xff) shl netbiasshift
            val g = (p[pix + 1].toInt() and 0xff) shl netbiasshift
            val r = (p[pix + 2].toInt() and 0xff) shl netbiasshift
            val j = contest(b, g, r)
            altersingle(alpha, j, b, g, r)
            if (rad != 0) alterneigh(rad, j, b, g, r)
            pix += step
            if (pix >= lim) pix -= lengthCount
            i++
            if (delta != 0 && i % delta == 0) {
                alpha -= alpha / alphadec
                radius -= radius / radiusdec
                rad = radius shr radiusbiasshift
                if (rad <= 1) rad = 0
                for (k in 0 until rad) radpower[k] = alpha * (((rad * rad - k * k) * radbias) / (rad * rad))
            }
        }
    }
}

/**
 * LZW-GIF encoder based on GIFCOMPR (Spencer W. Thomas et al.) via Kevin
 * Weiner's public-domain AnimatedGifEncoder. Emits variable-length codes as
 * GIF sub-blocks.
 */
private class LzwEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixels: ByteArray,
    private val initCodeSize: Int,
) {
    private val EOF = -1
    private val BITS = 12
    private val HSIZE = 5003
    private var nBits = 0
    private var maxbits = BITS
    private var maxcode = 0
    private val maxmaxcode = 1 shl BITS
    private val htab = IntArray(HSIZE)
    private val codetab = IntArray(HSIZE)
    private var freeEnt = 0
    private var clearFlg = false
    private var gInitBits = 0
    private var clearCode = 0
    private var eofCode = 0
    private var curAccum = 0
    private var curBits = 0
    private val masks = intArrayOf(
        0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F,
        0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF,
    )
    private var aCount = 0
    private val accum = ByteArray(256)
    private var remaining = 0
    private var curPixel = 0

    fun encode(os: OutputStream) {
        os.write(initCodeSize)
        remaining = imgW * imgH
        curPixel = 0
        compress(initCodeSize + 1, os)
        os.write(0)  // block terminator
    }

    private fun maxcodeFor(nBits: Int): Int = (1 shl nBits) - 1

    private fun nextPixel(): Int {
        if (remaining == 0) return EOF
        remaining--
        val pix = pixels[curPixel++]
        return pix.toInt() and 0xff
    }

    private fun compress(initBits: Int, os: OutputStream) {
        var fcode: Int
        var c: Int
        var ent: Int
        var disp: Int
        val hshift: Int
        gInitBits = initBits
        clearFlg = false
        nBits = gInitBits
        maxcode = maxcodeFor(nBits)
        clearCode = 1 shl (initBits - 1)
        eofCode = clearCode + 1
        freeEnt = clearCode + 2
        aCount = 0
        ent = nextPixel()

        var fc = HSIZE
        var sh = 0
        while (fc < 65536) { sh++; fc *= 2 }
        hshift = 8 - sh
        for (i in 0 until HSIZE) htab[i] = -1

        output(clearCode, os)

        outer@ while (true) {
            c = nextPixel()
            if (c == EOF) break
            fcode = (c shl maxbits) + ent
            val i = (c shl hshift) xor ent
            if (htab[i] == fcode) { ent = codetab[i]; continue }
            else if (htab[i] >= 0) {
                disp = HSIZE - i
                if (i == 0) disp = 1
                var idx = i
                do {
                    idx -= disp
                    if (idx < 0) idx += HSIZE
                    if (htab[idx] == fcode) { ent = codetab[idx]; continue@outer }
                } while (htab[idx] >= 0)
                output(ent, os)
                ent = c
                if (freeEnt < maxmaxcode) {
                    codetab[idx] = freeEnt++
                    htab[idx] = fcode
                } else clearBlock(os)
            } else {
                output(ent, os)
                ent = c
                if (freeEnt < maxmaxcode) {
                    codetab[i] = freeEnt++
                    htab[i] = fcode
                } else clearBlock(os)
            }
        }
        output(ent, os)
        output(eofCode, os)
    }

    private fun output(code: Int, os: OutputStream) {
        curAccum = curAccum and masks[curBits]
        if (curBits > 0) curAccum = curAccum or (code shl curBits) else curAccum = code
        curBits += nBits
        while (curBits >= 8) {
            charOut((curAccum and 0xff).toByte(), os)
            curAccum = curAccum ushr 8
            curBits -= 8
        }
        if (freeEnt > maxcode || clearFlg) {
            if (clearFlg) {
                nBits = gInitBits
                maxcode = maxcodeFor(nBits)
                clearFlg = false
            } else {
                nBits++
                maxcode = if (nBits == maxbits) maxmaxcode else maxcodeFor(nBits)
            }
        }
        if (code == eofCode) {
            while (curBits > 0) {
                charOut((curAccum and 0xff).toByte(), os)
                curAccum = curAccum ushr 8
                curBits -= 8
            }
            flushChar(os)
        }
    }

    private fun clearBlock(os: OutputStream) {
        for (i in 0 until HSIZE) htab[i] = -1
        freeEnt = clearCode + 2
        clearFlg = true
        output(clearCode, os)
    }

    private fun charOut(c: Byte, os: OutputStream) {
        accum[aCount++] = c
        if (aCount >= 254) flushChar(os)
    }

    private fun flushChar(os: OutputStream) {
        if (aCount > 0) {
            os.write(aCount)
            os.write(accum, 0, aCount)
            aCount = 0
        }
    }
}
