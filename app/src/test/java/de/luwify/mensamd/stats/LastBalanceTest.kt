package de.luwify.mensamd.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LastBalanceTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private val day = LocalDate.of(2026, 11, 24)
    private val card = RegisteredCard("00", "mine", 1)
    private fun scan(id: Long, card: String, balance: Int, t: Long) = Scan(id, t, day, card, balance)

    @Test fun encodesAndDecodes() {
        val s = LastScan(2_800, 1_764_000_000_000)
        assertEquals(s, LastScan.decode(s.encode()))
        assertEquals(LastScan(-50, 5), LastScan.decode("-50,5"))
    }

    @Test fun brokenValuesAreIgnored() {
        listOf(null, "", "2800", "a,b", "2800,", "2800,0", "2800,-1", "1,2,3").forEach { assertNull(it, LastScan.decode(it)) }
    }

    @Test fun myCardsLastScanWinsWithStatisticsOn() {
        val data = StatsData(card = card, scans = listOf(scan(1, "mine", 4_000, 10), scan(2, "old", 9_000, 20), scan(3, "mine", 2_800, 30)))
        val other = LastScan(1_500, 40) // another card read later
        assertEquals(LastBalance(2_800, 30, mine = true), LastBalances.pick(true, data, other))
    }

    @Test fun withStatisticsOffOrNoCardTheLastReadCardIsShown() {
        val data = StatsData(card = card, scans = listOf(scan(1, "mine", 4_000, 10)))
        val last = LastScan(1_500, 40)
        assertEquals(LastBalance(1_500, 40, mine = false), LastBalances.pick(false, data, last))
        assertEquals(LastBalance(1_500, 40, mine = false), LastBalances.pick(true, data.copy(card = null), last))
        assertEquals(LastBalance(1_500, 40, mine = false), LastBalances.pick(true, StatsData(card = card), last)) // registered, no scan yet
        assertEquals(LastBalance(1_500, 40, mine = false), LastBalances.pick(true, null, last)) // statistics not loaded yet
        assertNull(LastBalances.pick(false, data, null))
    }

    @Test fun daysByLocalCalendar() {
        val now = at(LocalDateTime.of(2026, 11, 24, 0, 30))
        assertEquals(LastBalances.Day.TODAY, LastBalances.day(at(LocalDateTime.of(2026, 11, 24, 0, 5)), now, zone))
        assertEquals(LastBalances.Day.YESTERDAY, LastBalances.day(at(LocalDateTime.of(2026, 11, 23, 23, 55)), now, zone))
        assertEquals(LastBalances.Day.YESTERDAY, LastBalances.day(at(LocalDateTime.of(2026, 11, 23, 0, 0)), now, zone))
        assertEquals(LastBalances.Day.EARLIER, LastBalances.day(at(LocalDateTime.of(2026, 11, 22, 23, 59)), now, zone))
        assertEquals(LastBalances.Day.TODAY, LastBalances.day(now + 3_600_000, now, zone)) // clock moved back
    }
}
