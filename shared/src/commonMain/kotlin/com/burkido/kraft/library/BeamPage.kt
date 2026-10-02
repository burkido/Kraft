package com.burkido.kraft.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.KraftIcon
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.effects.beam.BeamColorVariant
import com.burkido.kraft.effects.beam.BeamSize
import com.burkido.kraft.effects.beam.BorderBeam
import kotlin.math.abs
import kotlin.math.roundToInt

internal object BeamPage : LibraryPage {
    override val subtitle = "An animated rainbow glow that rides the border of any card, button or input."

    override val usageCode = """
        import com.burkido.kraft.effects.beam.BorderBeam

        BorderBeam {
            YourCard()
        }
    """.trimIndent()

    override val prompt = """
        Add the Border beam effect from Kraft to my Compose Multiplatform app.

        Install:
        Depend on the :effects module from commonMain.

        Usage:
        import com.burkido.kraft.effects.beam.BorderBeam

        BorderBeam(size = BeamSize.Md, colorVariant = BeamColorVariant.Colorful, strength = 0.7f) {
            YourCard()
        }

        Parameters:
        - size: BeamSize.Md | Sm | Line | PulseInner | PulseOutside
        - colorVariant: BeamColorVariant.Colorful | Mono | Ocean | Sunset
        - strength: 0–1, glow intensity
        - active: false fades the beam out and stops its clock
        - theme: EffectTheme.Dark | Light | Auto
        - duration, borderRadius, brightness, saturation, hueRange: fine tuning

        BorderBeam wraps a composable and rides an animated glow around its border.
        It draws with Compose canvas primitives only, on Android (API 31+), iOS and Desktop.
    """.trimIndent()

