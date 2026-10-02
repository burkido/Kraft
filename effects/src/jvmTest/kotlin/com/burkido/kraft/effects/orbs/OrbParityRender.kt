package com.burkido.kraft.effects.orbs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the upstream orbs parity set (sites/orbs/parity.ts): every state × {64, 20} × theme at
 * raw engine times {0.6, 1.7, 3.3, 5.1}, 2× density, transparent background.
 *
 * Output: effects/build/parity/orbs/<state>-<size>-<d|l>-<t>.png
 */
class OrbParityRender {
    private val out = File("build/parity/orbs").apply { mkdirs() }

    @Test
    fun renderAll() {
        for (state in OrbState.entries) {
            for (size in listOf(OrbSize.S64, OrbSize.S20)) {
                for (dark in listOf(true, false)) {
                    for (t in listOf(0.6, 1.7, 3.3, 5.1)) {
                        val px = size.px * 2
                        val scene = ImageComposeScene(px, px, Density(2f)) {
                            CompositionLocalProvider(LocalEffectFrozenTime provides t) {
                                ThinkingOrb(state, size = size, theme = if (dark) EffectTheme.Dark else EffectTheme.Light)
                            }
                        }
                        try {
                            scene.render()
                            val name = "${state.name.lowercase()}-${size.px}-${if (dark) "d" else "l"}-$t.png"
                            File(out, name).writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
                        } finally {
                            scene.close()
                        }
                    }
                }
            }
        }
    }
}
