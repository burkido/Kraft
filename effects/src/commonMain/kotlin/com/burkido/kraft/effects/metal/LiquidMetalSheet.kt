/*
 * Derived from Paper Shaders' `liquidMetal` shader (https://github.com/paper-design/shaders),
 * Copyright (c) Paper Design, Inc., licensed under the Apache License, Version 2.0.
 * Modified: ported from GLSL to Kotlin and evaluated on the CPU. See NOTICE.
 */
package com.burkido.kraft.effects.metal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The liquid-metal material, rendered on the CPU exactly the way metal-fx v2 renders it on the
 * web: Paper Shaders' `liquidMetal` fragment stage (Apache-2.0, @paper-design/shaders 0.0.80) in
 * its `shape: none` full-fill mode, evaluated once per frame onto one shared square sheet that
 * every instance crops a window from. The web draws that sheet at 96 CSS px × DPR (capped at 2)
 * and refreshes it every 66 ms, so doing the same maths on the CPU reproduces its output rather
 * than approximating it.
 *
 * Everything that does not depend on time is precomputed per sheet; a frame only evaluates the
 * noise-driven terms. `fwidth()` is reproduced from 2×2-quad differences, as the GPU does.
 *
 * Output: RGBA_8888 premultiplied bytes, row-major, top row first.
 */
class LiquidMetalSheet(val size: Int, private val material: MetalMaterial) {
    private val n = size * size

    // Static per pixel (see precompute()).
    private val uvX = FloatArray(n)
    private val uvY = FloatArray(n)
    private val edge = FloatArray(n)
    private val opacity = FloatArray(n)
    private val diagBL = FloatArray(n)
    private val color2B = FloatArray(n)
    private val bump = FloatArray(n)
    private val dirBase = FloatArray(n)
    private val dirScale = FloatArray(n)
    private val dirAdd = FloatArray(n)
    private val w0 = FloatArray(n)
    private val w1 = FloatArray(n)
    private val w2 = FloatArray(n)
    private val dispR = FloatArray(n)
    private val dispB = FloatArray(n)
    private val dither = FloatArray(n)

    // Per frame.
    private val stripeR = FloatArray(n)
    private val stripeG = FloatArray(n)
    private val stripeB = FloatArray(n)
    private val w1Frame = FloatArray(n)

    init {
        precompute()
    }

    private fun precompute() {
        val m = material
        val cw = m.repetition * 2f
        val a = ((-m.angle + 70f) * PI / 180f).toFloat()
        val cosA = cos(a)
        val sinA = sin(a)
        // pixel_thickness = min(250 / box, .5): .5 for any sheet of 500 px or less.
        val thickness = min(250f / size, 0.5f)
        val contourMix = smoothstep(0f, 0.4f, m.contour)
        val edgeRaw = FloatArray(n)
        for (j in 0 until size) {
            for (i in 0 until size) {
                val p = j * size + i
                // v_responsiveUV at the pixel centre (y up), +.5 → borderUV.
                val bx = (i + 0.5f) / size
                val by = 1f - (j + 0.5f) / size
                val mx = smoothstep(0f, thickness, min(bx, 1f - bx)).pow(0.25f)
                val my = smoothstep(0f, thickness, min(by, 1f - by)).pow(0.25f)
                edgeRaw[p] = (1f - mx * my).coerceIn(0f, 1f)
                uvX[p] = bx
                uvY[p] = 1f - by
            }
        }
        // edge = mix(smoothstep(.9 − 2·fwidth(edge), .9, edge), edge, smoothstep(0, .4, contour)).
        val edgeMixed = FloatArray(n)
        val fwRaw = fwidth(edgeRaw)
        for (p in 0 until n) {
            val e = edgeRaw[p]
            edgeMixed[p] = mix(smoothstep(0.9f - 2f * fwRaw[p], 0.9f, e), e, contourMix)
        }
        val fwMixed = fwidth(edgeMixed)
        for (p in 0 until n) {
            val e = edgeMixed[p]
            opacity[p] = 1f - smoothstep(0.9f - 2f * fwMixed[p], 0.9f, e)
            edge[p] = 1.2f * e
        }

        for (p in 0 until n) {
            val ux = uvX[p]
            val uy = uvY[p]
            val rx = (ux - 0.5f) * cosA - (uy - 0.5f) * sinA + 0.5f
            val ry = (ux - 0.5f) * sinA + (uy - 0.5f) * cosA + 0.5f
            val bl = rx - ry
            diagBL[p] = bl
            color2B[p] = 0.1f + 0.1f * smoothstep(0.7f, 1.3f, rx + ry)

            val gx = ux - 0.5f
            val gy = uy - 0.5f
            val dist = sqrt(gx * gx + (gy + 0.2f * bl) * (gy + 0.2f * bl))
            val th = ((0.25f - 0.2f * bl) * PI).toFloat()
            dirBase[p] = cos(th) * gx - sin(th) * gy

            var b = 1f - (1.8f * dist).pow(1.2f)
            b *= uy.pow(0.3f)
            val thin1 = 0.12f / cw * (1f - 0.4f * b)
            val thin2 = 0.07f / cw * (1f + 0.4f * b)
            w0[p] = cw * thin1
            w1[p] = cw * thin2
            w2[p] = 1f - thin1 - thin2
            b *= uy.pow(0.1f).coerceIn(0.3f, 1f)
            bump[p] = b

            dirAdd[p] = 0.18f * (smoothstep(0.1f, 0.2f, uy) * (1f - smoothstep(0.2f, 0.4f, uy))) +
                0.03f * (smoothstep(0.1f, 0.2f, 1f - uy) * (1f - smoothstep(0.2f, 0.4f, 1f - uy)))
            dirScale[p] = (0.5f + 0.5f * uy * uy) * cw

            val cd = (1f - b).coerceIn(0f, 1f)
            dispR[p] = cd + 5f * (smoothstep(-0.1f, 0.2f, uy) * (1f - smoothstep(0.1f, 0.5f, uy))) *
                (smoothstep(0.4f, 0.6f, b) * (1f - smoothstep(0.4f, 1f, b))) - bl
            dispB[p] = cd * 1.3f + (smoothstep(0f, 0.4f, uy) * (1f - smoothstep(0.1f, 0.8f, uy))) *
                (smoothstep(0.4f, 0.6f, b) * (1f - smoothstep(0.4f, 0.8f, b)))
        }

        // colorBandingFix: ±½/256 from gl_FragCoord (origin bottom-left, pixel centres).
        for (j in 0 until size) {
            for (i in 0 until size) {
                val fx = 0.014f * (i + 0.5f)
                val fy = 0.014f * (size - 1 - j + 0.5f)
                val h = sin(fx * 12.9898f + fy * 78.233f) * 43758.547f
                dither[j * size + i] = (h - floor(h)) - 0.5f
            }
        }
    }

