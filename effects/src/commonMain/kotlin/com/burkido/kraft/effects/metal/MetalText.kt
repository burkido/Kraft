package com.burkido.kraft.effects.metal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burkido.kraft.effects.beam.EllipseGradient
import com.burkido.kraft.effects.beam.recordInsetShadow
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import com.burkido.kraft.effects.core.gaussianBlur
import com.burkido.kraft.effects.core.isDark
import com.burkido.kraft.effects.core.rememberEffectActivity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Metal inside the letters (`MetalText`): [text] in its own [style] colour with the liquid-metal
 * sheet composited over its glyphs at [metalOpacity], zoomed to [shaderScale], plus the Figma
 * inner shadow — a hairline of light along the top inside edge of every glyph. [anchor] lets a
 * neighbouring word reflect it (see [MetalReflectionText]).
 */
@Composable
fun MetalText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    preset: MetalPreset = MetalPreset.Chromatic,
    theme: EffectTheme = EffectTheme.Dark,
    strength: Float = 1f,
    metalOpacity: Float = 0.62f,
    shaderScale: Float = 2.8f,
    innerShadow: Boolean = true,
    paused: Boolean = false,
    anchor: MetalAnchor? = null,
) {
    val local = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style, local) { measurer.measure(text, style.copy(textMotion = null)) }
    val dark = theme.isDark()
    val sheet = remember(preset, dark, local.density) { MetalSheets.get(preset, dark, local.density) }
    val frozenTime = LocalEffectFrozenTime.current
    val activity = rememberEffectActivity()
    val frozenFrame = rememberFrozenFrame(sheet, paused, snapshot = frozenTime != null)
    if (frozenTime != null) LaunchedEffect(frozenTime, sheet) { sheet.renderNow(frozenTime) }
    val running = frozenTime == null && activity.isActive && !paused
    LaunchedEffect(running, sheet) {
        if (!running) return@LaunchedEffect
        while (true) withFrameNanos { sheet.tick(it) }
    }
    val alpha = (strength * metalOpacity).coerceIn(0f, 1f) * sheet.shaderOpacity
    val w = with(local) { layout.size.width.toDp() }
    val h = with(local) { layout.size.height.toDp() }
    Box(
        modifier
            .then(activity.modifier)
            .size(w, h)
            .semantics { contentDescription = text }
            .onGloballyPositioned { anchor?.coords = it }
            .drawWithCache {
                val metal = obtainGraphicsLayer()
                val rimBlur = obtainGraphicsLayer().apply { renderEffect = gaussianBlur(0.5f * density) }
                val rim = obtainGraphicsLayer().apply { this.alpha = 0.9f }
                // Clips the blurred rim to the glyphs (a DstIn layer clears between them too).
                val glyphs = obtainGraphicsLayer().apply { blendMode = BlendMode.DstIn }
                val window = SheetWindow(sheet.size, w.value, h.value, shaderScale)
                val sizePx = IntSize(size.width.toInt(), size.height.toInt())
                onDrawBehind {
                    val frozen = frozenFrame.value
                    val image = (frozen ?: sheet.surface).image
                    anchor?.let {
                        it.sheet = sheet; it.frozen = frozen; it.w = w.value; it.h = h.value; it.radius = 4f; it.ring = 0f
                        it.shaderScale = shaderScale; it.alpha = alpha; it.deform = null
                    }
                    drawText(layout)
                    metal.alpha = alpha
                    metal.record(sizePx) {
                        drawText(layout, color = Color.White)
                        drawImage(
                            image,
                            srcOffset = IntOffset(window.sx.roundToInt(), window.sy.roundToInt()),
                            srcSize = IntSize(max(1, window.srcW.roundToInt()), max(1, window.srcH.roundToInt())),
                            dstSize = sizePx,
                            blendMode = BlendMode.SrcIn,
                        )
                    }
                    drawLayer(metal)
                    if (innerShadow) {
                        // Read outside record{}: a recording scope's density resolves back through
                        // the node's draw scope and recurses on Skiko.
                        val shift = density
                        rimBlur.record(sizePx) {
                            drawText(layout, color = Color.White)
                            translate(0f, shift) { drawText(layout, color = Color.White, blendMode = BlendMode.DstOut) }
                        }
                        glyphs.record(sizePx) { drawText(layout, color = Color.White) }
                        rim.record(sizePx) {
                            drawLayer(rimBlur)
                            drawLayer(glyphs)
                        }
                        drawLayer(rim)
                    }
                }
            },
    )
}

