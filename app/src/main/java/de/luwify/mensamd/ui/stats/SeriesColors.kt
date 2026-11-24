package de.luwify.mensamd.ui.stats

import androidx.compose.ui.graphics.Color
import de.luwify.mensamd.ui.theme.Vision
import de.luwify.mensamd.ui.theme.contrast
import de.luwify.mensamd.ui.theme.deltaE2000
import de.luwify.mensamd.ui.theme.labSeenWith
import de.luwify.mensamd.ui.theme.lch
import de.luwify.mensamd.ui.theme.toLab
import kotlin.math.abs
import kotlin.math.max

/**
 * Keeps the two chart series (spending, top-ups) apart. Dynamic (wallpaper) colors can give a primary and a tertiary of
 * nearly the same lightness and close hues (sage and cream), so the pair is checked: lightness difference (CIELAB L*),
 * CIEDE2000 ΔE, and ΔE again as seen with deuteranopia and protanopia. A pair that fails keeps the first series and
 * gets a new second one: the tertiary's hue if it is at least 60° away, else the first series' hue turned by about 150°, at a clearly different
 * lightness, with the palette's chroma, still at the required contrast against the backgrounds. Only when no second
 * color fits beside the first one does the first one move too.
 */
internal object SeriesColors {
    data class Threshold(val lightness: Double, val deltaE: Double, val deltaECvd: Double)

    /** Normal style (System theme only). */
    val NORMAL = Threshold(lightness = 15.0, deltaE = 25.0, deltaECvd = 15.0)

    /** "Hoher Kontrast" (every theme). */
    val HIGH = Threshold(lightness = 20.0, deltaE = 28.0, deltaECvd = 20.0)

    data class Separation(val lightness: Double, val deltaE: Double, val deuteranopia: Double, val protanopia: Double)

    fun separation(a: Color, b: Color): Separation {
        val la = a.toLab()
        val lb = b.toLab()
        return Separation(
            lightness = abs(la.l - lb.l),
            deltaE = deltaE2000(la, lb),
            deuteranopia = deltaE2000(a.labSeenWith(Vision.DEUTERANOPIA), b.labSeenWith(Vision.DEUTERANOPIA)),
            protanopia = deltaE2000(a.labSeenWith(Vision.PROTANOPIA), b.labSeenWith(Vision.PROTANOPIA)),
        )
    }

    fun distinct(a: Color, b: Color, t: Threshold): Boolean = score(a, b, t) >= 1.0

    /**
     * The pair to draw: [first] and [second] unchanged when they are [distinct] (both already at [min] contrast against
     * [backgrounds]), else the closest pair found that is. If none is (a very narrow lightness band), the pair that
     * comes closest to [t].
     */
    fun pair(first: Color, second: Color, backgrounds: List<Color>, min: Double, t: Threshold): Pair<Color, Color> {
        if (distinct(first, second, t)) return first to second
        val f = first.toLab()
        val s = second.toLab()
        fun ok(c: Color) = backgrounds.all { contrast(c, it) >= min }
        // The palette's chroma, but enough to show the hue (also in a neutral theme: lightness alone is not enough).
        val chroma = max(max(f.chroma, s.chroma), 28.0).coerceAtMost(60.0)
        // The tertiary's hue when it is far enough from the first series' (else only lightness tells them apart: sage and
        // olive), about 150° away from the first series, then any hue (narrow lightness bands only allow chroma at some
        // hues).
        val hues = buildList {
            if (s.chroma >= 4 && (f.chroma < 4 || hueDistance(s.hue, f.hue) >= MIN_HUE_TURN)) add(s.hue)
            for (turn in listOf(150.0, -150.0, 180.0, 120.0, -120.0)) add((f.hue + turn).mod(360.0))
            for (turn in 0 until 360 step 15) add((f.hue + turn).mod(360.0))
        }.distinct()
        // The first series as it is, then moved a little lighter or darker, step by step.
        val firsts = buildList {
            add(first)
            for (shift in 2..46 step 2) for (sign in listOf(1, -1)) {
                val l = f.l + sign * shift
                if (l in 0.0..100.0) lch(l, f.chroma, f.hue).takeIf(::ok)?.let(::add)
            }
        }
        var best = first to second
        var bestScore = score(first, second, t)
        for (a in firsts) {
            val la = a.toLab().l
            // Lightness of the second series: the farthest of the threshold first, then further apart, both directions.
            val ls = (0..30).flatMap { i -> listOf(la + t.lightness + 2 + i * 2, la - t.lightness - 2 - i * 2) }
                .filter { it in 0.0..100.0 }
            for (hue in hues) for (l in ls) {
                val b = lch(l, chroma, hue)
                if (!ok(b)) continue
                val score = score(a, b, t)
                if (score >= 1.0) return a to b
                if (score > bestScore) {
                    best = a to b
                    bestScore = score
                }
            }
        }
        return best
    }

    private const val MIN_HUE_TURN = 60.0

    private fun hueDistance(a: Double, b: Double): Double = abs(a - b).mod(360.0).let { minOf(it, 360 - it) }

    /** How far [a] and [b] reach [t]: the smallest of the three ratios, 1 or more when they are [distinct]. */
    private fun score(a: Color, b: Color, t: Threshold): Double {
        val s = separation(a, b)
        return minOf(s.lightness / t.lightness, s.deltaE / t.deltaE, minOf(s.deuteranopia, s.protanopia) / t.deltaECvd)
    }
}
