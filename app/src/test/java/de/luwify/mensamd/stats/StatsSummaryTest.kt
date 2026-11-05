package de.luwify.mensamd.stats

import de.luwify.mensamd.stats.StatsSummary.Unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsSummaryTest {
    private val thu = LocalDate.of(2026, 11, 5)
    private var id = 0L
    private fun ev(kind: EventKind, amount: Int, date: LocalDate, known: Boolean = true) = CardEvent(++id, 0, kind, amount, date, known)

    private val events = listOf(
        ev(EventKind.PAYMENT, 3_100, thu),
        ev(EventKind.PAYMENT, 2_500, thu.minusDays(3)), // Monday same week
        ev(EventKind.TOP_UP, 20_000, thu.minusDays(3)),
        ev(EventKind.PAYMENT, 4_400, thu.minusDays(7)), // previous week (Thursday)
        ev(EventKind.PAYMENT, 5_000, thu.minusDays(8), known = false),
        ev(EventKind.PFAND, 2_000, thu),
        ev(EventKind.PAYMENT, 1_000, LocalDate.of(2026, 10, 31)), // Saturday, October
    )

    @Test fun weeksIncludeEmptyOnesOldestFirst() {
        val weeks = StatsSummary.periods(events, Unit.WEEK, thu, 3)
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2)), weeks.map { it.start })
        assertEquals(listOf(0, 4_400 + 5_000 + 1_000, 3_100 + 2_500), weeks.map { it.spent })
        assertEquals(listOf(0, 0, 20_000), weeks.map { it.toppedUp })
    }

    @Test fun months() {
        val months = StatsSummary.periods(events, Unit.MONTH, thu, 2)
        // October: 29th, 28th (unknown day) and 31st; November: 2nd and 5th.
        assertEquals(listOf(4_400 + 5_000 + 1_000, 3_100 + 2_500), months.map { it.spent })
    }

    @Test fun weekdaysOnlyKnownDatesAndWeekendOnlyWhenUsed() {
        val days = StatsSummary.weekdays(events)
        assertEquals(DayOfWeek.MONDAY, days.first().day)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), days.map { it.day })
        assertEquals(3_100 + 4_400, days.first { it.day == DayOfWeek.THURSDAY }.spent)
        assertEquals(0, days.first { it.day == DayOfWeek.WEDNESDAY }.spent) // the unknown-date payment doesn't count
    }

    @Test fun totals() {
        val data = StatsData(events = events, scans = listOf(Scan(1, 1, thu, "h", 7_000, 3_100)), card = RegisteredCard("s", "h", 0))
        val t = StatsSummary.totals(data, thu)
        assertEquals(3_100 + 2_500, t.spentThisMonth)
        assertEquals(20_000, t.toppedUpThisMonth)
        assertEquals(2_000, t.pfandThisMonth)
        assertEquals((3_100 + 2_500 + 4_400 + 5_000 + 1_000) / 5, t.averagePerVisit)
        assertEquals(7_000, t.currentBalance)
        assertNull(StatsSummary.totals(StatsData(), thu).averagePerVisit)
    }

    @Test fun balanceOnlyOfTheCurrentCard() {
        val scans = listOf(Scan(1, 10, thu, "a", 100), Scan(2, 20, thu, "b", 200), Scan(3, 30, thu, "a", 50))
        assertEquals(listOf(100, 50), StatsSummary.balance(scans, "a").map { it.balance })
    }
}

class NiceMaxTest {
    @Test fun roundsUpToCleanEuros() {
        assertEquals(1_000, de.luwify.mensamd.ui.stats.niceMax(0))
        assertEquals(30_000, de.luwify.mensamd.ui.stats.niceMax(27_800))
        assertEquals(20_000, de.luwify.mensamd.ui.stats.niceMax(20_000))
        assertEquals(80_000, de.luwify.mensamd.ui.stats.niceMax(61_000))
        assertEquals(100_000, de.luwify.mensamd.ui.stats.niceMax(80_001))
    }
}
