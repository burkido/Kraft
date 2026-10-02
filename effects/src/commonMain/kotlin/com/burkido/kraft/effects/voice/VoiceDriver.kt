package com.burkido.kraft.effects.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The resolved per-instance configuration (`VoiceDriverConfig`): type preset, theme, explicit
 * overrides and [VoiceGeometry.scale] already folded in, every value clamped as the web does.
 */
internal class VoiceConfig(
    val sensitivity: Double,
    val threshold: Double,
    val attack: Double,
    val release: Double,
    val idle: Double,
    val breatheDuration: Double,
    val reach: Double,
    val spread: Double,
    val bands: Boolean,
    val flow: Double,
    val lobeSpacing: Double,
    val bend: Double,
    val bandStrength: Double,
    val bandWidth: Double,
    val bandPosition: Double,
    val bandCurve: Double,
    val bandSpread: Double,
    val bandSkew: Double,
    val bandOffset: Double,
    val bandTail: Double,
    val bandTailPosition: Double,
    val bandTailCurve: Double,
    val bandTailOverflow: Double,
    val bandAberration: Double,
    val rangeWidth: Double,
    val rangeHeight: Double,
    val scale: Double,
    val radius: Double,
    val processing: Boolean,
    val processingDuration: Double,
    val processingLevel: Double,
    val processingEase: Double,
    val processingTravel: Double,
    val processingCurve: Double,
    val cornerFollow: Double,
    val hueRange: Double,
    val hueDuration: Double,
    val staticColors: Boolean,
    val reducedMotion: Boolean,
)

/** What one driver step produces: the web's per-frame custom properties plus the band line. */
internal class VoiceFrame {
    var level = 0.0
    var glow = 0.4
    var h = 0.8
    var w = 1.0
    var cx = 0.0
    var cy = 0.0
    var lift = 0.0
    var bendA = 0.0
    var maskWidth = 1.0
    var hue = 0.0
    var morph = 0.0
    var warp = 1.0
    val lobeX = DoubleArray(VoiceLobes.size) { VoiceLobes[it].x }
    val lobeL = DoubleArray(VoiceLobes.size) { 1.0 }
    val lobeY = DoubleArray(VoiceLobes.size)

    /** The band line, x/y interleaved, [BandSamples] + 1 points in element px. */
    val band = FloatArray((BandSamples + 1) * 2)
    var hasBand = false

    fun copyFrom(o: VoiceFrame) {
        level = o.level; glow = o.glow; h = o.h; w = o.w; cx = o.cx; cy = o.cy; lift = o.lift
        bendA = o.bendA; maskWidth = o.maskWidth; hue = o.hue; morph = o.morph; warp = o.warp
        o.lobeX.copyInto(lobeX); o.lobeL.copyInto(lobeL); o.lobeY.copyInto(lobeY)
        o.band.copyInto(band); hasBand = o.hasBand
    }
}

/**
 * The shared driver's per-element step (`voiceDriver.ts`): shape the raw level (gain, gate, soft
 * saturation), follow it with an attack/release envelope, advance the flow, carry the gathered
 * beam across the range while processing, fold in the idle breathing and the hue drift, and
 * trace the band line on the bent ceiling. Doubles throughout, like the JS it ports.
 */
internal class VoiceDriver {
    private var level = 0.0
    private val bands = DoubleArray(3)
    private var phase = 0.0
    private var scanA = 0.0
    private var scanT = 0.0
    private var warp = 1.0

    /** The instance's own clock: it advances only while running, so a pause resumes without a jump. */
    var t = 0.0
        private set

    private val rawBands = DoubleArray(3)

