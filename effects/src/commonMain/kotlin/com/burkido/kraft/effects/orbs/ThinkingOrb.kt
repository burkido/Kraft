package com.burkido.kraft.effects.orbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.jsRound
import com.burkido.kraft.effects.core.isDark
import com.burkido.kraft.effects.core.rememberEffectActivity
import com.burkido.kraft.effects.core.rememberEffectTime
import com.burkido.kraft.effects.core.rememberReducedMotion
import com.burkido.kraft.effects.core.LocalEffectFrozenTime

/**
 * Orbs that think while you wait — the port of thinking-orbs' `<ThinkingOrb>`.
 *
 * Nine [OrbState]s, each a dotted 3D figure that is rotated, depth-shaded and z-sorted every
 * frame. [size] picks the tuned geometry (64 or 20; 32 interpolates); [displaySize] scales the
 * drawing — not a bitmap — so any size stays sharp. [color] tints the grey ink ramp. All orbs share
 * one clock, so identical orbs stay in phase; reduced motion shows a static frame.
 */
@Composable
fun ThinkingOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: OrbSize = OrbSize.S64,
    theme: EffectTheme = EffectTheme.Auto,
    speed: Double = 1.0,
    paused: Boolean = false,
    color: Color? = null,
    displaySize: Dp = size.px.dp,
) {
    val resolved = remember(state, size) { resolvePreset(state, size) }
    val dark = theme.isDark()
    val reduced = rememberReducedMotion()
    val activity = rememberEffectActivity()
    val time = rememberEffectTime(
        speed = resolved.speed * speed,
        running = activity.isActive && !paused && !reduced,
    )
    val frozen = LocalEffectFrozenTime.current
    val frame = remember { OrbFrame() }

    Canvas(
        modifier
            .then(activity.modifier)
            .size(displaySize)
            .semantics { contentDescription = state.label },
    ) {
        val t = when {
            frozen != null -> frozen
            reduced -> 0.6
            else -> time.seconds
        }
        buildFrame(resolved.mode, size.px.toDouble(), t, resolved.opts, frame)
        val scale = this.size.width / size.px
        scale(scale, pivot = Offset.Zero) {
            drawOrbFrame(frame, dark, color)
        }
    }
}

/** Lines first (butt caps), then dots far → near, in the web painter's ink. */
internal fun DrawScope.drawOrbFrame(frame: OrbFrame, dark: Boolean, tint: Color?) {
    val lines = frame.lines
    for (i in 0 until frame.lineCount) {
        val o = i * 7
        drawLine(
            color = ink(lines[o + 4], lines[o + 5], dark, tint),
            start = Offset(lines[o].toFloat(), lines[o + 1].toFloat()),
            end = Offset(lines[o + 2].toFloat(), lines[o + 3].toFloat()),
            strokeWidth = lines[o + 6].toFloat(),
            cap = StrokeCap.Butt,
        )
    }
    val dots = frame.dots
    for (i in 0 until frame.dotCount) {
        val o = i * 6
        drawCircle(
            color = ink(dots[o + 4], dots[o + 5], dark, tint),
            radius = dots[o + 3].toFloat(),
            center = Offset(dots[o].toFloat(), dots[o + 1].toFloat()),
        )
    }
}

/**
 * `inkColor()`: ink value `white` (0 = darkest ink on paper), mirrored on dark substrates. A tint
 * keeps the ramp as a ramp on the tint — towards black with depth on dark, towards white on light.
 */
private fun ink(white: Double, alpha: Double, dark: Boolean, tint: Color?): Color {
    val w = white.coerceIn(0.0, 1.0)
    val a = alpha.toFloat().coerceIn(0f, 1f)
    if (tint == null) {
        val g = jsRound((if (dark) 1 - w else w) * 255).toInt()
        return Color(g, g, g, (a * 255).toInt().coerceIn(0, 255)).copy(alpha = a)
    }
    fun ramp(c: Float): Int {
        val v = c * 255.0
        return jsRound(if (dark) v * (1 - w) else v + (255 - v) * w).toInt()
    }
    return Color(ramp(tint.red), ramp(tint.green), ramp(tint.blue)).copy(alpha = a)
}
