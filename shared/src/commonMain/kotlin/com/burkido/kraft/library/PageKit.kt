package com.burkido.kraft.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.KraftIcon
import com.burkido.kraft.designsystem.KraftIcons
import com.burkido.kraft.designsystem.KraftMotion
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.animatedStateColor
import com.burkido.kraft.designsystem.clickableRaw
import com.burkido.kraft.designsystem.cssBlur
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.rememberInteraction
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.round

// The playground tokens (`playground.css`, dark).
internal object Pg {
    val Stage = Color(0xFF171717)
    val Panel = Color(0xFF1B1B1B)
    val Label = Color(0xFF979797)
    val Title = Color(0xFFFBFBFB)
    val ControlBg = Color.White.copy(alpha = 0.04f)
    val ControlText = Color(0xFFB1B1B1)
    val ControlTextHover = Color(0xFFE0E0E0)
    val ControlActiveBg = Color.White.copy(alpha = 0.08f)
    val ControlActiveText = Color(0xFFFBFBFB)
    val ControlBgHover = Color.White.copy(alpha = 0.08f)
    val Grab = Color(0xFF767676)
    val Rule = Color.White.copy(alpha = 0.04f)
    val SwatchRing = Color.White.copy(alpha = 0.14f)
    val PanelShadows = listOf(
        BoxShadow(y = 2.dp, blur = 6.dp, color = Color.Black.copy(alpha = 0.05f)),
        BoxShadow(y = 4.dp, blur = 42.dp, color = Color.Black.copy(alpha = 0.24f)),
        BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
        BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
    )
    val ActiveShadows = listOf(
        BoxShadow(y = 1.dp, blur = 1.dp, color = Color.Black.copy(alpha = 0.24f)),
        BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
        BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.06f), inset = true),
    )
}

// ── Examples ──────────────────────────────────────────────────────────────────────────────

