package com.burkido.kraft.effects.image

import kotlin.math.pow

/**
 * img-fx's three bundled presets (the `presets/` directory), each tuned for a dark and a light surface:
 * [PixelsOrganic] (Chromium Flow ridges), [PixelsMechanic] (Nebula) and [SweepGradient] (a
 * diagonal band with per-cell flicker).
 */
enum class ImagePreset { PixelsOrganic, PixelsMechanic, SweepGradient }

/** `EasingKey` (`engine/tween.ts`). */
internal enum class ImageEase {
    Linear, Smoothstep, EaseOutCubic, EaseOutQuint, EaseInOutCubic, EaseOutExpo, EaseOutBack;

    fun apply(t0: Float): Float {
        val t = t0.coerceIn(0f, 1f)
        return when (this) {
            Linear -> t
            Smoothstep -> t * t * (3 - 2 * t)
            EaseOutCubic -> 1 - (1 - t).pow(3)
            EaseOutQuint -> 1 - (1 - t).pow(5)
            EaseInOutCubic -> if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
            EaseOutExpo -> if (t == 1f) 1f else 1 - 2f.pow(-10 * t)
            EaseOutBack -> {
                val c = 1.70158f
                1 + (c + 1) * (t - 1).pow(3) + c * (t - 1).pow(2)
            }
        }
    }
}

/** `MaskShape`: which field (or geometry) decides where the image appears first. */
internal enum class MaskShape {
    Shader, ShaderHighlight, ShaderColor1, ShaderColor2, ShaderColor3, ShaderColor4, ShaderColor5,
    RadialCenter, RadialCorner, LinearTop, LinearBottom, LinearLeft, LinearRight,
    DiagonalTL, DiagonalBR, Diamond, BlindsH, BlindsV, GradientSweep;

    val fromShader: Boolean get() = ordinal <= ShaderColor5.ordinal

    /** The palette slot a `shaderColorN` mask tracks, or -1. */
    val colorSlot: Int get() = if (ordinal in ShaderColor1.ordinal..ShaderColor5.ordinal) ordinal - ShaderColor1.ordinal else -1
}

/** `RevealConfig`: the mask sweep and the per-cell chunky-to-smooth dissolve. */
internal class RevealConfig(
    val duration: Float,
    val easing: ImageEase,
    val maskShape: MaskShape,
    val softness: Float,
    val pixDuration: Float,
    val pixEasing: ImageEase,
)

/** One theme block of a preset (`PresetMode`, pixel mode — the only mode the bundle ships). */
internal class ImageMode(
    val effect: Int,
    /** Seven palette slots, `#rrggbb`. */
    val colors: List<String>,
    val cardBg: String,
    // `pixelConfig`.
    val cellSize: Float,
    val gap: Float,
    val dotOpacity: Float,
    val hlScale: Float,
    val fillOpacity: Float,
    val edgeFade: Float,
    val fadeStr: Float,
    /** Drift angle, degrees. */
    val direction: Float,
    val speed: Float,
    val intensity: Float,
    val scale: Float,
    val softness: Float,
    val distortion: Float,
    val flicker: Float,
    val complexity: Float,
    val shape: Float,
    val blur: Float,
    val highlight: Float,
    val vignette: Float,
    val vigOpacity: Float,
    val shaderOpacity: Float,
    val sweepEase: Int,
    val reveal: RevealConfig,
)

internal fun imageMode(preset: ImagePreset, dark: Boolean): ImageMode = when (preset) {
    ImagePreset.PixelsOrganic -> if (dark) OrganicDark else OrganicLight
    ImagePreset.PixelsMechanic -> if (dark) MechanicDark else MechanicLight
    ImagePreset.SweepGradient -> if (dark) SweepDark else SweepLight
}

