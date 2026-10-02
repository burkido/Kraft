package com.burkido.kraft.film

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Easing curves on 0..1. */
object Ease {
    fun linear(x: Float) = x
    fun smooth(x: Float) = x * x * (3 - 2 * x)
    fun sineInOut(x: Float) = (-(cos(PI * x) - 1) / 2).toFloat()
    fun cubicOut(x: Float) = 1 - (1 - x).pow(3)
    fun cubicInOut(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun quintOut(x: Float) = 1 - (1 - x).pow(5)
    fun expoOut(x: Float) = if (x >= 1f) 1f else 1 - 2f.pow(-10 * x)
    fun expoIn(x: Float) = if (x <= 0f) 0f else 2f.pow(10 * x - 10)
    fun expoInOut(x: Float) = when {
        x <= 0f -> 0f
        x >= 1f -> 1f
        x < 0.5f -> 2f.pow(20 * x - 10) / 2
        else -> (2 - 2f.pow(-20 * x + 10)) / 2
    }
    fun backOut(x: Float, s: Float = 1.4f): Float {
        val c3 = s + 1
        return 1 + c3 * (x - 1).pow(3) + s * (x - 1).pow(2)
    }
}

/** Progress of [t] through [start, start + dur], eased, clamped to 0..1. */
fun ramp(t: Double, start: Double, dur: Double, ease: (Float) -> Float = Ease::expoOut): Float {
    val x = ((t - start) / dur).coerceIn(0.0, 1.0).toFloat()
    return ease(x)
}

/** Fades in over [inDur] from [start] and out over [outDur] ending at [end]. */
fun window(t: Double, start: Double, end: Double, inDur: Double = 0.5, outDur: Double = 0.5, ease: (Float) -> Float = Ease::sineInOut): Float =
    minOf(ramp(t, start, inDur, ease), 1f - ramp(t, end - outDur, outDur, ease))

fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** Piecewise keyframes: (time, value) pairs, eased between neighbours. */
fun keys(t: Double, vararg frames: Pair<Double, Float>, ease: (Float) -> Float = Ease::cubicInOut): Float {
    if (t <= frames.first().first) return frames.first().second
    for (i in 1 until frames.size) {
        val (t1, v1) = frames[i]
        val (t0, v0) = frames[i - 1]
        if (t <= t1) return lerp(v0, v1, ease(((t - t0) / (t1 - t0)).toFloat()))
    }
    return frames.last().second
}

/** Slow organic drift, for handheld-like camera float. */
fun drift(t: Double, amp: Float, period: Double, phase: Double = 0.0): Float =
    (sin(2 * PI * (t / period + phase)) * amp).toFloat()
