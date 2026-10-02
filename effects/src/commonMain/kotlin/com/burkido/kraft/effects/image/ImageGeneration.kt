package com.burkido.kraft.effects.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import com.burkido.kraft.effects.core.isDark
import com.burkido.kraft.effects.core.parseCssColor
import com.burkido.kraft.effects.core.rememberEffectActivity
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The imperative handle of an [ImageGeneration] (`ImageGenerationHandle`): run reveal / hide /
 * regenerate passes from buttons, and follow the scheduler's [phase] to label them.
 */
@Stable
class ImageGenerationState internal constructor() {
    internal var cycle by mutableStateOf<ImageCycle?>(null)

    /** The card's engine, for tests that step reveals at fixed times. */
    internal var controller: ImageController? = null
    internal var regenerate: ((durationMillis: Int, tintFromImage: Boolean, autoReveal: Boolean) -> Unit)? = null

    /** The scheduler's current phase. */
    val phase: ImagePhase get() = cycle?.phase ?: ImagePhase.Idle

    /** An image is showing over the effect (revealing, visible or fading out). */
    val isImageActive: Boolean
        get() = phase == ImagePhase.Reveal || phase == ImagePhase.Visible || phase == ImagePhase.Hide

    /**
     * Reveals the next image from the pool now; no-op while one is already showing. With
     * [holdUntilHidden] the image stays until [triggerHide] — for Reveal / Hide toggles.
     */
    fun triggerReveal(holdUntilHidden: Boolean = false) {
        cycle?.triggerOnce(manual = holdUntilHidden)
    }

    /** Fades a revealed image back to the effect. */
    fun triggerHide() {
        cycle?.triggerHide()
    }

    /**
     * Regenerates the shown image in place: it breaks into the effect's cells, which churn while
     * the effect plays through the gaps (recoloured from the image when [tintFromImage]); after
     * [durationMillis] the next image resolves in over the churn and stays, unless [autoReveal]
     * is false. No-op unless an image is revealed.
     */
    fun triggerRegenerate(durationMillis: Int = 4000, tintFromImage: Boolean = true, autoReveal: Boolean = true) {
        regenerate?.invoke(durationMillis, tintFromImage, autoReveal)
    }
}

@Composable
fun rememberImageGenerationState(): ImageGenerationState = remember { ImageGenerationState() }

/**
 * img-fx's `<ImageGeneration>`: an image-generation loader for the card in [content] — a
 * churning pixel mosaic (one of three [preset]s) that periodically resolves into a real image from
 * [images], cell by cell. Rendered on the CPU at the web's 10 fps and 1.25× pixel density, off
 * the main thread, and shown crisply upscaled.
 *
 * @param strength 0..2: up to 1 it fades the mosaic, above 1 it boosts its intensity.
 * @param speed multiplies the preset's tempo.
 * @param pixelScale multiplies the cell size (0.5 finer, 2 chunkier).
 * @param cardBg the card surface, which the effect reasons against; defaults to the preset's.
 * @param colors per-slot palette overrides (up to 7; null keeps the preset's slot).
 * @param autoReveal runs the reveal loop: a random pause of [revealDelaySeconds], reveal, hold
 *   for [revealHoldMillis], fade out over [revealFadeOutMillis].
 * @param state triggers passes by hand and exposes the current phase.
 */
