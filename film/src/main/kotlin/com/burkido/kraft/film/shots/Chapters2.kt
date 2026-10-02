package com.burkido.kraft.film.shots

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.avatars.BotAvatar
import com.burkido.kraft.effects.avatars.BotAvatarState
import com.burkido.kraft.effects.avatars.BotAvatarType
import com.burkido.kraft.effects.avatars.botAvatarPointer
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.gooey.Item
import com.burkido.kraft.effects.gooey.Liquid
import com.burkido.kraft.effects.gooey.LiquidTransition
import com.burkido.kraft.effects.gooey.MoveItem
import com.burkido.kraft.effects.gooey.MoveTuning
import com.burkido.kraft.effects.image.ImageGeneration
import com.burkido.kraft.effects.image.ImagePreset
import com.burkido.kraft.effects.image.rememberImageGenerationState
import com.burkido.kraft.effects.voice.VoiceBeam
import com.burkido.kraft.effects.voice.VoiceBeamType
import com.burkido.kraft.film.Backdrop
import com.burkido.kraft.film.Camera
import com.burkido.kraft.film.Chapter
import com.burkido.kraft.film.Ease
import com.burkido.kraft.film.FadeIn
import com.burkido.kraft.film.FilmColors
import com.burkido.kraft.film.FilmIcons
import com.burkido.kraft.film.Icon
import com.burkido.kraft.film.LocalFilmClock
import com.burkido.kraft.film.Shot
import com.burkido.kraft.film.Touch
import com.burkido.kraft.film.drift
import com.burkido.kraft.film.lerp
import com.burkido.kraft.film.loadBitmap
import com.burkido.kraft.film.mono
import com.burkido.kraft.film.ramp
import com.burkido.kraft.film.sans
import com.burkido.kraft.film.shotTime
import com.burkido.kraft.film.surface
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sin

val Assets = File("film/assets")

// ── 04 Gooey: the plus menu splitting into droplets, and a liquid slider ────────────────────

private const val GooeyScale = 1.75f
private const val SliderLeft = 472f // local stage x of the slider box
private const val SliderTop = 216f

private val GooFill = Color(0xFF3A3A40)

/** Slider thumb target (0..188 dp along the track) over shot time. */
private fun thumbX(t: Double): Float {
    val a = ramp(t, 1.5, 0.9, Ease::cubicInOut)
    val b = ramp(t, 2.6, 0.55, Ease::cubicInOut)
    val c = ramp(t, 3.35, 0.8, Ease::expoOut)
    return 20f + a * 150f - b * 110f + c * 70f
}

/** The gooey camera: scale and vertical offset (dp). */
private fun gooeyCam(t: Double): Pair<Float, Float> =
    (GooeyScale + ramp(t, 0.0, ChapterSeconds, Ease::sineInOut) * 0.06f) to (drift(t, 1.5f, 6.0) - 20f)

private fun gooeyTouch(t: Double): Touch? {
    if (t < 1.1 || t > 4.6) return null
    val lx = SliderLeft + 14 + thumbX(t) + 12
    val ly = SliderTop + 42
    val (s, dy) = gooeyCam(t)
    val x = 480 + (lx - 480) * s
    val y = 270 + (ly - 270) * s + dy
    val alpha = ramp(t, 1.1, 0.25) * (1 - ramp(t, 4.3, 0.3))
    return Touch(x, y, down = t in 1.35..4.2, alpha = alpha)
}

val Gooey = Shot("gooey", ChapterSeconds, touch = ::gooeyTouch) {
    val t = shotTime()
    Backdrop(light = 0.9f)
    val (camScale, camY) = gooeyCam(t)
    Camera(scale = camScale, y = camY) {
        Box(Modifier.fillMaxSize()) {
            // The plus menu: open on the beat, fold back later.
            val open = t in 0.55..3.05
            Box(Modifier.offset(262.dp, 156.dp)) {
                Liquid(Modifier.size(200.dp, 140.dp), blur = 6.dp, contrast = 18f, fill = GooFill) {
                    val sats = listOf(Triple(-54, -34, FilmIcons.File), Triple(0, -64, FilmIcons.Image), Triple(54, -34, FilmIcons.Folder))
                    sats.forEachIndexed { i, (x, y, icon) ->
                        Item(
                            x = if (open) x.dp else 0.dp,
                            y = if (open) y.dp else 0.dp,
                            radius = 20.dp,
                            modifier = Modifier.offset(80.dp, 80.dp),
                            transition = if (open) LiquidTransition.Bouncy else LiquidTransition.Snappy,
                            delayMillis = if (open) i * 40 else 0,
                        ) {
                            val shown by animateFloatAsState(if (open) 1f else 0f, tween(if (open) 220 else 120, delayMillis = if (open) 140 + i * 40 else 0))
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Icon(icon, Color(0xFFEDEDED), 16.dp, Modifier.graphicsLayer {
                                    alpha = shown
                                    val b = (1 - shown) * 3f
                                    if (b > 0.05f) renderEffect = BlurEffect(b * density, b * density, TileMode.Decal)
                                })
                            }
                        }
                    }
                    Item(0.dp, 0.dp, radius = 20.dp, modifier = Modifier.offset(80.dp, 80.dp)) {
                        val rot by animateFloatAsState(if (open) 45f else 0f, tween(250))
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(FilmIcons.Plus, Color(0xFFEDEDED), 18.dp, Modifier.graphicsLayer { rotationZ = rot })
                        }
                    }
                }
            }
            // The liquid slider, dragged by the hand.
            Box(Modifier.offset(SliderLeft.dp, SliderTop.dp).size(240.dp, 80.dp)) {
                Box(Modifier.offset(14.dp, 38.dp).size(212.dp, 8.dp).surface(4.dp, Color(0xFF737373).copy(alpha = 0.28f), Color.Transparent))
                Liquid(Modifier.size(240.dp, 80.dp), blur = 6.dp, contrast = 18f, fill = Color(0xFF8C8C94)) {
                    MoveItem(
                        radius = 12.dp,
                        modifier = Modifier.offset((14 + thumbX(t)).dp, 30.dp).size(24.dp),
                        tuning = MoveTuning(springiness = 0.5f, wobble = 0.5f, stretch = 0.6f, trail = 0.35f),
                    ) { Box(Modifier.size(24.dp)) }
                }
            }
        }
    }
    Chapter("04", "Gooey", "Surfaces that split, merge and stretch like liquid.", start = 0.3, end = ChapterSeconds)
}

