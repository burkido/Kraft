package com.burkido.kraft.effects.core

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs

/**
 * A CSS `cubic-bezier()` timing function evaluated in double precision, solved the way Chromium's
 * `gfx::CubicBezier` does (Newton's method with a bisection fallback). Drivers that reproduce CSS
 * keyframe animations use this; Compose animations can use [easing].
 */
class CubicBezier(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
) {
    private val cx = 3.0 * x1
    private val bx = 3.0 * (x2 - x1) - cx
    private val ax = 1.0 - cx - bx
    private val cy = 3.0 * y1
    private val by = 3.0 * (y2 - y1) - cy
    private val ay = 1.0 - cy - by

    private fun sampleX(t: Double) = ((ax * t + bx) * t + cx) * t
    private fun sampleY(t: Double) = ((ay * t + by) * t + cy) * t
    private fun sampleDerivativeX(t: Double) = (3.0 * ax * t + 2.0 * bx) * t + cx

    private fun solveX(x: Double): Double {
        var t = x
        for (i in 0 until 8) {
            val err = sampleX(t) - x
            if (abs(err) < EPSILON) return t
            val d = sampleDerivativeX(t)
            if (abs(d) < 1e-6) break
            t -= err / d
        }
        var lo = 0.0
        var hi = 1.0
        t = x
        while (lo < hi) {
            val v = sampleX(t)
            if (abs(v - x) < EPSILON) return t
            if (x > v) lo = t else hi = t
            t = (hi - lo) * 0.5 + lo
            if (hi - lo < EPSILON) break
        }
        return t
    }

    /** Output progress for input progress [x], clamped to [0, 1] like a keyframe segment. */
    fun transform(x: Double): Double = when {
        x <= 0.0 -> 0.0
        x >= 1.0 -> 1.0
        else -> sampleY(solveX(x))
    }

    val easing: Easing get() = CubicBezierEasing(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())

    companion object {
        private const val EPSILON = 1e-7

        val Linear = CubicBezier(0.0, 0.0, 1.0, 1.0)
        val Ease = CubicBezier(0.25, 0.1, 0.25, 1.0)
        val EaseIn = CubicBezier(0.42, 0.0, 1.0, 1.0)
        val EaseOut = CubicBezier(0.0, 0.0, 0.58, 1.0)
        val EaseInOut = CubicBezier(0.42, 0.0, 0.58, 1.0)
    }
}

/**
 * A CSS `@keyframes` track: values at [offsets] (0..1, ascending) with [timing] applied to each
 * segment between keyframes, as `animation-timing-function` does.
 */
class Keyframes(
    private val offsets: DoubleArray,
    private val values: DoubleArray,
    private val timing: CubicBezier = CubicBezier.Ease,
) {
    init {
        require(offsets.size == values.size && offsets.size >= 2) { "Keyframes need matching offsets/values" }
    }

    /** The value at iteration [progress] in 0..1. */
    fun valueAt(progress: Double): Double {
        val p = progress.coerceIn(0.0, 1.0)
        var i = 0
        while (i < offsets.size - 2 && p >= offsets[i + 1]) i++
        val span = offsets[i + 1] - offsets[i]
        val local = if (span <= 0.0) 1.0 else (p - offsets[i]) / span
        val eased = timing.transform(local)
        return values[i] + (values[i + 1] - values[i]) * eased
    }

    /** The value at [seconds] into an infinite animation of [durationSeconds] per iteration. */
    fun valueAtTime(seconds: Double, durationSeconds: Double): Double =
        valueAt(positiveFraction(seconds / durationSeconds))
}

/** `x − floor(x)`: always in [0, 1), also for negative inputs. */
fun positiveFraction(x: Double): Double = x - kotlin.math.floor(x)

/** JavaScript's `Math.round`: halves round up (towards +∞), unlike Kotlin's half-even `round`. */
fun jsRound(x: Double): Double = kotlin.math.floor(x + 0.5)
