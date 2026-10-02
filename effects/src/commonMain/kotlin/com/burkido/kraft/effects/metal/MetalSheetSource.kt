package com.burkido.kraft.effects.metal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.burkido.kraft.effects.core.EffectClock
import com.burkido.kraft.effects.core.PixelSurface
import com.burkido.kraft.effects.core.parseCssColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The three bundled palettes (`presets.ts`); each carries a tuned dark and light material. */
enum class MetalPreset { Chromatic, Silver, Gold }

private fun rgba(hex: String): FloatArray = parseCssColor(hex).let { floatArrayOf(it.red, it.green, it.blue, it.alpha) }

/** Paper's `fullScreenPreset` minus its opaque backdrop, retinted per preset and theme. */
internal fun metalMaterial(preset: MetalPreset, dark: Boolean): MetalMaterial {
    fun base(
        tint: String,
        repetition: Float = 1.5f,
        softness: Float = 0.05f,
        shift: Float = 0.3f,
        speed: Float = 1f,
        opacity: Float = 1f,
    ) = MetalMaterial(
        repetition = repetition, softness = softness, shiftRed = shift, shiftBlue = shift, distortion = 0.1f,
        contour = 0.4f, angle = 90f, tint = rgba(tint), colorBack = rgba("#00000000"), speed = speed, shaderOpacity = opacity,
    )
    return when (preset) {
        MetalPreset.Chromatic -> if (dark) base("#88ccff2e", repetition = 2f, softness = 0.09f, shift = 0.75f) else base("#66b0ff99", shift = 0.6f)
        MetalPreset.Silver -> if (dark) base("#ffffff66", opacity = 0.88f) else base("#ffffff40")
        MetalPreset.Gold -> if (dark) base("#ffcc55cc", speed = 0.85f, opacity = 0.92f) else base("#f7d488aa")
    }
}

/** The canonical pill the sheet mapping is authored for (`CANONICAL_PILL_W/H`). */
internal const val CanonicalW = 140f
internal const val CanonicalH = 40f

/**
 * One shared liquid-metal sheet per material (metal-fx's single offscreen GL canvas): 96 dp at
 * up to 2× density, rendered on a background thread at most every 66 ms (15 fps) while any
 * instance showing it plays, and uploaded on the main thread. Every instance crops its own window
 * from it, so all the metal on screen moves as one material.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class MetalSheet(private val material: MetalMaterial, val size: Int) {
    val surface = PixelSurface(size, size)

    /** The last uploaded frame, owned by the main thread (frozen copies and reflections read it). */
    val front = ByteArray(size * size * 4)

    /** A copy of [front] refreshed at most every 1.5 s — the web's `readPixels` cadence, which the glow's luminance hunt reads. */
    val samples = ByteArray(size * size * 4)

    /** Incremented on every upload. */
    var frames = 0
        private set

    val shaderOpacity: Float get() = material.shaderOpacity

    private val engine = LiquidMetalSheet(size, material)
    private val work = ByteArray(size * size * 4)

    // The sheet outlives every instance, so its renders must too: one cancelled mid-flight would
    // leave the state at Rendering and freeze all the metal on screen.
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = AtomicInt(Idle)
    private var lastRenderNanos = Long.MIN_VALUE
    private var lastSampleNanos = Long.MIN_VALUE

    /**
     * One main-thread step: upload a finished render, and start the next one once 66 ms have
     * passed. Cheap to call from every instance on every frame.
     */
    fun tick(nowNanos: Long) {
        if (state.load() == Ready) {
            publish(nowNanos)
            state.store(Idle)
        }
        if (state.load() == Idle && (lastRenderNanos == Long.MIN_VALUE || nowNanos - lastRenderNanos >= FrameIntervalNanos)) {
            lastRenderNanos = nowNanos
            state.store(Rendering)
            val t = EffectClock.secondsAt(nowNanos) * material.speed
            worker.launch {
                engine.render(t, work)
                state.store(Ready)
            }
        }
    }

    /** Renders and uploads one frame synchronously — for frozen snapshots. */
    fun renderNow(timeSeconds: Double) {
        if (state.load() == Rendering) return
        engine.render(timeSeconds * material.speed, work)
        publish(Long.MIN_VALUE)
    }

    private fun publish(nowNanos: Long) {
        work.copyInto(front)
        surface.commit(front)
        frames++
        if (lastSampleNanos == Long.MIN_VALUE || nowNanos == Long.MIN_VALUE || nowNanos - lastSampleNanos >= SampleIntervalNanos) {
            lastSampleNanos = nowNanos
            front.copyInto(samples)
        }
    }

    private companion object {
        const val Idle = 0
        const val Rendering = 1
        const val Ready = 2
        const val FrameIntervalNanos = 66_000_000L
        const val SampleIntervalNanos = 1_500_000_000L
    }
}

