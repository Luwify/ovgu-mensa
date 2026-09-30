package de.luwify.mensamd.ui

import de.luwify.mensamd.data.MenuState
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.MenuDay
import java.time.DayOfWeek
import java.time.LocalDate

/** What the menu screen renders. Pure Kotlin so it can be unit-tested. */
data class MenuUiState(
    /** Only days that have dishes, ascending by date. */
    val days: List<MenuDay>,
    val legend: Legend,
    /** False until a page exists (cache or fetch); tells "error/loading" from "published nothing". */
    val hasData: Boolean,
    val lastUpdatedMillis: Long?,
    val isRefreshing: Boolean,
    val refreshFailed: Boolean,
) {
    /** Nothing to show: no cache and no successful fetch yet (loading or error), or a closed Mensa. */
    val hasNoDays: Boolean get() = days.isEmpty()
}

fun MenuState.toUiState() = MenuUiState(
    days = page?.days.orEmpty().filter { it.dishes.isNotEmpty() },
    legend = page?.legend ?: Legend.EMPTY,
    hasData = page != null,
    lastUpdatedMillis = fetchedAt,
    isRefreshing = isRefreshing,
    refreshFailed = refreshFailed,
)

/** What the pager shows: no past days, no weekends, no empty days. Order is kept. */
fun List<MenuDay>.upcomingWeekdays(today: LocalDate): List<MenuDay> = filter {
    it.date >= today &&
        it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY &&
        it.dishes.isNotEmpty()
}
