package com.burkido.kraft.effects.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * The composited cards of sites/beam/kraft-image.html: a [size] dp card on #1B1B1B with 20 dp
 * corners, 40 dp of #171717 around it, density 2, the clock at 1 s.
 * Output: effects/build/parity/image/<name>.png.
 */
class ImageParityRender {
    private val out = File("build/parity/image").apply { mkdirs() }

    private fun render(name: String, preset: ImagePreset, size: Int = 320, seconds: Double = 1.0) {
        val density = 2f
        val px = ((size + 80) * density).toInt()
        val scene = ImageComposeScene(px, px, Density(density)) {
            CompositionLocalProvider(LocalEffectFrozenTime provides seconds) {
                Box(Modifier.background(Color(0xFF171717)).padding(40.dp)) {
                    ImageGeneration(preset = preset, theme = EffectTheme.Dark, cardBg = Color(0xFF1B1B1B)) {
                        Box(Modifier.size(size.dp))
                    }
                }
            }
        }
        try {
            repeat(4) { scene.render(it * 16_000_000L) }
            File(out, "$name.png").writeBytes(scene.render(80_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    /** A reveal of gen-2 frozen at [elapsedMs] into it (the web capture's `kraft-image-reveal.html`). */
    private fun reveal(name: String, preset: ImagePreset, elapsedMs: Double) {
        val density = 2f
        val px = (400 * density).toInt()
        val bytes = File("../shared/src/commonMain/composeResources/drawable/gen_2.jpg").takeIf { it.exists() }?.readBytes()
            ?: File(System.getProperty("user.home"), "gen-2.jpg").readBytes()
        val bitmap = org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        val state = ImageGenerationState()
        val scene = ImageComposeScene(px, px, Density(density)) {
            CompositionLocalProvider(LocalEffectFrozenTime provides 1.0) {
                Box(Modifier.background(Color(0xFF171717)).padding(40.dp)) {
                    ImageGeneration(preset = preset, theme = EffectTheme.Dark, cardBg = Color(0xFF1B1B1B), images = listOf(bitmap), state = state) {
                        Box(Modifier.size(320.dp))
                    }
                }
            }
        }
        try {
            repeat(3) { scene.render(it * 16_000_000L) }
            state.controller!!.startReveal(RevealImages.of(bitmap), -elapsedMs) {}
            repeat(4) { scene.render((3 + it) * 16_000_000L) }
            File(out, "$name.png").writeBytes(scene.render(120_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun revealSequence() {
        for (t in listOf(300, 900, 1500, 2200, 3200)) reveal("reveal-organic-$t", ImagePreset.PixelsOrganic, t.toDouble())
    }

    @Test
    fun presets() {
        render("organic", ImagePreset.PixelsOrganic)
        render("mechanic", ImagePreset.PixelsMechanic)
        render("sweep", ImagePreset.SweepGradient)
    }
}
