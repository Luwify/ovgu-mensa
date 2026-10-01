package de.luwify.mensamd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the hand conversion of notes/vegan_logo.svg to the vector drawable: same viewport, same
 * number of subpaths and curve/line segments (only coordinate precision is reduced).
 */
class VeganLogoTest {
    private fun read(vararg candidates: String): String =
        candidates.map(::File).first { it.isFile }.readText()

    private val svg = read("../notes/vegan_logo.svg", "notes/vegan_logo.svg")
    private val xml = read("src/main/res/drawable/ic_vegan_logo.xml", "app/src/main/res/drawable/ic_vegan_logo.xml")

    private fun commands(d: String) = Regex("[A-Za-z]").findAll(d).groupingBy { it.value }.eachCount()

    private val svgPath = Regex("""<path\s+d="([^"]*)"""", RegexOption.DOT_MATCHES_ALL).find(svg)!!.groupValues[1]
    private val xmlPath = Regex("""android:pathData="([^"]*)"""").find(xml)!!.groupValues[1]

    @Test
    fun sameSegmentsAsTheSource() {
        assertEquals(commands(svgPath), commands(xmlPath))
        assertEquals(5, commands(xmlPath)["z"]) // ring (2 contours) + the flower parts
    }

    @Test
    fun sameViewportAsTheSource() {
        val vb = Regex("""viewBox="([^"]*)"""").find(svg)!!.groupValues[1].split(" ").map { it.toFloat() }
        assertEquals(vb[2], Regex("""android:viewportWidth="([^"]*)"""").find(xml)!!.groupValues[1].toFloat(), 0.001f)
        assertEquals(vb[3], Regex("""android:viewportHeight="([^"]*)"""").find(xml)!!.groupValues[1].toFloat(), 0.001f)
    }

    @Test
    fun coordinatesStayWithinTheViewport() {
        val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(xmlPath).map { it.value.toFloat() }.toList()
        assertTrue(nums.all { it in -1f..118f })
    }

    @Test
    fun isFilledBlackSoItCanBeTinted() = assertTrue(xml.contains("""android:fillColor="#FF000000""""))
}
