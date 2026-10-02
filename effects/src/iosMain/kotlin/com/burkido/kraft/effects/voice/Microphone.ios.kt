package com.burkido.kraft.effects.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.setActive
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberMicrophone(): Microphone {
    val mic = remember { Microphone() }
    val capture = remember { IosCapture(mic) }
    SideEffect {
        mic.onStart = { capture.begin() }
        mic.onStop = { capture.end() }
    }
    DisposableEffect(capture) { onDispose { capture.end() } }
    return mic
}

/**
 * `AVAudioEngine`'s input tapped at 1024 frames. The session runs in measurement mode, which
 * turns off the system's voice processing (gain, noise and echo) like the web hook's constraints.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosCapture(private val mic: Microphone) {
    private var engine: AVAudioEngine? = null

    @Suppress("DEPRECATION")
    fun begin() {
        if (engine != null) return
        val session = AVAudioSession.sharedInstance()
        when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> startEngine()
            AVAudioSessionRecordPermissionDenied -> mic.state = MicrophoneState.Denied
            else -> {
                mic.state = MicrophoneState.Requesting
                session.requestRecordPermission { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) startEngine() else mic.state = MicrophoneState.Denied
                    }
                }
            }
        }
    }

    private fun startEngine() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeMeasurement, AVAudioSessionCategoryOptionMixWithOthers, null)
        session.setActive(true, null)
        val engine = AVAudioEngine()
        val input = engine.inputNode
        val format = input.outputFormatForBus(0u)
        if (format.sampleRate <= 0.0) {
            mic.state = MicrophoneState.Unsupported
            return
        }
        mic.analyser.reset(format.sampleRate.toInt())
        val scratch = FloatArray(4096)
        input.installTapOnBus(0u, 1024u, format) { buffer, _ ->
            val pcm = buffer ?: return@installTapOnBus
            val data = pcm.floatChannelData?.get(0) ?: return@installTapOnBus
            val n = minOf(pcm.frameLength.toInt(), scratch.size)
            for (i in 0 until n) scratch[i] = data[i]
            mic.analyser.write(scratch, n)
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
            mic.state = MicrophoneState.Error
            return
        }
        this.engine = engine
        mic.state = MicrophoneState.Live
    }

    fun end() {
        engine?.let {
            it.inputNode.removeTapOnBus(0u)
            it.stop()
        }
        engine = null
        if (mic.state == MicrophoneState.Live || mic.state == MicrophoneState.Requesting) mic.state = MicrophoneState.Idle
    }
}
