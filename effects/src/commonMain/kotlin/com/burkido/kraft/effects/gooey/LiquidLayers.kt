package com.burkido.kraft.effects.gooey

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import com.burkido.kraft.effects.core.LayerBlur
import com.burkido.kraft.effects.core.recordBlurred

/**
 * [liquidRenderEffect]'s filter chain built from layers instead of a render effect, for where
 * render effects don't run (Android < 12). Every node is a layer of the filter region's size:
 * blurs are [LayerBlur]s, colour matrices and tints are layer colour filters, offsets are
 * translations and blends are a layer's blend mode inside an offscreen parent. Nodes are taken
 * from a pool in the same order every frame, so each frame re-records the same layers.
 */
internal class LiquidLayers(
    private val obtain: () -> GraphicsLayer,
    private val blurPx: Float,
    private val contrast: Float,
    private val fill: Color,
    private val shadows: List<ShadowPx>,
) {
    private val layers = ArrayList<GraphicsLayer>()
    private val blurs = ArrayList<LayerBlur>()

    // The field is cut by a steep threshold, which shows a box pyramid's square kernel.
    private val fieldBlur = LayerBlur(obtain, isotropic = true)
    private var nextLayer = 0
    private var nextBlur = 0
    private val intercept = gooIntercept(contrast)

    /**
     * Records [blobs] filtered into [target] at [size]. [target]'s own properties (position,
     * alpha) apply to the result.
     */
    fun record(scope: DrawScope, target: GraphicsLayer, size: IntSize, blobs: DrawScope.() -> Unit) = with(scope) {
        nextLayer = 0
        nextBlur = 0
        val field = layer()
        if (blurPx > 0f) recordBlurred(field, fieldBlur, blurPx, size, blobs) else field.record(size, blobs)

        // The cut is a white mask, tinted afterwards. Android 8's GL renderer premultiplies a colour
        // matrix's result before clamping its alpha, so a steep cut (alpha ≫ 1 inside the shape)
        // would push any colour but white towards white.
        fun cut(slope: Float, offset: Float): GraphicsLayer = layer().apply {
            colorFilter = ColorFilter.colorMatrix(
                ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, slope, offset * 255f)),
            )
            record(size) { drawLayer(field) }
        }

        fun bin(grow: Float): GraphicsLayer {
            val icpt = thresholdShift(contrast, intercept, blurPx, grow)
            return cut(BinarizeSlope * contrast, BinarizeSlope * icpt + BinarizeIntercept)
        }

        fun offset(x: Float, y: Float, input: GraphicsLayer): GraphicsLayer =
            layer().apply { record(size) { translate(x, y) { drawLayer(input) } } }

        fun blur(sigmaPx: Float, input: GraphicsLayer): GraphicsLayer =
            if (sigmaPx > 0.5f) layer().also { recordBlurred(it, blur(), sigmaPx, size) { drawLayer(input) } } else input

        fun tint(color: Color, input: GraphicsLayer): GraphicsLayer = layer().apply {
            colorFilter = ColorFilter.tint(color, BlendMode.SrcIn)
            record(size) { drawLayer(input) }
        }

        fun over(bottom: GraphicsLayer, top: GraphicsLayer): GraphicsLayer =
            layer().apply { record(size) { drawLayer(bottom); drawLayer(top) } }

        /** `src` with `dst` cut out of it: the platforms' blend(SRC_OUT, dst, src). */
        fun srcOut(dst: GraphicsLayer, src: GraphicsLayer): GraphicsLayer = layer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
            src.blendMode = BlendMode.SrcOut
            record(size) { drawLayer(dst); drawLayer(src) }
        }

        val shape = tint(fill, cut(contrast, intercept))
        val svg = shadows.filter { it.inset || it.spread != 0f }
        val drops = shadows.filter { !it.inset && it.spread == 0f }

        fun pass(s: ShadowPx): GraphicsLayer = if (s.inset) {
            // The band: the silhouette minus its shrunk / offset / blurred self.
            var src = if (s.spread != 0f) bin(-s.spread) else bin(0f)
            if (s.x != 0f || s.y != 0f) src = offset(s.x, s.y, src)
            if (s.blur > 0f) src = blur(s.blur / 2, src)
            tint(s.color, srcOut(src, bin(0f)))
        } else {
            var src = if (s.spread != 0f) bin(s.spread) else shape
            if (s.blur > 0f) src = blur(s.blur / 2, src)
            if (s.x != 0f || s.y != 0f) src = offset(s.x, s.y, src)
            tint(s.color, src)
        }

        // Outer passes under the shape (the first listed on top), inset passes over it.
        var merged: GraphicsLayer? = null
        for (s in svg.filter { !it.inset }.asReversed()) merged = merged?.let { over(it, pass(s)) } ?: pass(s)
        merged = merged?.let { over(it, shape) } ?: shape
        for (s in svg.filter { it.inset }) merged = over(merged!!, pass(s))

        // CSS `drop-shadow()`s on the rendered liquid, applied in order (blur is σ, not 2σ).
        var out = merged!!
        for (s in drops) out = over(tint(s.color, offset(s.x, s.y, blur(s.blur, out))), out)
        val result = out
        target.renderEffect = null
        target.record(size) { drawLayer(result) }
    }

    /** The next pooled layer, reset to a plain display list. */
    private fun layer(): GraphicsLayer {
        if (layers.size <= nextLayer) layers += obtain()
        return layers[nextLayer++].apply {
            colorFilter = null
            blendMode = BlendMode.SrcOver
            compositingStrategy = CompositingStrategy.Auto
        }
    }

    private fun blur(): LayerBlur {
        if (blurs.size <= nextBlur) blurs += LayerBlur(obtain)
        return blurs[nextBlur++]
    }
}
