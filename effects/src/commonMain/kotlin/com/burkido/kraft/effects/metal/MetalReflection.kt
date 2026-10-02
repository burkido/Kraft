package com.burkido.kraft.effects.metal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.burkido.kraft.effects.core.gaussianBlur
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// `reflection/constants.ts`.
private const val RangePx = 12f
private const val BaseAlpha = 0.55f
private const val BoostAlpha = 1.0f
private const val IntensityMult = 1.3f
private const val MaxAlphaStack = 3.6f
private const val GlobalAttenuation = 0.7f
private const val StrokeExtraAlpha = 0.52f
private const val BorderHiliteAlpha = 0.044f
private const val RefDrawW = 235f
private const val FillExtraAlpha = 2.535f
private const val FillOpacityMul = 0.7f
private const val FillCircleAttenuation = 0.5f
private const val FillBlur = 4f

/**
 * A neighbour that catches [anchor]'s metal on its facing edge (`reflectionTargets`, dark theme):
 * the ring mirrored across the gap into a blurred, lifted strip along that edge, a hairline of it
 * on the edge itself and a faint border highlight — strongest nearest the ring, gone 12 dp in.
 * [cornerRadius] is this element's own; [strength] scales the whole reflection.
 */
@Composable
fun MetalReflection(
    anchor: MetalAnchor,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    strength: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val self = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier
            .onGloballyPositioned { self[0] = it }
            .drawWithCache {
                val painter = ReflectionPainter(this)
                onDrawWithContent {
                    drawContent()
                    anchor.version
                    val image = anchor.image ?: return@onDrawWithContent
                    val t = self[0] ?: return@onDrawWithContent
                    val a = anchor.coords ?: return@onDrawWithContent
                    if (!t.isAttached || !a.isAttached) return@onDrawWithContent
                    painter.draw(this, anchor, image, a, t, cornerRadius.toPx(), strength, glyphMask = null)
                }
            },
    ) { content() }
}

/** How one facing edge is lit (`paintReflections`' per-layout geometry), in px. */
private class EdgeLayout(
    val horiz: Boolean,
    val drawX: Float, val drawY: Float, val drawW: Float, val drawH: Float,
    val g0: Offset, val g1: Offset,
)

internal class ReflectionPainter(scope: CacheDrawScope) {
    // Read once here: a recording scope's density resolves back through the node's draw scope and
    // recurses on Skiko, so nothing inside record{} may ask for it.
    private val d = scope.density
    private val fillLayer: GraphicsLayer = scope.obtainGraphicsLayer()
    private val strokeLayer: GraphicsLayer = scope.obtainGraphicsLayer()

    // The letterforms as a DstIn layer: a layer composites over its whole bounds, so it clears
    // everything between the glyphs too (a DstIn text draw would only touch the glyphs' own pixels).
    private val glyphLayer: GraphicsLayer = scope.obtainGraphicsLayer().apply { blendMode = BlendMode.DstIn }
    private val outer = OutlineBuf()
    private val inner = OutlineBuf()
    private val band = Path()
    private val clip = Path()

