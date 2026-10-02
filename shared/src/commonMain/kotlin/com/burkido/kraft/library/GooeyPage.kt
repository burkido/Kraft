package com.burkido.kraft.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.KraftIcon
import com.burkido.kraft.designsystem.KraftMotion
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.clickableRaw
import com.burkido.kraft.designsystem.cssBlur
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.rememberInteraction
import com.burkido.kraft.effects.core.CubicBezier
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import com.burkido.kraft.effects.core.rememberEffectActivity
import com.burkido.kraft.effects.gooey.BendItem
import com.burkido.kraft.effects.gooey.BendTuning
import com.burkido.kraft.effects.gooey.Item
import com.burkido.kraft.effects.gooey.Liquid
import com.burkido.kraft.effects.gooey.LiquidBend
import com.burkido.kraft.effects.gooey.LiquidTransition
import com.burkido.kraft.effects.gooey.MoveItem
import com.burkido.kraft.effects.gooey.MoveTuning
import com.burkido.kraft.effects.gooey.parseLiquidShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal object GooeyPage : LibraryPage {
    override val subtitle = "Liquid UI for Compose. Touching pieces merge like goo and morph like jelly while text stays crisp."

    override val usageCode = """
        import com.burkido.kraft.effects.gooey.Liquid
        import com.burkido.kraft.effects.gooey.Item

        Liquid(fill = Color.White) {
            Item(x = if (open) (-54).dp else 0.dp, y = if (open) (-34).dp else 0.dp, radius = 20.dp) {
                RoundButton()
            }
            Item(x = 0.dp, y = 0.dp, radius = 20.dp) { PlusButton() }
        }
    """.trimIndent()

    override val prompt = """
        Add the Gooey liquid effect from Kraft to my Compose Multiplatform app.

        Install:
        Depend on the :effects module from commonMain.

        Usage:
        Liquid(blur = 6.dp, contrast = 18f, fill = Color.White, shadow = parseLiquidShadow("0 2px 6px rgba(0,0,0,.08)")) {
            // Morph: the library animates element and liquid together; touching pieces merge.
            Item(x = if (open) (-54).dp else 0.dp, y = if (open) (-34).dp else 0.dp, radius = 20.dp,
                 transition = LiquidTransition.Bouncy) { RoundButton() }
            // Move: position the element yourself; the liquid chases it and trails a droplet tail.
            MoveItem(radius = 12.dp, modifier = Modifier.offset { thumbOffset }) { Thumb() }
            // Bend: the body bows with velocity; content can lean with the live bend.
            BendItem(radius = 23.dp, modifier = draggable) { bend -> Card(bend) }
        }

        The silhouettes are blurred and cut at an alpha threshold into one liquid surface with a
        shadow rebuilt on the merged shape; the content stays crisp on top.
    """.trimIndent()

    @Composable
    override fun Preview() {
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExampleRowFull { MorphDemo(GooeyGroup()) }
                ExampleRowSplit(
                    left = { MoveDemo(GooeyGroup()) },
                    right = { BendDemo(GooeyGroup()) },
                )
            }
            Spacer(Modifier.height(12.dp))
            PlaygroundLabel()
            GooeyPlayground()
        }
    }
}

/** The surface knobs every demo shares (`GroupTuning`). A null fill is the effect's own surface. */
private data class GooeyGroup(val blur: Float = 6f, val contrast: Float = 18f, val fill: Color? = null)

/** The live demo page's dark surface shadow ("Figma soft"). */
internal val LiquidShadowDark = parseLiquidShadow(
    "0 0 0 1px rgba(255, 255, 255, 0.04) inset, 0 1px 0 0 rgba(255, 255, 255, 0.03) inset, " +
        "0 0 0 1px rgba(0, 0, 0, 0.06), 0 2px 6px 0 rgba(0, 0, 0, 0.05), 0 4px 42px 0 rgba(0, 0, 0, 0.24)",
)

/** Ink flips with the liquid's luminance so icons stay legible on light fills. */
private fun inkFor(fill: Color): Color {
    val lum = 0.299 * fill.red * 255 + 0.587 * fill.green * 255 + 0.114 * fill.blue * 255
    return if (lum > 140) Color(0xFF17181C) else Color(0xFFFBFBFB)
}

