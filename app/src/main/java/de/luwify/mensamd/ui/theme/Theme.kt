package de.luwify.mensamd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.data.settings.ThemeMode

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF39656B),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF94D88F),
    secondary = Color(0xFFB9CCB4),
    tertiary = Color(0xFFA1CED5),
)

/** Fill, text and optional outline of a control (price pill, tag chip, filter button, selected chip). */
@Immutable
class ControlColors(val container: Color, val content: Color, val border: Color?)

/**
 * The colors that carry meaning or define the cards and controls, per theme, so no screen hardcodes
 * a color. [cardContainer] null means the Material default card color.
 */
@Immutable
class MensaColors(
    val cardContainer: Color?,
    val cardBorder: Color?,
    val cardElevation: Dp,
    val veganContainer: Color,
    val veganContent: Color,
    /** Footprint scale: low (green), middle (amber), high (red). */
    val footprint: List<Color>,
    /** The selected price group's pill. */
    val price: ControlColors,
    val tag: ControlColors,
    val filterButton: ControlColors,
    /** Filter button while code exclusions are active. */
    val filterButtonActive: ControlColors,
    /** A selected toggle chip; null keeps the Material look. */
    val chipSelected: ControlColors?,
    /** Text/icons for secondary actions (code toggle, price arrow). */
    val linkText: Color,
    /** Section headings in the filter sheet. */
    val headingText: Color,
    /** The filled heart of a favorite dish (the empty heart uses onSurfaceVariant). */
    val favorite: Color,
    /** Width of [cardBorder]; a hairline unless the border is what sets the card apart. */
    val cardBorderWidth: Dp = Dp.Hairline,
    /** A soft blue halo around cards (AMOLED): plain strokes behind the card, no blur, no extra layer. */
    val cardGlow: Color? = null,
)

/** Heart red for favorites, the same in every theme; at least 3:1 on every card (an icon, not text). */
object FavoriteColors {
    val light = Color(0xFFC62828)
    val dark = Color(0xFFF28B82)
}

/** Footprint scale stops used by every theme; they already pass AA on the Soft and Material cards. */
object FootprintStops {
    val light = listOf(Color(0xFF2E7D32), Color(0xFF9A6700), Color(0xFFC62828)) // green, amber, red
    val dark = listOf(Color(0xFF81C784), Color(0xFFFFCC66), Color(0xFFEF8A8A))
}

/**
 * System colors: neutral surfaces and mostly outlined controls, so the wallpaper palette only
 * shows where it means something (selected tab, selected price, active filters). The cards stand
 * apart from the page by their fill (see [withCardContrast]): white on a light gray page, a lifted
 * gray on the dark page; the hairline border is only a finishing edge.
 */
internal fun systemMensaColors(darkTheme: Boolean, scheme: ColorScheme) = MensaColors(
    // Dark: one step below the tag chips (surfaceContainerHigh), so the chips stay visible on the card.
    cardContainer = if (darkTheme) scheme.surfaceContainer else scheme.surfaceContainerLowest,
    cardBorder = scheme.outlineVariant,
    cardElevation = 0.dp,
    veganContainer = if (darkTheme) Color(0xFF264D2A) else Color(0xFFCDEBCF),
    veganContent = if (darkTheme) Color(0xFFC8F0CA) else Color(0xFF14501A),
    footprint = if (darkTheme) FootprintStops.dark else FootprintStops.light,
    price = ControlColors(Color.Transparent, scheme.primary, scheme.primary),
    tag = ControlColors(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, null),
    filterButton = ControlColors(Color.Transparent, scheme.onSurface, scheme.outline),
    filterButtonActive = ControlColors(Color.Transparent, scheme.primary, scheme.primary),
    chipSelected = ControlColors(Color.Transparent, scheme.primary, scheme.primary),
    linkText = scheme.onSurfaceVariant,
    headingText = scheme.onSurface,
    favorite = if (darkTheme) FavoriteColors.dark else FavoriteColors.light,
)

/**
 * The System theme's page background, one step deeper than Material's default so the cards stand
 * out (user's choice "variant 1", 2026-10-01): light surfaceContainer, dark surfaceContainerLowest.
 * The top bar and day headers use the same color, so the page stays one plane.
 */