private fun mode(
    effect: Int,
    colors: List<String>,
    cardBg: String,
    fillOpacity: Float,
    edgeFade: Float,
    direction: Float,
    speed: Float,
    intensity: Float,
    scale: Float,
    flicker: Float,
    highlight: Float,
    vignette: Float,
    vigOpacity: Float,
    reveal: RevealConfig,
    sweepEase: Int = 0,
) = ImageMode(
    effect = effect, colors = colors, cardBg = cardBg,
    cellSize = 0.22f, gap = 0.14f, dotOpacity = 0.68f, hlScale = 0.8f, fillOpacity = fillOpacity, edgeFade = edgeFade, fadeStr = 1f,
    direction = direction, speed = speed, intensity = intensity, scale = scale, softness = 0.76f, distortion = 0.3f,
    flicker = flicker, complexity = 0.2f, shape = 0.52f, blur = 1f, highlight = highlight, vignette = vignette,
    vigOpacity = vigOpacity, shaderOpacity = 1f, sweepEase = sweepEase, reveal = reveal,
)

private fun reveal(mask: MaskShape, pixDuration: Float) =
    RevealConfig(3f, ImageEase.EaseOutCubic, mask, 0.5f, pixDuration, ImageEase.EaseOutCubic)

// `pixels-organic.ts` — Chromium Flow.
private val OrganicDark = mode(
    22, listOf("#0f0f0f", "#4a4949", "#b9b9b9", "#0f0f0f", "#d8d8d8", "#0f0f0f", "#2f2f2f"), "#0f0f0f",
    fillOpacity = 0.44f, edgeFade = 24f, direction = 0f, speed = 0.3f, intensity = 1f, scale = 1f, flicker = 0f,
    highlight = 0.2f, vignette = 0.26f, vigOpacity = 1f, reveal = reveal(MaskShape.ShaderColor4, 2.65f),
)
private val OrganicLight = mode(
    22, listOf("#e3e3e3", "#ffffff", "#f5f5f5", "#f5f5f5", "#080808", "#f5f5f5", "#f5f5f5"), "#f5f5f5",
    fillOpacity = 0.18f, edgeFade = 20f, direction = 25f, speed = 0.3f, intensity = 0.85f, scale = 1f, flicker = 0f,
    highlight = 0.7f, vignette = 0f, vigOpacity = 0f, reveal = reveal(MaskShape.ShaderColor4, 2.55f),
)

// `pixels-mechanic.ts` — Nebula.
private val MechanicDark = mode(
    11, listOf("#949494", "#2d2d2d", "#333333", "#3a3a3a", "#0b0b0b", "#060606", "#2f2f2f"), "#0f0f0f",
    fillOpacity = 0.44f, edgeFade = 24f, direction = 0f, speed = 0.7f, intensity = 1f, scale = 1.4f, flicker = 0.5f,
    highlight = 0.32f, vignette = 0.26f, vigOpacity = 1f, reveal = reveal(MaskShape.ShaderColor4, 2.65f),
)
private val MechanicLight = mode(
    11, listOf("#e0e0e0", "#fdfdfd", "#f2f2f2", "#0a0a0a", "#dcdcdc", "#f5f5f5", "#f5f5f5"), "#f5f5f5",
    fillOpacity = 0.18f, edgeFade = 20f, direction = 25f, speed = 0.55f, intensity = 0.85f, scale = 0.9f, flicker = 0.5f,
    highlight = 0.92f, vignette = 0f, vigOpacity = 0f, reveal = reveal(MaskShape.ShaderColor3, 2.6f),
)

// `sweep-gradient.ts` — Gradient Sweep.
private val SweepDark = mode(
    25, listOf("#0f0f0f", "#0f0f0f", "#282828", "#3a3a3a", "#525252", "#0f0f0f", "#0f0f0f"), "#0f0f0f",
    fillOpacity = 0.44f, edgeFade = 24f, direction = 0f, speed = 2.65f, intensity = 1f, scale = 1f, flicker = 0.5f,
    highlight = 0.32f, vignette = 0.26f, vigOpacity = 1f, reveal = reveal(MaskShape.GradientSweep, 2.65f), sweepEase = 1,
)
private val SweepLight = mode(
    25, listOf("#f5f5f5", "#f5f5f5", "#ededed", "#eaeaea", "#d2d2d2", "#f5f5f5", "#f5f5f5"), "#f5f5f5",
    fillOpacity = 0.18f, edgeFade = 20f, direction = 0f, speed = 2.65f, intensity = 0.85f, scale = 1f, flicker = 0.5f,
    highlight = 0.92f, vignette = 0f, vigOpacity = 0f, reveal = reveal(MaskShape.GradientSweep, 2.6f), sweepEase = 1,
)
