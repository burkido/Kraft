package com.burkido.kraft.effects.image

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** The scheduler's phases (`CyclePhase`). */
enum class ImagePhase { Idle, Reveal, Visible, Hide }

/**
 * The reveal scheduler (`createCycle`): shader for a random pause, reveal a random image from the
 * pool (never the previous one), hold it, fade back — and the manual reveal / hide / regenerate
 * passes the imperative handle drives. Timers run on [scope]; [now] reads the frame clock the
 * overlay animates on.
 */
internal class ImageCycle(
    private val scope: CoroutineScope,
    private val controller: ImageController,
    private val now: () -> Double,
) {
    var images: List<RevealImage> = emptyList()
        set(value) {
            field = value
            lastIdx = -1
        }
    var delayRange: ClosedFloatingPointRange<Float> = 2f..4f
    var holdRange: IntRange = 2000..2000
    var fadeOutMs: Int = 300
    var initialDelayMs: Long? = null
    var onPhase: (ImagePhase) -> Unit = {}

    var phase by mutableStateOf(ImagePhase.Idle)
        private set

    private var timer: Job? = null
    private var lastIdx = -1
    private var running = false
    private var paused = false
    private var activeReschedule = false
    private var manualHold = false

    private fun emit(p: ImagePhase) {
        phase = p
        onPhase(p)
    }

    private fun clearTimer() {
        timer?.cancel()
        timer = null
    }

    private fun after(ms: Long, block: () -> Unit) {
        clearTimer()
        timer = scope.launch {
            delay(ms)
            timer = null
            block()
        }
    }

    private fun pickHoldMs(): Long {
        val lo = max(0, min(holdRange.first, holdRange.last))
        val hi = max(0, max(holdRange.first, holdRange.last))
        return (lo + Random.nextDouble() * (hi - lo)).toLong()
    }

    private fun scheduleIdle(initialMs: Long? = null) {
        if (!running || paused) return
        emit(ImagePhase.Idle)
        val lo = delayRange.start
        val span = max(0f, delayRange.endInclusive - lo)
        val ms = initialMs ?: ((lo + Random.nextFloat() * span) * 1000).toLong()
        after(ms) { runReveal(reschedule = true, manual = false) }
    }

    private fun performHide() {
        if (paused) return
        emit(ImagePhase.Hide)
        controller.startHide(now(), fadeOutMs.toDouble())
        after(fadeOutMs.toLong()) {
            if (paused) return@after
            if (activeReschedule) {
                scheduleIdle()
            } else {
                running = false
                emit(ImagePhase.Idle)
            }
        }
    }

    private fun pick(): RevealImage? {
        val pool = images
        if (pool.isEmpty()) return null
        if (pool.size == 1) { lastIdx = 0; return pool[0] }
        var idx: Int
        do { idx = Random.nextInt(pool.size) } while (idx == lastIdx)
        lastIdx = idx
        return pool[idx]
    }

    private fun runReveal(reschedule: Boolean, manual: Boolean) {
        if (!running || paused) return
        val img = pick()
        if (img == null) {
            if (reschedule) scheduleIdle(500) else running = false
            return
        }
        activeReschedule = reschedule
        manualHold = manual
        emit(ImagePhase.Reveal)
        controller.startReveal(img, now()) {
            if (!running || paused) return@startReveal
            emit(ImagePhase.Visible)
            clearTimer()
            if (!manual) after(pickHoldMs()) { if (running && !paused) performHide() }
        }
    }

    private fun triggerOnceImpl(manual: Boolean) {
        if (paused) return
        if (phase == ImagePhase.Reveal || phase == ImagePhase.Visible || phase == ImagePhase.Hide) return
        val wasRunning = running
        clearTimer()
        if (!wasRunning) running = true
        runReveal(reschedule = wasRunning, manual = manual)
    }

    fun start() {
        if (running) return
        running = true
        paused = false
        scheduleIdle(initialDelayMs ?: (Random.nextDouble() * 1500).toLong())
    }

    fun stop() {
        running = false
        clearTimer()
        controller.clear()
        phase = ImagePhase.Idle
    }

    /** One reveal pass now; [manual] holds the image until [triggerHide]. */
    fun triggerOnce(manual: Boolean) = triggerOnceImpl(manual)

    fun triggerHide() {
        if (paused) return
        if (phase != ImagePhase.Reveal && phase != ImagePhase.Visible) return
        performHide()
    }

    /** Dissolves the shown image into the churn; after [autoRevealAfterMs] the next one resolves in. */
    fun triggerBoil(autoRevealAfterMs: Long?) {
        if (paused) return
        if (phase != ImagePhase.Reveal && phase != ImagePhase.Visible) return
        clearTimer()
        controller.startBoil(now())
        running = false
        emit(ImagePhase.Idle)
        if (autoRevealAfterMs != null) after(max(0L, autoRevealAfterMs)) { triggerOnceImpl(manual = true) }
    }

    fun setPaused(p: Boolean) {
        if (paused == p) return
        paused = p
        if (paused) {
            clearTimer()
        } else if (running) {
            when (phase) {
                ImagePhase.Visible -> if (!manualHold) after(min(pickHoldMs(), 500L)) { performHide() }
                ImagePhase.Hide -> after(fadeOutMs.toLong()) { scheduleIdle() }
                else -> scheduleIdle()
            }
        }
    }

    fun dispose() {
        running = false
        clearTimer()
    }
}
