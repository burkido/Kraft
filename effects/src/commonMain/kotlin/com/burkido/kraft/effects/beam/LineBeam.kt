package com.burkido.kraft.effects.beam

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import com.burkido.kraft.effects.core.CssFilter
import com.burkido.kraft.effects.core.CubicBezier
import com.burkido.kraft.effects.core.Keyframes
import com.burkido.kraft.effects.core.gaussianBlur
import com.burkido.kraft.effects.core.parseCssColor

/**
 * The `line` type: a glow that travels along the bottom edge. Five CSS keyframe tracks drive it
 * (travel x/w, edge fade, breathe, two spike tracks); three layers draw it, as the CSS does:
 *
 * - inner (`::before`, z 1): blobs riding the travel point + inset shadow, masked by the travelling
 *   window ellipse and the 28 px edge fades;
 * - stroke (`::after`, z 2): blobs + a highlight, masked to the 1 px ring and the window;
 * - bloom (z 3): seven vertical "spikes" and two glows, blurred, masked by a taller window.
 */
internal class LineBeamPainter(
    cache: CacheDrawScope,
    private val spec: BeamSpec,
    private val variant: BeamColorVariant,
    dark: Boolean,
    private val radius: Float,
    private val durationSeconds: Double,
    private val isStatic: Boolean,
    private val hueRange: Double,
    private val brightness: Double,
    private val saturation: Double,
    strength: Float,
) : BeamPainter {
    private val w = cache.size.width
    private val h = cache.size.height
    private val d = cache.density
    private val theme = if (dark) "dark" else "light"
    private val line = spec.line
    private val preset = spec.sizeThemePresets.getValue("line").getValue(theme)
    private val clampedStrength = strength.coerceIn(0f, 1f)

    private val innerContent = cache.obtainGraphicsLayer()
    private val innerMask = cache.obtainGraphicsLayer()
    private val innerOuter = cache.obtainGraphicsLayer()
    private val insetShadow = cache.obtainGraphicsLayer()
    private val strokeContent = cache.obtainGraphicsLayer()
    private val strokeOuter = cache.obtainGraphicsLayer()
    private val bloomContent = cache.obtainGraphicsLayer()
    private val bloomOuter = cache.obtainGraphicsLayer()

    private class Blob(val gradient: EllipseGradient, val sizeW: Float, val sizeH: Float, val dx: Float, val dy: Float)

    private val strokeBlobs = spec.palettes.line.getValue(variant.key).getValue(theme).map { it.toBlob() }
    private val innerBlobs = spec.palettes.lineInner.getValue(variant.key).map { it.toBlob() }
    private val highlight = line.whiteHighlight.getValue(theme)
    private val highlightGradient = EllipseGradient.ofStops(if (highlight.onBlack) Color.Black else Color.White, highlight.stops)
    private val bloomGradients = line.bloomGradients.getValue(variant.key).getValue(theme).map { g ->
        g to EllipseGradient(Array(g.stops.size) { i ->
            val s = g.stops[i]
            s.pos.toFloat() to Color((s.r / 255).toFloat(), (s.g / 255).toFloat(), (s.b / 255).toFloat(), s.a.toFloat())
        })
    }
    private val beamWindow = line.beamMaskEllipse.let { EllipseGradient.softMask((it.softStop[0] / 100).toFloat(), it.softStop[1].toFloat()) }
    private val bloomWindow = line.bloomMaskEllipse.let { EllipseGradient.softMask((it.softStop[0] / 100).toFloat(), it.softStop[1].toFloat()) }

    private val ring = ringPath(w, h, (radius - 1f) * d, 1f * d)
    private val bloomBackground = roundRectPath(0f, 0f, w, h, (radius - 1f) * d)
    private val bloomBlurPx = when {
        variant == BeamColorVariant.Mono -> line.monoBloomExtraBlurPx.toFloat()
        isStatic -> 0f
        else -> line.bloomBlurPx.toFloat()
    }

    // Keyframe tracks. Travel and edge fade run at the duration; the rest at the CSS generator's
    // `toFixed(1)`-rounded multiples (3.1 s → 4.0, 4.1, 5.3 s).
    private val k = line.keyframes
    private val travelX = track(k.travel.x, CubicBezier.Linear)
    private val travelW = track(k.travel.w, CubicBezier.Linear)
    private val edgeFade = track(k.edgeFade, CubicBezier.Linear)
    private val breathe = track(k.breathe, CubicBezier.EaseInOut)
    private val spike = track(k.spike, CubicBezier.EaseInOut)
    private val spike2 = track(k.spike2, CubicBezier.EaseInOut)
    private val breathePeriod = toFixed1(durationSeconds * k.durationScale.getValue("breathe"))
    private val spikePeriod = toFixed1(durationSeconds * k.durationScale.getValue("spike"))
    private val spike2Period = toFixed1(durationSeconds * k.durationScale.getValue("spike2"))
    private val hueTrack = Keyframes(doubleArrayOf(0.0, 0.5, 1.0), doubleArrayOf(-hueRange, hueRange, -hueRange), CubicBezier.EaseInOut)
    private val bloomHueRange = hueRange + spec.defaults.lineBloomHueRangeBonus
    private val bloomHueTrack = Keyframes(doubleArrayOf(0.0, 0.5, 1.0), doubleArrayOf(-bloomHueRange, bloomHueRange, -bloomHueRange), CubicBezier.EaseInOut)

    private var staticRecorded = false

    override fun drawOver(scope: DrawScope, t: Double, fade: Float): Unit = with(scope) {
        if (w <= 0f || h <= 0f) return
        if (!staticRecorded) {
            recordInsetShadow(insetShadow, w, h, radius * d, 9f * d, 1f * d, parseCssColor(preset.innerShadow))
            recordEdgeFadeMask(innerMask, w, h, spec.rotate.innerEdgeMaskPx.toFloat() * d)
            staticRecorded = true
        }
        val x = travelX.valueAtTime(t, durationSeconds).toFloat()
        val bw = travelW.valueAtTime(t, durationSeconds).toFloat()
        val edge = edgeFade.valueAtTime(t, durationSeconds).toFloat()
        val bh = breathe.valueAtTime(t, breathePeriod).toFloat()
        val sp = spike.valueAtTime(t, spikePeriod).toFloat()
        val sp2 = spike2.valueAtTime(t, spike2Period).toFloat()
        val beamX = x * w
        val opacity = fade * edge * clampedStrength

        val filter: ColorFilter?
        val bloomFilter: ColorFilter?
        if (isStatic) {
            filter = null
            bloomFilter = null
        } else {
            val hue = hueTrack.valueAtTime(t, spec.defaults.rotateHueShiftPeriod)
            val bloomHue = bloomHueTrack.valueAtTime(t, spec.defaults.lineBloomHueShiftPeriod)
            filter = CssFilter.colorFilter(CssFilter.hueBrightnessSaturate(hue, brightness, saturation))
            bloomFilter = CssFilter.colorFilter(CssFilter.hueBrightnessSaturate(bloomHue, brightness, saturation))
        }

        // z 1 — inner.
        innerContent.colorFilter = filter
        innerContent.record {
            for (i in innerBlobs.indices.reversed()) drawBlob(innerBlobs[i], beamX, bw, bh)
            drawLayer(insetShadow)
        }
        innerOuter.compositingStrategy = CompositingStrategy.Offscreen
        innerOuter.alpha = (opacity * preset.innerOpacity.toFloat()).coerceIn(0f, 1f)
        innerOuter.record {
            drawLayer(innerContent)
            drawLayer(innerMask)
            beamWindow.drawMask(this, beamX, h, line.beamMaskEllipse.w.toFloat() * bw * d, line.beamMaskEllipse.h.toFloat() * bh * d)
        }
        drawLayer(innerOuter)

        // z 2 — stroke.
        strokeContent.colorFilter = filter
        strokeContent.record {
            for (i in strokeBlobs.indices.reversed()) drawBlob(strokeBlobs[i], beamX, bw, bh)
            highlightGradient.draw(this, beamX, h + highlight.yOffset.toFloat() * d, highlight.w.toFloat() * bw * d, highlight.h.toFloat() * bh * d)
        }
        strokeOuter.compositingStrategy = CompositingStrategy.Offscreen
        strokeOuter.alpha = (opacity * preset.strokeOpacity.toFloat()).coerceIn(0f, 1f)
        strokeOuter.record {
            clipPath(ring) { drawLayer(strokeContent) }
            beamWindow.drawMask(this, beamX, h, line.beamMaskEllipse.w.toFloat() * bw * d, line.beamMaskEllipse.h.toFloat() * bh * d)
        }
        drawLayer(strokeOuter)

        // z 3 — bloom: blur, then the hue/brightness/saturate matrix, then the window mask.
        bloomContent.renderEffect = gaussianBlur(bloomBlurPx * d)
        bloomContent.colorFilter = bloomFilter
        bloomContent.record {
            clipPath(bloomBackground) {
                for (i in bloomGradients.indices.reversed()) {
                    val (g, gradient) = bloomGradients[i]
                    val cx = g.xPct?.let { (it / 100).toFloat() * w } ?: beamX
                    gradient.draw(
                        this,
                        cx,
                        h + g.yOffPx.toFloat() * d,
                        g.w.base.toFloat() * multiplier(g.w.mult, bw, bh, sp, sp2) * d,
                        g.h.base.toFloat() * multiplier(g.h.mult, bw, bh, sp, sp2) * d,
                    )
                }
            }
        }
        bloomOuter.compositingStrategy = CompositingStrategy.Offscreen
        bloomOuter.alpha = (opacity * preset.bloomOpacity.toFloat()).coerceIn(0f, 1f)
        bloomOuter.record {
            drawLayer(bloomContent)
            bloomWindow.drawMask(this, beamX, h, line.bloomMaskEllipse.w.toFloat() * bw * d, line.bloomMaskEllipse.h.toFloat() * bh * d)
        }
        drawLayer(bloomOuter)
    }

    private fun DrawScope.drawBlob(b: Blob, beamX: Float, bw: Float, bh: Float) {
        b.gradient.draw(this, beamX + b.dx * d, h + b.dy * d, b.sizeW * bw * d, b.sizeH * bh * d)
    }

    private fun multiplier(kind: String, bw: Float, bh: Float, sp: Float, sp2: Float): Float = when (kind) {
        "spike" -> sp
        "spike2" -> sp2
        "inv-spike" -> 2f - sp
        "inv-spike2" -> 2f - sp2
        "w" -> bw
        "h" -> bh
        else -> 1f
    }

    private fun BeamSpec.LineBlob.toBlob() = Blob(
        EllipseGradient.fade(parseCssColor(color)),
        sizeW.toFloat(), sizeH.toFloat(), offsetX.toFloat(), offsetY.toFloat(),
    )

    private fun track(table: List<List<Double>>, timing: CubicBezier) = Keyframes(
        DoubleArray(table.size) { table[it][0] / 100 },
        DoubleArray(table.size) { table[it][1] },
        timing,
    )
}
