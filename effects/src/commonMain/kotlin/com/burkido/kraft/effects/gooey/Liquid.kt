package com.burkido.kraft.effects.gooey

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.CubicBezier
import com.burkido.kraft.effects.core.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Liquid UI (`liquid-gooey`): the items inside share one goo surface, so touching pieces merge and
 * bridge like liquid, while their content stays crisp above it. The surface is a silhouette of
 * every item, blurred by [blur] and cut at an alpha threshold of slope [contrast], filled with
 * [fill] and shaded with [shadow] — rebuilt on the merged shape, so one shadow hugs the liquid
 * through every merge and split.
 *
 * [liquidOffset] shifts the whole surface (not the content), for gestures like a close nudge.
 */
@Composable
fun Liquid(
    modifier: Modifier = Modifier,
    blur: Dp = 6.dp,
    contrast: Float = 18f,
    fill: Color = Color.White,
    shadow: List<LiquidShadow> = emptyList(),
    filterPadding: Dp = 24.dp,
    liquidOffset: () -> Offset = { Offset.Zero },
    content: @Composable LiquidScope.() -> Unit,
) {
    val engine = remember { LiquidEngine() }
    val density = LocalDensity.current
    engine.gooBlurPx = with(density) { blur.toPx() }

    // The engine sleeps when nothing moves and wakes whenever an item's box changes.
    LaunchedEffect(engine) {
        snapshotFlow { engine.wakeCount }.collect {
            var last = Long.MIN_VALUE
            var clean = 0
            while (clean <= 30) {
                withFrameNanos { now ->
                    val dt = if (last == Long.MIN_VALUE) 1.0 / 60 else ((now - last) / 1e9).coerceIn(1.0 / 240, 0.25)
                    last = now
                    if (engine.step(dt)) clean = 0 else clean++
                }
            }
        }
    }

    Box(
        modifier
            .onGloballyPositioned {
                engine.container = it
                engine.wake()
            }
            .drawWithCache {
                val layer = obtainGraphicsLayer()
                val dropSigma = shadow.filter { !it.inset && it.spread == 0.dp }.maxOfOrNull { it.blur.toPx() + max(abs(it.x.toPx()), abs(it.y.toPx())) } ?: 0f
                val svgExtent = shadow.filter { it.inset || it.spread != 0.dp }
                    .maxOfOrNull { max(abs(it.x.toPx()), abs(it.y.toPx())) + it.blur.toPx() * 1.5f + max(0f, it.spread.toPx()) } ?: 0f
                // The filter region: room for the goo blur, the shadow passes, blobs travelling
                // past the group box and the drop shadows' reach.
                val pad = ceil(blur.toPx() * 3 + svgExtent + filterPadding.toPx() + dropSigma * 3).toInt()
                layer.renderEffect = liquidRenderEffect(
                    blurPx = blur.toPx(),
                    contrast = contrast,
                    shadows = shadow.map { ShadowPx(it.x.toPx(), it.y.toPx(), it.blur.toPx(), it.spread.toPx(), it.color, it.inset) },
                )
                onDrawBehind {
                    engine.tick // Every engine step redraws the surface, never recomposes.
                    val nudge = liquidOffset()
                    layer.topLeft = IntOffset(-pad + nudge.x.roundToInt(), -pad + nudge.y.roundToInt())
                    layer.record(IntSize(size.width.toInt() + 2 * pad, size.height.toInt() + 2 * pad)) {
                        translate(pad.toFloat(), pad.toFloat()) { engine.drawBlobs(this, fill) }
                    }
                    drawLayer(layer)
                }
            },
    ) {
        LiquidScope(engine, this).content()
    }
}

/** Where the liquid's items go. */
@Stable
class LiquidScope internal constructor(internal val engine: LiquidEngine, boxScope: BoxScope) : BoxScope by boxScope

/** A `{ duration, ease }` transition for [LiquidScope.Item]'s x / y. */
@Immutable
class LiquidTransition(val durationMillis: Int, val easing: Easing) {
    companion object {
        /** `cubic-bezier(0.34, 1.56, 0.64, 1)` over 550 ms: the plus menu's opening. */
        val Bouncy = LiquidTransition(550, CubicBezier(0.34, 1.56, 0.64, 1.0).easing)
        val Smooth = LiquidTransition(500, CubicBezier(0.3, 1.05, 0.4, 1.0).easing)
        val Snappy = LiquidTransition(250, CubicBezier(0.22, 1.0, 0.36, 1.0).easing)
    }
}