    /** Renders the sheet at shader time [timeSeconds] (already × preset speed) into [out]. */
    fun render(timeSeconds: Double, out: ByteArray) {
        val m = material
        val t = (0.3 * (timeSeconds + 2.8)).toFloat()
        val contourHi = smoothstep(0.5f, 1f, m.contour)
        val contourPow = 0.2f * m.contour.pow(4)
        val shiftR = m.shiftRed / 20f
        val shiftB = m.shiftBlue / 20f
        val distortion = m.distortion

        for (p in 0 until n) {
            val noise = snoise(uvX[p] - t, uvY[p] - t)
            val e = edge[p] + (1f - edge[p]) * distortion * noise
            val bl = diagBL[p]
            val b = bump[p]
            val se = smoothstep(0f, 1f, e)
            var direction = dirBase[p] + bl
            direction -= 2f * noise * bl * (se * (1f - se))
            direction *= mix(1f, 1f - e, contourHi)
            direction -= 1.7f * e * contourHi
            direction += contourPow * (1f - se)
            direction *= 0.1f + (1.1f - e) * b
            direction *= 0.4f + 0.6f * (1f - smoothstep(0.5f, 1f, e))
            direction += dirAdd[p]
            direction = direction * dirScale[p] - t

            val dR = (dispR[p] + 0.03f * b * noise) * shiftR
            val dB = (dispB[p] - 0.2f * e) * shiftB
            w1Frame[p] = w1[p] - 0.02f * smoothstep(0f, 1f, e + b)
            stripeR[p] = fract(direction + dR)
            stripeG[p] = fract(direction)
            stripeB[p] = fract(direction - dB)
        }

        val blur = m.softness / 15f
        val fwR = fwidth(stripeR)
        val fwG = fwidth(stripeG)
        val fwB = fwidth(stripeB)
        val tint = m.tint
        val back = m.colorBack
        for (p in 0 until n) {
            val b = bump[p]
            val wa = w0[p]
            val wb = w1Frame[p]
            val wc = w2[p]
            var r = colorChanges(0.98f, 0.1f, stripeR[p], wa, wb, wc, blur + fwR[p], b, tint[0], tint[3])
            var g = colorChanges(0.98f, 0.1f, stripeG[p], wa, wb, wc, blur + fwG[p], b, tint[1], tint[3])
            var bl = colorChanges(1.0f, color2B[p], stripeB[p], wa, wb, wc, blur + fwB[p], b, tint[2], tint[3])
            var op = opacity[p]
            r = r * op + back[0] * back[3] * (1f - op)
            g = g * op + back[1] * back[3] * (1f - op)
            bl = bl * op + back[2] * back[3] * (1f - op)
            op += back[3] * (1f - op)
            val d = dither[p] / 256f
            val o = p * 4
            val a255 = (op * 255f + 0.5f).toInt().coerceIn(0, 255)
            out[o] = ((r + d) * 255f + 0.5f).toInt().coerceIn(0, a255).toByte()
            out[o + 1] = ((g + d) * 255f + 0.5f).toInt().coerceIn(0, a255).toByte()
            out[o + 2] = ((bl + d) * 255f + 0.5f).toInt().coerceIn(0, a255).toByte()
            out[o + 3] = a255.toByte()
        }
    }

