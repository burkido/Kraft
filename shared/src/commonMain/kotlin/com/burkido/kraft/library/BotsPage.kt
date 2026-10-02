package com.burkido.kraft.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.ShimmerText
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.rememberInteraction
import com.burkido.kraft.effects.avatars.BotAvatar
import com.burkido.kraft.effects.avatars.BotAvatarFace
import com.burkido.kraft.effects.avatars.BotAvatarShading
import com.burkido.kraft.effects.avatars.BotAvatarState
import com.burkido.kraft.effects.avatars.BotAvatarType
import com.burkido.kraft.effects.avatars.botAvatarPointer
import com.burkido.kraft.effects.avatars.preset
import com.burkido.kraft.effects.core.CubicBezier
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

internal object BotsPage : LibraryPage {
    override val subtitle =
        "Animated avatars for your AI agents: eighteen glossy 3D shapes with living faces that look around, jump, hop while they work and doze off between tasks."

    override val usageCode = """
        import com.burkido.kraft.effects.avatars.BotAvatar
        import com.burkido.kraft.effects.avatars.BotAvatarState
        import com.burkido.kraft.effects.avatars.BotAvatarType

        BotAvatar(type = BotAvatarType.Clover, state = if (busy) BotAvatarState.Working else BotAvatarState.Default)
    """.trimIndent()

    override val prompt = """
        Add the Bot avatars from Kraft to my Compose Multiplatform app.

        Install:
        Depend on the :effects module from commonMain.

        Usage:
        import com.burkido.kraft.effects.avatars.*

        BotAvatar(
            type = BotAvatarType.Clover,          // 18 shapes, each with its own colour
            state = BotAvatarState.Working,       // Default (idle) | Working | Sleeping
            face = BotAvatarFace.Mouth,           // Eyes (default) | Mouth
            size = 64.dp,
        )
        // A container that lets a pointer anywhere over it lead the avatars' eyes:
        Box(Modifier.botAvatarPointer()) { … }

        Parameters: color, ink, brightness, saturation, shading (Plastic | Crisp | Smooth | Flat),
        shadow, highlight, depth, light, rim, spread, speed, paused, seed, interactive, turn,
        whirl, jump (BotJumpConfig).

        Each avatar is a rig (the web engine, line for line) drawn as a stack of its outline with a
        pillow profile; in plastic the front is a baked height field lit per texel from a matcap.
        A tap makes it hop and turn right round. Android (API 31+), iOS and Desktop.
    """.trimIndent()

    @Composable
    override fun Preview() {
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamRow()
                ExampleRowSplit(
                    left = { BotRoster() },
                    right = { BotChat() },
                    leftWeight = 1f,
                    rightWeight = 1f,
                    cellPadding = 28.dp,
                    cellVerticalPadding = 32.dp,
                    stackBelow = 740,
                )
            }
            Spacer(Modifier.height(12.dp))
            PlaygroundLabel()
            BotsPlayground()
        }
    }
}

// ── Examples (sites/home/src/avatars.tsx, examples/avatars-mocks.tsx) ─────────────────────

/** The team: eight of the eighteen — one of each kind of shape and colour. */
private val Team = listOf(
    BotAvatarType.Clover, BotAvatarType.Flower, BotAvatarType.Star, BotAvatarType.Ghost,
    BotAvatarType.Mech, BotAvatarType.Circle, BotAvatarType.Hexagon, BotAvatarType.Square,
)

private val MockBg = Color(0xFF1D1D1D)
private val MockBorderColor = Color(44, 47, 54).copy(alpha = 0.52f)
private val MockBorder = listOf(BoxShadow(spread = 1.dp, color = MockBorderColor, inset = true))
private val BubbleShape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
private val MockChip = Color.White.copy(alpha = 0.04f)
private val MockText = Color(0xFFC0C0C0)
private val MockPlaceholder = Color(0xFF4E4E4E)

