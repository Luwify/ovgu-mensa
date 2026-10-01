package de.luwify.mensamd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A funnel, drawn from scratch for the filter button. Black; tint it where it is used. */
val FilterIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Filter",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 5.5f)
            horizontalLineTo(20f)
            lineTo(14f, 12.8f)
            verticalLineTo(18.5f)
            lineTo(10f, 20f)
            verticalLineTo(12.8f)
            close()
        }
    }.build()
}
