package com.burkido.kraft.effects.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.CubicBezier
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import com.burkido.kraft.effects.core.isDark
import com.burkido.kraft.effects.core.rememberEffectActivity
import com.burkido.kraft.effects.core.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Per-knob overrides on the type preset — every `VoiceBeam` prop past the basics. `null` keeps
 * the preset's value for the resolved type and theme.
 */
@Immutable
data class VoiceTuning(
    /** Input gain on the analysed audio; raise it for quiet sources. */
    val sensitivity: Double = 3.1,
    /** Noise gate, 0–1: below this is silence. */
    val threshold: Double = 0.015,
    /** Seconds to rise toward a louder level, and to settle after it drops. */
    val attack: Double = 0.325,
    val release: Double = 0.86,
    /** Period of the idle breathing, seconds. */
    val breatheDuration: Double = 5.2,
    /** Let the low / mid / high bands move the lobes independently. */
    val bands: Boolean = true,
    /** Seconds the morph into and out of processing takes. */
    val processingEase: Double = 0.6,
    val idle: Double? = null,
    val reach: Double? = null,
    val spread: Double? = null,
    val flow: Double? = null,
    val bend: Double? = null,
    val glowSize: Double? = null,
    val strokeOpacity: Double? = null,
    val innerOpacity: Double? = null,
    val bloomOpacity: Double? = null,
    val bandStrength: Double? = null,
    val bandWidth: Double? = null,
    val bandPosition: Double? = null,
    val bandCurve: Double? = null,
    val bandSpread: Double? = null,
    val bandSkew: Double? = null,
    val bandOffset: Double? = null,
    val bandTail: Double? = null,
    val bandTailPosition: Double? = null,
    val bandTailCurve: Double? = null,
    val bandTailOverflow: Double? = null,
    val bandAberration: Double? = null,
    val glowWidth: Double? = null,
    val glowHeight: Double? = null,
    val lobeSpacing: Double? = null,
    val rangeWidth: Double? = null,
    val rangeHeight: Double? = null,
    val softness: Double? = null,
    val coreSize: Double? = null,
    val coreLight: Double? = null,
    val coreLightWidth: Double? = null,
    val coreLightHeight: Double? = null,
    val strokeScale: Double? = null,
    val innerScale: Double? = null,
    val innerHeight: Double? = null,
    val bloomScale: Double? = null,
    val bloomHeight: Double? = null,
    val processingDuration: Double? = null,
    val processingLevel: Double? = null,
    val processingTravel: Double? = null,
    val processingCurve: Double? = null,
    val cornerFollow: Double? = null,
    val hueRange: Double? = null,
    val hueDuration: Double? = null,
)

/**
 * Sound-reactive glow (`voice-glow`): a centred, colourful beam along the bottom edge of the
 * element that rises and blooms with the level of a voice, then — with [processing] — gathers
 * into one beam that sweeps the range while the reply is thought through.
 *
 * Drive it with a [microphone] (see [rememberMicrophone]) or yourself: [level] is sampled every
 * frame with the beam's own clock in seconds and returns 0–1. The glow is layered as on the web:
 * [background] (the element's surface), then the inner light, the 1 dp edge stroke, the blurred
 * bloom and the band line along the bent ceiling, then [content] above all of it.
 */
