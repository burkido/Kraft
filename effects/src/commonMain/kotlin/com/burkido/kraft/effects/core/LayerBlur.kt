package com.burkido.kraft.effects.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

/** Whether a [GraphicsLayer]'s `renderEffect` blurs on this platform (Android 12+, Skia everywhere). */
internal expect val renderEffectBlurSupported: Boolean

/** How the effects blur. */
object KraftBlur {
    /**
     * Draws every blur with the layer-pyramid fallback, even where `RenderEffect` works: for
     * checking the Android < 12 path on a newer device. Set it before the effects first draw.
     */
    var forceFallback: Boolean = false

    internal val useFallback: Boolean get() = forceFallback || !renderEffectBlurSupported
}

/**
 * A Gaussian blur of σ px for a [GraphicsLayer], that also works where `RenderEffect` doesn't
 * (Android < 12). Where it does, it is just the layer's `renderEffect`.
 *
 * The fallback is a box pyramid of offscreen layers: the content is drawn at 1 / s₀ scale, halved
 * n times and doubled back, and the GPU resamples every step bilinearly, so each halving averages
 * 2×2 texels and each doubling interpolates them. The chain's response is close to a Gaussian of
 * σ ≈ s₀·2ⁿ⁻¹ (s₀ in 1..2), and it stays put as content moves, because only the first step is
 * fractional and that one draws the content itself (vector, anti-aliased) rather than sampling it.
 *
 * A box pyramid's kernel is slightly square, which no glow shows but a hard alpha threshold does
 * (round goo edges turn lumpy by ~1 px). [isotropic] chains drop one halving and smooth each of
 * the two coarsest levels with [1 2 1] / 4 along x, then y (the extra taps Dual Kawase uses), which
 * brings the kernel within ~0.2 px of a Gaussian's contours at the cost of sixteen more small
 * layers. The taps sit on whole texels: Android 8's GL renderer snaps a layer drawn with a pure
 * translation to the pixel grid, so half-texel taps would shift the result.
 *
 * The pyramid's layers come from [obtain] and only when the fallback is in use.
 */
internal class LayerBlur(private val obtain: () -> GraphicsLayer, private val isotropic: Boolean = false) {
    private val layers = ArrayList<GraphicsLayer>()

    /** Pooled layer [i], reset: the chain's shape (and so each layer's role) follows σ. */
    private fun layer(i: Int): GraphicsLayer {
        while (layers.size <= i) layers += obtain()
        return layers[i].apply {
            alpha = 1f
            blendMode = BlendMode.SrcOver
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    /**
     * Records [block] into [target] at [size], blurred by [sigmaPx] in the layer's own pixels.
     * [target] keeps its other properties (alpha, colour filter, transform): they apply to the
     * blurred result, as they do to a `renderEffect`.
     */
    fun record(scope: DrawScope, target: GraphicsLayer, sigmaPx: Float, size: IntSize, block: DrawScope.() -> Unit) = with(scope) {
        if (!KraftBlur.useFallback) {
            target.renderEffect = gaussianBlur(sigmaPx)
            target.record(size, block)
            return
        }
        target.renderEffect = null
        if (sigmaPx <= 0.5f || size.width <= 0 || size.height <= 0) {
            target.record(size, block)
            return
        }
        val tapped = isotropic && sigmaPx >= IsotropicGain * 2f
        val (halvings, s0) = if (tapped) planIsotropic(sigmaPx) else plan(sigmaPx)

        // Level 0 is a plain display list: drawing it scaled re-renders the vectors.
        val source = layer(0).apply { compositingStrategy = CompositingStrategy.Auto }
        source.record(size, block)

        var i = 1
        /** [child] smoothed by [1 2 1] / 4 along x, then y, at its own resolution: shifted copies, added. */
        fun taps(child: GraphicsLayer): GraphicsLayer {
            var cur = child
            for (axis in 0..1) {
                val src = cur
                val taps = Array(3) { t ->
                    layer(i++).apply {
                        alpha = if (t == 1) 0.5f else 0.25f
                        blendMode = BlendMode.Plus
                        val d = (t - 1).toFloat()
                        record(src.size) { translate(if (axis == 0) d else 0f, if (axis == 1) d else 0f) { drawLayer(src) } }
                    }
                }
                cur = layer(i++).apply { record(src.size) { for (tap in taps) drawLayer(tap) } }
            }
            return cur
        }

        var cur = layer(i++).apply {
            record(levelSize(size, s0)) { scale(1f / s0, 1f / s0, Offset.Zero) { drawLayer(source) } }
        }
        if (tapped && halvings == 1) cur = taps(cur)
        val sizes = ArrayList<IntSize>(halvings + 1).apply { add(cur.size) }
        for (h in 1..halvings) {
            val child = cur
            val half = IntSize(ceil(child.size.width / 2f).toInt(), ceil(child.size.height / 2f).toInt())
            sizes += half
            cur = layer(i++).apply { record(half) { scale(0.5f, 0.5f, Offset.Zero) { drawLayer(child) } } }
            if (tapped && h >= halvings - 1) cur = taps(cur)
        }
        for (k in halvings - 1 downTo 0) {
            val child = cur
            cur = layer(i++).apply { record(sizes[k]) { scale(2f, 2f, Offset.Zero) { drawLayer(child) } } }
        }
        val level1 = cur
        target.record(size) { scale(s0, s0, Offset.Zero) { drawLayer(level1) } }
    }

    private fun levelSize(size: IntSize, s0: Float) =
        IntSize(ceil(size.width / s0).toInt(), ceil(size.height / s0).toInt())

    companion object {
        /** σ / (s₀·2ⁿ) of an [isotropic] chain with n ≥ 1 halvings, measured on the chain. */
        private const val IsotropicGain = 0.968f

        /**
         * The halvings n and first-step scale s₀ for [sigmaPx]. A chain of n ≥ 1 halvings has
         * σ ≈ s₀·2ⁿ⁻¹, so n = ⌊log₂σ⌋ + 1 leaves s₀ in 1..2. Below σ = 1 the single scaled step
         * alone gives σ ≈ 0.7..0.9.
         */
        internal fun plan(sigmaPx: Float): Pair<Int, Float> {
            if (sigmaPx < 1f) return 0 to (2f * sigmaPx).coerceIn(1f, 2f)
            val n = floor(log2(sigmaPx)).toInt() + 1
            return n to (sigmaPx / 2f.pow(n - 1)).coerceIn(1f, 2f)
        }

        /** [plan] for an [isotropic] chain, σ ≈ 0.968·s₀·2ⁿ, for σ ≥ 2·0.968. */
        internal fun planIsotropic(sigmaPx: Float): Pair<Int, Float> {
            val n = floor(log2(sigmaPx / IsotropicGain)).toInt().coerceAtLeast(1)
            return n to (sigmaPx / (IsotropicGain * 2f.pow(n))).coerceIn(1f, 2f)
        }
    }
}

/** [LayerBlur.record] for a layer drawn in this scope, at this scope's size by default. */
internal fun DrawScope.recordBlurred(
    layer: GraphicsLayer,
    blur: LayerBlur,
    sigmaPx: Float,
    size: IntSize = this.size.toIntSize(),
    block: DrawScope.() -> Unit,
) = blur.record(this, layer, sigmaPx, size, block)
