package com.burkido.kraft.effects.avatars

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import com.burkido.kraft.effects.core.rememberEffectActivity
import com.burkido.kraft.effects.core.rememberReducedMotion
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The page's pointer, as bot avatars follow it (`ticker.ts`'s `pointer`): a position in root
 * coordinates, or null while it's away. Fed by [botAvatarPointer] regions and by the avatars
 * themselves while a pointer is over them.
 */
object BotAvatarPointer {
    var position: Offset? by mutableStateOf(null)
}

/**
 * Makes this region the avatars' pointer: hovering (or dragging a finger) anywhere over it moves
 * the eyes and heads of the avatars within three head widths, as a pointer anywhere on the page
 * does on the web. Events pass through untouched.
 */
fun Modifier.botAvatarPointer(): Modifier {
    var coords: LayoutCoordinates? = null
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    val c = coords
                    BotAvatarPointer.position = when {
                        event.type == PointerEventType.Exit -> null
                        event.type == PointerEventType.Release && change.type == androidx.compose.ui.input.pointer.PointerType.Touch -> null
                        c != null && c.isAttached -> c.localToRoot(change.position)
                        else -> null
                    }
                }
            }
        }
}

/** How far a pointer's pull reaches, in head widths. */
private const val Reach = 3.0

/**
 * An animated bot avatar (`<BotAvatar>`): one of eighteen glossy 3D bodies with a living face —
 * it looks around, blinks, jumps and turns now and then, hops while [state] is working and dozes
 * while sleeping, and every state change cross-animates. Laid out at [size]; the drawing
 * overscans it so a hop or a flip is never clipped.
 *
 * @param saturation 1 as the palette has it; the default 1.5 is more vivid, like the web.
 * @param shading `Plastic` (default), `Crisp`, `Smooth` or `Flat`; [shadow], [highlight],
 *   [depth], [light] (degrees clockwise from the top), [rim] and [spread] tune the light.
 * @param interactive a tap makes it hop and turn right round; a pointer nearby is followed.
 * @param seed 0..1: offsets the blink and glance loops; random per avatar by default.
 */