    @Composable
    override fun Preview() {
        Column {
            // The examples come first: real UI wearing the effect, running on their own.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExampleRowFull {
                    BorderBeam(size = BeamSize.Md, borderRadius = 20.dp) { MockChatInput() }
                }
                ExampleRowSplit(
                    left = {
                        // A 36 dp box clamps the mock's 20 px corners to 18, as CSS does.
                        BorderBeam(size = BeamSize.Sm, borderRadius = 18.dp) { MockIconButton() }
                    },
                    right = {
                        BorderBeam(size = BeamSize.Line, duration = 3.1, borderRadius = 20.dp) { MockSearchBar() }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            PlaygroundLabel()
            BeamPlayground()
        }
    }
}

private enum class BeamFamily(val label: String, val sizes: List<Pair<BeamSize, String>>) {
    Rotate("Rotate", listOf(BeamSize.Sm to "Small", BeamSize.Md to "Large", BeamSize.Line to "Line")),
    Pulse("Pulse", listOf(BeamSize.PulseInner to "Pulse Inner", BeamSize.PulseOutside to "Pulse Outside")),
}

private val BeamColors = listOf(
    BeamColorVariant.Colorful to "Colorful",
    BeamColorVariant.Mono to "Mono",
    BeamColorVariant.Ocean to "Ocean",
    BeamColorVariant.Sunset to "Sunset",
)

private fun defaultDuration(size: BeamSize) = when (size) {
    BeamSize.Line -> 3.1
    BeamSize.PulseInner, BeamSize.PulseOutside -> 2.3
    else -> 1.96
}

private fun defaultRadius(size: BeamSize) = if (size == BeamSize.Sm) 18.0 else 16.0

/** The dark preset's brightness: the pulses carry their own, everything else the 1.3 fallback. */
private fun defaultBrightness(size: BeamSize) = when (size) {
    BeamSize.PulseOutside -> 1.9
    BeamSize.PulseInner -> 0.75
    else -> 1.3
}

/**
 * The playground: family, type and colour tabs plus the tuning the site keeps in its Studio, over a
 * stage that starts paused. The snippet below lists only what differs from the defaults.
 */
@Composable
private fun BeamPlayground() {
    var family by rememberSaveable { mutableStateOf(BeamFamily.Rotate) }
    var size by rememberSaveable { mutableStateOf(BeamSize.Md) }
    var color by rememberSaveable { mutableStateOf(BeamColorVariant.Colorful) }
    var active by rememberSaveable { mutableStateOf(false) }
    var duration by rememberSaveable { mutableDoubleStateOf(1.96) }
    var strength by rememberSaveable { mutableDoubleStateOf(100.0) }
    var radius by rememberSaveable { mutableDoubleStateOf(16.0) }
    var brightness by rememberSaveable { mutableDoubleStateOf(1.3) }
    var hueRange by rememberSaveable { mutableDoubleStateOf(30.0) }

    // An untouched knob follows the type's own default across switches, so the snippet never
    // shows a value nobody set.
    fun changeSize(next: BeamSize) {
        if (duration == defaultDuration(size)) duration = defaultDuration(next)
        if (radius == defaultRadius(size)) radius = defaultRadius(next)
        if (brightness == defaultBrightness(size)) brightness = defaultBrightness(next)
        size = next
    }

    val snippet = beamSnippet(size, color, active, duration, strength, radius, brightness, hueRange)
    Playground(
        stage = {
            BorderBeam(
                size = size,
                colorVariant = color,
                active = active,
                duration = duration,
                strength = (strength / 100).toFloat(),
                borderRadius = radius.dp,
                brightness = brightness,
                hueRange = hueRange,
            ) {
                if (size == BeamSize.Sm) MockIconButton() else BeamDemoCard(radius)
            }
            PlayPauseButton(active) { active = !active }
        },
        controls = {
            PgField("Family") {
                PgTabs(BeamFamily.entries.map { it to it.label }, family) {
                    if (it != family) {
                        family = it
                        changeSize(it.sizes.first { (s, _) -> s == BeamSize.Md || s == BeamSize.PulseInner }.first)
                    }
                }
            }
            PgField("Type") { PgTabs(family.sizes, size, ::changeSize) }
            PgField("Color") { PgTabs(BeamColors, color) { color = it } }
            PgRule()
            PgGroup("Tuning") {
                PgSlider("Duration", duration.toFloat(), 0.5f..6f, 0.02f, "${num(duration)}s") { duration = it.toDouble() }
                PgSlider("Strength", strength.toFloat(), 0f..100f, 1f, "${strength.roundToInt()}%") { strength = it.toDouble() }
                PgSlider("Corner radius", radius.toFloat(), 0f..32f, 1f, "${radius.roundToInt()}px") { radius = it.toDouble() }
                PgSlider("Brightness", brightness.toFloat(), 0.5f..2.2f, 0.05f, "${num(brightness)}×") { brightness = it.toDouble() }
                if (color != BeamColorVariant.Mono) {
                    PgSlider("Hue range", hueRange.toFloat(), 0f..120f, 1f, "${hueRange.roundToInt()}°") { hueRange = it.toDouble() }
                }
            }
        },
    )
    Spacer(Modifier.height(12.dp))
    CodeSnippet(snippet)
}

private fun beamSnippet(
    size: BeamSize,
    color: BeamColorVariant,
    active: Boolean,
    duration: Double,
    strength: Double,
    radius: Double,
    brightness: Double,
    hueRange: Double,
): String {
    val args = buildList {
        if (size != BeamSize.Md) add("size = BeamSize.${size.name}")
        if (color != BeamColorVariant.Colorful) add("colorVariant = BeamColorVariant.${color.name}")
        if (strength != 100.0) add("strength = ${num(strength / 100)}f")
        if (duration != defaultDuration(size)) add("duration = ${num(duration)}")
        if (radius != defaultRadius(size)) add("borderRadius = ${radius.roundToInt()}.dp")
        if (brightness != defaultBrightness(size)) add("brightness = ${num(brightness)}")
        if (hueRange != 30.0 && color != BeamColorVariant.Mono) add("hueRange = ${num(hueRange)}")
        if (!active) add("active = false")
    }
    val call = when {
        args.isEmpty() -> "BorderBeam"
        args.size <= 2 -> "BorderBeam(${args.joinToString()})"
        else -> "BorderBeam(\n" + args.joinToString("") { "    $it,\n" } + ")"
    }
    return "$call {\n    Card { Text(\"Content\") }\n}"
}

/** Up to three decimals, no trailing zeros — how the site prints knob values. */
internal fun num(v: Double): String {
    val scaled = (v * 1000).roundToInt()
    val whole = scaled / 1000
    val frac = abs(scaled % 1000)
    if (frac == 0) return (if (scaled < 0 && whole == 0) "-0" else whole.toString())
    val digits = frac.toString().padStart(3, '0').trimEnd('0')
    return "${if (scaled < 0 && whole == 0) "-" else ""}$whole.$digits"
}

// ── Mock UI (sites/home/src/examples/beam-mocks.tsx, examples.css dark tokens) ──────────────

private val MockBg = Color(0xFF1D1D1D)
private val MockChrome = listOf(
    BoxShadow(spread = 1.dp, color = Color(44, 47, 54).copy(alpha = 0.52f), inset = true),
    BoxShadow(blur = 50.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
)
private val ChipBg = Color.White.copy(alpha = 0.04f)
private val ChipChrome = listOf(
    BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
    BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
)

/** `.mock-chat`: the prompt composer — mention pill, placeholder, Agent / Auto tags and send. */
@Composable
private fun MockChatInput() {
    val fonts = LocalKraftFonts.current
    Column(
        Modifier
            .widthIn(max = 348.dp)
            .fillMaxWidth()
            .height(122.dp)
            .cssSurface(20.dp, MockChrome) { MockBg }
            .padding(start = 7.dp, top = 7.dp, end = 7.dp, bottom = 8.dp),
    ) {
        Box(
            Modifier.padding(start = 1.dp).height(24.dp).cssSurface(36.dp, ChipChrome) { ChipBg }.padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            KraftIcon(MockIcons.AtSign, Color(0xFF808388))
        }
        BasicText(
            "Build anything...",
            style = cssText(fonts.sans, 13f, 16f, FontWeight.Normal, Color(0xFF4E4E4E)),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp),
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MockTag("Agent", Modifier.padding(start = 1.dp))
            MockTag("Auto")
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(28.dp).cssSurface(36.dp, ChipChrome) { ChipBg }, contentAlignment = Alignment.Center) {
                KraftIcon(MockIcons.ArrowUp, Color(0xFF8B8B8B), Modifier.alpha(0.5f))
            }
        }
    }
}

