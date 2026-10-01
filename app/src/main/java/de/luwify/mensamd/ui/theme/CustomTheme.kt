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

    /** The accent as typed/picked, opaque. */
    fun accentColor(spec: CustomThemeSpec): Color = Color(0xFF000000.toInt() or (spec.accent and 0xFFFFFF))

    /**
     * Hue of the accent, and how far its saturation scales the built-in tones (the tones were tuned for a swatch
     * with 62 % saturation): a gray accent gives a neutral theme, a vivid one a more colorful one.
     */
    private fun hueAndScale(spec: CustomThemeSpec): Pair<Float, Float> {
        val (h, s, _) = accentColor(spec).toHsl()
        return h to (s / 0.62f).coerceIn(0f, 1.4f)
    }

    fun scheme(spec: CustomThemeSpec, dark: Boolean): ColorScheme {
        val (h, k) = hueAndScale(spec)
        val t = (h + 40f) % 360f // tertiary (badges): a neighbor of the accent
        val black = spec.base == CustomBase.BLACK
        return if (!dark) light(h, t, k, accentColor(spec)) else dark(h, t, k, accentColor(spec), black)
    }

    private fun light(h: Float, t: Float, k: Float, accent: Color): ColorScheme {
        // Page one step below the cards, like the System theme: cards stand out by their fill.
        val card = tone(h, 0.30f, 0.995f, k)
        // Yellow is brighter than blue at the same HSL lightness: darken the page until it reads against the card.
        val page = tone(h, 0.18f, 0.93f, k).withContrast(1.17, listOf(card))
        val sheet = tone(h, 0.20f, 0.97f, k)
        val surfaces = listOf(page, card, sheet)
        // The user's exact color wherever it is readable; otherwise darkened just enough.
        val primary = accent.withContrast(TEXT, surfaces)
        val primaryContainer = tone(h, 0.65f, 0.87f, k)
        val secondaryContainer = tone(h, 0.28f, 0.88f, k)
        val tertiaryContainer = tone(t, 0.60f, 0.89f, k)
        return lightColorScheme(
            primary = primary,
            onPrimary = Color.White.withContrast(TEXT, listOf(primary)),
            primaryContainer = primaryContainer,
            onPrimaryContainer = tone(h, 0.60f, 0.16f, k).withContrast(TEXT, listOf(primaryContainer)),
            inversePrimary = tone(h, 0.60f, 0.78f, k),
            secondary = tone(h, 0.20f, 0.36f, k).withContrast(TEXT, surfaces),
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = tone(h, 0.25f, 0.18f, k).withContrast(TEXT, listOf(secondaryContainer)),
            tertiary = tone(t, 0.55f, 0.36f, k).withContrast(TEXT, surfaces),
            onTertiary = Color.White,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = tone(t, 0.60f, 0.16f, k).withContrast(TEXT, listOf(tertiaryContainer)),
            background = page,
            onBackground = tone(h, 0.15f, 0.12f, k).withContrast(TEXT, surfaces),
            surface = page,
            onSurface = tone(h, 0.15f, 0.12f, k).withContrast(TEXT, surfaces),
            surfaceVariant = tone(h, 0.16f, 0.88f, k),
            onSurfaceVariant = tone(h, 0.10f, 0.34f, k).withContrast(TEXT, surfaces + secondaryContainer),
            surfaceTint = Color.Transparent,
            inverseSurface = tone(h, 0.12f, 0.20f, k),
            inverseOnSurface = tone(h, 0.20f, 0.95f, k),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = tone(h, 0.08f, 0.48f, k).withContrast(BORDER, surfaces),
            outlineVariant = tone(h, 0.14f, 0.82f, k),
            scrim = Color.Black,
            surfaceBright = sheet,
            surfaceDim = tone(h, 0.16f, 0.87f, k),
            surfaceContainerLowest = card,
            surfaceContainerLow = sheet,
            surfaceContainer = page,
            surfaceContainerHigh = tone(h, 0.16f, 0.90f, k),
            surfaceContainerHighest = tone(h, 0.14f, 0.87f, k),
        )
    }

    private fun dark(h: Float, t: Float, k: Float, accent: Color, black: Boolean): ColorScheme {
        // Black: pure black page and a barely lifted card, like AMOLED. Dark: a tinted near-black page.
        val page = if (black) Color.Black else tone(h, 0.15f, 0.05f, k)
        // Blue is darker than yellow at the same HSL lightness: lift the card until it reads against the page.
        val card = if (black) tone(h, 0.08f, 0.065f, k) else tone(h, 0.14f, 0.12f, k).withContrast(1.2, listOf(page))
        val sheet = if (black) tone(h, 0.08f, 0.08f, k) else tone(h, 0.14f, 0.09f, k)
        val chip = if (black) tone(h, 0.12f, 0.15f, k) else tone(h, 0.16f, 0.21f, k)
        val surfaces = listOf(page, card, sheet)
        // The user's exact color wherever it is readable; otherwise lightened just enough.
        val primary = accent.withContrast(TEXT, surfaces)
        val primaryContainer = tone(h, 0.45f, 0.28f, k)
        val tertiaryContainer = tone(t, 0.35f, 0.28f, k)
        return darkColorScheme(
            primary = primary,
            onPrimary = tone(h, 0.70f, 0.14f, k).withContrast(TEXT, listOf(primary)),
            primaryContainer = primaryContainer,
            onPrimaryContainer = tone(h, 0.70f, 0.90f, k).withContrast(TEXT, listOf(primaryContainer)),
            inversePrimary = tone(h, 0.55f, 0.42f, k),
            secondary = tone(h, 0.25f, 0.76f, k).withContrast(TEXT, surfaces),
            onSecondary = tone(h, 0.25f, 0.15f, k),
            secondaryContainer = chip,
            onSecondaryContainer = tone(h, 0.18f, 0.84f, k).withContrast(TEXT, listOf(chip)),
            tertiary = tone(t, 0.60f, 0.79f, k).withContrast(TEXT, surfaces),
            onTertiary = tone(t, 0.60f, 0.15f, k),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = tone(t, 0.70f, 0.90f, k).withContrast(TEXT, listOf(tertiaryContainer)),
            background = page,
            onBackground = tone(h, 0.12f, 0.90f, k).withContrast(TEXT, surfaces),
            surface = page,
            onSurface = tone(h, 0.12f, 0.90f, k).withContrast(TEXT, surfaces),
            surfaceVariant = tone(h, 0.10f, 0.20f, k),
            onSurfaceVariant = tone(h, 0.10f, 0.72f, k).withContrast(TEXT, surfaces + chip),
            surfaceTint = Color.Transparent,
            inverseSurface = tone(h, 0.12f, 0.90f, k),
            inverseOnSurface = tone(h, 0.12f, 0.12f, k),
            error = Color(0xFFFFB4A9),
            onError = Color(0xFF561E18),
            errorContainer = Color(0xFF73332B),
            onErrorContainer = Color(0xFFFFDAD5),
            outline = tone(h, 0.08f, 0.55f, k).withContrast(BORDER, surfaces),
            outlineVariant = tone(h, 0.10f, 0.27f, k),
            scrim = Color.Black,
            surfaceBright = tone(h, 0.10f, 0.20f, k),
            surfaceDim = page,
            surfaceContainerLowest = if (black) Color.Black else tone(h, 0.15f, 0.03f, k),
            surfaceContainerLow = sheet,
            surfaceContainer = card,
            surfaceContainerHigh = chip,
            surfaceContainerHighest = tone(h, 0.12f, if (black) 0.18f else 0.25f, k),
        )
    }

    fun mensaColors(spec: CustomThemeSpec, dark: Boolean, scheme: ColorScheme): MensaColors {
        val card = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest
        // Glow: the accent as a real 3:1 border, plus a faint halo (the AMOLED look, in the user's color).
        val glowEdge = accentColor(spec).withContrast(BORDER, listOf(scheme.background, card))
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

/** A tone of the palette: [s] scaled by the accent's saturation [k], so gray accents give neutral themes. */
private fun tone(h: Float, s: Float, l: Float, k: Float): Color = hsl(h, (s * k).coerceIn(0f, 1f), l)

/** Color to HSL: hue in degrees 0..360, saturation and lightness 0..1. */
internal fun Color.toHsl(): Triple<Float, Float, Float> {
    val maxC = maxOf(red, green, blue)
    val minC = minOf(red, green, blue)
    val l = (maxC + minC) / 2f
    val d = maxC - minC
    if (d == 0f) return Triple(0f, 0f, l)
    val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val h = when (maxC) {
        red -> ((green - blue) / d + (if (green < blue) 6f else 0f)) * 60f
        green -> ((blue - red) / d + 2f) * 60f
        else -> ((red - green) / d + 4f) * 60f
    }
    return Triple(h, s, l)
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
