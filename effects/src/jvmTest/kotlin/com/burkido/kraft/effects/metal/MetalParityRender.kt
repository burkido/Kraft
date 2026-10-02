package com.burkido.kraft.effects.metal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * The metal parity scenes of sites/beam/kraft-metal.html: each component on #171717 with a 40 dp
 * margin, density 2, the sheet frozen at t = 1 s. Output: effects/build/parity/metal/<scene>.png.
 */
class MetalParityRender {
    private val out = File("build/parity/metal").apply { mkdirs() }
    private val fonts = File("../shared/src/commonMain/composeResources/font")
    private val inter = FontFamily(
        Font(File(fonts, "inter_regular.ttf"), FontWeight.Normal),
        Font(File(fonts, "inter_medium.ttf"), FontWeight.Medium),
        Font(File(fonts, "inter_semibold.ttf"), FontWeight.SemiBold),
    )

    private fun render(name: String, w: Float, h: Float, time: Double = 1.0, content: @Composable () -> Unit) {
        val density = 2f
        val scene = ImageComposeScene(((w + 80) * density).toInt(), ((h + 80) * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalEffectFrozenTime provides time) {
                Box(Modifier.background(Color(0xFF171717)).padding(40.dp)) { content() }
            }
        }
        try {
            repeat(4) { scene.render(it * 16_000_000L) }
            File(out, "$name.png").writeBytes(scene.render(80_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Circle(glow: Boolean) {
        MetalFx(variant = MetalVariant.Circle, strength = 0.9f, innerShadow = true, glow = glow) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(16.dp)) {
                    val k = size.width / 24
                    val white = Color(0xFFF8F8F8)
                    drawLine(white, Offset(12 * k, 19 * k), Offset(12 * k, 5 * k), 2 * k, StrokeCap.Round)
                    drawLine(white, Offset(5 * k, 12 * k), Offset(12 * k, 5 * k), 2 * k, StrokeCap.Round)
                    drawLine(white, Offset(12 * k, 5 * k), Offset(19 * k, 12 * k), 2 * k, StrokeCap.Round)
                }
            }
        }
    }

    @Composable
    private fun Pill(glow: Boolean) {
        MetalFx(strength = 0.9f, glow = glow) {
            Box(Modifier.size(140.dp, 40.dp), contentAlignment = Alignment.Center) {
                BasicText("Upgrade to Pro", style = TextStyle(color = Color(0xFFF8F8F8), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = inter))
            }
        }
    }

    @Test
    fun scenes() {
        render("circle", 40f, 40f) { Circle(glow = false) }
        render("pill", 140f, 40f) { Pill(glow = false) }
        render("text", 38f, 29f) {
            MetalText(
                "Pro",
                TextStyle(color = Color(0xFFE2E2E2), fontSize = 24.sp, lineHeight = 28.8.sp, fontWeight = FontWeight.Medium, fontFamily = inter),
                strength = 0.9f,
            )
        }
        render("badge", 45f, 25f) {
            MetalBadge(textStyle = badgeText)
        }
    }

    /** The playground's pair: a search field catching the circle's metal (no glow, to isolate it). */
    @Test
    fun reflection() {
        render("reflect", 232f, 40f) {
            val anchor = rememberMetalAnchor()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MetalReflection(anchor, cornerRadius = 20.dp) {
                    Row(
                        Modifier.size(180.dp, 40.dp)
                            .background(Color(0xFF1D1D1D), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(44, 47, 54).copy(alpha = 0.52f), RoundedCornerShape(20.dp))
                            .padding(start = 13.dp, end = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(18.dp)) {
                            val k = size.width / 18
                            val c = Color(0xFF8B8B8B)
                            drawCircle(c, 6 * k, Offset(8 * k, 8 * k), style = Stroke(1.5f * k))
                            drawLine(c, Offset(16 * k, 16 * k), Offset(12.5f * k, 12.5f * k), 1.5f * k, StrokeCap.Round)
                        }
                        BasicText("Search", style = TextStyle(color = Color(0xFFF8F8F8).copy(alpha = 0.3f), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = inter))
                    }
                }
                MetalFx(variant = MetalVariant.Circle, strength = 0.81f, innerShadow = true, glow = false, anchor = anchor) {
                    Box(Modifier.size(40.dp))
                }
            }
        }
    }

    /** "Plan" catching the metal of "Pro" on its letterforms. */
    @Test
    fun plan() {
        render("plan", 95f, 29f) {
            val anchor = rememberMetalAnchor()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetalReflectionText(
                    anchor, "Plan",
                    TextStyle(color = Color(153, 153, 153).copy(alpha = 0.6f), fontSize = 24.sp, lineHeight = 28.8.sp, fontWeight = FontWeight.Medium, fontFamily = inter),
                    strength = 0.64f,
                )
                MetalText(
                    "Pro",
                    TextStyle(color = Color(0xFFE2E2E2), fontSize = 24.sp, lineHeight = 28.8.sp, fontWeight = FontWeight.Medium, fontFamily = inter),
                    strength = 0.9f,
                    anchor = anchor,
                )
            }
        }
    }

    /** With the glow, after two seconds of 60 Hz ticks (the web harness's `live=120`). */
    @Test
    fun live() {
        render("circle-live", 40f, 40f, time = 2.0) { Circle(glow = true) }
        render("pill-live", 140f, 40f, time = 2.0) { Pill(glow = true) }
        render("badge-live", 45f, 25f, time = 2.0) { MetalBadge(textStyle = badgeText) }
    }

    private val badgeText get() = TextStyle(fontSize = 12.222.sp, fontWeight = FontWeight.SemiBold, lineHeight = (12.222 * 1.4).sp, fontFamily = inter)
}