@Composable
fun BotAvatar(
    modifier: Modifier = Modifier,
    type: BotAvatarType = BotAvatarType.Clover,
    face: BotAvatarFace? = null,
    state: BotAvatarState = BotAvatarState.Default,
    size: Dp = 64.dp,
    color: Color? = null,
    ink: Color? = null,
    brightness: Double = 1.0,
    saturation: Double = 1.5,
    speed: Double = 1.0,
    paused: Boolean = false,
    seed: Double? = null,
    shading: BotAvatarShading = BotAvatarShading.Plastic,
    shadow: Double = 0.35,
    highlight: Double = 1.3,
    depth: Double = 0.65,
    light: Double = 265.0,
    rim: Double = 0.5,
    spread: Double = 1.55,
    interactive: Boolean = true,
    turn: Double = 1.0,
    whirl: BotWhirl = BotWhirl(),
    jump: BotJumpConfig = BotJumpConfig(),
    contentDescription: String? = null,
) {
    val preset = type.preset
    val frozenTime = LocalEffectFrozenTime.current
    val reduced = rememberReducedMotion()
    val activity = rememberEffectActivity()
    val frozen = paused || speed <= 0
    val seedValue = remember(seed) { (seed ?: Random.nextDouble()).coerceIn(0.0, 1.0) }

    val picked = color ?: preset.color
    val plain = brightness == 1.0 && saturation == 1.0
    val bodyHsl = if (plain) shade(picked, 0.0) else shade(picked, (brightness.coerceIn(0.0, 2.0) - 1) * 0.35, (saturation.coerceIn(0.0, 2.0) - 1) * 0.5)
    val bodyColor = if (plain) picked else bodyHsl.toColor()
    val cfg = BotDrawConfig(
        type = type,
        face = face ?: preset.face,
        faceX = preset.faceX.toDouble(),
        faceY = preset.faceY.toDouble(),
        faceScale = preset.faceScale.toDouble(),
        color = bodyColor,
        hsl = bodyHsl,
        ink = ink ?: autoInk(bodyColor),
        shading = shading,
        shadow = shadow.coerceIn(0.0, 2.0),
        highlight = highlight.coerceIn(0.0, 2.0),
        depth = depth.coerceIn(0.2, 2.0),
        light = light,
        rim = rim.coerceIn(0.0, 2.0),
        spread = spread.coerceIn(0.4, 2.5),
        whirl = BotWhirl(
            whirl.strength.coerceIn(0.0, 2.0), whirl.size.coerceIn(0.6, 1.6), whirl.width.coerceIn(0.4, 2.0),
            whirl.length.coerceIn(0.4, 1.6), whirl.tilt.coerceIn(0.5, 1.8),
        ),
        still = frozen || reduced || frozenTime != null,
    )

    val sim = remember(seedValue) { BotSim(seedValue, state) }
    val renderer = remember { BotRenderer() }
    var frame by remember { mutableIntStateOf(0) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentInteractive by rememberUpdatedState(interactive)
    val currentSpeed by rememberUpdatedState(speed)

    sim.setState(state)
    sim.setTurn(turn.coerceIn(0.0, 2.0))
    sim.setJump(
        jump.copy(
            time = max(0.2, jump.time),
            spin = max(0.0, jump.spin.roundToInt().toDouble()),
            squashTime = max(0.05, jump.squashTime),
            groundTime = max(0.0, jump.groundTime),
            riseTime = max(0.05, jump.riseTime),
            clickSquashTime = max(0.05, jump.clickSquashTime),
        ),
    )

    // Snapshots: the sim replayed at 60 Hz to the frozen time, so a frame is reproducible.
    val replayed = remember(frozenTime, seedValue, state) {
        if (frozenTime == null) return@remember null
        BotSim(seedValue, state).apply {
            setTurn(turn)
            repeat((frozenTime * 60).roundToInt()) { update(1.0 / 60) }
        }.pose
    }
    val restPose = remember(state) { BotPose.rest(state) }

    val animating = frozenTime == null && !frozen && !reduced && activity.isActive
    LaunchedEffect(animating) {
        if (!animating) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == Long.MIN_VALUE) 0.0 else min(0.1, (now - last) / 1e9)
                last = now
                // The pointer's pull: full within a head width, gone by three.
                val p = BotAvatarPointer.position
                val c = coords[0]
                if (currentInteractive && p != null && c != null && c.isAttached) {
                    val boxPx = c.size.width.toDouble().coerceAtLeast(1.0)
                    val center = c.localToRoot(Offset(c.size.width / 2f, c.size.height / 2f))
                    val dx = (p.x - center.x) / boxPx
                    val dy = (p.y - center.y) / boxPx
                    val d = hypot(dx, dy)
                    val strength = if (d < 1) 1.0 else if (d > Reach) 0.0 else 1 - (d - 1) / (Reach - 1)
                    sim.setPointer(dx / max(1.0, d), dy / max(1.0, d), strength)
                } else {
                    sim.setPointer(0.0, 0.0, 0.0)
                }
                sim.update(dt * currentSpeed)
                frame++
            }
        }
    }

    val landed = BotForms.landed
    Box(
        modifier
            .then(activity.modifier)
            .size(size)
            .onGloballyPositioned { coords[0] = it }
            .semantics {
                this.contentDescription = contentDescription ?: "${preset.label} bot, ${state.label}"
                role = Role.Image
            }
            .pointerInput(interactive, frozen) {
                if (!interactive) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        val c = coords[0]
                        BotAvatarPointer.position = when {
                            event.type == PointerEventType.Exit -> null
                            c != null && c.isAttached -> c.localToRoot(change.position)
                            else -> null
                        }
                    }
                }
            }
            .pointerInput(interactive, frozen) {
                if (!interactive || frozen) return@pointerInput
                detectTapGestures(onTap = { sim.poke() })
            }
            .drawWithCache {
                val layer = obtainGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
                val dpr = density.toDouble()
                val boxPx = this.size.width
                val boxDp = boxPx / density
                val fullPx = (boxPx * Overscan).roundToInt()
                onDrawBehind {
                    frame
                    landed.intValue
                    val pose = replayed ?: if (animating) sim.pose else if (reduced) restPose else sim.pose
                    layer.topLeft = IntOffset(-(boxPx * (Overscan - 1) / 2).roundToInt(), -(boxPx * ((Overscan - 1) / 2 + Rise)).roundToInt())
                    layer.record(IntSize(fullPx, fullPx)) { renderer.draw(this, boxDp, pose, cfg, dpr) }
                    drawLayer(layer)
                }
            },
    )
}
