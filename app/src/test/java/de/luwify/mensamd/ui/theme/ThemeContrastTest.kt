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

    @Test
    fun amoledCardHasABlueOutline_atLeast3to1_andAFainterGlow() {
        val m = AmoledTheme.mensaColors()
        val border = m.cardBorder!!
        assertTrue("outline/black ${ratio(border, Color.Black)}", ratio(border, Color.Black) >= 3.0)
        assertTrue("outline/card", ratio(border, m.cardContainer!!) >= 3.0)
        assertTrue("blue", border.blue > border.red && border.blue > border.green)
        val glow = m.cardGlow!!
        assertTrue("glow is a translucent version of the outline", glow.alpha < 0.5f && glow.copy(alpha = 1f) == border)
    }

    /** The System theme with the fallback palettes (dynamic colors depend on the wallpaper; these are the same tones). */
    @Test
    fun systemCardsStandOutByTheirFill_andTagChipsStayVisibleOnThem() {
        for (dark in listOf(false, true)) {
            val s = (if (dark) DarkColors else LightColors).softened().withCardContrast(dark)
            val m = systemMensaColors(dark, s)
            val card = m.cardContainer!!
            val name = if (dark) "dark" else "light"
            // Before (2026-10-01): default background, light cards surfaceContainerLowest, dark surfaceContainerLow.
            val old = (if (dark) DarkColors else LightColors).softened()
            val oldRatio = ratio(if (dark) old.surfaceContainerLow else old.surfaceContainerLowest, old.background)
            val now = ratio(card, s.background)
            assertTrue("$name card/page $now", now >= 1.1 && now > oldRatio + 0.02)
            assertTrue("$name tag chip/card ${ratio(m.tag.container, card)}", ratio(m.tag.container, card) >= 1.1)
            assertTrue("$name top bar = page", s.surface == s.background)
            // The System pill and chips are transparent (outlined), so their text sits on the card or page itself.
            val failures = mutableListOf<String>()
            check("onSurface/card", s.onSurface, card, 4.5, failures)
            check("onSurfaceVariant/card", s.onSurfaceVariant, card, 4.5, failures)
            check("price text (primary)/card", m.price.content, card, 4.5, failures)
            check("onSurface/page", s.onSurface, s.background, 4.5, failures)
            check("onSurfaceVariant/page", s.onSurfaceVariant, s.background, 4.5, failures)
            check("tag chip text", m.tag.content, m.tag.container, 4.5, failures)
            check("heart/card", m.favorite, card, 3.0, failures)
            assertTrue("$name: $failures", failures.isEmpty())
        }
    }

    /** The user's own theme: every hue (5° steps) on every base, with and without the glow border, must pass. */
    @Test
    fun customThemePassesAA_forEveryHueAndBase() {
        val failures = mutableListOf<String>()
        for (base in de.luwify.mensamd.data.settings.CustomBase.entries) for (dark in listOf(false, true)) for (glow in listOf(false, true)) {
            for (hue in 0 until 360 step 5) {
                val spec = de.luwify.mensamd.data.settings.CustomThemeSpec(base, hue, glow)
                val isDark = spec.isDark(systemDark = dark)
                val s = CustomTheme.scheme(spec, isDark)
                val m = CustomTheme.mensaColors(spec, isDark, s)
                val local = mutableListOf<String>()
                pairs(s, m, if (glow) 3.0 else 1.05, local)
                check("heart/card", m.favorite, m.cardContainer!!, 3.0, local)
                check("tag chip/card (visible)", m.tag.container, m.cardContainer!!, 1.1, local)
                check("card/page (cards stand out)", m.cardContainer!!, s.background, if (base == de.luwify.mensamd.data.settings.CustomBase.BLACK) 1.08 else 1.15, local)
                if (glow) check("glow edge/card", m.cardBorder!!, m.cardContainer!!, 3.0, local)
                local.forEach { failures += "$base dark=$isDark glow=$glow hue=$hue: $it" }
            }
        }
        val kinds = failures.groupBy { it.substringAfter(": ").substringBefore(":") }.mapValues { it.value.size }
        assertTrue("${failures.size} failures by kind $kinds, e.g. ${failures.take(6)}", failures.isEmpty())
    }

    @Test
    fun customBlackBase_isPureBlackLikeAmoled_andGlowIsTranslucentEdge() {
        val spec = de.luwify.mensamd.data.settings.CustomThemeSpec(de.luwify.mensamd.data.settings.CustomBase.BLACK, 220, glow = true)
        val s = CustomTheme.scheme(spec, dark = true)
        assertTrue(s.background == Color.Black && s.surface == Color.Black)
        val m = CustomTheme.mensaColors(spec, true, s)
        assertTrue(m.cardGlow!!.alpha < 0.5f && m.cardGlow!!.copy(alpha = 1f) == m.cardBorder)
        val plain = CustomTheme.mensaColors(spec.copy(glow = false), true, s)
        assertTrue(plain.cardGlow == null && plain.cardBorder == s.outlineVariant)
    }
}
