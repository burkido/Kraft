package com.burkido.kraft.effects.core

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/**
 * CSS `filter: blur(σ)`: a Gaussian of standard deviation [sigmaPx] (read at draw time, so it can
 * animate without recomposing) that spills past the element's box. Where `RenderEffect` doesn't
 * run (Android < 12) it is drawn with [LayerBlur]'s layer pyramid.
 */
fun Modifier.kraftBlur(sigmaPx: Density.() -> Float): Modifier =
    if (KraftBlur.useFallback) {
        this then LayerBlurElement(sigmaPx)
    } else {
        graphicsLayer {
            renderEffect = gaussianBlur(sigmaPx())
            clip = false
        }
    }

private class LayerBlurElement(val sigmaPx: Density.() -> Float) : ModifierNodeElement<LayerBlurNode>() {
    override fun create() = LayerBlurNode(sigmaPx)

    override fun update(node: LayerBlurNode) {
        node.sigmaPx = sigmaPx
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kraftBlur"
    }

    override fun equals(other: Any?) = other is LayerBlurElement && other.sigmaPx === sigmaPx

    override fun hashCode() = sigmaPx.hashCode()
}

private class LayerBlurNode(var sigmaPx: Density.() -> Float) : Modifier.Node(), DrawModifierNode {
    private val owned = ArrayList<GraphicsLayer>()
    private var target: GraphicsLayer? = null
    private var blur: LayerBlur? = null

    private fun obtain(): GraphicsLayer = requireGraphicsContext().createGraphicsLayer().also { owned += it }

    override fun ContentDrawScope.draw() {
        val sigma = sigmaPx()
        if (sigma <= 0.5f) {
            drawContent()
            return
        }
        val layer = target ?: obtain().also { target = it }
        val chain = blur ?: LayerBlur(::obtain).also { blur = it }
        // Room for the blur to spill past the box: 3σ holds all but 0.3 % of the Gaussian.
        val margin = ceil(sigma * 3f).toInt()
        layer.topLeft = IntOffset(-margin, -margin)
        recordBlurred(layer, chain, sigma, IntSize(size.width.toInt() + 2 * margin, size.height.toInt() + 2 * margin)) {
            translate(margin.toFloat(), margin.toFloat()) { this@draw.drawContent() }
        }
        drawLayer(layer)
    }

    override fun onDetach() {
        val context = requireGraphicsContext()
        owned.forEach(context::releaseGraphicsLayer)
        owned.clear()
        target = null
        blur = null
    }
}
