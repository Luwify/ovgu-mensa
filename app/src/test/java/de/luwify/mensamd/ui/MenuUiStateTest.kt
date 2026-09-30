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

    @Test
    fun hasData_isFalseOnlyWithoutPage() {
        assertEquals(false, MenuState().toUiState().hasData)
        assertEquals(true, MenuState(MenuPage(emptyList(), Legend.EMPTY)).toUiState().hasData)
    }

    @Test
    fun upcomingWeekdays_hidesPastWeekendAndEmptyDays_keepsToday() {
        // 2026-10-01 is a Thursday; 03.10. is a Saturday, 04.10. a Sunday.
        val days = listOf(day(1, dish), day(2, dish), day(3, dish), day(4, dish), day(5), day(6, dish), day(8, dish))
        val today = LocalDate.of(2026, 10, 2)
        assertEquals(listOf(2, 6, 8), days.upcomingWeekdays(today).map { it.date.dayOfMonth })
    }

    @Test
    fun upcomingWeekdays_onAWeekendOpensOnMonday() {
        val days = listOf(day(2, dish), day(5, dish))
        assertEquals(listOf(5), days.upcomingWeekdays(LocalDate.of(2026, 10, 3)).map { it.date.dayOfMonth })
    }
}
