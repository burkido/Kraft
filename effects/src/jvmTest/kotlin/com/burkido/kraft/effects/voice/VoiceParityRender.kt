package com.burkido.kraft.effects.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.EffectTheme
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the voice parity cases on the web harness's scene (sites/beam/kraft-voice.html): a
 * 40 dp #171717 margin around the host card, density 2, the driver replayed to the same time.
 * Output: effects/build/parity/voice/<name>.png, compared with the web captures by parity_diff.
 */
class VoiceParityRender {
    private val out = File("build/parity/voice").apply { mkdirs() }

    private fun render(
        name: String,
        t: Double,
        type: VoiceBeamType = VoiceBeamType.Default,
        w: Float = 371f,
        h: Float = 104f,
        radius: Float = 20f,
        scale: Double? = null,
        processing: Boolean = false,
        variant: VoiceColorVariant = VoiceColorVariant.Colorful,
        theme: EffectTheme = EffectTheme.Dark,
    ) {
        val density = 2f
        val scene = ImageComposeScene(
            width = ((w + 80) * density).toInt(),
            height = ((h + 80) * density).toInt(),
            density = Density(density),
        ) {
            CompositionLocalProvider(LocalEffectFrozenTime provides t) {
                Box(Modifier.background(Color(0xFF171717)).padding(40.dp)) {
                    VoiceBeam(
                        type = type,
                        level = { s -> demoVoiceLevel(s) },
                        processing = processing,
                        colorVariant = variant,
                        theme = theme,
                        borderRadius = radius.dp,
                        scale = scale,
                        background = {
                            val shape = RoundedCornerShape(radius.dp)
                            Box(
                                Modifier
                                    .size(w.dp, h.dp)
                                    .background(Color(48, 48, 48).copy(alpha = 0.4f), shape)
                                    .innerShadow(shape, Shadow(radius = 42.4.dp, color = Color.White.copy(alpha = 0.02f)))
                                    .drawBehind {
                                        val r = radius.dp.toPx()
                                        val px = 1.dp.toPx()
                                        drawRoundRect(
                                            Color(44, 47, 54).copy(alpha = 0.52f),
                                            topLeft = Offset(px / 2, px / 2),
                                            size = androidx.compose.ui.geometry.Size(size.width - px, size.height - px),
                                            cornerRadius = CornerRadius(r - px / 2),
                                            style = Stroke(px),
                                        )
                                    },
                            )
                        },
                    )
                }
            }
        }
        try {
            repeat(2) { scene.render(it * 16_000_000L) }
            File(out, "$name.png").writeBytes(scene.render(48_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun cases() {
        render("default_t1", 1.0)
        render("default_t2", 2.2)
        render("default_t3", 3.4)
        render("default_t7", 6.9)
        render("default_proc", 2.0, processing = true)
        render("pill_t2", 2.2, type = VoiceBeamType.Pill, w = 150f, h = 44f, radius = 22f)
        render("mobile_t2", 2.2, type = VoiceBeamType.Mobile, w = 273f, h = 357f, radius = 44.88f, scale = 0.85)
        render("mono_t2", 2.2, variant = VoiceColorVariant.Mono)
        render("light_t2", 2.2, theme = EffectTheme.Light)
    }
}
