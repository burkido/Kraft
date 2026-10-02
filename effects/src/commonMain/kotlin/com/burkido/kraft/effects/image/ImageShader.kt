package com.burkido.kraft.effects.image

import androidx.compose.ui.graphics.Color
import com.burkido.kraft.effects.core.parseCssColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The shader's `REF_DIM`: the card edge (CSS px) at which a preset's cell size is authored. */
internal const val RefDim = 320f

/**
 * The img-fx fragment shader (`engine/shaders.ts`) evaluated on the CPU, in 32-bit floats like
 * the GPU: simplex noise, fbm, the domain warp, the Gaussian palette and soft blend, and the three
 * effect branches the bundled presets use — 11 Nebula, 22 Chromium Flow, 25 Gradient Sweep.
 *
 * One instance per renderer; it keeps its uniforms and scratch space, so it must not be shared
 * between threads.
 */
internal class ImageShader {
    // Uniforms (see `uploadInstanceUniforms`).
    var effect = 22
    var intensity = 1f
    var scale = 1f
    var dirX = 1f
    var dirY = 0f
    var softness = 0.76f
    var distortion = 0.3f
    var complexity = 0.2f
    var shape = 0.52f
    var flicker = 0f
    var vignette = 0f
    var vigOpacity = 0f
    var blur = 1f
    var highlight = 0f
    var shaderOpacity = 1f
    var cellSize = 0.22f
    var gap = 0.14f
    var dotOpacity = 0.68f
    var hlScale = 0.8f
    var fillOpacity = 0.44f
    var edgeFade = 24f
    var fadeStr = 1f
    var sweepEase = 0
    val colors = FloatArray(7 * 3)
    val alphas = FloatArray(7) { 1f }
    val cardBg = FloatArray(3)

    private var octaves = 2
    private val warpA = FloatArray(2)
    private val warpB = FloatArray(2)
    private val tap = FloatArray(3)

    /**
     * Loads [mode] with the playground's knobs: [strength] above 1 boosts intensity and highlight,
     * [pixelScale] rescales the cell grid, [colorsOverride] re-tints palette slots and
     * [cardBgOverride] is the surface the palette's card-coloured slots follow.
     */
    fun configure(mode: ImageMode, strength: Float, pixelScale: Float, colorsOverride: List<Color?>?, cardBgOverride: Color?) {
        effect = mode.effect
        val boost = 1 + max(0f, strength - 1) * 2.2f
        intensity = mode.intensity * boost
        scale = mode.scale
        val dir = (mode.direction * PI / 180).toFloat()
        dirX = cos(dir)
        dirY = sin(dir)
        softness = mode.softness
        distortion = mode.distortion
        complexity = mode.complexity
        shape = mode.shape
        flicker = mode.flicker
        vignette = mode.vignette
        vigOpacity = mode.vigOpacity
        blur = mode.blur
        highlight = mode.highlight * boost
        shaderOpacity = mode.shaderOpacity
        cellSize = effectiveCellSize(mode.cellSize, pixelScale)
        gap = mode.gap
        dotOpacity = mode.dotOpacity
        hlScale = mode.hlScale
        fillOpacity = mode.fillOpacity
        edgeFade = mode.edgeFade
        fadeStr = mode.fadeStr
        sweepEase = mode.sweepEase
        octaves = (2f + complexity * 2f).toInt()
        val bg = cardBgOverride ?: parseCssColor(mode.cardBg)
        cardBg[0] = bg.red; cardBg[1] = bg.green; cardBg[2] = bg.blue
        for (i in 0 until 7) {
            val c = colorsOverride?.getOrNull(i) ?: effectivePaletteColor(mode, i, cardBgOverride)
            colors[i * 3] = c.red; colors[i * 3 + 1] = c.green; colors[i * 3 + 2] = c.blue
            alphas[i] = 1f
        }
    }

    /** `gridCounts(6 + cellSize·74)` along one axis of [px] physical pixels at [dpr]. */
    fun gridCount(px: Int, dpr: Float): Int {
        val cssRes = px / max(dpr, 0.0001f)
        return max(2f, floor((6f + cellSize * 74f) * cssRes / RefDim)).toInt()
    }

    // ── Noise ────────────────────────────────────────────────────────────────────────────

    private fun mod289(x: Float): Float = x - floor(x * (1f / 289f)) * 289f
    private fun permute(x: Float): Float = mod289((x * 34f + 1f) * x)

