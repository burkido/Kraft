package com.burkido.kraft.effects.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

actual class PlatformPixelImage actual constructor(actual val width: Int, actual val height: Int) {
    private val info = ImageInfo(
        ColorInfo(ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB),
        width,
        height,
    )
    private val skiaBitmap = Bitmap().apply { allocPixels(info) }

    actual val bitmap: ImageBitmap = skiaBitmap.asComposeImageBitmap()

    // Compose snapshots the bitmap into an SkImage on every draw, so replacing the pixels here
    // never tears a frame that is being drawn.
    actual fun upload(pixels: ByteArray) {
        skiaBitmap.installPixels(info, pixels, width * 4)
    }
}
