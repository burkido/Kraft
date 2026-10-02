package com.burkido.kraft.film

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

/** Stroke icons on a 24 grid (Lucide-style), for the demo props. */
object FilmIcons {
    private fun stroke(vararg d: String, width: Float = 2f): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            for (p in d) {
                addPath(
                    PathParser().parsePathString(p).toNodes(),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Plus = stroke("M12 5V19M5 12H19")
    val ArrowUp = stroke("M12 19V5M5 12L12 5L19 12")
    val Mic = stroke("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v3")
    val File = stroke("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6")
    val Image = stroke("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z", "M21 15l-5-5L5 21")
    val Folder = stroke("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")
    val Search = stroke("M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M21 21l-4.3-4.3")
    val Sparkle = stroke("M12 3l1.9 5.8L20 11l-6.1 2.2L12 19l-1.9-5.8L4 11l6.1-2.2z", width = 1.6f)
    val Globe = stroke("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M2 12h20", "M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20z")
    val Code = stroke("M16 18l6-6-6-6", "M8 6l-6 6 6 6")
    val Wave = stroke("M2 12h2", "M6 8v8", "M10 5v14", "M14 9v6", "M18 7v10", "M22 12h-2")
}

@Composable
fun Icon(icon: ImageVector, tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(icon), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** A rounded surface with a hairline border and a deep soft shadow: the film's "card". */
fun Modifier.surface(radius: Dp, color: Color = FilmColors.Card, border: Color = FilmColors.Hairline, elevation: Dp = 0.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
        .clip(shape)
        .background(color)
        .border(1.dp, border, shape)
}

@Composable
fun IconChip(icon: ImageVector, size: Dp = 34.dp, bg: Color = Color.White.copy(alpha = 0.06f), tint: Color = FilmColors.Muted) {
    Box(Modifier.size(size).surface(size / 2, bg), contentAlignment = Alignment.Center) { Icon(icon, tint, size * 0.47f) }
}

/** Loads a PNG/JPEG from disk. */
fun loadBitmap(file: File): ImageBitmap = org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
