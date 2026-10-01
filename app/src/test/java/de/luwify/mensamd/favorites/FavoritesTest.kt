package de.luwify.mensamd.favorites

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {
    private fun dish(de: String, en: String = de) = Dish(LocalizedText(de, en), emptyList(), emptyList(), emptyList(), emptyList(), null, null)

    @Test
    fun normalize_lowercasesCollapsesWhitespaceAndTrimsPunctuation() {
        assertEquals("spaghetti bolognese, parmesan", Favorites.normalize("  Spaghetti   Bolognese, Parmesan. "))
        assertEquals("käsespätzle mit röstzwiebeln", Favorites.normalize("\"Käsespätzle mit Röstzwiebeln\"!"))
        assertEquals("bio-linsensuppe", Favorites.normalize("BIO-Linsensuppe"))
        assertEquals("", Favorites.normalize(" ... "))
    }

    @Test
    fun normalize_removesPrefixBadges() {
        assertEquals("zupfkuchen vom blech", Favorites.normalize("Save the meal: Zupfkuchen vom Blech"))
        assertEquals("penne arrabiata", Favorites.normalize("Ausgabe Cafeteria: Pastabuffet: Penne Arrabiata"))
    }

    @Test
    fun normalize_keepsInnerPunctuation_soDifferentDishesStayDifferent() {
        assertNotEquals(Favorites.normalize("Reis, Gemüse"), Favorites.normalize("Reis Gemüse"))
    }

    @Test
    fun normalize_unifiesComposedAndDecomposedUmlauts() {
        assertEquals(Favorites.normalize("Käse"), Favorites.normalize("Käse"))
    }

    @Test
    fun matching_isExactOnTheNormalizedGermanName_only() {
        val favorites = setOf(Favorites.normalize("Gebratene Nudeln mit Gemüse"))
        assertTrue(Favorites.isFavorite(dish("Gebratene  Nudeln mit Gemüse."), favorites))
        assertTrue(Favorites.isFavorite(dish("Gebratene Nudeln mit Gemüse", "Fried noodles"), favorites))
        assertFalse(Favorites.isFavorite(dish("Gebratene Nudeln mit Gemüse und Ei"), favorites)) // no fuzzy/prefix match
        assertFalse(Favorites.isFavorite(dish("Fried noodles", "Gebratene Nudeln mit Gemüse"), favorites)) // English side ignored
        assertFalse(Favorites.isFavorite(dish("Gebratene Nudeln mit Gemüse"), emptySet()))
    }

    private fun fixture(name: String) = MenuPageParser.parse(
        checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/$name.html")).use { it.readBytes().toString(Charsets.UTF_8) },
    )

    @Test
    fun realPages_aFavoriteIsFoundOnEveryDayAndInEveryMensaThatServesIt() {
        val mensen = listOf("unicampus-unten", "unicampus-oben", "kellercafe", "herrenkrug", "stendal", "halberstadt", "wernigerode")
        // Where each normalized name occurs: (Mensa, date)
        val places = HashMap<String, MutableSet<Pair<String, java.time.LocalDate>>>()
        for (m in mensen) for (day in fixture(m).days) for (d in day.dishes) places.getOrPut(Favorites.key(d)) { HashSet() } += m to day.date
        val shared = places.filterValues { p -> p.map { it.first }.toSet().size >= 2 }
        assertTrue("some dish is served in two Mensen", shared.isNotEmpty())
        val key = shared.keys.first()
        val favorites = setOf(key)
        for (m in mensen) for (day in fixture(m).days) {
            val hits = day.dishes.filter { Favorites.isFavorite(it, favorites) }
            assertEquals("$m ${day.date}", if ((m to day.date) in places.getValue(key)) true else false, hits.isNotEmpty())
        }
    }
}
