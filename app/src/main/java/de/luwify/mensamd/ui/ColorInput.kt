package de.luwify.mensamd.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.luwify.mensamd.ui.theme.hsl
import de.luwify.mensamd.ui.theme.toHsl

/** Typing and converting the custom theme's accent color (0xRRGGBB). Pure, unit-tested. */
object ColorInput {
    private val HEX = Regex("""^#?([0-9a-fA-F]{6})$""")

    /** "#7839D0" or "7839d0"; only the full 6 digits (a 3-digit shorthand would fire mid-typing). */
    fun parseHex(text: String): Int? = HEX.matchEntire(text.trim())?.groupValues?.get(1)?.toInt(16)

    fun formatHex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

    /** One RGB channel, 0..255. */
    fun parseChannel(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..255 }

    fun red(rgb: Int) = (rgb shr 16) and 0xFF
    fun green(rgb: Int) = (rgb shr 8) and 0xFF
    fun blue(rgb: Int) = rgb and 0xFF

    fun withChannel(rgb: Int, channel: Int, value: Int): Int {
        val shift = 16 - 8 * channel
        return (rgb and (0xFF shl shift).inv() and 0xFFFFFF) or ((value and 0xFF) shl shift)
    }

    /** Hue of [rgb] in degrees, 0..359. */
    fun hue(rgb: Int): Int = Color(0xFF000000.toInt() or rgb).toHsl().first.toInt().mod(360)

    /**
     * [rgb] turned to [hue], keeping its saturation and lightness (the slider only turns the color). A gray has no
     * hue to turn, so it takes the swatches' saturation and lightness instead.
     */
    fun withHue(rgb: Int, hue: Float): Int {
        val (_, s, l) = Color(0xFF000000.toInt() or rgb).toHsl()
        val color = if (s < 0.08f) hsl(hue, 0.62f, 0.52f) else hsl(hue, s, l)
        return color.toArgb() and 0xFFFFFF
    }
}
