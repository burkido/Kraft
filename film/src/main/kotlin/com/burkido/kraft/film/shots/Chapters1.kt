package com.burkido.kraft.film.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.beam.BeamColorVariant
import com.burkido.kraft.effects.beam.BeamSize
import com.burkido.kraft.effects.beam.BorderBeam
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.metal.MetalBadge
import com.burkido.kraft.effects.metal.MetalFx
import com.burkido.kraft.effects.metal.MetalReflection
import com.burkido.kraft.effects.metal.MetalText
import com.burkido.kraft.effects.metal.MetalVariant
import com.burkido.kraft.effects.metal.metalBendArea
import com.burkido.kraft.effects.metal.rememberMetalAnchor
import com.burkido.kraft.effects.metal.rememberMetalBend
import com.burkido.kraft.effects.orbs.OrbSize
import com.burkido.kraft.effects.orbs.OrbState
import com.burkido.kraft.effects.orbs.ThinkingOrb
import com.burkido.kraft.film.Backdrop
import com.burkido.kraft.film.Camera
import com.burkido.kraft.film.Chapter
import com.burkido.kraft.film.Ease
import com.burkido.kraft.film.FadeIn
import com.burkido.kraft.film.FilmColors
import com.burkido.kraft.film.FilmIcons
import com.burkido.kraft.film.Icon
import com.burkido.kraft.film.IconChip
import com.burkido.kraft.film.Shot
import com.burkido.kraft.film.Touch
import com.burkido.kraft.film.drift
import com.burkido.kraft.film.lerp
import com.burkido.kraft.film.mono
import com.burkido.kraft.film.ramp
import com.burkido.kraft.film.sans
import com.burkido.kraft.film.shotTime
import com.burkido.kraft.film.surface
import kotlin.math.abs
import kotlin.math.roundToInt

const val ChapterSeconds = 5.0

// ── 01 Thinking orbs: a slow dolly along all nine states, one in focus ───────────────────────

private val OrbLineup = listOf(
    OrbState.Breathing, OrbState.Working, OrbState.Searching, OrbState.Solving, OrbState.Listening,
    OrbState.Connecting, OrbState.Weaving, OrbState.Composing, OrbState.Shaping,
)

val Orbs = Shot("orbs", ChapterSeconds) {
    val t = shotTime()
    Backdrop(light = 0.9f)
    val focus = lerp(1.35f, 4.65f, ramp(t, 0.0, ChapterSeconds, Ease::sineInOut))
    val spacing = 250f
    Box(Modifier.fillMaxSize()) {
        OrbLineup.forEachIndexed { i, state ->
            val d = i - focus
            val ad = abs(d)
            if (ad > 3.2f) return@forEachIndexed
            val scale = lerp(1f, 0.62f, (ad / 1.6f).coerceIn(0f, 1f))
            val blur = (ad - 0.25f).coerceAtLeast(0f) * 5f
            val appear = ramp(t, 0.05 + i * 0.05, 1.0, Ease::cubicOut)
            val alpha = (1f - (ad - 0.6f).coerceAtLeast(0f) * 0.38f).coerceIn(0f, 1f) * appear
            Box(
                Modifier
                    .offset(x = (480 + d * spacing - 105).dp, y = (224 - 105).dp)
                    .requiredSize(210.dp)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        this.alpha = alpha
                        if (blur > 0.1f) renderEffect = BlurEffect(blur * density, blur * density, TileMode.Decal)
                    },
                contentAlignment = Alignment.Center,
            ) {
                ThinkingOrb(state, size = OrbSize.S64, theme = EffectTheme.Dark, displaySize = 196.dp)
            }
            // The state name under the orb in focus.
            val label = (1f - (ad / 0.45f)).coerceIn(0f, 1f) * appear
            if (label > 0f) {
                Box(Modifier.offset(x = (480 + d * spacing - 100).dp, y = 340.dp).width(200.dp), contentAlignment = Alignment.Center) {
                    BasicText(state.label, style = mono(12f, FilmColors.Muted), modifier = Modifier.graphicsLayer { this.alpha = label })
                }
            }
        }
    }
    Chapter("01", "Thinking orbs", "Nine states for an AI that is working on it.", start = 0.3, end = ChapterSeconds)
}

