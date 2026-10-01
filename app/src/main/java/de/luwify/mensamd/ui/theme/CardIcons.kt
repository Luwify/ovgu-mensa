package de.luwify.mensamd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A bank-card outline with a stripe, drawn from scratch. Black; tint it where it is used. */
val CardIcon: ImageVector by lazy {
    ImageVector.Builder("Card", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // body
            moveTo(5f, 5f)
            horizontalLineTo(19f)
            curveTo(20.1f, 5f, 21f, 5.9f, 21f, 7f)
            verticalLineTo(17f)
            curveTo(21f, 18.1f, 20.1f, 19f, 19f, 19f)
            horizontalLineTo(5f)
            curveTo(3.9f, 19f, 3f, 18.1f, 3f, 17f)
            verticalLineTo(7f)
            curveTo(3f, 5.9f, 3.9f, 5f, 5f, 5f)
            close()
            // stripe
            moveTo(3f, 10f)
            horizontalLineTo(21f)
            // chip line
            moveTo(6.5f, 15f)
            horizontalLineTo(10f)
        }
    }.build()
}

/** Arrow pointing left, for the back button. */
val BackIcon: ImageVector by lazy {
    ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(19f, 12f)
            horizontalLineTo(5f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }.build()
}

/** A check mark (selected entry of a drop-down menu), drawn like [BackIcon]. */
val CheckIcon: ImageVector by lazy {
    ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 7f)
        }
    }.build()
}

/** A chevron pointing down (drop-down field); turned 180° while the menu is open. */
val ChevronDownIcon: ImageVector by lazy {
    ImageVector.Builder("ChevronDown", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 9.5f)
            lineTo(12f, 15.5f)
            lineTo(18f, 9.5f)
        }
    }.build()
}
