package com.burkido.kraft.effects.metal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.burkido.kraft.effects.beam.recordInsetShadow
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import com.burkido.kraft.effects.core.PixelSurface
import com.burkido.kraft.effects.core.gaussianBlur
import com.burkido.kraft.effects.core.isDark
import com.burkido.kraft.effects.core.rememberEffectActivity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** `variant`: a pill button (1 dp ring, shader scale 1.6) or a compact circle (2 dp ring, 1.3). */
enum class MetalVariant { Button, Circle }

/**
 * Where a [MetalFx] publishes itself so neighbours can reflect it (`reflectionTargets`): pass the
 * same anchor to `MetalFx(anchor = …)` and to a [MetalReflection] (or [MetalReflectionText]) around each target.
 */
@Stable
class MetalAnchor internal constructor() {
    internal var coords: LayoutCoordinates? = null
    internal var sheet: MetalSheet? = null
    internal var frozen: PixelSurface? = null
    internal var w = 0f
    internal var h = 0f
    internal var radius = 0f
    internal var ring = 0f
    internal var shaderScale = 1f
    internal var alpha = 1f
    internal var deform: Deform? = null

    /** Bumped when the geometry changes, so targets redraw. */
    internal var version by mutableIntStateOf(0)

    /** The texture to mirror; reading it subscribes a draw to every sheet upload. */
    internal val image: ImageBitmap? get() = (frozen ?: sheet?.surface)?.image
}

@Composable
fun rememberMetalAnchor(): MetalAnchor = remember { MetalAnchor() }

/**
 * metal-fx v2's `<MetalFx>`: an animated liquid-metal ring around [content] — Paper Shaders'
 * liquidMetal material (rendered on the CPU into one shared sheet at 15 fps) cropped to this
 * box and punched to a [ringWidth] band, with the surface fill, rims, a wandering glow that hunts
 * the brightest point of the ring, and an optional inner-shadow rim. [bend] lets a pointer dent
 * the ring; [anchor] lets neighbours reflect it.
 */
@Composable
fun MetalFx(
    modifier: Modifier = Modifier,
    variant: MetalVariant = MetalVariant.Button,
    preset: MetalPreset = MetalPreset.Chromatic,
    theme: EffectTheme = EffectTheme.Dark,
    strength: Float = 1f,
    glowGain: Float = 1f,
    paused: Boolean = false,
    cornerRadius: Dp? = null,
    innerShadow: Boolean = false,
    glow: Boolean = true,
    shaderScale: Float? = null,
    ringWidth: Dp? = null,
    fill: Color? = null,
    anchor: MetalAnchor? = null,
    bend: MetalBendState? = null,
    content: @Composable BoxScope.() -> Unit,
) = MetalFxImpl(
    modifier, variant, preset, theme, strength, glowGain, paused, cornerRadius, innerShadow, glow,
    shaderScale, ringWidth, fill, anchor, bend, fullFill = false, content = content,
)

