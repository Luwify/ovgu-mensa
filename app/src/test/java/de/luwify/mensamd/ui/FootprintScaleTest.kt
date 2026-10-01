package de.luwify.mensamd.ui

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.parser.MenuPageParser
import de.luwify.mensamd.ui.theme.footprintColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FootprintScaleTest {
    @Test fun lessThanThreePoints_isNeutral() {
        assertNull(metricScale(emptyList()))
        assertNull(metricScale(listOf(100)))
        assertNull(metricScale(listOf(100, 900)))
    }

    @Test fun valuesCloseTogether_areNeutral() {
        assertNull(metricScale(listOf(1000, 1050, 1100))) // spread 100 < 20% of 1100
        assertNull(metricScale(listOf(500, 500, 500)))
        assertNull(metricScale(listOf(0, 0, 0)))
    }

    @Test fun clearSpread_givesAScale_andLevels() {
        val scale = metricScale(listOf(400, 900, 2200, 1000))!!
        assertEquals(400, scale.min)
        assertEquals(2200, scale.max)
        assertEquals(0f, scale.level(400), 0.0001f)
        assertEquals(1f, scale.level(2200), 0.0001f)
        assertEquals((900 - 400) / 1800f, scale.level(900), 0.0001f)
        assertEquals(1f, scale.level(9999), 0.0001f) // clamped
    }

    @Test fun exactlyAtTheThreshold_countsAsSpread() {
        assertNotNull(metricScale(listOf(800, 900, 1000))) // spread 200 = 20% of 1000
    }

    private fun dish(co2: Int?, water: Int?) = Dish(LocalizedText("x"), emptyList(), emptyList(), emptyList(), emptyList(), co2, water)

    @Test fun scalesAreSeparatePerDay_andPerMetric() {
        val d1 = LocalDate.of(2026, 10, 5)
        val d2 = LocalDate.of(2026, 10, 6)
        val days = listOf(
            MenuDay(d1, listOf(dish(400, 20), dish(1200, 21), dish(2200, 20), dish(null, null))),
            MenuDay(d2, listOf(dish(400, 20), dish(2200, 80))), // only two points
        )
        val fp = dayFootprints(days)
        assertEquals(MetricScale(400, 2200), fp.getValue(d1).co2)
        assertNull(fp.getValue(d1).water) // 20..21: too close
        assertNull(fp.getValue(d2).co2) // fewer than 3
    }

    @Test fun realPage_hasScalesOnDaysWithEnoughData_andNoneBreaks() {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/unicampus-unten.html")).use { it.readBytes().toString(Charsets.UTF_8) }
        val fp = dayFootprints(MenuPageParser.parse(html).days)
        assertNotNull(fp.values.firstOrNull { it.co2 != null })
        for (f in fp.values) f.co2?.let { assert(it.max > it.min) }
    }

    @Test fun colors_runGreenToRed_andDifferPerTheme() {
        for (dark in listOf(false, true)) {
            val low = footprintColor(0f, dark)
            val mid = footprintColor(0.5f, dark)
            val high = footprintColor(1f, dark)
            assertNotEquals(low, mid)
            assertNotEquals(mid, high)
            assert(low.green > low.red) { "low is greenish" }
            assert(high.red > high.green) { "high is reddish" }
        }
        assertNotEquals(footprintColor(0f, false), footprintColor(0f, true))
        assertEquals(footprintColor(0f, false), footprintColor(-3f, false)) // clamped
    }
}
