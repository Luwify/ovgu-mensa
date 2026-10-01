package de.luwify.mensamd.favorites

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.MenuPage
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** A favorite on the menu of [mensaId] on [date]. [key] is the favorite (normalized name). */
data class FavoriteHit(val mensaId: String, val date: LocalDate, val dish: Dish, val key: String) {
    /** What "already notified" remembers: the same dish, day and Mensa is never announced twice. */
    val notifiedKey: String get() = "$date|$mensaId|$key"
}

/** One notification: a single favorite, or several shown as a list. Texts are built by the Android side. */
data class NotificationPlan(
    val hits: List<FavoriteHit>,
    /** "Heute" instead of "Morgen" wording (morning run time). */
    val today: Boolean,
    /** The tab a tap opens: the Mensa of the first hit (hits follow the watched Mensen in tab order). */
    val openMensaId: String,
) {
    val isSingle: Boolean get() = hits.size == 1
}

/** The decisions of the daily favorites job, free of Android so they are unit-tested. */
object FavoriteNotifications {
    /** The default time of day the job runs; the user can pick another one in the settings. */
    val DEFAULT_RUN_AT: LocalTime = LocalTime.of(18, 0)

    /**
     * A run time before noon announces the same day ("Heute"), from noon on the next day ("Morgen"). A morning
     * reminder for tomorrow's menu would make no sense.
     */
    private val NOON: LocalTime = LocalTime.NOON

    /** Lunch is over: a same-day reminder that comes later than this (a late catch-up) is pointless. */
    private val LUNCH_END: LocalTime = LocalTime.of(14, 0)

    fun announcesToday(runAt: LocalTime): Boolean = runAt.isBefore(NOON)

    /** Delay from [now] to the next [runAt] (today if still ahead, else tomorrow), DST-correct. */
    fun delayUntilNextRun(now: ZonedDateTime, runAt: LocalTime = DEFAULT_RUN_AT): Duration {
        var next = now.with(runAt).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(runAt).atZone(now.zone)
        return Duration.between(now, next)
    }

    /**
     * The day a run at [now] announces, or null for a catch-up that came too late (the phone was off or offline at
     * [runAt]). Evening times ([runAt] from noon): the next calendar day, but not from runs before noon, when
     * "tomorrow" would already be the day after (the next evening run covers it). Morning times: the same day,
     * until lunch is over at 14:00.
     */
    fun targetDate(now: ZonedDateTime, runAt: LocalTime = DEFAULT_RUN_AT): LocalDate? {
        val time = now.toLocalTime()
        return if (announcesToday(runAt)) {
            if (time.isBefore(LUNCH_END)) now.toLocalDate() else null
        } else {
            if (time.isBefore(NOON)) null else now.toLocalDate().plusDays(1)
        }
    }

    /**
     * Favorites on the menu of [date], per Mensa in the order of [menus] (the watched Mensen in tab order), each
     * dish once per Mensa, minus everything in [alreadyNotified]. Weekends announce nothing: the app never shows
     * weekend days either. Fixed (undated) menus like PIER 16 have no days and so never match.
     */
    fun select(
        menus: List<Pair<String, MenuPage?>>,
        favorites: Set<String>,
        date: LocalDate,
        alreadyNotified: Set<String>,
    ): List<FavoriteHit> {
        if (favorites.isEmpty() || date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return emptyList()
        return menus.flatMap { (mensaId, page) ->
            val day = page?.days?.firstOrNull { it.date == date } ?: return@flatMap emptyList()
            day.dishes
                .map { FavoriteHit(mensaId, date, it, Favorites.key(it)) }
                .filter { it.key in favorites }
                .distinctBy { it.key }
                .filter { it.notifiedKey !in alreadyNotified }
        }
    }

    /** At most one notification per run; null when there is nothing to say. */
    fun plan(hits: List<FavoriteHit>, today: Boolean = false): NotificationPlan? =
        if (hits.isEmpty()) null else NotificationPlan(hits, today, hits.first().mensaId)

    /** Drops entries for days before [today], and anything unreadable, so the stored set stays small. */
    fun prune(notified: Set<String>, today: LocalDate): Set<String> = notified.filterTo(HashSet()) { entry ->
        val date = try {
            LocalDate.parse(entry.substringBefore('|'))
        } catch (_: DateTimeParseException) {
            null
        }
        date != null && !date.isBefore(today)
    }
}