// ── Morph: the plus menu ──────────────────────────────────────────────────────────────────

private class Satellite(val label: String, val x: Int, val y: Int, val icon: ImageVector)

private val Satellites = listOf(
    Satellite("New file", -54, -34, GooeyIcons.File),
    Satellite("Add image", 0, -64, GooeyIcons.Image),
    Satellite("New folder", 54, -34, GooeyIcons.Folder),
)

private val OpenTransition = LiquidTransition.Bouncy
private val CloseTransition = LiquidTransition.Snappy
private const val OpenStagger = 40
private const val IconDuration = 180
private const val IconDelay = 120
private const val Anticipation = 5f
private const val AnticipationMillis = 700

/** The plus menu opening and closing by itself — the landing card's clip: open at 0.1 s, closed at 1.75 s, every 3.23 s. */
@Composable
internal fun GooeyMenuScene(playing: Boolean) {
    val activity = rememberEffectActivity()
    Box(activity.modifier) { MorphDemo(GooeyGroup(), scene = true, playing = playing && activity.isActive) }
}

/**
 * The plus menu: three satellites split from the button like droplets (550 ms bouncy, 40 ms
 * stagger) and fold back (250 ms snappy) with a small anticipation dip of the liquid and the
 * button; icons materialise with a soft cross-blur once their droplet pulls clear. As a [scene] it
 * takes no clicks and, while [playing], toggles itself on the landing clip's timeline.
 */
@Composable
private fun MorphDemo(group: GooeyGroup, scene: Boolean = false, playing: Boolean = false) {
    val fill = group.fill ?: Color(0xFF202020)
    val ink = inkFor(fill)
    var open by remember { mutableStateOf(false) }
    val anticipation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val toggle = {
        if (open) {
            scope.launch {
                anticipation.snapTo(0f)
                anticipation.animateTo(Anticipation, tween((AnticipationMillis * 0.3f).toInt(), easing = KraftMotion.SmoothOut))
                anticipation.animateTo(0f, tween((AnticipationMillis * 0.7f).toInt(), easing = KraftMotion.SmoothOut))
            }
        }
        open = !open
    }
    val autoplay = scene && playing && LocalEffectFrozenTime.current == null
    LaunchedEffect(autoplay) {
        if (!autoplay) return@LaunchedEffect
        while (true) {
            delay(100)
            if (!open) toggle()
            delay(1650)
            if (open) toggle()
            delay(1480)
        }
    }
    Liquid(
        Modifier.size(200.dp, 140.dp),
        blur = group.blur.dp,
        contrast = group.contrast,
        fill = fill,
        shadow = LiquidShadowDark,
        liquidOffset = { Offset(0f, with(density) { anticipation.value.dp.toPx() }) },
    ) {
        Satellites.forEachIndexed { i, s ->
            Item(
                x = if (open) s.x.dp else 0.dp,
                y = if (open) s.y.dp else 0.dp,
                radius = 20.dp,
                modifier = Modifier.offset(80.dp, 80.dp),
                transition = if (open) OpenTransition else CloseTransition,
                delayMillis = if (open) i * OpenStagger else 0,
            ) {
                SatelliteButton(s, i, open && !scene, ink, shown = open, onClick = toggle)
            }
        }
        Item(0.dp, 0.dp, radius = 20.dp, modifier = Modifier.offset(80.dp, 80.dp)) {
            val rotation by animateFloatAsState(if (open) 45f else 0f, tween(250, easing = CubicBezier.EaseInOut.easing))
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer { translationY = anticipation.value.dp.toPx() }
                    .then(if (scene) Modifier else Modifier.clickableRaw(rememberInteraction(), onClick = toggle))
                    .semantics { contentDescription = if (open) "Close menu" else "Open menu" },
                contentAlignment = Alignment.Center,
            ) {
                KraftIcon(GooeyIcons.Plus, ink, Modifier.graphicsLayer { rotationZ = rotation }, size = 20.dp)
            }
        }
    }
}

