package com.burkido.kraft.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.ShimmerText
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.orbs.OrbSize
import com.burkido.kraft.effects.orbs.OrbState
import com.burkido.kraft.effects.orbs.ThinkingOrb

internal object OrbsPage : LibraryPage {
    override val subtitle = "Thought-orb loading indicators for AI interfaces, with nine hand-tuned animated states."

    override val usageCode = """
        import com.burkido.kraft.effects.orbs.OrbState
        import com.burkido.kraft.effects.orbs.ThinkingOrb

        ThinkingOrb(state = OrbState.Searching)
    """.trimIndent()

    override val prompt = """
        Add the Thinking orbs loader from Kraft to my Compose Multiplatform app.

        Install:
        Depend on the :effects module from commonMain.

        Usage:
        import com.burkido.kraft.effects.orbs.OrbState
        import com.burkido.kraft.effects.orbs.ThinkingOrb

        ThinkingOrb(state = OrbState.Searching, size = OrbSize.S64)

        Parameters:
        - state: OrbState.Working | Searching | Solving | Listening | Connecting | Weaving | Composing | Breathing | Shaping
        - size: OrbSize.S64 | S32 | S20 (each size has its own tuned dot counts)
        - displaySize: draws the chosen size at any Dp
        - speed: time multiplier; paused: freezes the current frame
        - color: tints the ink; theme: EffectTheme.Dark | Light | Auto

        Each state is a small 3D dot field (globe, orbits, ribbon, braid, web…) projected and drawn
        with Compose canvas circles and lines. It stops its clock while off screen and honours
        reduced motion.
    """.trimIndent()

    @Composable
    override fun Preview() {
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OrbHeroes()
                OrbStateGrid()
            }
            Spacer(Modifier.height(12.dp))
            PlaygroundLabel()
            OrbPlayground()
        }
    }
}

private val PillBg = Color(29, 29, 29).copy(alpha = 0.5f)
private val PillRing = listOf(BoxShadow(spread = 1.dp, color = Color(44, 47, 54).copy(alpha = 0.22f), inset = true))
private val ShimmerBase = Color(251, 251, 251).copy(alpha = 0.5f)

/** `.ex-orb-heroes`: two 314 dp cells, each holding a large status pill. */
@Composable
private fun OrbHeroes() {
    val stacked = LocalViewport.current.maxWidth(860)
    val cells = listOf(OrbState.Solving to "Solving….", OrbState.Composing to "Thinking….")
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((state, label) in cells) OrbCell(Modifier.fillMaxWidth(), height = null, hero = true) { OrbPill(state, label) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((state, label) in cells) OrbCell(Modifier.weight(1f), height = 314.dp, hero = true) { OrbPill(state, label) }
        }
    }
}

private class GridEntry(val state: OrbState, val large: Boolean, val copy: String)

private val GridEntries = listOf(
    GridEntry(OrbState.Listening, false, "listening"),
    GridEntry(OrbState.Working, true, "working"),
    GridEntry(OrbState.Searching, true, "searching"),
    GridEntry(OrbState.Connecting, true, "solving"),
    GridEntry(OrbState.Weaving, false, "planning"),
    GridEntry(OrbState.Breathing, false, "thinking"),
    GridEntry(OrbState.Shaping, false, "shaping"),
)

/**
 * `.ex-orb-grid`: two columns of 151 dp rows where working, searching and connecting span two
 * rows. CSS auto-placement packs the seven cells into two columns of equal height, which is
 * exactly listening · searching · weaving · breathing beside working · connecting · shaping.
 * Below 860 px the cells stack in source order, at least 200 dp tall.
 */
@Composable
private fun OrbStateGrid() {
    val stacked = LocalViewport.current.maxWidth(860)
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (e in GridEntries) OrbCell(Modifier.fillMaxWidth(), height = null) { GridContent(e) }
        }
        return
    }
    val byState = GridEntries.associateBy { it.state }
    val left = listOf(OrbState.Listening, OrbState.Searching, OrbState.Weaving, OrbState.Breathing)
    val right = listOf(OrbState.Working, OrbState.Connecting, OrbState.Shaping)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (column in listOf(left, right)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (state in column) {
                    val e = byState.getValue(state)
                    OrbCell(Modifier.fillMaxWidth(), height = if (e.large) 314.dp else 151.dp) { GridContent(e) }
                }
            }
        }
    }
}

@Composable
private fun GridContent(e: GridEntry) {
    val word = e.copy.replaceFirstChar { it.uppercase() }
    if (e.large) OrbPill(e.state, "$word….") else OrbChip(e.state, "Agent ${e.copy}…")
}

