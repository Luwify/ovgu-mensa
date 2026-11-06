package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.parser.MenuPageParser
import de.luwify.mensamd.stats.MealMatcher.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    private fun fixture(path: String) =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$path")).use { it.readBytes().toString(Charsets.UTF_8) }

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

    @Test fun aMatchAtTheDefaultMensaDoesNotHideTheOthers() {
        // 2.50 €: Pfannkuchen unten (default) and Salat oben: both are offered, the default first.
        val plan = MealMatcher.plan(2_500, menu, unten, 0) as Plan.Choose
        assertEquals(listOf(unten, oben), plan.candidates.map { it.mensaId })
        // The other way round with oben as the default, even though unten comes first in the menu list.
        val swapped = MealMatcher.plan(2_500, menu, oben, 0) as Plan.Choose
        assertEquals(listOf(oben, unten), swapped.candidates.map { it.mensaId })
        // 2.80 €: only oben has it, so it is the one candidate although it isn't the default Mensa.
        val other = MealMatcher.plan(2_800, menu, unten, 0) as Plan.Choose
        assertEquals(oben, other.candidates.single().mensaId)
    }

    @Test fun theSameDishInTwoMensenIsTwoCandidates() {
        val m = listOf(dish(unten, "Kuchen", 150), dish(oben, "Kuchen", 150))
        val plan = MealMatcher.plan(1_500, m, unten, 0) as Plan.Choose
        assertEquals(listOf(unten, oben), plan.candidates.map { it.mensaId })
    }

    @Test fun tooManyAcrossMensenAsksWhereWithTheDefaultTicked() {
        val m = (1..3).map { dish(unten, "Unten $it", 300) } + (1..3).map { dish(oben, "Oben $it", 300) }
        assertEquals(Plan.AskWhere(listOf(unten, oben), setOf(unten)), MealMatcher.plan(3_000, m, unten, 0))
        val onlyOben = MealMatcher.plan(3_000, m, unten, 0, where = setOf(oben)) as Plan.Choose
        assertEquals(setOf(oben), onlyOben.candidates.map { it.mensaId }.toSet())
    }

    /** The real case of 2026-11-06: Pommes at the default Mensa and the Chili that was eaten both cost 2.00 €. */
    @Test fun realMenusOfTwoMensenAreNotSavedBlindly() {
        fun day(mensa: String) = MenuPageParser.parse(fixture("2026-11-06/$mensa.html")).days
            .first { it.date == LocalDate.of(2026, 11, 6) }.dishes
            .map { SnapDish(mensa, it.name, it.prices, it.priceGroupsKnown, it.tags) }
        val plan = MealMatcher.plan(2_000, day(unten) + day(oben), unten, 0) as Plan.Choose
        assertEquals(unten, plan.candidates.first().mensaId)
        assertTrue(plan.candidates.first().picks.single().dish.name.de.startsWith("Portion Pommes Frites"))
        assertTrue(plan.candidates.any { c -> c.mensaId == oben && c.picks.singleOrNull()?.dish?.name?.de.orEmpty().startsWith("Chili sin carne") })
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

    @Test fun correctionListHasPriceMatchesFirstThenEverythingElseOnceWithItsMensa() {
        val m = menu + dish(unten, "Suppe", 130, 200, 250) // lunch and dinner: one row
        val matched = (MealMatcher.plan(2_500, m, oben, 0) as Plan.Choose).candidates
        assertEquals(listOf(listOf("1xSalat"), listOf("1xPfannkuchen")), matched.map(::names))
        val others = MealMatcher.others(matched, m, oben)
        assertEquals(listOf(oben to "Pasta", unten to "Seelachs", unten to "Burger", unten to "Suppe"), others.map { it.mensaId to it.name.de })
        assertEquals(listOf(oben to "Pasta", oben to "Salat"), MealMatcher.others(emptyList(), m, unten, where = setOf(oben)).map { it.mensaId to it.name.de })
    }

    @Test fun aDishInAPairStaysInTheCorrectionList() {
        val matched = (MealMatcher.plan(5_700, menu, unten, 0) as Plan.Choose).candidates // Burger + Suppe
        assertTrue(MealMatcher.others(matched, menu, unten).any { it.name.de == "Burger" })
    }

    @Test fun aDishPickedFromTheListTakesTheWholePaymentAndKeepsItsMensa() {
        val eaten = MealMatcher.asPayment(menu[4], 2_000).dishes.single()
        assertEquals("pasta", eaten.key)
        assertEquals(oben, eaten.mensaId)
        assertEquals(2_000, eaten.price)
        assertEquals("vegan", eaten.tags.single().key)
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

    private val pasta = SnapDish(oben, LocalizedText("Farfalle al Fungo"), listOf(50, 80, 110), byWeight = true)
    private val soup = SnapDish(unten, LocalizedText("Tagessuppe"), listOf(60, 80, 100), byWeight = true)

    @Test fun aWeightDishNeverMatchesAsAFixedPrice() {
        val day = menu + pasta + soup
        assertEquals(Plan.NoMatch, MealMatcher.plan(500, day, unten, 0)) // the pasta's menu price
        assertEquals(Plan.NoMatch, MealMatcher.plan(600, day, unten, 0)) // the soup's menu price
        assertEquals(Plan.NoMatch, MealMatcher.plan(1_000, day, unten, 0)) // twice the pasta
        assertEquals(Plan.NoMatch, MealMatcher.plan(1_100, day, unten, 0)) // pasta + soup
        assertEquals(listOf(listOf("1xSeelachs")), (MealMatcher.plan(3_100, day, unten, 0) as Plan.Choose).candidates.map(::names))
        assertEquals(Plan.NoMatch, MealMatcher.plan(3_600, day, unten, 0)) // Seelachs + pasta: no pair
    }

    @Test fun weightDishAlone() {
        val options = MealMatcher.weightDishes(menu + pasta + soup + pasta, unten, 0)
        assertEquals(listOf("Tagessuppe", "Farfalle al Fungo"), options.map { it.dish.name.de }) // default Mensa first, once each
        val farfalle = options[1]
        assertEquals(500, farfalle.per100)
        assertEquals(470, farfalle.grams(2_350))
        val eaten = farfalle.toRecord(2_350).dishes.single()
        assertEquals(2_350, eaten.price) // what was paid, not the price per 100 g
        assertEquals(470, eaten.grams)
        assertEquals(1, eaten.count)
    }

    @Test fun weightPriceFollowsThePriceGroup() {
        assertEquals(800, MealMatcher.weightDishes(listOf(pasta), null, 1).single().per100)
        assertEquals(300, MealMatcher.weightDishes(listOf(pasta), null, 1).single().grams(2_400))
    }

    @Test fun estimateRoundsToTenGrams() {
        assertEquals(470, MealMatcher.estimateGrams(2_350, 500))
        assertEquals(470, MealMatcher.estimateGrams(2_370, 500)) // 474
        assertEquals(480, MealMatcher.estimateGrams(2_380, 500)) // 476
        assertEquals(10, MealMatcher.estimateGrams(10, 500))
    }

    @Test fun weightDishAfterAFixedDish() {
        val weight = MealMatcher.weightDishes(listOf(pasta), null, 0).single()
        val fixed = MealMatcher.Pick(menu[0], 1, 3_100)
        val record = MealMatcher.withExtras(5_450, listOf(fixed), emptyList(), listOf(MealMatcher.WeightPick(weight)))!!
        assertEquals(listOf(3_100, 2_350), record.dishes.map { it.price })
        assertEquals(listOf(null, 470), record.dishes.map { it.grams })
        assertEquals(0, record.extrasAmount)
    }

    @Test fun weightDishPlusFixedDishPlusExtras() {
        val weight = MealMatcher.weightDishes(listOf(pasta), null, 0).single()
        val fixed = MealMatcher.Pick(menu[0], 1, 3_100)
        val record = MealMatcher.withExtras(6_000, listOf(fixed), listOf(Extras.SALAD), listOf(MealMatcher.WeightPick(weight, 300)))!!
        assertEquals(listOf(3_100, 1_500), record.dishes.map { it.price })
        assertEquals(300, record.dishes[1].grams)
        assertEquals(1_400, record.extrasAmount)
        assertEquals(6_000, record.dishes.sumOf { it.price * it.count } + record.extrasAmount)
    }

    @Test fun weightPicksThatDoNotAddUpAreRefused() {
        val (a, b) = MealMatcher.weightDishes(listOf(pasta, soup), null, 0)
        val open = MealMatcher.WeightPick(a)
        assertNull(MealMatcher.withExtras(2_000, emptyList(), emptyList(), listOf(open, MealMatcher.WeightPick(b)))) // two take the rest
        assertNull(MealMatcher.withExtras(1_000, emptyList(), emptyList(), listOf(MealMatcher.WeightPick(a, 300)))) // costs more
        assertNull(MealMatcher.withExtras(3_100, listOf(MealMatcher.Pick(menu[0], 1, 3_100)), emptyList(), listOf(open))) // nothing left
        assertNull(MealMatcher.withExtras(2_000, emptyList(), emptyList(), listOf(MealMatcher.WeightPick(a, 100)))) // rest without an extra
    }

    @Test fun otherDishesLeaveOutTheWeightDishes() {
        assertEquals(false, MealMatcher.others(emptyList(), menu + pasta, unten).any { it.byWeight })
    }
}
