package com.burkido.kraft.effects.beam

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.burkido.kraft.effects.core.CssFilter
import com.burkido.kraft.effects.core.CubicBezier
import com.burkido.kraft.effects.core.Keyframes
import com.burkido.kraft.effects.core.gaussianBlur
import com.burkido.kraft.effects.core.positiveFraction
import com.burkido.kraft.effects.core.jsRound
import com.burkido.kraft.effects.core.parseCssColor
import kotlin.math.ceil

/**
 * The rotate family (`sm`, `md`): a conic mask spins around the card while three pseudo-element
 * layers glow through it. Each layer is reproduced the way Chrome renders the generated CSS —
 * content, then the `filter` colour matrix, then `mask`/`clip-path`, then `opacity`:
 *
 * - inner (`::before`, z 1): palette blobs + inset box-shadow, masked by the conic (and, for
 *   `md`, by 28 px edge fades);
 * - stroke (`::after`, z 2): palette blobs + a conic highlight, masked to the 1 px ring and the conic;
 * - bloom (`[data-beam-bloom]`, z 3): a conic highlight blurred, then masked to the ring.
 */
internal class RotateBeamPainter(
    cache: CacheDrawScope,
    private val config: RotateConfig,
    private val durationSeconds: Double,
    private val isStatic: Boolean,
    hueRange: Double,
    private val hueShiftPeriod: Double,
    private val brightness: Double,
    private val saturation: Double,
) : BeamPainter {
    private val hueTrack = Keyframes(doubleArrayOf(0.0, 0.5, 1.0), doubleArrayOf(-hueRange, hueRange, -hueRange), CubicBezier.EaseInOut)

    private val size: Size = cache.size
    private val density: Float = cache.density
    private val w = size.width
    private val h = size.height
    private val center = Offset(w / 2f, h / 2f)

    private val innerContent = cache.obtainGraphicsLayer()
    private val innerMask = cache.obtainGraphicsLayer()
    private val innerOuter = cache.obtainGraphicsLayer()
    private val insetShadow = cache.obtainGraphicsLayer()
    private val strokeContent = cache.obtainGraphicsLayer()
    private val strokeOuter = cache.obtainGraphicsLayer()
    private val bloomContent = cache.obtainGraphicsLayer()
    private val bloomOuter = cache.obtainGraphicsLayer()

    private val strokeBlobs = config.strokeBlobs.map { blobBrush(it) }
    private val innerBlobs = config.innerBlobs.map { blobBrush(it) }

    private val beamMask = sweep(config.beamMaskStops.toStops(Color.White))
    private val innerConicMask = sweep(config.innerMaskStops.toStops(Color.White))
    private val highlight = sweep(config.highlightStops.toStops(config.highlightColor))
    private val bloom = sweep(config.bloomStops.toStops(config.highlightColor))

    // `border-radius: R-1` with `padding: 1px` → the ring between the border box (R-1) and the
    // content box (inset 1, R-2). The wrapper's own `overflow: hidden` clip supplies the R curve.
    private val ring: Path = Path().apply {
        val b = config.borderWidth * density
        val r = config.radius * density
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(maxOf(0f, r - b))))
        addRoundRect(RoundRect(b, b, w - b, h - b, CornerRadius(maxOf(0f, r - 2 * b))))
    }
    private val bloomBackground: Path = Path().apply {
        val r = (config.radius - config.borderWidth) * density
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(maxOf(0f, r))))
    }

    private var staticRecorded = false

    override fun drawOver(scope: DrawScope, t: Double, fade: Float) {
        val angle = (positiveFraction(t / durationSeconds) * 360.0).toFloat()
        // Mono / staticColors drop the whole `filter` (no brightness or saturate either).
        val filter = if (isStatic) {
            null
        } else {
            val hue = hueTrack.valueAtTime(t, hueShiftPeriod)
            CssFilter.colorFilter(CssFilter.hueBrightnessSaturate(hue, brightness, saturation))
        }
        draw(scope, angle, filter, fade)
    }

    private fun draw(scope: DrawScope, angleDegrees: Float, colorFilter: ColorFilter?, fade: Float): Unit = with(scope) {
        if (w <= 0f || h <= 0f) return
        if (!staticRecorded) {
            recordStatic()
            staticRecorded = true
        }
        rotate(beamMask, angleDegrees)
        rotate(innerConicMask, angleDegrees)
        rotate(highlight, angleDegrees)
        rotate(bloom, angleDegrees)

        // z 1 — inner glow.
        innerContent.colorFilter = colorFilter
        innerContent.record {
            innerBlobs.forEach { it.draw(this) }
            drawLayer(insetShadow)
        }
        innerOuter.compositingStrategy = CompositingStrategy.Offscreen
        innerOuter.alpha = (fade * config.innerOpacity).coerceIn(0f, 1f)
        innerOuter.record {
            drawLayer(innerContent)
            if (config.innerEdgeMaskPx > 0f) drawLayer(innerMask)
            drawRect(innerConicMask, blendMode = BlendMode.DstIn)
        }
        drawLayer(innerOuter)

        // z 2 — stroke.
        strokeContent.colorFilter = colorFilter
        strokeContent.record {
            strokeBlobs.forEach { it.draw(this) }
            drawRect(highlight)
        }
        strokeOuter.compositingStrategy = CompositingStrategy.Offscreen
        strokeOuter.alpha = (fade * config.strokeOpacity).coerceIn(0f, 1f)
        strokeOuter.record {
            clipPath(ring) { drawLayer(strokeContent) }
            drawRect(beamMask, blendMode = BlendMode.DstIn)
        }
        drawLayer(strokeOuter)

        // z 3 — bloom: the filter (blur) runs before the ring mask, as CSS orders it.
        bloomContent.renderEffect = gaussianBlur(config.bloomBlurPx * density)
        bloomContent.record {
            clipPath(bloomBackground) { drawRect(bloom) }
        }
        bloomOuter.compositingStrategy = CompositingStrategy.Offscreen
        bloomOuter.alpha = (fade * config.bloomOpacity).coerceIn(0f, 1f)
        bloomOuter.record {
            clipPath(ring) { drawLayer(bloomContent) }
        }
        drawLayer(bloomOuter)
    }

    private fun DrawScope.recordStatic() {
        // `box-shadow: inset 0 0 <blur> <spread> <color>`: everything outside the padding box
        // shrunk by the spread, blurred with σ = blur / 2. Recorded with a margin so the blur sees
        // the shadow continuing past the edges, exactly like an unbounded CSS shadow.
        val sigma = config.insetShadowBlurPx / 2f * density
        val margin = ceil(sigma * 4f).toInt() + 1
        val spread = config.insetShadowSpreadPx * density
        val r = config.radius * density
        insetShadow.renderEffect = gaussianBlur(sigma)
        insetShadow.topLeft = IntOffset(-margin, -margin)
        insetShadow.record(IntSize(w.toInt() + 2 * margin, h.toInt() + 2 * margin)) {
            translate(margin.toFloat(), margin.toFloat()) {
                val frame = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(-margin.toFloat(), -margin.toFloat(), w + margin, h + margin))
                    addRoundRect(RoundRect(spread, spread, w - spread, h - spread, CornerRadius(maxOf(0f, r - spread))))
                }
                drawPath(frame, config.insetShadowColor)
            }
        }

        // `md` edge fades: two linear masks added together (`mask-composite: add`).
        val e = config.innerEdgeMaskPx * density
        if (e > 0f) {
            innerMask.blendMode = BlendMode.DstIn
            innerMask.record {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White, e / h to Color.Transparent,
                        1f - e / h to Color.Transparent, 1f to Color.White,
                    ),
                )
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.White, e / w to Color.Transparent,
                        1f - e / w to Color.Transparent, 1f to Color.White,
                    ),
                )
            }
        }
    }

    private fun sweep(stops: Array<Pair<Float, Color>>): ShaderBrush =
        Brush.sweepGradient(*stops, center = center) as ShaderBrush

    /** `conic-gradient(from <angle>)` starts at 12 o'clock; Skia's sweep starts at 3 o'clock. */
    private fun rotate(brush: ShaderBrush, angleDegrees: Float) {
        brush.transform = Matrix().apply {
            translate(center.x, center.y)
            rotateZ(angleDegrees - 90f)
            translate(-center.x, -center.y)
        }
    }

    private fun blobBrush(blob: Blob): BlobBrush {
        val cx = blob.centerX * w
        val cy = blob.centerY * h
        val rx = blob.radiusX * density
        val ry = blob.radiusY * density
        val brush = Brush.radialGradient(
            0f to blob.color,
            1f to blob.color.copy(alpha = 0f),
            center = Offset(cx, cy),
            radius = rx,
        ) as ShaderBrush
        brush.transform = Matrix().apply {
            translate(cx, cy)
            scale(1f, ry / rx)
            translate(-cx, -cy)
        }
        return BlobBrush(brush, Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry))
    }

    private class BlobBrush(val brush: ShaderBrush, val topLeft: Offset, val size: Size) {
        fun draw(scope: DrawScope) = scope.drawRect(brush, topLeft, size)
    }
}

