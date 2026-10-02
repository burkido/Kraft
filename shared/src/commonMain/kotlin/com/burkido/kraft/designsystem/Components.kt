package com.burkido.kraft.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hover (pointer) and press state for one element; touch has no hover, so press stands in. */
@Stable
class Interaction internal constructor(val source: MutableInteractionSource) {
    val hovered: Boolean @Composable get() = source.collectIsHoveredAsState().value
    val pressed: Boolean @Composable get() = source.collectIsPressedAsState().value
}

@Composable
fun rememberInteraction(): Interaction = remember { Interaction(MutableInteractionSource()) }

/** Clickable without ripple — the site has none — plus hover tracking. */
fun Modifier.clickableRaw(interaction: Interaction, role: Role = Role.Button, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.hoverable(interaction.source, enabled).clickable(interaction.source, indication = null, enabled = enabled, role = role, onClick = onClick)

/** A CSS `transition: background-color <ms>` between rest / hover / pressed colours. */
@Composable
fun animatedStateColor(interaction: Interaction, rest: Color, hover: Color, pressed: Color = hover, durationMs: Int = KraftMotion.Quick) =
    animateColorAsState(
        targetValue = when {
            interaction.pressed -> pressed
            interaction.hovered -> hover
            else -> rest
        },
        animationSpec = tween(durationMs, easing = KraftMotion.Ease),
    )

@Composable
fun KraftIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

/** `.nav-pill`: 36 dp, muted text that brightens on hover over a faint chip; `--active` keeps both. */
@Composable
fun NavPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    val interaction = rememberInteraction()
    val bg by animatedStateColor(interaction, if (active) Color.White.copy(alpha = 0.06f) else Color.Transparent, Color.White.copy(alpha = 0.06f))
    val fg by animatedStateColor(interaction, if (active) KraftColors.Text else KraftColors.TextMuted, KraftColors.Text)
    val fonts = LocalKraftFonts.current
    Box(
        modifier
            .height(36.dp)
            .cssSurface(50.dp) { bg }
            .clickableRaw(interaction, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = cssText(fonts.sans, 13f, 16f, FontWeight.Medium, fg))
    }
}

/** `.icon-btn`: a 36 dp chip circle holding a 16 dp muted icon. */
@Composable
fun ChipIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberInteraction()
    val bg by animatedStateColor(interaction, KraftColors.Chip, KraftColors.ChipHover, KraftColors.ChipPressed)
    val tint by animatedStateColor(interaction, KraftColors.IconMuted, KraftColors.Icon)
    Box(
        modifier.size(36.dp).cssSurface(50.dp) { bg }.clickableRaw(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KraftIcon(icon, tint)
    }
}

/** `.icon-btn-pill`: a chip pill with an icon and a label. */
@Composable
fun ChipPill(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberInteraction()
    val bg by animatedStateColor(interaction, KraftColors.Chip, KraftColors.ChipHover, KraftColors.ChipPressed)
    val fg by animatedStateColor(interaction, KraftColors.IconMuted, KraftColors.Icon)
    val fonts = LocalKraftFonts.current
    Row(
        modifier
            .height(36.dp)
            .cssSurface(50.dp) { bg }
            .clickableRaw(interaction, onClick = onClick)
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) KraftIcon(icon, fg)
        BasicText(text, style = cssText(fonts.sans, 13f, 16f, FontWeight.Medium, fg))
    }
}

/** `.nav-get-pro`: the blue-tinted pill. */
@Composable
fun TintPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 36.dp, horizontalPadding: Dp = 16.dp) {
    val interaction = rememberInteraction()
    val bg by animatedStateColor(interaction, KraftColors.ProTint, KraftColors.ProTintHover)
    val fonts = LocalKraftFonts.current
    Box(
        modifier
            .height(height)
            .cssSurface(50.dp) { bg }
            .clickableRaw(interaction, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = cssText(fonts.sans, 13f, null, FontWeight.Medium, KraftColors.ProInk))
    }
}

