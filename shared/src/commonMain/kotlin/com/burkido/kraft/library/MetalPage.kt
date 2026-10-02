package com.burkido.kraft.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.KraftFonts
import com.burkido.kraft.designsystem.KraftIcon
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.effects.metal.MetalAnchor
import com.burkido.kraft.effects.metal.MetalBadge
import com.burkido.kraft.effects.metal.MetalFx
import com.burkido.kraft.effects.metal.MetalPreset
import com.burkido.kraft.effects.metal.MetalReflection
import com.burkido.kraft.effects.metal.MetalReflectionText
import com.burkido.kraft.effects.metal.MetalText
import com.burkido.kraft.effects.metal.MetalVariant
import com.burkido.kraft.effects.metal.metalBendArea
import com.burkido.kraft.effects.metal.rememberMetalAnchor
import com.burkido.kraft.effects.metal.rememberMetalBend
import kotlin.math.roundToInt

internal object MetalPage : LibraryPage {
    override val subtitle =
        "Real-time liquid metal for buttons, icons, text and badges — a wandering halo, reflections on neighbours and a ring that bends under your finger."

    override val usageCode = """
        import com.burkido.kraft.effects.metal.MetalFx
        import com.burkido.kraft.effects.metal.MetalVariant

        MetalFx(variant = MetalVariant.Circle, innerShadow = true) {
            SendButton()
        }
    """.trimIndent()

    override val prompt = """
        Add the Liquid Metal effect from Kraft to my Compose Multiplatform app.

        Install:
        Depend on the :effects module from commonMain.

        Usage:
        import com.burkido.kraft.effects.metal.*

        val anchor = rememberMetalAnchor()
        val bend = rememberMetalBend()
        Row(Modifier.metalBendArea(bend)) {
            MetalReflection(anchor, cornerRadius = 20.dp) { SearchField() }   // catches the metal
            MetalFx(variant = MetalVariant.Circle, innerShadow = true, anchor = anchor, bend = bend) {
                SendButton()
            }
        }
        MetalText("Pro", style = TextStyle(fontSize = 24.sp, color = Color(0xFFE2E2E2)))
        MetalBadge("New")

        Parameters:
        - variant: MetalVariant.Button (1 dp ring) | Circle (2 dp ring)
        - preset: MetalPreset.Chromatic | Silver | Gold; theme: EffectTheme.Dark | Light | Auto
        - strength, glowGain, glow, innerShadow, shaderScale, paused, cornerRadius, fill

        One shared liquid-metal sheet (a CPU port of Paper Shaders' liquidMetal) renders at 15 fps and
        every instance crops its own window from it. Android (API 31+), iOS and Desktop.
    """.trimIndent()

    @Composable
    override fun Preview() {
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExampleRowFull(height = 314.dp) { ComposerExample(strength = BaseStrength) }
                ExampleRowFull(height = 170.dp, radius = 36.dp) { LiveModeCard(strength = BaseStrength) }
            }
            Spacer(Modifier.height(12.dp))
            PlaygroundLabel()
            MetalPlayground()
        }
    }
}

/** The site keeps every demo at the metal-fx demo page's baseline: chromatic at 90 %. */
private const val BaseStrength = 0.9f

// ── Examples (sites/home/src/examples/metal-examples-v2.tsx, examples.css mx-*) ─────────────

private val ChatBg = Color(29, 29, 29).copy(alpha = 0.7f)
private val FieldBg = Color(0xFF1D1D1D)
private val FieldChrome = listOf(
    BoxShadow(spread = 1.dp, color = Color(44, 47, 54).copy(alpha = 0.52f), inset = true),
    BoxShadow(blur = 50.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
)
private val ChipBg = Color.White.copy(alpha = 0.04f)
private val ChipChrome = listOf(
    BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
    BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
)
private val ChipText = Color(0xFFCACCD2)
private val Ink = Color(0xFFF8F8F8)

/**
 * `.mx-chat`: the composer — a circle send button with the inner-shadow rim and the liquid dent,
 * reflected on the Auto chip beside it. The dent follows the pointer anywhere over the row.
 */
@Composable
private fun ComposerExample(strength: Float) {
    val fonts = LocalKraftFonts.current
    val anchor = rememberMetalAnchor()
    val bend = rememberMetalBend()
    Box(Modifier.fillMaxSize().metalBendArea(bend), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .cssSurface(20.dp) { ChatBg }
                .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
        ) {
            BasicText(
                "Build anything...",
                style = cssText(fonts.sans, 14f, 16f, FontWeight.Normal, Color(0xFF4E4E4E)),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(0.7f).padding(bottom = 16.dp),
            )
            Row(Modifier.fillMaxWidth().overflowToEnd(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).cssSurface(18.dp, ChipChrome) { ChipBg }, contentAlignment = Alignment.Center) {
                    KraftIcon(MetalIcons.Plus, ChipText)
                }
                Spacer(Modifier.weight(1f))
                ChatChip("Agent")
                MetalReflection(anchor, cornerRadius = 18.dp) { ChatChip("Auto") }
                MetalFx(variant = MetalVariant.Circle, strength = strength * 0.9f, innerShadow = true, anchor = anchor, bend = bend) {
                    SendIcon()
                }
            }
        }
    }
}