    /** Ashima's 2D simplex noise, as written in the shader. */
    fun snoise(vx: Float, vy: Float): Float {
        val cx = 0.211324865405187f
        val cy = 0.366025403784439f
        val cz = -0.577350269189626f
        val cw = 0.024390243902439f
        val s = (vx + vy) * cy
        var ix = floor(vx + s)
        var iy = floor(vy + s)
        val t = (ix + iy) * cx
        val x0x = vx - ix + t
        val x0y = vy - iy + t
        val i1x: Float
        val i1y: Float
        if (x0x > x0y) { i1x = 1f; i1y = 0f } else { i1x = 0f; i1y = 1f }
        val x12x = x0x + cx - i1x
        val x12y = x0y + cx - i1y
        val x12z = x0x + cz
        val x12w = x0y + cz
        ix = mod289(ix)
        iy = mod289(iy)
        val p0 = permute(permute(iy) + ix)
        val p1 = permute(permute(iy + i1y) + ix + i1x)
        val p2 = permute(permute(iy + 1f) + ix + 1f)
        var m0 = max(0.5f - (x0x * x0x + x0y * x0y), 0f)
        var m1 = max(0.5f - (x12x * x12x + x12y * x12y), 0f)
        var m2 = max(0.5f - (x12z * x12z + x12w * x12w), 0f)
        m0 *= m0; m0 *= m0
        m1 *= m1; m1 *= m1
        m2 *= m2; m2 *= m2
        val xa = 2f * fract(p0 * cw) - 1f
        val xb = 2f * fract(p1 * cw) - 1f
        val xc = 2f * fract(p2 * cw) - 1f
        val ha = abs(xa) - 0.5f
        val hb = abs(xb) - 0.5f
        val hc = abs(xc) - 0.5f
        val a0a = xa - floor(xa + 0.5f)
        val a0b = xb - floor(xb + 0.5f)
        val a0c = xc - floor(xc + 0.5f)
        m0 *= 1.79284291400159f - 0.85373472095314f * (a0a * a0a + ha * ha)
        m1 *= 1.79284291400159f - 0.85373472095314f * (a0b * a0b + hb * hb)
        m2 *= 1.79284291400159f - 0.85373472095314f * (a0c * a0c + hc * hc)
        val g0 = a0a * x0x + ha * x0y
        val g1 = a0b * x12x + hb * x12y
        val g2 = a0c * x12z + hc * x12w
        return 130f * (m0 * g0 + m1 * g1 + m2 * g2)
    }

    /** `nfbm`: [octaves] octaves of fbm (`2 + complexity·2`, truncated). */
    fun nfbm(px: Float, py: Float): Float {
        var x = px
        var y = py
        var value = 0f
        var amp = 0.5f
        for (i in 0 until min(octaves, 4)) {
            value += amp * snoise(x, y)
            x *= 2f; y *= 2f
            amp *= 0.5f
        }
        return value
    }

    /** `warp(p, t)` into [out]. */
    private fun warp(px: Float, py: Float, t: Float, out: FloatArray) {
        val str = distortion * 2f
        out[0] = nfbm(px + t * 0.1f, py) * str
        out[1] = nfbm(px + 5f, py + t * 0.12f + 5f) * str
    }

    // ── Colour ───────────────────────────────────────────────────────────────────────────

    private fun palette(t0: Float, out: FloatArray) {
        var t = t0.coerceIn(0f, 1f)
        t = t * t * (3f - 2f * t)
        val k = 64f
        val w1 = alphas[0] * exp(-k * t * t)
        val w2 = alphas[1] * exp(-k * (t - 0.25f) * (t - 0.25f))
        val w3 = alphas[2] * exp(-k * (t - 0.5f) * (t - 0.5f))
        val w4 = alphas[3] * exp(-k * (t - 0.75f) * (t - 0.75f))
        val w5 = alphas[4] * exp(-k * (t - 1f) * (t - 1f))
        val total = w1 + w2 + w3 + w4 + w5 + 0.0001f
        for (ch in 0 until 3) {
            out[ch] = (colors[ch] * w1 + colors[3 + ch] * w2 + colors[6 + ch] * w3 + colors[9 + ch] * w4 + colors[12 + ch] * w5) / total
        }
    }