/**
 * A plain-merge item (`<Liquid.Item x y transition delay>`): the library moves the element and its
 * silhouette by ([x], [y]) together, retargeting like a CSS transition from wherever it is, after
 * [delayMillis]. The silhouette is the element's box with [radius] corners.
 */
@Composable
fun LiquidScope.Item(
    x: Dp,
    y: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
    transition: LiquidTransition = LiquidTransition.Smooth,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()
    val item = remember { LiquidItemState(LiquidItemState.Kind.Mirror) }
    item.radiusPx = with(density) { radius.toPx() }
    val ax = remember { Animatable(with(density) { x.toPx() }) }
    val ay = remember { Animatable(with(density) { y.toPx() }) }
    item.mirrorX = { ax.value }
    item.mirrorY = { ay.value }
    val tx = with(density) { x.toPx() }
    val ty = with(density) { y.toPx() }
    LaunchedEffect(tx, ty) {
        if (ax.targetValue == tx && ay.targetValue == ty) return@LaunchedEffect
        if (reduced || transition.durationMillis <= 0) {
            ax.snapTo(tx)
            ay.snapTo(ty)
            return@LaunchedEffect
        }
        delay(delayMillis.toLong())
        val spec = tween<Float>(transition.durationMillis, easing = transition.easing)
        launch { ax.animateTo(tx, spec) }
        ay.animateTo(ty, spec)
    }
    DisposableEffect(item) {
        engine.add(item)
        onDispose { engine.remove(item) }
    }
    // The outer box reports the untransformed base box; the inner one carries the shared motion.
    Box(modifier.onGloballyPositioned {
        item.coords = it
        engine.wake()
    }) {
        Box(Modifier.graphicsLayer {
            translationX = ax.value
            translationY = ay.value
        }) { content() }
    }
}

/** Tuning for [MoveItem] — the web's normalised 0..1 knobs. */
@Immutable
data class MoveTuning(
    /** How tightly the liquid chases: 0 heavy syrup, 1 near-instant. */
    val springiness: Float = 0.5f,
    /** Overshoot and wobble on arrival. */
    val wobble: Float = 0.5f,
    /** Velocity stretch of the drop. */
    val stretch: Float = 0.36f,
    /** Trailing droplet size; 0 disables the tail. */
    val trail: Float = 0.575f,
)

/** Tuning for [BendItem]: vertical bow and horizontal cap deformation, 0..1. */
@Immutable
data class BendTuning(val vertical: Float = 0.6f, val horizontal: Float = 0.35f)

/**
 * The live bend of a [BendItem] (`--lg-bend-x/-y`): how far the body's middle leads (px) on each
 * axis, rounded to a tenth like the web's CSS variables. Content can lean with it.
 */
@Stable
class LiquidBend internal constructor() {
    var x: Float by mutableStateOf(0f)
        internal set
    var y: Float by mutableStateOf(0f)
        internal set
}

/**
 * A move item (`effect="move"`): position the element yourself — the liquid surface chases it on a
 * spring, stretches with speed and strings a droplet tail behind it.
 */