    /**
     * Paints the reflection of [anchor] over this target. With [glyphMask] (text targets: draws
     * the glyphs, opaque) the mirror shows the whole sheet, fades over 18 dp and is cut to the
     * letterforms.
     */
    fun draw(
        scope: DrawScope,
        anchor: MetalAnchor,
        image: androidx.compose.ui.graphics.ImageBitmap,
        anchorCoords: LayoutCoordinates,
        target: LayoutCoordinates,
        cornerPx: Float,
        strength: Float,
        glyphMask: (DrawScope.() -> Unit)?,
    ): Unit = with(scope) {
        val a = anchorCoords.boundsInRootCompat()
        val t = target.boundsInRootCompat()
        if (a.width < 4 || a.height < 4 || t.width < 1 || t.height < 1) return
        val glyph = glyphMask != null
        val range = RangePx * d
        // Only facing neighbours within 32 dp that overlap across the gap are lit.
        val overlapV = min(a.bottom, t.bottom) - max(a.top, t.top)
        val overlapH = min(a.right, t.right) - max(a.left, t.left)
        val gapH = max(max(a.left - t.right, t.left - a.right), 0f)
        val gapV = max(max(a.top - t.bottom, t.top - a.bottom), 0f)
        val attach = 32 * d
        val horizNeighbour = overlapV >= d && gapH <= attach
        val vertNeighbour = overlapH >= d && gapV <= attach
        if (!horizNeighbour && !vertNeighbour) return

        val dx = a.center.x - t.center.x
        val dy = a.center.y - t.center.y
        val dist = hypot(gapH, gapV)
        var proximity = 1 - min(1f, dist / range)
        proximity = proximity * proximity * (3 - 2 * proximity)
        val intensity = BaseAlpha + (BoostAlpha - BaseAlpha) * proximity
        val reflectionAlpha = min(MaxAlphaStack, intensity * IntensityMult * GlobalAttenuation) * strength
        val contained = a.left >= t.left && a.right <= t.right && a.top >= t.top && a.bottom <= t.bottom
        val layouts = if (contained) listOf(true, false) else listOf(gapH >= gapV)

        val tw = t.width
        val th = t.height
        val box = RoundRect(0f, 0f, tw, th, CornerRadius(min(cornerPx, min(tw, th) / 2)))
        for (horiz in layouts) {
            val bandPx = min((if (glyph) RangePx * 1.5f else RangePx) * d, max(tw, th))
            val g0: Offset
            val g1: Offset
            if (horiz) {
                g0 = Offset(if (dx > 0) tw else 0f, th / 2)
                g1 = Offset(if (dx > 0) tw - bandPx else bandPx, th / 2)
            } else {
                g0 = Offset(tw / 2, if (dy > 0) th else 0f)
                g1 = Offset(tw / 2, if (dy > 0) th - bandPx else bandPx)
            }
            val refW = if (glyph) {
                max(1f, min(if (horiz) tw else th, if (horiz) a.width else a.height))
            } else {
                max(1f, RefDrawW * max(0.1f, a.width / d / CanonicalW) * d)
            }
            val layout = if (horiz) {
                val top = max(a.top, t.top)
                val bot = min(a.bottom, t.bottom)
                EdgeLayout(true, if (dx > 0) tw - refW else 0f, top - t.top, refW, max(1f, bot - top), g0, g1)
            } else {
                val left = max(a.left, t.left)
                val right = min(a.right, t.right)
                EdgeLayout(false, left - t.left, if (dy > 0) th - refW else 0f, max(1f, right - left), refW, g0, g1)
            }
            val gradient = Brush.linearGradient(
                0f to Color.Black, 0.5f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent,
                start = g0, end = g1,
            )

            if (glyph) {
                // Letterforms: the mirrored sheet itself, one pass, kept to anti-aliasing blur.
                val alpha = min(1f, reflectionAlpha * FillOpacityMul)
                fillLayer.renderEffect = gaussianBlur(0.4f * d)
                fillLayer.colorFilter = saturateBrighten(1.35f, 1.2f)
                glyphLayer.record(IntSize(tw.roundToInt(), th.roundToInt())) { glyphMask.invoke(this) }
                fillLayer.record(IntSize(tw.roundToInt(), th.roundToInt())) {
                    drawSource(anchor, image, layout, alpha, sheetOnly = true)
                    drawRect(gradient, blendMode = BlendMode.DstIn)
                    drawLayer(glyphLayer)
                }
                drawLayer(fillLayer)
                continue
            }

            clip.rewind()
            clip.addRoundRect(box)
            clipPath(clip) {
                // Fill: the mirrored band in the edge strip, blurred and lifted.
                val fillAlpha = min(MaxAlphaStack, reflectionAlpha * FillExtraAlpha * FillOpacityMul * FillCircleAttenuation)
                fillLayer.renderEffect = gaussianBlur(FillBlur * d)
                fillLayer.colorFilter = saturateBrighten(1.2f, 1.58f)
                fillLayer.record(IntSize(tw.roundToInt(), th.roundToInt())) {
                    passes(anchor, image, layout, fillAlpha, edgeBand(box, (RangePx + FillBlur * 3) * d), gradient)
                }
                drawLayer(fillLayer)

                // Stroke: a hairline of it just inside the edge, plus the border highlight.
                strokeLayer.colorFilter = saturateBrighten(1.35f, 1.75f)
                strokeLayer.record(IntSize(tw.roundToInt(), th.roundToInt())) {
                    passes(anchor, image, layout, reflectionAlpha * StrokeExtraAlpha, edgeBand(box, max(1f, d.roundToInt().toFloat())), gradient)
                    val hi = min(0.85f, BorderHiliteAlpha * reflectionAlpha)
                    val hiBrush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = hi), 0.5f to Color.White.copy(alpha = hi * 0.45f), 1f to Color.Transparent,
                        start = g0, end = g1,
                    )
                    clipPath(edgeBand(box, d)) {
                        val p = Path().apply { addRoundRect(box) }
                        drawPath(p, hiBrush, style = Stroke(2 * d), blendMode = BlendMode.Plus)
                    }
                }
                drawLayer(strokeLayer)
            }
        }
    }

    /** `maskedFillPasses` / `maskedStrokePasses`: up to three stacked chunks, then the gradient. */
    private fun DrawScope.passes(anchor: MetalAnchor, image: androidx.compose.ui.graphics.ImageBitmap, layout: EdgeLayout, total: Float, clipBand: Path, gradient: Brush) {
        var remaining = max(0f, total)
        var first = true
        var i = 0
        while (i < 3 && remaining > 1e-4f) {
            val a = min(1f, remaining)
            clipPath(clipBand) {
                drawSource(anchor, image, layout, a, sheetOnly = false, blend = if (first) BlendMode.SrcOver else BlendMode.Plus)
                drawRect(gradient, blendMode = BlendMode.DstIn)
            }
            first = false
            remaining -= a
            i++
        }
    }

    /** The anchor's band (or its whole sheet window) stretched and mirrored into the layout's rect. */
    private fun DrawScope.drawSource(
        anchor: MetalAnchor,
        image: androidx.compose.ui.graphics.ImageBitmap,
        l: EdgeLayout,
        alpha: Float,
        sheetOnly: Boolean,
        blend: BlendMode = BlendMode.SrcOver,
    ) {
        val aw = anchor.w * d
        val ah = anchor.h * d
        if (aw <= 0f || ah <= 0f) return
        val sx = l.drawW / aw * (if (l.horiz) -1f else 1f)
        val sy = l.drawH / ah * (if (l.horiz) 1f else -1f)
        val ox = l.drawX + if (l.horiz) l.drawW else 0f
        val oy = l.drawY + if (l.horiz) 0f else l.drawH
        val window = SheetWindow(image.width, anchor.w, anchor.h, anchor.shaderScale)
        withTransform({
            translate(ox, oy)
            scale(sx, sy, pivot = Offset.Zero)
        }) {
            val draw: DrawScope.() -> Unit = {
                drawImage(
                    image,
                    srcOffset = IntOffset(window.sx.roundToInt(), window.sy.roundToInt()),
                    srcSize = IntSize(max(1, window.srcW.roundToInt()), max(1, window.srcH.roundToInt())),
                    dstSize = IntSize(aw.roundToInt(), ah.roundToInt()),
                    alpha = (alpha * anchor.alpha).coerceIn(0f, 1f),
                    blendMode = blend,
                )
            }
            if (sheetOnly) {
                draw()
            } else {
                roundRectOutline(0f, 0f, anchor.w, anchor.h, anchor.radius, anchor.deform, outer)
                roundRectOutline(anchor.ring, anchor.ring, anchor.w - 2 * anchor.ring, anchor.h - 2 * anchor.ring, max(0f, anchor.radius - anchor.ring), anchor.deform, inner)
                bandPath(outer, inner, d, band)
                clipPath(band) { draw() }
            }
        }
    }

    /** The evenodd strip [width] px wide just inside the rounded box. */
    private fun edgeBand(box: RoundRect, width: Float): Path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(box)
        if (box.width > 2 * width && box.height > 2 * width) {
            val r = max(0f, box.topLeftCornerRadius.x - width)
            addRoundRect(RoundRect(width, width, box.width - width, box.height - width, CornerRadius(r)))
        }
    }
}

/** CSS `saturate(s) brightness(b)` as one colour matrix (Rec. 709 luma, as the Filter spec). */
internal fun saturateBrighten(s: Float, b: Float): ColorFilter {
    val lr = 0.2126f
    val lg = 0.7152f
    val lb = 0.0722f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                (lr + (1 - lr) * s) * b, (lg - lg * s) * b, (lb - lb * s) * b, 0f, 0f,
                (lr - lr * s) * b, (lg + (1 - lg) * s) * b, (lb - lb * s) * b, 0f, 0f,
                (lr - lr * s) * b, (lg - lg * s) * b, (lb + (1 - lb) * s) * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/** The node's box in root px. */
internal fun LayoutCoordinates.boundsInRootCompat(): Rect {
    val o = localToRoot(Offset.Zero)
    return Rect(o, Size(size.width.toFloat(), size.height.toFloat()))
}
