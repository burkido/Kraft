package com.burkido.kraft.effects.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.concurrent.Volatile
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** `COVER_EDGE_INSET_FRAC`: trimmed off every edge so a baked-in border row never sits flush. */
private const val CoverInset = 0.006f

/** The longest side of the CPU copy the per-cell colours are averaged from. */
private const val SmallSide = 256

/**
 * An image in the reveal pool: the bitmap the card draws, plus a small CPU copy (≤ 256 px, box
 * filtered) built on first use off the main thread, from which the mosaic's per-cell colours
 * and the regenerate palette are averaged.
 */
internal class RevealImage(val bitmap: ImageBitmap) {
    private class Small(val px: IntArray, val w: Int, val h: Int)

    // Built by whichever worker needs it first; cards sharing a pool may race to build it, and
    // publishing one immutable holder keeps a reader from seeing half of it.
    @Volatile
    private var small: Small? = null

    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height

    /** `computeCoverSourceRect` for a [targetW]×[targetH] box: (sx, sy, sw, sh) in image px. */
    fun coverRect(targetW: Float, targetH: Float, out: FloatArray = FloatArray(4)): FloatArray {
        val targetAspect = targetW / max(1f, targetH)
        val imgAspect = width.toFloat() / max(1, height)
        var sx = 0f
        var sy = 0f
        var sw = width.toFloat()
        var sh = height.toFloat()
        if (imgAspect > targetAspect) {
            sw = height * targetAspect
            sx = (width - sw) / 2
        } else {
            sh = width / targetAspect
            sy = (height - sh) / 2
        }
        val inset = min(sw, sh) * CoverInset
        if (sw - 2 * inset > 1 && sh - 2 * inset > 1) {
            sx += inset; sy += inset; sw -= 2 * inset; sh -= 2 * inset
        }
        out[0] = sx; out[1] = sy; out[2] = sw; out[3] = sh
        return out
    }

    private fun ensureSmall(): Small {
        small?.let { return it }
        val w = width
        val h = height
        val full = IntArray(w * h)
        bitmap.readPixels(full)
        val k = max(1f, max(w, h) / SmallSide.toFloat())
        val sw = max(1, (w / k).roundToInt())
        val sh = max(1, (h / k).roundToInt())
        val out = IntArray(sw * sh)
        for (y in 0 until sh) {
            val y0 = (y * h / sh)
            val y1 = max(y0 + 1, (y + 1) * h / sh)
            for (x in 0 until sw) {
                val x0 = (x * w / sw)
                val x1 = max(x0 + 1, (x + 1) * w / sw)
                var r = 0; var g = 0; var b = 0; var n = 0
                for (yy in y0 until y1) {
                    var i = yy * w + x0
                    for (xx in x0 until x1) {
                        val p = full[i++]
                        r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                        n++
                    }
                }
                out[y * sw + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return Small(out, sw, sh).also { small = it }
    }

    /**
     * The cover crop for a [targetW]×[targetH] box downsampled to [gx]×[gy] cells (the web's
     * high-quality `drawImage` into the grid canvas), as 0..1 rgb into [out], rows top-down.
     */
    fun cellColors(targetW: Float, targetH: Float, gx: Int, gy: Int, out: FloatArray) {
        val copy = ensureSmall()
        val px = copy.px
        val smallW = copy.w
        val smallH = copy.h
        val k = smallW / width.toFloat()
        val rect = coverRect(targetW, targetH)
        val sx = rect[0] * k
        val sy = rect[1] * k
        val sw = rect[2] * k
        val sh = rect[3] * k
        for (cy in 0 until gy) {
            val y0 = sy + cy * sh / gy
            val y1 = sy + (cy + 1) * sh / gy
            val ry0 = floor(y0).toInt().coerceIn(0, smallH - 1)
            val ry1 = max(ry0 + 1, min(smallH, kotlin.math.ceil(y1).toInt()))
            for (cx in 0 until gx) {
                val x0 = sx + cx * sw / gx
                val x1 = sx + (cx + 1) * sw / gx
                val rx0 = floor(x0).toInt().coerceIn(0, smallW - 1)
                val rx1 = max(rx0 + 1, min(smallW, kotlin.math.ceil(x1).toInt()))
                // Area-weighted box filter over the covered small pixels.
                var r = 0f; var g = 0f; var b = 0f; var wsum = 0f
                for (yy in ry0 until ry1) {
                    val wy = min(yy + 1f, y1) - max(yy.toFloat(), y0)
                    if (wy <= 0f) continue
                    for (xx in rx0 until rx1) {
                        val wx = min(xx + 1f, x1) - max(xx.toFloat(), x0)
                        if (wx <= 0f) continue
                        val p = px[yy * smallW + xx]
                        val w = wx * wy
                        r += ((p shr 16) and 0xFF) * w; g += ((p shr 8) and 0xFF) * w; b += (p and 0xFF) * w
                        wsum += w
                    }
                }
                val o = (cy * gx + cx) * 3
                if (wsum > 0f) {
                    out[o] = r / wsum / 255f; out[o + 1] = g / wsum / 255f; out[o + 2] = b / wsum / 255f
                } else {
                    out[o] = 0f; out[o + 1] = 0f; out[o + 2] = 0f
                }
            }
        }
    }

    /**
     * `samplePaletteFromCanvas`: the image (as the card shows it) averaged to 24 × 24, sorted by
     * luminance, and mapped onto [presetColors] by luminance rank — so the effect keeps its
     * contrast structure but wears the image's colours. Also returns the average colour.
     */
    fun samplePalette(targetW: Float, targetH: Float, presetColors: List<Color>): SampledPalette {
        val grid = 24
        val cells = FloatArray(grid * grid * 3)
        cellColors(targetW, targetH, grid, grid, cells)
        val n = grid * grid
        val lum = FloatArray(n)
        var ar = 0f; var ag = 0f; var ab = 0f
        for (i in 0 until n) {
            val r = cells[i * 3] * 255; val g = cells[i * 3 + 1] * 255; val b = cells[i * 3 + 2] * 255
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
            ar += r; ag += g; ab += b
        }
        val order = (0 until n).sortedBy { lum[it] }
        val ranked = presetColors.indices.sortedBy { presetColors[it].let { c -> 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue } }
        val colors = arrayOfNulls<Color>(presetColors.size)
        for (rank in ranked.indices) {
            val q = 0.05f + 0.9f * rank / max(1, ranked.size - 1)
            val p = order[min(n - 1, (q * (n - 1)).roundToInt())]
            colors[ranked[rank]] = rgb255(cells[p * 3] * 255, cells[p * 3 + 1] * 255, cells[p * 3 + 2] * 255)
        }
        return SampledPalette(colors.map { it!! }, rgb255(ar / n, ag / n, ab / n))
    }
}

/** An image-derived recolour: one colour per palette slot, plus the average colour for the card. */
internal class SampledPalette(val colors: List<Color>, val cardBg: Color)

/** Rounds to whole 8-bit channels, as the web's hex round-trip does. */
private fun rgb255(r: Float, g: Float, b: Float): Color =
    Color(r.roundToInt().coerceIn(0, 255), g.roundToInt().coerceIn(0, 255), b.roundToInt().coerceIn(0, 255))

/** Pool entries by bitmap identity, so every card sharing a pool shares one CPU copy. */
internal object RevealImages {
    private val cache = HashMap<ImageBitmap, RevealImage>()

    fun of(bitmap: ImageBitmap): RevealImage {
        cache[bitmap]?.let { return it }
        if (cache.size >= 24) cache.clear()
        return RevealImage(bitmap).also { cache[bitmap] = it }
    }
}
