package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.parser.MenuPageParser
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
    private fun found(amount: Int, dishes: List<SnapDish> = menu, group: Int = 0) = MealMatcher.candidates(amount, dishes, group).map(::names)

    @Test fun singleDishAtStudentPrice() = assertEquals(listOf(listOf("1xSeelachs")), found(3_100))

    @Test fun priceGroupChangesTheMatch() {
        assertEquals(listOf(listOf("1xSeelachs")), found(5_200, group = 1))
        assertEquals(emptyList<List<String>>(), found(5_200))
    }

    @Test fun sameDishTwice() = assertEquals(listOf(listOf("2xBurger")), found(8_800))

    @Test fun twoDifferentDishesOfOneMensa() {
        assertEquals(listOf(listOf("1xBurger", "1xSuppe")), found(4_400 + 1_300))
        assertEquals(5_700, MealMatcher.candidates(5_700, menu, 0).single().total)
        // Seelachs (unten) + Pasta (oben): never a pair across Mensen.
        assertEquals(emptyList<List<String>>(), found(3_100 + 2_800))
    }

    @Test fun everyMensaCounts() {
        assertEquals(listOf(unten, oben), MealMatcher.candidates(2_500, menu, 0).map { it.mensaId })
        assertEquals(listOf(listOf("1xPasta")), found(2_800))
    }

    @Test fun onlyOneCandidateOverallIsSingle() {
        assertEquals(listOf("1xSeelachs"), names(MealMatcher.single(3_100, menu, 0)!!))
        assertNull(MealMatcher.single(2_500, menu, 0)) // Pfannkuchen unten and Salat oben
        assertNull(MealMatcher.single(9_999, menu, 0))
    }

    /** The real case of 2026-11-06: Pommes at the default Mensa and the Chili that was eaten both cost 2.00 €. */
    @Test fun realMenusOfTwoMensenAreNotSavedBlindly() {
        fun day(mensa: String) = MenuPageParser.parse(fixture("2026-11-06/$mensa.html")).days
            .first { it.date == LocalDate.of(2026, 11, 6) }.dishes
            .map { SnapDish(mensa, it.name, it.prices, it.priceGroupsKnown, it.tags) }
        val all = MealMatcher.candidates(2_000, day(unten) + day(oben), 0)
        assertNull(MealMatcher.single(2_000, day(unten) + day(oben), 0))
        assertTrue(all.any { c -> c.mensaId == unten && c.picks.single().dish.name.de.startsWith("Portion Pommes Frites") })
        assertTrue(all.any { c -> c.mensaId == oben && c.picks.singleOrNull()?.dish?.name?.de.orEmpty().startsWith("Chili sin carne") })
    }

    @Test fun lunchAndDinnerDuplicateIsOneChoice() = assertEquals(1, found(3_100, menu + dish(unten, "Seelachs", 310, 520, 620)).size)

    @Test fun unknownPriceGroupsTryEveryPrice() {
        val pier = listOf(dish("pier16", "Bowl", 450, 650, known = false))
        assertEquals(1, found(6_500, pier).size)
        assertEquals(1, found(4_500, pier, group = 2).size)
    }

    @Test fun singlePriceDishMatchesEveryGroup() = assertEquals(1, found(1_500, listOf(dish(unten, "Kuchen", 150)), group = 2).size)

    @Test fun candidateRecordKeepsTagsAndMensa() {
        val eaten = MealMatcher.candidates(3_100, menu, 0).single().toRecord().dishes.single()
        assertEquals(unten, eaten.mensaId)
        assertEquals(3_100, eaten.price)
        assertEquals("vegan", eaten.tags.single().key)
    }

    private val pasta = SnapDish(oben, LocalizedText("Farfalle al Fungo"), listOf(50, 80, 110), byWeight = true)
    private val soup = SnapDish(unten, LocalizedText("Tagessuppe"), listOf(60, 80, 100), byWeight = true)

    @Test fun aWeightDishNeverMatchesAsAFixedPrice() {
        val day = menu + pasta + soup
        for (amount in listOf(500, 600, 1_000, 1_100, 3_600)) assertEquals("$amount", emptyList<List<String>>(), found(amount, day))
        assertEquals(listOf(listOf("1xSeelachs")), found(3_100, day))
    }

    @Test fun estimateRoundsToTenGrams() {
        assertEquals(470, MealMatcher.estimateGrams(2_350, 500))
        assertEquals(470, MealMatcher.estimateGrams(2_370, 500)) // 474
        assertEquals(480, MealMatcher.estimateGrams(2_380, 500)) // 476
        assertEquals(10, MealMatcher.estimateGrams(10, 500))
    }
}
