package de.luwify.mensamd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF39656B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF94D88F),
    secondary = Color(0xFFB9CCB4),
    tertiary = Color(0xFFA1CED5),
)

/**
 * The colors that carry meaning or define the card, per theme, so no screen hardcodes a color.
 * [cardContainer] null means the Material default card color.
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
    val priceContainer: Color,
    val onPriceContainer: Color,
)

/** Footprint scale stops used by every theme; they already pass AA on the Soft and Material cards. */
object FootprintStops {
    val light = listOf(Color(0xFF2E7D32), Color(0xFF9A6700), Color(0xFFC62828)) // green, amber, red
    val dark = listOf(Color(0xFF81C784), Color(0xFFFFCC66), Color(0xFFEF8A8A))
}

private fun systemMensaColors(darkTheme: Boolean, scheme: ColorScheme) = MensaColors(
    cardContainer = null,
    cardBorder = null,
    cardElevation = 0.dp,
    veganContainer = if (darkTheme) Color(0xFF264D2A) else Color(0xFFCDEBCF),
    veganContent = if (darkTheme) Color(0xFFC8F0CA) else Color(0xFF14501A),
    footprint = if (darkTheme) FootprintStops.dark else FootprintStops.light,
    priceContainer = scheme.primaryContainer,
    onPriceContainer = scheme.onPrimaryContainer,
)

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
        ThemeMode.SYSTEM -> {
            colorScheme = when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                darkTheme -> DarkColors
                else -> LightColors
            }
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
