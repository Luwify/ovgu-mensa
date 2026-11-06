package de.luwify.mensamd.stats

import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DishSearchTest {
    private val friday = LocalDate.of(2026, 11, 6)

    private fun page(mensa: String) = mensa to MenuPageParser.parse(
        checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-11-06/$mensa.html")).use { it.readBytes().toString(Charsets.UTF_8) },
    )

    private val pages = listOf(page("unicampus-unten"), page("unicampus-oben"))
    private val realHits = DishSearch.hits(MenuDishes.onDay(pages, friday), MenuDishes.all(pages), emptyList(), emptyList(), 0)

    private fun snap(mensa: String, de: String, en: String = de, cents: Int = 300) =
        SnapDish(mensa, LocalizedText(de, en), listOf(cents, cents + 200, cents + 300))

    private fun names(hits: List<DishSearch.Hit>) = hits.map { it.name.de }

    /** The real dish of 2026-11-06, at the Mensa it was eaten, with the student price. */
    @Test fun chiliSinFindsTheRealDish() {
        val hit = DishSearch.search(realHits, "Chili sin").single { it.onDay }
        assertTrue(hit.name.de.startsWith("Chili sin carne aus BIO-Sonnenblumenhack"))
        assertEquals("unicampus-oben", hit.mensaId)
        assertEquals(2_000, hit.price)
    }

    @Test fun anyCaseAndAnyPartOfTheName() {
        for (query in listOf("chili sin", "CHILI SIN", "  chili   sin ", "sonnenblumen", "blumenhack reis", "sin chili", "organic")) {
            assertTrue(query, DishSearch.search(realHits, query).any { it.name.de.startsWith("Chili sin carne") })
        }
        assertEquals(emptyList<DishSearch.Hit>(), DishSearch.search(realHits, "chili nichtdrin"))
    }

    @Test fun umlautsDontMatter() {
        val hits = DishSearch.hits(listOf(snap("a", "Gemüsebrühe"), snap("a", "Soße"), snap("a", "Sauerkraut")), emptyList(), emptyList(), emptyList(), 0)
        for (query in listOf("gemüse", "gemuse", "gemuese", "GEMÜSE", "Bruhe", "bruehe")) {
            assertEquals(query, listOf("Gemüsebrühe"), names(DishSearch.search(hits, query)))
        }
        assertEquals(listOf("Soße"), names(DishSearch.search(hits, "sosse")))
        assertEquals(listOf("Soße"), names(DishSearch.search(hits, "soße")))
        assertEquals(listOf("Sauerkraut"), names(DishSearch.search(hits, "erkraut"))) // "ue" of a plain word stays findable
        // Composed or decomposed umlauts, as a page might send them.
        val decomposed = DishSearch.hits(listOf(snap("a", "Gemüse")), emptyList(), emptyList(), emptyList(), 0)
        assertEquals(1, DishSearch.search(decomposed, "gemüse").size)
    }

    @Test fun otherDaysOtherMensenEatenAndCustomDishesAreAllFound() {
        val eaten = CardEvent(
            1, 1, EventKind.PAYMENT, 3_100, friday, true,
            MealRecord(dishes = listOf(EatenDish("seelachs", LocalizedText("Seelachs"), "herrenkrug", 3_100))),
        )
        val custom = listOf(KnownDish("mein salat", LocalizedText("Mein Salat"), 2_000))
        val hits = DishSearch.hits(listOf(snap("a", "Burger")), listOf(snap("a", "Burger"), snap("b", "Pasta")), listOf(eaten), custom, 0)
        assertEquals(listOf("Burger", "Mein Salat", "Pasta", "Seelachs"), names(hits))
        assertEquals("herrenkrug", DishSearch.search(hits, "seelachs").single().mensaId)
        assertEquals(null, DishSearch.search(hits, "salat").single().mensaId)
    }

    @Test fun thatDaysDishesComeFirstInMenuOrder() {
        val day = listOf(snap("default", "Zander Curry"), snap("other", "Apfel Curry"))
        val hits = DishSearch.hits(day, day + snap("x", "Aal Curry"), emptyList(), emptyList(), 0)
        assertEquals(listOf("Zander Curry", "Apfel Curry", "Aal Curry"), names(DishSearch.search(hits, "curry")))
    }

    @Test fun aDishOncePerMensaAndWithoutMensaOnlyIfNothingNamesOne() {
        val a = snap("a", "Pasta")
        val eaten = CardEvent(1, 1, EventKind.PAYMENT, 2_000, friday, true, MealRecord(dishes = listOf(EatenDish("pasta", LocalizedText("Pasta"), null, 2_000))))
        val custom = listOf(KnownDish("pasta", LocalizedText("Pasta"), 2_000))
        val hits = DishSearch.hits(listOf(a), listOf(a, a, snap("b", "Pasta")), listOf(eaten), custom, 0)
        assertEquals(listOf("a", "b"), hits.map { it.mensaId })
    }

    @Test fun priceIsTheUsersGroupAndMaybeMissing() {
        val hits = DishSearch.hits(listOf(snap("a", "Pasta"), SnapDish("a", LocalizedText("Ohne Preis"), emptyList())), emptyList(), emptyList(), emptyList(), 1)
        assertEquals(listOf(5_000, null), hits.map { it.price })
    }

    @Test fun emptyQueryKeepsEverything() = assertEquals(realHits, DishSearch.search(realHits, "  "))

    @Test fun aDishByWeightKeepsItsPricePer100gAndEstimatesTheWeight() {
        val pasta = SnapDish("oben", LocalizedText("Farfalle"), listOf(50, 80, 110), byWeight = true)
        val hit = DishSearch.hits(listOf(pasta), emptyList(), emptyList(), emptyList(), 0).single()
        assertEquals(true, hit.byWeight)
        assertEquals(500, hit.price)
        assertEquals(470, hit.grams(2_350))
    }

    @Test fun anEatenPortionByWeightGivesNoPriceToReuse() {
        val eaten = EatenDish("farfalle", LocalizedText("Farfalle"), "oben", 2_350, 1, emptyList(), grams = 470)
        val event = CardEvent(1, 1, EventKind.PAYMENT, 2_350, java.time.LocalDate.of(2026, 11, 6), true, MealRecord(listOf(eaten)))
        val hit = DishSearch.hits(emptyList(), emptyList(), listOf(event), emptyList(), 0).single()
        assertEquals(null, hit.price)
        assertEquals(null, hit.grams(2_350))
    }
}