@Composable
private fun SatelliteButton(s: Satellite, index: Int, open: Boolean, ink: Color, shown: Boolean = open, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val shown by animateFloatAsState(
        if (shown) 1f else 0f,
        if (shown) {
            tween(IconDuration, delayMillis = IconDelay + index * OpenStagger, easing = CubicBezier.Ease.easing)
        } else {
            tween(120, easing = CubicBezier.Ease.easing)
        },
    )
    val hovered = interaction.hovered
    Box(
        Modifier
            .size(40.dp)
            .then(if (open) Modifier.clickableRaw(interaction, onClick = onClick) else Modifier)
            .semantics { contentDescription = s.label }
            .cssSurface(20.dp) { if (open && hovered) Color.White.copy(alpha = 0.06f) else Color.Transparent },
        contentAlignment = Alignment.Center,
    ) {
        KraftIcon(s.icon, ink, Modifier.graphicsLayer { alpha = shown }.cssBlur((2f * (1f - shown)).dp))
    }
}

// ── Move: the liquid slider ───────────────────────────────────────────────────────────────

private const val ThumbMax = 188f

/** A 240 × 80 slider whose thumb is pure liquid: it lags, stretches and trails a droplet. */
@Composable
private fun MoveDemo(group: GooeyGroup) {
    var x by remember { mutableFloatStateOf(84f) }
    Box(Modifier.size(240.dp, 80.dp)) {
        // The track sits under the liquid, so the thumb and its tail paint over it.
        Box(
            Modifier.offset(14.dp, 38.dp).size(212.dp, 8.dp)
                .cssSurface(4.dp, listOf(BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true))) {
                    Color(115, 115, 115).copy(alpha = 0.2f)
                },
        )
        Liquid(
            Modifier.size(240.dp, 80.dp),
            blur = group.blur.dp,
            contrast = group.contrast,
            fill = group.fill ?: Color(0xFF525252),
            shadow = LiquidShadowDark,
        ) {
            val density = LocalDensity.current
            MoveItem(
                radius = 12.dp,
                modifier = Modifier
                    .offset((14 + x).dp, 30.dp)
                    .size(24.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dx ->
                            change.consume()
                            x = (x + dx / density.density).coerceIn(0f, ThumbMax)
                        }
                    }
                    .semantics {
                        contentDescription = "Demo slider"
                        progressBarRangeInfo = ProgressBarRangeInfo(x / ThumbMax, 0f..1f)
                        setProgress { v -> x = v.coerceIn(0f, 1f) * ThumbMax; true }
                    },
                tuning = MoveTuning(springiness = 0.5f, wobble = 0.5f, stretch = 0.6f, trail = 0.35f),
            ) { Box(Modifier.size(24.dp)) }
        }
    }
}

// ── Bend: the drag card ───────────────────────────────────────────────────────────────────

private const val CardWidth = 261f
private const val CardHeight = 46f
private const val ContentBend = 0.3f
private val DropEase = CubicBezier(0.34, 1.40, 0.64, 1.0).easing

/**
 * A pill card to drag around: its body bows with the motion and the content leans with it; let go
 * and it flies home (400 ms, overshoot bezier), landing with a bounce.
 */
@Composable
private fun BendDemo(group: GooeyGroup) {
    val fill = group.fill ?: Color(0xFF202020)
    val ink = inkFor(fill)
    val scope = rememberCoroutineScope()
    val px = remember { Animatable(0f) }
    val py = remember { Animatable(0f) }
    BoxWithConstraints(Modifier.widthIn(max = 640.dp).fillMaxWidth().height(210.dp)) {
        val limX = ((maxWidth.value - CardWidth) / 2).coerceAtLeast(0f)
        val limY = ((210f - CardHeight) / 2).coerceAtLeast(0f)
        Liquid(
            Modifier.matchParentSize(),
            blur = group.blur.dp,
            contrast = group.contrast,
            fill = fill,
            shadow = LiquidShadowDark,
        ) {
            val density = LocalDensity.current
            BendItem(
                radius = 23.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { androidx.compose.ui.unit.IntOffset(px.value.dp.roundToPx(), py.value.dp.roundToPx()) }
                    .pointerInput(limX, limY) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch { px.animateTo(0f, tween(400, easing = DropEase)) }
                                scope.launch { py.animateTo(0f, tween(400, easing = DropEase)) }
                            },
                            onDragCancel = {
                                scope.launch { px.animateTo(0f, tween(400, easing = DropEase)) }
                                scope.launch { py.animateTo(0f, tween(400, easing = DropEase)) }
                            },
                        ) { change, drag ->
                            change.consume()
                            scope.launch {
                                px.snapTo((px.value + drag.x / density.density).coerceIn(-limX, limX))
                                py.snapTo((py.value + drag.y / density.density).coerceIn(-limY, limY))
                            }
                        }
                    },
                tuning = BendTuning(vertical = 0.6f, horizontal = 0.35f),
            ) { bend -> BendCard(bend, ink) }
        }
    }
}

