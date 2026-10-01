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

    // ---- super sparkle ----

    @Test
    fun roundedRectOutline_isClosedContinuous_onTheEdge_withUnitNormals() {
        val w = 300f
        val h = 120f
        val r = 12f
        val pts = (0..400).map { SparkleMath.roundedRectPoint(it / 400f, w, h, r) }
        for (p in pts) {
            assertEquals(1f, kotlin.math.hypot(p[2], p[3]), 0.001f)
            assertTrue(p[0] in -0.01f..w + 0.01f && p[1] in -0.01f..h + 0.01f)
        }
        // neighbors are close: no jumps at the corners, and the loop closes
        val steps = pts.zipWithNext().map { (a, b) -> kotlin.math.hypot(a[0] - b[0], a[1] - b[1]) }
        assertTrue("max step ${steps.max()}", steps.max() < 3f)
        assertEquals(pts.first()[0], pts.last()[0], 0.01f)
        // top edge points up, right edge right
        assertEquals(-1f, SparkleMath.roundedRectPoint(0.05f, w, h, r)[3], 0.001f)
        assertEquals(1f, SparkleMath.roundedRectPoint(0.42f, w, h, r)[2], 0.001f) // right edge: 0.36..0.48 of the loop
    }

    @Test
    fun jelly_trailingEdgeLagsMore_andWobbleStretchesThenSquashes() {
        val (top, bottom, inset) = SparkleMath.jelly(drag = 20f, wobble = 0f).toList()
        assertTrue("bottom lags more than top", bottom > top && top > 0f)
        assertEquals(0f, inset, 0f)
        val stretched = SparkleMath.jelly(0f, 10f)
        assertTrue(stretched[1] - stretched[0] > 0f && stretched[2] > 0f) // taller and narrower
        val squashed = SparkleMath.jelly(0f, -10f)
        assertTrue(squashed[1] - squashed[0] < 0f && squashed[2] < 0f) // shorter and wider
        assertTrue(SparkleMath.jelly(0f, 0f).all { it == 0f }) // at rest: the plain card shape
    }

    @Test
    fun wobble_growsWithScrollSpeedEitherWay_andIsCapped() {
        assertTrue(SparkleMath.wobbleAfter(0f, 30f, 50f) > 0f)
        assertEquals(SparkleMath.wobbleAfter(0f, 30f, 50f), SparkleMath.wobbleAfter(0f, -30f, 50f), 0f)
        assertEquals(50f, SparkleMath.wobbleAfter(45f, 500f, 50f), 0f)
    }

    @Test
    fun sparkles_flashBriefly_thenComeBackElsewhere() {
        val samples = (0L until 20000L step 25L).map { SparkleMath.sparkleSpot(0, it) }
        assertTrue(samples.all { it[0] in 0f..1f && it[1] in 2f..10f && it[3] in 0f..1f })
        assertTrue("dark most of the time", samples.count { it[3] < 0.05f } > samples.size / 2)
        assertTrue("bright moments", samples.any { it[3] > 0.95f })
        assertTrue("different places", samples.map { (it[0] * 100).toInt() }.toSet().size > 5)
    }

    @Test
    fun unlock_needsFiveTapsWithinThreeSeconds() {
        var taps = emptyList<Long>()
        for (t in listOf(1000L, 1400L, 1800L, 2200L)) taps = SparkleMath.recentTaps(taps + t, t)
        assertEquals(4, taps.size)
        taps = SparkleMath.recentTaps(taps + 2600L, 2600L)
        assertTrue(taps.size >= SparkleMath.UNLOCK_TAPS)
        // slow taps never get there
        var slow = emptyList<Long>()
        for (t in listOf(0L, 1000L, 2000L, 3000L, 4000L, 5000L)) slow = SparkleMath.recentTaps(slow + t, t)
        assertTrue(slow.size < SparkleMath.UNLOCK_TAPS)
    }

    @Test
    fun scrollDrag_trailsAgainstTheScroll_andIsCapped() {
        assertTrue(SparkleMath.dragAfter(0f, -10f, 50f) > 0f) // content moved up: the aura stays lower
        assertEquals(50f, SparkleMath.dragAfter(40f, -100f, 50f), 0f)
        assertEquals(-50f, SparkleMath.dragAfter(0f, 1000f, 50f), 0f)
    }

    @Test
    fun unlockReveal_starsPopInOrder_andSettle() {
        val t = UnlockTimeline.STARS_START + 2 * UnlockTimeline.STAR_STEP + 0.1f
        assertTrue(UnlockTimeline.pop(t, UnlockTimeline.STARS_START) == 1f) // first star settled
        assertTrue(UnlockTimeline.pop(t, UnlockTimeline.STARS_START + 4 * UnlockTimeline.STAR_STEP) == 0f) // last not yet
        assertEquals(1.3f, UnlockTimeline.pop(0.6f * 0.28f, 0f), 0.001f) // overshoot
        assertEquals(1f, UnlockTimeline.pop(UnlockTimeline.TOTAL, 0f), 0f)
    }
}
