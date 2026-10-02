package com.burkido.kraft.effects.voice

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One read of the microphone: RMS of the last window and the three voice bands, 0–1, before gain. */
internal class VoiceAnalysis {
    var rms = 0.0
    val bands = DoubleArray(3)
}

/**
 * A replica of the Web Audio `AnalyserNode` voice-glow reads (fftSize 1024, smoothing 0.5,
 * −100…−30 dB): the capture thread [write]s samples into a ring, and every frame [analyse] takes
 * the latest window's RMS (`getFloatTimeDomainData`) and the byte spectrum
 * (`getByteFrequencyData`: Blackman window, FFT scaled by 1/N, time smoothing, dB, byte), averaged
 * over the voice bands 80–300 / 300–2000 / 2000–6000 Hz.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class VoiceAnalyser(sampleRate: Int) {
    var sampleRate: Int = sampleRate
        private set

    private val ring = FloatArray(Size * 4)
    private val written = AtomicInt(0)

    private val window = DoubleArray(Size) { n ->
        val a = 0.16
        (1 - a) / 2 - 0.5 * cos(2 * PI * n / Size) + a / 2 * cos(4 * PI * n / Size)
    }
    private val re = DoubleArray(Size)
    private val im = DoubleArray(Size)
    private val smoothed = DoubleArray(Size / 2)
    private val bytes = IntArray(Size / 2)
    private val cosTable = DoubleArray(Size / 2) { cos(2 * PI * it / Size) }
    private val sinTable = DoubleArray(Size / 2) { sin(2 * PI * it / Size) }
    private val bitReverse = IntArray(Size) { i ->
        var r = 0
        var x = i
        repeat(Bits) { r = (r shl 1) or (x and 1); x = x shr 1 }
        r
    }

    /** Called from the capture thread with mono samples in −1…1. */
    fun write(samples: FloatArray, count: Int) {
        var w = written.load()
        for (i in 0 until count) {
            ring[w % ring.size] = samples[i]
            w = (w + 1) % (ring.size * 1024)
        }
        written.store(w)
    }

    fun reset(rate: Int) {
        sampleRate = rate
        written.store(0)
        ring.fill(0f)
        smoothed.fill(0.0)
    }

    fun analyse(out: VoiceAnalysis) {
        val end = written.load()
        var sum = 0.0
        for (n in 0 until Size) {
            val s = ring[((end - Size + n) % ring.size + ring.size) % ring.size].toDouble()
            sum += s * s
            re[bitReverse[n]] = s * window[n]
            im[bitReverse[n]] = 0.0
        }
        out.rms = sqrt(sum / Size)
        fft()
        for (k in 0 until Size / 2) {
            val mag = sqrt(re[k] * re[k] + im[k] * im[k]) / Size
            smoothed[k] = Smoothing * smoothed[k] + (1 - Smoothing) * mag
            val db = if (smoothed[k] > 0) 20 * log10(smoothed[k]) else -1000.0
            bytes[k] = floor(255 / (MaxDb - MinDb) * (db - MinDb)).toInt().coerceIn(0, 255)
        }
        val binHz = sampleRate.toDouble() / Size
        for (b in 0 until 3) {
            val from = max(0, floor(BandsHz[2 * b] / binHz).toInt())
            val to = min(Size / 2 - 1, ceil(BandsHz[2 * b + 1] / binHz).toInt())
            var acc = 0
            for (i in from..to) acc += bytes[i]
            out.bands[b] = if (to >= from) acc.toDouble() / (to - from + 1) / 255 else 0.0
        }
    }

    /** In-place iterative radix-2 FFT on the bit-reversed [re]/[im]. */
    private fun fft() {
        var len = 2
        while (len <= Size) {
            val half = len / 2
            val step = Size / len
            var i = 0
            while (i < Size) {
                for (j in 0 until half) {
                    val c = cosTable[j * step]
                    val s = -sinTable[j * step]
                    val a = i + j
                    val b = a + half
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                i += len
            }
            len *= 2
        }
    }

    private companion object {
        const val Size = 1024
        const val Bits = 10
        const val Smoothing = 0.5
        const val MinDb = -100.0
        const val MaxDb = -30.0
        val BandsHz = doubleArrayOf(80.0, 300.0, 300.0, 2000.0, 2000.0, 6000.0)
    }
}
