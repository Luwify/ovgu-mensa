package de.luwify.mensamd.stats

import de.luwify.mensamd.model.MenuPage
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/** The decisions of the daily "scan your card" reminder, free of Android so they are unit-tested. */
object CardReminderRules {
    /** A run more than this late (the phone was off or dozing at the set time) reminds of nothing. */
    const val MAX_LATE_MINUTES = 180

    /** Delay from [now] to the next [runAt] (today if still ahead, else tomorrow), DST-correct. */
    fun delayUntilNextRun(now: ZonedDateTime, runAt: LocalTime): Duration {
        var next = now.with(runAt).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(runAt).atZone(now.zone)
        return Duration.between(now, next)
    }

    /**
     * Whether to remind at [now]: a weekday, at most [MAX_LATE_MINUTES] after [runAt] on the same day (a catch-up
     * after midnight belongs to a day that is over), a registered card ([cardHash]) without a scan today, and the default Mensa not known to be closed
     * today ([mensaHasMenuToday] false). Without statistics data nothing is reminded.
     */
    fun shouldRemind(now: LocalDateTime, runAt: LocalTime, data: StatsData?, mensaHasMenuToday: Boolean?): Boolean {
        val today = now.toLocalDate()
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) return false
        val late = now.toLocalTime().toSecondOfDay() / 60 - runAt.toSecondOfDay() / 60
        if (late !in 0..MAX_LATE_MINUTES) return false
        val card = data?.card ?: return false
        if (data.scans.any { it.card == card.hash && it.date == today }) return false
        return mensaHasMenuToday != false
    }

    /**
     * Whether the default Mensa serves [today], from its cached [page] fetched on [fetchedOn]: true with dishes for
     * today; false when the page (fetched today, or covering later days) has no dishes today, i.e. closed; null when the
     * page says nothing about today (no cache, old cache). A fixed menu (PIER 16) counts as open on weekdays.
     */
    fun menuToday(page: MenuPage?, fetchedOn: LocalDate?, today: LocalDate): Boolean? {
        if (page == null) return null
        if (page.days.isEmpty()) return if (page.fixed.any { it.dishes.isNotEmpty() }) true else if (fetchedOn == today) false else null
        val day = page.days.firstOrNull { it.date == today }
        return when {
            day != null -> day.dishes.isNotEmpty()
            fetchedOn == today || page.days.any { it.date.isAfter(today) } -> false
            else -> null
        }
    }
}