    private fun softBlend(a0: Float, b0: Float, c0: Float, out: FloatArray) {
        var a = a0.coerceIn(0f, 1f); a *= a
        var b = b0.coerceIn(0f, 1f); b *= b
        var c = c0.coerceIn(0f, 1f); c *= c
        var d = (a * 0.7f + c * 0.3f).coerceIn(0f, 1f); d *= d
        var e = (b * 0.5f + c * 0.5f).coerceIn(0f, 1f); e *= e
        a *= alphas[0]; b *= alphas[1]; c *= alphas[2]; d *= alphas[3]; e *= alphas[4]
        val total = a + b + c + d + e
        val floorW = max(0.001f - total, 0f)
        for (ch in 0 until 3) {
            val fallback = (colors[ch] + colors[3 + ch] + colors[6 + ch] + colors[9 + ch] + colors[12 + ch]) * 0.2f
            out[ch] = (colors[ch] * a + colors[3 + ch] * b + colors[6 + ch] * c + colors[9 + ch] * d + colors[12 + ch] * e + fallback * floorW) /
                (total + floorW)
        }
    }

    private fun sweepEase(x: Float): Float = when (sweepEase) {
        1 -> x * x * (3f - 2f * x)
        2 -> { val p = 1f - x; 1f - p * p * p }
        3 -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) * 0.5f
        4 -> 1f - 2f.pow(-10f * x) * (1f - x)
        else -> x
    }

    // ── Effects ──────────────────────────────────────────────────────────────────────────

    /**
     * `computeEffect(uv, …)` into [out] (rgb). [uvY] runs bottom-up like `gl_FragCoord`;
     * [gridX]/[gridY] are the mosaic's cell counts (the sweep's flicker is per cell).
     */
    fun effect(uvX: Float, uvY: Float, aspect: Float, t: Float, gridX: Int, gridY: Int, out: FloatArray) {
        var px = (uvX - 0.5f) * scale * aspect
        var py = (uvY - 0.5f) * scale
        px += dirX * t * 0.15f
        py += dirY * t * 0.15f
        val dist = distortion
        val cpx = complexity
        val shp = shape
        when (effect) {
            11 -> {
                val qx = nfbm(px * 0.5f + t * 0.05f, py * 0.5f)
                val qy = nfbm(px * 0.5f, py * 0.5f + t * 0.07f)
                val k = 1f + dist * 1.5f
                val rx = nfbm(px * 0.6f + qx * k + 1.7f + t * 0.03f, py * 0.6f + qy * k + 9.2f + t * 0.03f)
                val ry = nfbm(px * 0.6f + qx * k + 8.3f + t * 0.04f, py * 0.6f + qy * k + 2.8f + t * 0.04f)
                val f = nfbm(px + rx * 1.5f, py + ry * 1.5f)
                val f2 = nfbm(px * 0.7f + rx + 3f, py * 0.7f + ry + 7f)
                val f3 = nfbm(px * 0.4f - t * 0.02f, py * 0.4f - t * 0.02f)
                softBlend((f * 0.5f + 0.5f) * intensity, (f2 * 0.5f + 0.5f) * intensity, (f3 * 0.5f + 0.5f) * intensity, out)
            }
            22 -> {
                warp(px * 0.7f, py * 0.7f, t * 0.5f, warpA)
                val wx = warpA[0]
                val wy = warpA[1]
                warp(px * 0.4f + wx * 0.3f, py * 0.4f + wy * 0.3f, t * 0.3f, warpB)
                val k = 0.4f + dist * 0.6f
                val wpx = px + wx * k
                val wpy = py + wy * k
                val f1 = 1.4f + cpx * 1.6f
                val n1 = snoise(wpx * f1 + t * 0.14f, wpy * f1 + t * 0.14f)
                val f2 = 2f + cpx * 2f
                val n2 = snoise(
                    (wpx + warpB[0] * dist * 0.4f) * f2 + 3f - t * 0.1f,
                    (wpy + warpB[1] * dist * 0.4f) * f2 + 7f - t * 0.1f,
                )
                val ridge1 = (1f - abs(n1)).pow(5f + shp * 12f)
                val ridge2 = (1f - abs(n2)).pow(4f + shp * 10f)
                val base = (n1 + n2) * 0.25f + 0.5f
                softBlend(
                    (base * 0.6f + ridge1 * 1.2f) * intensity,
                    ((1f - base) * 0.6f + ridge2 * 1f) * intensity,
                    (ridge1 * 0.8f + ridge2 * 0.6f) * intensity,
                    out,
                )
            }
            25 -> {
                val d = (uvX + (1f - uvY)) * 0.5f
                val w = 0.9f / max(scale, 0.25f)
                val cyc = t * 0.08f
                val pA = -w + (1f + 2 * w) * sweepEase(fract(cyc))
                val pB = -w + (1f + 2 * w) * sweepEase(fract(cyc + 0.5f))
                val band = max((1f - abs(d - pA) / w).coerceIn(0f, 1f), (1f - abs(d - pB) / w).coerceIn(0f, 1f))
                var v = band * intensity
                val cellX = floor(uvX * gridX)
                val cellY = floor(uvY * gridY)
                val clk = t * 1.6f
                val step0 = modF(floor(clk), 1024f)
                val step1 = modF(step0 + 1f, 1024f)
                val fz = smooth(0f, 1f, fract(clk))
                val seed = cellX * 127.1f + cellY * 311.7f
                val r1 = fract(sin(seed + step0 * 17.23f) * 43758.5453f)
                val r2 = fract(sin(seed + step1 * 17.23f) * 43758.5453f)
                val rnd = r1 + (r2 - r1) * fz
                v += (rnd - 0.5f) * flicker * 0.9f * (0.15f + band * 0.85f)
                palette(v.coerceIn(0f, 1f), out)
            }
            else -> { out[0] = 0f; out[1] = 0f; out[2] = 0f }
        }
    }

    /** `main()`'s 5-tap blur around [uvX], [uvY] (or one tap when `u_blur` is 0). */
    fun blurred(uvX: Float, uvY: Float, aspect: Float, t: Float, gridX: Int, gridY: Int, out: FloatArray) {
        if (blur < 0.01f) {
            effect(uvX, uvY, aspect, t, gridX, gridY, out)
            return
        }
        val r = blur * 0.02f
        effect(uvX, uvY, aspect, t, gridX, gridY, out)
        var cr = out[0] * 0.4f
        var cg = out[1] * 0.4f
        var cb = out[2] * 0.4f
        for (k in 0 until 4) {
            val ox = when (k) { 0 -> r; 1 -> -r; else -> 0f }
            val oy = when (k) { 2 -> r; 3 -> -r; else -> 0f }
            effect(uvX + ox, uvY + oy, aspect, t, gridX, gridY, tap)
            cr += tap[0] * 0.15f
            cg += tap[1] * 0.15f
            cb += tap[2] * 0.15f
        }
        out[0] = cr; out[1] = cg; out[2] = cb
    }

    /** The per-cell highlight wave (`hlFactor`) at a cell centre, squared as in the shader. */
    fun highlightAt(centerX: Float, centerY: Float, aspect: Float, t: Float): Float {
        if (highlight <= 0.01f && hlScale <= 0.01f) return 0f
        val cpX = (centerX - 0.5f) * scale * aspect
        val cpY = (centerY - 0.5f) * scale
        var lw = sin(cpX * 3f + t * 1.5f) * 0.5f + 0.5f
        lw *= sin(cpY * 2.5f - t * 1.1f) * 0.5f + 0.5f
        lw += (snoise(cpX * 2f + t * 0.6f, cpY * 2f + t * 0.6f) * 0.5f + 0.5f) * 0.3f
        val hl = lw.coerceIn(0f, 1f)
        return hl * hl
    }
}

internal fun fract(x: Float): Float = x - floor(x)

internal fun modF(x: Float, y: Float): Float = x - y * floor(x / y)

/** GLSL `smoothstep`. */
internal fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** `effectiveCellSize`: the preset cell size re-derived for a [pixelScale] multiplier. */
internal fun effectiveCellSize(cellSize: Float, pixelScale: Float): Float {
    if (pixelScale <= 0f || pixelScale == 1f) return cellSize
    val baseCount = (6f + cellSize * 74f) / pixelScale
    return (baseCount - 6f) / 74f
}

/**
 * `effectivePaletteColor`: palette slot [index], except that slots authored at the preset's own
 * card colour follow [cardBgOverride] when one is set.
 */
internal fun effectivePaletteColor(mode: ImageMode, index: Int, cardBgOverride: Color?): Color {
    val c = parseCssColor(mode.colors[index])
    if (cardBgOverride == null) return c
    val bg = parseCssColor(mode.cardBg)
    fun chan(v: Float) = (v * 255).roundToInt()
    return if (chan(c.red) == chan(bg.red) && chan(c.green) == chan(bg.green) && chan(c.blue) == chan(bg.blue)) {
        cardBgOverride.copy(alpha = 1f)
    } else {
        c
    }
}
