package com.burkido.kraft.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File

// ---- Palette ----------------------------------------------------------------------------------

object FilmColors {
    val Black = Color(0xFF080809)
    val Ink = Color(0xFF0E0E10)
    val Card = Color(0xFF151517)
    val CardHi = Color(0xFF1B1B1E)
    val Hairline = Color.White.copy(alpha = 0.07f)
    val Text = Color(0xFFF4F4F5)
    val Muted = Color(0xFF9A9AA0)
    val Faint = Color(0xFF5E5E64)
    val Accent = Color(0xFF55CFFF)
}

// ---- Type -------------------------------------------------------------------------------------

@Immutable
class FilmFontSet(val display: FontFamily, val sans: FontFamily, val mono: FontFamily)

val LocalFilmFonts = staticCompositionLocalOf<FilmFontSet> { error("no fonts") }

private val fontDir = File("shared/src/commonMain/composeResources/font")

@Composable
fun FilmFonts(content: @Composable () -> Unit) {
    val set = remember {
        FilmFontSet(
            display = FontFamily(Font(File(fontDir, "inter_display_medium.ttf"), FontWeight.Medium)),
            sans = FontFamily(
                Font(File(fontDir, "inter_regular.ttf"), FontWeight.Normal),
                Font(File(fontDir, "inter_medium.ttf"), FontWeight.Medium),
                Font(File(fontDir, "inter_semibold.ttf"), FontWeight.SemiBold),
            ),
            mono = FontFamily(
                Font(File(fontDir, "roboto_mono_regular.ttf"), FontWeight.Normal),
                Font(File(fontDir, "roboto_mono_medium.ttf"), FontWeight.Medium),
            ),
        )
    }
    CompositionLocalProvider(LocalFilmFonts provides set, content = content)
}

@Composable
fun display(size: Float, color: Color = FilmColors.Text, tracking: TextUnit = (-0.035).em): TextStyle =
    TextStyle(fontFamily = LocalFilmFonts.current.display, fontWeight = FontWeight.Medium, fontSize = size.sp, lineHeight = (size * 1.08f).sp, color = color, letterSpacing = tracking)

@Composable
fun sans(size: Float, color: Color = FilmColors.Muted, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = (-0.01).em): TextStyle =
    TextStyle(fontFamily = LocalFilmFonts.current.sans, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4f).sp, color = color, letterSpacing = tracking)

@Composable
fun mono(size: Float, color: Color = FilmColors.Faint, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontFamily = LocalFilmFonts.current.mono, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.5f).sp, color = color, letterSpacing = 0.04.em)

// ---- Motion primitives ------------------------------------------------------------------------

/**
 * Words rise out of a blur, one after another — the premium title reveal. [start] is shot time;
 * [out] (if set) blurs them away again.
 */
@Composable
fun RevealText(
    text: String,
    style: TextStyle,
    start: Double,
    modifier: Modifier = Modifier,
    stagger: Double = 0.07,
    dur: Double = 1.1,
    rise: Dp = 14.dp,
    blur: Float = 14f,
    out: Double? = null,
    outDur: Double = 0.6,
) {
    val t = shotTime()
    val words = remember(text) { text.split(" ") }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        words.forEachIndexed { i, word ->
            val p = ramp(t, start + i * stagger, dur, Ease::expoOut)
            val o = if (out != null) ramp(t, out + i * stagger * 0.5, outDur, Ease::cubicInOut) else 0f
            val vis = p * (1 - o)
            val b = blur * (1 - p) + blur * o
            BasicText(
                if (i < words.lastIndex) "$word " else word,
                style = style,
                modifier = Modifier.graphicsLayer {
                    alpha = vis
                    translationY = (rise.toPx() * (1 - p)) - rise.toPx() * 0.6f * o
                    if (b > 0.05f) renderEffect = BlurEffect(b * density / 2, b * density / 2, TileMode.Decal)
                },
            )
        }
    }
}

