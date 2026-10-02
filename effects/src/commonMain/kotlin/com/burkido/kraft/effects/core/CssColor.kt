package com.burkido.kraft.effects.core

import androidx.compose.ui.graphics.Color

/**
 * Parses the CSS colour syntaxes the upstream specs and presets use: `rgb()`/`rgba()` (comma or
 * space separated, alpha as number or percentage), `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`, and
 * the keywords `transparent`, `white` and `black`.
 */
fun parseCssColor(css: String): Color {
    val s = css.trim().lowercase()
    return when {
        s == "transparent" -> Color(0f, 0f, 0f, 0f)
        s == "white" -> Color.White
        s == "black" -> Color.Black
        s.startsWith("#") -> parseHex(s.substring(1), css)
        s.startsWith("rgb") -> parseRgb(s, css)
        else -> throw IllegalArgumentException("Unsupported CSS colour: $css")
    }
}

private fun parseHex(hex: String, source: String): Color {
    fun nibble(i: Int) = hex[i].digitToInt(16) * 17
    fun byte(i: Int) = hex.substring(i, i + 2).toInt(16)
    return when (hex.length) {
        3 -> Color(nibble(0), nibble(1), nibble(2))
        4 -> Color(nibble(0), nibble(1), nibble(2), nibble(3))
        6 -> Color(byte(0), byte(2), byte(4))
        8 -> Color(byte(0), byte(2), byte(4), byte(6))
        else -> throw IllegalArgumentException("Bad hex colour: $source")
    }
}

private fun parseRgb(s: String, source: String): Color {
    val open = s.indexOf('(')
    val close = s.lastIndexOf(')')
    require(open > 0 && close > open) { "Bad rgb colour: $source" }
    val parts = s.substring(open + 1, close)
        .replace('/', ' ')
        .split(',', ' ')
        .filter { it.isNotBlank() }
    require(parts.size == 3 || parts.size == 4) { "Bad rgb colour: $source" }
    fun channel(p: String): Float =
        if (p.endsWith("%")) p.dropLast(1).toFloat() / 100f else p.toFloat() / 255f
    fun alpha(p: String): Float =
        if (p.endsWith("%")) p.dropLast(1).toFloat() / 100f else p.toFloat()
    return Color(
        red = channel(parts[0]).coerceIn(0f, 1f),
        green = channel(parts[1]).coerceIn(0f, 1f),
        blue = channel(parts[2]).coerceIn(0f, 1f),
        alpha = if (parts.size == 4) alpha(parts[3]).coerceIn(0f, 1f) else 1f,
    )
}
