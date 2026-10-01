package de.luwify.mensamd.ui

import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.model.isCakeOfTheDay
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DishFiltersTest {
    private fun dish(name: String, vararg tags: String) = Dish(
        LocalizedText(name), emptyList(), emptyList(), tags.map { Tag(it, it.uppercase()) }, emptyList(), null, null,
    )

    private val vegan = dish("Linsen", "vegan")
    private val veggie = dish("Käsespätzle", "vegetarisch")
    private val pork = dish("Schnitzel", "schwein", "knoblauch")
    private val fish = dish("Lachs", "fisch")
    private val plain = dish("Brot")
    private val day = MenuDay(LocalDate.of(2026, 10, 5), listOf(vegan, veggie, pork, fish, plain))

    private fun names(filters: Set<String>) = DishFilters.apply(listOf(day), filters).single().dishes.map { it.name.de }

    @Test fun noFilters_showsEverything() = assertEquals(listOf("Linsen", "Käsespätzle", "Schnitzel", "Lachs", "Brot"), names(emptySet()))

    @Test fun vegan_isInclusive() = assertEquals(listOf("Linsen"), names(setOf("vegan")))

    @Test fun vegetarian_includesVegan() = assertEquals(listOf("Linsen", "Käsespätzle"), names(setOf("vegetarisch")))

    @Test fun veganAndVegetarianTogether_meansVegan() = assertEquals(listOf("Linsen"), names(setOf("vegan", "vegetarisch")))

    @Test fun meatTag_hidesDishesWithThatTag() = assertEquals(listOf("Linsen", "Käsespätzle", "Lachs", "Brot"), names(setOf("schwein")))

    @Test fun otherTag_hidesToo_andFiltersCombine() {
        assertEquals(listOf("Linsen", "Käsespätzle", "Lachs", "Brot"), names(setOf("knoblauch")))
        assertEquals(listOf("Linsen", "Käsespätzle", "Brot"), names(setOf("schwein", "fisch")))
    }

    @Test fun inclusivePlusExclusive() {
        assertEquals(listOf("Linsen", "Käsespätzle"), names(setOf("vegetarisch", "schwein")))
    }

    @Test
    fun dayWithEverythingFilteredOut_isKeptWithNoDishes() {
        val result = DishFilters.apply(listOf(day, MenuDay(LocalDate.of(2026, 10, 6), listOf(pork))), setOf("schwein"))
        assertEquals(2, result.size)
        assertTrue(result[1].dishes.isEmpty())
    }

    @Test
    fun options_areBuiltFromFoundTags_inFixedOrder() {
        val keys = DishFilters.options(listOf(day), emptySet()).map { it.key }
        assertEquals(listOf("vegan", "vegetarisch", DishFilters.FAVORITES, "schwein", "fisch", "knoblauch"), keys)
    }

    @Test
    fun options_offerVegetarianWhenOnlyVeganExists_andNotVeganWhenAbsent() {
        val onlyVegan = listOf(MenuDay(day.date, listOf(vegan)))
        assertEquals(listOf("vegan", "vegetarisch", DishFilters.FAVORITES), DishFilters.options(onlyVegan, emptySet()).map { it.key })
        val onlyVeggie = listOf(MenuDay(day.date, listOf(veggie)))
        assertEquals(listOf("vegetarisch", DishFilters.FAVORITES), DishFilters.options(onlyVeggie, emptySet()).map { it.key })
    }

    @Test
    fun options_keepActiveFiltersAbsentFromThePage_andUnknownKeysLast() {
        val d = listOf(MenuDay(day.date, listOf(dish("X", "neu_ding"), dish("Y", "rind"))))
        val keys = DishFilters.options(d, setOf("alkohol")).map { it.key }
        assertEquals(listOf(DishFilters.FAVORITES, "rind", "alkohol", "neu_ding"), keys)
        assertFalse(DishFilters.options(d, emptySet()).any { it.key == "alkohol" })
    }

    @Test
    fun realPage_offersTheTagsItContains() {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/unicampus-unten.html")).use { it.readBytes().toString(Charsets.UTF_8) }
        val days = MenuPageParser.parse(html).days
        val keys = DishFilters.options(days, emptySet()).map { it.key }
        assertTrue(keys.containsAll(listOf("vegan", "vegetarisch", "fisch", "rind")))
        // and the filter really removes every fish dish from the real data
        val noFish = DishFilters.apply(days, setOf("fisch")).flatMap { it.dishes }
        assertTrue(noFish.none { d -> d.tags.any { it.key == "fisch" } })
        assertTrue(noFish.size < days.flatMap { it.dishes }.size)
    }

    private fun withCodes(name: String, vararg codes: String) = dish(name).copy(codes = codes.toList())

    @Test
    fun excludedCodes_hideDishesContainingAnyOfThem() {
        val bread = withCodes("Brot", "a1", "g")
        val rice = withCodes("Reis")
        val egg = withCodes("Ei", "c")
        val d = listOf(MenuDay(day.date, listOf(bread, rice, egg)))
        fun names(codes: Set<String>) = DishFilters.apply(d, emptySet(), codes).single().dishes.map { it.name.de }
        assertEquals(listOf("Brot", "Reis", "Ei"), names(emptySet()))
        assertEquals(listOf("Reis", "Ei"), names(setOf("a1")))
        assertEquals(listOf("Reis"), names(setOf("a1", "c")))
        assertEquals(listOf("Brot", "Reis", "Ei"), names(setOf("zz"))) // a code nobody lists hides nothing
    }

    @Test
    fun codesAndTagsCombine_andAnEmptiedDayIsKept() {
        val d = listOf(MenuDay(day.date, listOf(withCodes("Käse", "g").copy(tags = listOf(Tag("vegetarisch", "v"))))))
        val r = DishFilters.apply(d, setOf("vegetarisch"), setOf("g"))
        assertEquals(1, r.size)
        assertTrue(r.single().dishes.isEmpty())
    }

    // ---- fixed menus (PIER 16) ----

    private fun fixedSection(vararg dishes: Dish) = de.luwify.mensamd.model.FixedSection(null, dishes.toList())

    @Test
    fun sections_areFilteredLikeDays_andKeptWhenEmptied() {
        val s = listOf(fixedSection(vegan, veggie, pork), fixedSection(fish))
        val r = DishFilters.applySections(s, setOf("vegetarisch", "schwein"))
        assertEquals(listOf("Linsen", "Käsespätzle"), r[0].dishes.map { it.name.de })
        assertEquals(2, r.size)
        assertTrue(r[1].dishes.isEmpty()) // fish is not vegetarian: section kept, empty
    }

    @Test
    fun sections_excludedCodesHideDishes() {
        val s = listOf(fixedSection(withCodes("Brot", "a1"), withCodes("Reis")))
        assertEquals(listOf("Reis"), DishFilters.applySections(s, emptySet(), setOf("a1"))[0].dishes.map { it.name.de })
        assertEquals(s, DishFilters.applySections(s, emptySet(), emptySet()))
    }

    @Test
    fun options_includeTagsOfFixedSections_inFixedOrder() {
        val keys = DishFilters.options(emptyList(), emptySet(), listOf(fixedSection(pork, vegan))).map { it.key }
        assertEquals(listOf("vegan", "vegetarisch", DishFilters.FAVORITES, "schwein", "knoblauch"), keys)
    }

    @Test
    fun realPier_offersTheTagsItHas_andFiltersWork() {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-10-01/pier16.html")).use { it.readBytes().toString(Charsets.UTF_8) }
        val page = de.luwify.mensamd.parser.PierParser.parse(html)
        val keys = DishFilters.options(emptyList(), emptySet(), page.fixed).map { it.key }
        assertEquals(listOf("vegan", "vegetarisch", DishFilters.FAVORITES, "schwein", "rind", "gefluegel", "knoblauch"), keys)
        val vegan = DishFilters.applySections(page.fixed, setOf("vegan")).flatMap { it.dishes }
        assertEquals(3, vegan.size) // Veggie burger, Vegane Curry-Gemüsebratwurst, Mediterraner Wrap
        val vegetarian = DishFilters.applySections(page.fixed, setOf("vegetarisch")).flatMap { it.dishes }
        assertEquals(5, vegetarian.size) // + Cheesy Veggie burger, Caprese wrap
        val noGluten = DishFilters.applySections(page.fixed, emptySet(), setOf("a1")).flatMap { it.dishes }
        assertEquals(1, noGluten.size) // only the Curry-Bratwurst states no wheat
    }

    // ---- cake of the day ----

    private fun fixture(name: String) = MenuPageParser.parse(
        checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/$name.html")).use { it.readBytes().toString(Charsets.UTF_8) },
    )

    private fun withBadge(name: String, de: String, en: String = de) = dish(name).copy(badges = listOf(LocalizedText(de, en)))

    @Test
    fun cake_isDetectedFromEitherLanguage() {
        assertTrue(withBadge("A", "Der heutige Lieblingskuchen vom Mensa-Team", "Today's favorite cake of the Mensa team").isCakeOfTheDay)
        // German text missing on the page: the parser puts the English one on both sides
        assertTrue(withBadge("B", "Today's favourite cake of our Mensa-Team").isCakeOfTheDay)
        assertFalse(withBadge("C", "Save the meal").isCakeOfTheDay)
        assertFalse(dish("Käsekuchen").isCakeOfTheDay) // a cake by name only is not the cake of the day
    }

    @Test
    fun cake_realPages_showOnlyTheCakeOfTheDay_oneEachDay() {
        for ((name, expected) in listOf("unicampus-unten" to 6, "herrenkrug" to 1, "wernigerode" to 1)) {
            val days = fixture(name).days
            assertTrue(name, DishFilters.options(days, emptySet()).any { it.key == DishFilters.CAKE })
            val shown = DishFilters.apply(days, setOf(DishFilters.CAKE))
            assertEquals(name, days.size, shown.size) // days without cake stay (they say "no matching dishes")
            val cakes = shown.flatMap { it.dishes }
            assertEquals(name, expected, cakes.size)
            assertTrue(name, shown.all { it.dishes.size <= 1 })
            assertTrue(name, cakes.all { d -> d.badges.any { it.de.startsWith("Der heutige Lieblingskuchen") && it.en.contains("cake") } })
        }
    }

    @Test
    fun cake_herrenkrugUsesTheBritishSpelling_andIsStillFound() {
        val cake = fixture("herrenkrug").days.flatMap { it.dishes }.single { it.isCakeOfTheDay }
        assertTrue(cake.badges.any { it.en.contains("favourite cake") })
    }

    @Test
    fun cake_chipComesRightAfterVegetarian_andOnlyWhereThereIsCake() {
        val keys = DishFilters.options(fixture("unicampus-unten").days, emptySet()).map { it.key }
        assertEquals(listOf(DishFilters.VEGAN, DishFilters.VEGETARIAN, DishFilters.CAKE), keys.take(3))
        assertFalse(DishFilters.options(fixture("stendal").days, emptySet()).any { it.key == DishFilters.CAKE })
        // a persisted cake filter can still be switched off where there is no cake
        assertTrue(DishFilters.options(fixture("stendal").days, setOf(DishFilters.CAKE)).any { it.key == DishFilters.CAKE })
    }

    @Test
    fun cake_combinesWithOtherFilters() {
        val veganCake = withBadge("Schokokuchen", "Der heutige Lieblingskuchen vom Mensa-Team").copy(tags = listOf(Tag("vegan", "v")))
        val cake = withBadge("Zupfkuchen", "Der heutige Lieblingskuchen vom Mensa-Team")
        val d = listOf(MenuDay(day.date, listOf(veganCake, cake, vegan)))
        assertEquals(listOf("Schokokuchen", "Zupfkuchen"), DishFilters.apply(d, setOf(DishFilters.CAKE)).single().dishes.map { it.name.de })
        assertEquals(listOf("Schokokuchen"), DishFilters.apply(d, setOf(DishFilters.CAKE, "vegan")).single().dishes.map { it.name.de })
    }

    // ---- favorites ----

    @Test
    fun favorites_showOnlyFavoritedDishes_andCombineWithOtherFilters() {
        val favorites = setOf(Favorites.key(vegan), Favorites.key(pork))
        assertEquals(listOf("Linsen", "Schnitzel"), DishFilters.apply(listOf(day), setOf(DishFilters.FAVORITES), favorites = favorites).single().dishes.map { it.name.de })
        assertEquals(listOf("Linsen"), DishFilters.apply(listOf(day), setOf(DishFilters.FAVORITES, "schwein"), favorites = favorites).single().dishes.map { it.name.de })
        // without favorites the day stays, empty (the screen shows the "no favorites yet" message instead)
        assertTrue(DishFilters.apply(listOf(day), setOf(DishFilters.FAVORITES)).single().dishes.isEmpty())
        // favorites alone don't filter anything while the chip is off
        assertEquals(5, DishFilters.apply(listOf(day), emptySet(), favorites = favorites).single().dishes.size)
    }

    @Test
    fun favorites_chipIsAlwaysOffered_afterCake() {
        assertEquals(listOf(DishFilters.VEGAN, DishFilters.VEGETARIAN, DishFilters.CAKE, DishFilters.FAVORITES),
            DishFilters.options(fixture("unicampus-unten").days, emptySet()).map { it.key }.take(4))
        assertEquals(listOf(DishFilters.FAVORITES), DishFilters.options(emptyList(), emptySet()).map { it.key })
    }

    @Test
    fun favorites_workOnFixedMenusToo() {
        val s = listOf(fixedSection(vegan, fish))
        assertEquals(listOf("Lachs"), DishFilters.applySections(s, setOf(DishFilters.FAVORITES), favorites = setOf("lachs"))[0].dishes.map { it.name.de })
    }
}
