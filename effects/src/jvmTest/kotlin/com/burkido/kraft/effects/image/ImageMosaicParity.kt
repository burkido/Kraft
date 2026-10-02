package com.burkido.kraft.effects.image

import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.test.Test

/**
 * The raw mosaic buffers of sites/beam/kraft-image.html: a 320 CSS px card at the GL DPR of 1.25
 * (400 × 400), dark theme on #1B1B1B, clock at 1 s. Output: effects/build/parity/image-raw/.
 */
class ImageMosaicParity {
    private val out = File("build/parity/image-raw").apply { mkdirs() }

    private fun render(name: String, preset: ImagePreset, seconds: Float = 1f, dir: File = out) {
        val mode = imageMode(preset, dark = true)
        val shader = ImageShader().apply { configure(mode, 1f, 1f, null, Color(0xFF1B1B1B)) }
        val pixels = ByteArray(400 * 400 * 4)
        ImageMosaic(shader).render(400, 400, 1.25f, seconds * mode.speed, pixels)
        val bitmap = Bitmap().apply {
            allocPixels(ImageInfo(400, 400, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
            installPixels(pixels)
        }
        File(dir, "$name.png").writeBytes(Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    @Test
    fun presets() {
        render("organic", ImagePreset.PixelsOrganic)
        render("mechanic", ImagePreset.PixelsMechanic)
        render("sweep", ImagePreset.SweepGradient)
        val later = File("build/parity/image-raw-t").apply { mkdirs() }
        render("sweep", ImagePreset.SweepGradient, 2.5f, later)
        render("organic", ImagePreset.PixelsOrganic, 7f, later)
        render("mechanic", ImagePreset.PixelsMechanic, 4.2f, later)
    }
}
