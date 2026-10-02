package com.burkido.kraft.effects.avatars

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToInt

/** Parses an outline's SVG path data. */
internal fun parsePath(d: String): Path = PathParser().parsePathString(d).toPath()

/**
 * The baked forms, shared by every avatar (`formFor`): keyed by outline, texture size and depth,
 * built on a background thread the first time one is asked for (the smooth look stands in until
 * then), or right away for a still avatar.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object BotForms {
    private val forms = HashMap<String, BotForm>()
    private val pending = HashSet<String>()
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bumped when a bake lands, so avatars waiting on it redraw. */
    val landed = mutableIntStateOf(0)

    private fun key(outline: String, n: Int, halfDepth: Double) = "$outline|$n|${halfDepth.roundToInt()}"

    /** The form for [outline] (a type's key) at [n] and [halfDepth]; null while it bakes. */
    fun formFor(outline: String, pathData: String, n: Int, halfDepth: Double, sync: Boolean): BotForm? {
        collect()
        val id = key(outline, n, halfDepth)
        forms[id]?.let { return it }
        if (sync) {
            val form = bake(pathData, n, halfDepth)
            store(id, form)
            return form
        }
        if (pending.add(id)) {
            worker.launch {
                val form = bake(pathData, n, halfDepth)
                hand(id, form)
            }
        }
        return null
    }

    /** Bakes finished on the worker, waiting for the main thread to take them. */
    private val landedForms = AtomicReference<Map<String, BotForm>>(emptyMap())

    private fun hand(id: String, form: BotForm) {
        while (true) {
            val cur = landedForms.load()
            if (landedForms.compareAndSet(cur, cur + (id to form))) break
        }
        // A state write from a background thread only schedules the redraw.
        Snapshot.withMutableSnapshot { landed.intValue++ }
    }

    /** Moves bakes that finished on the worker into the cache (main thread). */
    fun collect() {
        val got = landedForms.exchange(emptyMap())
        for ((id, f) in got) {
            store(id, f)
            pending.remove(id)
        }
    }

    private fun store(id: String, form: BotForm) {
        if (forms.size >= 48) forms.clear()
        forms[id] = form
    }

    /** Bakes ahead of time (`warmBotAvatarPlastic`). */
    fun warm(outline: String, pathData: String, devicePx: Float, depth: Double) {
        formFor(outline, pathData, tierFor(devicePx), 15 * depth, sync = false)
    }

    /** `rasterize` + `buildForm`, off the main thread: the outline's own path, never a shared one. */
    private fun bake(pathData: String, n: Int, halfDepth: Double): BotForm {
        val cov = rasterize(parsePath(pathData), n)
        return buildForm(cov, n, halfDepth.toFloat())
    }

    /** Coverage raster of the outline: n × n over design units [−Pad, 100 + Pad], antialiased. */
    private fun rasterize(path: Path, n: Int): ByteArray {
        val image = ImageBitmap(n, n)
        val canvas = Canvas(image)
        val u = Span.toFloat() / n
        canvas.translate(Pad / u, Pad / u)
        canvas.scale(1 / u, 1 / u)
        canvas.drawPath(path, Paint().apply { color = Color.White; isAntiAlias = true })
        val px = IntArray(n * n)
        image.readPixels(px)
        return ByteArray(n * n) { i -> (px[i] ushr 24).toByte() }
    }
}
