package com.burkido.kraft.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.image.ImageGeneration
import com.burkido.kraft.effects.image.ImagePhase
import com.burkido.kraft.effects.image.ImagePreset
import com.burkido.kraft.effects.image.rememberImageGenerationState
import com.burkido.kraft.resources.Res
import com.burkido.kraft.resources.gen_1
import com.burkido.kraft.resources.gen_2
import com.burkido.kraft.resources.gen_3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal object ImagePage : LibraryPage {
    override val subtitle = "An image-generation loader built on a churning pixel mosaic that dissolves into real images."

    override val usageCode = """
        import com.burkido.kraft.effects.image.ImageGeneration
        import com.burkido.kraft.effects.image.ImagePreset

        ImageGeneration(preset = ImagePreset.PixelsOrganic, images = images, autoReveal = true) {
            Box(Modifier.size(320.dp))
        }
    """.trimIndent()

    override val prompt = """
        Add the Image generation loader from Kraft to my Compose Multiplatform app.

        Install:
        Depend on the :effects module from commonMain.

        Usage:
        import com.burkido.kraft.effects.image.*

        val state = rememberImageGenerationState()
        ImageGeneration(
            preset = ImagePreset.PixelsOrganic,   // PixelsMechanic | SweepGradient
            images = images,                     // List<ImageBitmap> to reveal
            autoReveal = true,                   // pause → reveal → hold → fade, on a loop
            state = state,
        ) {
            Box(Modifier.size(320.dp))           // the card the loader fills
        }
        // By hand: state.triggerReveal(holdUntilHidden = true), state.triggerHide(),
        // state.triggerRegenerate(durationMillis = 3000)

        Parameters: preset, theme, strength (0–2), speed, pixelScale, cardBg, colors (palette slots),
        revealDelaySeconds, revealHoldMillis, revealFadeOutMillis, cornerRadius, paused.

        The mosaic is the img-fx shader evaluated on the CPU at the web's 10 fps and 1.25× density,
        off the main thread, and shown crisply upscaled. Android (API 31+), iOS and Desktop.
    """.trimIndent()

    @Composable
    override fun Preview() {
        val pool = rememberGenImages()
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExampleRowFull(verticalPadding = 25.dp) {
                    ImageGeneration(preset = ImagePreset.PixelsOrganic, theme = EffectTheme.Dark, cardBg = CardSurface, images = pool, autoReveal = true) {
                        Box(Modifier.squareUpTo(320.dp))
                    }
                }
                ExampleRowSplit(
                    left = {
                        ImageGeneration(preset = ImagePreset.PixelsMechanic, theme = EffectTheme.Dark, cardBg = CardSurface, images = pool, autoReveal = true) {
                            Box(Modifier.squareUpTo(180.dp))
                        }
                    },
                    right = {
                        ImageGeneration(preset = ImagePreset.SweepGradient, theme = EffectTheme.Dark, cardBg = CardSurface, images = pool, autoReveal = true) {
                            Box(Modifier.squareUpTo(180.dp))
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            PlaygroundLabel()
            ImagePlayground(pool)
        }
    }
}

/**
 * `.ex-image-card`: square, [max] wide at most (`max-width: 100%` below that). A layout rather
 * than `aspectRatio`, so the split row's intrinsic height sees the capped side, not the cell width.
 */
private fun Modifier.squareUpTo(max: Dp): Modifier = layout { measurable, constraints ->
    val side = minOf(max.roundToPx(), constraints.maxWidth)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

/** The demo page's card surface. */
private val CardSurface = Color(0xFF1B1B1B)

/** The reveal pool, decoded once off the main thread and kept for the session. */
private var genImages: List<ImageBitmap>? = null

@Composable
internal fun rememberGenImages(): List<ImageBitmap> {
    var images by remember { mutableStateOf(genImages ?: emptyList()) }
    LaunchedEffect(Unit) {
        if (genImages != null) return@LaunchedEffect
        val env = getSystemResourceEnvironment()
        val decoded = withContext(Dispatchers.Default) {
            listOf(Res.drawable.gen_1, Res.drawable.gen_2, Res.drawable.gen_3).map { getDrawableResourceBytes(env, it).decodeToImageBitmap() }
        }
        genImages = decoded
        images = decoded
    }
    return images
}

// ── Playground ────────────────────────────────────────────────────────────────────────────

private val Presets = listOf(
    ImagePreset.PixelsOrganic to "Organic",
    ImagePreset.PixelsMechanic to "Mechanic",
    ImagePreset.SweepGradient to "Gradient Sweep",
)

/** The Studio's palettes: re-hue the preset's ink slots, keeping each slot's lightness. */
private enum class ImagePalette(val label: String, val hue: Float, val saturation: Float) {
    Preset("Preset", 0f, 0f),
    Ocean("Ocean", 205f, 0.62f),
    Ember("Ember", 20f, 0.72f),
    Mono("Mono", 0f, 0f),
}

private val CardBackgrounds = listOf(
    Triple(0, CardSurface, "Surface (default)"),
    Triple(1, Color(0xFF1B1B24), "Ink"),
    Triple(2, Color(0xFF1A2330), "Navy"),
    Triple(3, Color(0xFF241A2E), "Plum"),
)

/** The dark palettes as authored (`presets/`), for the palette re-hue. */
private fun presetColors(preset: ImagePreset): List<Color> = when (preset) {
    ImagePreset.PixelsOrganic -> listOf(0xFF0F0F0F, 0xFF4A4949, 0xFFB9B9B9, 0xFF0F0F0F, 0xFFD8D8D8, 0xFF0F0F0F, 0xFF2F2F2F)
    ImagePreset.PixelsMechanic -> listOf(0xFF949494, 0xFF2D2D2D, 0xFF333333, 0xFF3A3A3A, 0xFF0B0B0B, 0xFF060606, 0xFF2F2F2F)
    ImagePreset.SweepGradient -> listOf(0xFF0F0F0F, 0xFF0F0F0F, 0xFF282828, 0xFF3A3A3A, 0xFF525252, 0xFF0F0F0F, 0xFF0F0F0F)
}.map { Color(it) }

private fun lum(c: Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

/**
 * `paletteColors`: slots on the card surface stay (null keeps the preset's); the others take the
 * palette's hue at their own lightness, so the grid keeps its contrast structure.
 */
private fun paletteOverride(preset: ImagePreset, palette: ImagePalette): List<Color?>? {
    if (palette == ImagePalette.Preset) return null
    val bgLum = lum(Color(0xFF0F0F0F))
    return presetColors(preset).map { c ->
        val l = lum(c)
        if (abs(l - bgLum) < 0.08f) null else hsl(palette.hue, palette.saturation, l)
    }
}

/** HSL → colour, rounded to 8-bit channels like the Studio's hex. */
private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs((h / 60f) % 2f - 1))
    val m = l - c / 2
    val (r, g, b) = when (floor(h / 60f).toInt() % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
    return Color(ch(r), ch(g), ch(b))
}

/**
 * The playground: type and strength as on the site, plus the tuning its Studio keeps — speed,
 * pixel scale, palette and card surface — over a stage that starts paused, with the demo's
 * Play / Reveal image / Regenerate toolbar.
 */
@Composable
private fun ImagePlayground(pool: List<ImageBitmap>) {
    var preset by rememberSaveable { mutableStateOf(ImagePreset.PixelsOrganic) }
    var strength by rememberSaveable { mutableFloatStateOf(100f) }
    var speed by rememberSaveable { mutableFloatStateOf(100f) }
    var pixelScale by rememberSaveable { mutableFloatStateOf(1f) }
    var palette by rememberSaveable { mutableStateOf(ImagePalette.Preset) }
    var cardBg by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(true) }
    val state = rememberImageGenerationState()
    val colors = paletteOverride(preset, palette)
    val surface = CardBackgrounds[cardBg].second
    // The site's `imageRevealed`: set on reveal / visible, cleared when the scheduler idles.
    val revealed = state.phase != ImagePhase.Idle

    Playground(
        stageMinHeight = 440.dp,
        stage = {
            ImageGeneration(
                preset = preset,
                theme = EffectTheme.Dark,
                strength = strength / 100f,
                speed = speed / 100f,
                pixelScale = pixelScale,
                cardBg = surface,
                colors = colors,
                images = pool,
                paused = paused,
                state = state,
            ) {
                Box(Modifier.size(280.dp))
            }
            StageToolbar {
                StageButton(if (paused) "Play" else "Pause") { paused = !paused }
                StageButton(if (revealed) "Hide image" else "Reveal image", enabled = !paused) {
                    if (state.isImageActive) state.triggerHide() else state.triggerReveal(holdUntilHidden = true)
                }
                StageButton("Regenerate", enabled = !paused && revealed) { state.triggerRegenerate(durationMillis = 3000) }
            }
        },
        controls = {
            PgField("Type") { PgTabs(Presets, preset) { preset = it } }
            PgRule()
            PgGroup("Effect settings") {
                PgSlider("Strength", strength, 0f..200f, 1f, "${strength.roundToInt()}%") { strength = it }
                PgSlider("Speed", speed, 25f..300f, 5f, "${num(speed / 100.0)}×") { speed = it }
                PgSlider("Pixel scale", pixelScale, 0.5f..2f, 0.05f, "${num(pixelScale.toDouble())}×") { pixelScale = it }
            }
            PgRule()
            PgField("Palette") { PgTabs(ImagePalette.entries.map { it to it.label }, palette) { palette = it } }
            PgGroup("Card background") { PgSwatches(CardBackgrounds, cardBg) { cardBg = it } }
        },
    )
    Spacer(Modifier.height(12.dp))
    CodeSnippet(imageSnippet(preset, strength, speed, pixelScale, cardBg, colors))
}

private fun imageSnippet(preset: ImagePreset, strength: Float, speed: Float, pixelScale: Float, cardBg: Int, colors: List<Color?>?): String {
    fun hex(c: Color) = "Color(0xFF" + listOf(c.red, c.green, c.blue).joinToString("") {
        (it * 255).roundToInt().toString(16).padStart(2, '0').uppercase()
    } + ")"
    val args = buildList {
        add("preset = ImagePreset.${preset.name}")
        if (abs(strength - 100f) > 0.5f) add("strength = ${num(strength / 100.0)}f")
        if (abs(speed - 100f) > 0.5f) add("speed = ${num(speed / 100.0)}f")
        if (abs(pixelScale - 1f) > 0.001f) add("pixelScale = ${num(pixelScale.toDouble())}f")
        if (cardBg != 0) add("cardBg = ${hex(CardBackgrounds[cardBg].second)}")
        if (colors != null) add("colors = listOf(${colors.joinToString { it?.let(::hex) ?: "null" }})")
        add("images = images")
    }
    return "import com.burkido.kraft.effects.image.*\n\nImageGeneration(\n" + args.joinToString("") { "    $it,\n" } +
        ") {\n    Box(Modifier.size(280.dp))\n}"
}