/**
 * Flex items that won't shrink below their content: on a narrow screen the row keeps every chip
 * and the send button whole and runs past its end, as the site's does, instead of squeezing the
 * last child.
 */
private fun Modifier.overflowToEnd(): Modifier = layout { measurable, constraints ->
    val need = measurable.minIntrinsicWidth(constraints.maxHeight)
    val w = maxOf(constraints.maxWidth, need)
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(0, 0) }
}

/** `.mx-chip`: a 36 dp model picker with its chevron turned down. */
@Composable
private fun ChatChip(text: String) {
    val fonts = LocalKraftFonts.current
    Row(
        Modifier.height(36.dp).cssSurface(18.dp, ChipChrome) { ChipBg }.padding(start = 14.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = cssText(fonts.sans, 12f, 14f, FontWeight.Normal, ChipText), softWrap = false)
        KraftIcon(MetalIcons.Chevron, Color(0xFF8E8E8E), Modifier.rotate(90f))
    }
}

@Composable
private fun SendIcon() {
    Box(Modifier.size(40.dp).semantics { contentDescription = "Send" }, contentAlignment = Alignment.Center) {
        KraftIcon(MetalIcons.ArrowUp, Ink)
    }
}

/** `.mx-row--card`: "Live mode" and the New badge. */
@Composable
private fun LiveModeCard(strength: Float, preset: MetalPreset = MetalPreset.Chromatic, shaderScale: Float = 1.6f, paused: Boolean = false) {
    val fonts = LocalKraftFonts.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText("Live mode", style = cssText(fonts.sans, 20f, 24f, FontWeight.Normal, Color(0xFF999999)), softWrap = false)
        MetalBadge(preset = preset, strength = strength, shaderScale = shaderScale, paused = paused, textStyle = badgeStyle(fonts))
    }
}

private fun badgeStyle(fonts: KraftFonts): TextStyle = cssText(fonts.sans, 12.222f, 12.222f * 1.4f, FontWeight.SemiBold)

// ── Playground ────────────────────────────────────────────────────────────────────────────

private enum class MetalType(val label: String) { Circle("Circle button"), Text("Text"), Button("Button"), Badge("Badge") }

private val Presets = listOf(MetalPreset.Chromatic to "Chromatic", MetalPreset.Silver to "Silver", MetalPreset.Gold to "Gold")

/** The circle runs at 90 % of the page's baseline, as on the site. */
private fun defaultStrength(type: MetalType) = if (type == MetalType.Circle) 81f else 90f

/** Slider values are snapped in floats, so defaults compare with a tolerance. */
private fun Float.near(other: Float) = kotlin.math.abs(this - other) < 1e-3f

private fun defaultScale(type: MetalType) = when (type) {
    MetalType.Circle -> 1.3f
    MetalType.Text -> 2.8f
    else -> 1.6f
}

/**
 * The playground: the four v2 components (the site shows the pill button and the badge in its
 * Studio only), the glow and reflection switches, and the tuning the site keeps in its Studio,
 * over a stage that starts paused.
 */
