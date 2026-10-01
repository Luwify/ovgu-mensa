package de.luwify.mensamd.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x contrast for every text/background pair the screens use in the Soft theme. */
class SoftThemeContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double, failures: MutableList<String>) {
        val r = ratio(fg, bg)
        if (r < min) failures += "$name: %.2f < $min".format(r)
    }

    private fun pairs(dark: Boolean, s: ColorScheme, failures: MutableList<String>) {
        val m = SoftTheme.mensaColors(dark)
        val card = m.cardContainer!!
        val sheet = s.surfaceContainerLow
        val text = 4.5
        check("onSurface/background", s.onSurface, s.background, text, failures)
        check("onSurfaceVariant/background", s.onSurfaceVariant, s.background, text, failures)
        check("primary/background (active tab)", s.primary, s.background, text, failures)
        check("onSurface/card", s.onSurface, card, text, failures)
        check("onSurfaceVariant/card", s.onSurfaceVariant, card, text, failures)
        check("primary/card (code toggle)", s.primary, card, text, failures)
        check("price pill", m.onPriceContainer, m.priceContainer, text, failures)
        check("tag chip", s.onSecondaryContainer, s.secondaryContainer, text, failures)
        check("badge text (tertiary/card)", s.tertiary, card, text, failures)
        check("sheet note", s.onTertiaryContainer, s.tertiaryContainer, text, failures)
        check("primary/sheet", s.primary, sheet, text, failures)
        check("onSurface/sheet", s.onSurface, sheet, text, failures)
        check("onSurfaceVariant/sheet", s.onSurfaceVariant, sheet, text, failures)
        check("error footer/background", s.error, s.background, text, failures)
        check("onPrimary/primary (retry)", s.onPrimary, s.primary, text, failures)
        check("vegan chip text", m.veganContent, m.veganContainer, text, failures)
        for ((i, c) in m.footprint.withIndex()) check("footprint stop $i/card", c, card, text, failures)
        // non-text: borders and the filter button outline must be visible
        check("chip outline/background", s.outline, s.background, 3.0, failures)
        check("filter button border (primary)/background", s.primary, s.background, 3.0, failures)
        check("badge border (tertiary)/card", s.tertiary, card, 3.0, failures)
    }

    @Test
    fun lightPairsPassAA() {
        val failures = mutableListOf<String>()
        pairs(false, SoftTheme.light, failures)
        assertTrue("Soft light: $failures", failures.isEmpty())
    }

    @Test
    fun darkPairsPassAA() {
        val failures = mutableListOf<String>()
        pairs(true, SoftTheme.dark, failures)
        assertTrue("Soft dark: $failures", failures.isEmpty())
    }

    @Test
    fun cardIsDistinctFromBackground_andHasAHairlineBorder() {
        for (dark in listOf(false, true)) {
            val m = SoftTheme.mensaColors(dark)
            val s = if (dark) SoftTheme.dark else SoftTheme.light
            assertTrue(m.cardContainer != s.background)
            assertTrue(m.cardBorder == s.outlineVariant)
        }
    }
}
