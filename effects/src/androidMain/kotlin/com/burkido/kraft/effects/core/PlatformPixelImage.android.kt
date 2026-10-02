package com.burkido.kraft.effects.core

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.nio.ByteBuffer

actual class PlatformPixelImage actual constructor(actual val width: Int, actual val height: Int) {
    // ARGB_8888 is stored as premultiplied R,G,B,A bytes, so the buffer copies in unchanged.
    private val androidBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    actual val bitmap: ImageBitmap = androidBitmap.asImageBitmap()

    actual fun upload(pixels: ByteArray) {
        androidBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
    }
}