    /**
     * Advances by [dt] seconds. [analysis], when present, is a microphone read (RMS level and the
     * three voice bands, before the sensitivity gain); otherwise [manual] is the 0–1 level.
     */
    fun step(
        c: VoiceConfig,
        dt: Double,
        manual: Double,
        analysis: VoiceAnalysis?,
        cw: Double,
        ch: Double,
        out: VoiceFrame,
    ) {
        t += dt
        val tSec = t

        // ── Raw level and bands from the source ──────────────────────────
        val raw: Double
        if (analysis != null) {
            raw = analysis.rms * BaseGain * c.sensitivity
            for (b in 0 until 3) rawBands[b] = analysis.bands[b] * BandGain * c.sensitivity
        } else {
            raw = manual.coerceIn(0.0, 1.0)
            // No spectrum, so the bands get slow out-of-phase wobbles of their own.
            rawBands[0] = raw
            rawBands[1] = raw * (0.72 + 0.28 * sin(tSec * 9.1))
            rawBands[2] = raw * (0.6 + 0.4 * sin(tSec * 13.7 + 2))
        }

        // ── Shape and follow ─────────────────────────────────────────────
        level = follow(level, shape(raw, c.threshold), dt, c.attack, c.release)
        for (b in 0 until 3) {
            bands[b] = follow(bands[b], shape(rawBands[b], c.threshold * 0.6), dt, c.attack, c.release * 1.15)
        }

        // ── Processing travel ────────────────────────────────────────────
        val span = LobeSpan * c.lobeSpacing
        // A fresh start begins the pass at the centre, heading right.
        if (c.processing && scanA < 0.001 && scanT == 0.0) scanT = max(0.05, c.processingDuration) / 2
        val ease = max(0.05, c.processingEase)
        scanA = follow(scanA, if (c.processing) 1.0 else 0.0, dt, ease * 0.9, ease * 0.8)
        if (c.processing) scanT += dt else if (scanA < 0.001) scanT = 0.0
        val morph = scanA * scanA * (3 - 2 * scanA)
        val travel = span / 2 * c.processingTravel
        val passes = scanT / max(0.05, c.processingDuration)
        val passIndex = floor(passes)
        val u = passes - passIndex
        val k = max(1.0, c.processingCurve)
        val eased = if (u < 0.5) 0.5 * (2 * u).pow(k) else 1 - 0.5 * (2 - 2 * u).pow(k)
        val pass = when {
            c.reducedMotion -> 0.0
            passIndex.toLong() % 2 == 0L -> 2 * eased - 1
            else -> 1 - 2 * eased
        }
        val cx = morph * travel * pass
        val gather = 1 - morph * 0.6
        val maskWidth = 1 - morph * 0.45
        val passWidth = 1 + morph * 0.3 * (1 - pass * pass)

        // ── Idle breathing folded under the voice ────────────────────────
        val breathe = if (c.reducedMotion) 0.5 else 0.5 + 0.5 * sin(TwoPi * tSec / c.breatheDuration)
        val voiced = level + (1 - level) * c.idle * breathe
        val heldT = ((morph - 0.25) / 0.75).coerceIn(0.0, 1.0)
        val held = heldT * heldT * (3 - 2 * heldT)
        val eff = max(voiced, c.processingLevel * held)

        val glow = 0.15 + 0.85 * eff
        val h = 0.5 + c.reach * eff
        val w = (0.85 + c.spread * eff) * passWidth

        // ── Flow: the spectrum slides sideways as the voice comes in ─────
        if (c.flow != 0.0 && !c.reducedMotion) {
            phase = (((phase + c.flow * eff * dt) % span) + span) % span
        }

        // ── Bend: the ceiling humps up at the centre ─────────────────────
        val lift = c.bend * eff
        val bendA = if (c.bend > 0) min(1.0, lift / c.bend) else 0.0

        // Corner arcs: while processing, the beam and its lobes ride the element's corners.
        val beamAbsX = cw / 2 + cx * w
        val lobeReach = 30 * c.scale * w
        val arcRadius = paintedRadius(c.radius, cw, ch)
        val cornerBlend = morph * c.cornerFollow

        warp = follow(warp, if (c.processing) 0.0 else 1.0, dt, WarpInTau, WarpOutTau)

        out.level = level
        out.glow = glow
        out.h = h
        out.w = w
        out.cx = cx
        out.cy = -cornerLift(beamAbsX, cw, arcRadius, lobeReach * 1.4) * cornerBlend
        out.lift = max(0.0, lift)
        out.bendA = bendA
        out.maskWidth = maskWidth
        out.morph = morph
        out.warp = warp

        for (i in VoiceLobes.indices) {
            val lobe = VoiceLobes[i]
            val x = wrapX(lobe.x * c.lobeSpacing + phase, span)
            val bandLift = if (c.bands) 0.6 + 0.7 * bands[lobe.band] else 1.0
            out.lobeX[i] = x * gather
            out.lobeL[i] = bandLift * edgeEnvelope(x, span)
            val lobeAbsX = cw / 2 + (cx + x * gather) * w
            out.lobeY[i] = -cornerLift(lobeAbsX, cw, arcRadius, lobeReach) * cornerBlend
        }

        // ── Hue drift ────────────────────────────────────────────────────
        out.hue = if (c.staticColors || c.reducedMotion || c.hueRange == 0.0) {
            0.0
        } else {
            -c.hueRange + 2 * c.hueRange * (1 - cos(TwoPi * tSec / c.hueDuration)) / 2
        }

        out.hasBand = cw > 0 && ch > 0
        if (out.hasBand) bandPoints(c, out, cw, ch)
    }

