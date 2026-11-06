package de.luwify.mensamd.stats

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.FixedSection
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.MenuPage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MenuDishesTest {
    private val friday = LocalDate.of(2026, 11, 6)
    private val monday = LocalDate.of(2026, 11, 9)

    private fun dish(name: String, vararg cents: Int) =
        Dish(LocalizedText(name), emptyList(), cents.toList(), emptyList(), emptyList(), null, null)

    private val oben = MenuPage(
        listOf(MenuDay(friday, listOf(dish("Chili", 200))), MenuDay(monday, listOf(dish("Pasta", 300)))),
        Legend.EMPTY,
    )
    private val pier = MenuPage(emptyList(), Legend.EMPTY, listOf(FixedSection(null, listOf(dish("Bowl", 450)))))
    private val pages = listOf("oben" to oben, "pier16" to pier)

    @Test fun aDayHasItsOwnDishesOfEveryMensaAndTheFixedList() {
        assertEquals(listOf("oben" to "Chili", "pier16" to "Bowl"), MenuDishes.onDay(pages, friday).map { it.mensaId to it.name.de })
        assertEquals(listOf("pier16" to "Bowl"), MenuDishes.onDay(pages, friday.plusDays(1)).map { it.mensaId to it.name.de })
    }

    @Test fun allHasEveryDay() {
        assertEquals(listOf("Chili", "Pasta", "Bowl"), MenuDishes.all(pages).map { it.name.de })
    }

    /** The correction of an answered payment has no kept menu: the cached pages must fill the question. */
    @Test fun aQuestionWithoutAKeptMenuUsesTheCachedDay() {
        val merged = MenuDishes.merge(emptyList(), MenuDishes.onDay(pages, friday))
        assertEquals(2, merged.size)
        val plan = MealMatcher.plan(2_000, merged, "unten", 0) as MealMatcher.Plan.Choose
        assertEquals("Chili", plan.candidates.single().picks.single().dish.name.de)
    }

    @Test fun aKeptMenuSurvivesThePageAndCountsOnce() {
        val kept = MenuDishes.onDay(pages, friday)
        assertEquals(kept, MenuDishes.merge(kept, emptyList()))
        assertEquals(kept, MenuDishes.merge(kept, MenuDishes.onDay(pages, friday)))
    }
}
