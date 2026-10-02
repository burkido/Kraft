package com.burkido.kraft.effects.image

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.burkido.kraft.effects.core.PixelSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** The web renderer's 10 fps cap: the presets drift slowly enough that more buys nothing. */
private const val FrameIntervalMs = 100.0

/** `maxDpr`: the mosaic renders at no more than 1.25 px per CSS px, then upscales crisply. */
private const val MaxGlDpr = 1.25f

/** What the reveal overlay shows this frame. */
internal enum class OverlayKind { None, Image, Reveal, Boil }

/**
 * One published frame's compositing: the mosaic's crossfade opacity, the overlay's opacity and
 * content — the smooth [image] at [imageAlpha], then the chunky cells, then (reveal only) the
 * quantised mask cutting it all to the revealed cells.
 */
internal class DrawPlan(
    val shaderAlpha: Float,
    val overlayAlpha: Float,
    val overlay: OverlayKind,
    val image: RevealImage?,
    val imageAlpha: Float,
) {
    companion object {
        val Idle = DrawPlan(1f, 1f, OverlayKind.None, null, 0f)
    }
}

/** A snapshot of everything the worker needs for one frame. */
internal class FrameInput(
    val mode: ImageMode,
    val configVersion: Int,
    val strength: Float,
    val pixelScale: Float,
    val colors: List<Color?>?,
    val cardBgOverride: Color?,
    val iw: Int,
    val ih: Int,
    val dpr: Float,
    val cssW: Int,
    val cssH: Int,
    /** `accumulatedTime`, seconds (the flicker clock reads it unscaled). */
    val time: Double,
    val renderMosaic: Boolean,
    val overlay: OverlayKind,
    val overlaySeq: Int,
    /** Seconds into the reveal. */
    val elapsed: Float,
    val image: RevealImage?,
    val pending: RevealImage?,
    val boil: BoilTimeline?,
    val plan: DrawPlan,
) {
    /** The shader's `t = u_time · u_speed`, in 32-bit floats like the uniform. */
    val shaderTime: Float get() = time.toFloat() * mode.speed
}

/** The worker half: owns the shader, the mosaic and the reveal cells; one frame at a time. */
internal class ImageRenderer {
    private val shader = ImageShader()
    private val mosaic = ImageMosaic(shader)
    val cells = RevealCells(shader)
    private var configVersion = -1
    var pixels = ByteArray(0)
        private set

    fun render(f: FrameInput) {
        if (f.configVersion != configVersion) {
            shader.configure(f.mode, f.strength, f.pixelScale, f.colors, f.cardBgOverride)
            configVersion = f.configVersion
        }
        if (f.renderMosaic) {
            val need = f.iw * f.ih * 4
            if (pixels.size != need) pixels = ByteArray(need)
            mosaic.render(f.iw, f.ih, f.dpr, f.shaderTime, pixels)
        }
        val image = f.image ?: return
        when (f.overlay) {
            OverlayKind.Reveal -> cells.reveal(f, image)
            OverlayKind.Boil -> cells.boil(f, image, f.pending, f.boil!!)
            else -> Unit
        }
    }
}

/** The overlay phase (`RevealInternals.phase`). */
internal enum class OverlayPhase { Idle, Reveal, Hold, Hide, Boil }

/**
 * The main-thread half of one card (`Instance` + `RevealState`): its clock, the reveal state
 * machine and the published frame. Every 100 ms [frame] advances the clock, steps the phases,
 * snapshots a [FrameInput] and renders it on a background thread; the next display frame after it
 * finishes uploads the result and swaps in its [DrawPlan].
 */
@OptIn(ExperimentalAtomicApi::class)
@Stable
internal class ImageController {
    // Configuration.
    var mode: ImageMode = imageMode(ImagePreset.PixelsOrganic, dark = true)
        private set
    private var strength = 1f
    private var pixelScale = 1f
    private var colors: List<Color?>? = null
    private var cardBgOverride: Color? = null
    private var configVersion = 0
    var speedMul = 1f

    // Size (CSS px, as `getBoundingClientRect` rounds them) and density.
    var cssW = 0
        private set
    var cssH = 0
        private set
    private var density = 1f

    /** `accumulatedTime`: a random start so cards sharing a page don't move in lockstep. */
    var time: Double = Random.nextDouble() * 1000
    private var lastTickMs = Double.NaN
    private var lastFrameMs = Double.NaN

    // Reveal state.
    var phase = OverlayPhase.Idle
        private set
    var image: RevealImage? = null
        private set
    private var pending: RevealImage? = null
    private var revealStartMs = 0.0
    private var hideStartMs = 0.0
    private var hideDurationMs = 300.0
    private var boilStartMs = 0.0
    private var handoffStartMs = 0.0
    private var overlaySeq = 0
    private var onRevealComplete: (() -> Unit)? = null
    private var lastPlan = DrawPlan.Idle
    private var hideFrom = DrawPlan.Idle

