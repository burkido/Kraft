package com.burkido.kraft.effects.beam

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.burkido.kraft.effects.core.CssFilter
import com.burkido.kraft.effects.core.gaussianBlur
import com.burkido.kraft.effects.core.positiveFraction
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

/**
 * The pulse family: a breathing glow with no rotation.
 *
 * Nine palette blobs grow, shrink, drift and fade per quadrant under 17 desynced cosine
 * oscillators (the web's shared `pulseDriver.ts` loop), while the hue rotates a full circle.
 *
 * - `pulse-inner`: inner glow + corner accents (edge-fade masked), a 1 px ring, and a frozen
 *   blurred bloom masked to the ring.
 * - `pulse-outside`: a halo *behind* the element — a core glow 10 px outside it (blur 3/6) and a
 *   bloom 30 px outside it (blur 22.5/15), both scaled 0.95 × 0.9 — plus the 1 px ring on top. The
 *   element must be opaque, exactly as on the web.
 */
internal class PulseBeamPainter(
    cache: CacheDrawScope,
    private val spec: BeamSpec,
    private val outside: Boolean,
    variant: BeamColorVariant,
    private val dark: Boolean,
    private val radius: Float,
    durationSeconds: Double,
    private val isStatic: Boolean,
    private val reducedMotion: Boolean,
    private val brightness: Double,
    private val saturation: Double,
    strength: Float,
) : BeamPainter {
    private val w = cache.size.width
    private val h = cache.size.height
    private val d = cache.density
    private val theme = if (dark) "dark" else "light"
    private val pulse = spec.pulse
    private val preset = spec.sizeThemePresets.getValue(if (outside) "pulse-outside" else "pulse-inner").getValue(theme)
    private val monoMul = if (variant == BeamColorVariant.Mono) 0.5f else 1f
    private val strengthMul = strength.coerceIn(0f, 1f)
    private val themeTable = (if (outside) pulse.outside else pulse.inner).getValue(theme)
    private val huePeriod = pulse.huePeriod.getValue(if (outside) "pulse-outside" else "pulse-inner")

    // Oscillators, scaled from the spec's 2.3 s reference duration.
    private val durScale = durationSeconds / 2.3
    private val oscillators = themeTable.oscillators
    private val values = FloatArray(PROPS.size)
    private val propIndex = IntArray(oscillators.size) { PROPS.indexOf(oscillators[it].prop) }

    private val palette = spec.palettes.border.getValue(variant.key).border.map { it.parse() }
    private val fades = palette.map { EllipseGradient.fade(it.color.copy(alpha = 1f)) }

    // pulse-outside scales the authored 350×140 geometry to the element, per axis.
    private val sx: Float
    private val sy: Float

    init {
        if (outside) {
            val c = pulse.outsideConstants
            val ref = c.referenceSize
            val lo = c.scaleClamp.getValue("min")
            val hi = c.scaleClamp.getValue("max")
            sx = toFixed3((w / d / ref.getValue("w")).coerceIn(lo, hi))
            sy = toFixed3((h / d / ref.getValue("h")).coerceIn(lo, hi))
        } else {
            sx = 1f
            sy = 1f
        }
    }

    private val ring = ringPath(w, h, radius * d, 1f * d)
    private val strokeContent = cache.obtainGraphicsLayer()
    private val strokeOuter = cache.obtainGraphicsLayer()

    // pulse-inner
    private val innerContent = cache.obtainGraphicsLayer()
    private val innerMask = cache.obtainGraphicsLayer()
    private val innerOuter = cache.obtainGraphicsLayer()
    private val bloomContent = cache.obtainGraphicsLayer()
    private val bloomOuter = cache.obtainGraphicsLayer()
    private val corner = pulse.innerCornerAccent
    private val cornerGradient = EllipseGradient(
        arrayOf(
            0f to (if (dark) Color.White else Color.Black),
            (corner.fadeStop / 100).toFloat() to (if (dark) Color.White else Color.Black).copy(alpha = 0f),
        ),
    )
    private val cornerAlpha = corner.alpha.getValue(theme).toFloat()
    private val innerBackground = roundRectPath(0f, 0f, w, h, radius * d)

    // pulse-outside
    private val coreLayer = cache.obtainGraphicsLayer()
    private val bloomHaloLayer = cache.obtainGraphicsLayer()

    private var staticRecorded = false

    override fun drawBehind(scope: DrawScope, t: Double, fade: Float) {
        if (!outside || w <= 0f || h <= 0f) return
        evaluate(t)
        val c = pulse.outsideConstants
        val filter = filter(t)
        // ::before — the core glow, 10 px outside the element.
        drawHalo(
            scope, coreLayer, c.coreInsetPx.toFloat(), c.glowBlurPx.getValue(theme).toFloat(), filter,
            (fade * preset.innerOpacity.toFloat() * monoMul * strengthMul).coerceIn(0f, 1f),
        ) { boxW, boxH -> drawLiveBlobs(pulse.outerCore, boxW, boxH) }
        // [data-beam-bloom] — the frozen outer bloom, 30 px outside it.
        drawHalo(
            scope, bloomHaloLayer, c.bloomInsetPx.toFloat(), c.bloomBlurPx.getValue(theme).toFloat(), filter,
            (fade * preset.bloomOpacity.toFloat() * monoMul * strengthMul).coerceIn(0f, 1f),
        ) { boxW, boxH -> drawFrozenBlobs(pulse.outerBloom, boxW, boxH) }
    }

    override fun drawOver(scope: DrawScope, t: Double, fade: Float): Unit = with(scope) {
        if (w <= 0f || h <= 0f) return
        evaluate(t)
        val filter = filter(t)
        if (!outside) {
            if (!staticRecorded) {
                recordEdgeFadeMask(innerMask, w, h, spec.rotate.innerEdgeMaskPx.toFloat() * d)
                staticRecorded = true
            }
            // z 1 — inner glow + corner accents, edge-fade masked.
            innerContent.colorFilter = filter
            innerContent.record {
                val q = pulse.innerCornerAccent.sizePx.toFloat() * d
                drawCorner(w, h, q, values[BOP_BR])
                drawCorner(0f, h, q, values[BOP_BL])
                drawCorner(w, 0f, q, values[BOP_TR])
                drawCorner(0f, 0f, q, values[BOP_TL])
                for (i in palette.indices.reversed()) {
                    val slot = pulse.ringMap[i]
                    val size = pulse.innerSizes[i]
                    drawBlob(i, slot.region, slot.quad, palette[i].centerX, palette[i].centerY, size[0], size[1], w, h, Offset.Zero)
                }
            }
            innerOuter.compositingStrategy = CompositingStrategy.Offscreen
            innerOuter.alpha = (fade * preset.innerOpacity.toFloat() * monoMul * strengthMul).coerceIn(0f, 1f)
            innerOuter.record {
                drawLayer(innerContent)
                drawLayer(innerMask)
            }
            drawLayer(innerOuter)
        }

        // z 2 — the 1 px ring.
        strokeContent.colorFilter = filter
        strokeContent.record {
            if (outside) {
                drawLiveBlobs(pulse.outerCore, w, h)
            } else {
                for (i in palette.indices.reversed()) {
                    val slot = pulse.ringMap[i]
                    drawBlob(i, slot.region, slot.quad, palette[i].centerX, palette[i].centerY, palette[i].radiusX.toDouble(), palette[i].radiusY.toDouble(), w, h, Offset.Zero)
                }
            }
        }
        strokeOuter.compositingStrategy = CompositingStrategy.Offscreen
        strokeOuter.alpha = (fade * preset.strokeOpacity.toFloat() * monoMul * strengthMul).coerceIn(0f, 1f)
        strokeOuter.record { clipPath(ring) { drawLayer(strokeContent) } }
        drawLayer(strokeOuter)

        if (!outside) {
            // z 3 — frozen bloom: blur, colour matrix, then the ring mask.
            bloomContent.renderEffect = gaussianBlur(pulse.innerBloomBlurPx.toFloat() * d)
            bloomContent.colorFilter = filter
            bloomContent.record { clipPath(innerBackground) { drawFrozenBlobs(pulse.innerBloom, w, h) } }
            bloomOuter.compositingStrategy = CompositingStrategy.Offscreen
            bloomOuter.alpha = (fade * preset.bloomOpacity.toFloat() * monoMul * strengthMul).coerceIn(0f, 1f)
            bloomOuter.record { clipPath(ring) { drawLayer(bloomContent) } }
            drawLayer(bloomOuter)
        }
    }

    /** Oscillator values at [t]; reduced motion pins them at the CSS `@property` initial values. */
    private fun evaluate(t: Double) {
        for (i in PROPS.indices) values[i] = if (PROPS[i].startsWith("bx") || PROPS[i].startsWith("by")) 0f else 1f
        if (reducedMotion) return
        for (k in oscillators.indices) {
            val o = oscillators[k]
            val phase = (t - o.delay * durScale) / (o.period * durScale)
            val ping = (1 - cos(2 * PI * phase)) / 2
            values[propIndex[k]] = (o.a + (o.b - o.a) * ping).toFloat()
        }
    }

    private fun filter(t: Double): ColorFilter {
        // Pulse keeps brightness/saturate with static colours; only the hue rotation stops.
        val hue = if (isStatic || reducedMotion) 0.0 else positiveFraction(t / huePeriod) * 360.0
        return CssFilter.colorFilter(CssFilter.hueBrightnessSaturate(hue, brightness, saturation))
    }

    private fun DrawScope.drawCorner(cx: Float, cy: Float, size: Float, bop: Float) {
        cornerGradient.draw(this, cx, cy, size, size, cornerAlpha * bop)
    }

    /**
     * One live blob: centre at the palette position (fractions of [boxW]×[boxH]) plus the region's
     * drift, radii × the region's size oscillators, alpha = the quadrant's opacity oscillator.
     */
    private fun DrawScope.drawBlob(
        ci: Int, region: Int, quad: String,
        fx: Float, fy: Float, baseW: Double, baseH: Double,
        boxW: Float, boxH: Float, origin: Offset,
    ) {
        val r = region - 1
        val cx = origin.x + fx * boxW + values[BX1 + r * 4] * d
        val cy = origin.y + fy * boxH + values[BY1 + r * 4] * d
        val rx = (baseW * values[BW1 + r * 4] * sx).toFloat() * d
        val ry = (baseH * values[BH1 + r * 4] * values[BGH] * sy).toFloat() * d
        fades[ci].draw(this, cx, cy, rx, ry, values[quadIndex(quad)])
    }

    private fun DrawScope.drawLiveBlobs(table: List<BeamSpec.PulseBlob>, boxW: Float, boxH: Float) {
        for (i in table.indices.reversed()) {
            val e = table[i]
            val base = palette[e.ci]
            val fx = e.x?.let { percent(it) } ?: base.centerX
            val fy = e.y?.let { percent(it) } ?: base.centerY
            drawBlob(e.ci, e.region, e.quad, fx, fy, e.w, e.h, boxW, boxH, Offset.Zero)
        }
    }

    /** The frozen blooms: literal sizes (× the element scale) at the time-averaged alpha. */
    private fun DrawScope.drawFrozenBlobs(table: List<BeamSpec.PulseBlob>, boxW: Float, boxH: Float) {
        val alpha = themeTable.frozenBloomAlpha.toFloat()
        for (i in table.indices.reversed()) {
            val e = table[i]
            val base = palette[e.ci]
            val fx = e.x?.let { percent(it) } ?: base.centerX
            val fy = e.y?.let { percent(it) } ?: base.centerY
            fades[e.ci].draw(this, fx * boxW, fy * boxH, (e.w * sx).toFloat() * d, (e.h * sy).toFloat() * d, alpha)
        }
    }

    /**
     * Records a halo layer [insetPx] outside the element: background clipped to radius + inset,
     * blurred by [blurPx] then colour-filtered, drawn scaled (0.95, 0.9) about the element centre.
     */
    private fun drawHalo(
        scope: DrawScope,
        layer: GraphicsLayer,
        insetPx: Float,
        blurPx: Float,
        filter: ColorFilter,
        alpha: Float,
        content: DrawScope.(boxW: Float, boxH: Float) -> Unit,
    ) = with(scope) {
        val inset = insetPx * d
        val boxW = w + 2 * inset
        val boxH = h + 2 * inset
        val sigma = blurPx * d
        val margin = ceil(sigma * 3f).toInt() + 2
        val layerW = boxW.toInt() + 2 * margin
        val layerH = boxH.toInt() + 2 * margin
        val scale = pulse.outsideConstants.glowScale
        layer.renderEffect = gaussianBlur(sigma)
        layer.colorFilter = filter
        layer.alpha = alpha
        layer.scaleX = scale.getValue("x").toFloat()
        layer.scaleY = scale.getValue("y").toFloat()
        layer.pivotOffset = Offset(layerW / 2f, layerH / 2f)
        layer.topLeft = IntOffset(-(inset.toInt() + margin), -(inset.toInt() + margin))
        layer.record(IntSize(layerW, layerH)) {
            translate(margin.toFloat(), margin.toFloat()) {
                clipPath(roundRectPath(0f, 0f, boxW, boxH, radius * d + inset)) { content(boxW, boxH) }
            }
        }
        drawLayer(layer)
    }

    private fun quadIndex(quad: String) = when (quad) {
        "tl" -> BOP_TL
        "tr" -> BOP_TR
        "bl" -> BOP_BL
        else -> BOP_BR
    }

    private fun percent(s: String): Float = s.trim().removeSuffix("%").toFloat() / 100f

    private fun toFixed3(v: Double): Float = (kotlin.math.floor(v * 1000 + 0.5) / 1000).toFloat()

    companion object {
        /** Oscillated properties, grouped per region as (bw, bh, bx, by) so region r starts at r·4. */
        private val PROPS = listOf(
            "bw1", "bh1", "bx1", "by1",
            "bw2", "bh2", "bx2", "by2",
            "bw3", "bh3", "bx3", "by3",
            "bgh", "bop-tl", "bop-tr", "bop-bl", "bop-br",
        )
        private const val BW1 = 0
        private const val BH1 = 1
        private const val BX1 = 2
        private const val BY1 = 3
        private const val BGH = 12
        private const val BOP_TL = 13
        private const val BOP_TR = 14
        private const val BOP_BL = 15
        private const val BOP_BR = 16
    }
}

/** A beam family's renderer. [drawBehind] runs under the content, [drawOver] above it. */
internal interface BeamPainter {
    fun drawBehind(scope: DrawScope, t: Double, fade: Float) {}

    fun drawOver(scope: DrawScope, t: Double, fade: Float)
}
