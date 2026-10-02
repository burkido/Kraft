package com.burkido.kraft.effects.metal

import com.burkido.kraft.effects.core.parseCssColor
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.test.Test

class MetalSheetBenchmark {
    private fun rgba(hex: String) = parseCssColor(hex).let { floatArrayOf(it.red, it.green, it.blue, it.alpha) }

    // chromatic / dark (src/engine/presets.ts)
    private val chromaticDark = MetalMaterial(
        repetition = 2f, softness = 0.09f, shiftRed = 0.75f, shiftBlue = 0.75f, distortion = 0.1f,
        contour = 0.4f, angle = 90f, tint = rgba("#88ccff2e"), colorBack = rgba("#00000000"),
        speed = 1f, shaderOpacity = 1f,
    )

    @Test
    fun sheetTiming() {
        val sheet = LiquidMetalSheet(192, chromaticDark)
        val out = ByteArray(192 * 192 * 4)
        repeat(30) { sheet.render(it * 0.066, out) } // warm up the JIT
        val runs = 60
        val start = System.nanoTime()
        repeat(runs) { sheet.render(2.0 + it * 0.066, out) }
        val ms = (System.nanoTime() - start) / 1e6 / runs
        println("LiquidMetalSheet 192×192 on JVM: %.2f ms per frame (budget at 15 fps: 66 ms)".format(ms))

        File("build/parity/metal").mkdirs()
        sheet.render(1.0, out)
        val info = ImageInfo(ColorInfo(ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB), 192, 192)
        val bmp = Bitmap().apply { allocPixels(info); installPixels(info, out, 192 * 4) }
        File("build/parity/metal/sheet_chromatic_dark_t1.png").writeBytes(Image.makeFromBitmap(bmp).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
}