@Composable
private fun MetalPlayground() {
    var type by rememberSaveable { mutableStateOf(MetalType.Circle) }
    var paused by rememberSaveable { mutableStateOf(true) }
    var noGlow by rememberSaveable { mutableStateOf(false) }
    var noReflection by rememberSaveable { mutableStateOf(false) }
    var preset by rememberSaveable { mutableStateOf(MetalPreset.Chromatic) }
    var strength by rememberSaveable { mutableFloatStateOf(defaultStrength(MetalType.Circle)) }
    var glowGain by rememberSaveable { mutableFloatStateOf(100f) }
    var scale by rememberSaveable { mutableFloatStateOf(defaultScale(MetalType.Circle)) }

    // An untouched knob follows the type's own default across switches.
    fun changeType(next: MetalType) {
        if (strength.near(defaultStrength(type))) strength = defaultStrength(next)
        if (scale.near(defaultScale(type))) scale = defaultScale(next)
        type = next
    }

    val tune = MetalTune(type, preset, strength / 100, glowGain / 100, scale, !noGlow, !noReflection, paused)
    Playground(
        stage = {
            MetalStage(tune)
            PlayPauseButton(!paused) { paused = !paused }
        },
        controls = {
            PgField("Type") { PgTabs(MetalType.entries.map { it to it.label }, type, ::changeType) }
            PgField("Options") {
                PgToggles(listOf("No Glow" to noGlow, "No Reflection" to noReflection)) {
                    if (it == 0) noGlow = !noGlow else noReflection = !noReflection
                }
            }
            PgField("Color") { PgTabs(Presets, preset) { preset = it } }
            PgRule()
            PgGroup("Tuning") {
                PgSlider("Strength", strength, 0f..100f, 1f, "${strength.roundToInt()}%") { strength = it }
                if (type == MetalType.Circle || type == MetalType.Button) {
                    PgSlider("Glow strength", glowGain, 0f..200f, 5f, "${glowGain.roundToInt()}%") { glowGain = it }
                }
                PgSlider("Shader scale", scale, 0.5f..3.2f, 0.1f, "${num(scale.toDouble())}×") { scale = it }
            }
        },
    )
    Spacer(Modifier.height(12.dp))
    CodeSnippet(metalSnippet(tune, glowGain))
}

/** The playground's circle row at its defaults, which the landing card plays. */
@Composable
internal fun MetalCircleScene(paused: Boolean) {
    MetalStage(
        MetalTune(
            MetalType.Circle, MetalPreset.Chromatic, defaultStrength(MetalType.Circle) / 100, 1f,
            defaultScale(MetalType.Circle), glow = true, reflection = true, paused = paused,
        ),
    )
}

private data class MetalTune(
    val type: MetalType,
    val preset: MetalPreset,
    val strength: Float,
    val glowGain: Float,
    val scale: Float,
    val glow: Boolean,
    val reflection: Boolean,
    val paused: Boolean,
)

/**
 * The stage (`StageV2`): the circle and the pill sit beside a search field that catches their
 * metal and dent under the pointer; the text type pairs "Plan", reflecting, with metal "Pro".
 */
@Composable
private fun MetalStage(t: MetalTune) {
    val fonts = LocalKraftFonts.current
    val anchor = rememberMetalAnchor()
    val bend = rememberMetalBend()
    val reflectOn = anchor.takeIf { t.reflection }
    Box(Modifier.fillMaxSize().metalBendArea(bend), contentAlignment = Alignment.Center) {
        when (t.type) {
            MetalType.Circle, MetalType.Button -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(reflectOn)
                val circle = t.type == MetalType.Circle
                MetalFx(
                    variant = if (circle) MetalVariant.Circle else MetalVariant.Button,
                    preset = t.preset,
                    strength = t.strength,
                    glowGain = t.glowGain,
                    paused = t.paused,
                    innerShadow = circle,
                    glow = t.glow,
                    shaderScale = t.scale,
                    anchor = anchor,
                    bend = bend,
                ) {
                    if (circle) {
                        SendIcon()
                    } else {
                        Box(Modifier.size(140.dp, 40.dp), contentAlignment = Alignment.Center) {
                            BasicText("Upgrade to Pro", style = cssText(fonts.sans, 14f, 18f, FontWeight.Medium, Ink), softWrap = false)
                        }
                    }
                }
            }
            MetalType.Text -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Same size and line box, so top alignment is baseline alignment.
                val plan = cssText(fonts.sans, 24f, 28.8f, FontWeight.Medium, Color(153, 153, 153).copy(alpha = 0.6f))
                if (reflectOn != null) MetalReflectionText(reflectOn, "Plan", plan, strength = 0.64f) else BasicText("Plan", style = plan)
                MetalText(
                    "Pro",
                    cssText(fonts.sans, 24f, 28.8f, FontWeight.Medium, Color(0xFFE2E2E2)),
                    preset = t.preset,
                    strength = t.strength,
                    shaderScale = t.scale,
                    paused = t.paused,
                    anchor = anchor,
                )
            }
            MetalType.Badge -> LiveModeCard(t.strength, t.preset, t.scale, t.paused)
        }
    }
}

