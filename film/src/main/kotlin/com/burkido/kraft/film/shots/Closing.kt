package com.burkido.kraft.film.shots

import androidx.compose.foundation.background
import java.io.File
import com.burkido.kraft.film.loadBitmap
import com.burkido.kraft.effects.image.ImagePreset
import com.burkido.kraft.effects.image.ImageGeneration
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burkido.kraft.App
import com.burkido.kraft.effects.avatars.BotAvatar
import com.burkido.kraft.effects.avatars.BotAvatarType
import com.burkido.kraft.effects.beam.BeamColorVariant
import com.burkido.kraft.effects.beam.BeamSize
import com.burkido.kraft.effects.beam.BorderBeam
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.metal.MetalPreset
import com.burkido.kraft.effects.metal.MetalText
import com.burkido.kraft.effects.orbs.OrbSize
import com.burkido.kraft.effects.orbs.OrbState
import com.burkido.kraft.effects.orbs.ThinkingOrb
import com.burkido.kraft.film.Backdrop
import com.burkido.kraft.film.Camera
import com.burkido.kraft.film.Ease
import com.burkido.kraft.film.FadeIn
import com.burkido.kraft.film.FilmColors
import com.burkido.kraft.film.FilmIcons
import com.burkido.kraft.film.IconChip
import com.burkido.kraft.film.LocalFilmFonts
import com.burkido.kraft.film.RevealText
import com.burkido.kraft.film.Shot
import com.burkido.kraft.film.display
import com.burkido.kraft.film.drift
import com.burkido.kraft.film.lerp
import com.burkido.kraft.film.mono
import com.burkido.kraft.film.ramp
import com.burkido.kraft.film.sans
import com.burkido.kraft.film.shotTime
import com.burkido.kraft.film.surface
import com.burkido.kraft.landing.LibraryScene
import com.burkido.kraft.library.LibraryId

// ── Code: one composable per effect ─────────────────────────────────────────────────────────

private class Snippet(val code: String, val at: Double)

private val Snippets = listOf(
    Snippet("ThinkingOrb(\n    state = OrbState.Searching,\n)", 0.35),
    Snippet("BorderBeam(size = BeamSize.Md) {\n    ChatInput()\n}", 1.95),
    Snippet("BotAvatar(\n    type = BotAvatarType.Ghost,\n)", 3.45),
)

private val FnColor = Color(0xFF7DB4FF)
private val TypeColor = Color(0xFF6FE3C8)
private val ArgColor = Color(0xFFC79BFF)
private val PunctColor = Color(0xFF8A8A92)

/** A tiny Kotlin highlighter: calls, types after a dot, named arguments. */
private fun highlight(code: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("""([A-Z][A-Za-z]*)(?=\()|([A-Z][A-Za-z]*)(\.)([A-Z][A-Za-z]*)|([a-z]+)(?= =)|([(){},.=])|(\s+)|([^\s(){},.=]+)""")
    for (m in re.findAll(code)) {
        val g = m.groups
        when {
            g[1] != null -> withStyle(SpanStyle(color = FnColor)) { append(g[1]!!.value) }
            g[2] != null -> {
                withStyle(SpanStyle(color = TypeColor)) { append(g[2]!!.value) }
                withStyle(SpanStyle(color = PunctColor)) { append(".") }
                withStyle(SpanStyle(color = Color(0xFFEDEDED))) { append(g[4]!!.value) }
            }
            g[5] != null -> withStyle(SpanStyle(color = ArgColor)) { append(g[5]!!.value) }
            g[6] != null -> withStyle(SpanStyle(color = PunctColor)) { append(g[6]!!.value) }
            else -> append(m.value)
        }
    }
}

