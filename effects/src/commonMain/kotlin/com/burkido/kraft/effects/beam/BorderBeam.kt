package com.burkido.kraft.effects.beam

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.Dp
import com.burkido.kraft.effects.core.CubicBezier
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import com.burkido.kraft.effects.core.isDark
import com.burkido.kraft.effects.core.rememberEffectActivity
import com.burkido.kraft.effects.core.rememberEffectTime
import com.burkido.kraft.effects.core.rememberReducedMotion

/** The beam types (`size` prop). */
enum class BeamSize(internal val key: String) {
    /** Small rotating beam for pills and icon buttons. */
    Sm("sm"),

    /** Rotating beam for cards and inputs (default). */
    Md("md"),

    /** A glow travelling along the bottom edge. */
    Line("line"),

    /** A breathing halo outside the element. */
    PulseOutside("pulse-outside"),

    /** A breathing glow inside the element. */
    PulseInner("pulse-inner"),
}

/** The colour palettes (`colorVariant` prop). */
enum class BeamColorVariant(internal val key: String) { Colorful("colorful"), Mono("mono"), Ocean("ocean"), Sunset("sunset") }

/**
 * A soft glow that rides the border of [content] — the port of border-beam's `<BorderBeam>`.
 *
 * Defaults match the web component: `md`, colorful, dark theme, 1.96 s rotation (3.1 s for
 * `line`, 2.3 s for the pulses), ±30° hue drift. [active] fades the beam in (0.6 s) and out
 * (0.5 s) and then calls [onActivate] / [onDeactivate]; [paused] holds the current frame. The beam
 * draws over [content] and clips it to [borderRadius].
 */
@Composable
fun BorderBeam(
    modifier: Modifier = Modifier,
    size: BeamSize = BeamSize.Md,
    colorVariant: BeamColorVariant = BeamColorVariant.Colorful,
    theme: EffectTheme = EffectTheme.Dark,
    staticColors: Boolean = false,
    duration: Double? = null,
    active: Boolean = true,
    paused: Boolean = false,
    borderRadius: Dp? = null,
    brightness: Double? = null,
    saturation: Double? = null,
    hueRange: Double = 30.0,
    strength: Float = 1f,
    onActivate: (() -> Unit)? = null,
    onDeactivate: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val spec = rememberBeamSpec()
    val dark = theme.isDark()
    val preset = spec?.sizeThemePresets?.get(size.key)?.get(if (dark) "dark" else "light")
    val radius = borderRadius?.value ?: spec?.sizePresets?.get(size.key)?.borderRadius?.toFloat() ?: 16f
    val isStatic = colorVariant == BeamColorVariant.Mono || staticColors
    val periodSeconds = duration ?: when (size) {
        BeamSize.Line -> 3.1
        BeamSize.PulseInner, BeamSize.PulseOutside -> 2.3
        else -> 1.96
    }
    val finalHueRange = if (size == BeamSize.Line) minOf(hueRange, 13.0) else hueRange
    val finalBrightness = brightness ?: preset?.brightness ?: (spec?.defaults?.brightnessFallback ?: 1.3)
    val finalSaturation = saturation ?: preset?.saturation ?: 1.0

    val frozen = LocalEffectFrozenTime.current != null
    val fade = remember { Animatable(if (frozen && active) 1f else 0f) }
    val currentOnActivate by rememberUpdatedState(onActivate)
    val currentOnDeactivate by rememberUpdatedState(onDeactivate)
    LaunchedEffect(active, frozen) {
        if (frozen) {
            fade.snapTo(if (active) 1f else 0f)
            return@LaunchedEffect
        }
        if (active) {
            fade.animateTo(1f, tween(600, easing = CubicBezier.Ease.easing))
            currentOnActivate?.invoke()
        } else if (fade.value > 0f) {
            fade.animateTo(0f, tween(500, easing = CubicBezier.Ease.easing))
            currentOnDeactivate?.invoke()
        }
    }

    val isPulse = size == BeamSize.PulseInner || size == BeamSize.PulseOutside
    // Like the web, only the pulse family honours reduced motion.
    val reducedMotion = isPulse && rememberReducedMotion()
    val activity = rememberEffectActivity()
    val beamVisible by remember { derivedStateOf { fade.value > 0f } }
    val time = rememberEffectTime(running = activity.isActive && !paused && !reducedMotion && (active || beamVisible))
    val shape = RoundedCornerShape(Dp(radius))

    Box(
        modifier
            .then(activity.modifier)
            // pulse-outside's wrapper is `overflow: visible`: its halo spills past the element.
            .then(if (size == BeamSize.PulseOutside) Modifier else Modifier.clip(shape))
            .drawWithCache {
                val painter: BeamPainter? = spec?.let {
                    when (size) {
                        BeamSize.Sm, BeamSize.Md -> RotateBeamPainter(
                            cache = this,
                            config = it.rotateConfig(size, colorVariant, dark, radius, strength),
                            durationSeconds = periodSeconds,
                            isStatic = isStatic,
                            hueRange = finalHueRange,
                            hueShiftPeriod = it.defaults.rotateHueShiftPeriod,
                            brightness = finalBrightness,
                            saturation = finalSaturation,
                        )
                        BeamSize.Line -> LineBeamPainter(
                            cache = this, spec = it, variant = colorVariant, dark = dark, radius = radius,
                            durationSeconds = periodSeconds, isStatic = isStatic, hueRange = finalHueRange,
                            brightness = finalBrightness, saturation = finalSaturation, strength = strength,
                        )
                        BeamSize.PulseInner, BeamSize.PulseOutside -> PulseBeamPainter(
                            cache = this, spec = it, outside = size == BeamSize.PulseOutside, variant = colorVariant,
                            dark = dark, radius = radius, durationSeconds = periodSeconds, isStatic = isStatic,
                            reducedMotion = reducedMotion, brightness = finalBrightness, saturation = finalSaturation,
                            strength = strength,
                        )
                    }
                }
                onDrawWithContent {
                    val alpha = fade.value
                    if (painter == null || alpha <= 0f) {
                        drawContent()
                        return@onDrawWithContent
                    }
                    val t = time.seconds
                    painter.drawBehind(this, t, alpha)
                    drawContent()
                    painter.drawOver(this, t, alpha)
                }
            },
        propagateMinConstraints = true,
    ) {
        content()
    }
}

@Composable
private fun rememberBeamSpec(): BeamSpec? {
    BeamSpec.current?.let { return it }
    return produceState(BeamSpec.current) { value = BeamSpec.load() }.value
}
