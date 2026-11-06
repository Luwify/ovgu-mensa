package de.luwify.mensamd.stats

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The credits the user knows (mills): top-up amounts, and the Pfand of single bottles. The Pfand machine credits the
 * sum of all bottles returned at once, so any sum of [pfand] amounts up to [MAX_PFAND] counts as a Pfand return.
 */
data class KnownAmounts(val topUps: List<Int>, val pfand: List<Int>) {
    /** Which amounts up to [MAX_PFAND] are a sum of Pfand amounts (each any number of times, at least one bottle). */
    private val pfandSums: BooleanArray by lazy {
        val reachable = BooleanArray(MAX_PFAND + 1)
        reachable[0] = true
        val bottles = pfand.filter { it in 1..MAX_PFAND }.distinct()
        for (sum in 1..MAX_PFAND) reachable[sum] = bottles.any { it <= sum && reachable[sum - it] }
        reachable
    }

    /** Whether [amount] can be what the Pfand machine credits for one return. */
    fun isPfandSum(amount: Int): Boolean = amount in 1..MAX_PFAND && pfandSums[amount]

    /** The kind of a credit of [amount]: a top-up amount wins over a Pfand sum of the same amount; null if unknown. */
    fun creditKind(amount: Int): EventKind? = when {
        amount in topUps -> EventKind.TOP_UP
        isPfandSum(amount) -> EventKind.PFAND
        else -> null
    }

    /** The top-up amounts, ascending: what "top-up + payment" offers (Pfand sums are too many to list). */
    val credits: List<Pair<Int, EventKind>>
        get() = topUps.distinct().sorted().map { it to EventKind.TOP_UP }

    companion object {
        val DEFAULT_TOP_UPS = listOf(10_000, 20_000, 30_000, 50_000)

        /** The German deposits per bottle or can: 0.08 €, 0.15 €, 0.25 €. */
        val DEFAULT_PFAND = listOf(80, 150, 250)

        /** The largest credit that still counts as one Pfand return: 5 €. */
        const val MAX_PFAND = 5_000
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
 * - d > 0 and L changed: a payment happened in between, so it is a top-up plus payments: always asked (the top-up
 *   d + L is preselected when it is a top-up amount). L unchanged: a top-up amount d is a top-up; a Pfand sum d (up to
 *   5 €) is a Pfand return (shown with "Anders zuordnen", see [StatsOps.reassignCredit]); anything else is asked.
 *
 * A top-up leaves L as it is: seen on a real card (docs/STATS.md; a reset to 0 would look the same and is not ruled out).
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
                debitChanged && l > 0 -> Outcome.AskCredit(d, l, suggested = (d + l).takeIf { it in known.topUps })
                d in known.topUps -> Outcome.Events(listOf(EventKind.TOP_UP to d))
                known.isPfandSum(d) -> Outcome.Events(listOf(EventKind.PFAND to d))
                else -> Outcome.AskCredit(d, l, suggested = null)
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
     * The top-ups that could explain a change of [delta] together with one payment (payment = top-up - delta > 0),
     * ascending.
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
