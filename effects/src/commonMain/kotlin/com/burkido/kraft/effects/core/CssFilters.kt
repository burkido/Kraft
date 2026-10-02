package com.burkido.kraft.effects.core

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * CSS filter functions as 4×5 colour matrices, exactly as the Filter Effects spec defines them.
 *
 * Matrices are row-major `FloatArray(20)` in Compose's [ColorMatrix] convention: they act on
 * unpremultiplied sRGB, and the offset column is in 0..255 units on every platform (CSS/SVG
 * write offsets in 0..1, so they are scaled by 255 here).
 */
object CssFilter {
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** `hue-rotate(<degrees>)`. */
    fun hueRotate(degrees: Double): FloatArray {
        val a = degrees * PI / 180.0
        val c = cos(a)
        val s = sin(a)
        return floatArrayOf(
            (0.213 + c * 0.787 - s * 0.213).toFloat(),
            (0.715 - c * 0.715 - s * 0.715).toFloat(),
            (0.072 - c * 0.072 + s * 0.928).toFloat(), 0f, 0f,
            (0.213 - c * 0.213 + s * 0.143).toFloat(),
            (0.715 + c * 0.285 + s * 0.140).toFloat(),
            (0.072 - c * 0.072 - s * 0.283).toFloat(), 0f, 0f,
            (0.213 - c * 0.213 - s * 0.787).toFloat(),
            (0.715 - c * 0.715 + s * 0.715).toFloat(),
            (0.072 + c * 0.928 + s * 0.072).toFloat(), 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** `saturate(<amount>)`. */
    fun saturate(amount: Double): FloatArray {
        val s = amount
        return floatArrayOf(
            (0.213 + 0.787 * s).toFloat(), (0.715 - 0.715 * s).toFloat(), (0.072 - 0.072 * s).toFloat(), 0f, 0f,
            (0.213 - 0.213 * s).toFloat(), (0.715 + 0.285 * s).toFloat(), (0.072 - 0.072 * s).toFloat(), 0f, 0f,
            (0.213 - 0.213 * s).toFloat(), (0.715 - 0.715 * s).toFloat(), (0.072 + 0.928 * s).toFloat(), 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** `brightness(<amount>)`. */
    fun brightness(amount: Double): FloatArray {
        val b = amount.toFloat()
        return floatArrayOf(
            b, 0f, 0f, 0f, 0f,
            0f, b, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** `contrast(<amount>)`: `c·x + (0.5 − 0.5c)`, offset scaled to 0..255. */
    fun contrast(amount: Double): FloatArray {
        val c = amount.toFloat()
        val o = ((0.5 - 0.5 * amount) * 255.0).toFloat()
        return floatArrayOf(
            c, 0f, 0f, 0f, o,
            0f, c, 0f, 0f, o,
            0f, 0f, c, 0f, o,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** `opacity(<amount>)` as a matrix (scales alpha). */
    fun opacity(amount: Double): FloatArray = identity().also { it[18] = amount.toFloat() }

    /**
     * `outer ∘ inner`: the matrix that applies [inner] first, then [outer]. A CSS
     * `filter: f1 f2 f3` list is `concat(f3, concat(f2, f1))`.
     */
    fun concat(outer: FloatArray, inner: FloatArray): FloatArray {
        val r = FloatArray(20)
        for (row in 0 until 4) {
            val o = row * 5
            for (col in 0 until 4) {
                r[o + col] = outer[o] * inner[col] +
                    outer[o + 1] * inner[5 + col] +
                    outer[o + 2] * inner[10 + col] +
                    outer[o + 3] * inner[15 + col]
            }
            r[o + 4] = outer[o] * inner[4] +
                outer[o + 1] * inner[9] +
                outer[o + 2] * inner[14] +
                outer[o + 3] * inner[19] +
                outer[o + 4]
        }
        return r
    }

    /** `filter: hue-rotate(h) brightness(b) saturate(s)` — the order the effect libraries use. */
    fun hueBrightnessSaturate(hueDegrees: Double, brightness: Double, saturate: Double): FloatArray =
        concat(saturate(saturate), concat(brightness(brightness), hueRotate(hueDegrees)))

    fun colorFilter(matrix: FloatArray): ColorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix))
}

/**
 * The Compose blur radius (px) whose Gaussian σ is [sigmaPx].
 *
 * Both Skia backends convert a blur radius with σ = 0.57735·r + 0.5 (HWUI `Blur.cpp`, Skiko
 * `convertRadiusToSigma`), so this inverts it exactly. CSS `filter: blur(N)` means σ = N, while a
 * `box-shadow` blur radius B means σ = B / 2.
 */
fun blurRadiusForSigma(sigmaPx: Float): Float =
    if (sigmaPx <= 0.5f) 0f else (sigmaPx - 0.5f) / 0.57735f

/** A Gaussian blur with standard deviation [sigmaPx], or `null` when it would be a no-op. */
fun gaussianBlur(sigmaPx: Float, tileMode: TileMode = TileMode.Decal): RenderEffect? {
    val r = blurRadiusForSigma(sigmaPx)
    return if (r <= 0f) null else BlurEffect(r, r, tileMode)
}
