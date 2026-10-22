package de.luwify.mensamd.ui

import de.luwify.mensamd.data.MenuState
import de.luwify.mensamd.model.FixedSection
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.MenuDay
import java.time.DayOfWeek
import java.time.LocalDate

/** What the menu screen renders. Pure Kotlin so it can be unit-tested. */
data class MenuUiState(
    /** Only days that have dishes, ascending by date. */
    val days: List<MenuDay>,
    /** Sections of a fixed (undated) menu such as PIER 16; empty for dated Mensen. */
    val fixed: List<FixedSection>,
    val legend: Legend,
    /** False until a page exists (cache or fetch); tells "error/loading" from "published nothing". */
    val hasData: Boolean,
    val lastUpdatedMillis: Long?,
    val isRefreshing: Boolean,
    val refreshFailed: Boolean,
    /** The data came from a fallback host: the footer says so. */
    val fromFallback: Boolean = false,
) {
    /** Nothing to show: no cache and no successful fetch yet (loading or error), or a closed Mensa. */
    val hasNoDays: Boolean get() = days.isEmpty()
}

fun MenuState.toUiState() = MenuUiState(
    days = page?.days.orEmpty().filter { it.dishes.isNotEmpty() },
    fixed = page?.fixed.orEmpty().filter { it.dishes.isNotEmpty() },
    legend = page?.legend ?: Legend.EMPTY,
    hasData = page != null,
    lastUpdatedMillis = fetchedAt,
    isRefreshing = isRefreshing,
    refreshFailed = refreshFailed,
    fromFallback = fromFallback,
)

/** What the pager shows: no past days, no weekends, no empty days. Order is kept. */
fun List<MenuDay>.upcomingWeekdays(today: LocalDate): List<MenuDay> = filter {
    it.date >= today &&
        it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY &&
        it.dishes.isNotEmpty()
}
