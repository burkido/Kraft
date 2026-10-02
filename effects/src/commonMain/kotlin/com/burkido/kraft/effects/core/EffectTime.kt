package com.burkido.kraft.effects.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos

/**
 * The epoch every effect clock shares: the first frame any effect sees is t = 0.
 *
 * The web libraries all read `performance.now()`, so two identical orbs mounted at different
 * moments still move in phase. Sharing one epoch keeps that property.
 */
object EffectClock {
    private const val NO_EPOCH = Long.MIN_VALUE
    private var epochNanos = NO_EPOCH

    /** Seconds since the epoch at the most recent frame any effect observed. */
    var latestSeconds: Double = 0.0
        private set

    internal fun secondsAt(frameTimeNanos: Long): Double {
        if (epochNanos == NO_EPOCH) epochNanos = frameTimeNanos
        latestSeconds = (frameTimeNanos - epochNanos) / 1e9
        return latestSeconds
    }

    /** Makes the next observed frame t = 0 again. Tests call this for deterministic clocks. */
    fun reset() {
        epochNanos = NO_EPOCH
        latestSeconds = 0.0
    }
}

/**
 * Pins every effect clock below it to a fixed engine time in seconds (already including any
 * speed factor). Used for snapshots and parity captures; `null` lets clocks run.
 */
val LocalEffectFrozenTime = staticCompositionLocalOf<Double?> { null }

/** An effect's time in seconds. Read [seconds] only inside draw or layout lambdas. */
@Stable
class EffectTime internal constructor(initialSeconds: Double) {
    private val state = mutableDoubleStateOf(initialSeconds)

    val seconds: Double get() = state.doubleValue

    internal fun publish(seconds: Double) {
        state.doubleValue = seconds
    }
}

/**
 * A frame-driven clock on the shared [EffectClock] epoch.
 *
 * Time advances by `dt · speed`, so speed changes never jump, and it holds still while
 * [running] is false (off-screen, backgrounded, paused, reduced motion) and resumes from where it
 * stopped. [frameRate] > 0 publishes at most that many updates per second, matching the web
 * engines that deliberately tick slower than the display (the image mosaic at 10 fps, the metal
 * sheet at 15 fps).
 */
@Composable
fun rememberEffectTime(
    speed: Double = 1.0,
    running: Boolean = true,
    frameRate: Double = 0.0,
): EffectTime {
    val frozen = LocalEffectFrozenTime.current
    val time = remember { EffectTime(frozen ?: EffectClock.latestSeconds * speed) }
    if (frozen != null) {
        SideEffect { time.publish(frozen) }
        return time
    }

    val currentSpeed by rememberUpdatedState(speed)
    LaunchedEffect(time, running, frameRate) {
        if (!running) return@LaunchedEffect
        val minInterval = if (frameRate > 0.0) (1e9 / frameRate).toLong() - 1_000_000L else 0L
        var seconds = time.seconds
        var lastFrame = Long.MIN_VALUE
        var lastPublish = Long.MIN_VALUE
        var fresh = seconds == 0.0
        while (true) {
            withFrameNanos { now ->
                val global = EffectClock.secondsAt(now)
                if (fresh) {
                    seconds = global * currentSpeed
                    fresh = false
                } else if (lastFrame != Long.MIN_VALUE) {
                    seconds += (now - lastFrame) / 1e9 * currentSpeed
                }
                lastFrame = now
                if (lastPublish == Long.MIN_VALUE || now - lastPublish >= minInterval) {
                    lastPublish = now
                    time.publish(seconds)
                }
            }
        }
    }
    return time
}
