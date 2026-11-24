package de.luwify.mensamd.ui.stats

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.luwify.mensamd.data.settings.CustomBase
import de.luwify.mensamd.data.settings.CustomThemeSpec
import de.luwify.mensamd.ui.theme.AmoledTheme
import de.luwify.mensamd.ui.theme.CustomTheme
import de.luwify.mensamd.ui.theme.DarkColors
import de.luwify.mensamd.ui.theme.LightColors
import de.luwify.mensamd.ui.theme.SoftTheme
import de.luwify.mensamd.ui.theme.softened
import de.luwify.mensamd.ui.theme.withCardContrast
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart marks reach 3:1 against the page and the selected column's band in every fixed theme (WCAG 1.4.11), 4.5:1
 * with "Hoher Kontrast"; the grid of the high-contrast style is visible at 3:1 too, and its two series are apart
 * ([SeriesColors.HIGH]).
 */
class ChartStyleTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val schemes: List<Pair<String, ColorScheme>> = buildList {
        add("System light (no dynamic color)" to LightColors.softened().withCardContrast(false))
        add("System dark (no dynamic color)" to DarkColors.softened().withCardContrast(true))
        add("Soft light" to SoftTheme.light)
        add("Soft dark" to SoftTheme.dark)
        add("AMOLED" to AmoledTheme.scheme)
        for (base in CustomBase.entries) for (dark in listOf(false, true)) for (hue in 0 until 360 step 15) {
            val h = hue / 60.0
            val c = 0.62 * (1 - kotlin.math.abs(2 * 0.5 - 1))
            val x = c * (1 - kotlin.math.abs(h % 2 - 1))
            val (r, g, b) = when (h.toInt()) { 0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x); 3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x) }
            fun ch(v: Double) = ((v + 0.5 - c / 2) * 255).toInt().coerceIn(0, 255)
            val spec = CustomThemeSpec(base = base, accent = (ch(r) shl 16) or (ch(g) shl 8) or ch(b))
            add("Custom $base dark=$dark hue=$hue" to CustomTheme.scheme(spec, CustomTheme.isDark(spec, dark)))
        }
    }

    @Test fun seriesReachTheirContrastInEveryTheme() {
        val failures = mutableListOf<String>()
        for ((name, s) in schemes) for (high in listOf(false, true)) {
            val style = ChartStyle.of(s, high)
            val min = if (high) ChartStyle.MIN_HIGH else ChartStyle.MIN
            for (bg in ChartStyle.backgrounds(s)) {
                if (ratio(style.spent, bg) < min) failures += "$name high=$high spent %.2f".format(ratio(style.spent, bg))
                if (ratio(style.topUp, bg) < min) failures += "$name high=$high top-up %.2f".format(ratio(style.topUp, bg))
            }
            if (high && ratio(style.grid, s.surface) < ChartStyle.MIN) failures += "$name grid %.2f".format(ratio(style.grid, s.surface))
            if (high && !SeriesColors.distinct(style.spent, style.topUp, SeriesColors.HIGH)) {
                failures += "$name series ${SeriesColors.separation(style.spent, style.topUp)}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun highContrastIsThicker() {
        val normal = ChartStyle.of(AmoledTheme.scheme, false)
        val high = ChartStyle.of(AmoledTheme.scheme, true)
        assertTrue(high.line > normal.line && high.outline > normal.outline && high.gridWidth > normal.gridWidth)
    }
}