/** `.example-row-full`: 370 tall, #171717, radius 16, content centred with 48/40 padding. */
@Composable
fun ExampleRowFull(
    height: Dp = 370.dp,
    padding: Dp = 40.dp,
    radius: Dp = 16.dp,
    verticalPadding: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .cssSurface(radius) { Pg.Stage }
            .padding(horizontal = padding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** `.example-row-split`: two cells at 271fr : 552fr, min 314 tall. */
@Composable
fun ExampleRowSplit(
    left: @Composable BoxScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit,
    leftWeight: Float = 271f,
    rightWeight: Float = 552f,
    cellPadding: Dp = 40.dp,
    cellVerticalPadding: Dp = 48.dp,
    /** One column at or below this viewport width (px), like a row's own media query. */
    stackBelow: Int? = null,
) {
    if (stackBelow != null && LocalViewport.current.maxWidth(stackBelow)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExampleCell(Modifier.fillMaxWidth(), cellPadding, cellVerticalPadding, left)
            ExampleCell(Modifier.fillMaxWidth(), cellPadding, cellVerticalPadding, right)
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ExampleCell(Modifier.weight(leftWeight), cellPadding, cellVerticalPadding, left)
        ExampleCell(Modifier.weight(rightWeight), cellPadding, cellVerticalPadding, right)
    }
}

@Composable
fun ExampleCell(modifier: Modifier, padding: Dp = 40.dp, verticalPadding: Dp = 48.dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .heightIn(min = 314.dp)
            .clip(RoundedCornerShape(16.dp))
            .cssSurface(16.dp) { Pg.Stage }
            .padding(horizontal = padding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun PlaygroundLabel() {
    val fonts = LocalKraftFonts.current
    BasicText(
        "Playground",
        style = cssText(fonts.sans, 13f, 14f, FontWeight.Medium, Pg.Title),
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
    )
}

// ── Playground ────────────────────────────────────────────────────────────────────────────

/** `.pg`: the stage and a 244 dp controls panel side by side, stacked below 860 px. */
@Composable
fun Playground(
    stageMinHeight: Dp = 380.dp,
    stage: @Composable BoxScope.() -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    val stacked = LocalViewport.current.maxWidth(860)
    val stageBox: @Composable (Modifier) -> Unit = { m ->
        StageBox(m.clip(RoundedCornerShape(16.dp)).cssSurface(16.dp) { Pg.Stage }, stageMinHeight, stage)
    }
    val panel: @Composable (Modifier) -> Unit = { m ->
        Column(
            m.cssSurface(16.dp, Pg.PanelShadows) { Pg.Panel }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = controls,
        )
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            stageBox(Modifier.fillMaxWidth())
            panel(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            stageBox(Modifier.weight(1f).fillMaxHeight())
            panel(Modifier.width(244.dp).fillMaxHeight())
        }
    }
}

/**
 * The stage box: at least [minHeight], stretched to the row's height beside the panel. Its
 * intrinsic height is [minHeight] whatever the stage holds, so the row's `IntrinsicSize.Max`
 * never has to ask an effect (or a `BoxWithConstraints` inside one) for its intrinsics.
 */
@Composable
private fun StageBox(modifier: Modifier, minHeight: Dp, content: @Composable BoxScope.() -> Unit) {
    Layout(
        content = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content) },
        modifier = modifier,
        measurePolicy = object : MeasurePolicy {
            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                val w = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
                val h = maxOf(constraints.minHeight, minHeight.roundToPx()).coerceAtMost(constraints.maxHeight)
                val placeable = measurables.first().measure(Constraints.fixed(w, h))
                return layout(w, h) { placeable.place(0, 0) }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = minHeight.roundToPx()
            override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = minHeight.roundToPx()
            override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0
            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0
        },
    )
}

/** `.btn-animate.pg-play`: the Play / Pause pill 20 dp above the stage's bottom edge. */
@Composable
fun BoxScope.PlayPauseButton(playing: Boolean, onToggle: () -> Unit) {
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
        StageButton(if (playing) "Pause" else "Play", onClick = onToggle)
    }
}

/** `.pg-toolbar`: a row of stage buttons, 20 dp above the stage's bottom edge. */
@Composable
fun BoxScope.StageToolbar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** `.btn-animate` at stage size: a 32 dp pill; disabled, it keeps its fill and dims its label. */
@Composable
fun StageButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val fonts = LocalKraftFonts.current
    val interaction = rememberInteraction()
    val rest = Color.White.copy(alpha = 0.04f)
    val bg by animatedStateColor(
        interaction,
        rest,
        if (enabled) Color.White.copy(alpha = 0.06f) else rest,
        if (enabled) Color.White.copy(alpha = 0.08f) else rest,
    )
    val text by animateColor(if (enabled) Color(0xFFE3E3E3) else Color(227, 227, 227).copy(alpha = 0.5f))
    Box(
        Modifier
            .height(32.dp)
            .cssSurface(40.dp) { bg }
            .clickableRaw(interaction, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = cssText(fonts.sans, 13f, 16f, FontWeight.Medium, text), softWrap = false)
    }
}

/** `.pg-field`: a label over its control. */
@Composable
fun PgField(label: String, content: @Composable () -> Unit) {
    val fonts = LocalKraftFonts.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(label, style = cssText(fonts.sans, 13f, 14f, FontWeight.Normal, Pg.Label))
        content()
    }
}

/** `.pg-tabs`: a wrapping row of radio pills; the active one lifts onto a brighter chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PgTabs(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((value, label) in options) PgTab(label, value == selected) { onSelect(value) }
    }
}

/** `.pg-toggle`s: independent on/off pills in the tabs' wrapping row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PgToggles(options: List<Pair<String, Boolean>>, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, (label, on) -> PgTab(label, on, toggle = true) { onToggle(i) } }
    }
}

@Composable
private fun PgTab(label: String, active: Boolean, toggle: Boolean = false, onClick: () -> Unit) {
    val fonts = LocalKraftFonts.current
    val interaction = rememberInteraction()
    val bg by animateColor(if (active) Pg.ControlActiveBg else Pg.ControlBg)
    val fg by animateColor(
        when {
            active -> Pg.ControlActiveText
            interaction.hovered -> Pg.ControlTextHover
            else -> Pg.ControlText
        },
    )
    Box(
        Modifier
            .height(32.dp)
            .cssSurface(36.dp, if (active) Pg.ActiveShadows else emptyList()) { bg }
            .clickableRaw(interaction, role = if (toggle) Role.Switch else Role.Tab, onClick = onClick)
            .semantics { if (toggle) stateDescription = if (active) "On" else "Off" else selected = active }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = cssText(fonts.sans, 13f, 16f, FontWeight.Medium, fg), softWrap = false)
    }
}

@Composable
private fun animateColor(target: Color) =
    androidx.compose.animation.animateColorAsState(target, tween(KraftMotion.Quick, easing = KraftMotion.Ease))

/**
 * `.pg-vslider`: a 32 dp block whose raised fill grows with the value; the label rides the fill,
 * the value sits at the right, and a grab line fades in at the fill's end on hover. Press or drag
 * anywhere sets the value from the pointer's x, snapped to [step] like `<input type=range>`.
 */