/** `.metal-search`: a 180 × 40 search field, the metal's reflection target. */
@Composable
private fun SearchField(anchor: MetalAnchor?) {
    val fonts = LocalKraftFonts.current
    val field = @Composable {
        Row(
            Modifier.size(180.dp, 40.dp).cssSurface(20.dp, FieldChrome) { FieldBg }.padding(start = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KraftIcon(MetalIcons.Search, Color(0xFF8B8B8B), size = 18.dp)
            BasicText("Search", style = cssText(fonts.sans, 14f, 18f, FontWeight.Medium, Ink.copy(alpha = 0.3f)), softWrap = false)
        }
    }
    if (anchor != null) MetalReflection(anchor, cornerRadius = 20.dp) { field() } else field()
}

private fun metalSnippet(t: MetalTune, glowGainPercent: Float): String {
    fun common(args: MutableList<String>) {
        if (t.preset != MetalPreset.Chromatic) args.add("preset = MetalPreset.${t.preset.name}")
        if (!t.strength.near(1f)) args.add("strength = ${num(t.strength.toDouble())}f")
        if (!t.scale.near(defaultScale(t.type))) args.add("shaderScale = ${num(t.scale.toDouble())}f")
    }
    fun call(name: String, args: List<String>) = "$name(\n" + args.joinToString("") { "    $it,\n" } + ")"
    val reflect = t.reflection
    return when (t.type) {
        MetalType.Circle, MetalType.Button -> {
            val circle = t.type == MetalType.Circle
            val args = mutableListOf<String>()
            if (circle) args.add("variant = MetalVariant.Circle")
            common(args)
            if (circle) args.add("innerShadow = true")
            if (!t.glow) args.add("glow = false") else if (!glowGainPercent.near(100f)) args.add("glowGain = ${num(glowGainPercent / 100.0)}f")
            if (reflect) args.add("anchor = anchor")
            args.add("bend = bend")
            val target = if (reflect) "    MetalReflection(anchor, cornerRadius = 20.dp) { SearchField() }\n" else "    SearchField()\n"
            val child = if (circle) "SendButton()" else "Text(\"Upgrade to Pro\")"
            buildString {
                append("import com.burkido.kraft.effects.metal.*\n\n")
                if (reflect) append("val anchor = rememberMetalAnchor()\n")
                append("val bend = rememberMetalBend() // the liquid dent under your pointer\n\n")
                append("Row(Modifier.metalBendArea(bend)) {\n")
                append(target)
                append(call("MetalFx", args).prependIndent("    "))
                append(" {\n        $child\n    }\n}")
            }
        }
        MetalType.Text -> {
            val args = mutableListOf("\"Pro\"", "style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE2E2E2))")
            common(args)
            if (reflect) args.add("anchor = anchor")
            buildString {
                append("import com.burkido.kraft.effects.metal.*\n\n")
                if (reflect) append("val anchor = rememberMetalAnchor()\n\n")
                append("Row {\n")
                if (reflect) append("    MetalReflectionText(anchor, \"Plan\", planStyle, strength = 0.64f) // catches the metal\n")
                else append("    Text(\"Plan\", style = planStyle)\n")
                append(call("MetalText", args).prependIndent("    "))
                append("\n}")
            }
        }
        MetalType.Badge -> {
            val args = mutableListOf("\"New\"")
            common(args)
            "import com.burkido.kraft.effects.metal.MetalBadge\n\nRow {\n    Text(\"Live mode\")\n" + call("MetalBadge", args).prependIndent("    ") + "\n}"
        }
    }
}

private object MetalIcons {
    val Plus = stroke(24f, 2f, "M12 5V19M5 12H19")
    val ArrowUp = stroke(24f, 2f, "M12 19V5M5 12L12 5L19 12")
    val Chevron = stroke(16f, 1.5f, "M7 11L10 8L7 5")
    val Search = stroke(18f, 1.5f, "M14 8A6 6 0 1 1 2 8A6 6 0 1 1 14 8Z", "M16 16L12.5 12.5")

    private fun stroke(viewport: Float, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder("MetalIcon", viewport.dp, viewport.dp, viewport, viewport).apply {
            for (d in paths) {
                addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}