internal fun ColorScheme.withCardContrast(dark: Boolean): ColorScheme {
    val page = if (dark) surfaceContainerLowest else surfaceContainer
    return copy(background = page, surface = page)
}

/** Fraction of the wallpaper's color kept in accent roles / in surface roles of the System theme. */
private const val ACCENT_KEPT = 0.7f
private const val SURFACE_KEPT = 0.5f

/** The dynamic scheme with its colors slightly pulled toward gray: same lightness, less saturation. */
fun ColorScheme.softened(): ColorScheme {
    fun a(c: Color) = c.desaturated(ACCENT_KEPT)
    fun n(c: Color) = c.desaturated(SURFACE_KEPT)
    return copy(
        primary = a(primary), primaryContainer = a(primaryContainer), inversePrimary = a(inversePrimary),
        secondary = a(secondary), secondaryContainer = a(secondaryContainer),
        tertiary = a(tertiary), tertiaryContainer = a(tertiaryContainer),
        background = n(background), onBackground = n(onBackground),
        surface = n(surface), onSurface = n(onSurface), surfaceVariant = n(surfaceVariant), onSurfaceVariant = n(onSurfaceVariant),
        surfaceBright = n(surfaceBright), surfaceDim = n(surfaceDim),
        surfaceContainerLowest = n(surfaceContainerLowest), surfaceContainerLow = n(surfaceContainerLow),
        surfaceContainer = n(surfaceContainer), surfaceContainerHigh = n(surfaceContainerHigh),
        surfaceContainerHighest = n(surfaceContainerHighest),
        outline = n(outline), outlineVariant = n(outlineVariant),
        surfaceTint = Color.Transparent,
    )
}

val LocalMensaColors = compositionLocalOf { systemMensaColors(false, LightColors) }
val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }

@Composable
fun MensaTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme
    val shapes: Shapes
    val mensa: MensaColors
    when (mode) {
        ThemeMode.SOFT -> {
            colorScheme = if (darkTheme) SoftTheme.dark else SoftTheme.light
            shapes = SoftTheme.shapes
            mensa = remember(darkTheme) { SoftTheme.mensaColors(darkTheme) }
        }
        ThemeMode.AMOLED -> {
            // Always dark, whatever the system setting says.
            colorScheme = AmoledTheme.scheme
            shapes = Shapes()
            mensa = remember { AmoledTheme.mensaColors() }
        }
        ThemeMode.SYSTEM -> {
            colorScheme = when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (darkTheme) dynamicDarkColorScheme(context).softened() else dynamicLightColorScheme(context).softened()
                darkTheme -> DarkColors.softened()
                else -> LightColors.softened()
            }.withCardContrast(darkTheme)
            shapes = Shapes()
            mensa = remember(darkTheme, colorScheme) { systemMensaColors(darkTheme, colorScheme) }
        }
    }
    CompositionLocalProvider(LocalMensaColors provides mensa, LocalThemeMode provides mode) {
        MaterialTheme(colorScheme = colorScheme, shapes = shapes, content = content)
    }
}

/**
 * Text color for a footprint value: [level] 0 is the first stop (green), 0.5 the middle (amber),
 * 1 the last (red). [stops] come from the theme so the text keeps its contrast on the card.
 */
fun footprintColor(level: Float, stops: List<Color>): Color {
    val t = level.coerceIn(0f, 1f)
    return if (t < 0.5f) lerp(stops[0], stops[1], t * 2f) else lerp(stops[1], stops[2], (t - 0.5f) * 2f)
}

/** A FilterChip whose selected look comes from the theme (outlined with primary, or the Material tonal fill). */
@Composable
fun MensaFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val style = LocalMensaColors.current.chipSelected
    if (style == null) {
        FilterChip(selected = selected, onClick = onClick, label = label)
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = style.container,
                selectedLabelColor = style.content,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                selectedBorderColor = style.border ?: style.content,
                selectedBorderWidth = 1.5.dp,
            ),
        )
    }
}