    // The worker.
    private val renderer = ImageRenderer()
    private val jobState = AtomicInt(JobIdle)
    private var inFlight: FrameInput? = null
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Published frame (read by draw).
    var plan by mutableStateOf(DrawPlan.Idle)
        private set
    var mosaic by mutableStateOf<PixelSurface?>(null)
        private set
    var chunky by mutableStateOf<PixelSurface?>(null)
        private set
    var mask by mutableStateOf<PixelSurface?>(null)
        private set
    /** Bumped whenever a frame is needed, so an idle (paused) card's loop wakes to render it. */
    var wakes by mutableIntStateOf(0)
        private set

    private var needsFrame = true
        set(value) {
            if (value && !field) wakes++
            field = value
        }

    /** Nothing to render or publish: a paused card's frame loop may stop. */
    val idle: Boolean get() = !needsFrame && jobState.load() == JobIdle

    fun configure(mode: ImageMode, strength: Float, pixelScale: Float, colors: List<Color?>?, cardBgOverride: Color?) {
        if (mode === this.mode && strength == this.strength && pixelScale == this.pixelScale && colors == this.colors &&
            cardBgOverride == this.cardBgOverride
        ) {
            return
        }
        this.mode = mode
        this.strength = strength
        this.pixelScale = pixelScale
        this.colors = colors
        this.cardBgOverride = cardBgOverride
        configVersion++
        needsFrame = true
    }

    fun resize(cssW: Int, cssH: Int, density: Float) {
        if (cssW == this.cssW && cssH == this.cssH && density == this.density) return
        this.cssW = cssW
        this.cssH = cssH
        this.density = density
        needsFrame = true
        // Always wake: a snapshot that ran before layout still has needsFrame set.
        wakes++
    }

    /** The GL DPR: the device's, capped at 1.25. */
    private val glDpr: Float get() = min(density, MaxGlDpr)

    // ── Reveal control (`createReveal`'s handle) ──────────────────────────────────────────

    val isActive: Boolean get() = phase != OverlayPhase.Idle

    fun startReveal(img: RevealImage, nowMs: Double, onComplete: () -> Unit) {
        if (phase == OverlayPhase.Boil) {
            // Hand the incoming image to the churn, which morphs into it (see BoilTimeline).
            pending = img
            onRevealComplete = onComplete
            if (handoffStartMs == 0.0) handoffStartMs = nowMs
            return
        }
        image = img
        onRevealComplete = onComplete
        revealStartMs = nowMs
        phase = OverlayPhase.Reveal
        handoffStartMs = 0.0
        pending = null
        overlaySeq++
        needsFrame = true
    }

    fun startHide(nowMs: Double, durationMs: Double = 300.0) {
        if (phase == OverlayPhase.Idle || phase == OverlayPhase.Hide) return
        phase = OverlayPhase.Hide
        hideStartMs = nowMs
        hideDurationMs = durationMs
        hideFrom = lastPlan
        needsFrame = true
    }

    fun startBoil(nowMs: Double) {
        if (image == null) return
        if (phase != OverlayPhase.Hold && phase != OverlayPhase.Reveal) return
        phase = OverlayPhase.Boil
        boilStartMs = nowMs
        handoffStartMs = 0.0
        pending = null
        overlaySeq++
        needsFrame = true
    }

    fun clear() {
        phase = OverlayPhase.Idle
        image = null
        pending = null
        handoffStartMs = 0.0
        lastPlan = DrawPlan.Idle
        plan = DrawPlan.Idle
        needsFrame = true
    }

    // ── Frames ────────────────────────────────────────────────────────────────────────────

    /**
     * One display frame: publishes a finished render, and (while [running], at most every
     * 100 ms, or once when something changed while idle) starts the next.
     */
    fun frame(nowNanos: Long, running: Boolean) {
        if (jobState.load() == JobReady) publish()
        val nowMs = nowNanos / 1e6
        if (lastTickMs.isNaN()) { lastTickMs = nowMs; lastFrameMs = nowMs }
        if (running) {
            val elapsed = nowMs - lastFrameMs
            if (elapsed < FrameIntervalMs && !needsFrame) return
            if (elapsed >= FrameIntervalMs) lastFrameMs = nowMs - (elapsed % FrameIntervalMs)
            time += (nowMs - lastTickMs) / 1000.0 * speedMul
            lastTickMs = nowMs
            launch(nowMs)
        } else {
            lastTickMs = nowMs
            lastFrameMs = nowMs
            if (needsFrame) launch(nowMs)
        }
    }

    /** Renders one frame synchronously at [seconds] of card time — for snapshots. */
    fun renderNow(seconds: Double) {
        time = seconds
        val input = snapshot(0.0) ?: return
        renderer.render(input)
        inFlight = input
        publish()
        needsFrame = false
    }

