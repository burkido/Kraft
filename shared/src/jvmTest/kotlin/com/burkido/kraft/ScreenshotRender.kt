package com.burkido.kraft

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.burkido.kraft.effects.beam.BeamSpec
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Headless screenshots of the app for side-by-side review against libraries.dev.
 * Output: shared/build/screens/<name>.png. Effects are frozen at t = 1 s so frames are stable.
 */
class ScreenshotRender {
    private val out = File("build/screens").apply { mkdirs() }

    private fun shot(name: String, widthDp: Int, heightDp: Int, density: Float, library: String? = null) {
        runBlocking { BeamSpec.load() }
        val scene = ImageComposeScene(
            width = (widthDp * density).toInt(),
            height = (heightDp * density).toInt(),
            density = Density(density),
        ) {
            CompositionLocalProvider(LocalEffectFrozenTime provides 1.0) { App(startLibrary = library) }
        }
        try {
            repeat(3) { scene.render(it * 16_000_000L) }
            File(out, "$name.png").writeBytes(scene.render(64_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test fun landingDesktop() = shot("landing-desktop", 1280, 3900, 1f)
    @Test fun landingPhoneFirstScreen() = shot("landing-phone", 402, 874, 2f)
    @Test fun landingPhoneFull() = shot("landing-phone-full", 402, 5200, 1f)
    @Test fun beamDesktop() = shot("beam-desktop", 1280, 2600, 1f, library = "beam")
    @Test fun beamPhone() = shot("beam-phone", 402, 3400, 1f, library = "beam")
    @Test fun orbsDesktop() = shot("orbs-desktop", 1280, 3000, 1f, library = "orbs")
    @Test fun orbsPhone() = shot("orbs-phone", 402, 4400, 1f, library = "orbs")
    @Test fun voiceDesktop() = shot("voice-desktop", 1280, 2500, 1f, library = "voice")
    @Test fun voicePhone() = shot("voice-phone", 402, 3400, 1f, library = "voice")
    @Test fun gooeyDesktop() = shot("gooey-desktop", 1280, 2300, 1f, library = "gooey")
    @Test fun gooeyPhone() = shot("gooey-phone", 402, 3000, 1f, library = "gooey")
    @Test fun metalDesktop() = shot("metal-desktop", 1440, 2204, 1f, library = "metal")
    @Test fun metalPhone() = shot("metal-phone", 390, 2720, 2f, library = "metal")
    @Test fun imageDesktop() = shot("image-desktop", 1440, 2298, 1f, library = "image")
    @Test fun imagePhone() = shot("image-phone", 390, 2800, 2f, library = "image")
    @Test fun botsDesktop() = shot("bots-desktop", 1440, 2296, 1f, library = "bots")
    @Test fun botsPhone() = shot("bots-phone", 390, 2900, 2f, library = "bots")
    @Test fun licensesDesktop() = shot("licenses-desktop", 1280, 1100, 1f, library = "licenses")
}
