package com.burkido.kraft.effects.gooey

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * One layer of the liquid's `box-shadow`, rebuilt on the merged silhouette. Outer layers without
 * spread are drop shadows under the whole liquid; `inset` layers and spread rings follow the
 * silhouette's edge.
 */
@Immutable
data class LiquidShadow(
    val x: Dp = 0.dp,
    val y: Dp = 0.dp,
    val blur: Dp = 0.dp,
    val spread: Dp = 0.dp,
    val color: Color,
    val inset: Boolean = false,
)

/** A shadow layer in pixels, as the platform filter builders take it. */
internal class ShadowPx(val x: Float, val y: Float, val blur: Float, val spread: Float, val color: Color, val inset: Boolean)

/**
 * The goo filter chain (`GooFilterPrimitives` + the group's CSS `drop-shadow`s) as one render
 * effect evaluated once per frame over the layer holding the blobs:
 *
 * 1. `shape` = alpha threshold of the blobs blurred by [blurPx] — `A' = contrast·A + intercept`.
 * 2. Spread rings and inset passes on the binarised shape. Morphology is expressed as a shifted
 *    threshold of the same blurred field ([thresholdShift]): moving the cut level by the field's
 *    slope at the edge moves the edge by that many pixels, on every platform.
 * 3. Outer passes under the shape, inset passes over it, then the drop shadows under everything.
 */
internal expect fun liquidRenderEffect(
    blurPx: Float,
    contrast: Float,
    shadows: List<ShadowPx>,
): RenderEffect?

/** `round((0.5 − contrast·5/12)·100)/100`: keeps the cut near the classic 18 / −7 crossing. */
internal fun gooIntercept(contrast: Float): Float = kotlin.math.round((0.5 - contrast * (5.0 / 12)) * 100).toFloat() / 100f

/**
 * The intercept that moves the goo edge [px] outward (positive) or inward (negative), from the
 * blurred field's profile at a straight edge: `A(d) = Φ(d / σ)`.
 */
internal fun thresholdShift(contrast: Float, intercept: Float, sigma: Float, px: Float): Float {
    if (sigma <= 0f || px == 0f) return intercept
    val a0 = ((0.5 - intercept) / contrast).coerceIn(1e-4, 1 - 1e-4)
    val z0 = inverseNormal(a0)
    val a1 = normalCdf(z0 - px / sigma)
    return (0.5 - contrast * a1).toFloat()
}

/** The 60 / −29.5 binarising cut the inset and spread passes read (`BINARIZE`). */
internal const val BinarizeSlope = 60f
internal const val BinarizeIntercept = -29.5f

private fun normalCdf(z: Double): Double = 0.5 * (1 + erf(z / sqrt(2.0)))

private fun erf(x: Double): Double {
    // Abramowitz–Stegun 7.1.26, |error| < 1.5e-7.
    val t = 1 / (1 + 0.3275911 * abs(x))
    val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
    return if (x >= 0) y else -y
}

private fun inverseNormal(p: Double): Double {
    // Acklam's rational approximation, refined by one Halley step.
    val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
    val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
    val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
    val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
    val low = 0.02425
    val x = when {
        p < low -> {
            val q = sqrt(-2 * ln(p))
            (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
        }
        p > 1 - low -> {
            val q = sqrt(-2 * ln(1 - p))
            -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
        }
        else -> {
            val q = p - 0.5
            val r = q * q
            (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
        }
    }
    val e = normalCdf(x) - p
    val u = e * sqrt(2 * PI) * exp(x * x / 2)
    return x - u / (1 + x * u / 2)
}

/** `parseShadow`: CSS `box-shadow` syntax into layers (lengths in dp). */
fun parseLiquidShadow(css: String): List<LiquidShadow> {
    if (css.isBlank() || css.trim() == "none") return emptyList()
    return splitTop(css, ',').mapNotNull { layer ->
        val tokens = splitTop(layer, ' ')
        if (tokens.isEmpty()) return@mapNotNull null
        val inset = "inset" in tokens
        val nums = ArrayList<Float>()
        val colour = StringBuilder()
        for (tok in tokens) {
            if (tok == "inset") continue
            val n = tok.removeSuffix("px").toFloatOrNull()
            if (nums.size < 4 && n != null) nums += n else colour.append(tok).append(' ')
        }
        LiquidShadow(
            x = (nums.getOrNull(0) ?: 0f).dp,
            y = (nums.getOrNull(1) ?: 0f).dp,
            blur = (nums.getOrNull(2) ?: 0f).dp,
            spread = (nums.getOrNull(3) ?: 0f).dp,
            color = parseColor(colour.toString().trim()),
            inset = inset,
        )
    }
}

private fun splitTop(s: String, sep: Char): List<String> {
    val parts = ArrayList<String>()
    var depth = 0
    val cur = StringBuilder()
    for (ch in s) {
        when {
            ch == '(' -> { depth++; cur.append(ch) }
            ch == ')' -> { depth--; cur.append(ch) }
            depth == 0 && (if (sep == ',') ch == ',' else ch.isWhitespace()) -> {
                if (cur.isNotBlank()) parts += cur.toString().trim()
                cur.clear()
            }
            else -> cur.append(ch)
        }
    }
    if (cur.isNotBlank()) parts += cur.toString().trim()
    return parts
}

private fun parseColor(css: String): Color =
    if (css.isEmpty()) Color.Black.copy(alpha = 0.35f) else com.burkido.kraft.effects.core.parseCssColor(css)