/** [MetalFx], optionally with the metal filling the whole shape (`mask` = the pill, the badge). */
@Composable
internal fun MetalFxImpl(
    modifier: Modifier,
    variant: MetalVariant,
    preset: MetalPreset,
    theme: EffectTheme,
    strength: Float,
    glowGain: Float,
    paused: Boolean,
    cornerRadius: Dp?,
    innerShadow: Boolean,
    glow: Boolean,
    shaderScale: Float?,
    ringWidth: Dp?,
    fill: Color?,
    anchor: MetalAnchor?,
    bend: MetalBendState?,
    fullFill: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = theme.isDark()
    val circle = variant == MetalVariant.Circle
    val density = LocalDensity.current.density
    val sheet = remember(preset, dark, density) { MetalSheets.get(preset, dark, density) }
    val ring = ringWidth?.value ?: if (circle) 2f else 1f
    val scale = shaderScale ?: if (circle) 1.3f else 1.6f
    val surface = fill ?: if (dark) Color(0xFF272727) else Color.White
    val alpha = strength.coerceIn(0f, 1f) * sheet.shaderOpacity

    val host = remember { MetalHost() }
    val frozenTime = LocalEffectFrozenTime.current
    val activity = rememberEffectActivity()
    val glowState = remember { GlowState() }
    val glowFrame = remember { GlowFrame() }
    var glowTick by remember { mutableIntStateOf(0) }
    val currentStrength by rememberUpdatedState(strength * glowGain)
    val currentBend by rememberUpdatedState(bend)

    // A paused ring keeps the frame it was showing (`freezeFrame`).
    val frozenFrame = rememberFrozenFrame(sheet, paused, snapshot = frozenTime != null)

    // Snapshots: the sheet at the frozen time (the glow settles over it on the first draw).
    if (frozenTime != null) {
        LaunchedEffect(frozenTime, sheet) {
            sheet.renderNow(frozenTime)
        }
    }
    val settleGlow = remember { booleanArrayOf(false) }

    val running = frozenTime == null && activity.isActive
    LaunchedEffect(running, paused, sheet, glow) {
        if (!running) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == Long.MIN_VALUE) 1f / 60 else ((now - last) / 1e9f)
                last = now
                if (!paused) {
                    sheet.tick(now)
                    if (glow && host.w > 0f && sheet.frames > 0) {
                        glowState.configure(host.w, host.h, host.radius, host.kind)
                        val window = SheetWindow(sheet.size, host.w, host.h, scale)
                        val deform = currentBend?.takeIf { it.fieldOn }?.deform
                        if (glowState.tick(now / 1e6, sheet.samples, sheet.size, window, currentStrength, deform, glowFrame)) glowTick++
                    }
                }
                val b = currentBend
                val c = host.coords
                if (b != null && c != null && c.isAttached && host.w > 0f) {
                    b.step(dt, host.w, host.h, c.localToRoot(Offset.Zero), density)
                }
            }
        }
    }

    Box(
        modifier
            .then(activity.modifier)
            .onGloballyPositioned {
                host.coords = it
                anchor?.coords = it
            }
            .drawWithCache {
                val painter = MetalRingPainter(this, ring, circle, innerShadow, dark, fullFill)
                onDrawWithContent {
                    val w = size.width / density
                    val h = size.height / density
                    val r = min(cornerRadius?.value ?: Float.MAX_VALUE, min(w, h) / 2)
                    host.w = w; host.h = h; host.radius = r
                    host.kind = if (circle || (w == h && r >= w / 2 - 0.01f)) ShapeKind.Circle else ShapeKind.Pill
                    bend?.version // A dent redraws the ring.
                    glowTick // Every glow step redraws the halo.
                    if (frozenTime != null && glow && !settleGlow[0] && sheet.frames > 0) {
                        // One and a half seconds of 60 Hz ticks: the first hotspot fully faded in.
                        settleGlow[0] = true
                        glowState.configure(w, h, r, host.kind)
                        val win = SheetWindow(sheet.size, w, h, scale)
                        repeat(90) { i -> glowState.tick(i * 1000.0 / 60, sheet.samples, sheet.size, win, strength * glowGain, null, glowFrame) }
                        glowTick++
                    }
                    val deform = bend?.takeIf { it.fieldOn }?.deform
                    val frozen = frozenFrame.value
                    val image = (frozen ?: sheet.surface).image
                    val window = SheetWindow(sheet.size, w, h, scale)
                    anchor?.let {
                        val changed = it.w != w || it.h != h || it.radius != r || it.deform !== deform
                        it.sheet = sheet; it.frozen = frozen; it.w = w; it.h = h; it.radius = r; it.ring = ring
                        it.shaderScale = scale; it.alpha = alpha; it.deform = deform
                        if (changed) it.version++
                    }
                    painter.draw(
                        this, w, h, r, deform, surface, image, window, alpha,
                        glowFrame.takeIf { glow && glowTick > 0 },
                        bendReach = bend?.reach ?: 0f,
                    )
                    drawContent()
                }
            },
    ) { content() }
}

private class MetalHost {
    var coords: LayoutCoordinates? = null
    var w = 0f
    var h = 0f
    var radius = 0f
    var kind = ShapeKind.Pill
}

/**
 * Paints one frame of a ring the way the web stacks its layers: surface fill, the metal band,
 * the circle's outer hairline, the `::before` inner glow, the glow (at 0.7, 50 % outside the band,
 * full inside it), the inner-shadow rim and the `::after` edge rim. Lengths arrive in dp.
 */
