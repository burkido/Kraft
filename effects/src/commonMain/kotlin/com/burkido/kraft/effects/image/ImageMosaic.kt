package com.burkido.kraft.effects.image

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The pixel-mode mosaic, rasterised the way the web renders it: the shader's `main()` at every
 * pixel of a buffer at the GL DPR (≤ 1.25), which the card then shows nearest-neighbour upscaled.
 *
 * The effect is sampled once per cell (at the cell centre, as `sampleUV` does), so the expensive
 * noise runs per cell; only the gaps, edge fade and vignette vary within a cell, and those are
 * cheap per-pixel arithmetic.
 *
 * Output matches what the page shows: RGBA premultiplied, top row first, with the colour carrying
 * the alpha twice — the GL canvas blends `SRC_ALPHA` into a buffer the browser then reads as
 * non-premultiplied (`premultipliedAlpha: false`).
 */
internal class ImageMosaic(private val shader: ImageShader) {
    private var cells = 0
    private var cellR = FloatArray(0)
    private var cellG = FloatArray(0)
    private var cellB = FloatArray(0)
    private var cellGain = FloatArray(0)
    private var cellLift = FloatArray(0)
    private var cellGap = FloatArray(0)
    private var cellContrast = FloatArray(0)
    private val rgb = FloatArray(3)

    // Per-column / per-row lookups.
    private var colCell = IntArray(0)
    private var colFrac = FloatArray(0)
    private var colEdge = FloatArray(0)
    private var rowCell = IntArray(0)
    private var rowFrac = FloatArray(0)
    private var rowEdge = FloatArray(0)

    var gridX = 0
        private set
    var gridY = 0
        private set

    /** Renders [iw]×[ih] physical pixels (at GL DPR [dpr]) at shader time [t] into [out]. */
    fun render(iw: Int, ih: Int, dpr: Float, t: Float, out: ByteArray) {
        val s = shader
        val gx = s.gridCount(iw, dpr)
        val gy = s.gridCount(ih, dpr)
        gridX = gx
        gridY = gy
        val aspect = iw.toFloat() / ih.toFloat()
        val cssW = iw / max(dpr, 0.0001f)
        val cssH = ih / max(dpr, 0.0001f)
        ensureCells(gx * gy)

        // Per cell: the blurred effect colour, the highlight terms and the gap width.
        val bgLum = s.cardBg[0] * 0.299f + s.cardBg[1] * 0.587f + s.cardBg[2] * 0.114f
        val hlOn = s.highlight > 0.01f
        for (cy in 0 until gy) {
            val uvY = (cy + 0.5f) / gy
            for (cx in 0 until gx) {
                val uvX = (cx + 0.5f) / gx
                val i = cy * gx + cx
                s.blurred(uvX, uvY, aspect, t, gx, gy, rgb)
                cellR[i] = rgb[0]; cellG[i] = rgb[1]; cellB[i] = rgb[2]
                val hl = s.highlightAt(uvX, uvY, aspect, t)
                val boost = 1f + smooth(0.2f, 0.8f, hl) * s.hlScale * 1.2f
                cellGap[i] = s.gap * 0.35f / boost
                if (hlOn) {
                    val h = hl * s.highlight
                    cellGain[i] = 1f + h * 2.5f
                    cellLift[i] = h * h * 0.3f
                } else {
                    cellGain[i] = 1f
                    cellLift[i] = 0f
                }
                val lum = rgb[0] * 0.299f + rgb[1] * 0.587f + rgb[2] * 0.114f
                cellContrast[i] = smooth(0f, 0.33f, abs(lum - bgLum))
            }
        }

        // Per column (x) and per GL row (y from the bottom): cell index, position in the cell and
        // the CSS-px distance to the nearer edge on that axis.
        ensureLines(iw, ih)
        for (x in 0 until iw) {
            val uv = (x + 0.5f) / iw
            val g = uv * gx
            colCell[x] = min(gx - 1, floor(g).toInt())
            colFrac[x] = g - floor(g)
            val css = uv * cssW
            colEdge[x] = min(css, cssW - css)
        }
        for (y in 0 until ih) {
            val uv = (y + 0.5f) / ih
            val g = uv * gy
            rowCell[y] = min(gy - 1, floor(g).toInt())
            rowFrac[y] = g - floor(g)
            val css = uv * cssH
            rowEdge[y] = min(css, cssH - css)
        }

        val vigAmount = s.vignette * s.vigOpacity
        val vigRange = 40f * (1f + s.vignette * 3f)
        val invVig2 = 1f / (vigRange * vigRange)
        val fadeOn = s.edgeFade > 0.5f && s.fadeStr > 0.005f
        val colorAlphaMean = (s.alphas[0] + s.alphas[1] + s.alphas[2] + s.alphas[3] + s.alphas[4]) / 5f
        val proximity = colorAlphaMean < 0.999f
        val fill = s.fillOpacity
        val dot = s.dotOpacity
        val opacity = s.shaderOpacity

        for (glY in 0 until ih) {
            val row = ih - 1 - glY
            val cyBase = rowCell[glY] * gx
            val fy = rowFrac[glY]
            val ey = rowEdge[glY]
            var o = row * iw * 4
            for (x in 0 until iw) {
                val i = cyBase + colCell[x]
                val d = min(colEdge[x], ey)
                // Vignette on the colour.
                val vig = smooth(0f, 1f, d * d * invVig2)
                val vigMul = 1f + (vig - 1f) * vigAmount
                var r = cellR[i] * vigMul
                var g = cellG[i] * vigMul
                var b = cellB[i] * vigMul
                val colorAlpha = if (proximity) proximityAlpha(r, g, b) else colorAlphaMean
                val gain = cellGain[i]
                val lift = cellLift[i]
                r = r * gain + lift
                g = g * gain + lift
                b = b * gain + lift
                // The gap mask (hard steps at pixel centres) and the edge fade.
                var mask = 1f
                val gapW = cellGap[i]
                if (gapW > 0.003f) {
                    val fx = colFrac[x]
                    if (fx < gapW || 1f - fx < gapW || fy < gapW || 1f - fy < gapW) mask = 0f
                }
                if (fadeOn) mask *= 1f + (smooth(0f, s.edgeFade, d) - 1f) * s.fadeStr
                val a = (colorAlpha * (fill + (dot - fill) * mask) * cellContrast[i] * opacity).coerceIn(0f, 1f)
                // The framebuffer clamps the colour before blending multiplies it by alpha.
                val aa = a * a
                out[o] = channel(r.coerceIn(0f, 1f) * aa)
                out[o + 1] = channel(g.coerceIn(0f, 1f) * aa)
                out[o + 2] = channel(b.coerceIn(0f, 1f) * aa)
                out[o + 3] = channel(a)
                o += 4
            }
        }
    }

