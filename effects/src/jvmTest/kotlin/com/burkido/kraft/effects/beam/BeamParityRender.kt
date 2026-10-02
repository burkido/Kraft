package com.burkido.kraft.effects.beam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Renders the upstream parity scene (sites/beam/src/parity-entry.tsx): a 348×122 card, radius 20,
 * 60 px padding, frozen at the harness's per-family timestamps, at 2× — 936×484 PNGs directly
 * comparable to the web captures.
 *
 * Output: effects/build/parity/beam/<size>_<variant>_<theme>.png
 */
class BeamParityRender {
    private val out = File("build/parity/beam").apply { mkdirs() }

    @Test
    fun renderAll() {
        runBlocking { BeamSpec.load() }
        for (size in BeamSize.entries) {
            for (variant in BeamColorVariant.entries) {
                for (dark in listOf(true, false)) render(size, variant, dark)
            }
        }
    }

    private fun render(size: BeamSize, variant: BeamColorVariant, dark: Boolean) {
        val frozen = when (size) {
            BeamSize.Line -> 3.1 * 0.5
            BeamSize.PulseInner, BeamSize.PulseOutside -> 0.8
            else -> 0.49
        }
        val scene = ImageComposeScene(width = 936, height = 484, density = Density(2f)) {
            CompositionLocalProvider(LocalEffectFrozenTime provides frozen) {
                ParityScene(size, variant, dark)
            }
        }
        try {
            scene.render() // first frame composes; the second draws with settled state
            val image = scene.render()
            val name = "${size.key}_${variant.key}_${if (dark) "dark" else "light"}.png"
            File(out, name).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}

private fun grey(v: Double): Color {
    val c = (v * 255).roundToInt()
    return Color(c, c, c)
}

@Composable
private fun ParityScene(size: BeamSize, variant: BeamColorVariant, dark: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.size(468.dp, 242.dp).background(if (dark) grey(0.027) else grey(1.0))) {
        Box(Modifier.offset(60.dp, 60.dp)) {
            BorderBeam(
                size = size,
                colorVariant = variant,
                theme = if (dark) EffectTheme.Dark else EffectTheme.Light,
                borderRadius = 20.dp,
            ) {
                Box(
                    Modifier
                        .size(348.dp, 122.dp)
                        .clip(shape)
                        .background(if (dark) grey(0.094) else grey(0.97))
                        .border(1.dp, if (dark) grey(0.24) else grey(0.85), shape),
                )
            }
        }
    }
}
