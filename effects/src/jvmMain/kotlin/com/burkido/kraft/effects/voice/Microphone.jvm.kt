package com.burkido.kraft.effects.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

@Composable
actual fun rememberMicrophone(): Microphone {
    val mic = remember { Microphone() }
    val capture = remember { JvmCapture(mic) }
    SideEffect {
        mic.onStart = { capture.begin() }
        mic.onStop = { capture.end() }
    }
    DisposableEffect(capture) { onDispose { capture.end() } }
    return mic
}

/** The default input line at 48 kHz, 16-bit mono, read on its own thread. */
private class JvmCapture(private val mic: Microphone) {
    @Volatile private var running = false
    @Volatile private var line: TargetDataLine? = null

    fun begin() {
        if (running) return
        running = true
        mic.state = MicrophoneState.Requesting
        Thread({
            val format = AudioFormat(48_000f, 16, 1, true, false)
            val opened = try {
                AudioSystem.getTargetDataLine(format).also {
                    it.open(format, 48_000 / 10 * 2)
                    it.start()
                }
            } catch (e: LineUnavailableException) {
                null
            } catch (e: SecurityException) {
                running = false
                mic.state = MicrophoneState.Denied
                return@Thread
            } catch (e: IllegalArgumentException) {
                null
            }
            if (opened == null) {
                running = false
                mic.state = MicrophoneState.Unsupported
                return@Thread
            }
            line = opened
            mic.analyser.reset(48_000)
            mic.state = MicrophoneState.Live
            val bytes = ByteArray(1024)
            val samples = FloatArray(512)
            while (running) {
                val n = opened.read(bytes, 0, bytes.size)
                if (n <= 0) continue
                val count = n / 2
                for (i in 0 until count) {
                    val lo = bytes[2 * i].toInt() and 0xFF
                    val hi = bytes[2 * i + 1].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort() / 32768f
                }
                mic.analyser.write(samples, count)
            }
            opened.stop()
            opened.close()
        }, "VoiceBeamMicrophone").apply { isDaemon = true }.start()
    }

    fun end() {
        running = false
        line = null
        if (mic.state == MicrophoneState.Live || mic.state == MicrophoneState.Requesting) mic.state = MicrophoneState.Idle
    }
}
