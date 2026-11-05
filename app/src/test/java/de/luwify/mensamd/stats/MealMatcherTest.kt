package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.stats.MealMatcher.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealMatcherTest {
    private fun dish(mensa: String, name: String, vararg cents: Int, known: Boolean = true) =
        SnapDish(mensa, LocalizedText(name), cents.toList(), known, listOf(Tag("vegan", "Vegan")))

    private val unten = "unicampus-unten"
    private val oben = "unicampus-oben"
    private val menu = listOf(
        dish(unten, "Seelachs", 310, 520, 620),
        dish(unten, "Burger", 440, 650, 760),
        dish(unten, "Pfannkuchen", 250, 460, 560),
        dish(unten, "Suppe", 130, 200, 250),
        dish(oben, "Pasta", 280, 490, 590),
        dish(oben, "Salat", 250, 400, 500),
    )

    private fun names(c: MealMatcher.Candidate) = c.picks.map { "${it.count}x${it.dish.name.de}" }

    @Test fun singleDishAtStudentPrice() {
        val plan = MealMatcher.plan(3_100, menu, unten, 0)
        assertEquals(listOf(listOf("1xSeelachs")), (plan as Plan.Choose).candidates.map(::names))
    }

    @Test fun priceGroupChangesTheMatch() {
        val plan = MealMatcher.plan(5_200, menu, unten, 1)
        assertEquals(listOf(listOf("1xSeelachs")), (plan as Plan.Choose).candidates.map(::names))
        assertEquals(Plan.NoMatch, MealMatcher.plan(5_200, menu, unten, 0))
    }

    @Test fun sameDishTwice() {
        val plan = MealMatcher.plan(8_800, menu, unten, 0) as Plan.Choose
        assertEquals(listOf(listOf("2xBurger")), plan.candidates.map(::names))
    }

    @Test fun twoDifferentDishes() {
        val plan = MealMatcher.plan(4_400 + 1_300, menu, unten, 0) as Plan.Choose
        assertEquals(listOf(listOf("1xBurger", "1xSuppe")), plan.candidates.map(::names))
        assertEquals(5_700, plan.candidates.single().total)
    }

    @Test fun defaultMensaFirstOthersOnlyWithoutMatch() {
        // 2.50 €: Pfannkuchen unten (default) and Salat oben; the default wins.
        val plan = MealMatcher.plan(2_500, menu, unten, 0) as Plan.Choose
        assertEquals(listOf(listOf("1xPfannkuchen")), plan.candidates.map(::names))
        // 2.80 €: only oben has it.
        val other = MealMatcher.plan(2_800, menu, unten, 0) as Plan.Choose
        assertEquals(oben, other.candidates.single().mensaId)
    }

    @Test fun ambiguousGivesSeveralCandidates() {
        val m = menu + dish(unten, "Gemüsecurry", 310, 520, 620)
        val plan = MealMatcher.plan(3_100, m, unten, 0) as Plan.Choose
        assertEquals(2, plan.candidates.size)
    }

    @Test fun tooManyAsksWhereThenMatchesAgain() {
        val many = (1..7).map { dish(oben, "Gericht $it", 300, 500, 600) } + dish(unten, "Seelachs", 310, 520, 620)
        val plan = MealMatcher.plan(3_000, many, unten, 0)
        assertEquals(Plan.AskWhere(listOf(unten, oben), emptySet()), plan)
        val again = MealMatcher.plan(3_000, many, unten, 0, where = setOf(oben)) as Plan.Choose
        assertEquals(7, again.candidates.size)
        assertEquals(Plan.NoMatch, MealMatcher.plan(3_000, many, unten, 0, where = setOf(unten)))
    }

    @Test fun lunchAndDinnerDuplicateIsOneChoice() {
        val plan = MealMatcher.plan(3_100, menu + dish(unten, "Seelachs", 310, 520, 620), unten, 0) as Plan.Choose
        assertEquals(1, plan.candidates.size)
    }

    @Test fun unknownPriceGroupsTryEveryPrice() {
        val pier = listOf(dish("pier16", "Bowl", 450, 650, known = false))
        assertTrue(MealMatcher.plan(6_500, pier, unten, 0) is Plan.Choose)
        assertTrue(MealMatcher.plan(4_500, pier, unten, 2) is Plan.Choose)
    }

    @Test fun singlePriceDishMatchesEveryGroup() {
        val cake = listOf(dish(unten, "Kuchen", 150))
        assertTrue(MealMatcher.plan(1_500, cake, unten, 2) is Plan.Choose)
    }

    @Test fun extrasTakeTheRest() {
        val pick = MealMatcher.Pick(menu[0], 1, 3_100)
        val record = MealMatcher.withExtras(4_250, listOf(pick), listOf(Extras.SALAD, Extras.DRINK))!!
        assertEquals(1_150, record.extrasAmount)
        assertEquals("seelachs", record.dishes.single().key)
        assertEquals(listOf(Extras.SALAD, Extras.DRINK), record.extras)
        assertNull(MealMatcher.withExtras(4_250, listOf(pick), emptyList())) // rest without an extra
        assertNull(MealMatcher.withExtras(3_000, listOf(pick), listOf(Extras.SALAD))) // dish costs more
        assertEquals(0, MealMatcher.withExtras(3_100, listOf(pick), emptyList())!!.extrasAmount)
        assertEquals(4_250, MealMatcher.withExtras(4_250, emptyList(), listOf(Extras.SALAD))!!.extrasAmount) // only extras
    }

    @Test fun candidateRecordKeepsTagsAndMensa() {
        val c = (MealMatcher.plan(3_100, menu, unten, 0) as Plan.Choose).candidates.single()
        val eaten = c.toRecord().dishes.single()
        assertEquals(unten, eaten.mensaId)
        assertEquals(3_100, eaten.price)
        assertEquals("vegan", eaten.tags.single().key)
    }
}