internal class MetalRingPainter(
    scope: CacheDrawScope,
    private val ring: Float,
    private val circle: Boolean,
    private val innerShadow: Boolean,
    private val dark: Boolean,
    private val fullFill: Boolean = false,
) {
    private val d = scope.density
    private val outer = OutlineBuf()
    private val inner = OutlineBuf()
    private val rimInner = OutlineBuf()
    private val hair = OutlineBuf()
    private val outerPath = Path()
    private val bandPath = Path()
    private val rimPath = Path()
    private val hairPath = Path()
    private val surroundPath = Path()
    private val shifted = Path()
    private val beforeGlow: GraphicsLayer = scope.obtainGraphicsLayer()
    private val rimLayer: GraphicsLayer = scope.obtainGraphicsLayer()
    private val rimBlur: GraphicsLayer = scope.obtainGraphicsLayer()
    private val glowLayer: GraphicsLayer = scope.obtainGraphicsLayer()
    private var beforeKey = ""

    fun draw(
        scope: DrawScope,
        w: Float,
        h: Float,
        r: Float,
        deform: Deform?,
        surface: Color,
        image: ImageBitmap,
        window: SheetWindow,
        alpha: Float,
        glow: GlowFrame?,
        bendReach: Float,
    ): Unit = with(scope) {
        if (w <= 0f || h <= 0f) return
        roundRectOutline(0f, 0f, w, h, r, deform, outer)
        roundRectOutline(ring, ring, w - 2 * ring, h - 2 * ring, max(0f, r - ring), deform, inner)
        outerPath.rewind(); outerPath.addOutline(outer, d)
        bandPath(outer, inner, d, bandPath)

        // Surface fill under the band.
        drawPath(outerPath, surface)

        // The metal band: the sheet window over the box (plus the dent's overscan while bending).
        clipPath(if (fullFill) outerPath else bandPath) { drawSheet(image, window, w, h, if (deform != null) bendReach else 0f, alpha) }

        // Circle: a 1 dp black hairline just outside the ring.
        if (circle && dark) {
            roundRectOutline(-0.5f, -0.5f, w + 1, h + 1, r + 0.5f, deform, hair)
            hairPath.rewind(); hairPath.addOutline(hair, d)
            drawPath(hairPath, Color.Black.copy(alpha = 0.45f), style = Stroke(d))
        }

        // `::before`: inset 0 0 50px white .02 (dropped while bending, as the web does).
        if (deform == null) {
            val key = "$w|$h|$r|$dark"
            if (key != beforeKey) {
                beforeKey = key
                recordInsetShadow(beforeGlow, w * d, h * d, r * d, 50 * d, 0f, if (dark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f))
            }
            clipPath(outerPath) { drawLayer(beforeGlow) }
        }

        // The glow: half strength outside the ring, full on the band, nothing in the hole.
        if (glow != null && glow.env > 0f) drawGlow(glow, w, h, r)

        // The inner-shadow rim: the band minus itself shifted down 1 dp, blurred 0.5, kept in the band.
        if (innerShadow) {
            shifted.rewind()
            shifted.addPath(bandPath, Offset(0f, d))
            shifted.fillType = PathFillType.EvenOdd
            rimBlur.renderEffect = gaussianBlur(0.5f * d)
            rimBlur.record(IntSize(size.width.toInt(), size.height.toInt())) {
                drawPath(bandPath, Color.White)
                drawPath(shifted, Color.White, blendMode = BlendMode.DstOut)
            }
            rimLayer.alpha = 0.9f
            rimLayer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                drawLayer(rimBlur)
                drawPath(bandPath, Color.White, blendMode = BlendMode.DstIn)
            }
            drawLayer(rimLayer)
        }

        // `::after`: the 1 dp (circle 2 dp) white .1 edge rim.
        val rimW = if (circle) 2f else 1f
        roundRectOutline(rimW, rimW, w - 2 * rimW, h - 2 * rimW, max(0f, r - rimW), deform, rimInner)
        bandPath(outer, rimInner, d, rimPath)
        drawPath(rimPath, if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f))
    }

    /** `copyShaderToInstance`: the window of the sheet stretched over the box. */
    private fun DrawScope.drawSheet(image: ImageBitmap, window: SheetWindow, w: Float, h: Float, overscan: Float, alpha: Float) {
        if (overscan <= 0f) {
            drawImage(
                image,
                srcOffset = IntOffset(window.sx.roundToInt(), window.sy.roundToInt()),
                srcSize = IntSize(max(1, window.srcW.roundToInt()), max(1, window.srcH.roundToInt())),
                dstSize = IntSize((w * d).roundToInt(), (h * d).roundToInt()),
                alpha = alpha,
            )
            return
        }
        // While bending the texture runs past the box so an outward bulge still has metal under it;
        // the crop grows by the same ratio, so the mapping inside the box never jumps.
        val kx = window.srcW / w
        val ky = window.srcH / h
        val sw = min(image.width.toFloat(), window.srcW + 2 * overscan * kx)
        val sh = min(image.height.toFloat(), window.srcH + 2 * overscan * ky)
        val dw = sw / kx
        val dh = sh / ky
        drawImage(
            image,
            srcOffset = IntOffset(((image.width - sw) / 2).roundToInt(), ((image.height - sh) / 2).roundToInt()),
            srcSize = IntSize(sw.roundToInt(), sh.roundToInt()),
            dstOffset = IntOffset(((w - dw) / 2 * d).roundToInt(), ((h - dh) / 2 * d).roundToInt()),
            dstSize = IntSize((dw * d).roundToInt(), (dh * d).roundToInt()),
            alpha = alpha,
        )
    }

    private fun DrawScope.drawGlow(f: GlowFrame, w: Float, h: Float, r: Float) {
        val kind = if (circle || (w == h && r >= w / 2 - 0.01f)) ShapeKind.Circle else ShapeKind.Pill
        val ratio = shapePerim(w, h, r, kind) / rrPerim(CanonicalW, CanonicalH, 20f)
        val halo = GlowSprites.halo(max(1f, GlowConfig.HaloHalfLen * ratio), d)
        val extra = GlowSprites.extra(max(0.6f, GlowConfig.ExtraHalfLen * ratio), d)
        val margin = max(halo.ay, extra.ay) + GlowConfig.ExtraOutward * ratio + 2
        surroundPath.rewind()
        surroundPath.fillType = PathFillType.EvenOdd
        surroundPath.addRect(androidx.compose.ui.geometry.Rect(-margin * d, -margin * d, (w + margin) * d, (h + margin) * d))
        surroundPath.addPath(outerPath)
        val tint = ColorFilter.tint(Color(f.tint[0], f.tint[1], f.tint[2]), BlendMode.Modulate)
        glowLayer.alpha = 0.7f * f.env
        val m = (margin * d).roundToInt()
        glowLayer.topLeft = IntOffset(-m, -m)
        glowLayer.record(IntSize(size.width.toInt() + 2 * m, size.height.toInt() + 2 * m)) {
            translate(m.toFloat(), m.toFloat()) {
                clipPath(surroundPath) { sprites(f, halo, extra, tint, 0.5f) }
                clipPath(bandPath) { sprites(f, halo, extra, tint, 1f) }
            }
        }
        drawLayer(glowLayer)
    }

    private fun DrawScope.sprites(f: GlowFrame, halo: GlowSprite, extra: GlowSprite, tint: ColorFilter, mul: Float) {
        if (f.haloOp > 0.002f) {
            translate(f.x * d, f.y * d) {
                rotateRad(f.tangent, Offset.Zero) {
                    drawImage(
                        halo.image.bitmap,
                        dstOffset = IntOffset((-halo.ax * d).roundToInt(), (-halo.ay * d).roundToInt()),
                        dstSize = IntSize((halo.w * d).roundToInt(), (halo.h * d).roundToInt()),
                        alpha = (f.haloOp * mul).coerceIn(0f, 1f),
                        colorFilter = tint,
                    )
                }
            }
        }
        if (f.extraOp > 0.002f) {
            translate(f.ex * d, f.ey * d) {
                rotateRad(f.tangent, Offset.Zero) {
                    drawImage(
                        extra.image.bitmap,
                        dstOffset = IntOffset((-extra.ax * d).roundToInt(), (-extra.ay * d).roundToInt()),
                        dstSize = IntSize((extra.w * d).roundToInt(), (extra.h * d).roundToInt()),
                        alpha = (f.extraOp * mul).coerceIn(0f, 1f),
                    )
                }
            }
        }
    }
}
