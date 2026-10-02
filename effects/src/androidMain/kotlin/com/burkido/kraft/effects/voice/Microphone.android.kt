package com.burkido.kraft.effects.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberMicrophone(): Microphone {
    val context = LocalContext.current.applicationContext
    val mic = remember { Microphone() }
    val capture = remember { AndroidCapture(context, mic) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture.begin() else mic.state = MicrophoneState.Denied
    }
    SideEffect {
        mic.onStart = {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                capture.begin()
            } else {
                mic.state = MicrophoneState.Requesting
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        mic.onStop = { capture.end() }
    }
    DisposableEffect(capture) { onDispose { capture.end() } }
    return mic
}

/**
 * `AudioRecord` at 48 kHz mono float, from the UNPROCESSED source where the device has one (no
 * automatic gain or noise suppression), VOICE_RECOGNITION otherwise.
 */
private class AndroidCapture(private val context: Context, private val mic: Microphone) {
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun begin() {
        if (running) return
        val rate = 48_000
        val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBytes <= 0) {
            mic.state = MicrophoneState.Unsupported
            return
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val source = if (audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        val record = try {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(minBytes, 16 * 1024))
        } catch (e: SecurityException) {
            mic.state = MicrophoneState.Denied
            return
        } catch (e: IllegalArgumentException) {
            mic.state = MicrophoneState.Error
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            mic.state = MicrophoneState.Error
            return
        }
        mic.analyser.reset(rate)
        record.startRecording()
        running = true
        mic.state = MicrophoneState.Live
        Thread({
            val buffer = FloatArray(512)
            while (running) {
                val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (n > 0) mic.analyser.write(buffer, n) else if (n < 0) break
            }
            record.stop()
            record.release()
        }, "VoiceBeamMicrophone").start()
    }

    fun end() {
        running = false
        if (mic.state == MicrophoneState.Live || mic.state == MicrophoneState.Requesting) mic.state = MicrophoneState.Idle
    }
}
