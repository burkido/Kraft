package com.burkido.kraft.effects.core

import androidx.compose.runtime.Composable

/** Desktop JVMs expose no reduce-motion preference. */
@Composable
actual fun rememberReducedMotion(): Boolean = false
