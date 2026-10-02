package com.burkido.kraft.effects.voice

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import androidx.compose.ui.graphics.layer.drawLayer
import com.burkido.kraft.effects.beam.EllipseGradient
import com.burkido.kraft.effects.beam.recordEdgeFadeMask
import com.burkido.kraft.effects.beam.recordInsetShadow
import com.burkido.kraft.effects.beam.ringPath
import com.burkido.kraft.effects.beam.roundRectPath
import com.burkido.kraft.effects.core.CssFilter
import com.burkido.kraft.effects.core.gaussianBlur
import kotlin.math.max
import kotlin.math.min

/**
 * Paints one frame of the glow the way `generateVoiceBeamCSS` has the browser paint it, for an
 * element of the cache scope's size: the inner light (z 1), the edge stroke (z 2), the blurred
 * bloom (z 3), the band canvas (z 4) and, on light, the epicentre wash — all inside the
 * element's rounded box. Static pieces (paths, gradients, the inset shadow and the corner fades)
 * are built once per size; the moving ones are re-recorded each frame.
 */
internal class VoicePainter(scope: CacheDrawScope, setup: VoiceSetup) {
    private val spec = setup.paint
    private val d = scope.density
    private val width = scope.size.width
    private val height = scope.size.height
    private val radius = (spec.radius * d).toFloat()
    private val hairline = d
    private val full = Size(width, height)

    private val clipBox = roundRectPath(0f, 0f, width, height, radius)
    private val clipInner = roundRectPath(0f, 0f, width, height, radius - hairline)
    private val ring = ringPath(width, height, radius - hairline, hairline)

    private val strokeLayer = scope.obtainGraphicsLayer()
    private val innerLayer = scope.obtainGraphicsLayer()
    private val insetShadow = scope.obtainGraphicsLayer()
    private val edgeFade = scope.obtainGraphicsLayer()
    private val bloomLayer = scope.obtainGraphicsLayer()
    private val bloomBlurLayer = scope.obtainGraphicsLayer()
    private val bandLayer = scope.obtainGraphicsLayer()
    private val haloLayer = scope.obtainGraphicsLayer()
    private val ridgeLayer = scope.obtainGraphicsLayer()
    private val coreLayer = scope.obtainGraphicsLayer()

    private val strokeLobes = lobeGradients(spec.colors, 1f, spec.fade)
    private val innerLobes = lobeGradients(spec.colors, 0.46f, spec.fade)
    private val bloomLobes = lobeGradients(spec.colors, if (spec.dark) 0.9f else 0.7f, min(95.0, spec.fade + 2))

    // Rounded lobe radii (`Math.round(lobe.w * sw)`), per layer.
    private val strokeRx = VoiceLobes.map { jsRound(it.w * spec.strokeW) }
    private val strokeRy = VoiceLobes.map { jsRound(it.h * spec.strokeH) }
    private val innerRx = VoiceLobes.map { jsRound(it.w * spec.innerW) }
    private val innerRy = VoiceLobes.map { jsRound(it.h * spec.innerH) }
    private val bloomRx = VoiceLobes.map { jsRound(it.w * spec.bloomW) }
    private val bloomRy = VoiceLobes.map { jsRound(it.h * spec.bloomH) }

    /** The hot core at the centre of the edge — white on dark, a dark spot on light. */
    private val highlight = if (spec.dark) {
        EllipseGradient(arrayOf(0f to Color.White.copy(alpha = 0.45f), 0.3f to Color.White.copy(alpha = 0.14f), 0.65f to Color.White.copy(alpha = 0f)))
    } else {
        EllipseGradient(arrayOf(0f to Color.Black.copy(alpha = 0.55f), 0.35f to Color.Black.copy(alpha = 0.22f), 0.7f to Color.Black.copy(alpha = 0f)))
    }
    private val highlightRx = tenth((if (spec.dark) 30 else 40) * spec.coreSize)
    private val highlightRy = tenth(30 * spec.coreSize)