/** Everything the rotate painter needs, resolved from the spec for one size/variant/theme. */
internal class RotateConfig(
    val radius: Float,
    val borderWidth: Float,
    val strokeBlobs: List<Blob>,
    val innerBlobs: List<Blob>,
    val beamMaskStops: List<List<Double>>,
    val innerMaskStops: List<List<Double>>,
    val innerEdgeMaskPx: Float,
    val highlightStops: List<List<Double>>,
    val bloomStops: List<List<Double>>,
    val highlightColor: Color,
    val insetShadowColor: Color,
    val insetShadowBlurPx: Float,
    val insetShadowSpreadPx: Float,
    val bloomBlurPx: Float,
    val strokeOpacity: Float,
    val innerOpacity: Float,
    val bloomOpacity: Float,
)

internal fun BeamSpec.rotateConfig(
    size: BeamSize,
    variant: BeamColorVariant,
    dark: Boolean,
    radius: Float,
    strength: Float,
): RotateConfig {
    val theme = if (dark) "dark" else "light"
    val preset = sizeThemePresets.getValue(size.key).getValue(theme)
    val mono = variant == BeamColorVariant.Mono
    val opacityScale = (if (mono) defaults.monoOpacityMultiplier.toFloat() else 1f) * strength.coerceIn(0f, 1f)
    val small = size == BeamSize.Sm
    val strokeBlobs: List<Blob>
    val innerBlobs: List<Blob>
    if (small) {
        val palette = palettes.small.getValue(variant.key)
        strokeBlobs = palette.border.map { it.parse() }
        innerBlobs = palette.inner.map { it.parse() }
    } else {
        val derivation = rotate.innerGradientDerivation
        val alpha = if (mono) derivation.monoAlpha else derivation.alpha
        strokeBlobs = palettes.border.getValue(variant.key).border.map { it.parse() }
        // getInnerGradients(): radii × 0.9 rounded to whole px, colour at the inner alpha.
        innerBlobs = strokeBlobs.map {
            Blob(
                color = it.color.copy(alpha = alpha.toFloat()),
                centerX = it.centerX,
                centerY = it.centerY,
                radiusX = jsRound(it.radiusX * derivation.sizeScale).toFloat(),
                radiusY = jsRound(it.radiusY * derivation.sizeScale).toFloat(),
            )
        }
    }
    return RotateConfig(
        radius = radius,
        borderWidth = sizePresets.getValue(size.key).borderWidth.toFloat(),
        strokeBlobs = strokeBlobs,
        innerBlobs = innerBlobs,
        beamMaskStops = rotate.beamMaskStops,
        innerMaskStops = if (small) rotate.smallMaskStops else rotate.beamMaskStops,
        innerEdgeMaskPx = if (small) 0f else rotate.innerEdgeMaskPx.toFloat(),
        highlightStops = rotate.whiteGradientStops.getValue(theme),
        bloomStops = rotate.bloomGradientStops.getValue(theme),
        highlightColor = if (dark) Color.White else Color.Black,
        insetShadowColor = parseCssColor(preset.innerShadow),
        insetShadowBlurPx = rotate.innerShadowBlur.getValue(size.key).toFloat(),
        insetShadowSpreadPx = 1f,
        bloomBlurPx = rotate.bloomBlurPx.toFloat(),
        strokeOpacity = (preset.strokeOpacity * opacityScale).toFloat(),
        innerOpacity = (preset.innerOpacity * opacityScale).toFloat(),
        bloomOpacity = (preset.bloomOpacity * opacityScale).toFloat(),
    )
}
