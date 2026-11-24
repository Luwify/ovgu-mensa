package de.luwify.mensamd.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The last balance read from any card ([balance] in mills, [at] epoch millis), without anything that identifies the card.
 * Kept with the statistics on or off (settings key `last_card_scan`, so it is in the backup), for the card screen before
 * the next scan. Stored as one string "balance,at", so the two values can never come from different scans.
 */
data class LastScan(val balance: Int, val at: Long) {
    fun encode(): String = "$balance,$at"

    companion object {
        /** Null for anything unreadable (a broken or hand-edited value is ignored, never fatal). */
        fun decode(text: String?): LastScan? {
            val parts = text?.split(',')?.takeIf { it.size == 2 } ?: return null
            val balance = parts[0].trim().toIntOrNull() ?: return null
            val at = parts[1].trim().toLongOrNull()?.takeIf { it > 0 } ?: return null
            return LastScan(balance, at)
        }
    }
}

/** What the card screen shows before a card is held to the phone: [mine] = the registered card's last scan. */
data class LastBalance(val balance: Int, val at: Long, val mine: Boolean)

/** Which last balance the card screen shows, and how its time is told. Pure. */
object LastBalances {
    /**
     * With the statistics on and a registered card that has a scan: that card's last scan ("Meine Karte"), even when
     * another card was read later. Otherwise the last balance read from any card ([last]), or nothing.
     */
    fun pick(statsOn: Boolean, data: StatsData?, last: LastScan?): LastBalance? {
        val card = data?.card?.takeIf { statsOn }
        val mine = card?.let { c -> data.scans.lastOrNull { it.card == c.hash } }
        return when {
            mine != null -> LastBalance(mine.balance, mine.at, mine = true)
            last != null -> LastBalance(last.balance, last.at, mine = false)
            else -> null
        }
    }

    enum class Day { TODAY, YESTERDAY, EARLIER }

    /** Today / yesterday / earlier, by local calendar day ([at] in the future counts as today: a clock change). */
    fun day(at: Long, now: Long, zone: ZoneId): Day {
        val date = date(at, zone)
        val today = date(now, zone)
        return when {
            !date.isBefore(today) -> Day.TODAY
            date == today.minusDays(1) -> Day.YESTERDAY
            else -> Day.EARLIER
        }
    }

    fun date(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}