@Composable
private fun BendCard(bend: LiquidBend, ink: Color) {
    val fonts = LocalKraftFonts.current
    Row(
        Modifier.size(CardWidth.dp, CardHeight.dp).padding(start = 8.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .lean(bend, arc = 0.45f, rot = 1.6f, sqx = 0.014f, sqy = 0f)
                .size(32.dp)
                .cssSurface(16.dp, listOf(BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.08f), inset = true))) {
                    Color.White.copy(alpha = 0.06f)
                },
            contentAlignment = Alignment.Center,
        ) { KraftIcon(GooeyIcons.Folder, ink) }
        BasicText(
            "Gooey Project",
            style = cssText(fonts.sans, 13f, null, FontWeight.Medium, ink),
            modifier = Modifier.lean(bend, arc = 1f, rot = 0f, sqx = 0.006f, sqy = 0.01f),
            softWrap = false,
        )
        BasicText(
            "45 files",
            style = cssText(fonts.sans, 13f, null, FontWeight.Normal, ink.copy(alpha = ink.alpha * 0.5f)),
            modifier = Modifier.lean(bend, arc = 0.6f, rot = -1.6f, sqx = 0.006f, sqy = 0f),
            softWrap = false,
        )
    }
}

/**
 * `.dgc-card > *`: each piece rides the arc (translate, the middle most), rotates onto its local
 * tangent and shears with the cap deformation, scaled by the content-bend knob.
 */
