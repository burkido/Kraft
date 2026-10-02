package com.burkido.kraft.landing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.effects.avatars.BotAvatar
import com.burkido.kraft.effects.avatars.BotAvatarType
import com.burkido.kraft.effects.beam.BeamSize
import com.burkido.kraft.effects.beam.BorderBeam
import com.burkido.kraft.effects.core.EffectActivity
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import com.burkido.kraft.effects.core.rememberEffectActivity
import com.burkido.kraft.effects.gooey.Item
import com.burkido.kraft.effects.gooey.Liquid
import com.burkido.kraft.effects.gooey.LiquidTransition
import com.burkido.kraft.effects.image.ImageGeneration
import com.burkido.kraft.effects.image.ImagePreset
import com.burkido.kraft.effects.metal.MetalFx
import com.burkido.kraft.effects.metal.MetalVariant
import com.burkido.kraft.effects.orbs.OrbState
import com.burkido.kraft.effects.orbs.ThinkingOrb
import com.burkido.kraft.effects.voice.VoiceBeam
import com.burkido.kraft.effects.voice.VoiceBeamType
import com.burkido.kraft.effects.voice.demoVoiceLevel
import com.burkido.kraft.library.GooeyMenuScene
import com.burkido.kraft.library.LibraryId
import com.burkido.kraft.library.LiquidShadowDark
import com.burkido.kraft.library.MetalCircleScene
import com.burkido.kraft.library.RecordingPillForeground
import com.burkido.kraft.library.RecordingPillSurface
import com.burkido.kraft.library.rememberGenImages
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The live preview a hero tile ([compact]) or library card shows — the scene the site plays as a
 * looping video, rendered by the real effect instead. While not [playing] it holds its frame, as
 * the site's clip does when paused.
 */
@Composable
fun BoxScope.LibraryScene(library: LibraryId, width: Dp, height: Dp, compact: Boolean, playing: Boolean = true) {
    when (library) {
        LibraryId.Orbs -> if (compact) OrbTileScene(width, playing) else OrbCardScene(playing)
        LibraryId.Beam -> if (compact) BeamTileScene(width, playing) else BeamCardScene(playing)
        LibraryId.Gooey -> if (compact) GooeyTileScene(width, playing) else ClipFrame(width, height, WideClip, 296.dp) {
            // The menu's 200 × 140 box sits 4 dp low, so the button rests at 63 % of the frame.
            Box(Modifier.offset(y = 4.dp)) { GooeyMenuScene(playing) }
        }
        LibraryId.Metal -> if (compact) MetalTileScene(width, playing) else MetalCardScene(width, height, playing)
        LibraryId.Image -> if (compact) ImageTileScene(width, playing) else ImageCardScene(width, height, playing)
        LibraryId.Voice -> VoiceCardScene(width, height, playing)
        LibraryId.Bots -> BotsCardScene(width, height, playing)
    }
}

// ── Clip framing ──────────────────────────────────────────────────────────────────────────

/** The card clips' frame, 2516 × 2160. */
private const val WideClip = 2516f / 2160f

/** The width a clip of [aspect] takes when it covers [width] × [height] (`object-fit: cover`). */
private fun coverWidth(width: Dp, height: Dp, aspect: Float): Dp = maxOf(width, height * aspect)

/**
 * A clip's frame, laid out at [design] wide and scaled to cover the stage like the site's
 * `object-fit: cover` video, centred and cropped by the stage.
 */
