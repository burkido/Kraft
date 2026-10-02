package com.burkido.kraft.effects.core

import androidx.compose.runtime.Composable

/**
 * The platform's reduce-motion preference — the counterpart of `prefers-reduced-motion`. Effects
 * render a static frame while it is on.
 */
@Composable
expect fun rememberReducedMotion(): Boolean
