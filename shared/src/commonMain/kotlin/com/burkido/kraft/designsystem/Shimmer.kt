package com.burkido.kraft.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.burkido.kraft.effects.core.rememberEffectActivity
import com.burkido.kraft.effects.core.rememberEffectTime
import com.burkido.kraft.effects.core.rememberReducedMotion

/**
 * `.t-shimmer`: text in its dim base colour with a bright band sweeping across it.
 *
 * The site paints a copy of the text through a `background-clip: text` gradient four times the
 * text's width (transparent · highlight at 50 % · transparent, ±10 %) and slides it from
 * `background-position: 100%` to `0%` every [periodSeconds]. In the text's own coordinates the
 * band's centre therefore travels linearly from −W to 2W with a half-width of 0.4 W. Reduced
 * motion stops the animation where CSS leaves it: band off to the right, text plain.
 */
@Composable
fun ShimmerText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    highlight: Color = Color.White,
    periodSeconds: Double = 2.0,
) {
    val activity = rememberEffectActivity()
    val reduced = rememberReducedMotion()
    val time = rememberEffectTime(running = activity.isActive && !reduced)
    Box(modifier.then(activity.modifier)) {
        BasicText(text, style = style, softWrap = false)
        if (!reduced) {
            BasicText(
                text,
                style = style.copy(color = highlight),
                softWrap = false,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val w = size.width
                        val p = (time.seconds / periodSeconds).let { it - kotlin.math.floor(it) }.toFloat()
                        val centre = -w + 3f * w * p
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to Color.Black,
                                1f to Color.Transparent,
                                startX = centre - 0.4f * w,
                                endX = centre + 0.4f * w,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
    }
}
