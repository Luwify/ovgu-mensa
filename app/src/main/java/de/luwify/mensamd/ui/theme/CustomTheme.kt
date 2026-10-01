package de.luwify.mensamd.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.data.settings.CustomBase
import de.luwify.mensamd.data.settings.CustomThemeSpec
import kotlin.math.abs

/**
 * The user's own theme, generated from a [CustomThemeSpec] (base brightness, accent hue, glow). Tones are picked
 * in HSL, then every text color is pushed lighter or darker until it meets WCAG AA (4.5:1) on each surface it is
 * used on, and control borders 3:1, so no hue can produce unreadable text. ThemeContrastTest checks every hue.
 * Pure (no Android), like the other palettes.
 */
object CustomTheme {
    private const val TEXT = 4.5
    private const val BORDER = 3.0

    fun scheme(spec: CustomThemeSpec, dark: Boolean): ColorScheme {
        val h = spec.hue.mod(360).toFloat()
        val t = (h + 40f) % 360f // tertiary (badges): a neighbor of the accent
        val black = spec.base == CustomBase.BLACK
        return if (!dark) light(h, t) else dark(h, t, black)
    }

    private fun light(h: Float, t: Float): ColorScheme {
        // Page one step below the cards, like the System theme: cards stand out by their fill.
        val card = hsl(h, 0.30f, 0.995f)
        // Yellow is brighter than blue at the same HSL lightness: darken the page until it reads against the card.
        val page = hsl(h, 0.18f, 0.93f).withContrast(1.17, listOf(card))
        val sheet = hsl(h, 0.20f, 0.97f)
        val surfaces = listOf(page, card, sheet)
        val primary = hsl(h, 0.60f, 0.38f).withContrast(TEXT, surfaces)
        val primaryContainer = hsl(h, 0.65f, 0.87f)
        val secondaryContainer = hsl(h, 0.28f, 0.88f)
        val tertiaryContainer = hsl(t, 0.60f, 0.89f)
        return lightColorScheme(
            primary = primary,
            onPrimary = Color.White.withContrast(TEXT, listOf(primary)),
            primaryContainer = primaryContainer,
            onPrimaryContainer = hsl(h, 0.60f, 0.16f).withContrast(TEXT, listOf(primaryContainer)),
            inversePrimary = hsl(h, 0.60f, 0.78f),
            secondary = hsl(h, 0.20f, 0.36f).withContrast(TEXT, surfaces),
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = hsl(h, 0.25f, 0.18f).withContrast(TEXT, listOf(secondaryContainer)),
            tertiary = hsl(t, 0.55f, 0.36f).withContrast(TEXT, surfaces),
            onTertiary = Color.White,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = hsl(t, 0.60f, 0.16f).withContrast(TEXT, listOf(tertiaryContainer)),
            background = page,
            onBackground = hsl(h, 0.15f, 0.12f).withContrast(TEXT, surfaces),
            surface = page,
            onSurface = hsl(h, 0.15f, 0.12f).withContrast(TEXT, surfaces),
            surfaceVariant = hsl(h, 0.16f, 0.88f),
            onSurfaceVariant = hsl(h, 0.10f, 0.34f).withContrast(TEXT, surfaces + secondaryContainer),
            surfaceTint = Color.Transparent,
            inverseSurface = hsl(h, 0.12f, 0.20f),
            inverseOnSurface = hsl(h, 0.20f, 0.95f),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = hsl(h, 0.08f, 0.48f).withContrast(BORDER, surfaces),
            outlineVariant = hsl(h, 0.14f, 0.82f),
            scrim = Color.Black,
            surfaceBright = sheet,
            surfaceDim = hsl(h, 0.16f, 0.87f),
            surfaceContainerLowest = card,
            surfaceContainerLow = sheet,
            surfaceContainer = page,
            surfaceContainerHigh = hsl(h, 0.16f, 0.90f),
            surfaceContainerHighest = hsl(h, 0.14f, 0.87f),
        )
    }