    // The ceiling ellipses every layer is masked to (`edgeMask(w, h, mid, tail)`).
    private val strokeMask = EllipseGradient.softMask(0.45f, 0.5f)
    private val innerMask = EllipseGradient(
        arrayOf(0f to Color.White, 0.45f to Color.White.copy(alpha = 0.5f), 0.85f to Color.White.copy(alpha = 0.3f), 1f to Color.White.copy(alpha = 0f)),
    )
    private val bloomMask = EllipseGradient.softMask(0.35f, 0.5f)
    private val maskHalf170 = tenth(170 * spec.rangeWidth)
    private val maskHeight64 = tenth(64 * spec.rangeHeight)
    private val maskHalf200 = tenth(200 * spec.rangeWidth)
    private val maskHeight130 = tenth(130 * spec.rangeHeight)

    // The epicentre (light theme): past 1 the solid core widens and the wash grows.
    private val coreBoost = (spec.coreLight - 1).coerceIn(0.0, 2.0)
    private val coreWash: EllipseGradient? = if (spec.coreLight > 0) {
        val b1 = min(1.0, coreBoost)
        val b2 = max(0.0, coreBoost - 1)
        val solid = (tenth(45 * b1 + 27 * b2) / 100).toFloat()
        val midStop = (tenth(40 + 25 * b1 + 15 * b2) / 100).toFloat()
        val midAlpha = min(1.0, 0.55 + 0.35 * b1 + 0.1 * b2).toFloat()
        val endStop = (tenth(72 + 14 * b1 + 8 * b2) / 100).toFloat()
        EllipseGradient(arrayOf(0f to Color.White, solid to Color.White, midStop to Color.White.copy(alpha = midAlpha), endStop to Color.White.copy(alpha = 0f)))
    } else {
        null
    }
    private val coreGrow = 1 + 0.3 * coreBoost

    private val bandPath = Path()
    private val belowPath = Path()

    private var staticRecorded = false

    /**
     * The soft layers (inner light, bloom, epicentre) raster at half size and are scaled back up
     * — the web's `data-voice-halfres`: the same picture for blurred gradients at a quarter of the
     * pixels and blur work. The band's backing store is capped at 2×, as its canvas is.
     */
    private val soft = 0.5f
    private val bandScale = min(1f, 2f / d)

    init {
        bloomBlurLayer.renderEffect = gaussianBlur((spec.bloomBlur * d * soft).toFloat())
        val ridgeBlur = 3.5 * spec.bandWidth / 2
        ridgeLayer.renderEffect = gaussianBlur((ridgeBlur * d * bandScale).toFloat())
        haloLayer.renderEffect = gaussianBlur((ridgeBlur * 3 * d * bandScale).toFloat())
        coreLayer.renderEffect = gaussianBlur((spec.coreBlur * d * soft).toFloat())
    }

    /** Records [layer] at [q] of the element's resolution; it draws back scaled up by 1 / [q]. */
    private fun DrawScope.recordScaled(layer: GraphicsLayer, q: Float, block: DrawScope.() -> Unit) {
        layer.pivotOffset = Offset.Zero
        layer.scaleX = 1f / q
        layer.scaleY = 1f / q
        layer.record(IntSize(ceil(width * q).toInt(), ceil(height * q).toInt())) {
            scale(q, q, pivot = Offset.Zero) { block() }
        }
    }

