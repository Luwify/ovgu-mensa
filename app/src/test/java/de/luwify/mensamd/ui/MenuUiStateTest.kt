package de.luwify.mensamd.ui

import de.luwify.mensamd.data.MenuState
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.MenuPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MenuUiStateTest {
    private val dish = Dish(LocalizedText("Suppe"), emptyList(), emptyList(), emptyList(), emptyList(), null, null)
    private fun day(d: Int, vararg dishes: Dish) = MenuDay(LocalDate.of(2026, 10, d), dishes.toList())

    @Test
    fun emptyDaysAreDropped_orderAndOthersKept() {
        val page = MenuPage(listOf(day(5, dish), day(6), day(7, dish, dish)), Legend.EMPTY)
        val ui = MenuState(page, fetchedAt = 42L, isRefreshing = true, refreshFailed = true).toUiState()
        assertEquals(listOf(5, 7), ui.days.map { it.date.dayOfMonth })
        assertEquals(42L, ui.lastUpdatedMillis)
        assertTrue(ui.isRefreshing && ui.refreshFailed)
    }

    @Test
    fun noPageOrOnlyEmptyDays_hasNoDays() {
        assertTrue(MenuState().toUiState().hasNoDays)
        assertTrue(MenuState(MenuPage(listOf(day(5)), Legend.EMPTY)).toUiState().hasNoDays)
    }
}