@Composable
private fun BoxScope.ClipFrame(width: Dp, height: Dp, aspect: Float, design: Dp, content: @Composable BoxScope.() -> Unit) {
    val scale = coverWidth(width, height, aspect) / design
    Box(
        Modifier
            .align(Alignment.Center)
            .requiredSize(design, design / aspect)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Toggles a flag on a looping timeline while [playing] and on screen (through [activity]'s
 * modifier): on after [onAt], off after [offAt], every [period] (ms).
 */
@Composable
private fun rememberLoopFlag(activity: EffectActivity, playing: Boolean, onAt: Long, offAt: Long, period: Long): Boolean {
    var on by remember { mutableStateOf(false) }
    val run = playing && activity.isActive && LocalEffectFrozenTime.current == null
    LaunchedEffect(run) {
        if (!run) return@LaunchedEffect
        while (true) {
            delay(onAt)
            on = true
            delay(offAt - onAt)
            on = false
            delay(period - offAt)
        }
    }
    return on
}

// ── Orbs ──────────────────────────────────────────────────────────────────────────────────

/** The orb tile plays one of three clips, picked once per mount: globe, ribbon or orbits. */
@Composable
private fun OrbTileScene(stage: Dp, playing: Boolean) {
    val state = remember { listOf(OrbState.Searching, OrbState.Composing, OrbState.Working)[Random.nextInt(3)] }
    ThinkingOrb(state, displaySize = stage * 0.46f, paused = !playing)
}

/** "Agent listening…" chip over two status pills, each led by an orb. */
@Composable
private fun OrbCardScene(playing: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StatusPill(OrbState.Listening, "Agent listening…", orb = 16.dp, height = 28.dp, textSize = 11f, playing)
        StatusPill(OrbState.Solving, "Solving…", orb = 36.dp, height = 52.dp, textSize = 17f, playing)
        StatusPill(OrbState.Breathing, "Thinking…", orb = 36.dp, height = 52.dp, textSize = 17f, playing)
    }
}

@Composable
private fun StatusPill(state: OrbState, label: String, orb: Dp, height: Dp, textSize: Float, playing: Boolean) {
    val fonts = LocalKraftFonts.current
    Row(
        Modifier
            .height(height)
            .cssSurface(height / 2, SceneCardShadows) { SceneCard }
            .padding(start = (height - orb) / 2, end = height / 2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThinkingOrb(state, displaySize = orb, paused = !playing)
        BasicText(label, style = cssText(fonts.sans, textSize, null, FontWeight.Normal, Color(0xFFBDBDBD)))
    }
}

// ── Beam ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BeamTileScene(stage: Dp, playing: Boolean) {
    val card = stage * 0.48f
    BorderBeam(size = BeamSize.Md, borderRadius = card * 0.18f, paused = !playing) {
        Box(Modifier.size(card).cssSurface(card * 0.18f, SceneCardShadows) { SceneCard })
    }
}

/** The composer with an `md` beam, above a search bar with a travelling `line` beam. */
@Composable
private fun BeamCardScene(playing: Boolean) {
    val fonts = LocalKraftFonts.current
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BorderBeam(size = BeamSize.Md, borderRadius = 16.dp, paused = !playing) {
            Box(Modifier.width(250.dp).height(88.dp).cssSurface(16.dp, SceneCardShadows) { SceneCard }) {
                BasicText(
                    "Build anything…",
                    style = cssText(fonts.sans, 12f, null, FontWeight.Normal, Color(0xFF4E4E4E)),
                    modifier = Modifier.padding(start = 14.dp, top = 16.dp),
                )
            }
        }
        BorderBeam(size = BeamSize.Line, borderRadius = 20.dp, duration = 3.1, paused = !playing) {
            Box(Modifier.width(220.dp).height(34.dp).cssSurface(20.dp, SceneCardShadows) { SceneCard }) {
                BasicText(
                    "Search",
                    style = cssText(fonts.sans, 12f, null, FontWeight.Normal, Color(0xFF6B6B6B)),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
                )
            }
        }
    }
}

// ── Gooey ─────────────────────────────────────────────────────────────────────────────────

/**
 * The gooey tile: a drop 26.5 % of the stage wide splits in two (bouncy, to ±22 %) at 0.72 s and
 * snaps back together at 2.8 s, every 3.97 s.
 */
@Composable
private fun GooeyTileScene(stage: Dp, playing: Boolean) {
    val activity = rememberEffectActivity()
    val split = rememberLoopFlag(activity, playing, onAt = 720, offAt = 2800, period = 3970)
    val r = stage * 0.1325f
    val d = stage * 0.2225f
    Liquid(Modifier.requiredSize(stage).then(activity.modifier), fill = GooeyTileFill, shadow = LiquidShadowDark) {
        for (side in listOf(-1, 1)) {
            Item(
                x = if (split) d * side.toFloat() else 0.dp,
                y = 0.dp,
                radius = r,
                modifier = Modifier.offset(stage / 2 - r, stage / 2 - r),
                transition = if (split) LiquidTransition.Bouncy else LiquidTransition.Snappy,
            ) { Box(Modifier.size(r * 2)) }
        }
    }
}

private val GooeyTileFill = Color(0xFF2A2A2A)

// ── Metal ─────────────────────────────────────────────────────────────────────────────────