// ── 05 Voice glow: a phone that listens, driven by a real voice ─────────────────────────────

const val VoiceStart = 0.55

/** A take of the voice line: `assets/voice_<name>.wav`, its loudness envelope, and its word onsets (s after the clip starts). */
class VoiceTake(val name: String, val wordAt: List<Double>)

val VoiceTakes = listOf(
    // v1, v2: female ("sage"). Onsets from Whisper word timestamps.
    VoiceTake("sage", listOf(0.05, 1.08, 1.42, 1.64, 2.08)),
    // v3: male ("onyx"). Onsets from Whisper, refined on the loudness envelope.
    VoiceTake("onyx", listOf(0.10, 1.14, 1.60, 1.72, 2.12)),
)

/** The take the film is rendered with; `--voice` in Main. */
var FilmVoice: VoiceTake = VoiceTakes.last()

private val voiceLevels: FloatArray by lazy { File(Assets, "voice_${FilmVoice.name}_level.txt").readLines().map { it.toFloat() }.toFloatArray() }
private const val VoiceRate = 120.0
private val Words = listOf("Hey…", "show", "me", "something", "beautiful.")
private val WordAt get() = FilmVoice.wordAt

private fun voiceLevel(t: Double): Float {
    val i = ((t - VoiceStart) * VoiceRate).roundToInt()
    return if (i in voiceLevels.indices) voiceLevels[i] else 0f
}

private const val PhoneW = 236f
private const val PhoneH = 486f

val Voice = Shot("voice", ChapterSeconds) {
    val t = shotTime()
    val clock = LocalFilmClock.current
    Backdrop(light = 0.8f)
    val speechEnd = VoiceStart + WordAt.last() + 0.75
    Camera(
        scale = lerp(1.04f, 0.98f, ramp(t, 0.0, ChapterSeconds, Ease::sineInOut)),
        rotY = lerp(-10f, 6f, ramp(t, 0.0, ChapterSeconds, Ease::sineInOut)),
        x = 150f,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FadeIn(0.0, dur = 0.7, blur = 10f, scaleFrom = 0.97f) {
                VoiceBeam(
                    modifier = Modifier.size(PhoneW.dp, PhoneH.dp),
                    type = VoiceBeamType.Mobile,
                    level = { voiceLevel(clock.seconds) },
                    processing = t > speechEnd,
                    theme = EffectTheme.Dark,
                    scale = 1.25 * PhoneW / 402,
                    borderRadius = 40.dp,
                    background = {
                        Box(Modifier.fillMaxSize().surface(40.dp, Color(0xFF151517), Color.White.copy(alpha = 0.09f)))
                    },
                ) {
                    // Dynamic-island-ish notch and the transcript.
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(76.dp, 22.dp).surface(11.dp, Color.Black, Color.Transparent))
                        Column(Modifier.padding(start = 22.dp, end = 16.dp, top = 150.dp)) {
                            val listening = t < VoiceStart + 0.1
                            BasicText(
                                if (t > speechEnd + 0.2) "Thinking…" else if (listening) "How can I help?" else "Listening…",
                                style = mono(11f, FilmColors.Muted),
                            )
                            Spacer(Modifier.height(10.dp))
                            // "Hey…" / "show me something" / "beautiful."
                            for (line in listOf(0..0, 1..3, 4..4)) {
                                Row {
                                    for (i in line) {
                                        val p = ramp(t, VoiceStart + WordAt[i] - 0.05, 0.45, Ease::expoOut)
                                        BasicText(
                                            if (i != line.last) "${Words[i]} " else Words[i],
                                            style = sans(19f, FilmColors.Text, FontWeight.Medium),
                                            softWrap = false,
                                            modifier = Modifier.graphicsLayer {
                                                alpha = p
                                                translationY = (1 - p) * 6.dp.toPx()
                                                val b = (1 - p) * 6f
                                                if (b > 0.05f) renderEffect = BlurEffect(b * density, b * density, TileMode.Decal)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp).fillMaxWidth().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.height(34.dp).surface(17.dp, Color.White.copy(alpha = 0.08f), Color.Transparent).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                BasicText("Agent (auto)", style = sans(12f, Color(0xFFCACCD2), FontWeight.Medium))
                            }
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(34.dp).surface(17.dp, Color.White.copy(alpha = 0.08f), Color.Transparent), contentAlignment = Alignment.Center) {
                                Icon(FilmIcons.Mic, Color(0xFFEFEFEF), 16.dp)
                            }
                        }
                    }
                }
            }
        }
    }
    Chapter("05", "Voice glow", "A glow that listens, driven by any audio level.", start = 0.3, end = ChapterSeconds)
}