@Composable
fun ImageGeneration(
    modifier: Modifier = Modifier,
    preset: ImagePreset = ImagePreset.PixelsOrganic,
    theme: EffectTheme = EffectTheme.Auto,
    strength: Float = 1f,
    speed: Float = 1f,
    pixelScale: Float = 1f,
    cardBg: Color? = null,
    colors: List<Color?>? = null,
    images: List<ImageBitmap> = emptyList(),
    autoReveal: Boolean = false,
    revealDelaySeconds: ClosedFloatingPointRange<Float> = 2f..4f,
    revealInitialDelaySeconds: ClosedFloatingPointRange<Float>? = null,
    revealHoldMillis: IntRange = 2000..2000,
    revealFadeOutMillis: Int = 300,
    cornerRadius: Dp = 20.dp,
    paused: Boolean = false,
    state: ImageGenerationState = rememberImageGenerationState(),
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = theme.isDark()
    val frozen = LocalEffectFrozenTime.current
    val activity = rememberEffectActivity()
    val scope = rememberCoroutineScope()
    val controller = remember { ImageController() }
    val clock = remember { longArrayOf(0L) }
    val cycle = remember { ImageCycle(scope, controller) { clock[0] / 1e6 } }

    // The regenerate churn's transient recolour and pixel preset, cleared once the next image shows.
    var regenTint by remember { mutableStateOf<SampledPalette?>(null) }
    var regenPreset by remember { mutableStateOf<ImagePreset?>(null) }
    val mode = imageMode(regenPreset ?: preset, dark)
    val effColors = regenTint?.colors ?: colors
    val effBg = regenTint?.cardBg ?: cardBg
    val surface = effBg ?: parseCssColor(mode.cardBg)
    SideEffect {
        controller.configure(mode, strength.coerceIn(0f, 2f), if (pixelScale > 0f) pixelScale else 1f, effColors?.take(7), effBg)
        controller.speedMul = if (speed > 0f) speed else 1f
    }

    val pool = remember(images) { images.map(RevealImages::of) }
    val initialDelay = remember { revealInitialDelaySeconds?.let { (it.start + Random.nextFloat() * (it.endInclusive - it.start)) * 1000 } }
    SideEffect {
        if (cycle.images !== pool) cycle.images = pool
        cycle.delayRange = revealDelaySeconds
        cycle.holdRange = revealHoldMillis
        cycle.fadeOutMs = revealFadeOutMillis
        cycle.initialDelayMs = initialDelay?.toLong()
        cycle.onPhase = { phase ->
            if (phase == ImagePhase.Visible) {
                regenTint = null
                regenPreset = null
            }
        }
    }
    val currentPreset by rememberUpdatedState(preset)
    val currentPaused by rememberUpdatedState(paused)
    SideEffect {
        state.cycle = cycle
        state.controller = controller
        state.regenerate = regenerate@{ durationMillis, tintFromImage, autoRevealAfter ->
            if (currentPaused) return@regenerate
            val shown = controller.image ?: return@regenerate
            if (cycle.phase != ImagePhase.Reveal && cycle.phase != ImagePhase.Visible) return@regenerate
            // The churn always runs on a pixel-mosaic preset.
            val churn = if (currentPreset == ImagePreset.SweepGradient) {
                if (Random.nextBoolean()) ImagePreset.PixelsMechanic else ImagePreset.PixelsOrganic
            } else {
                null
            }
            if (tintFromImage && controller.cssW > 0) {
                val slots = imageMode(churn ?: currentPreset, dark).colors.map(::parseCssColor)
                regenTint = shown.samplePalette(controller.cssW.toFloat(), controller.cssH.toFloat(), slots)
            }
            if (churn != null) regenPreset = churn
            cycle.triggerBoil(if (autoRevealAfter) durationMillis.toLong() else null)
        }
    }

    LaunchedEffect(autoReveal) {
        if (autoReveal) cycle.start() else cycle.stop()
    }
    LaunchedEffect(paused) { cycle.setPaused(paused) }
    DisposableEffect(Unit) { onDispose { cycle.dispose() } }

    val animating = frozen == null && activity.isActive && !paused
    val wake = controller.wakes
    if (frozen != null) {
        LaunchedEffect(frozen, wake) { controller.renderNow(frozen) }
    } else if (activity.isActive) {
        LaunchedEffect(animating, wake) {
            while (true) {
                withFrameNanos { now ->
                    clock[0] = now
                    controller.frame(now, animating)
                }
                if (!animating && controller.idle) break
            }
        }
    }

    val density = LocalDensity.current.density
    val opacity = strength.coerceIn(0f, 1f)
    Box(
        modifier
            .then(activity.modifier)
            .onSizeChanged { controller.resize((it.width / density).roundToInt(), (it.height / density).roundToInt(), density) }
            .clip(RoundedCornerShape(cornerRadius))
            .background(surface)
            .drawWithCache {
                val overlayLayer = obtainGraphicsLayer()
                onDrawWithContent {
                    drawContent()
                    val plan = controller.plan
                    val w = size.width.roundToInt()
                    val h = size.height.roundToInt()
                    val mosaic = controller.mosaic
                    val shaderAlpha = plan.shaderAlpha * opacity
                    if (mosaic != null && shaderAlpha > 0f) {
                        drawImage(
                            mosaic.image,
                            srcSize = IntSize(mosaic.width, mosaic.height),
                            dstSize = IntSize(w, h),
                            alpha = shaderAlpha,
                            filterQuality = FilterQuality.None,
                        )
                    }
                    if (plan.overlay != OverlayKind.None && plan.overlayAlpha > 0f && w > 0 && h > 0) {
                        val chunky = controller.chunky
                        val mask = controller.mask
                        overlayLayer.alpha = plan.overlayAlpha
                        overlayLayer.record(IntSize(w, h)) { drawOverlay(plan, chunky, mask, w, h) }
                        drawLayer(overlayLayer)
                    }
                }
            },
        content = content,
    )
}

/**
 * The reveal overlay: the smooth cover-fit image, the chunky cells over it (nearest-neighbour),
 * and during a reveal the quantised mask cutting everything to the revealed cells.
 */
private fun DrawScope.drawOverlay(
    plan: DrawPlan,
    chunky: com.burkido.kraft.effects.core.PixelSurface?,
    mask: com.burkido.kraft.effects.core.PixelSurface?,
    w: Int,
    h: Int,
) {
    val img = plan.image
    if (img != null && plan.imageAlpha > 0f) {
        val r = img.coverRect(w.toFloat(), h.toFloat())
        drawImage(
            img.bitmap,
            srcOffset = IntOffset(r[0].roundToInt(), r[1].roundToInt()),
            srcSize = IntSize(r[2].roundToInt(), r[3].roundToInt()),
            dstSize = IntSize(w, h),
            alpha = plan.imageAlpha,
            filterQuality = FilterQuality.High,
        )
    }
    if (plan.overlay == OverlayKind.Reveal || plan.overlay == OverlayKind.Boil) {
        if (chunky != null) {
            drawImage(chunky.image, srcSize = IntSize(chunky.width, chunky.height), dstSize = IntSize(w, h), filterQuality = FilterQuality.None)
        }
        if (plan.overlay == OverlayKind.Reveal && mask != null) {
            drawImage(
                mask.image,
                srcSize = IntSize(mask.width, mask.height),
                dstSize = IntSize(w, h),
                blendMode = BlendMode.DstIn,
                filterQuality = FilterQuality.None,
            )
        }
    }
}