/** The metal tile: the circle's ring alone, 32 % of the stage, its glow wandering round it. */
@Composable
private fun MetalTileScene(stage: Dp, playing: Boolean) {
    val size = stage * 0.3226f
    MetalFx(
        variant = MetalVariant.Circle,
        strength = 0.81f,
        shaderScale = 1.3f,
        ringWidth = size * 0.05f,
        innerShadow = true,
        paused = !playing,
    ) { Box(Modifier.size(size)) }
}

/**
 * The metal card: the playground's search field and send button at 1.19×, framed so the button
 * sits at 58 % of the width and the field runs off the left edge.
 */
@Composable
private fun BoxScope.MetalCardScene(width: Dp, height: Dp, playing: Boolean) {
    ClipFrame(width, height, 1852f / 2160f, 249.3.dp) {
        Box(Modifier.fillMaxSize().offset(x = (-76.8).dp)) { MetalCircleScene(paused = !playing) }
    }
}

// ── Image ─────────────────────────────────────────────────────────────────────────────────

/**
 * The pixel scale that gives a [card] wide mosaic [cells] cells across, whatever its size: the
 * clips were recorded from larger cards, so their grids are finer than the card's own would be.
 */
private fun pixelScaleFor(card: Dp, cells: Int): Float = card.value * (6f + 0.22f * 74f) / (320f * (cells + 0.5f))

/** The image tile: a mechanic mosaic card 64.5 % of the stage, 13 cells across, loading and never revealing. */
@Composable
private fun ImageTileScene(stage: Dp, playing: Boolean) {
    val card = stage * 0.645f
    ImageGeneration(
        preset = ImagePreset.PixelsMechanic,
        theme = EffectTheme.Dark,
        cardBg = ImageCardBg,
        pixelScale = pixelScaleFor(card, 13),
        cornerRadius = stage * 0.0403f,
        paused = !playing,
    ) { Box(Modifier.size(card)) }
}

/** The image card: an organic mosaic card, 22 cells across, that reveals the demo photos in turn. */
@Composable
private fun BoxScope.ImageCardScene(width: Dp, height: Dp, playing: Boolean) {
    val w = coverWidth(width, height, WideClip)
    val h = w / WideClip
    val pool = rememberGenImages()
    val card = w * 0.7214f
    Box(Modifier.align(Alignment.Center).offset(w * 0.003f, h * 0.006f)) {
        ImageGeneration(
            preset = ImagePreset.PixelsOrganic,
            theme = EffectTheme.Dark,
            cardBg = ImageCardBg,
            pixelScale = pixelScaleFor(card, 22),
            images = pool,
            autoReveal = true,
            cornerRadius = w * 0.0346f,
            paused = !playing,
        ) { Box(Modifier.size(card, h * 0.737f)) }
    }
}

private val ImageCardBg = Color(0xFF1B1B1B)

// ── Voice ─────────────────────────────────────────────────────────────────────────────────

/**
 * The voice card: the recording pill, its glow rising with the demo voice, the clock from 00:12.
 * A paused beam stays dark, so it plays its first 1.2 s regardless and holds a lit frame after.
 */
@Composable
private fun BoxScope.VoiceCardScene(width: Dp, height: Dp, playing: Boolean) {
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200)
        warm = true
    }
    val paused = warm && !playing
    ClipFrame(width, height, WideClip, 296.dp) {
        Box(Modifier.offset(x = 3.5.dp)) {
            VoiceBeam(
                type = VoiceBeamType.Pill,
                level = { t -> demoVoiceLevel(t) },
                paused = paused,
                borderRadius = 22.dp,
                background = { RecordingPillSurface() },
            ) { RecordingPillForeground(running = true, paused = paused, resetKey = 0, startSeconds = 12) }
        }
    }
}

// ── Bots ──────────────────────────────────────────────────────────────────────────────────

/** The bots card: Clover, Flower and Star idling a third of the frame apart. */
@Composable
private fun BoxScope.BotsCardScene(width: Dp, height: Dp, playing: Boolean) {
    val w = coverWidth(width, height, 2488f / 2160f)
    val size = w * 0.207f
    Box(Modifier.align(Alignment.Center)) {
        listOf(BotAvatarType.Clover to -0.3265f, BotAvatarType.Flower to 0f, BotAvatarType.Star to 0.3265f).forEach { (type, x) ->
            BotAvatar(
                Modifier.align(Alignment.Center).offset(x = w * x),
                type = type,
                size = size,
                paused = !playing,
                interactive = false,
            )
        }
    }
}

private val SceneCard = Color(0xFF1A1A1A)
private val SceneCardShadows = listOf(
    BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.06f), inset = true),
)
