package app.lumora.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Tiny hand-rolled icon set (Material Symbols paths) so we do not have to ship
 * `material-icons-extended` (~4 MB of vectors) just for a few glyphs.
 */
object LumoraIcons {

    private fun icon(name: String, path: String, stroke: Boolean = false): ImageVector {
        val nodes = PathParser().parsePathString(path).toNodes()
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        builder.addPath(
            pathData = nodes,
            fill = if (stroke) null else SolidColor(Color.Black),
            stroke = if (stroke) SolidColor(Color.Black) else null,
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
        )
        return builder.build()
    }

    /** Four-point star used for the store tab and "featured" badges. */
    val Sparkle: ImageVector by lazy {
        icon("Sparkle", "M12 2.2l2.2 7.6 7.6 2.2-7.6 2.2L12 21.8l-2.2-7.6L2.2 12l7.6-2.2z")
    }

    /** Masonry grid. */
    val Grid: ImageVector by lazy {
        icon(
            "Grid",
            "M4 4h6v7H4zM14 4h6v4h-6zM14 11h6v9h-6zM4 14h6v6H4z",
        )
    }

    /** Settings sliders. */
    val Sliders: ImageVector by lazy {
        icon(
            "Sliders",
            "M3 7h5M11 7h10M3 17h9M15 17h6M3 12h12M18 12h3",
            stroke = true,
        )
    }

    val Download: ImageVector by lazy {
        icon("Download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
    }

    val Image: ImageVector by lazy {
        icon(
            "Image",
            "M21 19V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z",
        )
    }

    val Video: ImageVector by lazy {
        icon(
            "Video",
            "M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11l-4 4z",
        )
    }

    val LiveWallpaper: ImageVector by lazy {
        icon(
            "LiveWallpaper",
            "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM10 9l6 3-6 3z",
        )
    }

    val Check: ImageVector by lazy {
        icon("Check", "M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z")
    }

    val Wifi: ImageVector by lazy {
        icon(
            "Wifi",
            "M2 8.5C7.5 3.5 16.5 3.5 22 8.5M5.5 12c3.8-3.2 9.2-3.2 13 0M9 15.5c1.8-1.5 4.2-1.5 6 0",
            stroke = true,
        )
    }
}