    /** |dFdx| + |dFdy| with 2×2-quad differences (each quad shares its pair's delta). */
    private fun fwidth(v: FloatArray): FloatArray {
        val out = FloatArray(n)
        for (j in 0 until size) {
            val jq = j and 1.inv()
            val jn = min(jq + 1, size - 1)
            for (i in 0 until size) {
                val iq = i and 1.inv()
                val inx = min(iq + 1, size - 1)
                val dx = v[j * size + inx] - v[j * size + iq]
                val dy = v[jn * size + i] - v[jq * size + i]
                out[j * size + i] = abs(dx) + abs(dy)
            }
        }
        return out
    }
}

/** A resolved preset mode (`src/engine/presets.ts`): the uniforms Paper's shader reads. */
class MetalMaterial(
    val repetition: Float,
    val softness: Float,
    val shiftRed: Float,
    val shiftBlue: Float,
    val distortion: Float,
    val contour: Float,
    val angle: Float,
    /** Colour-burn tint RGBA in 0..1; alpha is the blend amount. */
    val tint: FloatArray,
    /** Backdrop RGBA in 0..1, composited under the material. */
    val colorBack: FloatArray,
    val speed: Float,
    val shaderOpacity: Float,
)

private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun fract(x: Float): Float = x - floor(x)

/** Paper's `getColorChanges()` for one channel; tint is applied as colour burn. */
private fun colorChanges(
    c1: Float, c2: Float, p: Float,
    w0: Float, w1: Float, w2: Float,
    blur: Float, bump: Float, tint: Float, tintAmount: Float,
): Float {
    var ch = mix(c2, c1, smoothstep(0f, 2f * blur, p))
    var border = w0
    ch = mix(ch, c2, smoothstep(border, border + 2f * blur, p))
    border = w0 + 0.4f * (1f - bump) * w1
    ch = mix(ch, c1, smoothstep(border, border + 2f * blur, p))
    border = w0 + 0.5f * (1f - bump) * w1
    ch = mix(ch, c2, smoothstep(border, border + 2f * blur, p))
    border = w0 + w1
    ch = mix(ch, c1, smoothstep(border, border + 2f * blur, p))
    val gradient = mix(c1, c2, smoothstep(0f, 1f, (p - w0 - w1) / w2))
    ch = mix(ch, gradient, smoothstep(border, border + 0.5f * blur, p))
    return mix(ch, 1f - min(1f, (1f - ch) / max(tint, 0.0001f)), tintAmount)
}

/** Ashima 2D simplex noise (webgl-noise), as in Paper's `simplexNoise`. */
private fun snoise(vx: Float, vy: Float): Float {
    val cx = 0.21132487f
    val cy = 0.36602542f
    val cz = -0.57735026f
    val cw = 0.024390243f
    val s = (vx + vy) * cy
    var ix = floor(vx + s)
    var iy = floor(vy + s)
    val t = (ix + iy) * cx
    val x0x = vx - ix + t
    val x0y = vy - iy + t
    val i1x: Float
    val i1y: Float
    if (x0x > x0y) {
        i1x = 1f
        i1y = 0f
    } else {
        i1x = 0f
        i1y = 1f
    }
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
    val xx0 = 2f * fract(p0 * cw) - 1f
    val xx1 = 2f * fract(p1 * cw) - 1f
    val xx2 = 2f * fract(p2 * cw) - 1f
    val h0 = abs(xx0) - 0.5f
    val h1 = abs(xx1) - 0.5f
    val h2 = abs(xx2) - 0.5f
    val a0 = xx0 - floor(xx0 + 0.5f)
    val a1 = xx1 - floor(xx1 + 0.5f)
    val a2 = xx2 - floor(xx2 + 0.5f)
    m0 *= 1.7928429f - 0.85373473f * (a0 * a0 + h0 * h0)
    m1 *= 1.7928429f - 0.85373473f * (a1 * a1 + h1 * h1)
    m2 *= 1.7928429f - 0.85373473f * (a2 * a2 + h2 * h2)
    val g0 = a0 * x0x + h0 * x0y
    val g1 = a1 * x12x + h1 * x12y
    val g2 = a2 * x12z + h2 * x12w
    return 130f * (m0 * g0 + m1 * g1 + m2 * g2)
}

private fun mod289(x: Float): Float = x - floor(x / 289f) * 289f

private fun permute(x: Float): Float = mod289(((x * 34f) + 1f) * x)