    /** `bandPoints`: the band line on the bent ceiling, left to right, in element px. */
    private fun bandPoints(c: VoiceConfig, f: VoiceFrame, cw: Double, ch: Double) {
        val centre = cw / 2 + f.cx * f.w
        val half = CeilingHalfWidth * c.rangeWidth * f.w * f.maskWidth
        // Sit on the ceiling but never leave the element (the clamp shrinks with the scale below 1).
        val apexCap = ch * 0.82 * min(1.0, c.scale)
        val apex = min(apexCap, (CeilingHeight * c.rangeHeight * f.h + f.lift) * c.bandPosition)
        val base = ch - c.bandOffset
        // The corner hooks are the resting look; they are gone by a quarter of the processing morph.
        val tailT = min(1.0, f.morph * 4)
        val tail = c.bandTail * (1 - tailT * tailT * (3 - 2 * tailT))
        val withTail = tail > 0.001
        val over = if (withTail) c.bandTailOverflow else 0.0
        val x0 = if (withTail) -over else centre - half
        val x1 = if (withTail) cw + over else centre + half
        val radius = paintedRadius(c.radius, cw, ch)
        for (i in 0..BandSamples) {
            val x = x0 + (x1 - x0) * i / BandSamples
            val t = ((x - centre) / max(1.0, half)).coerceIn(-1.0, 1.0)
            val edge = (if (x < centre) centre else cw - centre) + over
            val y = bell(t, c.bandCurve, c.bandSpread, c.bandSkew) +
                tailLift(abs(x - centre), edge, tail, c.bandTailPosition, c.bandTailCurve)
            val arc = if (f.morph > 0) cornerLift(x, cw, radius) * f.morph else 0.0
            f.band[2 * i] = x.toFloat()
            f.band[2 * i + 1] = (base - apex * y - arc).toFloat()
        }
    }

    companion object {
        /** Lifts a laptop mic's conversational RMS (~0.03–0.2) into the shaping curve's range. */
        const val BaseGain = 5.0
        const val BandGain = 1.7
        private const val WarpOutTau = 0.06
        private const val WarpInTau = 0.35
        private const val TwoPi = 2 * PI

        /** Ceiling geometry the stylesheet masks to (px at multiplier 1). */
        const val CeilingHalfWidth = 170.0
        const val CeilingHeight = 64.0
    }
}

internal const val BandSamples = 56

/** Wrap a lobe offset into [-span/2, span/2). */
private fun wrapX(x: Double, span: Double): Double {
    val half = span / 2
    return (((x + half) % span) + span) % span - half
}

/** Full at the centre, gone at the wrap edge, so a lobe never pops across. */
private fun edgeEnvelope(x: Double, span: Double): Double {
    val t = x / (span / 2 + 4)
    return max(0.0, 1 - t * t)
}

/** Noise gate, then soft saturation so a shout rounds off instead of clipping. */
private fun shape(raw: Double, threshold: Double): Double {
    if (raw <= threshold) return 0.0
    val t = (raw - threshold) / max(0.001, 1 - threshold)
    return ((1 - exp(-3 * t)) / (1 - exp(-3.0))).coerceIn(0.0, 1.0)
}

/** One-pole follower: [attack] seconds up, [release] seconds down. */
private fun follow(prev: Double, target: Double, dt: Double, attack: Double, release: Double): Double {
    val tau = if (target > prev) attack else release
    val a = 1 - exp(-dt / max(0.001, tau))
    return prev + (target - prev) * a
}

/** The band's bell: `exp(-(|t|/σ)^p)`, normalised to 1 at the centre and 0 at the ends. */
private fun bell(t: Double, p: Double, sigma: Double, skew: Double): Double {
    val side = if (t < 0) 1 - skew else 1 + skew
    val s = max(0.05, sigma * side)
    val v = exp(-(abs(t) / s).pow(p))
    val tail = exp(-(1 / s).pow(p))
    return max(0.0, (v - tail) / (1 - tail))
}

/** The band's rise toward the corners: from [position] of the way out, full [lift] at the edge. */
private fun tailLift(dist: Double, edge: Double, lift: Double, position: Double, curve: Double): Double {
    if (lift <= 0 || edge <= 0) return 0.0
    val start = edge * position.coerceIn(0.0, 0.98)
    if (dist <= start) return 0.0
    val u = min(1.0, (dist - start) / max(1.0, edge - start))
    return lift * u.pow(max(0.5, curve))
}

/** The corner radius CSS actually paints: clamped to half the box. */
internal fun paintedRadius(radius: Double, cw: Double, ch: Double): Double = max(0.0, min(radius, min(cw / 2, ch / 2)))

/** How far the element's outline sits above the bottom edge at [x] (0 on the straight run). */
private fun cornerLift(x: Double, cw: Double, radius: Double, influence: Double = 0.0): Double {
    if (radius <= 0) return 0.0
    val d = min(x, cw - x) - influence
    if (d >= radius) return 0.0
    if (d <= 0) return radius
    val dx = radius - d
    return radius - sqrt(max(0.0, radius * radius - dx * dx))
}
