package de.luwify.mensamd.ui

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.isDinner
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MenuRowsTest {
    private fun dish(name: String, dinner: Boolean = false) = Dish(
        LocalizedText(name),
        if (dinner) listOf(LocalizedText("Abendessen im Café Latte", "Dinner at Café Latte")) else emptyList(),
        emptyList(), emptyList(), emptyList(), null, null,
    )

    private val d1 = LocalDate.of(2026, 10, 5)
    private val d2 = LocalDate.of(2026, 10, 6)

    private fun kinds(rows: List<ListRow>) = rows.map {
        when (it) {
            is ListRow.Header -> "H"
            is ListRow.SubHeader -> if (it.dinner) "sD" else "sL"
            is ListRow.DishRow -> "d:" + it.dish.name.de
            is ListRow.NoMatch -> "none"
            is ListRow.SubDivider -> "|"
            is ListRow.DayDivider -> "||"
            is ListRow.Footer -> "F"
            else -> error("fixed-menu row in a dated list")
        }
    }

    @Test
    fun dayWithoutDinner_hasNoSubHeaders() {
        val rows = buildRows(listOf(MenuDay(d1, listOf(dish("A"), dish("B")))), null, false)
        assertEquals(listOf("H", "d:A", "d:B"), kinds(rows))
    }

    @Test
    fun dayWithBoth_isSplitWithSubHeadersAndDivider() {
        val rows = buildRows(listOf(MenuDay(d1, listOf(dish("A"), dish("N", dinner = true), dish("B")))), null, false)
        assertEquals(listOf("H", "sL", "d:A", "d:B", "|", "sD", "d:N"), kinds(rows))
    }

    @Test
    fun onlyDinner_getsOnlyTheDinnerSubHeader() {
        val rows = buildRows(listOf(MenuDay(d1, listOf(dish("N", dinner = true)))), null, false)
        assertEquals(listOf("H", "sD", "d:N"), kinds(rows))
    }

    @Test
    fun filteredEmptyDay_isKeptWithNotice_dayDividerAndFooterPlaced() {
        val rows = buildRows(listOf(MenuDay(d1, emptyList()), MenuDay(d2, listOf(dish("A")))), 123L, true)
        assertEquals(listOf("H", "none", "||", "H", "d:A", "F"), kinds(rows))
        assertTrue((rows.last() as ListRow.Footer).failed)
    }

    @Test
    fun keysAreUnique_evenForRepeatedNamesAndBothParts() {
        val days = listOf(
            MenuDay(d1, listOf(dish("Suppe"), dish("Suppe"), dish("Suppe", dinner = true))),
            MenuDay(d2, listOf(dish("Suppe"))),
        )
        val keys = buildRows(days, 1L, false).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun realPages_dinnerPrefixIsRecognisedOnlyOnAbendessen() {
        fun page(n: String) = MenuPageParser.parse(
            checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/$n.html")).use { it.readBytes().toString(Charsets.UTF_8) },
        )
        val all = listOf("unicampus-unten", "unicampus-oben", "kellercafe", "herrenkrug", "stendal", "halberstadt", "wernigerode", "blub-wernigerode")
            .flatMap { page(it).days }.flatMap { it.dishes }
        val dinner = all.filter { it.isDinner }
        assertTrue("some dinner dishes exist", dinner.isNotEmpty())
        assertTrue(dinner.all { d -> d.badges.any { it.de.startsWith("Abendessen") && it.en.startsWith("Dinner") } })
        assertFalse(all.filter { !it.isDinner }.any { d -> d.badges.any { it.de.startsWith("Abendessen") } })
        // and they really split days on the two UniCampus halls
        val rows = buildRows(page("unicampus-unten").days, null, false)
        assertTrue(rows.any { it is ListRow.SubHeader && it.dinner })
        assertTrue(rows.any { it is ListRow.SubHeader && !it.dinner })
    }

    // ---- fixed (undated) menus ----

    private fun section(title: String?, vararg dishes: Dish) =
        de.luwify.mensamd.model.FixedSection(title?.let { de.luwify.mensamd.model.LocalizedText(it) }, dishes.toList())

    private fun fixedKinds(rows: List<ListRow>) = rows.map {
        when (it) {
            ListRow.FixedHeader -> "FH"
            is ListRow.SectionTitle -> "T:" + it.title.de
            is ListRow.DishRow -> "d:" + it.dish.name.de + (if (it.date == null) "" else "@date")
            is ListRow.NoMatch -> "none"
            is ListRow.SectionDivider -> "||"
            is ListRow.Footer -> "F"
            else -> "?"
        }
    }

    @Test
    fun fixedMenu_hasOneLabelInsteadOfDayHeaders_andNoDates() {
        val rows = buildFixedRows(listOf(section(null, dish("A"), dish("B"))), 5L, false)
        assertEquals(listOf("FH", "d:A", "d:B", "F"), fixedKinds(rows))
        assertTrue(rows.filterIsInstance<ListRow.DishRow>().all { it.date == null })
        assertTrue(rows.none { it is ListRow.Header || it is ListRow.SubHeader || it is ListRow.DayDivider })
    }

    @Test
    fun fixedSections_getTitlesAndDividers_andEmptiedSectionsSayNoMatch() {
        val rows = buildFixedRows(listOf(section("Frühstück", dish("A")), section("Kuchen"), section(null, dish("B"))), null, false)
        assertEquals(listOf("FH", "T:Frühstück", "d:A", "||", "T:Kuchen", "none", "||", "d:B"), fixedKinds(rows))
    }

    @Test
    fun fixedKeys_areUnique_evenForRepeatedNamesAndSections() {
        val rows = buildFixedRows(listOf(section(null, dish("Suppe"), dish("Suppe")), section("Mehr", dish("Suppe"))), 1L, false)
        val keys = rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun realPierFixture_buildsAHeaderNineCardsAndAFooter() {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-10-01/pier16.html")).use { it.readBytes().toString(Charsets.UTF_8) }
        val page = de.luwify.mensamd.parser.PierParser.parse(html)
        val rows = buildFixedRows(page.fixed, 1L, false)
        assertEquals(ListRow.FixedHeader, rows.first())
        assertEquals(9, rows.count { it is ListRow.DishRow })
        assertTrue(rows.last() is ListRow.Footer)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}
