package com.burkido.kraft.effects.gooey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * The gooey parity scenes of sites/beam/kraft-gooey.html: static liquid states on #171717 with a
 * 60 dp margin, density 2. Output: effects/build/parity/gooey/<scene>.png.
 */
class GooeyParityRender {
    private val out = File("build/parity/gooey").apply { mkdirs() }
    private val shadow = parseLiquidShadow(
        "0 0 0 1px rgba(255, 255, 255, 0.04) inset, 0 1px 0 0 rgba(255, 255, 255, 0.03) inset, " +
            "0 0 0 1px rgba(0, 0, 0, 0.06), 0 2px 6px 0 rgba(0, 0, 0, 0.05), 0 4px 42px 0 rgba(0, 0, 0, 0.24)",
    )

    private fun render(name: String, w: Int, h: Int, content: @Composable () -> Unit) {
        val density = 2f
        val scene = ImageComposeScene(((w + 120) * density).toInt(), ((h + 120) * density).toInt(), Density(density)) {
            Box(Modifier.background(Color(0xFF171717)).padding(60.dp)) { content() }
        }
        try {
            repeat(4) { scene.render(it * 16_000_000L) }
            File(out, "$name.png").writeBytes(scene.render(80_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Morph(k: Float) {
        Liquid(Modifier.size(200.dp, 140.dp), fill = Color(0xFF202020), shadow = shadow) {
            for ((x, y) in listOf(-54 to -34, 0 to -64, 54 to -34)) {
                Item((x * k).dp, (y * k).dp, radius = 20.dp, modifier = Modifier.offset(80.dp, 80.dp)) { Box(Modifier.size(40.dp)) }
            }
            Item(0.dp, 0.dp, radius = 20.dp, modifier = Modifier.offset(80.dp, 80.dp)) { Box(Modifier.size(40.dp)) }
        }
    }

    @Test
    fun scenes() {
        render("morph-open", 200, 140) { Morph(1f) }
        render("morph-closed", 200, 140) { Morph(0f) }
        render("morph-mid", 200, 140) { Morph(0.45f) }
        render("move", 240, 80) {
            Box(Modifier.size(240.dp, 80.dp)) {
                Box(
                    Modifier.offset(14.dp, 38.dp).size(212.dp, 8.dp)
                        .background(Color(115, 115, 115).copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                )
                Liquid(Modifier.size(240.dp, 80.dp), fill = Color(0xFF525252), shadow = shadow) {
                    MoveItem(12.dp, Modifier.offset(14.dp + 84.dp, 30.dp), MoveTuning(stretch = 0.6f, trail = 0.35f)) { Box(Modifier.size(24.dp)) }
                }
            }
        }
        render("bend", 400, 210) {
            Liquid(Modifier.size(400.dp, 210.dp), fill = Color(0xFF202020), shadow = shadow) {
                BendItem(23.dp, Modifier.align(Alignment.Center)) { Box(Modifier.size(261.dp, 46.dp)) }
            }
        }
    }
}
