package com.burkido.kraft.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The site's inline SVG icons, path data verbatim. Tint them with `Icon(tint = …)`. */
object KraftIcons {
    /** FAQ chevron: 16×16, stroke 1.5. */
    val ChevronDown: ImageVector = strokeIcon("ChevronDown", 16f, "M4 6.5L8 10.5L12 6.5", 1.5f)

    /** Hero tile label chevron (filled, drawn rotated −90° on the site). */
    val ChevronFilled: ImageVector = fillIcon(
        "ChevronFilled", 16f,
        "M4.47 6.22a.75.75 0 0 1 1.06 0L8 8.69l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z",
        evenOdd = true,
    )

    val More: ImageVector = ImageVector.Builder("More", 16.dp, 16.dp, 16f, 16f).apply {
        for (d in listOf(
            "M6.66667 8C6.66667 7.26362 7.26362 6.66667 8 6.66667C8.73638 6.66667 9.33333 7.26362 9.33333 8C9.33333 8.73638 8.73638 9.33333 8 9.33333C7.26362 9.33333 6.66667 8.73638 6.66667 8Z",
            "M6.66667 3.33333C6.66667 2.59695 7.26362 2 8 2C8.73638 2 9.33333 2.59695 9.33333 3.33333C9.33333 4.06971 8.73638 4.66667 8 4.66667C7.26362 4.66667 6.66667 4.06971 6.66667 3.33333Z",
            "M6.66667 12.6667C6.66667 11.9303 7.26362 11.3333 8 11.3333C8.73638 11.3333 9.33333 11.9303 9.33333 12.6667C9.33333 13.403 8.73638 14 8 14C7.26362 14 6.66667 13.403 6.66667 12.6667Z",
        )) addPath(addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
    }.build()

    /** The X (Twitter) glyph on testimonial cards. */
    val XLogo: ImageVector = fillIcon(
        "XLogo", 24f,
        "M18.244 2.25h3.308l-7.227 8.26 8.502 11.24h-6.657l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z",
    )

    val GitHub: ImageVector = fillIcon(
        "GitHub", 16f,
        "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z",
    )

    val ArrowLeft: ImageVector = strokeIcon("ArrowLeft", 16f, "M12.667 8H3.333M6.667 4.667 3.333 8l3.334 3.333", 1.5f)
    val ArrowRight: ImageVector = strokeIcon("ArrowRight", 16f, "M3.333 8h9.334M9.333 4.667 12.667 8l-3.334 3.333", 1.5f)

    /** `copy-03` (the Copy prompt button). */
    val CopyPrompt: ImageVector = strokeIcon(
        "CopyPrompt", 16f,
        "M5.6 5.6V3.92C5.6 3.24794 5.6 2.91191 5.73079 2.65521C5.84584 2.42942 6.02942 2.24584 6.25521 2.13079C6.51191 2 6.84794 2 7.52 2H12.08C12.7521 2 13.0881 2 13.3448 2.13079C13.5706 2.24584 13.7542 2.42942 13.8692 2.65521C14 2.91191 14 3.24794 14 3.92V8.48C14 9.15206 14 9.4881 13.8692 9.74479C13.7542 9.97058 13.5706 10.1542 13.3448 10.2692C13.0881 10.4 12.7521 10.4 12.08 10.4H10.4M3.92 14H8.48C9.15206 14 9.48809 14 9.74479 13.8692C9.97058 13.7542 10.1542 13.5706 10.2692 13.3448C10.4 13.0881 10.4 12.7521 10.4 12.08V7.52C10.4 6.84794 10.4 6.51191 10.2692 6.25521C10.1542 6.02942 9.97058 5.84584 9.74479 5.73079C9.48809 5.6 9.15206 5.6 8.48 5.6H3.92C3.24794 5.6 2.91191 5.6 2.65521 5.73079C2.42942 5.84584 2.24584 6.02942 2.13079 6.25521C2 6.51191 2 6.84794 2 7.52V12.08C2 12.7521 2 13.0881 2.13079 13.3448C2.24584 13.5706 2.42942 13.7542 2.65521 13.8692C2.91191 14 3.24794 14 3.92 14Z",
        1.5f,
    )

    /** The code-block copy icon (24-unit grid, stroke 2). */
    val Copy: ImageVector = ImageVector.Builder("Copy", 24.dp, 24.dp, 24f, 24f).apply {
        for (d in listOf("M11 9H20A2 2 0 0 1 22 11V20A2 2 0 0 1 20 22H11A2 2 0 0 1 9 20V11A2 2 0 0 1 11 9Z", "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1")) {
            addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

    val Check: ImageVector = strokeIcon("Check", 16f, "M3.5 8.46889L6.26923 11.58L12.5 4.58", 2f)

    private fun fillIcon(name: String, viewport: Float, d: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(name, viewport.dp, viewport.dp, viewport, viewport)
            .addPath(
                addPathNodes(d),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black),
            )
            .build()

    private fun strokeIcon(name: String, viewport: Float, d: String, width: Float): ImageVector =
        ImageVector.Builder(name, viewport.dp, viewport.dp, viewport, viewport)
            .addPath(
                addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
