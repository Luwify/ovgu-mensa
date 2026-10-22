package de.luwify.mensamd.favorites

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.FixedSection
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

class FavoriteOverviewTest {
    private val vegan = Tag("vegan", "vegan")
    private fun dish(de: String, tags: List<Tag> = emptyList()) =
        Dish(LocalizedText(de, "$de (en)"), emptyList(), emptyList(), tags, emptyList(), null, null)
    private fun key(de: String) = Favorites.normalize(de)
    private fun page(vararg days: Pair<LocalDate, List<Dish>>) = MenuPage(days.map { MenuDay(it.first, it.second) }, Legend.EMPTY)

    // Thursday 2026-10-22; Friday 23, Saturday 24, Monday 26.
    private val thu = LocalDate.of(2026, 10, 22)
    private val wed = thu.minusDays(1)
    private val fri = thu.plusDays(1)
    private val sat = thu.plusDays(2)
    private val mon = thu.plusDays(4)
    private val german = Collator.getInstance(Locale.GERMAN)

    private fun entries(favorites: Set<String>, menus: List<Pair<String, MenuPage?>>, saved: Map<String, FavoriteInfo> = emptyMap(), language: String = "de") =
        FavoriteOverview.entries(favorites, menus, thu, saved, language, german)

    // ---- next serving -----------------------------------------------------------------------

    @Test
    fun earliestDateWins_acrossAllMensen() {
        val menus = listOf(
            "a" to page(mon to listOf(dish("Schnitzel"))),
            "b" to page(fri to listOf(dish("Schnitzel"))),
        )
        val next = FavoriteOverview.nextServings(setOf(key("Schnitzel")), menus, thu)[key("Schnitzel")]!!
        assertEquals("b", next.mensaId)
        assertEquals(fri, next.date)
    }

    @Test
    fun sameDate_theMensaFirstInTabOrderWins() {
        val menus = listOf("b" to page(fri to listOf(dish("Suppe"))), "a" to page(fri to listOf(dish("Suppe"))))
        assertEquals("b", FavoriteOverview.nextServings(setOf(key("Suppe")), menus, thu)[key("Suppe")]!!.mensaId)
    }

    @Test
    fun todayCounts_pastDaysAndWeekendsDoNot() {
        val menus = listOf(
            "a" to page(wed to listOf(dish("Pasta")), sat to listOf(dish("Pasta")), mon to listOf(dish("Pasta"))),
            "b" to page(thu to listOf(dish("Reis"))),
        )
        val next = FavoriteOverview.nextServings(setOf(key("Pasta"), key("Reis")), menus, thu)
        assertEquals(mon, next[key("Pasta")]!!.date)
        assertEquals(thu, next[key("Reis")]!!.date)
    }

    @Test
    fun matchesByNormalizedName_likeTheHeart() {
        val menus = listOf("a" to page(fri to listOf(dish("  Grab the prep: Hähnchenbrust  Milano. "))))
        assertEquals(fri, FavoriteOverview.nextServings(setOf("hähnchenbrust milano"), menus, thu)["hähnchenbrust milano"]!!.date)
    }

    @Test
    fun fixedMenu_countsOnlyWithoutADatedServing() {
        val pier = MenuPage(emptyList(), Legend.EMPTY, listOf(FixedSection(null, listOf(dish("Burger"), dish("Wrap")))))
        val menus = listOf("pier16" to pier, "a" to page(mon to listOf(dish("Wrap"))))
        val next = FavoriteOverview.nextServings(setOf(key("Burger"), key("Wrap")), menus, thu)
        assertEquals(NextServing("pier16", null, dish("Burger")), next[key("Burger")])
        assertEquals("a", next[key("Wrap")]!!.mensaId)
    }

    @Test
    fun noCache_noServing() {
        val next = FavoriteOverview.nextServings(setOf(key("Schnitzel")), listOf("a" to null), thu)
        assertTrue(next.isEmpty())
    }

    @Test
    fun realFixture_findsADishOnItsDay() {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-10-22-fallback/unicampus-unten.html")).use { it.readBytes().toString(Charsets.UTF_8) }
        val page = MenuPageParser.parse(html)
        val target = page.days.last().dishes.first()
        val next = FavoriteOverview.nextServings(setOf(Favorites.key(target)), listOf("unicampus-unten" to page), thu)
        // The dish may also be on an earlier day; it must be found on the first day that has it.
        val firstDay = page.days.first { d -> d.dishes.any { Favorites.key(it) == Favorites.key(target) } }.date
        assertEquals(firstDay, next[Favorites.key(target)]!!.date)
    }

