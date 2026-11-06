package de.luwify.mensamd.model

import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightPricingTest {
    private fun dish(badges: List<LocalizedText> = emptyList(), tags: List<Tag> = emptyList()) =
        Dish(LocalizedText("Gericht"), badges, listOf(50), tags, emptyList(), null, null)

    private fun page(path: String) = MenuPageParser.parse(
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$path")).use { it.readBytes().toString(Charsets.UTF_8) },
    )

    private val soup = Tag("suppe", "Suppe")

    @Test fun pastaBuffetEverywhere_eitherLanguage() {
        assertTrue(WeightPricing.isPer100g("stendal", dish(listOf(LocalizedText("Pastabuffet", "pasta buffet")))))
        assertTrue(WeightPricing.isPer100g("stendal", dish(listOf(LocalizedText("pasta buffet")))))
        assertFalse(WeightPricing.isPer100g("stendal", dish(listOf(LocalizedText("Aktionstheke", "Promotional counter")))))
    }

    @Test fun soupIconOnlyInUniCampusUnten() {
        assertTrue(WeightPricing.isPer100g("unicampus-unten", dish(tags = listOf(soup))))
        assertFalse(WeightPricing.isPer100g("unicampus-oben", dish(tags = listOf(soup))))
        assertFalse(WeightPricing.isPer100g("unicampus-unten", dish(tags = listOf(Tag("vegan", "Vegan")))))
    }

    @Test fun realPages() {
        val unten = page("2026-11-06/unicampus-unten.html").days.flatMap { it.dishes }
        val weighed = unten.filter { WeightPricing.isPer100g("unicampus-unten", it) }
        assertTrue(weighed.isNotEmpty())
        // Every dish found is a cheap soup; a stew at a portion price (no soup icon) stays fixed.
        assertEquals(listOf(60), weighed.map { it.prices.first() }.distinct())
        assertFalse(unten.filter { "eintopf" in it.name.de.lowercase() }.any { WeightPricing.isPer100g("unicampus-unten", it) })

        val oben = page("2026-11-06/unicampus-oben.html").days.flatMap { it.dishes }
        val pasta = oben.filter { WeightPricing.isPer100g("unicampus-oben", it) }
        assertTrue(pasta.isNotEmpty())
        assertEquals(listOf(50), pasta.map { it.prices.first() }.distinct())
    }
}
