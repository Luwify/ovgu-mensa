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
    fun unlockReveal_starsPopInOrder_andSettle() {
        val t = UnlockTimeline.STARS_START + 2 * UnlockTimeline.STAR_STEP + 0.1f
        assertTrue(UnlockTimeline.pop(t, UnlockTimeline.STARS_START) == 1f) // first star settled
        assertTrue(UnlockTimeline.pop(t, UnlockTimeline.STARS_START + 4 * UnlockTimeline.STAR_STEP) == 0f) // last not yet
        assertEquals(1.3f, UnlockTimeline.pop(0.6f * 0.28f, 0f), 0.001f) // overshoot
        assertEquals(1f, UnlockTimeline.pop(UnlockTimeline.TOTAL, 0f), 0f)
    }

    @Test
    fun auraBand_startsBelowThePinnedHeader_orAtTheTop() {
        // pinned header at the top (offset 0, 48 px), then cards
        assertEquals(48, SparkleMath.pinnedHeaderBottom(listOf(0 to 48, 60 to 300, 372 to 300), listOf(true, false, false)))
        // header being pushed out by the next one: only its visible part counts
        assertEquals(20, SparkleMath.pinnedHeaderBottom(listOf(-28 to 48, 20 to 300), listOf(true, false)))
        // a header further down the list is not pinned: the band starts at the top
        assertEquals(0, SparkleMath.pinnedHeaderBottom(listOf(0 to 300, 312 to 48), listOf(false, true)))
        assertEquals(0, SparkleMath.pinnedHeaderBottom(emptyList(), emptyList()))
    }

    @Test
    fun auraFalloff_reachesExactlyZeroSmoothly_andIsCloseToExpNearTheCard() {
        assertEquals(1f, SparkleMath.auraFalloff(0f), 0f)
        assertEquals(0f, SparkleMath.auraFalloff(1f), 0f)
        assertEquals(0f, SparkleMath.auraFalloff(1.3f), 0f)
        // no visible edge at the end: the last step before zero is tiny
        assertTrue(SparkleMath.auraFalloff(0.98f) < 1e-6f)
        // monotone
        val v = (0..100).map { SparkleMath.auraFalloff(it / 100f) }
        assertTrue(v.zipWithNext().all { (a, b) -> b <= a })
        // close to exp(-d/reach) with maxReach = 4 x reach, near the card
        assertEquals(kotlin.math.exp(-0.5f), SparkleMath.auraFalloff(0.5f / 4f), 0.03f)
    }

    @Test
    fun stackedFills_addUpToTheTargetOpacity() {
        val targets = listOf(0.01f, 0.05f, 0.15f, 0.35f, 0.6f, 0.85f)
        val alphas = SparkleMath.stackedAlphas(targets)
        var covered = 0f
        alphas.forEachIndexed { i, a ->
            covered += (1f - covered) * a
            assertEquals(targets[i], covered, 0.0001f)
        }
    }

    @Test
    fun edgeGlow_fallsSmoothlyToZero_andTheFallbackStepsAreSmall() {
        assertEquals(1f, edgeGlowFalloff(0f, 2f), 0f)
        assertEquals(0f, edgeGlowFalloff(1f, 2f), 0f)
        assertEquals(0f, edgeGlowFalloff(1.5f, 2f), 0f)
        assertTrue(edgeGlowFalloff(0.97f, 2f) < 0.001f) // no edge at the end of the reach
        val v = (0..50).map { edgeGlowFalloff(it / 50f, 1.8f) }
        assertTrue(v.zipWithNext().all { (a, b) -> b <= a })
        // the fallback's rings (side by side, each at the curve's alpha for its middle) step down in small steps
        val n = EDGE_GLOW_FALLBACK_FILLS
        val ringAlphas = (0 until n).map { 0.5f * edgeGlowFalloff((it + 0.5f) / n, 2f) }
        assertTrue(ringAlphas.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("largest step ${ringAlphas.zipWithNext().maxOf { (a, b) -> a - b }}", ringAlphas.zipWithNext().all { (a, b) -> a - b < 0.08f })
    }

    @Test
    fun trailReach_baseAtRest_extraBehindStrongestMidEdge_shorterAhead() {
        val base = 40f
        val extra = 200f
        for (n in listOf(0f to 1f, 1f to 0f, 0.7f to 0.7f)) assertEquals(base, SparkleMath.trailReach(n.first, n.second, 0f, 0f, base, extra), 0f)
        val below = SparkleMath.trailReach(0f, 1f, 0f, 1f, base, extra)
        val nearCorner = SparkleMath.trailReach(0f, 1f, 0.9f, 1f, base, extra)
        val side = SparkleMath.trailReach(1f, 0f, 0f, 1f, base, extra)
        val above = SparkleMath.trailReach(0f, -1f, 0f, 1f, base, extra)
        assertEquals(base + extra, below, 0.01f) // full absolute extra mid-edge behind the card
        assertTrue(nearCorner in side..below)
        assertEquals(base, side, 0f)
        assertEquals(0.75f * base, above, 0.01f)
        assertEquals(below, SparkleMath.trailReach(0f, -1f, 0f, -1f, base, extra), 0f) // mirrored
    }
    @Test
    fun trail_growsWithScrollSpeed_saturating_slowIsSmall_flingIsMax() {
        fun t(v: Float) = kotlin.math.abs(SparkleMath.trailForVelocity(v))
        assertEquals(0f, t(0f), 0f)
        assertEquals(0.30f, t(500f), 0.03f) // slow drag
        assertEquals(0.70f, t(1500f), 0.05f) // normal swipe
        assertTrue("fast fling 6000 dp/s: ${t(6000f)}", t(6000f) > 0.99f)
        assertTrue(t(20000f) <= 1f)
        val curve = (0..100).map { t(it * 100f) }
        assertTrue(curve.zipWithNext().all { (a, b) -> b >= a })
        // content moving up (negative velocity): trailing below (positive); and mirrored
        assertTrue(SparkleMath.trailForVelocity(-2000f) > 0f)
        assertEquals(-SparkleMath.trailForVelocity(-2000f), SparkleMath.trailForVelocity(2000f), 0f)
    }

    @Test
    fun velocityLowPass_smoothsSpikes_andConvergesOnSteadySpeed() {
        // one spike of 5000 dp/s between steady 1000 dp/s steps moves the filtered value only partly
        var v = 1000f
        repeat(20) { v = SparkleMath.lowPass(v, 1000f, 8f, SparkleMath.TRAIL_TAU_MS) }
        val spiked = SparkleMath.lowPass(v, 5000f, 8f, SparkleMath.TRAIL_TAU_MS)
        assertTrue("spike damped: $spiked", spiked < 1600f)
        // a steady speed is reached after a few time constants
        var w = 0f
        repeat(60) { w = SparkleMath.lowPass(w, 3000f, 8f, SparkleMath.TRAIL_TAU_MS) } // 480 ms
        assertEquals(3000f, w, 30f)
        // a long step moves further than a short one
        assertTrue(SparkleMath.lowPass(0f, 100f, 50f, 70f) > SparkleMath.lowPass(0f, 100f, 8f, 70f))
    }
}
