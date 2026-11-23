package de.luwify.mensamd.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Numbers for the statistics overview, from [StatsData]. Pure; amounts in mills. */
object StatsSummary {
    enum class Unit { WEEK, MONTH }

    /** One bar group: the week (starting Monday) or month starting at [start]. */
    data class Period(val start: LocalDate, val spent: Int, val toppedUp: Int)

    data class Weekday(val day: DayOfWeek, val spent: Int, val payments: Int)

    data class BalancePoint(val at: Long, val balance: Int)

    data class Totals(
        val spentThisMonth: Int,
        val toppedUpThisMonth: Int,
        val pfandThisMonth: Int,
        /**
         * Spending per visit: a "visit" is one payment, and a payment that stands for at least two ([CardEvent.payments])
         * counts as that many. Null without payments.
         */
        val averagePerVisit: Int?,
        /** Spending per day with at least one payment (all payments; an undated one counts on its scan's day). */
        val averagePerDay: Int?,
        val payments: Int,
        val currentBalance: Int?,
    )

    fun periodStart(date: LocalDate, unit: Unit): LocalDate = when (unit) {
        Unit.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Unit.MONTH -> date.withDayOfMonth(1)
    }

    /** The last [count] periods up to the one containing [today], oldest first; empty periods included. */
    fun periods(events: List<CardEvent>, unit: Unit, today: LocalDate, count: Int): List<Period> {
        val last = periodStart(today, unit)
        val starts = (count - 1 downTo 0).map { i -> if (unit == Unit.WEEK) last.minusWeeks(i.toLong()) else last.minusMonths(i.toLong()) }
        val byStart = events.groupBy { periodStart(it.date, unit) }
        return starts.map { start ->
            val list = byStart[start].orEmpty()
            Period(start, list.sumOfKind(EventKind.PAYMENT), list.sumOfKind(EventKind.TOP_UP))
        }
    }

    /**
     * Spending per weekday, only payments whose day is known. Monday to Friday always; Saturday and Sunday only when
     * there was a payment then.
     */
    fun weekdays(events: List<CardEvent>): List<Weekday> {
        val payments = events.filter { it.kind == EventKind.PAYMENT && it.dateKnown }
        return DayOfWeek.entries.mapNotNull { day ->
            val list = payments.filter { it.date.dayOfWeek == day }
            if (list.isEmpty() && day.value >= 6) null else Weekday(day, list.sumOf { it.amount }, list.sumOf { it.payments })
        }
    }

    /** The balance at every scan of the current card ([card] = its hash; null = every scan), oldest first. */
    fun balance(scans: List<Scan>, card: String?): List<BalancePoint> =
        scans.filter { card == null || it.card == card }.map { BalancePoint(it.at, it.balance) }

    fun totals(data: StatsData, today: LocalDate): Totals {
        val month = periodStart(today, Unit.MONTH)
        val thisMonth = data.events.filter { periodStart(it.date, Unit.MONTH) == month }
        val payments = data.events.filter { it.kind == EventKind.PAYMENT }
        val spent = payments.sumOf { it.amount.toLong() }
        // A payment kept as "at least 2" counts as that many visits; a day counts once however many payments it had.
        val visits = payments.sumOf { it.payments }
        val days = payments.map { it.date }.distinct().size
        return Totals(
            spentThisMonth = thisMonth.sumOfKind(EventKind.PAYMENT),
            toppedUpThisMonth = thisMonth.sumOfKind(EventKind.TOP_UP),
            pfandThisMonth = thisMonth.sumOfKind(EventKind.PFAND),
            averagePerVisit = visits.takeIf { it > 0 }?.let { (spent / it).toInt() },
            averagePerDay = days.takeIf { it > 0 }?.let { (spent / it).toInt() },
            payments = visits,
            currentBalance = data.scans.lastOrNull { data.card == null || it.card == data.card.hash }?.balance
                ?: data.scans.lastOrNull()?.balance,
        )
    }

    private fun List<CardEvent>.sumOfKind(kind: EventKind) = filter { it.kind == kind }.sumOf { it.amount }
}