    private fun dark(h: Float, t: Float, black: Boolean): ColorScheme {
        // Black: pure black page and a barely lifted card, like AMOLED. Dark: a tinted near-black page.
        val page = if (black) Color.Black else hsl(h, 0.15f, 0.05f)
        // Blue is darker than yellow at the same HSL lightness: lift the card until it reads against the page.
        val card = if (black) hsl(h, 0.08f, 0.065f) else hsl(h, 0.14f, 0.12f).withContrast(1.2, listOf(page))
        val sheet = if (black) hsl(h, 0.08f, 0.08f) else hsl(h, 0.14f, 0.09f)
        val chip = if (black) hsl(h, 0.12f, 0.15f) else hsl(h, 0.16f, 0.21f)
        val surfaces = listOf(page, card, sheet)
        val primary = hsl(h, 0.70f, 0.76f).withContrast(TEXT, surfaces)
        val primaryContainer = hsl(h, 0.45f, 0.28f)
        val tertiaryContainer = hsl(t, 0.35f, 0.28f)
        return darkColorScheme(
            primary = primary,
            onPrimary = hsl(h, 0.70f, 0.14f).withContrast(TEXT, listOf(primary)),
            primaryContainer = primaryContainer,
            onPrimaryContainer = hsl(h, 0.70f, 0.90f).withContrast(TEXT, listOf(primaryContainer)),
            inversePrimary = hsl(h, 0.55f, 0.42f),
            secondary = hsl(h, 0.25f, 0.76f).withContrast(TEXT, surfaces),
            onSecondary = hsl(h, 0.25f, 0.15f),
            secondaryContainer = chip,
            onSecondaryContainer = hsl(h, 0.18f, 0.84f).withContrast(TEXT, listOf(chip)),
            tertiary = hsl(t, 0.60f, 0.79f).withContrast(TEXT, surfaces),
            onTertiary = hsl(t, 0.60f, 0.15f),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = hsl(t, 0.70f, 0.90f).withContrast(TEXT, listOf(tertiaryContainer)),
            background = page,
            onBackground = hsl(h, 0.12f, 0.90f).withContrast(TEXT, surfaces),
            surface = page,
            onSurface = hsl(h, 0.12f, 0.90f).withContrast(TEXT, surfaces),
            surfaceVariant = hsl(h, 0.10f, 0.20f),
            onSurfaceVariant = hsl(h, 0.10f, 0.72f).withContrast(TEXT, surfaces + chip),
            surfaceTint = Color.Transparent,
            inverseSurface = hsl(h, 0.12f, 0.90f),
            inverseOnSurface = hsl(h, 0.12f, 0.12f),
            error = Color(0xFFFFB4A9),
            onError = Color(0xFF561E18),
            errorContainer = Color(0xFF73332B),
            onErrorContainer = Color(0xFFFFDAD5),
            outline = hsl(h, 0.08f, 0.55f).withContrast(BORDER, surfaces),
            outlineVariant = hsl(h, 0.10f, 0.27f),
            scrim = Color.Black,
            surfaceBright = hsl(h, 0.10f, 0.20f),
            surfaceDim = page,
            surfaceContainerLowest = if (black) Color.Black else hsl(h, 0.15f, 0.03f),
            surfaceContainerLow = sheet,
            surfaceContainer = card,
            surfaceContainerHigh = chip,
            surfaceContainerHighest = hsl(h, 0.12f, if (black) 0.18f else 0.25f),
        )
    }

    fun mensaColors(spec: CustomThemeSpec, dark: Boolean, scheme: ColorScheme): MensaColors {
        val h = spec.hue.mod(360).toFloat()
        val card = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest
        // Glow: an accent edge that is a real 3:1 border, plus a faint halo (the AMOLED look, in the user's hue).
        val glowEdge = (if (dark) hsl(h, 0.65f, 0.58f) else scheme.primary).withContrast(BORDER, listOf(scheme.background, card))
        return MensaColors(
            cardContainer = card,
            cardBorder = if (spec.glow) glowEdge else scheme.outlineVariant,
            cardElevation = 0.dp,
            veganContainer = if (dark) Color(0xFF264D2A) else Color(0xFFCDEBCF),
            veganContent = if (dark) Color(0xFFC8F0CA) else Color(0xFF14501A),
            footprint = if (dark) FootprintStops.dark else FootprintStops.light,
            price = ControlColors(scheme.primaryContainer, scheme.onPrimaryContainer, null),
            tag = ControlColors(scheme.secondaryContainer, scheme.onSecondaryContainer, null),
            filterButton = ControlColors(Color.Transparent, scheme.onSurface, scheme.outline),
            filterButtonActive = ControlColors(Color.Transparent, scheme.primary, scheme.primary),
            chipSelected = ControlColors(Color.Transparent, scheme.primary, scheme.primary),
            linkText = scheme.primary,
            headingText = scheme.primary,
            favorite = if (dark) FavoriteColors.dark else FavoriteColors.light,
            cardBorderWidth = if (spec.glow) 1.dp else Dp.Hairline,
            cardGlow = if (spec.glow) glowEdge.copy(alpha = if (dark) 0.22f else 0.16f) else null,
        )
    }
}

/** HSL to color; [h] in degrees, [s] and [l] 0..1. */
internal fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (h.mod(360f)) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}

internal fun contrast(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * This color, moved toward white (on dark backgrounds) or black (on light ones) in small steps until it reaches
 * [min] contrast on every one of [backgrounds]. Hue is kept as far as the lightness allows.
 */
internal fun Color.withContrast(min: Double, backgrounds: List<Color>): Color {
    if (backgrounds.all { contrast(this, it) >= min }) return this
    val towardWhite = backgrounds.map { it.luminance() }.average() < 0.18
    val target = if (towardWhite) Color.White else Color.Black
    for (step in 1..50) {
        val c = androidx.compose.ui.graphics.lerp(this, target, step / 50f)
        if (backgrounds.all { contrast(c, it) >= min }) return c
    }
    return target
}