private fun Modifier.lean(bend: LiquidBend, arc: Float, rot: Float, sqx: Float, sqy: Float): Modifier = drawWithContent {
    val bx = bend.x
    val by = bend.y
    if (bx == 0f && by == 0f) {
        drawContent()
        return@drawWithContent
    }
    val k = ContentBend * arc
    val m = Matrix()
    val cx = size.width / 2
    val cy = size.height / 2
    m.translate(bx * k * 2.4f * density + cx, by * k * 2.4f * density + cy)
    m.rotateZ(by * rot * ContentBend)
    // skewX(-0.55° · xn · cb): x' = x + tan(θ)·y.
    val skew = kotlin.math.tan(radians(bx * ContentBend * -0.55f))
    val shear = Matrix(floatArrayOf(1f, 0f, 0f, 0f, skew, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
    m.timesAssign(shear)
    m.scale(1f - bx * ContentBend * sqx, 1f + by * ContentBend * sqy, 1f)
    m.translate(-cx, -cy)
    withTransform({ transform(m) }) { this@drawWithContent.drawContent() }
}

private fun radians(deg: Float): Float = (deg * kotlin.math.PI / 180).toFloat()

// ── Playground ────────────────────────────────────────────────────────────────────────────

private enum class GooeyEffect(val label: String) { Morph("Morph"), Move("Move"), Bend("Bend") }

private val FillOptions = listOf(
    Triple(0, Color(0xFF202020), "Surface (default)"),
    Triple(1, Color(0xFFE9E9E9), "Light"),
    Triple(2, Color(0xFF7CD4FF), "Sky"),
    Triple(3, Color(0xFFFFD28F), "Amber"),
)

@Composable
private fun GooeyPlayground() {
    var effect by rememberSaveable { mutableStateOf(GooeyEffect.Morph) }
    var blur by rememberSaveable { mutableFloatStateOf(6f) }
    var contrast by rememberSaveable { mutableFloatStateOf(18f) }
    var fill by rememberSaveable { mutableIntStateOf(0) }
    val group = GooeyGroup(blur, contrast, if (fill == 0) null else FillOptions[fill].second)
    Playground(
        stage = {
            Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                when (effect) {
                    GooeyEffect.Morph -> MorphDemo(group)
                    GooeyEffect.Move -> MoveDemo(group)
                    GooeyEffect.Bend -> BendDemo(group)
                }
            }
        },
        controls = {
            PgField("Effect") { PgTabs(GooeyEffect.entries.map { it to it.label }, effect) { effect = it } }
            PgSlider("Goo blur", blur, 0f..16f, 0.5f, num(blur.toDouble())) { blur = it }
            PgSlider("Contrast", contrast, 4f..40f, 1f, contrast.roundToInt().toString()) { contrast = it }
            PgRule()
            PgGroup("Fill") { PgSwatches(FillOptions, fill) { fill = it } }
        },
    )
    Spacer(Modifier.height(12.dp))
    CodeSnippet(gooeySnippet(effect, blur, contrast, fill))
}

private fun gooeySnippet(effect: GooeyEffect, blur: Float, contrast: Float, fill: Int): String {
    val fillHex = when (fill) {
        1 -> "0xFFE9E9E9"
        2 -> "0xFF7CD4FF"
        3 -> "0xFFFFD28F"
        else -> if (effect == GooeyEffect.Move) "0xFF525252" else "0xFF202020"
    }
    val args = buildList {
        if (blur != 6f) add("blur = ${num(blur.toDouble())}.dp")
        if (contrast != 18f) add("contrast = ${contrast.roundToInt()}f")
        add("fill = Color($fillHex)")
    }.joinToString()
    val body = when (effect) {
        GooeyEffect.Morph ->
            "    Item(x = if (open) (-54).dp else 0.dp, y = if (open) (-34).dp else 0.dp, radius = 20.dp, transition = LiquidTransition.Bouncy) {\n" +
                "        RoundButton()\n    }\n" +
                "    Item(x = 0.dp, y = if (open) (-64).dp else 0.dp, radius = 20.dp, transition = LiquidTransition.Bouncy, delayMillis = 40) {\n" +
                "        RoundButton()\n    }\n" +
                "    Item(x = 0.dp, y = 0.dp, radius = 20.dp) { PlusButton() }"
        GooeyEffect.Move ->
            "    MoveItem(radius = 12.dp, modifier = Modifier.offset { IntOffset(x, 0) }, tuning = MoveTuning(stretch = 0.6f, trail = 0.35f)) {\n" +
                "        Thumb()\n    }"
        GooeyEffect.Bend ->
            "    BendItem(radius = 23.dp, modifier = Modifier.graphicsLayer { translationX = x; translationY = y }) { bend ->\n" +
                "        Card(bend)\n    }"
    }
    return "import com.burkido.kraft.effects.gooey.*\n\nLiquid($args) {\n$body\n}"
}

private object GooeyIcons {
    val File = stroke(16f, 1.4f, "M9 1.5H4A1.5 1.5 0 0 0 2.5 3v10A1.5 1.5 0 0 0 4 14.5h8a1.5 1.5 0 0 0 1.5-1.5V6z", "M9 1.5V6h4.5")
    val Image = stroke(
        16f, 1.4f,
        "M3.5 1.5H12.5A2 2 0 0 1 14.5 3.5V12.5A2 2 0 0 1 12.5 14.5H3.5A2 2 0 0 1 1.5 12.5V3.5A2 2 0 0 1 3.5 1.5Z",
        "M6.75 5.5A1.25 1.25 0 1 1 4.25 5.5A1.25 1.25 0 1 1 6.75 5.5Z",
        "M14.5 10.5L11 7l-7.5 7.5",
    )
    val Folder = stroke(16f, 1.4f, "M14.5 12.5A1.5 1.5 0 0 1 13 14H3a1.5 1.5 0 0 1-1.5-1.5V3A1.5 1.5 0 0 1 3 1.5h3L7.5 4H13a1.5 1.5 0 0 1 1.5 1.5z")
    val Plus = stroke(20f, 1.75f, "M10 4V16M4 10H16")

    private fun stroke(viewport: Float, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder("GooeyIcon", viewport.dp, viewport.dp, viewport, viewport).apply {
            for (d in paths) {
                addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}
