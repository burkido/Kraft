package com.burkido.kraft.film

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.burkido.kraft.designsystem.KraftTheme
import com.burkido.kraft.effects.core.EffectClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.math.roundToInt

/** The stage every shot is laid out on, in dp. Output resolution only changes the density. */
const val StageW = 960f
const val StageH = 540f

/** A finger or cursor on the stage, in stage dp. */
data class Touch(val x: Float, val y: Float, val down: Boolean = false, val alpha: Float = 1f)

/** One continuous take: rendered in its own scene, from its own t = 0. */
class Shot(
    val name: String,
    val seconds: Double,
    /** Rendered but not written, so effects are warm on the first frame. */
    val preroll: Double = 0.6,
    /** Where the invisible hand is at shot time t; drives real pointer events and the touch overlay. */
    val touch: ((Double) -> Touch?)? = null,
    /** Motion-blur sub-frames for this shot, when it moves faster than the quality's default allows. */
    val subframes: Int? = null,
    val content: @Composable () -> Unit,
)

@Stable
class FilmClock {
    var seconds by mutableDoubleStateOf(0.0)
}

val LocalFilmClock = staticCompositionLocalOf<FilmClock> { error("no clock") }

/** Shot time in seconds; recomposes every frame. */
@Composable
fun shotTime(): Double = LocalFilmClock.current.seconds

class Quality(
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    /** Sub-frames averaged per frame: motion blur over a 180° shutter. */
    val subframes: Int,
    val encoder: List<String>,
    val ext: String,
) {
    companion object {
        private val x264Intermediate = listOf("-c:v", "libx264", "-preset", "medium", "-crf", "10", "-pix_fmt", "yuv444p")
        val all = listOf(
            Quality("draft", 640, 360, 30, 1, listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-pix_fmt", "yuv420p"), "mp4"),
            Quality("preview", 960, 540, 30, 1, listOf("-c:v", "libx264", "-preset", "fast", "-crf", "18", "-pix_fmt", "yuv420p"), "mp4"),
            Quality("final", 1920, 1080, 60, 3, x264Intermediate, "mp4"),
            Quality("uhd", 3840, 2160, 60, 3, x264Intermediate, "mp4"),
        )

        fun named(name: String) = all.first { it.name == name }
    }
}

/** Renders [shot] up to [at] seconds (at 30 fps) and writes that one frame as a PNG. */
fun renderStill(shot: Shot, q: Quality, at: Double, out: File) {
    val tmp = File.createTempFile("still", ".mp4")
    val cut = Shot(shot.name, at + 1.0 / 30, shot.preroll, shot.touch, null, shot.content)
    renderShot(cut, Quality("still", q.width, q.height, 30, 1, listOf("-c:v", "libx264", "-crf", "8", "-pix_fmt", "yuv444p"), "mp4"), tmp)
    out.parentFile.mkdirs()
    ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-sseof", "-0.04", "-i", tmp.absolutePath, "-frames:v", "1", "-update", "1", out.absolutePath)
        .inheritIO().start().waitFor()
    tmp.delete()
}

/** Renders [shot] frame by frame into a video file, on a virtual clock so every run is identical. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalComposeUiApi::class)
fun renderShot(shot: Shot, q: Quality, out: File) {
    EffectClock.reset()
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)
    val clock = FilmClock()
    val density = q.width / StageW
    val scene = ImageComposeScene(q.width, q.height, Density(density), coroutineContext = dispatcher) {
        CompositionLocalProvider(LocalFilmClock provides clock) {
            KraftTheme {
                FilmFonts {
                    Box(Modifier.fillMaxSize()) {
                        shot.content()
                        shot.touch?.let { TouchOverlay(it) }
                    }
                }
            }
        }
    }

    out.parentFile.mkdirs()
    val ffmpeg = ProcessBuilder(
        listOf(
            "ffmpeg", "-y", "-loglevel", "error",
            "-f", "rawvideo", "-pix_fmt", "rgba", "-s", "${q.width}x${q.height}", "-r", "${q.fps}", "-i", "-",
        ) + q.encoder + listOf("-r", "${q.fps}", out.absolutePath),
    ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start()
    val sink = ffmpeg.outputStream.buffered(1 shl 22)

    val w = q.width
    val h = q.height
    val bitmap = Bitmap().apply { allocPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL)) }
    val steps = if (q.subframes > 1) maxOf(q.subframes, shot.subframes ?: 0) else 1
    val acc = if (steps > 1) IntArray(w * h * 4) else null
    val blended = if (steps > 1) ByteArray(w * h * 4) else null
    val pre = (shot.preroll * q.fps).roundToInt()
    val total = (shot.seconds * q.fps).roundToInt()
    var lastTouch: Touch? = null
    val started = System.nanoTime()

    try {
        for (f in -pre until total) {
            for (s in 0 until steps) {
                // Sub-frames span half the frame interval (a 180° shutter), centred on the frame.
                val offset = if (steps > 1) (s.toDouble() / (steps - 1) - 0.5) * 0.5 else 0.0
                val t = (f + offset) / q.fps
                clock.seconds = t
                val nanos = ((t + shot.preroll + 1.0) * 1e9).toLong()
                val millis = nanos / 1_000_000

                shot.touch?.let { track ->
                    val now = track(t)
                    val prev = lastTouch
                    if (now != null) {
                        val pos = Offset(now.x * density, now.y * density)
                        when {
                            prev == null -> scene.sendPointerEvent(PointerEventType.Enter, pos, timeMillis = millis)
                        }
                        when {
                            now.down && prev?.down != true -> scene.sendPointerEvent(PointerEventType.Press, pos, timeMillis = millis)
                            !now.down && prev?.down == true -> scene.sendPointerEvent(PointerEventType.Release, pos, timeMillis = millis)
                            else -> scene.sendPointerEvent(PointerEventType.Move, pos, timeMillis = millis)
                        }
                    } else if (prev != null) {
                        val pos = Offset(prev.x * density, prev.y * density)
                        if (prev.down) scene.sendPointerEvent(PointerEventType.Release, pos, timeMillis = millis)
                        scene.sendPointerEvent(PointerEventType.Exit, pos, timeMillis = millis)
                    }
                    lastTouch = now
                }

                val delta = millis - scheduler.currentTime
                if (delta > 0) scheduler.advanceTimeBy(delta)
                scheduler.runCurrent()
                val image = scene.render(nanos)
                scheduler.runCurrent()
                if (f >= 0) {
                    image.readPixels(bitmap)
                    val px = bitmap.readPixels()!!
                    if (acc == null) {
                        sink.write(px)
                    } else {
                        if (s == 0) acc.fill(0)
                        for (i in px.indices) acc[i] += px[i].toInt() and 0xFF
                        if (s == steps - 1) {
                            for (i in acc.indices) blended!![i] = ((acc[i] + steps / 2) / steps).toByte()
                            sink.write(blended!!)
                        }
                    }
                }
                image.close()
            }
            if (f >= 0 && f % q.fps == 0) {
                val secs = (System.nanoTime() - started) / 1e9
                println("  ${shot.name}: frame ${f + 1}/$total  (${"%.1f".format(secs)} s)")
            }
        }
    } finally {
        sink.close()
        ffmpeg.waitFor()
        scene.close()
    }
    println("  ${shot.name}: done in ${"%.1f".format((System.nanoTime() - started) / 1e9)} s -> $out")
}