    private fun launch(nowMs: Double) {
        if (jobState.load() != JobIdle) return
        val input = snapshot(nowMs) ?: return
        needsFrame = false
        inFlight = input
        jobState.store(JobRendering)
        worker.launch {
            try {
                renderer.render(input)
            } finally {
                jobState.store(JobReady)
            }
        }
    }

    /** Steps the phases at [nowMs] and captures the frame (`afterShaderFrame`). */
    private fun snapshot(nowMs: Double): FrameInput? {
        if (cssW < 1 || cssH < 1) return null
        val dpr = glDpr
        val iw = max(1, floor(cssW * dpr).toInt())
        val ih = max(1, floor(cssH * dpr).toInt())
        var overlay = OverlayKind.None
        var elapsed = 0f
        var boil: BoilTimeline? = null
        val img = image
        val next: DrawPlan = when (phase) {
            OverlayPhase.Idle -> DrawPlan.Idle
            OverlayPhase.Reveal -> {
                val cfg = mode.reveal
                elapsed = ((nowMs - revealStartMs) / 1000.0).toFloat()
                val t = min(elapsed / max(0.05f, cfg.duration), 1f)
                if (t >= 1f) {
                    phase = OverlayPhase.Hold
                    onRevealComplete?.invoke()
                    DrawPlan(0f, 1f, OverlayKind.Image, img, 1f)
                } else {
                    overlay = OverlayKind.Reveal
                    DrawPlan(1f - cfg.easing.apply(t), 1f, OverlayKind.Reveal, img, 1f)
                }
            }
            OverlayPhase.Hold -> DrawPlan(0f, 1f, OverlayKind.Image, img, 1f)
            OverlayPhase.Boil -> {
                val pend = pending
                val hand = if (pend != null && handoffStartMs > 0.0) (nowMs - handoffStartMs).toFloat() else -1f
                val tl = BoilTimeline((nowMs - boilStartMs).toFloat(), hand, pend != null)
                if (tl.complete && pend != null) {
                    image = pend
                    pending = null
                    handoffStartMs = 0.0
                    phase = OverlayPhase.Hold
                    onRevealComplete?.invoke()
                    DrawPlan(0f, 1f, OverlayKind.Image, pend, 1f)
                } else {
                    overlay = OverlayKind.Boil
                    boil = tl
                    when {
                        tl.pixelatingIn && tl.pixInBaseAlpha > 0f -> DrawPlan(tl.ramp, 1f, OverlayKind.Boil, img, tl.pixInBaseAlpha)
                        tl.resolving && pend != null && tl.baseT > 0f -> DrawPlan(tl.ramp, 1f, OverlayKind.Boil, pend, tl.baseT)
                        else -> DrawPlan(tl.ramp, 1f, OverlayKind.Boil, null, 0f)
                    }
                }
            }
            OverlayPhase.Hide -> {
                val t = min((nowMs - hideStartMs) / max(1.0, hideDurationMs), 1.0).toFloat()
                if (t >= 1f) {
                    phase = OverlayPhase.Idle
                    image = null
                    DrawPlan.Idle
                } else {
                    DrawPlan(t, 1f - t, hideFrom.overlay, hideFrom.image, hideFrom.imageAlpha)
                }
            }
        }
        if (phase != OverlayPhase.Hide) lastPlan = next
        return FrameInput(
            mode = mode, configVersion = configVersion, strength = strength, pixelScale = pixelScale, colors = colors,
            cardBgOverride = cardBgOverride, iw = iw, ih = ih, dpr = dpr, cssW = cssW, cssH = cssH, time = time,
            renderMosaic = next.shaderAlpha > 0f || mosaic == null, overlay = overlay, overlaySeq = overlaySeq,
            elapsed = elapsed, image = if (overlay == OverlayKind.None) null else img, pending = pending, boil = boil, plan = next,
        )
    }

    private fun publish() {
        val f = inFlight ?: return
        if (f.renderMosaic) {
            val surface = mosaic?.takeIf { it.width == f.iw && it.height == f.ih } ?: PixelSurface(f.iw, f.ih)
            surface.commit(renderer.pixels)
            mosaic = surface
        }
        if (f.overlay == OverlayKind.Reveal || f.overlay == OverlayKind.Boil) {
            val cells = renderer.cells
            val c = chunky?.takeIf { it.width == cells.gridX && it.height == cells.gridY } ?: PixelSurface(cells.gridX, cells.gridY)
            c.commit(cells.chunky)
            chunky = c
            if (f.overlay == OverlayKind.Reveal) {
                val m = mask?.takeIf { it.width == cells.gridX && it.height == cells.gridY } ?: PixelSurface(cells.gridX, cells.gridY)
                m.commit(cells.mask)
                mask = m
            }
        }
        plan = f.plan
        inFlight = null
        jobState.store(JobIdle)
    }

    private companion object {
        const val JobIdle = 0
        const val JobRendering = 1
        const val JobReady = 2
    }
}