    private fun proximityAlpha(r: Float, g: Float, b: Float): Float {
        val c = shader.colors
        val al = shader.alphas
        var total = 0.0001f
        var weighted = 0f
        for (k in 0 until 5) {
            val dr = r - c[k * 3]
            val dg = g - c[k * 3 + 1]
            val db = b - c[k * 3 + 2]
            val p = exp(-8f * (dr * dr + dg * dg + db * db))
            total += p
            weighted += p * al[k]
        }
        return weighted / total
    }

    private fun ensureCells(n: Int) {
        if (cells == n) return
        cells = n
        cellR = FloatArray(n); cellG = FloatArray(n); cellB = FloatArray(n)
        cellGain = FloatArray(n); cellLift = FloatArray(n); cellGap = FloatArray(n); cellContrast = FloatArray(n)
    }

    private fun ensureLines(iw: Int, ih: Int) {
        if (colCell.size != iw) { colCell = IntArray(iw); colFrac = FloatArray(iw); colEdge = FloatArray(iw) }
        if (rowCell.size != ih) { rowCell = IntArray(ih); rowFrac = FloatArray(ih); rowEdge = FloatArray(ih) }
    }
}

/** A 0..1 value (clamped, as the 8-bit framebuffer does) as an unsigned byte. */
internal fun channel(v: Float): Byte = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()

/**
 * `sampleShaderField`: the raw (non-mosaic) shader colour the reveal masks read — the blurred
 * effect with the raw path's 1.3 gamma and the vignette — on a [size]×[size] lattice over the
 * card, top row first, quantised to 8 bits like the web's `getImageData`. Pixel presets sample
 * with `u_dotMode = 0`, so no cells, gaps or highlight.
 */
internal fun sampleRawField(
    shader: ImageShader,
    iw: Int,
    ih: Int,
    dpr: Float,
    t: Float,
    size: Int,
    out: FloatArray,
) {
    val aspect = iw.toFloat() / ih.toFloat()
    val cssW = iw / max(dpr, 0.0001f)
    val cssH = ih / max(dpr, 0.0001f)
    val gx = shader.gridCount(iw, dpr)
    val gy = shader.gridCount(ih, dpr)
    val vigAmount = shader.vignette * shader.vigOpacity
    val vigRange = 40f * (1f + shader.vignette * 3f)
    val rgb = FloatArray(3)
    for (r in 0 until size) {
        val uvY = 1f - (r + 0.5f) / size
        val cssY = uvY * cssH
        val ey = min(cssY, cssH - cssY)
        for (c in 0 until size) {
            val uvX = (c + 0.5f) / size
            val cssX = uvX * cssW
            val d = min(min(cssX, cssW - cssX), ey)
            shader.blurred(uvX, uvY, aspect, t, gx, gy, rgb)
            val vig = smooth(0f, 1f, (d * d) / (vigRange * vigRange))
            val vigMul = 1f + (vig - 1f) * vigAmount
            val o = (r * size + c) * 3
            for (ch in 0 until 3) {
                val v = max(rgb[ch], 0f).pow(1.3f) * vigMul
                out[o + ch] = (v.coerceIn(0f, 1f) * 255f).roundToInt() / 255f
            }
        }
    }
}
