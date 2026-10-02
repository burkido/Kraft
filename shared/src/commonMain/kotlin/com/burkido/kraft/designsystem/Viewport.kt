package com.burkido.kraft.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The window size in dp — the "viewport" the site's CSS media queries test. One CSS px is one dp,
 * so `@media (max-width: 639px)` is simply `viewport.maxWidth(639)`.
 */
@Immutable
data class Viewport(val width: Dp, val height: Dp) {
    /** `@media (max-width: <px>px)`. */
    fun maxWidth(px: Int): Boolean = width <= px.dp

    /** `@media (min-width: <px>px)`. */
    fun minWidth(px: Int): Boolean = width >= px.dp

    /** `<n>vw` in dp. */
    fun vw(n: Float): Dp = width * (n / 100f)

    /** `.app`'s horizontal gutter. */
    val gutter: Dp get() = if (maxWidth(639)) 12.dp else 24.dp
}

val LocalViewport = staticCompositionLocalOf { Viewport(400.dp, 800.dp) }