/** `.example-row-full--team`: the team row, idle, each one pleased to be hovered (it gets to work). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamRow() {
    val fonts = LocalKraftFonts.current
    var hot by remember { mutableStateOf<BotAvatarType?>(null) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .clip(RoundedCornerShape(16.dp))
            .cssSurface(16.dp) { Pg.Stage }
            .botAvatarPointer()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            for (type in Team) {
                Column(
                    Modifier
                        .width(84.dp)
                        .pointerInput(type) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    when (e.type) {
                                        PointerEventType.Enter, PointerEventType.Press -> hot = type
                                        PointerEventType.Exit -> if (hot == type) hot = null
                                        PointerEventType.Release -> if (e.changes.firstOrNull()?.type == androidx.compose.ui.input.pointer.PointerType.Touch && hot == type) hot = null
                                        else -> Unit
                                    }
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BotAvatar(type = type, state = if (hot == type) BotAvatarState.Working else BotAvatarState.Default, size = 64.dp)
                    BasicText(type.preset.label, style = cssText(fonts.sans, 12f, 14f, FontWeight.Normal, Color(0xFF767676)))
                }
            }
        }
    }
}

private class RosterBot(val type: BotAvatarType, val name: String, val state: BotAvatarState, val status: String)

private val Roster = listOf(
    RosterBot(BotAvatarType.Clover, "Chief", BotAvatarState.Working, "Booking the venue…"),
    RosterBot(BotAvatarType.Star, "Inbox manager", BotAvatarState.Default, "Inbox at zero, 5 drafts parked"),
    RosterBot(BotAvatarType.Flower, "Talent scout", BotAvatarState.Default, "3 intros drafted in your voice"),
    RosterBot(BotAvatarType.Ghost, "Night shift", BotAvatarState.Sleeping, "Back at 9:00"),
)

/** `.mock-bots`: the agent list — avatar, name and what each one is up to. */
@Composable
private fun BotRoster() {
    val fonts = LocalKraftFonts.current
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .cssSurface(16.dp, MockBorder) { MockBg }
            .botAvatarPointer()
            .padding(8.dp),
    ) {
        BasicText(
            "Bots",
            style = cssText(fonts.sans, 12f, 16f, FontWeight.Medium, MockPlaceholder),
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
        )
        for (bot in Roster) {
            val interaction = rememberInteraction()
            val hovered = interaction.hovered
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverable(interaction.source)
                    .cssSurface(10.dp) { if (hovered) MockChip else Color.Transparent }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotAvatar(type = bot.type, state = bot.state, size = 36.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(bot.name, style = cssText(fonts.sans, 13f, 16f, FontWeight.Medium, Color.White), softWrap = false)
                    val status = cssText(fonts.sans, 12f, 15f, FontWeight.Normal, KraftColors.TextMuted)
                    if (bot.state == BotAvatarState.Working) {
                        ShimmerText(bot.status, status, highlight = Color(0xFFF5F5F5))
                    } else {
                        BasicText(bot.status, style = status, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private const val Reply = "They only sign annual, Dana approves, and pricing is the same thread as last quarter. I answered without waiting on you."

/**
 * `.mock-thread`: a question, then the bot thinks (working, "Thinking…" shimmering) and settles
 * with the answer streaming in word by word — on a loop, so the state changes can be watched.
 */
@Composable
private fun BotChat() {
    val fonts = LocalKraftFonts.current
    var thinking by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            thinking = true
            delay(2600)
            thinking = false
            delay(3200)
        }
    }
    val text = cssText(fonts.sans, 13f, 18f, FontWeight.Normal, MockText)
    Column(
        Modifier.widthIn(max = 340.dp).fillMaxWidth().botAvatarPointer(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // `.mock-thread-user`: right-aligned, at most 82 % wide, the tail at the bottom right.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .wrapContentWidth(Alignment.End)
                    .background(MockChip, BubbleShape)
                    .border(1.dp, MockBorderColor, BubbleShape)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                BasicText("Can you summarise the thread with Acme?", style = cssText(fonts.sans, 13f, 18f, FontWeight.Normal, Color.White))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotAvatar(type = BotAvatarType.Clover, state = if (thinking) BotAvatarState.Working else BotAvatarState.Default, size = 32.dp)
            // The answer's own box, always: a hidden copy holds the height while the bot thinks.
            Box(Modifier.heightIn(min = 32.dp).padding(top = 1.dp)) {
                BasicText(Reply, style = text, modifier = Modifier.alpha(0f))
                if (thinking) {
                    ShimmerText("Thinking…", cssText(fonts.sans, 13f, 18f, FontWeight.Normal, Color(251, 251, 251).copy(alpha = 0.5f)))
                } else {
                    StreamedReply(Reply, text)
                }
            }
        }
    }
}

private val StreamEase = CubicBezier(0.22, 1.0, 0.36, 1.0)

/** transitions.dev's Streaming text: each word resolves through opacity and a 1 px blur, one every 60 ms. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamedReply(reply: String, style: androidx.compose.ui.text.TextStyle) {
    val words = remember(reply) { reply.trim().split(Regex("\\s+")) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(reply) {
        val start = withFrameNanos { it }
        val end = (words.size - 1) * 60L + 350L
        while (elapsed < end) {
            withFrameNanos { elapsed = (it - start) / 1_000_000 }
        }
    }
    FlowRow {
        words.forEachIndexed { i, w ->
            BasicText(
                if (i < words.size - 1) "$w " else w,
                style = style,
                modifier = Modifier.graphicsLayer {
                    val t = ((elapsed - i * 60L) / 350f).coerceIn(0f, 1f)
                    val e = StreamEase.transform(t.toDouble()).toFloat()
                    alpha = e
                    val r = (1f - e) * density
                    renderEffect = if (r > 0.05f) BlurEffect(r, r, TileMode.Decal) else null
                },
            )
        }
    }
}

// ── Playground ────────────────────────────────────────────────────────────────────────────

private val States = listOf(BotAvatarState.Default to "Idle", BotAvatarState.Working to "Working", BotAvatarState.Sleeping to "Sleeping")
private val Faces = listOf(BotAvatarFace.Eyes to "Eyes", BotAvatarFace.Mouth to "Mouth")
private val Sizes = listOf(96 to "96px", 64 to "64px", 32 to "32px")
private val Shadings = listOf(
    BotAvatarShading.Plastic to "Plastic",
    BotAvatarShading.Crisp to "Crisp",
    BotAvatarShading.Smooth to "Smooth",
    BotAvatarShading.Flat to "Flat",
)

/** The team first, then the other ten. */
private val TypeOptions = (Team + BotAvatarType.entries.filter { it !in Team }).map { it to it.preset.label }

/**
 * The Studio's colour and ink rows. None picked is the type's own colour and the ink that reads
 * on it; picking the picked one again goes back to that.
 */
private val Colors = listOf(
    Triple(1, Color(0xFF35B8FF), "Sky"),
    Triple(2, Color(0xFFFF7AB8), "Pink"),
    Triple(3, Color(0xFFDC48FF), "Magenta"),
    Triple(4, Color(0xFF2FCB7A), "Green"),
    Triple(5, Color(0xFFFFD32B), "Yellow"),
)
private val Inks = listOf(
    Triple(1, Color(0xFF1E1A33), "Dark ink"),
    Triple(2, Color(0xFFF7F5F2), "Light ink"),
    Triple(3, Color(0xFF35B8FF), "Sky ink"),
)

/**
 * The playground: type, face, state and size as on the site, plus the look and motion its Studio
 * keeps, over a stage that starts paused — the pose shows, the motion waits for Play.
 */
@Composable
private fun BotsPlayground() {
    var type by rememberSaveable { mutableStateOf(BotAvatarType.Clover) }
    var face by rememberSaveable { mutableStateOf<BotAvatarFace?>(null) }
    var state by rememberSaveable { mutableStateOf(BotAvatarState.Default) }
    var size by rememberSaveable { mutableIntStateOf(96) }
    var color by rememberSaveable { mutableIntStateOf(0) }
    var ink by rememberSaveable { mutableIntStateOf(0) }
    var brightness by rememberSaveable { mutableFloatStateOf(100f) }
    var saturation by rememberSaveable { mutableFloatStateOf(150f) }
    var shading by rememberSaveable { mutableStateOf(BotAvatarShading.Plastic) }
    var shadow by rememberSaveable { mutableFloatStateOf(35f) }
    var depth by rememberSaveable { mutableFloatStateOf(65f) }
    var speed by rememberSaveable { mutableFloatStateOf(100f) }
    var paused by rememberSaveable { mutableStateOf(true) }

    val shownFace = face ?: type.preset.face
    val colorValue = Colors.firstOrNull { it.first == color }?.second
    val inkValue = Inks.firstOrNull { it.first == ink }?.second
    Playground(
        stage = {
            Box(Modifier.botAvatarPointer(), contentAlignment = Alignment.Center) {
                BotAvatar(
                    type = type,
                    face = shownFace,
                    state = state,
                    size = size.dp,
                    color = colorValue,
                    ink = inkValue,
                    brightness = brightness / 100.0,
                    saturation = saturation / 100.0,
                    shading = shading,
                    shadow = shadow / 100.0,
                    depth = depth / 100.0,
                    speed = speed / 100.0,
                    paused = paused,
                )
            }
            PlayPauseButton(!paused) { paused = !paused }
        },
        controls = {
            PgField("Type") { PgTabs(TypeOptions, type) { if (it != type) { type = it; face = null } } }
            PgField("Face") { PgTabs(Faces, shownFace) { face = it } }
            PgField("State") { PgTabs(States, state) { state = it } }
            PgField("Size") { PgTabs(Sizes, size) { size = it } }
            PgRule()
            // The Studio teaser's rows, in its order.
            PgGroup("Color") { PgSwatches(Colors, color) { color = if (it == color) 0 else it } }
            PgSlider("Brightness", brightness, 0f..200f, 1f, "${brightness.roundToInt()}%") { brightness = it }
            PgSlider("Saturation", saturation, 0f..200f, 1f, "${saturation.roundToInt()}%") { saturation = it }
            PgGroup("Ink") { PgSwatches(Inks, ink) { ink = if (it == ink) 0 else it } }
            PgSlider("Speed", speed, 0f..300f, 5f, "${num(speed / 100.0)}×") { speed = it }
            PgField("Shading") { PgTabs(Shadings, shading) { shading = it } }
            PgSlider("Shadow", shadow, 0f..200f, 5f, "${shadow.roundToInt()}%") { shadow = it }
            PgSlider("Depth", depth, 20f..200f, 5f, "${depth.roundToInt()}%") { depth = it }
        },
    )
    Spacer(Modifier.height(12.dp))
    CodeSnippet(botsSnippet(type, face, state, size, colorValue, inkValue, brightness, saturation, shading, shadow, depth, speed, paused))
}

private fun botsSnippet(
    type: BotAvatarType,
    face: BotAvatarFace?,
    state: BotAvatarState,
    size: Int,
    color: Color?,
    ink: Color?,
    brightness: Float,
    saturation: Float,
    shading: BotAvatarShading,
    shadow: Float,
    depth: Float,
    speed: Float,
    paused: Boolean,
): String {
    fun hex(c: Color) = "Color(0xFF" + listOf(c.red, c.green, c.blue).joinToString("") {
        (it * 255).roundToInt().toString(16).padStart(2, '0').uppercase()
    } + ")"
    fun near(a: Float, b: Float) = abs(a - b) < 0.5f
    val args = buildList {
        add("type = BotAvatarType.${type.name}")
        if (face != null && face != type.preset.face) add("face = BotAvatarFace.${face.name}")
        if (state != BotAvatarState.Default) add("state = BotAvatarState.${state.name}")
        if (size != 64) add("size = $size.dp")
        if (color != null) add("color = ${hex(color)}")
        if (ink != null) add("ink = ${hex(ink)}")
        if (!near(brightness, 100f)) add("brightness = ${num(brightness / 100.0)}")
        if (!near(saturation, 150f)) add("saturation = ${num(saturation / 100.0)}")
        if (shading != BotAvatarShading.Plastic) add("shading = BotAvatarShading.${shading.name}")
        if (!near(shadow, 35f)) add("shadow = ${num(shadow / 100.0)}")
        if (!near(depth, 65f)) add("depth = ${num(depth / 100.0)}")
        if (!near(speed, 100f)) add("speed = ${num(speed / 100.0)}")
        if (paused) add("paused = true")
    }
    return "import com.burkido.kraft.effects.avatars.*\n\nBotAvatar(\n" + args.joinToString("") { "    $it,\n" } + ")"
}