// ── 02 Border beam: a chat composer with a living border, tilted in 3D ─────────────────────

private const val Prompt = "Summarize the design review and draft next steps"

@Composable
private fun Composer(t: Double) {
    val typed = ((t - 0.7) / 1.9).coerceIn(0.0, 1.0)
    val n = (Prompt.length * typed).roundToInt()
    val caretOn = ((t * 2.2).toInt() % 2 == 0) || typed in 0.01..0.99
    Column(Modifier.size(520.dp, 132.dp).padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n == 0) {
                BasicText("Ask Kraft anything…", style = sans(16f, FilmColors.Faint))
            } else {
                BasicText(Prompt.take(n), style = sans(16f, FilmColors.Text))
            }
            Box(Modifier.padding(start = 2.dp).size(1.5.dp, 19.dp).background(FilmColors.Accent.copy(alpha = if (caretOn) 0.9f else 0f)))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconChip(FilmIcons.Plus)
            IconChip(FilmIcons.Globe)
            Spacer(Modifier.weight(1f))
            BasicText("Auto", style = sans(13f, FilmColors.Muted, FontWeight.Medium), modifier = Modifier.padding(end = 6.dp))
            val ready = n == Prompt.length
            Box(
                Modifier.size(34.dp).surface(17.dp, if (ready) Color.White else Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(FilmIcons.ArrowUp, if (ready) Color.Black else FilmColors.Muted, 16.dp) }
        }
    }
}

@Composable
private fun StatusPill(text: String, variant: BeamColorVariant, start: Double) {
    FadeIn(start, dur = 0.9, blur = 8f, rise = 10.dp) {
        BorderBeam(size = BeamSize.Sm, colorVariant = variant, theme = EffectTheme.Dark, borderRadius = 18.dp) {
            Row(
                Modifier.height(36.dp).surface(18.dp, FilmColors.Card).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(FilmIcons.Sparkle, FilmColors.Muted, 14.dp)
                BasicText(text, style = sans(13f, FilmColors.Text.copy(alpha = 0.85f), FontWeight.Medium))
            }
        }
    }
}

