package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The hub's line icons, drawn on a 24 × 24 grid (no icon library needed). */
object Glyphs {
    val Sparkle = glyph(
        "sparkle",
        "M12 2.5C12.5 7.8 16.2 11.5 21.5 12C16.2 12.5 12.5 16.2 12 21.5C11.5 16.2 7.8 12.5 2.5 12C7.8 11.5 11.5 7.8 12 2.5Z",
        width = 1.2f,
    )
    val Settings = glyph(
        "settings",
        "M18.5 12a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0Z",
        "M14.6 12a2.6 2.6 0 1 1-5.2 0a2.6 2.6 0 1 1 5.2 0Z",
        "M12 2v2.6M12 19.4V22M2 12h2.6M19.4 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8",
    )
    val Link = glyph(
        "link",
        "M10 13.5a4.5 4.5 0 0 0 6.8.5l2.7-2.7a4.5 4.5 0 0 0-6.4-6.4l-1.5 1.5",
        "M14 10.5a4.5 4.5 0 0 0-6.8-.5l-2.7 2.7a4.5 4.5 0 0 0 6.4 6.4l1.5-1.5",
    )
    val Back = glyph("back", "M19 12H5", "M11 18l-6-6 6-6")
    val OpenExternal = glyph("open", "M8 7h9v9", "M7 17L17 7")
    val Remove = glyph("remove", "M6 6l12 12", "M18 6L6 18")
    val Plus = glyph("plus", "M12 5v14", "M5 12h14")
    val Paste = glyph(
        "paste",
        "M15.5 4.5h1.5a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2h1.5",
        "M9 3h6v3H9Z",
    )
    val Import = glyph("import", "M12 3v12", "M7 10l5 5 5-5", "M4 20h16")
    val Reset = glyph("reset", "M4 12a8 8 0 1 0 2.4-5.7", "M4 4v4.5h4.5")
    val Broom = glyph("clear", "M5 7h14", "M9 7V4.5h6V7", "M6.5 7l1 13h9l1-13")
}

private fun glyph(name: String, vararg paths: String, width: Float = 1.6f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                addPathNodes(it),
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
