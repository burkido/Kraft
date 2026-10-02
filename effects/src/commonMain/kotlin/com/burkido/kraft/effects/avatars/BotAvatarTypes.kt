package com.burkido.kraft.effects.avatars

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The eighteen body shapes (`BotAvatarType`). */
enum class BotAvatarType { Clover, Flower, Triangle, Square, Blob, Ghost, Circle, Drop, Star, Droid, Mech, Alien, Hexagon, Cat, Cloud, Pill, Pebble, Puddle }

/** What the face is made of: the eyes alone, or eyes and a mouth that changes with the state. */
enum class BotAvatarFace { Eyes, Mouth }

/** What the bot is doing: idle, working (hopping, spinning) or sleeping. */
enum class BotAvatarState { Default, Working, Sleeping }

/**
 * How the body is lit: `Plastic` (a glossy material shaded per texel), `Crisp` (a lit rim with a
 * clean edge), `Smooth` (a soft shadow and highlight over the form) or `Flat` (depth, no light).
 */
enum class BotAvatarShading { Plastic, Crisp, Smooth, Flat }

/** How a jump's landing squash, ground hold and rise play out. */
enum class BotSquashEase { Sharp, Pulse, Soft, Bouncy }

/** A type's defaults (`BotAvatarPreset`): its label, colour and where the face sits on it. */
class BotAvatarPreset internal constructor(
    val label: String,
    val color: Color,
    val face: BotAvatarFace,
    /** Where the face sits, in the 100 × 100 body box. */
    val faceX: Float,
    val faceY: Float,
    /** Shapes with a small middle wear a smaller face. */
    val faceScale: Float,
)

private fun preset(label: String, hex: Long, faceX: Float, faceY: Float, faceScale: Float) =
    BotAvatarPreset(label, Color(hex), BotAvatarFace.Eyes, faceX, faceY, faceScale)

/** `botAvatarPresets`. */
val BotAvatarType.preset: BotAvatarPreset
    get() = when (this) {
        BotAvatarType.Clover -> preset("Clover", 0xFF35B8FF, 50f, 50f, 1f)
        BotAvatarType.Flower -> preset("Flower", 0xFF2FCB7A, 50f, 51f, 0.95f)
        BotAvatarType.Triangle -> preset("Triangle", 0xFFDC48FF, 50f, 61f, 0.9f)
        BotAvatarType.Square -> preset("Square", 0xFF35B8FF, 50f, 50f, 1f)
        BotAvatarType.Blob -> preset("Blob", 0xFF2FCB7A, 49.5f, 50f, 1f)
        BotAvatarType.Ghost -> preset("Ghost", 0xFFF4F2FA, 50f, 48f, 0.95f)
        BotAvatarType.Circle -> preset("Circle", 0xFF9A62FF, 50f, 50f, 1f)
        BotAvatarType.Drop -> preset("Drop", 0xFF1ED3C6, 50f, 62f, 0.9f)
        BotAvatarType.Star -> preset("Star", 0xFFFFD32B, 50f, 52f, 0.82f)
        BotAvatarType.Droid -> preset("Droid", 0xFFD5DBEA, 50f, 60f, 0.95f)
        BotAvatarType.Mech -> preset("Mech", 0xFF95A6C4, 50f, 59f, 1f)
        BotAvatarType.Alien -> preset("Alien", 0xFF9BE85A, 50f, 45f, 1.05f)
        BotAvatarType.Hexagon -> preset("Hexagon", 0xFFFF2A2A, 50f, 50f, 0.95f)
        BotAvatarType.Cat -> preset("Cat", 0xFFFF8C42, 50f, 58f, 1f)
        BotAvatarType.Cloud -> preset("Cloud", 0xFFCFE6FF, 50f, 58f, 0.95f)
        BotAvatarType.Pill -> preset("Pill", 0xFF7B77F0, 50f, 50f, 0.9f)
        BotAvatarType.Pebble -> preset("Pebble", 0xFF2FCB7A, 50f, 50f, 0.95f)
        BotAvatarType.Puddle -> preset("Puddle", 0xFFFF2A2A, 50f, 50f, 0.95f)
    }

/** `stateLabels`: how a state reads in the default content description. */
internal val BotAvatarState.label: String
    get() = when (this) {
        BotAvatarState.Default -> "idle"
        BotAvatarState.Working -> "working"
        BotAvatarState.Sleeping -> "sleeping"
    }

// ── Colour (`color.ts`) ───────────────────────────────────────────────────────────────────

internal val DarkInk = Color(0xFF1E1A33)
internal val LightInk = Color(0xFFF7F5F2)

/** Relative luminance (WCAG), 0..1. */
internal fun luminance(c: Color): Double {
    fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

/** Face ink for a body colour: dark ink, or light ink on a dark body. */
fun autoInk(color: Color): Color = if (luminance(color) < 0.13) LightInk else DarkInk

/** A colour in HSL, each 0..1 — the space `shade()` works in. */
internal class Hsl(val h: Double, val s: Double, val l: Double) {
    fun toColor(alpha: Float = 1f): Color {
        if (s == 0.0) return Color(l.toFloat(), l.toFloat(), l.toFloat(), alpha)
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun f(t0: Double): Double {
            val t = ((t0 % 1) + 1) % 1
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 0.5 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        return Color(f(h + 1.0 / 3).toFloat().coerceIn(0f, 1f), f(h).toFloat().coerceIn(0f, 1f), f(h - 1.0 / 3).toFloat().coerceIn(0f, 1f), alpha)
    }

    /** `mixCss`: the three numbers interpolated straight (hue included, no wrap). */
    fun mix(o: Hsl, t: Double) = Hsl(h + (o.h - h) * t, s + (o.s - s) * t, l + (o.l - l) * t)

    /** As `hsl()` prints it: hue to a tenth of a degree, s and l to a tenth of a percent. */
    fun rounded() = Hsl(kotlin.math.round(h * 3600) / 3600, kotlin.math.round(s * 1000) / 1000, kotlin.math.round(l * 1000) / 1000)
}

internal fun Color.toHsl(): Hsl {
    // The web parses 8-bit channels.
    val r = (red * 255).toDouble().let { kotlin.math.round(it) } / 255
    val g = (green * 255).toDouble().let { kotlin.math.round(it) } / 255
    val b = (blue * 255).toDouble().let { kotlin.math.round(it) } / 255
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    if (mx == mn) return Hsl(0.0, 0.0, l)
    val d = mx - mn
    val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> (g - b) / d + (if (g < b) 6 else 0)
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    return Hsl(h / 6, s, l)
}

/**
 * `shade`: [dl] moves the lightness (−1..1), [ds] the saturation; darker shades gain a touch of
 * saturation so they stay rich instead of going grey.
 */
internal fun shade(color: Color, dl: Double, ds: Double = 0.0): Hsl {
    val c = color.toHsl()
    return Hsl(c.h, (c.s + ds + (if (dl < 0) -dl * 0.25 else 0.0)).coerceIn(0.0, 1.0), (c.l + dl).coerceIn(0.0, 1.0)).rounded()
}

internal fun shade(hsl: Hsl, dl: Double, ds: Double = 0.0): Hsl =
    Hsl(hsl.h, (hsl.s + ds + (if (dl < 0) -dl * 0.25 else 0.0)).coerceIn(0.0, 1.0), (hsl.l + dl).coerceIn(0.0, 1.0)).rounded()

internal fun near(a: Double, b: Double) = abs(a - b) < 1e-9