    // ---- order and display ------------------------------------------------------------------

    @Test
    fun sort_soonestFirst_thenFixed_thenTheRestAlphabetically() {
        val pier = MenuPage(emptyList(), Legend.EMPTY, listOf(FixedSection(null, listOf(dish("Burger")))))
        val menus = listOf(
            "a" to page(mon to listOf(dish("Zucchini")), fri to listOf(dish("Bohnen"))),
            "b" to page(thu to listOf(dish("Wrap")), fri to listOf(dish("Apfel"))),
            "pier16" to pier,
        )
        val favorites = setOf("Zucchini", "Bohnen", "Wrap", "Apfel", "Burger", "Älplermagronen", "Erbsen", "brot").map(::key).toSet()
        val names = entries(favorites, menus).map { it.name.de }
        assertEquals(listOf("Wrap", "Bohnen", "Apfel", "Zucchini", "Burger", "Älplermagronen", "Brot", "Erbsen"), names)
    }

    @Test
    fun sameDayAndMensa_alphabetical() {
        val menus = listOf("a" to page(fri to listOf(dish("Nudeln"), dish("Eintopf"))))
        assertEquals(listOf("Eintopf", "Nudeln"), entries(setOf(key("Nudeln"), key("Eintopf")), menus).map { it.name.de })
    }

    @Test
    fun alphabeticalByTheShownLanguage() {
        val saved = mapOf(
            key("Apfel") to FavoriteInfo(LocalizedText("Apfel", "Zebra apple"), emptyList()),
            key("Zwiebel") to FavoriteInfo(LocalizedText("Zwiebel", "Onion"), emptyList()),
        )
        val favorites = setOf(key("Apfel"), key("Zwiebel"))
        assertEquals(listOf("Apfel", "Zwiebel"), entries(favorites, emptyList(), saved, "de").map { it.name.de })
        assertEquals(listOf("Onion", "Zebra apple"), entries(favorites, emptyList(), saved, "en").map { it.name.en })
    }

    @Test
    fun nameAndTags_fromTheServing_elseSaved_elseTheKey() {
        val menus = listOf("a" to page(fri to listOf(dish("Tofu Bowl", listOf(vegan)))))
        val saved = mapOf(key("Linsen") to FavoriteInfo(LocalizedText("Linsen-Dal", "Lentil dal"), listOf(vegan)))
        val byKey = entries(setOf(key("Tofu Bowl"), key("Linsen"), key("Pizza Salami")), menus, saved).associateBy { it.key }

        assertEquals(LocalizedText("Tofu Bowl", "Tofu Bowl (en)"), byKey[key("Tofu Bowl")]!!.name)
        assertEquals(listOf(vegan), byKey[key("Tofu Bowl")]!!.tags)
        assertEquals("Linsen-Dal", byKey[key("Linsen")]!!.name.de)
        assertEquals(listOf(vegan), byKey[key("Linsen")]!!.tags)
        assertNull(byKey[key("Linsen")]!!.next)
        assertEquals("Pizza salami", byKey[key("Pizza Salami")]!!.name.de)
        assertTrue(byKey[key("Pizza Salami")]!!.tags.isEmpty())
    }

    @Test
    fun infoUpdates_onlyForServedFavorites_whoseSavedInfoIsMissingOrOld() {
        val menus = listOf("a" to page(fri to listOf(dish("Tofu Bowl", listOf(vegan)), dish("Reis"))))
        val saved = mapOf(
            key("Reis") to FavoriteInfo.of(dish("Reis")),
            key("Linsen") to FavoriteInfo(LocalizedText("Linsen"), emptyList()),
        )
        val list = entries(setOf(key("Tofu Bowl"), key("Reis"), key("Linsen")), menus, saved)
        assertEquals(mapOf(key("Tofu Bowl") to FavoriteInfo.of(dish("Tofu Bowl", listOf(vegan)))), FavoriteOverview.infoUpdates(list, saved))
    }

    @Test
    fun noFavorites_noEntries() {
        assertTrue(entries(emptySet(), listOf("a" to page(fri to listOf(dish("Reis"))))).isEmpty())
    }
}
