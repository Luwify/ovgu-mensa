package de.luwify.mensamd.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast for every text/background pair the screens use, in the themes with a fixed
 * palette (Soft light and dark, AMOLED). Text needs 4.5:1, control borders 3:1.
 */
class ThemeContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double, failures: MutableList<String>) {
        val r = ratio(fg, bg)
        if (r < min) failures += "$name: %.2f < $min".format(r)
    }

    /** [cardEdgeMin]: the card hairline is decorative, so it only has to be visible, not 3:1. */
    private fun pairs(s: ColorScheme, m: MensaColors, cardEdgeMin: Double, failures: MutableList<String>) {
        val card = m.cardContainer!!
        val sheet = s.surfaceContainerLow
        val text = 4.5
        check("onSurface/background", s.onSurface, s.background, text, failures)
        check("onSurfaceVariant/background", s.onSurfaceVariant, s.background, text, failures)
        check("primary/background (active tab)", s.primary, s.background, text, failures)
        check("onSurface/card", s.onSurface, card, text, failures)
        check("onSurfaceVariant/card", s.onSurfaceVariant, card, text, failures)
        check("link text/card (code toggle)", m.linkText, card, text, failures)
        check("price pill", m.price.content, m.price.container, text, failures)
        check("tag chip", m.tag.content, m.tag.container, text, failures)
        check("filter button text/background", m.filterButton.content, s.background, text, failures)
        check("badge text (tertiary/card)", s.tertiary, card, text, failures)
        check("sheet note", s.onTertiaryContainer, s.tertiaryContainer, text, failures)
        check("sheet heading/sheet", m.headingText, sheet, text, failures)
        check("onSurface/sheet", s.onSurface, sheet, text, failures)
        check("onSurfaceVariant/sheet", s.onSurfaceVariant, sheet, text, failures)
        check("error footer/background", s.error, s.background, text, failures)
        check("onPrimary/primary (retry)", s.onPrimary, s.primary, text, failures)
        check("vegan chip text", m.veganContent, m.veganContainer, text, failures)
        for ((i, c) in m.footprint.withIndex()) check("footprint stop $i/card", c, card, text, failures)
        // borders of controls must be visible
        check("chip outline/background", s.outline, s.background, 3.0, failures)
        check("filter button border/background", s.primary, s.background, 3.0, failures)
        check("badge border (tertiary)/card", s.tertiary, card, 3.0, failures)
        check("favorite heart/card", m.favorite, card, 3.0, failures)
        check("empty heart (onSurfaceVariant)/card", s.onSurfaceVariant, card, 3.0, failures)
        check("card hairline/background", m.cardBorder!!, s.background, cardEdgeMin, failures)
    }

    private fun run(name: String, s: ColorScheme, m: MensaColors, cardEdgeMin: Double) {
        val failures = mutableListOf<String>()
        pairs(s, m, cardEdgeMin, failures)
        assertTrue("$name: $failures", failures.isEmpty())
    }

    @Test fun softLightPassesAA() = run("Soft light", SoftTheme.light, SoftTheme.mensaColors(false), 1.05)

    @Test fun softDarkPassesAA() = run("Soft dark", SoftTheme.dark, SoftTheme.mensaColors(true), 1.05)

    @Test fun amoledPassesAA() = run("AMOLED", AmoledTheme.scheme, AmoledTheme.mensaColors(), 1.5)

    @Test
    fun amoledMatchesTheSpecifiedBlackBackgroundAndCards() {
        val s = AmoledTheme.scheme
        assertTrue(s.background == Color(0xFF000000) && s.surface == Color(0xFF000000))
        assertTrue(AmoledTheme.mensaColors().cardContainer == Color(0xFF0F0F11))
        assertTrue(AmoledTheme.mensaColors().cardBorder == s.outlineVariant)
        // light, slightly desaturated blue: not a pure saturated blue
        assertTrue(s.primary.blue > s.primary.red && s.primary.blue > s.primary.green && s.primary.red > 0.3f)
    }

    @Test
    fun softCardIsDistinctFromBackground_andHasAHairlineBorder() {
        for (dark in listOf(false, true)) {
            val m = SoftTheme.mensaColors(dark)
            val s = if (dark) SoftTheme.dark else SoftTheme.light
            assertTrue(m.cardContainer != s.background)
            assertTrue(m.cardBorder == s.outlineVariant)
        }
    }
}
