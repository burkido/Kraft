package com.burkido.kraft.landing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.KraftIcons
import com.burkido.kraft.designsystem.KraftIcon
import com.burkido.kraft.designsystem.KraftMotion
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.SkillButton
import com.burkido.kraft.designsystem.StageRing
import com.burkido.kraft.designsystem.animatedStateColor
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.materialShadows
import com.burkido.kraft.designsystem.rememberInteraction
import com.burkido.kraft.library.LibraryId
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val TILE = 130f

/** The five floating previews, in the site's DOM order. */
private enum class HeroTileKind(val library: LibraryId, val label: String) {
    Orb(LibraryId.Orbs, "Thinking orbs"),
    Beam(LibraryId.Beam, "Border beam"),
    Metal(LibraryId.Metal, "Liquid metal"),
    Gooey(LibraryId.Gooey, "Gooey"),
    Image(LibraryId.Image, "Image generation"),
}

/** `.header`: badge, title, subtitle, CTAs, and the draggable tiles. */
@Composable
fun Hero(onBrowse: () -> Unit, onHowTo: () -> Unit, onOpenLibrary: (LibraryId) -> Unit) {
    if (LocalViewport.current.minWidth(761)) {
        DesktopHero(onBrowse, onHowTo, onOpenLibrary)
    } else {
        PhoneHero(onBrowse, onHowTo, onOpenLibrary)
    }
}

@Composable
private fun DesktopHero(onBrowse: () -> Unit, onHowTo: () -> Unit, onOpenLibrary: (LibraryId) -> Unit) {
    val vp = LocalViewport.current
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 689.dp)
            .onSizeChanged { box = it },
    ) {
        val half = with(density) { (box.width / 2f).toDp() }
        // `.hero-tile--*` positions: fixed tops, lefts relative to the box centre and the viewport.
        val positions = mapOf(
            HeroTileKind.Orb to (half - 7.dp to 0.dp),
            HeroTileKind.Beam to (half - minOf(481.dp, vp.vw(37.6f)) to 147.dp),
            HeroTileKind.Metal to (half + minOf(317.dp, vp.vw(24.8f)) to 171.dp),
            HeroTileKind.Gooey to (half - minOf(351.dp, vp.vw(27.4f)) to 432.dp),
            HeroTileKind.Image to (half + minOf(90.dp, vp.vw(7f)) to 475.dp),
        )
        if (box != IntSize.Zero) {
            for ((kind, pos) in positions) {
                HeroTile(kind, TILE.dp, pos.first, pos.second, box, showPill = true, onOpenLibrary)
            }
        }
        Column(
            Modifier.fillMaxWidth().zIndex(6f).padding(top = 210.dp, bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroCopy(center = true, onBrowse = onBrowse, onHowTo = onHowTo)
        }
    }
}

@Composable
private fun PhoneHero(onBrowse: () -> Unit, onHowTo: () -> Unit, onOpenLibrary: (LibraryId) -> Unit) {
    val vp = LocalViewport.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    // `100svh`: the visible height, without the system bars.
    val visibleHeight = with(density) {
        vp.height - (insets.getTop(this) + insets.getBottom(this)).toDp()
    }
    // `--hero-tile: clamp(84px, (100svh - 420px) / 3, 130px)`, gap 16.
    val tile = ((visibleHeight - 420.dp) / 3).coerceIn(84.dp, 130.dp)
    val gap = 16.dp
    var box by remember { mutableStateOf(IntSize.Zero) }
    // The copy inset: `calc(32px - var(--app-gutter))`.
    val inset = 32.dp - vp.gutter
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = visibleHeight - 68.dp)
            .padding(top = 24.dp, bottom = 32.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(tile * 3 + gap * 2)
                .onSizeChanged { box = it },
        ) {
            if (box != IntSize.Zero) {
                val w = with(density) { box.width.toDp() }
                val row = tile + gap
                val positions = mapOf(
                    HeroTileKind.Beam to (12.dp to 0.dp),
                    HeroTileKind.Metal to (w - 12.dp - tile to 0.dp),
                    HeroTileKind.Orb to (w / 2 - tile / 2 to row),
                    HeroTileKind.Gooey to (12.dp to row * 2),
                    HeroTileKind.Image to (w - 12.dp - tile to row * 2),
                )
                for ((kind, pos) in positions) {
                    HeroTile(kind, tile, pos.first, pos.second, box, showPill = false, onOpenLibrary)
                }
            }
        }
        // `.hero-badge { margin-top: auto }` pins the copy to the bottom of the first screen.
        Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
        Column(Modifier.padding(start = inset)) {
            HeroCopy(center = false, onBrowse = onBrowse, onHowTo = onHowTo)
        }
    }
}

