package de.luwify.mensamd.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkleMathTest {
    @Test
    fun burst_fliesOutAndFades_nothingLeftAtTheEnd() {
        val start = SparkleMath.burst(0f)
        val mid = SparkleMath.burst(0.5f)
        val end = SparkleMath.burst(1f)
        assertEquals(SparkleMath.PARTICLES, start.size)
        assertTrue(start.zip(mid).all { (a, b) -> b.distanceDp > a.distanceDp })
        assertTrue(start.all { it.alpha == 1f })
        assertTrue(end.all { it.alpha == 0f })
        // evenly around the heart, stars and dots alternating
        assertEquals(6, start.count { it.star })
        assertTrue(start.map { it.angleDeg }.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun heart_popsAndSettles() {
        assertEquals(1f, SparkleMath.heartScale(0f), 0.001f)
        assertEquals(1.35f, SparkleMath.heartScale(0.25f), 0.001f)
        assertEquals(1f, SparkleMath.heartScale(0.5f), 0.001f)
        assertEquals(1f, SparkleMath.heartScale(1f), 0.001f) // at rest: no scale at all
    }

    @Test
    fun twinkle_isMostlyOff_withShortBrightMoments_perStarOffset() {
        val samples = (0 until 2000 step 10).map { SparkleMath.twinkle(it.toLong(), 0) }
        assertTrue(samples.all { it in 0f..1f })
        assertTrue("bright moments exist", samples.any { it > 0.9f })
        assertTrue("mostly dim", samples.count { it < 0.2f } > samples.size / 2)
        assertTrue("stars twinkle at different times", SparkleMath.twinkle(500, 0) != SparkleMath.twinkle(500, 1))
    }

    @Test
    fun turn_and_cyclicGradient_wrap() {
        assertEquals(0f, SparkleMath.turn(0), 0f)
        assertEquals(0.5f, SparkleMath.turn(1800), 0.001f)
        assertEquals(0f, SparkleMath.turn(3600), 0f)
        val stops = listOf(Color.Red, Color.Blue)
        assertEquals(Color.Red, SparkleMath.cyclic(stops, 0f))
        assertEquals(SparkleMath.cyclic(stops, 0.25f), SparkleMath.cyclic(stops, 1.25f))
        assertEquals(SparkleMath.cyclic(stops, 0.75f), SparkleMath.cyclic(stops, -0.25f))
    }

    @Test
    fun gold_isBrightOnDarkCards_deeperOnLightOnes() {
        val onDark = goldFor(Color(0xFF0F0F11))
        val onLight = goldFor(Color.White)
        assertTrue(onDark.first() != onLight.first())
    }
}
