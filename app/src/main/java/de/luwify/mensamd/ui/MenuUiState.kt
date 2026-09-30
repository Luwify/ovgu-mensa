package de.luwify.mensamd.ui

import de.luwify.mensamd.data.MenuState
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.MenuDay

/** What the menu screen renders. Pure Kotlin so it can be unit-tested. */
data class MenuUiState(
    /** Only days that have dishes, ascending by date. */
    val days: List<MenuDay>,
    val legend: Legend,
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
    lastUpdatedMillis = fetchedAt,
    isRefreshing = isRefreshing,
    refreshFailed = refreshFailed,
)
