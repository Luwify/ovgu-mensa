package de.luwify.mensamd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A simple sunflower drawn from scratch (12 petals around a seed disc) as the vegan marker.
 * Colors are fixed per theme instead of tinted: yellow petals with a darker rim read on the light
 * green chip, lighter yellow petals on the deep green dark chip.
 */
object Sunflower {
    private class Palette(val petal: Color, val rim: Color, val disc: Color, val seeds: Color)

    private val Light = Palette(petal = Color(0xFFF4B400), rim = Color(0xFF9A6100), disc = Color(0xFF5A3714), seeds = Color(0xFF8D5A2B))
    private val Dark = Palette(petal = Color(0xFFFFD54F), rim = Color(0xFFFFE9A8), disc = Color(0xFF3B2412), seeds = Color(0xFF7A5230))

    const val PETALS = 12

    private val light by lazy { build(Light, "SunflowerLight") }
    private val dark by lazy { build(Dark, "SunflowerDark") }

    fun vector(darkTheme: Boolean): ImageVector = if (darkTheme) dark else light

    private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
        moveTo(cx - rx, cy)
        arcTo(rx, ry, 0f, false, false, cx + rx, cy)
        arcTo(rx, ry, 0f, false, false, cx - rx, cy)
        close()
    }

    private fun build(p: Palette, name: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Petals: one ellipse pointing up, rotated around the center in 30 degree steps.
        for (k in 0 until PETALS) {
            group(rotate = k * (360f / PETALS), pivotX = 12f, pivotY = 12f) {
                path(fill = SolidColor(p.petal), stroke = SolidColor(p.rim), strokeLineWidth = 0.6f) {
                    ellipse(cx = 12f, cy = 5.2f, rx = 2.2f, ry = 4f)
                }
            }
        }
        // Seed disc with a lighter inner ring.
        path(fill = SolidColor(p.disc), stroke = SolidColor(p.rim), strokeLineWidth = 0.6f) { ellipse(12f, 12f, 4.6f, 4.6f) }
        path(fill = SolidColor(p.seeds)) { ellipse(12f, 12f, 2.4f, 2.4f) }
    }.build()
}
