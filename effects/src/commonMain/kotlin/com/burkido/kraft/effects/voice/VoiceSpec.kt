package com.burkido.kraft.effects.voice

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * The host a [VoiceBeam] is tuned for (`voice-glow`'s `type`):
 * - [Default]: a chat input or card, ~350 dp wide.
 * - [Pill]: a ~150×44 recording pill — glow pulled in, shallower bend, thin band.
 * - [Mobile]: the bottom of a phone screen — wider range, taller rise, broad band.
 */
enum class VoiceBeamType { Default, Pill, Mobile }

/** The eight palettes voice-glow shares with border-beam. */
enum class VoiceColorVariant { Colorful, Mono, Ocean, Sunset, Forest, Candy, Ice, Gold }

/**
 * Every geometry and response knob a type preset may retune (`VoiceGeometry`). Values are the
 * web's, in CSS px (dp here) for a scale of 1.
 */
@Immutable
data class VoiceGeometry(
    val scale: Double = 1.0,
    val glowSize: Double = 1.0,
    val processingDuration: Double = 1.1,
    val processingLevel: Double = 0.55,
    val processingTravel: Double = 1.55,
    val processingCurve: Double = 2.1,
    val cornerFollow: Double = 0.45,
    val strokeOpacity: Double = 1.0,
    val innerOpacity: Double = 1.0,
    val bloomOpacity: Double = 1.0,
    val idle: Double = 0.18,
    val reach: Double = 1.2,
    val spread: Double = 1.05,
    val flow: Double = 48.0,
    val bend: Double = 60.0,
    val bandStrength: Double = 1.55,
    val bandWidth: Double = 2.15,
    val bandPosition: Double = 0.35,
    val bandCurve: Double = 1.75,
    val bandSpread: Double = 0.87,
    val bandSkew: Double = 0.12,
    val bandOffset: Double = -27.0,
    val bandTail: Double = 0.59,
    val bandTailPosition: Double = 0.67,
    val bandTailCurve: Double = 2.4,
    val bandTailOverflow: Double = 15.0,
    val bandAberration: Double = 0.89,
    val distortion: Double = 0.62,
    val distortionDetail: Double = 2.3,
    val glowWidth: Double = 0.65,
    val glowHeight: Double = 1.25,
    val lobeSpacing: Double = 0.85,
    val rangeWidth: Double = 0.75,
    val rangeHeight: Double = 1.0,
    val softness: Double = 1.07,
    val coreSize: Double = 1.0,
    val coreLight: Double = 0.0,
    val coreLightWidth: Double = 1.0,
    val coreLightHeight: Double = 1.0,
    val strokeScale: Double = 1.0,
    val innerScale: Double = 1.0,
    val innerHeight: Double = 1.0,
    val bloomScale: Double = 1.0,
    val bloomHeight: Double = 1.0,
)

/** The tuned defaults — the `default` type, a ~350 px chat input. */
val VoiceDefaults = VoiceGeometry()

/** `voiceTypePresets`, applied to [VoiceDefaults]. */
private fun VoiceGeometry.withType(type: VoiceBeamType): VoiceGeometry = when (type) {
    VoiceBeamType.Default -> this
    VoiceBeamType.Pill -> copy(
        scale = 0.45, glowSize = 0.95, strokeOpacity = 1.2, innerOpacity = 0.85, reach = 1.35, spread = 1.1,
        flow = 0.0, bend = 23.0, bandStrength = 1.55, bandWidth = 1.85, bandCurve = 1.95, bandSpread = 0.38,
        bandOffset = -16.0, bandTail = 0.0, processingTravel = 2.0, cornerFollow = 0.0, distortion = 0.45,
        distortionDetail = 3.0, glowWidth = 0.65, glowHeight = 0.95, lobeSpacing = 0.45, rangeWidth = 0.8,
        rangeHeight = 0.7, softness = 0.88, coreSize = 0.25, strokeScale = 1.25, innerScale = 0.95,
        bloomScale = 1.05, bloomHeight = 2.25,
    )
    VoiceBeamType.Mobile -> copy(
        scale = 1.25, spread = 0.45, reach = 3.0, flow = 60.0, bend = 70.0, bandWidth = 2.4, bandCurve = 1.55,
        bandSpread = 0.9, bandOffset = -50.0, bandTail = 0.62, bandTailPosition = 0.42, bandTailCurve = 2.7,
        bandTailOverflow = 22.0, processingDuration = 1.05, processingLevel = 0.35, processingTravel = 1.0,
        cornerFollow = 0.4, bandStrength = 1.8, distortionDetail = 2.0, glowWidth = 1.15, glowHeight = 2.1,
        lobeSpacing = 1.35, rangeWidth = 1.25, rangeHeight = 1.2, softness = 1.1,
    )
}

/**
 * `resolveVoiceDefaults(type, theme)`: the defaults with the type's overrides and, for what the
 * type leaves alone, the light theme's own (taller, narrower, a lit epicentre, a stronger band).
 */
fun resolveVoiceDefaults(type: VoiceBeamType = VoiceBeamType.Default, dark: Boolean = true): VoiceGeometry {
    var g = VoiceDefaults
    if (!dark) {
        // Only where the type preset does not set its own (pill sets reach and spread; mobile sets both).
        if (type == VoiceBeamType.Default) g = g.copy(reach = 1.8, spread = 0.8)
        g = g.copy(coreLight = 1.8)
    }
    g = g.withType(type)
    if (!dark) g = g.copy(bandStrength = if (type == VoiceBeamType.Pill) 2.0 else 1.7)
    return g
}

/** `themePresets`: per-theme layer opacities, inner shadow and colour grading. */
internal class VoiceThemeColors(
    val strokeOpacity: Double,
    val innerOpacity: Double,
    val bloomOpacity: Double,
    val innerShadow: Color,
    val saturation: Double,
    val brightness: Double,
    val hueRange: Double = 24.0,
    val hueDuration: Double = 12.0,
    val hueBase: Double = 0.0,
    val strength: Double = 1.0,
)

internal val DarkVoiceTheme = VoiceThemeColors(
    strokeOpacity = 1.16, innerOpacity = 0.47, bloomOpacity = 0.89,
    innerShadow = Color.White.copy(alpha = 0.1f), saturation = 1.2, brightness = 1.1,
)

internal val LightVoiceTheme = VoiceThemeColors(
    strokeOpacity = 1.2, innerOpacity = 0.85, bloomOpacity = 0.5,
    innerShadow = Color.Black.copy(alpha = 0.08f), saturation = 1.6, brightness = 0.95,
    hueRange = 40.0, hueDuration = 8.5, hueBase = 5.0, strength = 0.8,
)

/** `resolveVoiceStyle`: a type's brightness / saturation / strength lift, where it has one. */
internal class VoiceTypeStyle(val brightness: Double? = null, val saturation: Double? = null, val strength: Double? = null)

internal fun resolveVoiceStyle(type: VoiceBeamType, dark: Boolean): VoiceTypeStyle = when {
    !dark && type == VoiceBeamType.Mobile -> VoiceTypeStyle(strength = 1.0)
    !dark -> VoiceTypeStyle()
    type == VoiceBeamType.Default -> VoiceTypeStyle(brightness = 1.15)
    type == VoiceBeamType.Pill -> VoiceTypeStyle(brightness = 1.35, saturation = 1.5)
    else -> VoiceTypeStyle(strength = 1.0, brightness = 1.2, saturation = 1.5)
}

/**
 * One lobe of the glow, in px for a ~350 px element: its resting offset from the centre, its
 * radii, and the frequency band that drives it (0 low, 1 mid, 2 high).
 */
internal class VoiceLobe(val x: Double, val w: Double, val h: Double, val band: Int)

internal val VoiceLobes = listOf(
    VoiceLobe(0.0, 74.0, 46.0, 0),
    VoiceLobe(-36.0, 54.0, 40.0, 1),
    VoiceLobe(36.0, 54.0, 40.0, 1),
    VoiceLobe(-72.0, 48.0, 32.0, 2),
    VoiceLobe(72.0, 48.0, 32.0, 2),
    VoiceLobe(-108.0, 42.0, 26.0, 1),
    VoiceLobe(108.0, 42.0, 26.0, 1),
)

/** Resting distance between neighbouring lobes, and the ring they flow around (one full turn). */
internal const val LobeSpacing = 36.0
internal val LobeSpan = LobeSpacing * VoiceLobes.size

private fun rgb(r: Int, g: Int, b: Int) = Color(r, g, b)

/** Seven colours per palette and theme, centre lobe first, then the pairs outward. */
internal fun voicePalette(variant: VoiceColorVariant, dark: Boolean): List<Color> = when (variant) {
    VoiceColorVariant.Colorful -> if (dark) {
        listOf(rgb(255, 70, 120), rgb(60, 190, 255), rgb(175, 70, 255), rgb(60, 220, 130), rgb(255, 150, 40), rgb(90, 100, 255), rgb(40, 200, 190))
    } else {
        listOf(rgb(255, 201, 21), rgb(126, 196, 255), rgb(180, 40, 230), rgb(235, 100, 160), rgb(255, 176, 122), rgb(154, 160, 255), rgb(127, 217, 238))
    }
    VoiceColorVariant.Mono -> if (dark) {
        listOf(rgb(215, 215, 215), rgb(180, 180, 180), rgb(190, 190, 190), rgb(160, 160, 160), rgb(170, 170, 170), rgb(150, 150, 150), rgb(155, 155, 155))
    } else {
        listOf(rgb(60, 60, 60), rgb(90, 90, 90), rgb(85, 85, 85), rgb(110, 110, 110), rgb(105, 105, 105), rgb(125, 125, 125), rgb(120, 120, 120))
    }
    VoiceColorVariant.Ocean -> if (dark) {
        listOf(rgb(80, 140, 255), rgb(40, 200, 230), rgb(120, 90, 255), rgb(30, 170, 210), rgb(160, 80, 240), rgb(60, 110, 255), rgb(40, 190, 180))
    } else {
        listOf(rgb(40, 100, 240), rgb(20, 160, 200), rgb(90, 60, 230), rgb(20, 130, 180), rgb(130, 50, 220), rgb(40, 80, 230), rgb(20, 150, 150))
    }
    VoiceColorVariant.Sunset -> if (dark) {
        listOf(rgb(255, 110, 60), rgb(255, 180, 40), rgb(255, 60, 90), rgb(255, 210, 80), rgb(240, 70, 140), rgb(255, 140, 50), rgb(230, 50, 110))
    } else {
        listOf(rgb(235, 80, 30), rgb(230, 150, 10), rgb(230, 30, 70), rgb(225, 175, 30), rgb(215, 40, 110), rgb(235, 110, 20), rgb(205, 30, 90))
    }
    VoiceColorVariant.Forest -> if (dark) {
        listOf(rgb(70, 220, 120), rgb(40, 200, 180), rgb(140, 230, 80), rgb(30, 170, 140), rgb(190, 235, 70), rgb(50, 190, 110), rgb(30, 150, 120))
    } else {
        listOf(rgb(30, 170, 80), rgb(20, 150, 130), rgb(90, 180, 30), rgb(20, 130, 100), rgb(130, 180, 20), rgb(30, 150, 80), rgb(20, 120, 90))
    }
    VoiceColorVariant.Candy -> if (dark) {
        listOf(rgb(255, 90, 170), rgb(255, 120, 220), rgb(210, 80, 255), rgb(255, 150, 190), rgb(180, 110, 255), rgb(255, 70, 140), rgb(230, 100, 240))
    } else {
        listOf(rgb(235, 40, 140), rgb(230, 70, 190), rgb(180, 40, 230), rgb(235, 100, 160), rgb(150, 70, 230), rgb(230, 30, 110), rgb(200, 60, 210))
    }
    VoiceColorVariant.Ice -> if (dark) {
        listOf(rgb(150, 230, 255), rgb(90, 200, 255), rgb(190, 240, 255), rgb(120, 190, 255), rgb(160, 220, 250), rgb(80, 170, 255), rgb(200, 235, 255))
    } else {
        listOf(rgb(30, 160, 220), rgb(20, 130, 210), rgb(60, 180, 230), rgb(40, 120, 220), rgb(50, 160, 220), rgb(20, 110, 220), rgb(70, 170, 230))
    }
    VoiceColorVariant.Gold -> if (dark) {
        listOf(rgb(255, 200, 70), rgb(255, 170, 40), rgb(255, 220, 110), rgb(240, 150, 30), rgb(255, 235, 140), rgb(230, 160, 40), rgb(250, 210, 90))
    } else {
        listOf(rgb(200, 140, 10), rgb(190, 120, 0), rgb(210, 160, 30), rgb(180, 110, 0), rgb(205, 170, 40), rgb(175, 115, 5), rgb(195, 150, 20))
    }
}

/** The band's ridge colour and its three fringes. */
@Immutable
class VoiceBandColors(val core: Color, val above: Color, val mid: Color, val below: Color)

internal fun defaultBandColors(dark: Boolean) = if (dark) {
    VoiceBandColors(core = rgb(255, 255, 255), above = rgb(255, 70, 80), mid = rgb(90, 255, 150), below = rgb(80, 140, 255))
} else {
    VoiceBandColors(core = rgb(197, 139, 255), above = rgb(255, 122, 182), mid = rgb(126, 196, 255), below = rgb(45, 255, 171))
}

/**
 * The demo site's synthetic voice: syllables (~3 Hz) under words (~0.55 Hz), a little roughness,
 * and a pause at the end of every 9 s phrase. A pure function of time, so every instance agrees.
 */
fun demoVoiceLevel(t: Double): Float {
    val phrase = ((t % 9.0) + 9.0) % 9.0
    if (phrase > 6.6) return 0f
    val syllable = 0.5 + 0.5 * sin(t * PI * 2 * 3.1)
    val word = 0.5 + 0.5 * sin(t * PI * 2 * 0.55 + 1)
    val rough = 0.86 + 0.14 * sin(t * 23.7)
    val v = syllable.pow(1.6) * (0.5 + 0.5 * word) * rough
    return minOf(1.0, v * 1.05).toFloat()
}