/** Fades and un-blurs [content] in over [dur] from [start]. */
@Composable
fun FadeIn(start: Double, dur: Double = 0.9, blur: Float = 10f, rise: Dp = 0.dp, scaleFrom: Float = 1f, out: Double? = null, outDur: Double = 0.5, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = shotTime()
    val p = ramp(t, start, dur, Ease::expoOut)
    val o = if (out != null) ramp(t, out, outDur, Ease::cubicInOut) else 0f
    Box(
        modifier.graphicsLayer {
            alpha = p * (1 - o)
            translationY = rise.toPx() * (1 - p)
            val s = lerp(scaleFrom, 1f, p)
            scaleX = s; scaleY = s
            val b = blur * (1 - p) + blur * o
            if (b > 0.05f) renderEffect = BlurEffect(b * density / 2, b * density / 2, TileMode.Decal)
        },
        content = content,
    )
}

/** A virtual camera over the whole stage: push-in, pan, and a slight 3D tilt. */
@Composable
fun Camera(
    scale: Float = 1f,
    x: Float = 0f,
    y: Float = 0f,
    rotX: Float = 0f,
    rotY: Float = 0f,
    rotZ: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = x * density; translationY = y * density
            rotationX = rotX; rotationY = rotY; rotationZ = rotZ
            cameraDistance = 14f * density
        },
        content = content,
    )
}

// ---- Set dressing -----------------------------------------------------------------------------

/** Near-black stage with a soft key light from above. */
@Composable
fun Backdrop(light: Float = 1f, tint: Color = Color.White, lightX: Float = 0.5f, lightY: Float = -0.15f) {
    Canvas(Modifier.fillMaxSize().background(FilmColors.Black)) {
        val c = Offset(size.width * lightX, size.height * lightY)
        drawRect(
            Brush.radialGradient(
                listOf(tint.copy(alpha = 0.075f * light), tint.copy(alpha = 0.02f * light), Color.Transparent),
                center = c,
                radius = size.width * 0.75f,
            ),
        )
    }
}

/** The chapter card: "01  Thinking orbs" and a one-line promise, bottom-left. */
@Composable
fun Chapter(no: String, title: String, line: String, start: Double, end: Double) {
    val t = shotTime()
    Box(Modifier.fillMaxSize().padding(start = 56.dp, bottom = 50.dp), contentAlignment = Alignment.BottomStart) {
        Column {
            val bar = ramp(t, start, 0.9, Ease::expoOut) * (1 - ramp(t, end - 0.45, 0.45, Ease::cubicInOut))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(no, style = mono(11f, FilmColors.Muted), modifier = Modifier.graphicsLayer { alpha = bar })
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(28.dp * bar).height(1.dp).background(FilmColors.Muted.copy(alpha = 0.6f)))
            }
            Spacer(Modifier.height(10.dp))
            RevealText(title, display(30f), start + 0.1, stagger = 0.06, out = end - 0.55, outDur = 0.45)
            Spacer(Modifier.height(6.dp))
            RevealText(line, sans(15f, FilmColors.Muted), start + 0.3, stagger = 0.03, rise = 8.dp, out = end - 0.5, outDur = 0.45)
        }
    }
}

/** The invisible hand: a soft touch indicator, like a product-demo capture. */
@Composable
fun TouchOverlay(track: (Double) -> Touch?) {
    val t = shotTime()
    val touch = track(t) ?: return
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(touch.x * density, touch.y * density)
        val r = (if (touch.down) 15f else 18f) * density
        drawCircle(Color.White.copy(alpha = (if (touch.down) 0.30f else 0.16f) * touch.alpha), r, c)
        drawCircle(Color.White.copy(alpha = 0.55f * touch.alpha), r, c, style = Stroke(1.2f * density))
    }
}

/** A rounded card surface with a hairline and a soft drop shadow. */
fun Modifier.card(radius: Dp = 20.dp, color: Color = FilmColors.Card): Modifier = this
    .graphicsLayer {
        shape = androidx.compose.foundation.shape.RoundedCornerShape(radius)
        clip = true
        shadowElevation = 0f
    }
    .background(color)

@Composable
fun CenterText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text, style = style.copy(textAlign = TextAlign.Center), modifier = modifier)
}

@Composable
fun Spacer(size: Dp) = Spacer(Modifier.size(size))
