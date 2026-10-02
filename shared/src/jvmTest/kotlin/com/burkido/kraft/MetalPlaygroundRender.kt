package com.burkido.kraft

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.burkido.kraft.effects.beam.BeamSpec
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Clicks through the Metal playground's types on the desktop layout and saves each stage at 2×.
 * Output: shared/build/screens/metal-pg-<type>.png.
 */
class MetalPlaygroundRender {
    private val out = File("build/screens").apply { mkdirs() }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun types() {
        runBlocking { BeamSpec.load() }
        val density = 2f
        val scene = ImageComposeScene((1440 * density).toInt(), (1500 * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalEffectFrozenTime provides 1.0) { App(startLibrary = "metal") }
        }
        var time = 0L
        fun frame(): Image = scene.render(time).also { time += 16_000_000L }
        fun click(x: Float, y: Float) {
            val p = Offset(x * density, y * density)
            scene.sendPointerEvent(PointerEventType.Move, p)
            scene.sendPointerEvent(PointerEventType.Press, p)
            scene.sendPointerEvent(PointerEventType.Release, p)
            repeat(20) { frame() }
        }
        fun save(name: String) {
            val shot = frame()
            // The stage: x 380…1060, y 880…1320 dp.
            val crop = Image.makeFromBitmap(org.jetbrains.skia.Bitmap().also { bmp ->
                bmp.allocN32Pixels((680 * density).toInt(), (440 * density).toInt())
                shot.readPixels(bmp, (380 * density).toInt(), (880 * density).toInt())
            })
            File(out, "metal-pg-$name.png").writeBytes(crop.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        try {
            repeat(4) { frame() }
            save("circle")
            click(1221f, 933f)
            save("text")
            click(1120f, 969f)
            save("button")
            click(1188f, 969f)
            save("badge")
        } finally {
            scene.close()
        }
    }
}
