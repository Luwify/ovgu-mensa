package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        pay(5_000, MealRecord(listOf(dish("Pasta", "unten", 2_800, 1, veggie)), listOf(ExtraItem(Extras.SALAD, 2_200)))),
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

    @Test fun weightMealsCountAsMealsAndTheAverageUsesWhatWasPaid() {
        val farfalle = EatenDish("farfalle", LocalizedText("Farfalle"), "oben", 2_350, 1, emptyList(), grams = 470)
        val s = MealSummary.summarize(listOf(pay(2_350, MealRecord(listOf(farfalle))), pay(3_650, MealRecord(listOf(dish("Curry", "unten", 3_650))))))
        assertEquals(2, s.mealPayments)
        assertEquals(2, s.servings)
        assertEquals(3_000, s.averagePerMeal) // (2.35 + 3.65) / 2, not the 0.50 per 100 g
        assertEquals(listOf("oben" to 1, "unten" to 1), s.byMensa.sortedBy { it.first })
    }

    private fun own(name: String, mensa: String?, vararg tags: Tag) =
        EatenDish(name.lowercase(), LocalizedText(name), mensa, 3_200, 1, tags.toList(), own = true)

    /** The Shawarma of 2026-11-09: saved by hand without tags, it must not count as "not vegan". */
    @Test fun dishesWithoutTagInformationAreLeftOutOfTheShares() {
        val s = MealSummary.summarize(
            listOf(
                pay(3_100, MealRecord(listOf(dish("Curry", "unten", 3_100, 1, vegan)))),
                pay(4_400, MealRecord(listOf(dish("Burger", "oben", 4_400)))), // a menu dish: known to carry no tag
                pay(3_200, MealRecord(listOf(own("Shawarma", null)))),
            ),
        )
        assertEquals(3, s.servings)
        assertEquals(1, s.unknownTags)
        assertEquals(1.0 / 2, s.veganShare!!, 1e-9)
        assertEquals(1.0 / 2, s.vegetarianShare!!, 1e-9)
    }

    @Test fun aVeganDishAlsoCountsAsVegetarian() {
        val s = MealSummary.summarize(listOf(pay(3_200, MealRecord(listOf(own("Shawarma", "unten", vegan)))), pay(4_400, MealRecord(listOf(dish("Burger", "oben", 4_400))))))
        assertEquals(1.0 / 2, s.veganShare!!, 1e-9)
        assertEquals(1.0 / 2, s.vegetarianShare!!, 1e-9)
        assertEquals(0, s.unknownTags)
    }

    @Test fun aHandNamedDishWithTagsCountsAndAMensaAloneIsNoInformation() {
        val withMensaOnly = own("Wrap", "unten")
        assertEquals(false, withMensaOnly.tagsKnown)
        assertEquals(true, own("Wrap", "unten", veggie).tagsKnown)
        val s = MealSummary.summarize(listOf(pay(3_200, MealRecord(listOf(withMensaOnly, own("Bowl", null, veggie))))))
        assertEquals(1, s.unknownTags)
        assertEquals(1.0, s.vegetarianShare!!, 1e-9)
        assertEquals(0.0, s.veganShare!!, 1e-9)
    }

    @Test fun noSharesWhenNothingHasTagInformation() {
        val s = MealSummary.summarize(listOf(pay(3_200, MealRecord(listOf(own("Shawarma", null)).let { it + own("Wrap", "unten") }))))
        assertNull(s.veganShare)
        assertNull(s.vegetarianShare)
        assertEquals(2, s.unknownTags)
        assertEquals(2, s.servings)
    }

    @Test fun servingsCountTimesTheDish() {
        val twice = own("Shawarma", null).copy(count = 2)
        assertEquals(2, MealSummary.summarize(listOf(pay(6_400, MealRecord(listOf(twice))))).unknownTags)
    }

    @Test fun dishesWithoutMensaAreListedNewestFirstWithTheirPlace() {
        val a = pay(3_200, MealRecord(listOf(dish("Curry", "unten", 3_200), own("Shawarma", null))))
        val b = pay(3_000, MealRecord(listOf(own("Wrap", null))))
        val refs = MealSummary.withoutMensa(listOf(a, b, pay(900, null)))
        assertEquals(listOf(b.id to 0, a.id to 1), refs.map { it.eventId to it.index })
        assertEquals(listOf("Wrap", "Shawarma"), refs.map { it.dish.name.de })
    }

    @Test fun dessertsHaveTheirOwnSummaryAndAreNoDishesOfTheMeals() {
        val pudding = EatenDish("pudding", LocalizedText("Pudding"), null, 500, 1, emptyList(), grams = 180, own = true, dessert = true)
        val grutze = EatenDish("rote grütze", LocalizedText("Rote Grütze"), null, 600, 1, emptyList(), own = true, dessert = true)
        val list = listOf(
            pay(3_000, MealRecord(listOf(dish("Curry", "unten", 2_500), pudding))),
            pay(1_100, MealRecord(listOf(pudding.copy(grams = 200, price = 600), grutze.copy(price = 500)))),
        )
        val s = MealSummary.summarize(list)
        assertEquals(listOf("curry"), s.top.map { it.key })
        assertEquals(1, s.mealPayments) // desserts only: no meal
        val d = MealSummary.desserts(list)
        assertEquals(1_600, d.spent)
        assertEquals(3, d.count)
        assertEquals(380 to 2, d.grams to d.weighed)
        assertEquals(listOf("pudding" to 1_100, "rote grütze" to 500), d.list.map { it.key to it.spent })
        assertTrue(MealSummary.withoutMensa(list).none { it.dish.dessert })
    }

    /** Pfand paid with a drink is no spending; a payment of Pfand only is no meal. */
    @Test fun pfandIsNotSpending() {
        val drink = pay(1_050, MealRecord(listOf(dish("Saft", "unten", 900)), listOf(ExtraItem(Extras.PFAND, 150))))
        val onlyPfand = pay(250, MealRecord(extras = listOf(ExtraItem(Extras.PFAND, 250))))
        val s = MealSummary.summarize(listOf(drink, onlyPfand))
        assertEquals(1, s.mealPayments)
        assertEquals(900, s.averagePerMeal)
    }

}