val Code = Shot("code", 5.0) {
    val t = shotTime()
    Backdrop(light = 0.9f)
    val active = Snippets.indexOfLast { t >= it.at }.coerceAtLeast(0)
    val snippet = Snippets[active]
    val typed = ((t - snippet.at) / 0.5).coerceIn(0.0, 1.0)
    val shown = snippet.code.take((snippet.code.length * typed).toInt())
    Camera(scale = lerp(1.05f, 1.0f, ramp(t, 0.0, 5.0, Ease::sineInOut)), y = drift(t, 1.5f, 6.0)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(70.dp))
            RevealText("One composable away.", display(34f), start = 0.0, stagger = 0.07)
            Spacer(Modifier.height(8.dp))
            RevealText("Pure Kotlin. Pure Compose. Nothing to wire up.", sans(16f), start = 0.2, stagger = 0.035)
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                FadeIn(0.1, dur = 0.8, blur = 10f, rise = 14.dp) {
                    Column(Modifier.size(420.dp, 230.dp).surface(18.dp, Color(0xFF111113))) {
                        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            repeat(3) { Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) }
                            Spacer(Modifier.width(10.dp))
                            BasicText("Screen.kt", style = mono(11f, FilmColors.Muted))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(FilmColors.Hairline))
                        Row(Modifier.padding(start = 18.dp, top = 22.dp)) {
                            BasicText("1\n2\n3", style = mono(15f, Color.White.copy(alpha = 0.16f)).copy(lineHeight = 26.sp))
                            Spacer(Modifier.width(18.dp))
                            val caret = if ((t * 2.4).toInt() % 2 == 0 || typed < 1.0) "▍" else " "
                            BasicText(
                                buildAnnotatedString {
                                    append(highlight(shown))
                                    withStyle(SpanStyle(color = FilmColors.Accent)) { append(caret) }
                                },
                                style = mono(15f, Color(0xFFEDEDED)).copy(lineHeight = 26.sp, letterSpacing = 0.sp),
                            )
                        }
                    }
                }
                FadeIn(0.2, dur = 0.8, blur = 10f, rise = 14.dp) {
                    Box(Modifier.size(300.dp, 230.dp).surface(18.dp, Color(0xFF0F0F11)), contentAlignment = Alignment.Center) {
                        Snippets.forEachIndexed { i, sn ->
                            val inAt = sn.at + 0.5
                            val outAt = Snippets.getOrNull(i + 1)?.let { it.at + 0.45 } ?: 99.0
                            val p = ramp(t, inAt, 0.7, Ease::expoOut)
                            val o = ramp(t, outAt, 0.35, Ease::cubicInOut)
                            if (p > 0f && o < 1f) {
                                Box(Modifier.graphicsLayer {
                                    alpha = p * (1 - o)
                                    val sc = lerp(0.9f, 1f, p) * lerp(1f, 1.04f, o)
                                    scaleX = sc; scaleY = sc
                                }) {
                                    when (i) {
                                        0 -> ThinkingOrb(OrbState.Searching, size = OrbSize.S64, theme = EffectTheme.Dark, displaySize = 120.dp)
                                        1 -> BorderBeam(size = BeamSize.Md, colorVariant = BeamColorVariant.Colorful, theme = EffectTheme.Dark, borderRadius = 16.dp) {
                                            Row(Modifier.size(236.dp, 52.dp).surface(16.dp, FilmColors.Card).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                BasicText("Ask anything…", style = sans(14f, FilmColors.Faint))
                                                Spacer(Modifier.weight(1f))
                                                IconChip(FilmIcons.ArrowUp, 28.dp)
                                            }
                                        }
                                        else -> BotAvatar(type = BotAvatarType.Ghost, size = 110.dp, seed = 0.4)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Platforms: the real app, on three form factors ──────────────────────────────────────────

/** Renders the real Kraft app at [w]×[h] dp, scaled by [scale] into its frame. */
@Composable
private fun LiveApp(w: Dp, h: Dp, scale: Float) {
    Box(Modifier.size(w * scale, h * scale)) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(w, h)
                .graphicsLayer { transformOrigin = TransformOrigin(0f, 0f); scaleX = scale; scaleY = scale },
        ) { App() }
    }
}

@Composable
private fun Phone(island: Boolean, scale: Float) {
    val w = 390.dp
    val h = 844.dp
    Box(Modifier.surface((48 * scale).dp + 6.dp, Color(0xFF1C1C1F), Color.White.copy(alpha = 0.14f), elevation = 30.dp).padding(6.dp)) {
        Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape((48 * scale).dp))) {
            LiveApp(w, h, scale)
            if (island) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = (11 * scale).dp).size((120 * scale).dp, (34 * scale).dp).clip(CircleShape).background(Color.Black))
            } else {
                Box(Modifier.align(Alignment.TopCenter).padding(top = (14 * scale).dp).size((26 * scale).dp).clip(CircleShape).background(Color.Black))
            }
        }
    }
}

