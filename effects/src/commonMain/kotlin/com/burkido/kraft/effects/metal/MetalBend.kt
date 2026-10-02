package com.burkido.kraft.effects.metal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** `BEND_DEFAULTS` (ring mode): the dent's reach, blobs, springs and gains, in dp. */
private object BendConfig {
    const val Strength = 0.74f
    const val FadeInMs = 200f
    const val FadeOutMs = 350f
    const val SmoothMs = 140f
    const val Reach = 36f
    const val Blob = 13f
    const val LiquidBlob = 10f
    const val MaxDisp = 9f
    const val Gain = 0.6f
    const val PressGain = 0.55f
    const val PullGain = 0.49f
    const val Press = 5f
    const val Liquid = 7.5f
    const val LiquidReach = 8f
    const val LiquidStiffness = 53f
    const val LiquidDamping = 9f
    const val Stiffness = 260f
    const val Damping = 13f
    const val Mass = 1f
    const val Follow = 0.32f
}

/**
 * The liquid dent of a [MetalFx] ring (`useMetalBend`): a pointer near the ring — hovering on
 * desktop, a finger on touch screens — presses it in or drags it out, with a divergent "liquid"
 * push at the contact point, and on release the ring springs back. Track the pointer with
 * [metalBendArea] on the area around the ring and pass the state to `MetalFx(bend = …)`.
 */
@Stable
class MetalBendState internal constructor() {
    internal var area: LayoutCoordinates? = null
    internal var pointer: Offset? = null
    internal var pressed = false
    private var lastPointer: Offset? = null
    private var lastPointerNanos = 0L
    private var svx = 0f
    private var svy = 0f

    // Simulation (dp).
    private var active = false
    private var cx = 0f; private var cy = 0f
    private var ax = 0f; private var ay = 0f; private var vax = 0f; private var vay = 0f
    private var la = 0f; private var vla = 0f
    private var stx = 0f; private var sty = 0f; private var stl = 0f
    private var env = 0f

    // The field (`evalField`), read by the ring while non-null.
    internal var fieldOn = false
        private set
    private var fDirX = 0f; private var fDirY = 0f; private var fRadK = 0f
    private var fS2b = 1f; private var fS2l = 1f

    /** Bumped whenever the field changes, so a ring drawing it redraws. */
    internal var version by mutableIntStateOf(0)
        private set

    /** Wakes the owning ring's loop when the pointer moves. */
    internal var onWake: () -> Unit = {}

    internal val deform = Deform { x, y, out ->
        val dx = x - cx
        val dy = y - cy
        val q = dx * dx + dy * dy
        val gb = exp(-q / fS2b)
        val gl = exp(-q / fS2l)
        out[0] = x + fDirX * gb + dx * fRadK * gl
        out[1] = y + fDirY * gb + dy * fRadK * gl
    }

    /** How far any outline point can travel, dp — the overscan a bent ring needs. */
    internal val reach: Float = (BendConfig.MaxDisp + BendConfig.Liquid * 1.5f) + 4

    internal fun onPointer(rootPosition: Offset?, nanos: Long, density: Float) {
        if (rootPosition != null) {
            val last = lastPointer
            if (last != null) {
                val dt = max(0.004f, (nanos - lastPointerNanos) / 1e9f)
                val a = 1 - exp(-dt / 0.04f)
                svx += ((rootPosition.x - last.x) / density / dt - svx) * a
                svy += ((rootPosition.y - last.y) / density / dt - svy) * a
            }
            lastPointer = rootPosition
            lastPointerNanos = nanos
        } else {
            lastPointer = null
        }
        pointer = rootPosition
        onWake()
    }