@Composable
fun PgSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    display: String,
    onChange: (Float) -> Unit,
) {
    val fonts = LocalKraftFonts.current
    val interaction = rememberInteraction()
    var dragging by remember { mutableStateOf(false) }
    val currentOnChange by rememberUpdatedState(onChange)
    val bg by animateColor(if (interaction.hovered || dragging) Pg.ControlBgHover else Pg.ControlBg)
    val grab by animateFloatAsState(
        when {
            dragging -> 1f
            interaction.hovered -> 0.6f
            else -> 0f
        },
        tween(KraftMotion.Quick, easing = KraftMotion.Ease),
    )
    fun snap(raw: Float): Float {
        val snapped = round(raw / step) * step
        return (round(snapped * 1000f) / 1000f).coerceIn(range.start, range.endInclusive)
    }
    fun fromX(x: Float, width: Int) = snap(range.start + (x / width).coerceIn(0f, 1f) * (range.endInclusive - range.start))
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .cssSurface(8.dp) { bg }
            .hoverable(interaction.source)
            .pointerInput(range, step) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    currentOnChange(fromX(down.position.x, size.width))
                    down.consume()
                    horizontalDrag(down.id) { change ->
                        currentOnChange(fromX(change.position.x, size.width))
                        change.consume()
                    }
                    dragging = false
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = display
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { target ->
                    currentOnChange(snap(target))
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fill = maxOf(32.dp, maxWidth * fraction)
        Box(
            Modifier
                .width(fill)
                .fillMaxHeight()
                .cssSurface(8.dp, Pg.ActiveShadows) { Pg.ControlActiveBg }
                .drawWithContent {
                    drawContent()
                    // `::after`: 2 dp grab line, 8 dp in from the fill's end, 6 dp from top and bottom.
                    if (grab > 0f) {
                        drawRoundRect(
                            Pg.Grab.copy(alpha = grab),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width - 10.dp.toPx(), 6.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height - 12.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                        )
                    }
                },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                label,
                style = cssText(fonts.sans, 13f, 14f, FontWeight.Medium, Pg.ControlActiveText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            BasicText(
                display,
                style = cssText(fonts.sans, 13f, 14f, FontWeight.Medium, Pg.ControlActiveText),
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp),
            )
        }
    }
}

/** `.pg-group`: a titled run of sliders, 12 dp apart. */
@Composable
fun PgGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    val fonts = LocalKraftFonts.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(label, style = cssText(fonts.sans, 13f, 14f, FontWeight.Normal, Pg.Label))
        content()
    }
}

/** The panel's hairline between sections: full bleed across the 16 dp panel padding. */
@Composable
fun PgRule() {
    Box(Modifier.layout { measurable, constraints ->
        val bleed = 16.dp.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * bleed, maxWidth = constraints.maxWidth + 2 * bleed))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
    }.height(1.dp).drawBehind { drawRect(Pg.Rule) })
}

