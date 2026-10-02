package com.burkido.kraft.effects.avatars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.burkido.kraft.effects.core.LocalEffectFrozenTime
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * The bot frames of sites/beam/kraft-bots.html: a sim seeded 0.5, stepped n times at 1/60 s, drawn
 * at 96 dp and density 2 over #171717, framed like the web's overscanned canvas (144 dp square,
 * the box at 24, 33.6). Also writes each pose, to diff against the web sim's.
 * Output: effects/build/parity/bots/<name>.png and .pose.txt.
 */
class BotParityRender {
    private val out = File("build/parity/bots").apply { mkdirs() }

    private fun render(
        name: String,
        type: BotAvatarType,
        n: Int,
        state: BotAvatarState = BotAvatarState.Default,
        shading: BotAvatarShading = BotAvatarShading.Plastic,
    ) {
        val density = 2f
        val scene = ImageComposeScene((144 * density).toInt(), (144 * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalEffectFrozenTime provides n / 60.0) {
                Box(Modifier.size(144.dp).background(Color(0xFF171717)).padding(start = 24.dp, top = 33.6.dp)) {
                    BotAvatar(type = type, state = state, size = 96.dp, seed = 0.5, shading = shading, interactive = false)
                }
            }
        }
        try {
            repeat(3) { scene.render(it * 16_000_000L) }
            File(out, "$name.png").writeBytes(scene.render(64_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
        val sim = BotSim(0.5, state)
        repeat(n) { sim.update(1.0 / 60) }
        val p = sim.pose
        File(out, "$name.pose.txt").writeText(
            listOf(
                "yaw" to p.yaw, "pitch" to p.pitch, "roll" to p.roll, "x" to p.x, "y" to p.y, "sx" to p.sx, "sy" to p.sy,
                "blinkL" to p.blinkL, "lookX" to p.lookX, "lookY" to p.lookY, "breath" to p.breath, "laugh" to p.laugh,
                "whirl" to p.whirl, "whirlAngle" to p.whirlAngle, "w0" to p.w[0], "w1" to p.w[1], "w2" to p.w[2],
            ).joinToString("\n") { (k, v) -> "$k=$v" },
        )
    }

    @Test
    fun frames() {
        render("clover-rest", BotAvatarType.Clover, 0)
        render("star-rest", BotAvatarType.Star, 0)
        render("mech-rest", BotAvatarType.Mech, 0)
        render("clover-150", BotAvatarType.Clover, 150)
        render("flower-work-100", BotAvatarType.Flower, 100, BotAvatarState.Working)
        render("ghost-sleep-200", BotAvatarType.Ghost, 200, BotAvatarState.Sleeping)
        render("square-crisp", BotAvatarType.Square, 150, shading = BotAvatarShading.Crisp)
        render("circle-smooth", BotAvatarType.Circle, 150, shading = BotAvatarShading.Smooth)
    }
}
