package com.burkido.kraft.effects.core

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * A platform bitmap that CPU-rendered effects (the metal sheet, avatar plastic caps, image
 * mosaics) upload pixels into.
 *
 * Pixels are RGBA_8888, premultiplied, sRGB, row-major: `width * height * 4` bytes.
 */
expect class PlatformPixelImage(width: Int, height: Int) {
    val width: Int
    val height: Int
    val bitmap: ImageBitmap

    /** Copies [pixels] into [bitmap]. Call on the main thread. */
    fun upload(pixels: ByteArray)
}

/**
 * [PlatformPixelImage] plus a version counter. The bitmap object never changes, so draw code
 * reads [image], which subscribes it to [commit] and redraws after every upload.
 */
@Stable
class PixelSurface(val width: Int, val height: Int) {
    private val platform = PlatformPixelImage(width, height)
    private val version = mutableIntStateOf(0)

    /** A scratch buffer the owner can fill on the main thread before [commit]. */
    val pixels = ByteArray(width * height * 4)

    /** The bitmap to draw. Reading it inside a draw lambda subscribes that draw to commits. */
    val image: ImageBitmap
        get() {
            version.intValue
            return platform.bitmap
        }

    /** Uploads [source] (defaults to [pixels]) and invalidates readers of [image]. */
    fun commit(source: ByteArray = pixels) {
        platform.upload(source)
        version.intValue++
    }
}