/** `.ex-orb-cell`: #171717, radius 16, content centred; `--hero` cells pad 48/40 instead of 32. */
@Composable
private fun OrbCell(modifier: Modifier, height: Dp?, hero: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier
            .then(if (height != null) Modifier.height(height) else Modifier.heightIn(min = 200.dp))
            .clip(RoundedCornerShape(16.dp))
            .cssSurface(16.dp) { Pg.Stage }
            .then(if (hero) Modifier.padding(horizontal = 40.dp, vertical = 48.dp) else Modifier.padding(32.dp)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** `.ex-pill`: a 74 dp status pill led by a 64 px orb drawn at 56 dp, with a shimmering label. */
@Composable
private fun OrbPill(state: OrbState, label: String) {
    val fonts = LocalKraftFonts.current
    Row(
        Modifier.height(74.dp).cssSurface(37.dp, PillRing) { PillBg }.padding(start = 9.dp, end = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThinkingOrb(state, size = OrbSize.S64, theme = EffectTheme.Dark, displaySize = 56.dp)
        ShimmerText(label, cssText(fonts.sans, 18f, 24f, FontWeight.Normal, ShimmerBase))
    }
}

/** `.ex-chip`: a 36 dp chip with a 20 px orb. */
@Composable
private fun OrbChip(state: OrbState, label: String) {
    val fonts = LocalKraftFonts.current
    Row(
        Modifier.height(36.dp).cssSurface(18.dp, PillRing) { PillBg }.padding(start = 8.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThinkingOrb(state, size = OrbSize.S20, theme = EffectTheme.Dark)
        ShimmerText(label, cssText(fonts.sans, 13f, 14f, FontWeight.Normal, ShimmerBase))
    }
}

private val OrbStates = listOf(
    OrbState.Working, OrbState.Searching, OrbState.Solving, OrbState.Listening, OrbState.Connecting,
    OrbState.Weaving, OrbState.Composing, OrbState.Breathing, OrbState.Shaping,
)

private val OrbSizes = listOf(OrbSize.S64 to "64px", OrbSize.S32 to "32px", OrbSize.S20 to "20px")

/** The Studio's ink presets; the first is the default ink, so it means "no tint". */
private val OrbInks = listOf(
    Triple(0, Color(0xFFEDEDED), "Ink (default)"),
    Triple(1, Color(0xFF7CD4FF), "Sky"),
    Triple(2, Color(0xFFFFD28F), "Amber"),
    Triple(3, Color(0xFFFF9EC9), "Pink"),
    Triple(4, Color(0xFF9FE8A8), "Mint"),
)
private val OrbInkHex = listOf("#ededed", "#7cd4ff", "#ffd28f", "#ff9ec9", "#9fe8a8")

@Composable
private fun OrbPlayground() {
    var state by rememberSaveable { mutableStateOf(OrbState.Listening) }
    var size by rememberSaveable { mutableStateOf(OrbSize.S64) }
    var paused by rememberSaveable { mutableStateOf(true) }
    var speed by rememberSaveable { mutableDoubleStateOf(100.0) }
    var ink by rememberSaveable { mutableStateOf(0) }

    Playground(
        stage = {
            ThinkingOrb(
                state = state,
                size = size,
                theme = EffectTheme.Dark,
                speed = speed / 100,
                paused = paused,
                color = if (ink == 0) null else OrbInks[ink].second,
            )
            PlayPauseButton(!paused) { paused = !paused }
        },
        controls = {
            PgField("State") { PgTabs(OrbStates.map { it to it.name }, state) { state = it } }
            PgField("Size") { PgTabs(OrbSizes, size) { size = it } }
            PgRule()
            PgGroup("Motion") {
                PgSlider("Speed", speed.toFloat(), 25f..300f, 5f, "${num(speed / 100)}×") { speed = it.toDouble() }
            }
            PgRule()
            PgGroup("Color") { PgSwatches(OrbInks, ink) { ink = it } }
        },
    )
    Spacer(Modifier.height(12.dp))
    CodeSnippet(orbSnippet(state, size, speed, ink))
}

private fun orbSnippet(state: OrbState, size: OrbSize, speed: Double, ink: Int): String {
    val args = buildList {
        add("state = OrbState.${state.name}")
        add("size = OrbSize.${size.name}")
        if (speed != 100.0) add("speed = ${num(speed / 100)}")
        if (ink != 0) add("color = Color(0xFF${OrbInkHex[ink].drop(1).uppercase()})")
    }
    val call = if (args.size <= 2) "ThinkingOrb(${args.joinToString()})" else "ThinkingOrb(\n" + args.joinToString("") { "    $it,\n" } + ")"
    return "import com.burkido.kraft.effects.orbs.OrbSize\nimport com.burkido.kraft.effects.orbs.OrbState\nimport com.burkido.kraft.effects.orbs.ThinkingOrb\n\n$call"
}
