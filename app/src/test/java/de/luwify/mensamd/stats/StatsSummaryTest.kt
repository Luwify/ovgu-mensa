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

    @Test fun weeksStartAtTheFirstWeekWithDataOldestFirst() {
        val weeks = StatsSummary.periods(events, Unit.WEEK, thu, 3)
        // The week of 19.10. had nothing: the axis starts with 26.10.
        assertEquals(listOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2)), weeks.map { it.start })
        assertEquals(listOf(4_400 + 5_000 + 1_000, 3_100 + 2_500), weeks.map { it.spent })
        assertEquals(listOf(0, 20_000), weeks.map { it.toppedUp })
        // Empty weeks after the first one with data stay; nothing at all: only the current week.
        val gap = listOf(CardEvent(1, 0, EventKind.PAYMENT, 1_000, thu.minusWeeks(2), true))
        assertEquals(3, StatsSummary.periods(gap, Unit.WEEK, thu, 8).size)
        assertEquals(listOf(LocalDate.of(2026, 11, 2)), StatsSummary.periods(emptyList(), Unit.WEEK, thu, 8).map { it.start })
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

    @Test fun averagesPerVisitAndPerDay() {
        val d = StatsData(events = listOf(
            CardEvent(1, 0, EventKind.PAYMENT, 3_000, thu, true),
            CardEvent(2, 0, EventKind.PAYMENT, 6_000, thu, true, payments = 2), // at least two payments
            CardEvent(3, 0, EventKind.PAYMENT, 3_000, thu.minusDays(1), true),
            CardEvent(4, 0, EventKind.TOP_UP, 50_000, thu, true),
        ))
        val t = StatsSummary.totals(d, thu)
        assertEquals(3_000, t.averagePerVisit) // 12 € over 4 visits
        assertEquals(6_000, t.averagePerDay) // 12 € over 2 days
        assertEquals(4, t.payments)
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
