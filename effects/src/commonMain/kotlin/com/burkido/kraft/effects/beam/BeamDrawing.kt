package com.burkido.kraft.effects.beam

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.burkido.kraft.effects.core.gaussianBlur
import kotlin.math.ceil

/**
 * One CSS `radial-gradient(ellipse rx ry at cx cy, stops…)` whose centre and radii may change
 * every frame. The shader is built once at unit radius and re-targeted through its transform,
 * so moving blobs never allocate new shaders.
 */
internal class EllipseGradient(stops: Array<Pair<Float, Color>>) {
    private val brush = Brush.radialGradient(*stops, center = Offset.Zero, radius = 1f) as ShaderBrush
    private val matrix = Matrix()

    /** Paints the gradient's own ellipse (everything outside it is transparent anyway). */
    fun draw(scope: DrawScope, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float = 1f) {
        if (rx <= 0f || ry <= 0f || alpha <= 0f) return
        aim(cx, cy, rx, ry)
        scope.drawRect(brush, Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry), alpha.coerceAtMost(1f))
    }

    /**
     * Paints the whole [cover] (the scope's size by default) — needed for masks, where pixels
     * outside the ellipse must be hit too. Pass the full size when drawing under a scale.
     */
    fun drawMask(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        blendMode: BlendMode = BlendMode.DstIn,
        cover: Size = scope.size,
    ) {
        if (rx <= 0f || ry <= 0f) {
            scope.drawRect(Color.Transparent, size = cover, blendMode = blendMode)
            return
        }
        aim(cx, cy, rx, ry)
        scope.drawRect(brush, size = cover, blendMode = blendMode)
    }

    private fun aim(cx: Float, cy: Float, rx: Float, ry: Float) {
        matrix.reset()
        matrix.translate(cx, cy)
        matrix.scale(rx, ry)
        brush.transform = matrix
    }

    companion object {
        /** `color, transparent` — the plain CSS blob. */
        fun fade(color: Color) = EllipseGradient(arrayOf(0f to color, 1f to color.copy(alpha = 0f)))

        /** CSS stops as `[percent, alpha]` pairs of one colour. */
        fun ofStops(color: Color, stops: List<List<Double>>) = EllipseGradient(stops.toStops(color))

        /** A mask ellipse: white → [soft] alpha at [softAt] (0..1) → transparent. */
        fun softMask(softAt: Float, soft: Float) =
            EllipseGradient(arrayOf(0f to Color.White, softAt to Color.White.copy(alpha = soft), 1f to Color.White.copy(alpha = 0f)))
    }
}

/** A rounded rect with CSS-style radius clamping at zero. */
internal fun roundRectPath(left: Float, top: Float, right: Float, bottom: Float, radius: Float): Path =
    Path().apply { addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(maxOf(0f, radius)))) }

/**
 * The CSS border ring `mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
 * mask-composite: exclude` on a box with [radius] and `padding: [width]`: the border box minus the
 * content box (inset [width], radius − [width]).
 */
internal fun ringPath(w: Float, h: Float, radius: Float, width: Float): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(maxOf(0f, radius))))
    addRoundRect(RoundRect(width, width, w - width, h - width, CornerRadius(maxOf(0f, radius - width))))
}

/**
 * Records `box-shadow: inset 0 0 <blurPx> <spreadPx> <color>` for a [w]×[h] box with [radius]:
 * everything outside the box shrunk by the spread, blurred with σ = blur / 2. The layer extends
 * past the box so the blur sees the shadow continue beyond the edges, like the unbounded CSS one.
 */
internal fun DrawScope.recordInsetShadow(
    layer: GraphicsLayer,
    w: Float,
    h: Float,
    radius: Float,
    blurPx: Float,
    spreadPx: Float,
    color: Color,
) {
    val sigma = blurPx / 2f
    val margin = ceil(sigma * 4f).toInt() + 1
    layer.renderEffect = gaussianBlur(sigma)
    layer.topLeft = IntOffset(-margin, -margin)
    layer.record(IntSize(w.toInt() + 2 * margin, h.toInt() + 2 * margin)) {
        translate(margin.toFloat(), margin.toFloat()) {
            val frame = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(-margin.toFloat(), -margin.toFloat(), w + margin, h + margin))
                addRoundRect(RoundRect(spreadPx, spreadPx, w - spreadPx, h - spreadPx, CornerRadius(maxOf(0f, radius - spreadPx))))
            }
            drawPath(frame, color)
        }
    }
}

/**
 * Records the `md`/`line` edge fades as a DstIn mask layer: two linear gradients, fading from the
 * edge to transparent over [edgePx], added together (`mask-composite: add`).
 */
internal fun DrawScope.recordEdgeFadeMask(layer: GraphicsLayer, w: Float, h: Float, edgePx: Float) {
    layer.blendMode = BlendMode.DstIn
    layer.record {
        drawRect(
            Brush.verticalGradient(
                0f to Color.White, edgePx / h to Color.Transparent,
                1f - edgePx / h to Color.Transparent, 1f to Color.White,
            ),
        )
        drawRect(
            Brush.horizontalGradient(
                0f to Color.White, edgePx / w to Color.Transparent,
                1f - edgePx / w to Color.Transparent, 1f to Color.White,
            ),
        )
    }
}

/** JavaScript's `Number.toFixed(1)` for the positive durations the CSS generator rounds. */
internal fun toFixed1(v: Double): Double = kotlin.math.floor(v * 10 + 0.5) / 10
