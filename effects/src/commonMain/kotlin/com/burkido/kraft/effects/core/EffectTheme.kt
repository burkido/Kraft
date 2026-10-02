package com.burkido.kraft.effects.core

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/** The web libraries' `theme` prop: `auto` follows the host, `dark` / `light` pin one. */
enum class EffectTheme { Auto, Light, Dark }

/** What [EffectTheme.Auto] resolves to below this point; `null` falls back to the system setting. */
val LocalEffectDarkTheme = compositionLocalOf<Boolean?> { null }

@Composable
@ReadOnlyComposable
fun EffectTheme.isDark(): Boolean = when (this) {
    EffectTheme.Dark -> true
    EffectTheme.Light -> false
    EffectTheme.Auto -> LocalEffectDarkTheme.current ?: isSystemInDarkTheme()
}