/** The hero CTA pills (`.skill-btn`): raised grey, or the blue call to action. */
@Composable
fun SkillButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberInteraction()
    val bg by if (primary) {
        animatedStateColor(interaction, KraftColors.RaisedPill, KraftColors.RaisedPillHover, KraftColors.RaisedPillPressed, 200)
    } else {
        animatedStateColor(interaction, KraftColors.Cta, KraftColors.CtaHover, KraftColors.CtaPressed, 200)
    }
    val shadows = remember(primary) {
        if (primary) {
            materialShadows(ringAlpha = 0.1f)
        } else {
            listOf(
                BoxShadow(y = 1.dp, blur = 3.dp, color = Color.Black.copy(alpha = 0.04f)),
                BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
                BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.06f), inset = true),
                BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.1f), inset = true),
            )
        }
    }
    val fonts = LocalKraftFonts.current
    Box(
        modifier
            .height(32.dp)
            .cssSurface(24.dp, shadows) { bg }
            .clickableRaw(interaction, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = cssText(fonts.sans, 13f, 13f, FontWeight.Medium, Color.White))
    }
}

/**
 * The brand mark: the site's scribble (`pathLength=100`, stroke 2.67, round caps), which erases
 * from its start and redraws (`stroke-dashoffset: 0 → -200` over 1.15 s ease-in-out) on hover or
 * press, while the whole brand scales to 1.02.
 */
@Composable
fun BrandLockup(word: String, dimWord: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberInteraction()
    val active = interaction.hovered || interaction.pressed
    val scale by animateFloatAsState(if (active) 1.02f else 1f, tween(KraftMotion.Slow, easing = KraftMotion.SmoothOut))
    val fonts = LocalKraftFonts.current
    Row(
        modifier
            .height(35.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickableRaw(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(redraw = active)
        Row {
            BasicText(word, style = cssText(fonts.sans, 15f, 15f, FontWeight.Medium, KraftColors.Text, TightTracking))
            BasicText(dimWord, style = cssText(fonts.sans, 15f, 15f, FontWeight.Medium, KraftColors.TextMuted, TightTracking))
        }
    }
}

private const val BRAND_PATH =
    "M21.3352 10.3354L6.89079 13.3354L18.0019 7.33536L1.33524 9.33536L19.3352 1.33536L1.33524 4.33536L9.33524 1.33536"

@Composable
fun BrandMark(redraw: Boolean, modifier: Modifier = Modifier, color: Color = KraftColors.Icon) {
    val path = remember { PathParser().parsePathString(BRAND_PATH).toPath() }
    val length = remember(path) { PathMeasure().apply { setPath(path, false) }.length }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(redraw) {
        if (redraw && !progress.isRunning) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1150, easing = KraftMotion.EaseInOut))
            progress.snapTo(0f)
        }
    }
    val segment = remember { Path() }
    val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
    // Its own paint: on Skiko a DrawScope `Stroke(join = Round)` is not applied, and the scribble's
    // sharp turns came out as miter spikes.
    val ink = remember {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = 2.67f
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            isAntiAlias = true
        }
    }
    Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp, 14.dp)) {
            // The dash [s, s+100] of a 200-long pattern slides along a 100-long path.
            val s = progress.value * 200f
            val (from, to) = if (s <= 100f) s / 100f to 1f else 0f to (s - 100f) / 100f
            segment.reset()
            if (to > from) measure.getSegment(from * length, to * length, segment, true)
            // SVG `xMidYMid meet`: one uniform scale, centred.
            val k = minOf(size.width / 22.6705f, size.height / 14.6705f)
            translate((size.width - 22.6705f * k) / 2f, (size.height - 14.6705f * k) / 2f) {
                scale(k, k, pivot = Offset.Zero) {
                    ink.color = color
                    drawIntoCanvas { it.drawPath(segment, ink) }
                }
            }
        }
    }
}