/**
 * The frame a paused instance keeps showing (`freezeFrame`), or null while it plays. A paused
 * instance whose sheet has not rendered yet ticks it until the first frame lands, so it never
 * shows the frames that playing instances keep uploading to the shared sheet. Snapshots (frozen
 * time) render their own static frame and need none.
 */
@Composable
internal fun rememberFrozenFrame(sheet: MetalSheet, paused: Boolean, snapshot: Boolean): State<PixelSurface?> {
    val frozen: MutableState<PixelSurface?> = remember { mutableStateOf(null) }
    LaunchedEffect(paused, sheet, snapshot) {
        if (!paused || snapshot) {
            frozen.value = null
            return@LaunchedEffect
        }
        while (sheet.frames == 0) withFrameNanos { sheet.tick(it) }
        frozen.value = PixelSurface(sheet.size, sheet.size).also { it.commit(sheet.front.copyOf()) }
    }
    return frozen
}

internal object MetalSheets {
    private val cache = HashMap<Triple<MetalPreset, Boolean, Int>, MetalSheet>()

    fun get(preset: MetalPreset, dark: Boolean, density: Float): MetalSheet {
        val size = (96 * min(2f, max(1f, density))).roundToInt()
        return cache.getOrPut(Triple(preset, dark, size)) { MetalSheet(metalMaterial(preset, dark), size) }
    }
}

/**
 * An instance's window onto the sheet (`copyShaderToInstance`): a [w]×[h] dp box at
 * [shaderScale] sees `w / (140·scale)` of the sheet's width and `h / (40·scale)` of its height,
 * around the centre.
 */
internal class SheetWindow(sheetSize: Int, w: Float, h: Float, shaderScale: Float) {
    // Each axis maps its own canonical length onto the whole sheet, so the 140×40 reference pill
    // sees a square window (the texture runs 3.5× wider than tall) and a circle a tall narrow one.
    val srcW = min(sheetSize.toFloat(), sheetSize * w / (CanonicalW * shaderScale))
    val srcH = min(sheetSize.toFloat(), sheetSize * h / (CanonicalH * shaderScale))
    val sx = max(0f, (sheetSize - srcW) / 2)
    val sy = max(0f, (sheetSize - srcH) / 2)
    private val kx = srcW / max(1f, w)
    private val ky = srcH / max(1f, h)
    private val size = sheetSize

    /** The sheet pixel under the box point ([x], [y]) dp. */
    fun sheetX(x: Float): Int = (sx + x * kx).roundToInt().coerceIn(0, size - 1)
    fun sheetY(y: Float): Int = (sy + y * ky).roundToInt().coerceIn(0, size - 1)
}

/** Mean luminance of the (2·[radius]+1)² sheet pixels around a box point (`sampleShaderLumAt`). */
internal fun sampleLum(buf: ByteArray, size: Int, window: SheetWindow, x: Float, y: Float, radius: Int = 2): Float {
    val bx = window.sheetX(x)
    val by = window.sheetY(y)
    var lum = 0f
    var count = 0
    for (py in max(0, by - radius)..min(size - 1, by + radius)) {
        for (px in max(0, bx - radius)..min(size - 1, bx + radius)) {
            val i = (py * size + px) * 4
            lum += (0.2126f * (buf[i].toInt() and 0xFF) + 0.7152f * (buf[i + 1].toInt() and 0xFF) + 0.0722f * (buf[i + 2].toInt() and 0xFF)) / 255f
            count++
        }
    }
    return if (count > 0) lum / count else 0f
}

/** Mean colour (0..255) of the same window (`sampleShaderRGBAt`). */
internal fun sampleRgb(buf: ByteArray, size: Int, window: SheetWindow, x: Float, y: Float, out: FloatArray, radius: Int = 2) {
    val bx = window.sheetX(x)
    val by = window.sheetY(y)
    var r = 0f; var g = 0f; var b = 0f
    var count = 0
    for (py in max(0, by - radius)..min(size - 1, by + radius)) {
        for (px in max(0, bx - radius)..min(size - 1, bx + radius)) {
            val i = (py * size + px) * 4
            r += buf[i].toInt() and 0xFF
            g += buf[i + 1].toInt() and 0xFF
            b += buf[i + 2].toInt() and 0xFF
            count++
        }
    }
    if (count == 0) { out[0] = 255f; out[1] = 255f; out[2] = 255f; return }
    out[0] = r / count; out[1] = g / count; out[2] = b / count
}
