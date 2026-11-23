package de.luwify.mensamd.ui.stats

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.ui.theme.withContrast

/**
 * Colors and line widths of the statistics charts, from the theme's [ColorScheme]: the series colors are the theme's
 * primary (spending) and tertiary (top-ups), moved just far enough from the screen's backgrounds (the page and the
 * selected column's band) to reach [MIN] contrast (WCAG 1.4.11 for graphics), [MIN_HIGH] with "Hoher Kontrast", which
 * also draws thicker lines and a stronger grid. Top-ups are drawn as outlined columns, so the two series differ by shape,
 * not by color alone. Dynamic (Material You) colors are nudged the same way; `ChartStyleTest` checks the fixed themes.
 */
internal data class ChartStyle(
    val spent: Color,
    val topUp: Color,
    val grid: Color,
    val line: Dp,
    val outline: Dp,
    val gridWidth: Dp,
    val wash: Float,
) {
    companion object {
        const val MIN = 3.0
        const val MIN_HIGH = 4.5

        /** The backgrounds a chart mark sits on. */
        fun backgrounds(s: ColorScheme): List<Color> = listOf(s.surface, s.surfaceContainerHighest)

        fun of(s: ColorScheme, high: Boolean): ChartStyle {
            val bgs = backgrounds(s)
            val min = if (high) MIN_HIGH else MIN
            return ChartStyle(
                spent = s.primary.withContrast(min, bgs),
                topUp = s.tertiary.withContrast(min, bgs),
                grid = if (high) s.outline.withContrast(MIN, listOf(s.surface)) else s.outlineVariant,
                line = if (high) 3.dp else 2.dp,
                outline = if (high) 2.5.dp else 1.5.dp,
                gridWidth = if (high) 1.5.dp else 1.dp,
                wash = if (high) 0.18f else 0.10f,
            )
        }
    }
}