val Beam = Shot("beam", ChapterSeconds) {
    val t = shotTime()
    Backdrop(light = 1f)
    val p = ramp(t, 0.0, ChapterSeconds, Ease::sineInOut)
    Camera(
        scale = lerp(1.16f, 1.0f, p),
        rotX = lerp(16f, 5f, p),
        rotY = lerp(-12f, 3f, p),
        y = lerp(18f, -8f, p) + drift(t, 2f, 7.0),
    ) {
        Column(Modifier.fillMaxSize().padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            FadeIn(0.0, dur = 0.8, blur = 12f, scaleFrom = 0.97f) {
                BorderBeam(size = BeamSize.Md, colorVariant = BeamColorVariant.Colorful, theme = EffectTheme.Dark, borderRadius = 22.dp) {
                    Box(Modifier.surface(22.dp, FilmColors.Card)) { Composer(t) }
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusPill("Thinking", BeamColorVariant.Ocean, 1.2)
                StatusPill("Searching the web", BeamColorVariant.Sunset, 1.32)
                StatusPill("Writing code", BeamColorVariant.Colorful, 1.44)
            }
        }
    }
    Chapter("02", "Border beam", "A living border for anything in progress.", start = 0.3, end = ChapterSeconds)
}

// ── 03 Liquid metal: a macro drift along the chrome ring, then the whole set ────────────────

private const val MetalScale = 1.95f

// Local stage positions (dp) of the props, so the camera and the hand can find them.
private const val SearchX = 364f
private const val RowY = 214f
private const val CircleX = 556f
private const val PillY = 270f

/** Screen position of local stage point ([x], [y]) under a camera at [s] centred on ([cx], [cy]). */
private fun toScreen(x: Float, y: Float, s: Float, cx: Float, cy: Float) = (480 + (x - cx) * s) to (270 + (y - cy) * s)

private fun metalCam(t: Double): Triple<Float, Float, Float> {
    // Macro on the pill's ring, drifting right; then a long pull-back to the whole set.
    val pull = ramp(t, 1.4, 1.8, Ease::cubicInOut)
    // Zoom in log space, so the pull-back feels even from macro to wide.
    val s = kotlin.math.exp(lerp(kotlin.math.ln(6.2f), kotlin.math.ln(MetalScale), pull))
    val cx = lerp(lerp(428f, 520f, ramp(t, 0.0, 1.9, Ease::sineInOut)), 480f, pull)
    val cy = lerp(PillY - 11f, 252f, pull)
    return Triple(s, cx, cy)
}

private fun metalTouch(t: Double): Touch? {
    if (t < 3.0) return null
    val (s, cx, cy) = metalCam(t)
    val circle = toScreen(CircleX + 20f, RowY, s, cx, cy)
    val from = 700f to 470f
    val m = ramp(t, 3.0, 0.7, Ease::cubicInOut)
    val x = lerp(from.first, circle.first, m) + (if (t > 3.9) 5f * ramp(t, 3.9, 0.5, Ease::sineInOut) else 0f)
    val y = lerp(from.second, circle.second, m)
    val down = t in 3.75..4.45
    val alpha = ramp(t, 3.0, 0.3) * (1 - ramp(t, 4.6, 0.3))
    return Touch(x, y, down, alpha)
}

val Metal = Shot("metal", ChapterSeconds, touch = ::metalTouch, subframes = 8) {
    val t = shotTime()
    Backdrop(light = 0.8f)
    val (s, cx, cy) = metalCam(t)
    val anchor = rememberMetalAnchor()
    val bend = rememberMetalBend()
    Camera(scale = s, x = -(cx - 480) * s, y = -(cy - 270) * s) {
        Box(Modifier.fillMaxSize().metalBendArea(bend)) {
            // Row: a search field that catches the metal, and the circle send button.
            Box(Modifier.offset(SearchX.dp, (RowY - 20).dp)) {
                MetalReflection(anchor, cornerRadius = 20.dp) {
                    Row(
                        Modifier.size(180.dp, 40.dp).surface(20.dp, Color(0xFF1A1A1C)).padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(FilmIcons.Search, Color(0xFF8B8B8B), 16.dp)
                        BasicText("Search", style = sans(14f, Color.White.copy(alpha = 0.3f), FontWeight.Medium))
                    }
                }
            }
            Box(Modifier.offset(CircleX.dp, (RowY - 20).dp)) {
                MetalFx(variant = MetalVariant.Circle, innerShadow = true, anchor = anchor, bend = bend, strength = 0.95f) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(FilmIcons.ArrowUp, Color(0xFFE8E8E8), 18.dp) }
                }
            }
            // The pill button.
            Box(Modifier.offset((480 - 70).dp, (PillY - 20).dp)) {
                MetalFx(variant = MetalVariant.Button) {
                    Box(Modifier.size(140.dp, 40.dp), contentAlignment = Alignment.Center) {
                        BasicText("Upgrade to Pro", style = sans(14f, Color(0xFFEDEDED), FontWeight.Medium))
                    }
                }
            }
            // Wordmark and badge, below.
            Row(Modifier.offset(392.dp, 306.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetalText("Pro", style = sans(22f, Color(0xFFE2E2E2), FontWeight.Medium))
                BasicText("Live mode", style = sans(16f, Color(0xFF8A8A8A)))
                MetalBadge()
            }
        }
    }
    Chapter("03", "Liquid metal", "Chromatic chrome that bends under your finger.", start = 2.7, end = ChapterSeconds)
}