/**
 * A word that catches [anchor]'s metal on its letterforms (`useMetalTextReflection`): the metal
 * sheet mirrored across the gap, fading over 18 dp, cut to the glyphs.
 */
@Composable
fun MetalReflectionText(
    anchor: MetalAnchor,
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    strength: Float = 0.64f,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style, density) { measurer.measure(text, style) }
    val self = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier
            .size(with(density) { layout.size.width.toDp() }, with(density) { layout.size.height.toDp() })
            .semantics { contentDescription = text }
            .onGloballyPositioned { self[0] = it }
            .drawWithCache {
                val painter = ReflectionPainter(this)
                onDrawBehind {
                    drawText(layout)
                    anchor.version
                    val image = anchor.image ?: return@onDrawBehind
                    val t = self[0] ?: return@onDrawBehind
                    val a = anchor.coords ?: return@onDrawBehind
                    if (!t.isAttached || !a.isAttached) return@onDrawBehind
                    painter.draw(this, anchor, image, a, t, 0f, strength) {
                        drawText(layout, color = Color.White)
                    }
                }
            },
    )
}

/** The badge's Figma metrics (`METAL_BADGE_DEFAULTS`, "Live mode · New"). */
private const val BadgeW = 45f
private const val BadgeH = 25f

/**
 * The "New" pill (`MetalBadge`): a white pill with the metal over its whole face at 80 %, a clean
 * white core under the label so the metal creeps in at the rim, soft inner glows and a crisp
 * top light, and the label in Semi Bold #323232. [scale] multiplies the 45 × 25 Figma metrics;
 * [shaderScale] zooms the metal.
 */
@Composable
fun MetalBadge(
    text: String = "New",
    modifier: Modifier = Modifier,
    preset: MetalPreset = MetalPreset.Chromatic,
    theme: EffectTheme = EffectTheme.Dark,
    strength: Float = 1f,
    scale: Float = 1f,
    shaderScale: Float = 1.6f,
    paused: Boolean = false,
    textStyle: TextStyle = TextStyle(fontSize = 12.222.sp, fontWeight = FontWeight.SemiBold, lineHeight = (12.222 * 1.4).sp),
    anchor: MetalAnchor? = null,
) {
    val w = (BadgeW * scale).dp
    val h = (BadgeH * scale).dp
    MetalFxImpl(
        modifier = modifier,
        variant = MetalVariant.Button,
        preset = preset,
        theme = theme,
        strength = strength * 0.8f,
        glowGain = 1f,
        paused = paused,
        cornerRadius = null,
        innerShadow = false,
        glow = true,
        shaderScale = shaderScale,
        ringWidth = null,
        fill = Color.White,
        anchor = anchor,
        bend = null,
        fullFill = true,
    ) {
        Box(
            Modifier
                .size(w, h)
                .drawWithCache {
                    val k = scale * density
                    val r = size.height / 2
                    val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                    // The clean core: an ellipse at 49 % of the box, solid to 46 %, gone at its edge.
                    val core = EllipseGradient(arrayOf(0.46f to Color.White, 1f to Color.White.copy(alpha = 0f)))
                    val glows = obtainGraphicsLayer()
                    var recorded = false
                    // The 0.833 dp inner rim and the 0.833 dp top light (`inset 0 .833px 0`).
                    val rim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addPath(pill)
                        addRoundRect(RoundRect(0.833f * k, 0.833f * k, size.width - 0.833f * k, size.height - 0.833f * k, CornerRadius(max(0f, r - 0.833f * k))))
                    }
                    val top = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addPath(pill)
                        addPath(pill, Offset(0f, 0.833f * k))
                    }
                    onDrawBehind {
                        if (!recorded) {
                            recorded = true
                            recordInsetShadow(glows, size.width, size.height, r, 8.333f * k, 0f, Color.White.copy(alpha = 0.41f))
                        }
                        clipPath(pill) {
                            core.draw(this, size.width / 2, size.height / 2, size.width * 0.49f, size.height * 0.49f, alpha = 0.94f)
                            // Two stacked soft inner glows.
                            drawLayer(glows)
                            drawLayer(glows)
                            drawPath(top, Color.White.copy(alpha = 0.78f))
                        }
                        drawPath(rim, Color.White.copy(alpha = 0.5f))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text, style = textStyle.copy(color = Color(0xFF323232), fontSize = textStyle.fontSize * scale, lineHeight = textStyle.lineHeight * scale))
        }
    }
}
