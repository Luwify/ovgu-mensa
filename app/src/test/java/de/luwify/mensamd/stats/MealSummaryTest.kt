package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MealSummaryTest {
    private val day = LocalDate.of(2026, 11, 5)
    private val vegan = Tag("vegan", "Vegan")
    private val veggie = Tag("vegetarisch", "Vegetarisch")
    private var id = 0L
    private fun dish(name: String, mensa: String?, price: Int, count: Int = 1, vararg tags: Tag) =
        EatenDish(name.lowercase(), LocalizedText(name), mensa, price, count, tags.toList())
    private fun pay(amount: Int, meal: MealRecord?) = CardEvent(++id, 0, EventKind.PAYMENT, amount, day, true, meal)

    private val events = listOf(
        pay(3_100, MealRecord(listOf(dish("Curry", "unten", 3_100, 1, vegan)))),
        pay(6_200, MealRecord(listOf(dish("Curry", "unten", 3_100, 2, vegan)))),
        pay(4_400, MealRecord(listOf(dish("Burger", "oben", 4_400)))),
        pay(5_000, MealRecord(listOf(dish("Pasta", "unten", 2_800, 1, veggie)), listOf(Extras.SALAD), 2_200)),
        pay(1_200, MealRecord(other = true)),
        pay(900, null),
        CardEvent(++id, 0, EventKind.TOP_UP, 20_000, day, true),
    )

    @Test fun mostEatenByServings() {
        val s = MealSummary.summarize(events)
        assertEquals(listOf("curry" to 3, "burger" to 1, "pasta" to 1), s.top.map { it.key to it.count })
        assertEquals(5, s.servings)
    }

    @Test fun byMensa() = assertEquals(listOf("unten" to 4, "oben" to 1), MealSummary.summarize(events).byMensa)

    @Test fun sharesVegetarianIncludesVegan() {
        val s = MealSummary.summarize(events)
        assertEquals(3.0 / 5, s.veganShare!!, 1e-9)
        assertEquals(4.0 / 5, s.vegetarianShare!!, 1e-9)
    }

    @Test fun averagePerMealIgnoresOtherAndUnmatched() {
        val s = MealSummary.summarize(events)
        assertEquals((3_100 + 6_200 + 4_400 + 5_000) / 4, s.averagePerMeal)
        assertEquals(4, s.mealPayments)
    }

    @Test fun emptySummary() {
        val s = MealSummary.summarize(emptyList())
        assertNull(s.veganShare)
        assertNull(s.averagePerMeal)
    }

    @Test fun knownDishes() {
        val custom = listOf(KnownDish("smoothie bowl", LocalizedText("Smoothie Bowl"), 3_500))
        assertEquals(listOf("burger", "curry", "pasta", "smoothie bowl"), MealSummary.knownDishes(events, custom).map { it.key })
    }
}
