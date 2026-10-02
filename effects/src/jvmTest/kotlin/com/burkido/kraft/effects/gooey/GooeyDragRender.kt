package com.burkido.kraft.effects.gooey

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Drags a liquid slider thumb headlessly and writes frames mid-drag: the tail must form. */
class GooeyDragRender {
    private val out = File("build/parity/gooey-drag").apply { mkdirs() }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun dragThumb() {
        val density = 2f
        val scene = ImageComposeScene((360 * density).toInt(), (200 * density).toInt(), Density(density)) {
            var x by remember { mutableFloatStateOf(20f) }
            Box(Modifier.background(Color(0xFF171717)).padding(60.dp)) {
                Liquid(Modifier.size(240.dp, 80.dp), fill = Color(0xFF525252)) {
                    MoveItem(
                        radius = 12.dp,
                        modifier = Modifier
                            .offset((14 + x).dp, 30.dp)
                            .size(24.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dx ->
                                    change.consume()
                                    x = (x + dx / density).coerceIn(0f, 188f)
                                }
                            },
                        tuning = MoveTuning(stretch = 0.6f, trail = 0.35f),
                    ) { Box(Modifier.size(24.dp)) }
                }
            }
        }
        try {
            var t = 0L
            fun frame() { scene.render(t); t += 16_000_000L }
            repeat(3) { frame() }
            // Thumb centre: padding 60 + offset 14 + 20 + 12 = 106 dp across, 60 + 30 + 12 = 102 dp down.
            var px = 106f * density
            val py = 102f * density
            scene.sendPointerEvent(PointerEventType.Press, Offset(px, py))
            frame()
            repeat(8) {
                px += 14f * density
                scene.sendPointerEvent(PointerEventType.Move, Offset(px, py))
                frame()
            }
            File(out, "mid-drag.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.sendPointerEvent(PointerEventType.Release, Offset(px, py))
            repeat(40) { frame() }
            File(out, "settled.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