@Composable
private fun MockTag(text: String, modifier: Modifier = Modifier) {
    val fonts = LocalKraftFonts.current
    Row(
        modifier.height(24.dp).cssSurface(36.dp, ChipChrome) { ChipBg }.padding(start = 8.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = cssText(fonts.sans, 12f, 14f, FontWeight.Normal, Color(0xFFCACCD2)))
        KraftIcon(MockIcons.Chevron, Color(0xFF8B9099), Modifier.rotate(90f).alpha(0.6f))
    }
}

/** `.mock-icon-btn`: a 36 dp stop button. */
@Composable
private fun MockIconButton() {
    Box(Modifier.size(36.dp).cssSurface(18.dp, MockChrome) { MockBg }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).cssSurface(2.dp) { Color(217, 217, 217).copy(alpha = 0.8f) })
    }
}

/** `.mock-search`: a 366 × 42 search field. */
@Composable
private fun MockSearchBar() {
    val fonts = LocalKraftFonts.current
    Row(
        Modifier
            .widthIn(max = 366.dp)
            .fillMaxWidth()
            .height(42.dp)
            .cssSurface(20.dp, MockChrome) { MockBg }
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KraftIcon(MockIcons.Search, Color.White, Modifier.alpha(0.4f), size = 20.dp)
        BasicText("Search", style = cssText(fonts.sans, 15f, 18f, FontWeight.Normal, Color(0xFF565656)))
    }
}

/** `.beam-card`: the stage's placeholder card — a title bar over two text lines. */
@Composable
private fun BeamDemoCard(radius: Double) {
    Column(
        Modifier
            .width(250.dp)
            .cssSurface(radius.dp) { MockBg }
            .padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.55f).height(10.dp).cssSurface(4.dp) { Color(238, 238, 239).copy(alpha = 0.14f) })
        Box(Modifier.fillMaxWidth().height(8.dp).cssSurface(4.dp) { Color(238, 238, 239).copy(alpha = 0.08f) })
        Box(Modifier.fillMaxWidth(0.78f).height(8.dp).cssSurface(4.dp) { Color(238, 238, 239).copy(alpha = 0.08f) })
    }
}

private object MockIcons {
    val AtSign = stroke(
        16f, 1.5f,
        "M10.4 5.59963V8.59962C10.4 9.07701 10.5896 9.53485 10.9272 9.87242C11.2648 10.21 11.7226 10.3996 12.2 10.3996C12.6774 10.3996 13.1352 10.21 13.4728 9.87242C13.8104 9.53485 14 9.07701 14 8.59962V7.99962C13.9999 6.64544 13.5417 5.33111 12.7 4.27035C11.8582 3.20958 10.6823 2.46476 9.36359 2.15701C8.04484 1.84925 6.66076 1.99665 5.43641 2.57525C4.21206 3.15384 3.21944 4.1296 2.61996 5.34386C2.02048 6.55812 1.84939 7.93947 2.13451 9.26329C2.41963 10.5871 3.14419 11.7756 4.19038 12.6354C5.23657 13.4952 6.54286 13.9758 7.89684 13.9991C9.25083 14.0224 10.5729 13.587 11.648 12.7636M10.4 7.99962C10.4 9.32511 9.32549 10.3996 8 10.3996C6.67452 10.3996 5.6 9.32511 5.6 7.99962C5.6 6.67414 6.67452 5.59963 8 5.59963C9.32549 5.59963 10.4 6.67414 10.4 7.99962Z",
    )
    val Chevron = stroke(16f, 1.5f, "M7 11L10 8L7 5")
    val ArrowUp = stroke(16f, 1.5f, "M8 12.6667V3.33333M12.6667 8L8 3.33333L3.33333 8")
    val Search = stroke(24f, 2f, "M19 11A8 8 0 1 1 3 11A8 8 0 1 1 19 11Z", "M21 21L16.65 16.65")

    private fun stroke(viewport: Float, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder("MockIcon", viewport.dp, viewport.dp, viewport, viewport).apply {
            for (d in paths) {
                addPath(
                    addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
