package com.burkido.kraft.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.burkido.kraft.effects.core.LocalEffectDarkTheme
import com.burkido.kraft.resources.Res
import com.burkido.kraft.resources.inter_display_medium
import com.burkido.kraft.resources.inter_medium
import com.burkido.kraft.resources.inter_regular
import com.burkido.kraft.resources.inter_semibold
import com.burkido.kraft.resources.roboto_mono_medium
import com.burkido.kraft.resources.roboto_mono_regular
import org.jetbrains.compose.resources.Font

/** The libraries.dev palette (`site.css`, dark theme — the site ships dark-only). */
object KraftColors {
    val Background = Color(0xFF121212)
    val Text = Color(0xFFFFFFFF)
    val TextMuted = Color(202, 202, 202, (0.7 * 255).toInt())
    val TextSubtle = Color(0xFF767676)
    val TextFaint = Color(0xFF8F8F8F)
    val FooterName = Color(0xFFE9E9E9)

    val Card = Color(0xFF181818)
    val CardHover = Color(0xFF1C1C1C)
    val Stage = Color(0xFF131313)
    val TileStage = Color(0xFF101010)
    val ExampleStage = Color(0xFF171717)
    val Controls = Color(0xFF1B1B1B)
    val Quote = Color(0xFF1A1A1A)
    val Menu = Color(0xFF202020)

    val RaisedPill = Color(0xFF2A2A2A)
    val RaisedPillHover = Color(0xFF323232)
    val RaisedPillPressed = Color(0xFF262626)

    val Chip = Color.White.copy(alpha = 0.07f)
    val ChipHover = Color.White.copy(alpha = 0.10f)
    val ChipPressed = Color.White.copy(alpha = 0.08f)

    val Cta = Color(0xFF0071FC)
    val CtaHover = Color(0xFF1A7DFF)
    val CtaPressed = Color(0xFF0063E0)
    val ProTint = Color(0, 113, 252, (0.18 * 255).toInt())
    val ProTintHover = Color(0, 113, 252, (0.26 * 255).toInt())
    val ProInk = Color(0xFF7DB4FF)

    val Icon = Color(0xFFEDEDED)
    val IconMuted = Color(237, 237, 237, (0.6 * 255).toInt())
    val Accent = Color(0xFF55CFFF)

    val Hairline = Color.White.copy(alpha = 0.08f)
}

/** `site.css` motion tokens. */
object KraftMotion {
    const val Stagger = 40
    const val Micro = 80
    const val Quick = 150
    const val Fast = 250
    const val Medium = 350
    const val Slow = 400
    const val VerySlow = 500

    /** `--ease-smooth-out`. */
    val SmoothOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** `--ease-bounce`. */
    val Bounce: Easing = CubicBezierEasing(0.34f, 1.36f, 0.64f, 1f)
    /** Hero tile lift: overshoots, then settles. */
    val Lift: Easing = CubicBezierEasing(0.34f, 1.85f, 0.64f, 1f)
    /** CSS `ease`. */
    val Ease: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    /** CSS `ease-in-out`. */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
}

/** Inter for UI, Inter Display standing in for the site's (commercial) Saans headings, Roboto Mono for code. */
@Immutable
class KraftFonts(val sans: FontFamily, val display: FontFamily, val mono: FontFamily)

val LocalKraftFonts = staticCompositionLocalOf<KraftFonts> { error("KraftTheme not provided") }

@Composable
fun KraftTheme(content: @Composable () -> Unit) {
    val fonts = KraftFonts(
        sans = FontFamily(
            Font(Res.font.inter_regular, FontWeight.Normal),
            Font(Res.font.inter_medium, FontWeight.Medium),
            Font(Res.font.inter_semibold, FontWeight.SemiBold),
        ),
        display = FontFamily(Font(Res.font.inter_display_medium, FontWeight.Medium)),
        mono = FontFamily(
            Font(Res.font.roboto_mono_regular, FontWeight.Normal),
            Font(Res.font.roboto_mono_medium, FontWeight.Medium),
        ),
    )
    CompositionLocalProvider(
        LocalKraftFonts provides fonts,
        // Effects left on `auto` follow the site: dark.
        LocalEffectDarkTheme provides true,
        content = content,
    )
}

/** CSS-like line boxes: half-leading above and below, no trimming. */
private val CssLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** A text style in CSS terms: px sizes map to sp one-to-one. */
fun cssText(
    family: FontFamily,
    size: Float,
    lineHeight: Float? = null,
    weight: FontWeight = FontWeight.Normal,
    color: Color = KraftColors.Text,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
    fontWeight = weight,
    color = color,
    letterSpacing = letterSpacing,
    lineHeightStyle = CssLineHeight,
)

/** `letter-spacing: -0.01em`. */
val TightTracking: TextUnit = (-0.01).em