    fun draw(scope: DrawScope, f: VoiceFrame, fade: Float): Unit = with(scope) {
        if (width <= 0f || height <= 0f) return
        if (!staticRecorded) {
            recordInsetShadow(insetShadow, width, height, radius, (spec.insetBlur * d).toFloat(), hairline, spec.innerShadow)
            recordEdgeFadeMask(edgeFade, width, height, (spec.cornerFade * d).toFloat())
            staticRecorded = true
        }
        val filter = CssFilter.colorFilter(CssFilter.hueBrightnessSaturate(spec.hueBase + f.hue, spec.brightness, spec.saturation))
        val strength = spec.strength
        val beamX = (width / 2 + f.cx * f.w * d).toFloat()
        val maskY = (height + f.cy * d).toFloat()

        clipPath(clipBox) {
            // ── Inner light (z 1) ──────────────────────────────────────────
            innerLayer.alpha = opacity(fade * f.glow * spec.innerOpacity * strength)
            innerLayer.colorFilter = filter
            recordScaled(innerLayer, soft) {
                clipPath(clipBox) {
                    drawLobes(innerLobes, innerRx, innerRy, 0.0, f)
                    drawLayer(insetShadow)
                }
                innerMask.drawMask(this, beamX, maskY, (maskHalf170 * f.w * f.maskWidth * d).toFloat(), ((maskHeight64 * f.h + f.lift) * d).toFloat(), cover = full)
                drawLayer(edgeFade)
            }
            drawLayer(innerLayer)

            // ── Edge stroke (z 2): the palette in the 1 dp ring, hot core on top ─
            strokeLayer.alpha = opacity(fade * f.glow * spec.strokeOpacity * strength)
            strokeLayer.colorFilter = filter
            strokeLayer.record {
                clipPath(ring) {
                    drawLobes(strokeLobes, strokeRx, strokeRy, 2.0, f)
                    highlight.draw(this, beamX, ((height + (2 + f.cy) * d)).toFloat(), (highlightRx * f.w * d).toFloat(), (highlightRy * f.h * d).toFloat())
                }
                strokeMask.drawMask(this, beamX, maskY, (maskHalf170 * f.w * f.maskWidth * d).toFloat(), ((maskHeight64 * f.h + f.lift) * d).toFloat(), cover = full)
            }
            drawLayer(strokeLayer)

            // ── Bloom (z 3): blurred, then clipped, then masked ─────────────
            bloomBlurLayer.colorFilter = filter
            bloomBlurLayer.record(IntSize(ceil(width * soft).toInt(), ceil(height * soft).toInt())) {
                scale(soft, soft, pivot = Offset.Zero) {
                    clipPath(clipInner) { drawLobes(bloomLobes, bloomRx, bloomRy, 0.0, f) }
                }
            }
            bloomLayer.alpha = opacity(fade * f.glow * spec.bloomOpacity * strength)
            recordScaled(bloomLayer, soft) {
                // The blurred layer is already at half size: undo the recording's scale for it.
                clipPath(clipBox) { scale(1f / soft, 1f / soft, pivot = Offset.Zero) { drawLayer(bloomBlurLayer) } }
                bloomMask.drawMask(this, beamX, maskY, (maskHalf200 * f.w * f.maskWidth * d).toFloat(), ((maskHeight130 * f.h + f.lift) * d).toFloat(), cover = full)
            }
            drawLayer(bloomLayer)

            // ── Band (z 4) and the epicentre over it ────────────────────────
            if (f.hasBand) {
                traceBand(f)
                drawBand(f, fade, filter)
                drawCore(f, fade, beamX, maskY)
            }
        }
    }

    private fun DrawScope.drawLobes(lobes: List<EllipseGradient>, rx: List<Double>, ry: List<Double>, y: Double, f: VoiceFrame) {
        // Painted bottom-up: the first background (the centre lobe) ends on top.
        for (i in VoiceLobes.indices.reversed()) {
            val cx = width / 2 + (f.cx + f.lobeX[i]) * f.w * d
            val cy = height + (y + f.lobeY[i]) * d
            lobes[i].draw(this, cx.toFloat(), cy.toFloat(), (rx[i] * f.w * d).toFloat(), (ry[i] * f.h * f.lobeL[i] * d).toFloat())
        }
    }