@Composable
private fun DesktopWindow(scale: Float) {
    Column(Modifier.width(1100.dp * scale).surface(12.dp, Color(0xFF1A1A1D), Color.White.copy(alpha = 0.12f), elevation = 40.dp)) {
        Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))) }
        }
        LiveApp(1100.dp, 700.dp, scale)
    }
}

val Platforms = Shot("platforms", 5.0, preroll = 1.5) {
    val t = shotTime()
    Backdrop(light = 1f)
    Camera(scale = lerp(1.08f, 1.0f, ramp(t, 0.0, 5.0, Ease::cubicOut)), y = drift(t, 1.5f, 7.0)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(top = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RevealText("Android. iOS. Desktop.", display(34f), start = 0.0, stagger = 0.12)
                Spacer(Modifier.height(8.dp))
                RevealText("One Compose Multiplatform codebase.", sans(16f), start = 0.35, stagger = 0.04)
            }
            FadeIn(0.25, dur = 1.1, blur = 12f, rise = 40.dp, modifier = Modifier.align(Alignment.Center).offset(y = 44.dp)) {
                DesktopWindow(0.44f)
            }
            FadeIn(0.45, dur = 1.1, blur = 12f, rise = 60.dp, modifier = Modifier.align(Alignment.Center).offset(x = (-318).dp, y = 70.dp)) {
                Phone(island = false, scale = 0.34f)
            }
            FadeIn(0.6, dur = 1.1, blur = 12f, rise = 60.dp, modifier = Modifier.align(Alignment.Center).offset(x = 318.dp, y = 70.dp)) {
                Phone(island = true, scale = 0.34f)
            }
        }
    }
}

// ── Finale: every library, live, in one grid ────────────────────────────────────────────────

private val Grid = listOf(
    listOf(LibraryId.Orbs, LibraryId.Beam, LibraryId.Metal, LibraryId.Gooey),
    listOf(LibraryId.Voice, LibraryId.Bots, LibraryId.Image),
)

/**
 * The landing's image card (`ImageCardScene`), rebuilt for the film so it reveals our own still
 * life instead of the library's demo photos, at the same size, mosaic and timing.
 */
@Composable
private fun BoxScope.StillLifeCard(width: Dp, height: Dp) {
    val aspect = 2516f / 2160f
    val w = maxOf(width, height * aspect)
    val h = w / aspect
    val card = w * 0.7214f
    val pool = remember { listOf(loadBitmap(File(Assets, "gen_chrome.jpg"))) }
    Box(Modifier.align(Alignment.Center).offset(w * 0.003f, h * 0.006f)) {
        ImageGeneration(
            preset = ImagePreset.PixelsOrganic,
            theme = EffectTheme.Dark,
            cardBg = Color(0xFF1B1B1B),
            pixelScale = card.value * (6f + 0.22f * 74f) / (320f * 22.5f),
            images = pool,
            autoReveal = true,
            // Cycles start at the preroll; this lands the reveal about a second into the shot.
            revealInitialDelaySeconds = 2.5f..2.5f,
            cornerRadius = w * 0.0346f,
        ) { Box(Modifier.size(card, h * 0.737f)) }
    }
}

/** What a finale render draws: the full grid, or a white matte of the image card's stage only. */
private enum class FinalePass { Full, ImageMatte }

