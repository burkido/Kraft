package com.burkido.kraft.effects.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * Whether an effect should be animating: some part of it is on screen and its window is at least
 * STARTED. RESUMED is deliberately not required — a desktop window loses RESUMED when it loses
 * focus, and effects must keep running while you compare them against the browser side by side.
 */
@Stable
class EffectActivity internal constructor(private val lifecycleState: State<Lifecycle.State>) {
    private var visible by mutableStateOf(false)

    val isActive: Boolean
        get() = visible && lifecycleState.value.isAtLeast(Lifecycle.State.STARTED)

    /** Attach to the effect's root node so visibility is tracked. */
    val modifier: Modifier = Modifier.onVisibilityChanged(minFractionVisible = 0f) { visible = it }
}

@Composable
fun rememberEffectActivity(): EffectActivity {
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return remember(lifecycleState) { EffectActivity(lifecycleState) }
}