@Composable
fun VoiceBeam(
    modifier: Modifier = Modifier,
    type: VoiceBeamType = VoiceBeamType.Default,
    level: (seconds: Double) -> Float = { 0f },
    microphone: Microphone? = null,
    processing: Boolean = false,
    colorVariant: VoiceColorVariant = VoiceColorVariant.Colorful,
    colors: List<Color>? = null,
    bandColors: VoiceBandColors? = null,
    theme: EffectTheme = EffectTheme.Dark,
    staticColors: Boolean = false,
    active: Boolean = true,
    paused: Boolean = false,
    borderRadius: Dp = 16.dp,
    scale: Double? = null,
    strength: Double? = null,
    brightness: Double? = null,
    saturation: Double? = null,
    tuning: VoiceTuning = VoiceTuning(),
    onLevel: ((Float) -> Unit)? = null,
    background: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = theme.isDark()
    val reduced = rememberReducedMotion()
    val setup = remember(type, dark, colorVariant, colors, bandColors, staticColors, borderRadius, scale, strength, brightness, saturation, tuning, processing, reduced) {
        resolveVoice(type, dark, colorVariant, colors, bandColors, staticColors, borderRadius.value.toDouble(), scale, strength, brightness, saturation, tuning, processing, reduced)
    }

    val frozen = LocalEffectFrozenTime.current
    val activity = rememberEffectActivity()
    val fade = remember { Animatable(if (frozen != null && active && !paused) 1f else 0f) }
    // The fade is a CSS animation the web pauses with everything else (`animation-play-state`), so
    // a beam paused on arrival stays dark until it plays, and a paused fade resumes where it held.
    LaunchedEffect(active, frozen, paused) {
        when {
            frozen != null -> fade.snapTo(if (active && !paused) 1f else 0f)
            paused -> Unit
            active -> fade.animateTo(1f, tween((600 * (1f - fade.value)).toInt(), easing = CubicBezier.Ease.easing))
            else -> fade.animateTo(0f, tween((500 * fade.value).toInt(), easing = CubicBezier.Ease.easing))
        }
    }

    val driver = remember { VoiceDriver() }
    val frame = remember { VoiceFrame() }
    val analysis = remember { VoiceAnalysis() }
    val size = remember { DoubleArray(2) }
    var tick by remember { mutableIntStateOf(0) }
    val currentLevel by rememberUpdatedState(level)
    val currentOnLevel by rememberUpdatedState(onLevel)
    val currentSetup by rememberUpdatedState(setup)
    val currentMic by rememberUpdatedState(microphone)

    fun step(dt: Double) {
        val mic = currentMic?.takeIf { it.state == MicrophoneState.Live }
        val read = if (mic != null) analysis.also { mic.analyser.analyse(it) } else null
        val manual = if (read == null) currentLevel(driver.t + dt).toDouble() else 0.0
        driver.step(currentSetup.config, dt, manual, read, size[0], size[1], frame)
        tick++
        currentOnLevel?.invoke(frame.level.toFloat())
    }

    // The shared loop's per-instance work: one step per frame while on screen, lit and unpaused.
    val lit by remember { derivedStateOf { fade.targetValue > 0f || fade.value > 0f } }
    val running = frozen == null && activity.isActive && lit && !paused
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == Long.MIN_VALUE) 1.0 / 60 else min(0.05, (now - last) / 1e9)
                last = now
                step(dt)
            }
        }
    }
    // Paused: the frame holds, but a changed setting still reshapes it (a step of zero time).
    LaunchedEffect(paused, setup) {
        if (paused && frozen == null) step(0.0)
    }

    val density = LocalDensity.current.density
    Box(modifier.then(activity.modifier).onSizeChanged {
        size[0] = it.width / density.toDouble()
        size[1] = it.height / density.toDouble()
    }) {
        background()
        Spacer(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val painter = VoicePainter(this, setup)
                    var simulatedFor: Pair<Double, Long>? = null
                    onDrawBehind {
                        tick // Every driver step invalidates this draw, never the composition.
                        val alpha = fade.value
                        if (alpha <= 0f) return@onDrawBehind
                        if (frozen != null) {
                            // Snapshots replay the driver from rest at 60 fps up to the frozen time.
                            val key = frozen to (size[0] * 1000 + size[1]).toLong()
                            if (simulatedFor != key) {
                                simulatedFor = key
                                val replay = VoiceDriver()
                                val steps = (frozen * 60).roundToInt()
                                repeat(steps) {
                                    replay.step(setup.config, 1.0 / 60, currentLevel(replay.t + 1.0 / 60).toDouble(), null, size[0], size[1], frame)
                                }
                            }
                        }
                        painter.draw(this, frame, alpha)
                    }
                },
        )
        content()
    }
}

/** Everything resolved from the props: the driver's config and the layers' paint. */
internal class VoiceSetup(val config: VoiceConfig, val paint: VoicePaintSpec)