@Composable
private fun LiveCard(id: LibraryId, start: Double, pass: FinalePass) {
    val cardW = 204.dp
    val cardH = 170.dp
    val design = 1.6f // scenes are laid out at 1.6× and scaled down, like the landing cards
    if (pass == FinalePass.ImageMatte) {
        // Solid over the whole card and a small margin, following the card's motion but not its
        // fades: around the stage both renders are identical, so the margin is safe.
        val t = shotTime()
        val p = ramp(t, start, 0.9, Ease::expoOut)
        Box(Modifier.size(cardW, cardH + 36.dp).graphicsLayer {
            translationY = 16.dp.toPx() * (1 - p)
            val sc = lerp(0.96f, 1f, p)
            scaleX = sc; scaleY = sc
        }) {
            if (id == LibraryId.Image) {
                Box(Modifier.wrapContentSize(unbounded = true).requiredSize(cardW + 12.dp, cardH + 48.dp).background(Color.White))
            }
        }
        return
    }
    FadeIn(start, dur = 0.9, blur = 10f, rise = 16.dp, scaleFrom = 0.96f) {
        Column(Modifier.size(cardW, cardH + 36.dp).surface(16.dp, Color(0xFF111113))) {
            Box(Modifier.size(cardW, cardH).background(Color(0xFF0D0D0F))) {
                Box(Modifier.align(Alignment.Center).wrapContentSize(unbounded = true).requiredSize(cardW * design, cardH * design).graphicsLayer {
                    scaleX = 1 / design; scaleY = 1 / design
                }) {
                    if (id == LibraryId.Image) {
                        StillLifeCard(cardW * design, cardH * design)
                    } else {
                        LibraryScene(id, cardW * design, cardH * design, compact = false)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(id.title, style = sans(12f, FilmColors.Text.copy(alpha = 0.85f), FontWeight.Medium))
            }
        }
    }
}

private fun finale(name: String, pass: FinalePass) = Shot(name, 5.0, preroll = 1.5) {
    val t = shotTime()
    if (pass == FinalePass.Full) Backdrop(light = 1f) else Box(Modifier.fillMaxSize().background(Color.Black))
    val out = ramp(t, 4.3, 0.7, Ease::cubicInOut)
    Camera(
        scale = lerp(1.22f, 1.0f, ramp(t, 0.0, 4.0, Ease::cubicOut)) - out * 0.04f,
        rotX = lerp(10f, 0f, ramp(t, 0.0, 4.0, Ease::cubicOut)),
        modifier = Modifier.graphicsLayer { alpha = if (pass == FinalePass.Full) 1 - out else 1f },
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Grid.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEachIndexed { c, id -> LiveCard(id, 0.05 + (r * 4 + c) * 0.06, pass) }
                }
                if (r == 0) Spacer(Modifier.height(14.dp))
            }
        }
    }
}

val Finale = finale("finale", FinalePass.Full)

/**
 * A white matte over the image card alone, same camera, fades and motion blur. The v2 cut merges
 * the new finale through it into the v1 finale, so every other card stays frame-for-frame the same.
 */
val FinaleImageMatte = finale("finale-matte", FinalePass.ImageMatte)

// ── End card ────────────────────────────────────────────────────────────────────────────────

val End = Shot("end", 7.5) {
    val t = shotTime()
    Backdrop(light = ramp(t, 0.0, 1.5, Ease::sineInOut) * (1 - ramp(t, 6.2, 0.9)))
    val out = ramp(t, 6.1, 1.0, Ease::cubicInOut)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1 - out }) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FadeIn(0.0, dur = 1.8, blur = 20f, scaleFrom = 0.9f) {
                ThinkingOrb(OrbState.Breathing, size = OrbSize.S64, theme = EffectTheme.Dark, displaySize = 64.dp)
            }
            Spacer(Modifier.height(18.dp))
            FadeIn(0.25, dur = 1.6, blur = 22f, scaleFrom = 1.05f) {
                MetalText(
                    "Kraft",
                    style = TextStyle(fontFamily = LocalFilmFonts.current.display, fontWeight = FontWeight.Medium, fontSize = 96.sp, letterSpacing = (-4.5).sp, color = FilmColors.Text.copy(alpha = 0.92f)),
                    preset = MetalPreset.Chromatic,
                    theme = EffectTheme.Dark,
                    metalOpacity = 0.9f,
                    shaderScale = 1.4f,
                )
            }
            Spacer(Modifier.height(6.dp))
            RevealText("High-crafted UI effects for Compose Multiplatform.", sans(19f, FilmColors.Muted), start = 0.9, stagger = 0.04)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)) {
            RevealText("Effects ported from Libraries.dev by Jakub Antalik", mono(10.5f, FilmColors.Faint), start = 1.6, stagger = 0.02, rise = 4.dp)
        }
    }
}
