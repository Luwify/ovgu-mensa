package de.luwify.mensamd.stats

import java.time.LocalDate

/**
 * One money movement between two scans, as the "Was ist passiert?" screen lists it: a top-up, a payment or a Pfand return
 * of [amount] (mills, positive) on [day]. [dayChosen]: the user set the day (or there was only one possible day), so it is
 * known; otherwise the event keeps the question's [OpenItem.dateKnown].
 */
data class Movement(val kind: EventKind, val amount: Int, val day: LocalDate, val dayChosen: Boolean = false) {
    /** What it did to the balance: payments negative. */
    val signed: Int get() = if (kind == EventKind.PAYMENT) -amount else amount
}

/** A complete answer for the change: [movements] in the order shown; [usesLastDebit] = one of the payments is the card's last debit. */
data class MovementSuggestion(val movements: List<Movement>, val usesLastDebit: Boolean)

/**
 * The logic of "Was ist passiert?" ([OpenItem.CreditAndPayment]: a change no single known amount explains), pure. The change
 * is answered with a list of [Movement]s whose signed sum is the change; suggestions fill it from the card's last debit,
 * the top-up amounts and Pfand sums.
 */
object CreditDraft {
    /** Suggestions shown at most. */
    const val MAX_SUGGESTIONS = 3

    fun net(movements: List<Movement>): Int = movements.sumOf { it.signed }

    /** What is still open of [delta] (positive: a credit is missing, negative: a payment is missing). */
    fun rest(delta: Int, movements: List<Movement>): Int = delta - net(movements)

    /** The movements explain [delta] exactly and are each worth something. */
    fun complete(delta: Int, movements: List<Movement>): Boolean =
        movements.isNotEmpty() && movements.all { it.amount > 0 } && rest(delta, movements) == 0

    /**
     * The days the movements can have happened on: the previous scan's day to the scan's (both included, weekdays in
     * between), latest last. Only [scanDate] without an earlier previous scan.
     */
    fun days(previous: LocalDate?, scanDate: LocalDate): List<LocalDate> {
        if (previous == null || !previous.isBefore(scanDate)) return listOf(scanDate)
        return (MealDraft.weekdays(previous, scanDate) + previous + scanDate).distinct().sorted()
    }

    /**
     * The ways to explain a credit of [credit] (> 0): one top-up amount, one Pfand return, or a top-up plus a Pfand return
     * (top-ups ascending).
     */
    private fun credits(credit: Int, known: KnownAmounts): List<List<Pair<EventKind, Int>>> {
        if (credit <= 0) return emptyList()
        val out = mutableListOf<List<Pair<EventKind, Int>>>()
        if (credit in known.topUps) out += listOf(EventKind.TOP_UP to credit)
        if (known.isPfandSum(credit)) out += listOf(EventKind.PFAND to credit)
        known.topUps.distinct().sorted().filter { it < credit && known.isPfandSum(credit - it) }.forEach {
            out += listOf(EventKind.TOP_UP to it, EventKind.PFAND to credit - it)
        }
        return out
    }

    /**
     * The 1-3 most likely answers for a change of [delta] with the card's [lastDebit] after the scan. [debitChanged]: the last
     * debit differs from the previous scan's, so a payment certainly happened (null: unknown).
     *
     * - With the last debit L: L is one payment and the credit delta + L is a top-up, a Pfand return or both.
     * - Without a payment (only when the last debit didn't change): delta itself as a top-up, Pfand return or both.
     * - A top-up T and the payments T - delta (when those are more than L: the rest, then L, which was the last one; less than
 *   L only when the last debit didn't change).
     *
     * Suggestions using L come first when a payment happened; otherwise those without a payment come first. Days: every
     * movement on [day] (the scan's day; the screen lets the user move each one).
     */
    fun suggestions(
        delta: Int,
        lastDebit: Int?,
        debitChanged: Boolean?,
        known: KnownAmounts,
        day: LocalDate,
        max: Int = MAX_SUGGESTIONS,
    ): List<MovementSuggestion> {
        val l = lastDebit?.takeIf { it > 0 }
        fun of(list: List<Pair<EventKind, Int>>, usesL: Boolean) = MovementSuggestion(list.map { (k, a) -> Movement(k, a, day) }, usesL)
        val withL = if (l == null) emptyList() else credits(delta + l, known).map { of(listOf(EventKind.PAYMENT to l) + it, true) }
        val noPayment = if (delta > 0 && debitChanged != true) credits(delta, known).map { of(it, false) } else emptyList()
        // With a changed last debit, the last payment was L: a single smaller payment can't be what happened.
        val topUpAndPay = known.topUps.distinct().sorted().filter { it - delta > 0 && !(debitChanged == true && l != null && it - delta < l) }.map { t ->
            val paid = t - delta
            if (l != null && paid > l) of(listOf(EventKind.TOP_UP to t, EventKind.PAYMENT to paid - l, EventKind.PAYMENT to l), true)
            else of(listOf(EventKind.TOP_UP to t, EventKind.PAYMENT to paid), l != null && paid == l)
        }
        val ordered = if (debitChanged == false) noPayment + withL + topUpAndPay else withL + noPayment + topUpAndPay
        return ordered.distinctBy { s -> s.movements.map { it.kind to it.amount }.sortedWith(compareBy({ it.first }, { it.second })) }.take(max)
    }

    /**
     * A new movement of [kind]: what is still open when it has the right sign (a credit for a positive rest, a payment for
     * a negative one; a Pfand return at most [KnownAmounts.MAX_PFAND]), else null (the user types the amount).
     */
    fun prefill(kind: EventKind, rest: Int): Int? = when (kind) {
        EventKind.PAYMENT -> (-rest).takeIf { it > 0 }
        EventKind.TOP_UP -> rest.takeIf { it > 0 }
        EventKind.PFAND -> rest.takeIf { it in 1..KnownAmounts.MAX_PFAND }
    }

    /** Whether the last debit after scan [scanId] differs from the previous scan's (null: no previous scan or no last debit). */
    fun debitChanged(data: StatsData, scanId: Long): Boolean? {
        val index = data.scans.indexOfFirst { it.id == scanId }
        if (index <= 0) return null
        val scan = data.scans[index]
        val previous = data.scans.subList(0, index).lastOrNull { it.card == scan.card } ?: return null
        if (scan.lastDebit == null || previous.lastDebit == null) return null
        return scan.lastDebit != previous.lastDebit
    }
}
