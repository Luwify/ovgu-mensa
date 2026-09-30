package de.luwify.mensamd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun MensaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Container/content pair for the vegan chip: light green in light theme, deep green in dark theme. */
data class ChipColors(val container: Color, val content: Color)

fun veganChipColors(darkTheme: Boolean) = if (darkTheme) {
    ChipColors(container = Color(0xFF264D2A), content = Color(0xFFC8F0CA))
} else {
    ChipColors(container = Color(0xFFCDEBCF), content = Color(0xFF14501A))
}
