package de.luwify.mensamd.ui

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.Tag
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
        assertEquals(listOf("vegan", "vegetarisch", "schwein", "fisch", "knoblauch"), keys)
    }

    @Test
    fun options_offerVegetarianWhenOnlyVeganExists_andNotVeganWhenAbsent() {
        val onlyVegan = listOf(MenuDay(day.date, listOf(vegan)))
        assertEquals(listOf("vegan", "vegetarisch"), DishFilters.options(onlyVegan, emptySet()).map { it.key })
        val onlyVeggie = listOf(MenuDay(day.date, listOf(veggie)))
        assertEquals(listOf("vegetarisch"), DishFilters.options(onlyVeggie, emptySet()).map { it.key })
    }

    @Test
    fun options_keepActiveFiltersAbsentFromThePage_andUnknownKeysLast() {
        val d = listOf(MenuDay(day.date, listOf(dish("X", "neu_ding"), dish("Y", "rind"))))
        val keys = DishFilters.options(d, setOf("alkohol")).map { it.key }
        assertEquals(listOf("rind", "alkohol", "neu_ding"), keys)
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
}