@Composable
private fun HeroCopy(center: Boolean, onBrowse: () -> Unit, onHowTo: () -> Unit) {
    val fonts = LocalKraftFonts.current
    val vp = LocalViewport.current
    val phone = vp.maxWidth(639)
    val align = if (center) TextAlign.Center else TextAlign.Start
    // `.hero-badge`
    Row(
        Modifier
            .height(28.dp)
            .cssSurface(
                24.dp,
                listOf(
                    BoxShadow(y = 1.dp, blur = 3.dp, color = Color.Black.copy(alpha = 0.04f)),
                    BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
                    BoxShadow(spread = 1.dp, color = Color(196, 196, 196).copy(alpha = 0.05f), inset = true),
                ),
            ) { KraftColors.Card }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("7 libraries", style = cssText(fonts.sans, 13f, 24.2f, FontWeight.Normal, Color(0xFFB5B5B5)))
        BasicText(
            "for Compose",
            style = cssText(fonts.sans, 13f, 24.2f, FontWeight.Normal, Color(0xFF939393)),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    BasicText(
        "High-crafted UI effects for Compose",
        style = cssText(
            fonts.display,
            if (phone) 30f else 42f,
            if (phone) 34f else 45f,
            FontWeight.Medium,
            Color(0xFFFEFEFE),
            (-0.21).sp,
        ).copy(textAlign = align),
        modifier = if (center) Modifier.widthIn(max = 409.dp) else Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    BasicText(
        "UI effects for modern apps, ported natively to Compose.",
        style = cssText(fonts.sans, if (phone) 15f else 16f, if (phone) 22f else 24f, FontWeight.Normal, Color(0xFF9E9E9E))
            .copy(textAlign = align),
        modifier = if (center) Modifier.widthIn(max = 560.dp) else Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        SkillButton("Browse", primary = true, onClick = onBrowse)
        SkillButton("How to use", primary = false, onClick = onHowTo)
    }
}

/**
 * One `.hero-tile`: a raised square whose stage holds a live preview. It lifts 6 dp with an
 * overshoot on hover or press, shows its label pill (desktop), and can be dragged anywhere inside
 * the hero box — a drag that travels more than 4 dp is not a tap.
 */
@Composable
private fun HeroTile(
    kind: HeroTileKind,
    size: Dp,
    left: Dp,
    top: Dp,
    bounds: IntSize,
    showPill: Boolean,
    onOpenLibrary: (LibraryId) -> Unit,
) {
    val fonts = LocalKraftFonts.current
    val density = LocalDensity.current
    val interaction = rememberInteraction()
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val active = interaction.hovered || interaction.pressed
    val lift by animateFloatAsState(if (active) 6f else 0f, tween(KraftMotion.Medium, easing = KraftMotion.Lift))
    val bg by animatedStateColor(interaction, KraftColors.Card, KraftColors.CardHover)
    val pill = remember { Animatable(0f) }
    LaunchedEffect(active && showPill) {
        pill.animateTo(if (active && showPill) 1f else 0f, tween(if (active) 250 else 150, easing = KraftMotion.SmoothOut))
    }
    val scale = size.value / TILE
    val sizePx = with(density) { size.toPx() }
    val leftPx = with(density) { left.toPx() }
    val topPx = with(density) { top.toPx() }

    Box(
        Modifier
            .zIndex(if (dragging) 7f else if (drag != Offset.Zero) 6f else 5f)
            .offset { IntOffset((leftPx + drag.x).roundToInt(), (topPx + drag.y - lift * density.density).roundToInt()) }
            .requiredSize(size)
            .pointerInput(bounds, leftPx, topPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    interaction.source.tryEmit(press)
                    val start = drag
                    var moved = false
                    var total = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            interaction.source.tryEmit(PressInteraction.Release(press))
                            if (!moved) onOpenLibrary(kind.library)
                            break
                        }
                        total += change.positionChange()
                        if (!moved && hypot(total.x, total.y) > 4.dp.toPx()) {
                            moved = true
                            dragging = true
                        }
                        if (moved) {
                            change.consume()
                            // Clamp to the hero box, measured from the tile's resting rect.
                            drag = Offset(
                                (start.x + total.x).coerceIn(-leftPx, bounds.width - sizePx - leftPx),
                                (start.y + total.y).coerceIn(-topPx, bounds.height - sizePx - topPx),
                            )
                        }
                    }
                    dragging = false
                }
            }
            .hoverable(interaction.source)
            .semantics {
                role = Role.Button
                contentDescription = kind.label
                onClick { onOpenLibrary(kind.library); true }
            },
    ) {
        Box(Modifier.requiredSize(size).cssSurface((24 * scale).dp, materialShadows()) { bg }) {
            val inset = (11 * scale).dp
            Box(
                Modifier
                    .padding(inset)
                    .requiredSize(size - inset * 2)
                    .clip(RoundedCornerShape((14 * scale).dp))
                    .cssSurface((14 * scale).dp) { KraftColors.TileStage },
                contentAlignment = Alignment.Center,
            ) {
                HeroTileScene(kind, size - inset * 2)
                Box(Modifier.requiredSize(size - inset * 2).cssSurface((14 * scale).dp, StageRing))
            }
        }
        if (showPill) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 12.dp + 28.dp)
                    .graphicsLayer {
                        alpha = pill.value
                        val s = 0.97f + 0.03f * pill.value
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .height(28.dp)
                    .cssSurface(24.dp, materialShadows(ringAlpha = 0.1f)) { KraftColors.RaisedPill }
                    .padding(start = 12.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(kind.label, style = cssText(fonts.sans, 12f, 13f, FontWeight.Medium, Color(0xFFE8E8E8)), softWrap = false)
                KraftIcon(KraftIcons.ChevronFilled, Color(0xFF9A9A9A), Modifier.graphicsLayer { rotationZ = -90f })
            }
        }
    }
}

@Composable
private fun BoxScope.HeroTileScene(kind: HeroTileKind, stage: Dp) {
    LibraryScene(kind.library, stage, stage, compact = true)
}
