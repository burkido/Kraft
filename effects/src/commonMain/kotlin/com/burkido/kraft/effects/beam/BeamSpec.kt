package com.burkido.kraft.effects.beam

import androidx.compose.ui.graphics.Color
import com.burkido.kraft.effects.core.parseCssColor
import com.burkido.kraft.effects.resources.Res
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The platform-neutral tuning data border-beam's ports share (`spec/beam-spec.json`, spec 1.0.0,
 * extracted from border-beam 1.3.0). Colours, positions and sizes stay CSS strings in the JSON and
 * are parsed once, on load.
 */
@Serializable
class BeamSpec(
    val specVersion: String,
    val defaults: Defaults,
    val sizePresets: Map<String, SizePreset>,
    val sizeThemePresets: Map<String, Map<String, ThemePreset>>,
    val palettes: Palettes,
    val rotate: Rotate,
    val line: Line,
    val pulse: Pulse,
) {
    @Serializable
    class Defaults(
        val hueRange: Double,
        val lineHueRangeCap: Double,
        val brightnessFallback: Double,
        val fadeInSeconds: Double,
        val fadeOutSeconds: Double,
        val rotateHueShiftPeriod: Double,
        val lineBloomHueShiftPeriod: Double,
        val lineBloomHueRangeBonus: Double,
        val monoOpacityMultiplier: Double,
        val duration: Durations,
    )

    @Serializable
    class Durations(val line: Double, val pulse: Double, val rotate: Double)

    @Serializable
    class SizePreset(val borderRadius: Double, val borderWidth: Double)

    @Serializable
    class ThemePreset(
        val strokeOpacity: Double,
        val innerOpacity: Double,
        val bloomOpacity: Double,
        val innerShadow: String,
        val saturation: Double,
        val brightness: Double? = null,
        val hairlineOpacity: Double? = null,
    )

    @Serializable
    class Palettes(
        val border: Map<String, BorderPalette>,
        val small: Map<String, SmallPalette>,
        /** variant → theme → blobs riding the line's travelling position. */
        val line: Map<String, Map<String, List<LineBlob>>>,
        /** variant → the line's inner-glow blobs (theme-independent). */
        val lineInner: Map<String, List<LineBlob>>,
    )

    /** A line blob: `radial-gradient(ellipse calc(W·w) calc(H·h) at calc(x·100% + dx) calc(100% + dy), color, transparent)`. */
    @Serializable
    class LineBlob(val color: String, val sizeW: Double, val sizeH: Double, val offsetX: Double, val offsetY: Double)

    @Serializable
    class Line(
        val keyframes: LineKeyframes,
        val beamMaskEllipse: MaskEllipse,
        val bloomMaskEllipse: MaskEllipse,
        val whiteHighlight: Map<String, Highlight>,
        /** variant → theme → the bloom's spike and glow gradients. */
        val bloomGradients: Map<String, Map<String, List<BloomGradient>>>,
        val monoBloomExtraBlurPx: Double,
        val bloomBlurPx: Double,
    )

    @Serializable
    class LineKeyframes(
        val travel: Travel,
        val edgeFade: List<List<Double>>,
        val breathe: List<List<Double>>,
        val spike: List<List<Double>>,
        val spike2: List<List<Double>>,
        val durationScale: Map<String, Double>,
    )

    @Serializable
    class Travel(val x: List<List<Double>>, val w: List<List<Double>>)

    /** An elliptical mask of base size (w, h) × (beam w, beam h): 1 → [softStop] → 0. */
    @Serializable
    class MaskEllipse(val w: Double, val h: Double, val softStop: List<Double>)

    @Serializable
    class Highlight(val w: Double, val h: Double, val yOffset: Double, val stops: List<List<Double>>, val onBlack: Boolean = false)

    @Serializable
    class BloomGradient(
        /** Horizontal position in percent; `null` follows the beam. */
        val xPct: Double? = null,
        val yOffPx: Double,
        val w: SizeTerm,
        val h: SizeTerm,
        val stops: List<RgbaStop>,
    )

    /** `base` px × a live multiplier: spike, spike2, inv-spike (2 − spike), inv-spike2, w or h. */
    @Serializable
    class SizeTerm(val base: Double, val mult: String)

    @Serializable
    class RgbaStop(val r: Double, val g: Double, val b: Double, val a: Double, val pos: Double)

    @Serializable
    class Pulse(
        val ringMap: List<RingSlot>,
        val innerSizes: List<List<Double>>,
        val innerBloom: List<PulseBlob>,
        val outerCore: List<PulseBlob>,
        val outerBloom: List<PulseBlob>,
        val innerCornerAccent: CornerAccent,
        val inner: Map<String, PulseTheme>,
        val outside: Map<String, PulseTheme>,
        val huePeriod: Map<String, Double>,
        val outsideConstants: OutsideConstants,
        val innerBloomBlurPx: Double,
    )

    @Serializable
    class RingSlot(val region: Int, val quad: String)

    /** A fixed pulse gradient: palette colour [ci], size (w, h), position override (x, y) in %. */
    @Serializable
    class PulseBlob(val ci: Int, val region: Int, val quad: String, val w: Double, val h: Double, val x: String? = null, val y: String? = null)

    @Serializable
    class CornerAccent(val sizePx: Double, val alpha: Map<String, Double>, val fadeStop: Double)

    @Serializable
    class PulseTheme(val oscillators: List<Oscillator>, val frozenBloomAlpha: Double)

    /** `a + (b − a)·(1 − cos 2π(t − delay)/period)/2` — the shared pulse driver's ping-pong. */
    @Serializable
    class Oscillator(val prop: String, val a: Double, val b: Double, val period: Double, val delay: Double)

    @Serializable
    class OutsideConstants(
        val glowScale: Map<String, Double>,
        val glowBlurPx: Map<String, Double>,
        val bloomBlurPx: Map<String, Double>,
        val coreInsetPx: Double,
        val bloomInsetPx: Double,
        val referenceSize: Map<String, Double>,
        val scaleClamp: Map<String, Double>,
    )

    @Serializable
    class BorderPalette(val border: List<CssBlob>)

    @Serializable
    class SmallPalette(val border: List<CssBlob>, val inner: List<CssBlob>)

    /** One `radial-gradient(ellipse <size> at <pos>, <color>, transparent)` layer. */
    @Serializable
    class CssBlob(val color: String, val pos: String, val size: String)

    @Serializable
    class Rotate(
        val whiteGradientStops: Map<String, List<List<Double>>>,
        val bloomGradientStops: Map<String, List<List<Double>>>,
        val beamMaskStops: List<List<Double>>,
        val smallMaskStops: List<List<Double>>,
        val innerGradientDerivation: InnerDerivation,
        val innerEdgeMaskPx: Double,
        val innerShadowBlur: Map<String, Double>,
        val bloomBlurPx: Double,
    )

    @Serializable
    class InnerDerivation(
        val sizeScale: Double,
        val alpha: Double,
        @SerialName("monoAlpha") val monoAlpha: Double,
    )

    companion object {
        private var loaded: BeamSpec? = null

        /** The spec, once [load] has run. Effects render nothing until then. */
        val current: BeamSpec? get() = loaded

        /** Reads and decodes the bundled spec; later calls return the cached instance. */
        suspend fun load(): BeamSpec {
            loaded?.let { return it }
            val bytes = Res.readBytes("files/beam-spec.json")
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString(serializer(), bytes.decodeToString()).also { loaded = it }
        }
    }
}

/** A parsed radial blob: centre as fractions of the box, radii in CSS px. */
class Blob(
    val color: Color,
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
)

internal fun BeamSpec.CssBlob.parse(): Blob {
    val (px, py) = pos.trim().split(Regex("\\s+")).map { it.removeSuffix("%").toFloat() / 100f }
    val (rx, ry) = size.trim().split(Regex("\\s+")).map { it.removeSuffix("px").toFloat() }
    return Blob(parseCssColor(color), px, py, rx, ry)
}

/** `[[percent, alpha], …]` → colour stops of [base] at those alphas, for sweep/linear brushes. */
internal fun List<List<Double>>.toStops(base: Color): Array<Pair<Float, Color>> =
    Array(size) { i ->
        val (percent, alpha) = this[i]
        (percent / 100.0).toFloat() to base.copy(alpha = alpha.toFloat())
    }