// ── 06 Bot avatars: a lineup whose eyes follow the hand ────────────────────────────────────

private val Lineup = listOf(
    BotAvatarType.Clover, BotAvatarType.Ghost, BotAvatarType.Mech, BotAvatarType.Star, BotAvatarType.Square, BotAvatarType.Cat,
)

private fun botsTouch(t: Double): Touch? {
    if (t < 0.6) return null
    // A lazy sweep left to right above the row, then a dip down to the middle.
    val u = ramp(t, 0.6, 3.2, Ease::sineInOut)
    val x = lerp(90f, 880f, u)
    val dip = ramp(t, 3.8, 0.9, Ease::cubicInOut)
    val y = 150f + sin(u * Math.PI * 2).toFloat() * 36f + dip * 190f
    return Touch(lerp(x, 520f, dip), y, alpha = ramp(t, 0.6, 0.3))
}

val Bots = Shot("bots", ChapterSeconds, touch = ::botsTouch) {
    val t = shotTime()
    Backdrop(light = 0.9f)
    Camera(scale = lerp(1.06f, 1.0f, ramp(t, 0.0, ChapterSeconds, Ease::sineInOut))) {
        Box(Modifier.fillMaxSize().botAvatarPointer()) {
            Row(
                Modifier.align(Alignment.Center).padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Lineup.forEachIndexed { i, type ->
                    val state = when {
                        type == BotAvatarType.Ghost && t < 3.3 -> BotAvatarState.Sleeping
                        type == BotAvatarType.Mech && t in 2.4..4.2 -> BotAvatarState.Working
                        else -> BotAvatarState.Default
                    }
                    FadeIn(0.05 + i * 0.07, dur = 0.8, blur = 10f, rise = 18.dp, scaleFrom = 0.9f) {
                        BotAvatar(type = type, state = state, size = 112.dp, seed = 0.13 + i * 0.11)
                    }
                }
            }
        }
    }
    Chapter("06", "Bot avatars", "Characters that notice you, sleep and get to work.", start = 0.3, end = ChapterSeconds)
}

// ── 07 Image generation: three prompts resolving out of pixels ─────────────────────────────

private class GenCard(val file: String, val prompt: String, val preset: ImagePreset, val revealAt: Double)

private val Cards = listOf(
    GenCard("gen_lighthouse.jpg", "Lighthouse at blue hour", ImagePreset.PixelsOrganic, 1.9),
    GenCard("gen_dunes.jpg", "Dunes at first light", ImagePreset.PixelsMechanic, 2.35),
    GenCard("gen_glass.jpg", "Glass, refracted", ImagePreset.SweepGradient, 2.8),
)

val Image = Shot("image", ChapterSeconds, preroll = 1.0) {
    val t = shotTime()
    Backdrop(light = 0.9f)
    Camera(scale = lerp(1.1f, 1.0f, ramp(t, 0.0, ChapterSeconds, Ease::cubicOut)), y = -40f) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Cards.forEachIndexed { i, card ->
                val bmp = remember { loadBitmap(File(Assets, card.file)) }
                val state = rememberImageGenerationState()
                val reveal = t >= card.revealAt
                LaunchedEffect(reveal) { if (reveal) state.triggerReveal(holdUntilHidden = true) }
                FadeIn(0.05 + i * 0.08, dur = 0.8, blur = 10f, rise = 14.dp) {
                    Column {
                        ImageGeneration(
                            modifier = Modifier.size(248.dp),
                            preset = card.preset,
                            theme = EffectTheme.Dark,
                            images = listOf(bmp),
                            autoReveal = false,
                            cornerRadius = 20.dp,
                            cardBg = Color(0xFF141416),
                            state = state,
                        ) {}
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(FilmIcons.Sparkle, FilmColors.Faint, 12.dp)
                            BasicText(card.prompt, style = sans(13f, FilmColors.Muted))
                        }
                    }
                }
            }
        }
    }
    Chapter("07", "Image generation", "The moment an image comes to life.", start = 0.3, end = ChapterSeconds)
}