    private fun traceBand(f: VoiceFrame) {
        bandPath.rewind()
        belowPath.rewind()
        val pts = f.band
        belowPath.moveTo(0f, height)
        for (i in 0..BandSamples) {
            val x = pts[2 * i] * d
            val y = pts[2 * i + 1] * d
            if (i == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
            belowPath.lineTo(x, y)
        }
        belowPath.lineTo(width, height)
        belowPath.close()
    }

    /** `drawBand`: a wide soft halo, then four stacked ridges (red above, green, blue below, core). */
    private fun DrawScope.drawBand(f: VoiceFrame, fade: Float, filter: ColorFilter) {
        val alpha = min(1.0, 0.6 * spec.bandStrength * f.bendA)
        if (alpha < 0.005 || spec.bandWidth <= 0) return
        val bw = spec.bandWidth * (1 + 0.35 * f.level)
        val split = spec.bandAberration * (0.35 + 0.65 * f.level)
        val dy = ((4 + 12 * split) * spec.scale * d).toFloat()
        val dx = (4 * split * spec.scale * d).toFloat()
        val base = (if (spec.dark) 0.42 else 0.4) * alpha
        val thickness = 14 * bw * d
        val x0 = f.band[0] * d
        val x1 = f.band[2 * BandSamples] * d
        val fadeAt = if (spec.bandTail > 0) 0.015f else 0.18f
        val colors = spec.bandColors

        fun ends(color: Color, a: Double): Brush {
            val c = color.copy(alpha = a.toFloat().coerceIn(0f, 1f))
            return Brush.horizontalGradient(
                0f to c.copy(alpha = 0f), fadeAt to c, 1f - fadeAt to c, 1f to c.copy(alpha = 0f),
                startX = x0, endX = max(x1, x0 + 1f),
            )
        }

        recordScaled(haloLayer, bandScale) {
            drawPath(bandPath, ends(colors.core, base * 0.3), style = Stroke((thickness * 2.2).toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        recordScaled(ridgeLayer, bandScale) {
            val ridges = arrayOf(
                Ridge(colors.above, 1.0, dx, -dy),
                Ridge(colors.mid, 0.55, dx * 0.35f, -dy * 0.35f),
                Ridge(colors.below, 1.0, -dx, dy),
                Ridge(colors.core, 0.9, 0f, 0f),
            )
            for (ridge in ridges) {
                for (k in RampWidth.indices) {
                    val strokeWidth = max(0.6 * d, thickness * RampWidth[k]).toFloat()
                    val brush = ends(ridge.color, base * ridge.alpha * RampAlpha[k])
                    val style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    if (ridge.ox == 0f && ridge.oy == 0f) {
                        drawPath(bandPath, brush, style = style)
                    } else {
                        drawContext.transform.translate(ridge.ox, ridge.oy)
                        drawPath(bandPath, brush, style = style)
                        drawContext.transform.translate(-ridge.ox, -ridge.oy)
                    }
                }
            }
        }
        bandLayer.alpha = opacity(fade * spec.strength)
        bandLayer.colorFilter = filter
        bandLayer.record {
            drawLayer(haloLayer)
            drawLayer(ridgeLayer)
        }
        drawLayer(bandLayer)
    }

    /** The light theme's epicentre: a white wash under the band line, blurred with its cut edge. */
    private fun DrawScope.drawCore(f: VoiceFrame, fade: Float, beamX: Float, maskY: Float) {
        val wash = coreWash ?: return
        val a = fade * min(1.0, f.glow * min(1.0, spec.coreLight) * (1.6 + 1.4 * coreBoost))
        if (a <= 0.0) return
        coreLayer.alpha = opacity(a)
        recordScaled(coreLayer, soft) {
            clipPath(clipInner) {
                clipPath(belowPath) {
                    wash.drawMask(
                        this, beamX, maskY,
                        (tenth(120 * spec.coreLightWidth * coreGrow * spec.scale) * f.w * d).toFloat(),
                        ((tenth(70 * spec.coreLightHeight * coreGrow * spec.scale) * f.h + f.lift) * d).toFloat(),
                        androidx.compose.ui.graphics.BlendMode.SrcOver,
                        cover = full,
                    )
                }
            }
        }
        drawLayer(coreLayer)
    }

    private class Ridge(val color: Color, val alpha: Double, val ox: Float, val oy: Float)

    private companion object {
        val RampWidth = doubleArrayOf(1.0, 0.72, 0.46, 0.22)
        val RampAlpha = doubleArrayOf(0.16, 0.2, 0.26, 0.34)

        fun lobeGradients(colors: List<Color>, alpha: Float, fade: Double): List<EllipseGradient> =
            VoiceLobes.indices.map { i ->
                val c = colors[i % colors.size].copy(alpha = alpha)
                EllipseGradient(arrayOf(0f to c, (fade / 100).toFloat() to c.copy(alpha = 0f)))
            }

        /** CSS clamps a computed opacity to 0…1. */
        fun opacity(v: Double): Float = v.toFloat().coerceIn(0f, 1f)
        fun opacity(v: Float): Float = v.coerceIn(0f, 1f)

        /** The generator's `px()`: rounded to a tenth. */
        fun tenth(v: Double): Double = jsRound(v * 10) / 10
        fun jsRound(x: Double): Double = kotlin.math.floor(x + 0.5)
    }
}
