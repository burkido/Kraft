package com.burkido.kraft.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.gaussianBlur

/** One CSS `box-shadow` entry. */
@Immutable
class BoxShadow(
    val x: Dp = 0.dp,
    val y: Dp = 0.dp,
    val blur: Dp = 0.dp,
    val spread: Dp = 0.dp,
    val color: Color,
    val inset: Boolean = false,
)

/** The site's "material" stack: soft drop, top highlight, black ring, bottom shade, grey ring. */
fun materialShadows(ringAlpha: Float = 0.07f): List<BoxShadow> = listOf(
    BoxShadow(y = 1.dp, blur = 3.dp, color = Color.Black.copy(alpha = 0.04f)),
    BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
    BoxShadow(spread = 1.dp, color = Color.Black.copy(alpha = 0.06f), inset = true),
    BoxShadow(y = (-1).dp, color = Color.Black.copy(alpha = 0.06f), inset = true),
    BoxShadow(spread = 1.dp, color = Color(196, 196, 196).copy(alpha = ringAlpha), inset = true),
)

/** The stage ring (`::after` inset hairlines) drawn over tile and card stages. */
val StageRing: List<BoxShadow> = listOf(
    BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
    BoxShadow(spread = 1.dp, color = Color(191, 192, 203).copy(alpha = 0.1f), inset = true),
)

/**
 * A CSS box: outer shadows, then the [background], then inset shadows — the order CSS paints
 * them — all behind the node's content. [background] is read at draw time, so animating it
 * redraws without recomposing.
 */
fun Modifier.cssSurface(
    radius: Dp,
    shadows: List<BoxShadow> = emptyList(),
    background: () -> Color = { Color.Transparent },
): Modifier {
    val shape = RoundedCornerShape(radius)
    var m: Modifier = this
    for (s in shadows.filter { !it.inset }.asReversed()) {
        m = m.dropShadow(shape, Shadow(radius = s.composeBlurRadius(), color = s.color, spread = s.spread, offset = DpOffset(s.x, s.y)))
    }
    // Unblurred insets are exact hairline bands (a box minus its shifted, shrunk copy); blurred
    // ones go through Compose's inner shadow on top of them, still under the content.
    val insets = shadows.filter { it.inset && it.blur == 0.dp }.asReversed()
    val blurredInsets = shadows.filter { it.inset && it.blur > 0.dp }.asReversed()
    m = m.drawWithCache {
        val r = radius.toPx()
        val box = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
        val insetPaths = insets.map { s ->
            val sp = s.spread.toPx()
            val dx = s.x.toPx()
            val dy = s.y.toPx()
            val hole = Path().apply {
                addRoundRect(
                    RoundRect(
                        dx + sp, dy + sp, size.width + dx - sp, size.height + dy - sp,
                        CornerRadius((r - sp).coerceAtLeast(0f)),
                    ),
                )
            }
            Path.combine(PathOperation.Difference, box, hole) to s.color
        }
        onDrawBehind {
            val bg = background()
            if (bg.alpha > 0f) drawPath(box, bg)
            for ((path, color) in insetPaths) drawPath(path, color)
        }
    }
    for (s in blurredInsets) {
        m = m.innerShadow(shape, Shadow(radius = s.composeBlurRadius(), color = s.color, spread = s.spread, offset = DpOffset(s.x, s.y)))
    }
    return m
}

/** CSS blur B is a Gaussian with σ = B/2; Compose's shadow radius r gives σ = 0.57735r + 0.5. */
private fun BoxShadow.composeBlurRadius(): Dp {
    val sigma = blur.value / 2f
    return (if (sigma <= 0.5f) 0f else (sigma - 0.5f) / 0.57735f).dp
}

/**
 * CSS `filter: blur(<sigma>)`: a Gaussian of standard deviation [sigma] that spills past the
 * element's box, unlike [androidx.compose.ui.draw.blur]'s default clipping and radius units.
 */
fun Modifier.cssBlur(sigma: Dp): Modifier = this.graphicsLayer {
    val s = sigma.toPx()
    renderEffect = gaussianBlur(s)
    clip = false
}

/**
 * A negative CSS `margin-top`: pulls this element (and everything after it) up by [amount],
 * which `Modifier.offset` cannot do — offset moves drawing, not layout.
 */
fun Modifier.negativeMarginTop(amount: Dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}
