package com.burkido.kraft.effects.gooey

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import com.burkido.kraft.effects.core.blurRadiusForSigma
import android.graphics.RenderEffect as PlatformEffect

internal actual fun liquidRenderEffect(blurPx: Float, contrast: Float, shadows: List<ShadowPx>): RenderEffect? {
    val intercept = gooIntercept(contrast)
    val sigma = blurPx
    val blurred: PlatformEffect? = blurRadiusForSigma(sigma).takeIf { it > 0f }?.let {
        PlatformEffect.createBlurEffect(it, it, Shader.TileMode.DECAL)
    }

    fun cut(slope: Float, offset: Float): PlatformEffect {
        val filter = ColorMatrixColorFilter(
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, slope, offset * 255f),
        )
        return if (blurred != null) PlatformEffect.createColorFilterEffect(filter, blurred) else PlatformEffect.createColorFilterEffect(filter)
    }

    /** The binarised silhouette, grown by [grow] px (negative shrinks). */
    fun bin(grow: Float): PlatformEffect {
        val icpt = thresholdShift(contrast, intercept, sigma, grow)
        return cut(BinarizeSlope * contrast, BinarizeSlope * icpt + BinarizeIntercept)
    }

    fun blur(sigmaPx: Float, input: PlatformEffect): PlatformEffect {
        val r = blurRadiusForSigma(sigmaPx)
        return if (r > 0f) PlatformEffect.createBlurEffect(r, r, input, Shader.TileMode.DECAL) else input
    }

    fun tint(color: androidx.compose.ui.graphics.Color, input: PlatformEffect): PlatformEffect =
        PlatformEffect.createColorFilterEffect(BlendModeColorFilter(color.toArgb(), BlendMode.SRC_IN), input)

    fun over(bottom: PlatformEffect, top: PlatformEffect): PlatformEffect =
        PlatformEffect.createBlendModeEffect(bottom, top, BlendMode.SRC_OVER)

    val shape = cut(contrast, intercept)
    val svg = shadows.filter { it.inset || it.spread != 0f }
    val drops = shadows.filter { !it.inset && it.spread == 0f }

    fun pass(s: ShadowPx): PlatformEffect = if (s.inset) {
        // The band: the silhouette minus its shrunk / offset / blurred self.
        var src: PlatformEffect = if (s.spread != 0f) bin(-s.spread) else bin(0f)
        if (s.x != 0f || s.y != 0f) src = PlatformEffect.createOffsetEffect(s.x, s.y, src)
        if (s.blur > 0f) src = blur(s.blur / 2, src)
        tint(s.color, PlatformEffect.createBlendModeEffect(src, bin(0f), BlendMode.SRC_OUT))
    } else {
        var src: PlatformEffect = if (s.spread != 0f) bin(s.spread) else shape
        if (s.blur > 0f) src = blur(s.blur / 2, src)
        if (s.x != 0f || s.y != 0f) src = PlatformEffect.createOffsetEffect(s.x, s.y, src)
        tint(s.color, src)
    }

    // Outer passes under the shape (the first listed on top), inset passes over it.
    var merged: PlatformEffect? = null
    for (s in svg.filter { !it.inset }.asReversed()) merged = merged?.let { over(it, pass(s)) } ?: pass(s)
    merged = merged?.let { over(it, shape) } ?: shape
    for (s in svg.filter { it.inset }) merged = over(merged!!, pass(s))

    // CSS `drop-shadow()`s on the rendered liquid, applied in order (blur is σ, not 2σ).
    var out = merged!!
    for (s in drops) {
        val shadow = tint(s.color, PlatformEffect.createOffsetEffect(s.x, s.y, blur(s.blur, out)))
        out = over(shadow, out)
    }
    return out.asComposeRenderEffect()
}
