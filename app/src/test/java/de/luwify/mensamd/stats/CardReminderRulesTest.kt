package de.luwify.mensamd.stats

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.FixedSection
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.MenuPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CardReminderRulesTest {
    private val thu = LocalDate.of(2026, 11, 5)
    private val runAt = LocalTime.of(15, 0)
    private val card = RegisteredCard("s", "h", 0)
    private val data = StatsData(card = card, scans = listOf(Scan(1, 0, thu.minusDays(1), "h", 5_000)))
    private fun at(date: LocalDate, h: Int, m: Int = 0) = LocalDateTime.of(date, LocalTime.of(h, m))

    @Test fun remindsOnAWeekdayWithoutScan() = assertTrue(CardReminderRules.shouldRemind(at(thu, 15), runAt, data, true))

    @Test fun notAfterAScanToday() {
        val scanned = data.copy(scans = data.scans + Scan(2, 0, thu, "h", 4_000))
        assertFalse(CardReminderRules.shouldRemind(at(thu, 15), runAt, scanned, true))
        // A scan of another (earlier registered) card doesn't count.
        val other = data.copy(scans = data.scans + Scan(2, 0, thu, "old", 4_000))
        assertTrue(CardReminderRules.shouldRemind(at(thu, 15), runAt, other, true))
    }

    @Test fun notOnWeekends() {
        assertFalse(CardReminderRules.shouldRemind(at(thu.plusDays(2), 15), runAt, data, true))
        assertFalse(CardReminderRules.shouldRemind(at(thu.plusDays(3), 15), runAt, data, true))
    }

    @Test fun notWhenTheMensaIsClosed() {
        assertFalse(CardReminderRules.shouldRemind(at(thu, 15), runAt, data, false))
        assertTrue(CardReminderRules.shouldRemind(at(thu, 15), runAt, data, null)) // unknown: remind
    }

    @Test fun notWithoutCardOrData() {
        assertFalse(CardReminderRules.shouldRemind(at(thu, 15), runAt, data.copy(card = null), true))
        assertFalse(CardReminderRules.shouldRemind(at(thu, 15), runAt, null, true))
    }

    @Test fun lateOrEarlyCatchUpIsSkipped() {
        assertFalse(CardReminderRules.shouldRemind(at(thu, 0, 30), runAt, data, true)) // yesterday's run after midnight
        assertFalse(CardReminderRules.shouldRemind(at(thu, 22, 5), runAt, data, true))
        assertTrue(CardReminderRules.shouldRemind(at(thu, 21, 59), runAt, data, true))
    }

    private fun dish() = Dish(LocalizedText("A"), emptyList(), listOf(310), emptyList(), emptyList(), null, null)
    private fun page(vararg days: Pair<LocalDate, Int>) = MenuPage(days.map { (d, n) -> MenuDay(d, List(n) { dish() }) }, Legend.EMPTY)

    @Test fun menuToday() {
        assertEquals(true, CardReminderRules.menuToday(page(thu to 3), thu.minusDays(1), thu))
        assertEquals(false, CardReminderRules.menuToday(page(thu.plusDays(1) to 3), thu.minusDays(1), thu)) // covers tomorrow, not today
        assertEquals(false, CardReminderRules.menuToday(page(), thu, thu)) // fetched today, nothing: closed
        assertNull(CardReminderRules.menuToday(page(thu.minusDays(3) to 2), thu.minusDays(3), thu)) // old cache
        assertNull(CardReminderRules.menuToday(null, null, thu))
        val fixed = MenuPage(emptyList(), Legend.EMPTY, listOf(FixedSection(null, listOf(dish()))))
        assertEquals(true, CardReminderRules.menuToday(fixed, thu.minusDays(5), thu))
    }

    @Test fun nextRun() {
        val zone = ZoneId.of("Europe/Berlin")
        assertEquals(Duration.ofHours(1), CardReminderRules.delayUntilNextRun(ZonedDateTime.of(thu, LocalTime.of(14, 0), zone), runAt))
        assertEquals(Duration.ofHours(24), CardReminderRules.delayUntilNextRun(ZonedDateTime.of(thu, runAt, zone), runAt))
    }
}
