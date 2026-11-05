package de.luwify.mensamd.stats

import java.time.DayOfWeek
import java.time.LocalDate

/** A credit the user knows: a top-up amount or a Pfand amount (mills). */
data class KnownAmounts(val topUps: List<Int>, val pfand: List<Int>) {
    /** The kind of a credit of [amount], top-ups first (they are the usual case), or null if unknown. */
    fun creditKind(amount: Int): EventKind? = when (amount) {
        in topUps -> EventKind.TOP_UP
        in pfand -> EventKind.PFAND
        else -> null
    }

    /** Every known credit, ascending, top-ups before Pfand at equal amounts. */
    val credits: List<Pair<Int, EventKind>>
        get() = (topUps.map { it to EventKind.TOP_UP } + pfand.filter { it !in topUps }.map { it to EventKind.PFAND })
            .distinct().sortedBy { it.first }

    companion object {
        val DEFAULT_TOP_UPS = listOf(10_000, 20_000, 30_000, 50_000)
    }
}

/**
 * What happened between two scans of the same card, from the balance and the card's last debit (the only data the
 * card exposes; see docs/STATS.md, phase 0). Pure.
 *
 * The rules, with d = new balance - old balance and L = the last debit after the new scan:
 * - d = 0: nothing, unless L changed (then a credit of exactly a payment: asked).
 * - d < 0 and -d = L: one payment. -d > L: several payments (asked: one or split). -d < L: a credit and the payment L;
 *   automatic when the credit L + d is a known amount, else asked. Cards without L (MagnaCarta): one payment.
 * - d > 0 and L unchanged: a known top-up / Pfand amount d, else asked (top-up + payment?). L changed: a payment L
 *   happened too, so the credit is d + L; automatic when known, else asked.
 */
object BalanceDiff {
    sealed interface Outcome {
        data object Nothing : Outcome

        /** Certain: these events, in this order. */
        data class Events(val events: List<Pair<EventKind, Int>>) : Outcome

        data class AskSeveral(val amount: Int, val lastDebit: Int?) : Outcome

        data class AskCredit(val delta: Int, val lastDebit: Int?, val suggested: Int?) : Outcome
    }

    fun derive(oldBalance: Int, oldLastDebit: Int?, newBalance: Int, newLastDebit: Int?, known: KnownAmounts): Outcome {
        val d = newBalance - oldBalance
        val l = newLastDebit
        val debitChanged = l != null && oldLastDebit != null && l != oldLastDebit
        return when {
            d == 0 -> if (debitChanged && l > 0) creditAndPayment(0, l, known) else Outcome.Nothing
            d < 0 -> {
                val paid = -d
                when {
                    l == null || l <= 0 -> Outcome.Events(listOf(EventKind.PAYMENT to paid))
                    paid == l -> Outcome.Events(listOf(EventKind.PAYMENT to paid))
                    paid > l -> Outcome.AskSeveral(paid, l)
                    else -> creditAndPayment(d, l, known)
                }
            }
            else -> when {
                debitChanged && l > 0 -> creditAndPayment(d, l, known)
                else -> known.creditKind(d)?.let { Outcome.Events(listOf(it to d)) }
                    ?: Outcome.AskCredit(d, l, suggested = null)
            }
        }
    }

    /** A credit plus the payment [lastDebit] that together changed the balance by [d]. */
    private fun creditAndPayment(d: Int, lastDebit: Int, known: KnownAmounts): Outcome {
        val credit = d + lastDebit
        val kind = known.creditKind(credit)
        return if (kind != null) {
            Outcome.Events(listOf(kind to credit, EventKind.PAYMENT to lastDebit))
        } else {
            Outcome.AskCredit(d, lastDebit, suggested = null)
        }
    }

    /**
     * The credits that could explain a change of [delta] together with one payment (payment = credit - delta > 0),
     * known amounts only, ascending.
     */
    fun creditOptions(delta: Int, known: KnownAmounts): List<Pair<Int, EventKind>> =
        known.credits.filter { (amount, _) -> amount - delta > 0 }

    /**
     * Whether an event revealed by a scan on [newDate] happened that day: the previous scan was the same day, or only
     * weekend days lie between them (the Mensen are closed then). A skipped weekday means "some day in between".
     */
    fun dateKnown(oldDate: LocalDate, newDate: LocalDate): Boolean {
        if (!newDate.isAfter(oldDate)) return true
        var day = oldDate.plusDays(1)
        while (day.isBefore(newDate)) {
            if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) return false
            day = day.plusDays(1)
        }
        return true
    }

    /** A split of [total] into [parts]: every part positive and the sum exact. */
    fun validSplit(total: Int, parts: List<Int>): Boolean = parts.size >= 2 && parts.all { it > 0 } && parts.sum() == total
}
