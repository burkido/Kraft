package com.burkido.kraft.film.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.burkido.kraft.film.LocalFilmFonts
import com.burkido.kraft.film.RevealText
import com.burkido.kraft.film.Shot
import com.burkido.kraft.film.Spacer
import com.burkido.kraft.film.display
import com.burkido.kraft.film.drift
import com.burkido.kraft.film.lerp
import com.burkido.kraft.film.mono
import com.burkido.kraft.film.ramp
import com.burkido.kraft.film.sans
import com.burkido.kraft.film.shotTime
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode

/** Cold open: darkness, then a single thinking orb forms and a line of copy. */
val Opening = Shot("open", seconds = 7.5) {
    val t = shotTime()
    Backdrop(light = ramp(t, 0.4, 3.0, Ease::sineInOut))
    val out = ramp(t, 6.6, 0.9, Ease::cubicInOut)
    Camera(
        scale = lerp(1.0f, 1.14f, ramp(t, 0.0, 7.5, Ease::sineInOut)) + out * 0.25f,
        x = drift(t, 3f, 11.0),
        y = drift(t, 2f, 9.0, 0.3) - 20f,
        modifier = Modifier.graphicsLayer {
            alpha = 1 - out
            val b = out * 18f
            if (b > 0.05f) renderEffect = BlurEffect(b * density, b * density, TileMode.Decal)
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FadeIn(start = 0.5, dur = 2.6, blur = 24f, scaleFrom = 0.82f) {
                ThinkingOrb(OrbState.Searching, size = OrbSize.S64, theme = EffectTheme.Dark, displaySize = 250.dp)
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(bottom = 58.dp), contentAlignment = Alignment.BottomCenter) {
        RevealText("Motion is how software feels.", display(30f, FilmColors.Text), start = 3.0, stagger = 0.09, dur = 1.3, out = 6.3, outDur = 0.7)
    }
}

/** The name in liquid metal. */
val Title = Shot("title", seconds = 5.0) {
    val t = shotTime()
    Backdrop(light = 1f)
    val out = ramp(t, 4.35, 0.65, Ease::cubicInOut)
    Camera(scale = lerp(1.04f, 1f, ramp(t, 0.0, 5.0, Ease::cubicOut)) + out * 0.04f) {
        Column(Modifier.fillMaxSize().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            FadeIn(start = 0.0, dur = 1.6, blur = 26f, scaleFrom = 1.08f, out = 4.35, outDur = 0.6) {
                MetalText(
                    "Kraft",
                    style = TextStyle(fontFamily = LocalFilmFonts.current.display, fontWeight = FontWeight.Medium, fontSize = 150.sp, letterSpacing = (-7).sp, color = FilmColors.Text.copy(alpha = 0.92f)),
                    preset = MetalPreset.Chromatic,
                    theme = EffectTheme.Dark,
                    metalOpacity = 0.9f,
                    shaderScale = 1.4f,
                )
            }
            Spacer(6.dp)
            RevealText("High-crafted UI effects for Compose.", sans(22f, FilmColors.Muted), start = 1.0, stagger = 0.05, out = 4.3, outDur = 0.5)
            Spacer(22.dp)
            RevealText("SEVEN LIBRARIES  ·  ANDROID  ·  IOS  ·  DESKTOP", mono(12f, FilmColors.Faint), start = 1.7, stagger = 0.04, rise = 6.dp, out = 4.3, outDur = 0.5)
        }
    }
}
