package com.burkido.kraft.effects.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where a [Microphone] is (`useMicrophone`'s `state`). */
enum class MicrophoneState { Idle, Requesting, Live, Denied, Unsupported, Error }

/**
 * A microphone for [VoiceBeam], from [rememberMicrophone]. Call [start] from a user action — it
 * asks for the permission first where the platform needs one — and [stop] to release the device.
 * The audio is analysed on the device and never recorded or played back.
 */
@Stable
class Microphone internal constructor() {
    var state: MicrophoneState by mutableStateOf(MicrophoneState.Idle)
        internal set

    internal val analyser = VoiceAnalyser(48_000)
    internal var onStart: () -> Unit = {}
    internal var onStop: () -> Unit = {}

    fun start() {
        if (state == MicrophoneState.Live || state == MicrophoneState.Requesting) return
        onStart()
    }

    fun stop() = onStop()
}

/**
 * A [Microphone] tied to this composition: released when it leaves. Voice processing (echo
 * cancellation, noise suppression, auto gain) is avoided where the platform allows, so the glow
 * sees the voice's real dynamics.
 */
@Composable
expect fun rememberMicrophone(): Microphone