    /**
     * One step for a ring whose box is [w]×[h] dp at [hostOrigin] (root px). Returns true while
     * the dent is alive.
     */
    internal fun step(dtSeconds: Float, w: Float, h: Float, hostOrigin: Offset, density: Float): Boolean {
        val dt = dtSeconds.coerceIn(0.001f, 0.032f)
        val ecx = w / 2
        val ecy = h / 2
        var tx = 0f
        var ty = 0f
        var pressure = 0f
        val p = pointer
        if (p != null) {
            val lx = (p.x - hostOrigin.x) / density
            val ly = (p.y - hostOrigin.y) / density
            val ddx = lx - ecx
            val ddy = ly - ecy
            val d = hypot(ddx, ddy)
            val r = min(w, h) / 2
            val reach = max(w, h) / 2 + BendConfig.Reach
            val wasActive = active
            active = d < reach
            val u = max(0f, 1 - kotlin.math.abs(d - r) / max(1f, BendConfig.LiquidReach))
            pressure = u * u * (3 - 2 * u)
            if (active) {
                if (!wasActive) { cx = lx; cy = ly }
                val fa = 1 - (1 - min(0.999f, BendConfig.Follow)).pow(dt * 60)
                cx += (lx - cx) * fa
                cy += (ly - cy) * fa
                val edgeFade = 1 - max(0f, (d - (reach - BendConfig.Reach)) / BendConfig.Reach)
                tx = svx * (BendConfig.Gain / 100) * edgeFade
                ty = svy * (BendConfig.Gain / 100) * edgeFade
                val ux = if (d > 0.001f) ddx / d else 0f
                val uy = if (d > 0.001f) ddy / d else 0f
                val pen = r - d
                if (pen > 0) {
                    val m = pen * BendConfig.PressGain
                    tx -= ux * m; ty -= uy * m
                } else {
                    val out = -pen
                    val f = max(0f, 1 - out / max(1f, BendConfig.Reach))
                    val m = out * BendConfig.PullGain * (f * f * (3 - 2 * f))
                    tx += ux * m; ty += uy * m
                }
                val tm = hypot(tx, ty)
                if (tm > BendConfig.MaxDisp) { tx *= BendConfig.MaxDisp / tm; ty *= BendConfig.MaxDisp / tm }
            }
        } else {
            active = false
        }
        svx *= exp(-dt * 12)
        svy *= exp(-dt * 12)
        val sa = 1 - exp(-(dt * 1000) / max(1f, BendConfig.SmoothMs))
        stx += (tx - stx) * sa
        sty += (ty - sty) * sa
        val m = max(0.05f, BendConfig.Mass)
        vax += ((-BendConfig.Stiffness * (ax - stx) - BendConfig.Damping * vax) / m) * dt
        vay += ((-BendConfig.Stiffness * (ay - sty) - BendConfig.Damping * vay) / m) * dt
        ax += vax * dt
        ay += vay * dt
        val pressAmt = if (pressed && active) BendConfig.Press else 0f
        val tl = if (active) BendConfig.Liquid * (pressure + if (pressed) 0.5f else 0f) else 0f
        stl += (tl - stl) * sa
        vla += ((-BendConfig.LiquidStiffness * (la - stl) - BendConfig.LiquidDamping * vla) / m) * dt
        la += vla * dt
        val envTarget = if (active) 1f else 0f
        val tauMs = max(1f, if (envTarget > 0f) BendConfig.FadeInMs else BendConfig.FadeOutMs) / 3
        env += (envTarget - env) * (1 - exp(-(dt * 1000) / tauMs))
        if (env < 0.002f && envTarget == 0f) env = 0f
        val amp = hypot(ax, ay)
        val idle = !active && env == 0f && amp < 0.05f && hypot(vax, vay) < 1f && kotlin.math.abs(la) < 0.05f && pressAmt == 0f
        if (idle) {
            ax = 0f; ay = 0f; vax = 0f; vay = 0f; la = 0f; vla = 0f; stx = 0f; sty = 0f; stl = 0f
            if (fieldOn) { fieldOn = false; version++ }
            return false
        }
        val k = max(0f, BendConfig.Strength) * env
        var pdx = ecx - cx
        var pdy = ecy - cy
        val pl = hypot(pdx, pdy).takeIf { it > 0f } ?: 1f
        pdx /= pl; pdy /= pl
        val sb = max(0.5f, BendConfig.Blob)
        val sl = max(0.5f, BendConfig.LiquidBlob)
        fS2b = 2 * sb * sb
        fS2l = 2 * sl * sl
        fDirX = (ax + pdx * pressAmt) * k
        fDirY = (ay + pdy * pressAmt) * k
        fRadK = (la / sl) * k
        fieldOn = true
        version++
        return true
    }
}

@Composable
fun rememberMetalBend(): MetalBendState = remember { MetalBendState() }

/**
 * Feeds [state] the pointer over this area: hover moves on desktop, touches everywhere (a press
 * deepens the dent). Put it on a region generously larger than the ring — the web tracks the
 * pointer up to 36 dp beyond the ring's edge.
 */
fun Modifier.metalBendArea(state: MetalBendState): Modifier = this
    .onGloballyPositioned { state.area = it }
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                val area = state.area
                val root = if (area != null && area.isAttached) area.localToRoot(change.position) else null
                when (event.type) {
                    PointerEventType.Exit -> state.onPointer(null, change.uptimeMillis * 1_000_000, density)
                    PointerEventType.Release -> {
                        state.pressed = false
                        // A lifted finger leaves; a mouse button release stays hovering.
                        if (change.type == androidx.compose.ui.input.pointer.PointerType.Touch) {
                            state.onPointer(null, change.uptimeMillis * 1_000_000, density)
                        } else {
                            state.onPointer(root, change.uptimeMillis * 1_000_000, density)
                        }
                    }
                    PointerEventType.Press -> {
                        state.pressed = true
                        state.onPointer(root, change.uptimeMillis * 1_000_000, density)
                    }
                    else -> state.onPointer(root, change.uptimeMillis * 1_000_000, density)
                }
            }
        }
    }
