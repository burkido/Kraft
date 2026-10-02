package com.burkido.kraft.effects.gooey

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaColorFilter
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.BlendMode as SkBlendMode

internal actual fun liquidRenderEffect(blurPx: Float, contrast: Float, shadows: List<ShadowPx>): RenderEffect? {
    val intercept = gooIntercept(contrast)
    val sigma = blurPx
    val blurred: ImageFilter? = if (sigma > 0f) ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL, null, null) else null

    fun cut(slope: Float, offset: Float): ImageFilter {
        val filter = ColorFilter.colorMatrix(
            ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, slope, offset * 255f)),
        )
        return ImageFilter.makeColorFilter(filter.asSkiaColorFilter(), blurred, null)
    }

    fun bin(grow: Float): ImageFilter {
        val icpt = thresholdShift(contrast, intercept, sigma, grow)
        return cut(BinarizeSlope * contrast, BinarizeSlope * icpt + BinarizeIntercept)
    }

    fun blur(sigmaPx: Float, input: ImageFilter): ImageFilter =
        if (sigmaPx > 0f) ImageFilter.makeBlur(sigmaPx, sigmaPx, FilterTileMode.DECAL, input, null) else input

    fun tint(color: Color, input: ImageFilter): ImageFilter =
        ImageFilter.makeColorFilter(ColorFilter.tint(color, BlendMode.SrcIn).asSkiaColorFilter(), input, null)

    fun over(bottom: ImageFilter, top: ImageFilter): ImageFilter = ImageFilter.makeBlend(SkBlendMode.SRC_OVER, bottom, top, null)

    val shape = cut(contrast, intercept)
    val svg = shadows.filter { it.inset || it.spread != 0f }
    val drops = shadows.filter { !it.inset && it.spread == 0f }

    fun pass(s: ShadowPx): ImageFilter = if (s.inset) {
        var src: ImageFilter = if (s.spread != 0f) bin(-s.spread) else bin(0f)
        if (s.x != 0f || s.y != 0f) src = ImageFilter.makeOffset(s.x, s.y, src, null)
        if (s.blur > 0f) src = blur(s.blur / 2, src)
        // Skia's blend puts the foreground over the background: the silhouette OUT its shrunk self.
        tint(s.color, ImageFilter.makeBlend(SkBlendMode.SRC_OUT, src, bin(0f), null))
    } else {
        var src: ImageFilter = if (s.spread != 0f) bin(s.spread) else shape
        if (s.blur > 0f) src = blur(s.blur / 2, src)
        if (s.x != 0f || s.y != 0f) src = ImageFilter.makeOffset(s.x, s.y, src, null)
        tint(s.color, src)
    }

    var merged: ImageFilter? = null
    for (s in svg.filter { !it.inset }.asReversed()) merged = merged?.let { over(it, pass(s)) } ?: pass(s)
    merged = merged?.let { over(it, shape) } ?: shape
    for (s in svg.filter { it.inset }) merged = over(merged!!, pass(s))

    var out = merged!!
    for (s in drops) {
        val shadow = tint(s.color, ImageFilter.makeOffset(s.x, s.y, blur(s.blur, out), null))
        out = over(shadow, out)
    }
    return out.asComposeRenderEffect()
}