/** The stylesheet's inputs (`generateVoiceBeamCSS`), resolved. Lengths in dp. */
internal class VoicePaintSpec(
    val dark: Boolean,
    val radius: Double,
    val strokeOpacity: Double,
    val innerOpacity: Double,
    val bloomOpacity: Double,
    val innerShadow: Color,
    val colors: List<Color>,
    val brightness: Double,
    val saturation: Double,
    val hueBase: Double,
    val strength: Double,
    val bloomBlur: Double,
    val coreBlur: Double,
    val cornerFade: Double,
    val insetBlur: Double,
    val fade: Double,
    val strokeW: Double, val strokeH: Double,
    val innerW: Double, val innerH: Double,
    val bloomW: Double, val bloomH: Double,
    val coreSize: Double,
    val rangeWidth: Double,
    val rangeHeight: Double,
    val coreLight: Double,
    val coreLightWidth: Double,
    val coreLightHeight: Double,
    val scale: Double,
    val bandColors: VoiceBandColors,
    val bandStrength: Double,
    val bandWidth: Double,
    val bandAberration: Double,
    val bandTail: Double,
)

private fun resolveVoice(
    type: VoiceBeamType,
    dark: Boolean,
    variant: VoiceColorVariant,
    colors: List<Color>?,
    bandColors: VoiceBandColors?,
    staticColors: Boolean,
    radius: Double,
    scaleProp: Double?,
    strengthProp: Double?,
    brightnessProp: Double?,
    saturationProp: Double?,
    t: VoiceTuning,
    processing: Boolean,
    reduced: Boolean,
): VoiceSetup {
    val d = resolveVoiceDefaults(type, dark)
    val sc = max(0.05, scaleProp ?: d.scale)
    val preset = if (dark) DarkVoiceTheme else LightVoiceTheme
    val style = resolveVoiceStyle(type, dark)
    val glowSize = t.glowSize ?: d.glowSize
    val glowWidth = (t.glowWidth ?: d.glowWidth) * sc
    val glowHeight = (t.glowHeight ?: d.glowHeight) * sc
    val softness = t.softness ?: d.softness
    val fade = jsRound(max(40.0, min(95.0, 70 * softness)))
    val strokeScale = t.strokeScale ?: d.strokeScale
    val innerScale = t.innerScale ?: d.innerScale
    val innerHeight = t.innerHeight ?: d.innerHeight
    val bloomScale = t.bloomScale ?: d.bloomScale
    val bloomHeight = t.bloomHeight ?: d.bloomHeight
    val isMono = variant == VoiceColorVariant.Mono
    val monoMul = if (isMono) 0.6 else 1.0
    val palette = voicePalette(variant, dark).mapIndexed { i, c -> colors?.getOrNull(i) ?: c }
    val bandStrength = t.bandStrength ?: d.bandStrength
    val bandWidth = (t.bandWidth ?: d.bandWidth) * sc
    val bandTail = t.bandTail ?: d.bandTail
    val coreLight = (t.coreLight ?: d.coreLight).coerceIn(0.0, 3.0)
    val rangeWidth = (t.rangeWidth ?: d.rangeWidth) * sc
    val rangeHeight = (t.rangeHeight ?: d.rangeHeight) * sc

    val config = VoiceConfig(
        sensitivity = max(0.0, t.sensitivity),
        threshold = t.threshold.coerceIn(0.0, 0.95),
        attack = max(0.0, t.attack),
        release = max(0.0, t.release),
        idle = (t.idle ?: d.idle).coerceIn(0.0, 1.0),
        breatheDuration = max(0.2, t.breatheDuration),
        reach = max(0.0, t.reach ?: d.reach),
        spread = max(0.0, t.spread ?: d.spread),
        bands = t.bands,
        flow = (t.flow ?: d.flow) * sc,
        lobeSpacing = max(0.1, (t.lobeSpacing ?: d.lobeSpacing) * sc),
        bend = max(0.0, (t.bend ?: d.bend) * sc),
        bandStrength = max(0.0, bandStrength),
        bandWidth = max(0.0, bandWidth),
        bandPosition = max(0.0, t.bandPosition ?: d.bandPosition),
        bandCurve = max(0.3, t.bandCurve ?: d.bandCurve),
        bandSpread = max(0.05, t.bandSpread ?: d.bandSpread),
        bandSkew = (t.bandSkew ?: d.bandSkew).coerceIn(-0.9, 0.9),
        bandOffset = (t.bandOffset ?: d.bandOffset) * sc,
        bandTail = bandTail.coerceIn(0.0, 1.5),
        bandTailPosition = (t.bandTailPosition ?: d.bandTailPosition).coerceIn(0.0, 0.98),
        bandTailCurve = max(0.5, t.bandTailCurve ?: d.bandTailCurve),
        bandTailOverflow = max(0.0, (t.bandTailOverflow ?: d.bandTailOverflow) * sc),
        bandAberration = (t.bandAberration ?: d.bandAberration).coerceIn(0.0, 1.0),
        rangeWidth = rangeWidth,
        rangeHeight = rangeHeight,
        scale = sc,
        radius = radius,
        processing = processing,
        processingDuration = max(0.05, t.processingDuration ?: d.processingDuration),
        processingLevel = (t.processingLevel ?: d.processingLevel).coerceIn(0.0, 1.0),
        processingEase = max(0.05, t.processingEase),
        processingTravel = max(0.0, t.processingTravel ?: d.processingTravel),
        processingCurve = max(1.0, t.processingCurve ?: d.processingCurve),
        cornerFollow = (t.cornerFollow ?: d.cornerFollow).coerceIn(0.0, 1.0),
        hueRange = max(0.0, t.hueRange ?: preset.hueRange),
        hueDuration = max(0.5, t.hueDuration ?: preset.hueDuration),
        staticColors = isMono || staticColors,
        reducedMotion = reduced,
    )
    val paint = VoicePaintSpec(
        dark = dark,
        radius = radius,
        strokeOpacity = preset.strokeOpacity * (t.strokeOpacity ?: d.strokeOpacity) * monoMul,
        innerOpacity = preset.innerOpacity * (t.innerOpacity ?: d.innerOpacity) * monoMul,
        bloomOpacity = preset.bloomOpacity * (t.bloomOpacity ?: d.bloomOpacity) * monoMul,
        innerShadow = preset.innerShadow,
        colors = palette,
        brightness = brightnessProp ?: style.brightness ?: preset.brightness,
        saturation = saturationProp ?: style.saturation ?: preset.saturation,
        hueBase = preset.hueBase,
        strength = (strengthProp ?: style.strength ?: preset.strength).coerceIn(0.0, 1.0),
        bloomBlur = scaleBlur(10.0, glowSize * sc),
        coreBlur = scaleBlur(8.0, glowSize * sc),
        cornerFade = jsRound(28 * sc * 10) / 10,
        insetBlur = jsRound(9 * sc * 10) / 10,
        fade = fade,
        strokeW = glowWidth * strokeScale, strokeH = glowHeight * strokeScale,
        innerW = glowWidth * 0.9 * innerScale, innerH = glowHeight * 0.9 * innerScale * innerHeight,
        bloomW = glowWidth * 1.15 * bloomScale, bloomH = glowHeight * 1.5 * bloomScale * bloomHeight,
        coreSize = (t.coreSize ?: d.coreSize) * sc,
        rangeWidth = rangeWidth,
        rangeHeight = rangeHeight,
        coreLight = coreLight,
        coreLightWidth = t.coreLightWidth ?: d.coreLightWidth,
        coreLightHeight = t.coreLightHeight ?: d.coreLightHeight,
        scale = sc,
        bandColors = bandColors ?: defaultBandColors(dark),
        bandStrength = bandStrength,
        bandWidth = bandWidth,
        bandAberration = config.bandAberration,
        bandTail = bandTail,
    )
    return VoiceSetup(config, paint)
}

/** A tuned blur scaled by `glowSize`, never below 0.5 so a layer keeps a soft edge. */
private fun scaleBlur(px: Double, glowSize: Double): Double = max(0.5, jsRound(px * glowSize * 100) / 100)

private fun jsRound(x: Double): Double = kotlin.math.floor(x + 0.5)
