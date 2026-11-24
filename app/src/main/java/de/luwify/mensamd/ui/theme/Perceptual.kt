package de.luwify.mensamd.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Perceptual color math (pure, unit-tested): CIELAB / LCh (D65), the CIEDE2000 color difference and a simulation of
 * deuteranopia and protanopia (Machado, Oliveira and Fernandes 2009, severity 1, applied in linear sRGB).
 */
internal data class Lab(val l: Double, val a: Double, val b: Double) {
    val chroma: Double get() = hypot(a, b)

    /** Hue angle in degrees, 0..360. */
    val hue: Double get() = Math.toDegrees(atan2(b, a)).mod(360.0)
}

private fun Float.linear(): Double {
    val c = toDouble()
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun Double.gamma(): Double = if (this <= 0.0031308) 12.92 * this else 1.055 * pow(1 / 2.4) - 0.055

private const val XN = 0.95047
private const val ZN = 1.08883

private fun labF(t: Double): Double = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
private fun labFInv(t: Double): Double = if (t * t * t > 216.0 / 24389) t * t * t else (116 * t - 16) / (24389.0 / 27)

private fun labOfLinear(r: Double, g: Double, b: Double): Lab {
    val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
    val fx = labF(x / XN)
    val fy = labF(y)
    val fz = labF(z / ZN)
    return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

internal fun Color.toLab(): Lab = labOfLinear(red.linear(), green.linear(), blue.linear())

/** Linear sRGB of a Lab color, possibly outside 0..1 (out of gamut). */
private fun Lab.linear(): DoubleArray {
    val fy = (l + 16) / 116
    val x = XN * labFInv(fy + a / 500)
    val y = labFInv(fy)
    val z = ZN * labFInv(fy - b / 200)
    return doubleArrayOf(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
    )
}

/**
 * The sRGB color with lightness [l], hue [hue] (degrees) and at most [chroma]: the chroma is lowered until the color
 * fits into sRGB, so lightness and hue stay as asked.
 */
internal fun lch(l: Double, chroma: Double, hue: Double): Color {
    val rad = Math.toRadians(hue)
    fun at(c: Double) = Lab(l, c * cos(rad), c * sin(rad)).linear()
    fun fits(v: DoubleArray) = v.all { it in -1e-4..1.0001 }
    var c = chroma
    if (!fits(at(c))) {
        var lo = 0.0
        var hi = chroma
        repeat(24) {
            val mid = (lo + hi) / 2
            if (fits(at(mid))) lo = mid else hi = mid
        }
        c = lo
    }
    val v = at(c)
    return Color(
        v[0].coerceIn(0.0, 1.0).gamma().toFloat().coerceIn(0f, 1f),
        v[1].coerceIn(0.0, 1.0).gamma().toFloat().coerceIn(0f, 1f),
        v[2].coerceIn(0.0, 1.0).gamma().toFloat().coerceIn(0f, 1f),
    )
}

/** Kinds of color vision [simulate] can show. */
internal enum class Vision { DEUTERANOPIA, PROTANOPIA }

private val DEUTAN = doubleArrayOf(
    0.367322, 0.860646, -0.227968,
    0.280085, 0.672501, 0.047413,
    -0.011820, 0.042940, 0.968881,
)
private val PROTAN = doubleArrayOf(
    0.152286, 1.052583, -0.204868,
    0.114503, 0.786281, 0.099216,
    -0.003882, -0.048116, 1.051998,
)

/** This color's Lab as seen with [vision]. */
internal fun Color.labSeenWith(vision: Vision): Lab {
    val m = if (vision == Vision.DEUTERANOPIA) DEUTAN else PROTAN
    val r = red.linear()
    val g = green.linear()
    val b = blue.linear()
    fun row(i: Int) = (m[3 * i] * r + m[3 * i + 1] * g + m[3 * i + 2] * b).coerceIn(0.0, 1.0)
    return labOfLinear(row(0), row(1), row(2))
}

/** The CIEDE2000 difference of two Lab colors (kL = kC = kH = 1). */
internal fun deltaE2000(x: Lab, y: Lab): Double {
    val c1 = x.chroma
    val c2 = y.chroma
    val cMean7 = ((c1 + c2) / 2).pow(7)
    val g = 0.5 * (1 - sqrt(cMean7 / (cMean7 + 25.0.pow(7))))
    val a1 = x.a * (1 + g)
    val a2 = y.a * (1 + g)
    val cp1 = hypot(a1, x.b)
    val cp2 = hypot(a2, y.b)
    fun hueOf(a: Double, b: Double) = if (a == 0.0 && b == 0.0) 0.0 else Math.toDegrees(atan2(b, a)).mod(360.0)
    val hp1 = hueOf(a1, x.b)
    val hp2 = hueOf(a2, y.b)
    val dL = y.l - x.l
    val dC = cp2 - cp1
    var dh = 0.0
    if (cp1 * cp2 != 0.0) {
        dh = hp2 - hp1
        if (dh > 180) dh -= 360 else if (dh < -180) dh += 360
    }
    val dH = 2 * sqrt(cp1 * cp2) * sin(Math.toRadians(dh / 2))
    val lMean = (x.l + y.l) / 2
    val cpMean = (cp1 + cp2) / 2
    val hpMean = when {
        cp1 * cp2 == 0.0 -> hp1 + hp2
        abs(hp1 - hp2) <= 180 -> (hp1 + hp2) / 2
        hp1 + hp2 < 360 -> (hp1 + hp2 + 360) / 2
        else -> (hp1 + hp2 - 360) / 2
    }
    val t = 1 - 0.17 * cos(Math.toRadians(hpMean - 30)) + 0.24 * cos(Math.toRadians(2 * hpMean)) +
        0.32 * cos(Math.toRadians(3 * hpMean + 6)) - 0.20 * cos(Math.toRadians(4 * hpMean - 63))
    val dTheta = 30 * exp(-((hpMean - 275) / 25).pow(2))
    val cpMean7 = cpMean.pow(7)
    val rc = 2 * sqrt(cpMean7 / (cpMean7 + 25.0.pow(7)))
    val l50 = (lMean - 50).pow(2)
    val sl = 1 + 0.015 * l50 / sqrt(20 + l50)
    val sc = 1 + 0.045 * cpMean
    val sh = 1 + 0.015 * cpMean * t
    val rt = -sin(Math.toRadians(2 * dTheta)) * rc
    val tl = dL / sl
    val tc = dC / sc
    val th = dH / sh
    return sqrt(tl * tl + tc * tc + th * th + rt * tc * th)
}
