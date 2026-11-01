package com.burkido.kraft.effects.core

import android.os.Build

/** `RenderNode.setRenderEffect` arrived in Android 12; older versions ignore the effect. */
internal actual val renderEffectBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
