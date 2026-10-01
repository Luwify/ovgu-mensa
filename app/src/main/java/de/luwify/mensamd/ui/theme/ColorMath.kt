package de.luwify.mensamd.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps hue and lightness, scales HSL saturation by [kept] (1 = unchanged, 0 = gray).
 * Pure math so it is unit-testable.
 */
fun Color.desaturated(kept: Float): Color {
    val r = red
    val g = green
    val b = blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2f
    val d = maxC - minC
    if (d == 0f) return this
    val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val h = when (maxC) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    val s2 = (s * kept).coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s2
    val x = c * (1f - abs((h * 6f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when ((h * 6f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r1 + m, g1 + m, b1 + m, alpha)
}