@Composable
fun LiquidScope.MoveItem(
    radius: Dp,
    modifier: Modifier = Modifier,
    tuning: MoveTuning = MoveTuning(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val item = remember { LiquidItemState(LiquidItemState.Kind.Move) }
    item.radiusPx = with(density) { radius.toPx() }
    item.move = mapMove(tuning)
    item.density = density.density
    DisposableEffect(item) {
        engine.add(item)
        onDispose { engine.remove(item) }
    }
    Box(modifier.onGloballyPositioned {
        item.coords = it
        engine.wake()
    }) { content() }
}

/**
 * A bend item (`effect="bend"`): the surface stays glued to the element while its body deforms with
 * velocity — a vertical drag arcs the long edges, a sideways one blunts the leading cap and
 * stretches the trailing one. [content] receives the live bend to lean along.
 */
@Composable
fun LiquidScope.BendItem(
    radius: Dp,
    modifier: Modifier = Modifier,
    tuning: BendTuning = BendTuning(),
    content: @Composable (LiquidBend) -> Unit,
) {
    val density = LocalDensity.current
    val item = remember { LiquidItemState(LiquidItemState.Kind.Move) }
    item.radiusPx = with(density) { radius.toPx() }
    item.move = mapBend(tuning)
    item.density = density.density
    DisposableEffect(item) {
        engine.add(item)
        onDispose { engine.remove(item) }
    }
    Box(modifier.onGloballyPositioned {
        item.coords = it
        engine.wake()
    }) { content(item.bend) }
}

// ── Engine ────────────────────────────────────────────────────────────────────────────────

/** `MoveOptions`, resolved. Lengths are the web's px; the engine scales them by density. */
internal class MoveOptions(
    val stiffness: Double,
    val damping: Double,
    val stretch: Double,
    val tail: Double,
    val force: Double = 0.5,
    val bend: Double = 0.0,
    val bendX: Double = 0.0,
)

private const val DefaultStiffness = 380.0
private const val DefaultDamping = 18.0

/** Damping ratio from a 0..1 bounciness knob; 0.5 lands on the tuned defaults' ratio. */
private fun zeta(bounce: Double): Double = max(0.12, 1 - 1.1 * bounce.coerceIn(0.0, 1.0))

internal fun mapMove(t: MoveTuning): MoveOptions {
    val p = t.springiness.toDouble().coerceIn(0.0, 1.0)
    val stiffness = DefaultStiffness * 10.0.pow(p - 0.5)
    val damping = DefaultDamping * sqrt(stiffness / DefaultStiffness) * (zeta(t.wobble.toDouble()) / zeta(0.5))
    return MoveOptions(
        stiffness = stiffness,
        damping = damping,
        stretch = 0.5 * t.stretch.toDouble().coerceIn(0.0, 1.0),
        tail = 0.8 * t.trail.toDouble().coerceIn(0.0, 1.0),
    )
}

internal fun mapBend(t: BendTuning): MoveOptions {
    // Springiness 1: the surface tracks the content 1:1, so all character comes from the bends.
    val base = mapMove(MoveTuning(springiness = 1f, stretch = 0f, trail = 0f))
    return MoveOptions(
        stiffness = base.stiffness,
        damping = base.damping,
        stretch = 0.0,
        tail = 0.0,
        bend = t.vertical.toDouble().coerceIn(0.0, 1.0),
        bendX = t.horizontal.toDouble().coerceIn(0.0, 1.0),
    )
}

internal class LiquidItemState(val kind: Kind) {
    enum class Kind { Mirror, Move }

    var coords: LayoutCoordinates? = null
    var radiusPx = 0f
    var density = 1f
    var move: MoveOptions? = null
    var mirrorX: () -> Float = { 0f }
    var mirrorY: () -> Float = { 0f }
    val bend = LiquidBend()

    // The simulated body (centre-based) and its trailing droplets, in px.
    var hasSim = false
    var cx = 0.0; var cy = 0.0; var w = 0.0; var h = 0.0
    var vcx = 0.0; var vcy = 0.0
    var tailX = 0.0; var tailY = 0.0; var tailVx = 0.0; var tailVy = 0.0; var tailR = 0.0
    var tailPhase = 0.0
    var bendCur = 0.0; var bendCurX = 0.0
    var frame: Rect? = null
}

internal class LiquidEngine {
    private val items = ArrayList<LiquidItemState>()
    var container: LayoutCoordinates? = null
    var gooBlurPx = 0f

    var tick by mutableIntStateOf(0)
        private set
    var wakeCount by mutableIntStateOf(0)
        private set

    fun add(item: LiquidItemState) {
        items += item
        wake()
    }

    fun remove(item: LiquidItemState) {
        items -= item
        tick++
    }

    fun wake() {
        wakeCount++
    }

    /** One frame for every item; true while anything is still moving. */
    fun step(dt: Double): Boolean {
        val group = container ?: return false
        if (!group.isAttached) return false
        var changed = false
        for (item in items) {
            val c = item.coords ?: continue
            if (!c.isAttached) continue
            val f = group.localBoundingBoxOf(c, clipBounds = false)
            if (item.kind == LiquidItemState.Kind.Mirror) {
                if (item.frame != f) changed = true
                item.frame = f
                continue
            }
            if (stepMove(item, f, dt)) changed = true
        }
        tick++
        return changed
    }

    /** `writeBlob`'s move branch: the surface chases the element and trails a droplet tongue. */
    private fun stepMove(item: LiquidItemState, f: Rect, dt: Double): Boolean {
        val mo = item.move ?: return false
        val tcx = (f.left + f.width / 2).toDouble()
        val tcy = (f.top + f.height / 2).toDouble()
        item.frame = f
        if (!item.hasSim) {
            item.hasSim = true
            item.cx = tcx; item.cy = tcy
        }
        item.w = f.width.toDouble()
        item.h = f.height.toDouble()
        // Physics runs in the web's px: convert, integrate, convert back.
        val d = item.density.toDouble()
        springSteps(item.cx / d, item.vcx / d, tcx / d, mo.stiffness, mo.damping, dt).let { (p, v) -> item.cx = p * d; item.vcx = v * d }
        springSteps(item.cy / d, item.vcy / d, tcy / d, mo.stiffness, mo.damping, dt).let { (p, v) -> item.cy = p * d; item.vcy = v * d }
        val speed = hypot(item.vcx, item.vcy) / d

        if (mo.tail > 0) {
            if (item.tailR == 0.0 && item.tailX == 0.0 && item.tailY == 0.0) {
                item.tailX = item.cx; item.tailY = item.cy
            }
            springSteps(item.tailX / d, item.tailVx / d, item.cx / d, 170.0, 22.0, dt).let { (p, v) -> item.tailX = p * d; item.tailVx = v * d }
            springSteps(item.tailY / d, item.tailVy / d, item.cy / d, 170.0, 22.0, dt).let { (p, v) -> item.tailY = p * d; item.tailVy = v * d }
            val ux = if (speed > 0.001) item.vcx / d / speed else 1.0
            val uy = if (speed > 0.001) item.vcy / d / speed else 0.0
            val perp = max(4.0, abs(ux) * item.h / d + abs(uy) * item.w / d)
            val halfAlong = (abs(ux) * item.w / d + abs(uy) * item.h / d) / 2
            val base = perp / 2
            val lagX = (item.tailX - item.cx) / d
            val lagY = (item.tailY - item.cy) / d
            val lag = hypot(lagX, lagY)
            val maxLag = halfAlong + base * (0.2 + mo.force * 1.6)
            if (lag > maxLag) {
                item.tailX = item.cx + lagX / lag * maxLag * d
                item.tailY = item.cy + lagY / lag * maxLag * d
            }
            val onset = ((speed - 20) / 120).coerceIn(0.0, 1.0)
            val targetR = base * mo.tail * onset
            item.tailR += (targetR * d - item.tailR) * min(1.0, dt * 10)
            if (item.tailR >= 0.3 * d) item.tailPhase += speed * dt * 0.045
        }

        if (mo.bend > 0 || mo.bendX > 0) {
            val bw = item.w / d
            val bh = item.h / d
            val cap = min(bw, bh) * 0.5
            val bTy = (item.vcy / d * 0.05).coerceIn(-cap, cap) * mo.bend
            val capX = min(bw, bh) * 0.9
            val bTx = (item.vcx / d * 0.09).coerceIn(-capX, capX) * mo.bendX
            item.bendCur += (bTy - item.bendCur) * min(1.0, dt * 9)
            item.bendCurX += (bTx - item.bendCurX) * min(1.0, dt * 9)
            item.bend.x = (kotlin.math.round(item.bendCurX * 10) / 10).toFloat()
            item.bend.y = (kotlin.math.round(item.bendCur * 10) / 10).toFloat()
        }

        val settled = abs(item.cx - tcx) < 0.05 * d && abs(item.cy - tcy) < 0.05 * d && speed < 1 &&
            item.tailR < 0.3 * d && abs(item.bendCur) < 0.05 && abs(item.bendCurX) < 0.05
        return !settled
    }

    private val bendPath = Path()

    /** Paints every silhouette in group coordinates: tails first, then the bodies. */
    fun drawBlobs(scope: DrawScope, fill: Color) = with(scope) {
        val group = container ?: return@with
        for (item in items) {
            val c = item.coords ?: continue
            if (!c.isAttached || !group.isAttached) continue
            when (item.kind) {
                LiquidItemState.Kind.Mirror -> {
                    val base = group.localBoundingBoxOf(c, clipBounds = false)
                    drawPill(base.translate(item.mirrorX(), item.mirrorY()), item.radiusPx, fill)
                }
                LiquidItemState.Kind.Move -> drawMoveBlob(item, fill)
            }
        }
    }

    private fun DrawScope.drawMoveBlob(item: LiquidItemState, fill: Color) {
        if (!item.hasSim) return
        val d = item.density.toDouble()
        val mo = item.move ?: return
        // Trailing droplets: the main satellite and two tapering mid-droplets that weave slightly.
        if (item.tailR >= 0.3 * d) {
            val speed = hypot(item.vcx, item.vcy) / d
            val ux = if (speed > 0.001) item.vcx / d / speed else 1.0
            val uy = if (speed > 0.001) item.vcy / d / speed else 0.0
            val lagX = item.tailX - item.cx
            val lagY = item.tailY - item.cy
            val wob = item.tailR * 0.16
            val w1 = sin(item.tailPhase) * wob
            val w2 = sin(item.tailPhase + 2.4) * -wob
            drawCircle(fill, item.tailR.toFloat(), Offset(item.tailX.toFloat(), item.tailY.toFloat()))
            drawCircle(fill, (item.tailR * 0.62).toFloat(), Offset((item.cx + lagX * 0.45 - uy * w1).toFloat(), (item.cy + lagY * 0.45 + ux * w1).toFloat()))
            drawCircle(fill, (item.tailR * 0.4).toFloat(), Offset((item.cx + lagX * 0.75 - uy * w2).toFloat(), (item.cy + lagY * 0.75 + ux * w2).toFloat()))
        }
        val w = item.w.toFloat()
        val h = item.h.toFloat()
        val left = (item.cx - item.w / 2).toFloat()
        val top = (item.cy - item.h / 2).toFloat()
        val r = pillRadius(item.radiusPx, w, h)
        // Mild stretch along the velocity axis, about the body's centre.
        val speed = hypot(item.vcx, item.vcy) / d
        val st = if (speed > 2) min(mo.stretch, speed * 0.0006).toFloat() else 0f
        val angle = atan2(item.vcy, item.vcx).toFloat()
        val bendY = item.bendCur * d
        val bendX = item.bendCurX * d
        val bending = abs(item.bendCur) > 0.5 || abs(item.bendCurX) > 0.5
        withStretch(st, angle, Offset(left + w / 2, top + h / 2)) {
            if (bending && w > 1 && h > 1) {
                bentPill(bendPath, left, top, w, h, r, bendY.toFloat(), bendX.toFloat())
                drawPath(bendPath, fill)
            } else {
                drawRoundRect(fill, Offset(left, top), Size(w, h), CornerRadius(r))
            }
        }
    }

    private inline fun DrawScope.withStretch(st: Float, angle: Float, pivot: Offset, block: DrawScope.() -> Unit) {
        if (st <= 0f) {
            block()
            return
        }
        rotateRad(angle, pivot) {
            scale(1 + st, 1 / (1 + st * 0.65f), pivot) {
                rotateRad(-angle, pivot) { block() }
            }
        }
    }

    private fun DrawScope.drawPill(r: Rect, radius: Float, fill: Color) {
        drawRoundRect(fill, r.topLeft, r.size, CornerRadius(pillRadius(radius, r.width, r.height)))
    }
}

/** A CSS radius as an SVG `rx`: clamped to half the short side so a `999px` pill stays a pill. */
private fun pillRadius(r: Float, w: Float, h: Float): Float = max(0f, min(r, min(w, h) / 2))

/**
 * The bowed body: the top and bottom edges curve through a quadratic sag of [bendY] (control
 * 2·[bendY]); [bendX] blunts the leading cap and stretches the trailing one.
 */
private fun bentPill(path: Path, left: Float, top: Float, bw: Float, bh: Float, radius: Float, bendY: Float, bendX: Float) {
    val r = min(radius, min(bw / 2, bh / 2))
    val cy = bendY * 2
    val k = bendX
    val rxR = (if (k > 0) r - 0.8f * k else r + 1.6f * -k).coerceIn(r * 0.2f, r * 3)
    val rxL = (if (k > 0) r + 1.6f * k else r - 0.8f * -k).coerceIn(r * 0.2f, r * 3)
    val c = 0.5523f
    path.rewind()
    path.moveTo(left + rxL, top)
    path.quadraticTo(left + bw / 2, top + cy, left + bw - rxR, top)
    path.cubicTo(left + bw - rxR + c * rxR, top, left + bw, top + r - c * r, left + bw, top + r)
    path.lineTo(left + bw, top + bh - r)
    path.cubicTo(left + bw, top + bh - r + c * r, left + bw - rxR + c * rxR, top + bh, left + bw - rxR, top + bh)
    path.quadraticTo(left + bw / 2, top + bh + bendY * 2, left + rxL, top + bh)
    path.cubicTo(left + rxL - c * rxL, top + bh, left, top + bh - r + c * r, left, top + bh - r)
    path.lineTo(left, top + r)
    path.cubicTo(left, top + r - c * r, left + rxL - c * rxL, top, left + rxL, top)
    path.close()
}

/** Semi-implicit Euler, substepped at ≤ 1/60 s so any frame gap integrates stably. */
private fun springSteps(cur: Double, vel: Double, target: Double, k: Double, c: Double, dt: Double): Pair<Double, Double> {
    var n = max(1, ceil(dt * 60).toInt())
    val h = dt / n
    var p = cur
    var v = vel
    while (n-- > 0) {
        val a = k * (target - p) - c * v
        v += a * h
        p += v * h
    }
    return p to v
}