/**
 * `.pg-swatches`: 26 dp colour discs with a hairline ring; the selected one shrinks to 22 dp
 * inside a 30 dp halo of its own colour, separated by a gap of panel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PgSwatches(options: List<Triple<T, Color, String>>, current: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, color, name) in options) {
            val active = value == current
            val interaction = rememberInteraction()
            val t by animateFloatAsState(if (active) 1f else 0f, tween(KraftMotion.Quick, easing = KraftMotion.Ease))
            Box(
                Modifier
                    .size(26.dp)
                    .clickableRaw(interaction, role = Role.RadioButton) { onSelect(value) }
                    .semantics {
                        contentDescription = name
                        selected = active
                    }
                    .drawBehind {
                        val ring = Pg.SwatchRing
                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                        val r = size.minDimension / 2
                        if (t > 0f) {
                            val halo = r + 2.dp.toPx()
                            drawCircle(color.copy(alpha = color.alpha * t), halo)
                            drawCircle(ring.copy(alpha = ring.alpha * t), halo - 0.5.dp.toPx(), style = stroke)
                            drawCircle(Pg.Panel, r)
                        }
                        val inner = r - 2.dp.toPx() * t
                        drawCircle(color, inner)
                        drawCircle(ring, inner - 0.5.dp.toPx(), style = stroke)
                    },
            )
        }
    }
}

// ── Code ──────────────────────────────────────────────────────────────────────────────────

/**
 * `.code-block`: plain monospace code on #171717 with a copy button whose icon swaps to a check
 * for 1.6 s, and a right-edge fade while the code scrolls horizontally.
 */
@Composable
fun CodeSnippet(code: String, modifier: Modifier = Modifier) {
    val fonts = LocalKraftFonts.current
    val scroll = rememberScrollState()
    Box(modifier.fillMaxWidth().cssSurface(16.dp) { Pg.Stage }) {
        Box(
            Modifier
                .fillMaxWidth()
                .codeFade(scroll, fade = 72.dp)
                .horizontalScroll(scroll),
        ) {
            BasicText(
                code,
                style = cssText(fonts.mono, 12.5f, 12.5f * 1.7f, FontWeight.Normal, KraftColors.Text),
                softWrap = false,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
            )
        }
        CopyIconButton(code, Modifier.align(Alignment.TopEnd).padding(14.dp))
    }
}

/**
 * `pre[data-fade]`: while code overflows, the edges it continues past fade out over [fade]
 * through the site's five stops (0 · .14 · .46 · .86 · 1 at 0 / 12 / 30 / 62 / 100 %); [end]
 * keeps a fully faded strip at the right edge, under a copy button.
 */
