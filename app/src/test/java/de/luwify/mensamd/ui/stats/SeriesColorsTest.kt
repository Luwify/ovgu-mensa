package de.luwify.mensamd.ui.stats

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import de.luwify.mensamd.ui.theme.AmoledTheme
import de.luwify.mensamd.ui.theme.Lab
import de.luwify.mensamd.ui.theme.SoftTheme
import de.luwify.mensamd.ui.theme.contrast
import de.luwify.mensamd.ui.theme.deltaE2000
import de.luwify.mensamd.ui.theme.lch
import de.luwify.mensamd.ui.theme.softened
import de.luwify.mensamd.ui.theme.toLab
import de.luwify.mensamd.ui.theme.withCardContrast
import de.luwify.mensamd.ui.theme.withContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart series stay apart with wallpaper colors. Dynamic schemes are built here the way Material You builds them
 * (tonal palettes: primary from the seed's hue, tertiary turned by some degrees, tone 40 in light and 80 in dark, neutral
 * surfaces), with CIELAB LCh instead of HCT, then softened like the System theme. Seeds include a sage wallpaper
 * (#43654A) whose tertiary is cream, the reported case.
 */
class SeriesColorsTest {
    @Test fun deltaE2000MatchesSharmasReferencePairs() {
        // G. Sharma, W. Wu, E. N. Dalal (2005), test data pairs 1, 2, 7, 17, 25.
        val pairs = listOf(
            Triple(Lab(50.0, 2.6772, -79.7751), Lab(50.0, 0.0, -82.7485), 2.0425),
            Triple(Lab(50.0, 3.1571, -77.2803), Lab(50.0, 0.0, -82.7485), 2.8615),
            Triple(Lab(50.0, 0.0, 0.0), Lab(50.0, -1.0, 2.0), 2.3669),
            Triple(Lab(50.0, 2.5, 0.0), Lab(73.0, 25.0, -18.0), 27.1492),
            Triple(Lab(60.2574, -34.0099, 36.2677), Lab(60.4626, -34.1751, 39.4387), 1.2644),
        )
        for ((a, b, expected) in pairs) {
            assertEquals(expected, deltaE2000(a, b), 1e-4)
            assertEquals(expected, deltaE2000(b, a), 1e-4)
        }
    }

    @Test fun lchRoundTripsInsideTheGamut() {
        val c = Color(0xFF43654A)
        val lab = c.toLab()
        val back = lch(lab.l, lab.chroma, lab.hue)
        assertEquals(c.red, back.red, 0.003f)
        assertEquals(c.green, back.green, 0.003f)
        assertEquals(c.blue, back.blue, 0.003f)
    }

    private fun dynamicScheme(seed: Color, tertiaryTurn: Double, chroma: Double, dark: Boolean): ColorScheme {
        val hue = seed.toLab().hue
        val c = if (seed.toLab().chroma < 4) 0.0 else chroma
        fun p(t: Double) = lch(t, c, hue)
        fun tr(t: Double) = lch(t, c * 2 / 3, hue + tertiaryTurn)
        fun n(t: Double) = lch(t, minOf(c, 4.0), hue)
        fun nv(t: Double) = lch(t, minOf(c, 8.0), hue)
        val s = if (dark) {
            darkColorScheme(
                primary = p(80.0), tertiary = tr(80.0), background = n(6.0), surface = n(6.0),
                surfaceContainerLowest = n(4.0), surfaceContainerLow = n(10.0), surfaceContainer = n(12.0),
                surfaceContainerHigh = n(17.0), surfaceContainerHighest = n(22.0), outline = nv(60.0), outlineVariant = nv(30.0),
            )
        } else {
            lightColorScheme(
                primary = p(40.0), tertiary = tr(40.0), background = n(98.0), surface = n(98.0),
                surfaceContainerLowest = n(100.0), surfaceContainerLow = n(96.0), surfaceContainer = n(94.0),
                surfaceContainerHigh = n(92.0), surfaceContainerHighest = n(90.0), outline = nv(50.0), outlineVariant = nv(80.0),
            )
        }
        return s.softened().withCardContrast(dark)
    }

    private val sage = Color(0xFF43654A)

    private val seeds: List<Color> = buildList {
        add(sage)
        add(Color(0xFF808080))
        for (h in 0 until 360 step 30) add(lch(50.0, 40.0, h.toDouble()))
    }

    @Test fun theReportedSageAndCreamPairIsCaughtAndSeparated() {
        // Sage wallpaper, cream tertiary (about 60° toward yellow), dark mode: both series near tone 80.
        val s = dynamicScheme(sage, tertiaryTurn = -60.0, chroma = 36.0, dark = true)
        val plain = ChartStyle.of(s, high = false)
        assertFalse(SeriesColors.distinct(plain.spent, plain.topUp, SeriesColors.NORMAL))
        val adapted = ChartStyle.of(s, high = false, adapt = true)
        assertTrue(SeriesColors.distinct(adapted.spent, adapted.topUp, SeriesColors.NORMAL))
        assertEquals("the first series keeps the theme's primary", plain.spent, adapted.spent)
    }

    @Test fun theColorsSeenOnAPhoneWithASageWallpaperAreSeparated() {
        // The System theme's dark colors read from a phone with the #43654A wallpaper (2026-11-24): sage primary, cream
        // tertiary, black page, the selected band's gray-green.
        val bgs = listOf(Color(0xFF000000), Color(0xFF222623))
        val primary = Color(0xFFAECAB2)
        val tertiary = Color(0xFFEFF7CF)
        assertFalse(SeriesColors.distinct(primary, tertiary, SeriesColors.NORMAL))
        for ((t, min) in listOf(SeriesColors.NORMAL to ChartStyle.MIN, SeriesColors.HIGH to ChartStyle.MIN_HIGH)) {
            val (a, b) = SeriesColors.pair(primary.withContrast(min, bgs), tertiary.withContrast(min, bgs), bgs, min, t)
            assertTrue(SeriesColors.separation(a, b).toString(), SeriesColors.distinct(a, b, t))
            for (bg in bgs) assertTrue(contrast(a, bg) >= min && contrast(b, bg) >= min)
        }
    }

    @Test fun dynamicSchemesAlwaysPassTheThreshold() {
        val failures = mutableListOf<String>()
        for (seed in seeds) for (turn in listOf(-90.0, -60.0, -30.0, 0.0, 30.0, 60.0, 90.0)) for (chroma in listOf(16.0, 36.0, 60.0))
            for (dark in listOf(false, true)) for (high in listOf(false, true)) {
                val s = dynamicScheme(seed, turn, chroma, dark)
                val style = ChartStyle.of(s, high, adapt = true)
                val t = if (high) SeriesColors.HIGH else SeriesColors.NORMAL
                val min = if (high) ChartStyle.MIN_HIGH else ChartStyle.MIN
                val name = "seed=%06X turn=$turn chroma=$chroma dark=$dark high=$high".format(seed.toArgb24())
                if (!SeriesColors.distinct(style.spent, style.topUp, t)) failures += "$name ${SeriesColors.separation(style.spent, style.topUp)}"
                for (bg in ChartStyle.backgrounds(s)) {
                    if (contrast(style.spent, bg) < min) failures += "$name spent contrast"
                    if (contrast(style.topUp, bg) < min) failures += "$name top-up contrast"
                }
            }
        assertTrue(failures.take(20).joinToString("\n"), failures.isEmpty())
    }

    @Test fun softStaysAsItIsInTheNormalStyle() {
        for (s in listOf(SoftTheme.light, SoftTheme.dark)) {
            assertEquals(ChartStyle.of(s, high = false), ChartStyle.of(s, high = false, adapt = false))
            val style = ChartStyle.of(s, high = false)
            assertTrue(contrast(style.spent, s.surface) >= ChartStyle.MIN)
        }
    }

    @Test fun highContrastSeparatesTheSeriesInEveryFixedTheme() {
        for (s in listOf(SoftTheme.light, SoftTheme.dark, AmoledTheme.scheme)) {
            val style = ChartStyle.of(s, high = true)
            assertTrue(SeriesColors.separation(style.spent, style.topUp).toString(), SeriesColors.distinct(style.spent, style.topUp, SeriesColors.HIGH))
        }
    }

    private fun Color.toArgb24(): Int =
        ((red * 255 + 0.5f).toInt() shl 16) or ((green * 255 + 0.5f).toInt() shl 8) or (blue * 255 + 0.5f).toInt()
}