fun Modifier.codeFade(scroll: ScrollState, fade: Dp, end: Dp = 0.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = size.width
        if (w <= 0f) return@drawWithContent
        val f = fade.toPx()
        if (scroll.value < scroll.maxValue) {
            val edge = w - end.toPx()
            val stops = buildList {
                add(0f to Color.Black)
                for ((at, a) in FadeStops.asReversed()) add(((edge - f * at) / w).coerceIn(0f, 1f) to Color.Black.copy(alpha = a))
                add(1f to Color.Transparent)
            }
            drawRect(Brush.horizontalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
        }
        if (scroll.value > 0) {
            val stops = buildList {
                for ((at, a) in FadeStops) add((f * at / w).coerceIn(0f, 1f) to Color.Black.copy(alpha = a))
                add(1f to Color.Black)
            }
            drawRect(Brush.horizontalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
        }
    }

/** (position into the fade from its transparent edge, alpha). */
private val FadeStops = listOf(0f to 0f, 0.12f to 0.14f, 0.3f to 0.46f, 0.62f to 0.86f, 1f to 1f)

/** `.detail-code-copy`: 32 dp, radius 9, muted icon that brightens over a chip on hover. */
@Suppress("DEPRECATION")
@Composable
fun CopyIconButton(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val interaction = rememberInteraction()
    var copied by remember { mutableIntStateOf(0) }
    LaunchedEffect(copied) {
        if (copied > 0) {
            delay(1600)
            copied = 0
        }
    }
    val bg by animatedStateColor(interaction, Color.Transparent, KraftColors.ChipHover, durationMs = 140)
    val tint by animatedStateColor(interaction, KraftColors.TextMuted, KraftColors.Text, durationMs = 140)
    Box(
        modifier
            .size(32.dp)
            .cssSurface(9.dp) { bg }
            .clickableRaw(interaction) {
                clipboard.setText(AnnotatedString(text))
                copied++
            },
        contentAlignment = Alignment.Center,
    ) {
        SwapIcon(showCheck = copied > 0, tint = tint, size = 15.dp, copyIcon = KraftIcons.Copy)
    }
}

/** The copy → check crossfade: opacity, scale 0.25 and a 4 dp blur over 300 ms. */
@Composable
fun SwapIcon(showCheck: Boolean, tint: Color, size: Dp, copyIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    val t by animateFloatAsState(if (showCheck) 1f else 0f, tween(300, easing = KraftMotion.SmoothOut))
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        KraftIcon(
            copyIcon, tint,
            Modifier.graphicsLayer { alpha = 1f - t; scaleX = 1f - 0.75f * t; scaleY = 1f - 0.75f * t }.cssBlur((4f * t).dp),
            size = size,
        )
        KraftIcon(
            KraftIcons.Check, tint,
            Modifier.graphicsLayer { alpha = t; scaleX = 0.25f + 0.75f * t; scaleY = 0.25f + 0.75f * t }.cssBlur((4f * (1f - t)).dp),
            size = size,
        )
    }
}

// ── Head ──────────────────────────────────────────────────────────────────────────────────

/**
 * `.detail-tabs`: "Preview | Install & Usage" with a pill that slides to the selected tab
 * (translate + width, 250 ms smooth-out), measured from each tab's box.
 */
@Composable
fun DetailTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val fonts = LocalKraftFonts.current
    val xs = remember { mutableStateListOf<Float>().apply { repeat(labels.size) { add(-1f) } } }
    val ws = remember { mutableStateListOf<Float>().apply { repeat(labels.size) { add(-1f) } } }
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    // The pill jumps into place on first layout and slides on every change after that.
    LaunchedEffect(selected, xs[selected], ws[selected]) {
        val tx = xs[selected]
        val tw = ws[selected]
        if (tx < 0f || tw < 0f) return@LaunchedEffect
        if (w.value == 0f) {
            x.snapTo(tx)
            w.snapTo(tw)
        } else {
            val spec = tween<Float>(KraftMotion.Fast, easing = KraftMotion.SmoothOut)
            launch { x.animateTo(tx, spec) }
            w.animateTo(tw, spec)
        }
    }
    Box(Modifier.height(36.dp).clip(RoundedCornerShape(48.dp)).padding(3.dp)) {
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), 0) }
                .layout { measurable, _ ->
                    val px = w.value.roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(px, 30.dp.roundToPx()))
                    layout(px, placeable.height) { placeable.place(0, 0) }
                }
                .cssSurface(
                    36.dp,
                    listOf(
                        BoxShadow(y = 1.dp, blur = 1.dp, color = Color.Black.copy(alpha = 0.12f)),
                        BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.06f), inset = true),
                    ),
                ) { Color.White.copy(alpha = 0.12f) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { i, label ->
                val interaction = rememberInteraction()
                val color by animateColor(
                    if (i == selected || interaction.hovered) Color(0xFFFBFBFB) else Color(0xFF979797),
                )
                Box(
                    Modifier
                        .height(30.dp)
                        .onGloballyPositioned {
                            xs[i] = it.positionInParentX()
                            ws[i] = it.size.width.toFloat()
                        }
                        .clickableRaw(interaction, role = Role.Tab) { onSelect(i) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = cssText(fonts.sans, 13f, null, FontWeight.Medium, color), softWrap = false)
                }
            }
        }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.positionInParentX(): Float =
    parentLayoutCoordinates?.localPositionOf(this, androidx.compose.ui.geometry.Offset.Zero)?.x ?: 0f

/** `.detail-prompt`: "Copy prompt" — copies the page's agent prompt; its icon swaps to a check. */
@Suppress("DEPRECATION")
@Composable
fun CopyPromptButton(label: String, text: String) {
    val fonts = LocalKraftFonts.current
    val clipboard = LocalClipboardManager.current
    val interaction = rememberInteraction()
    var copied by remember { mutableIntStateOf(0) }
    LaunchedEffect(copied) {
        if (copied > 0) {
            delay(1600)
            copied = 0
        }
    }
    val bg by animatedStateColor(
        interaction,
        Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.12f), durationMs = 140,
    )
    Row(
        Modifier
            .height(32.dp)
            .cssSurface(36.dp, Pg.ActiveShadows) { bg }
            .clickableRaw(interaction) {
                clipboard.setText(AnnotatedString(text))
                copied++
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwapIcon(showCheck = copied > 0, tint = Color(0xFFFBFBFB), size = 16.dp, copyIcon = KraftIcons.CopyPrompt)
        BasicText(label, style = cssText(fonts.sans, 13f, 14f, FontWeight.Medium, Color(0xFFFBFBFB)), modifier = Modifier.padding(start = 8.dp))
    }
}
