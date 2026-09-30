package de.luwify.mensamd.parser

import de.luwify.mensamd.model.CodeCategory
import de.luwify.mensamd.model.MenuPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MenuPageParserTest {
    private fun fixtureHtml(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/$name.html")) { "missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private fun fixture(name: String): MenuPage = MenuPageParser.parse(fixtureHtml(name))

    private val allFixtures = listOf(
        "unicampus-unten", "unicampus-oben", "kellercafe", "herrenkrug",
        "stendal", "halberstadt", "wernigerode", "blub-wernigerode",
    )

    // ---- days -------------------------------------------------------------------------------

    @Test
    fun unten_hasSixWorkingDaysAcrossTheWeekend() {
        val dates = fixture("unicampus-unten").days.map { it.date }
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8),
            ),
            dates,
        )
    }

    @Test
    fun oben_keepsTheGapBetweenFifthAndTwelfthOctober() {
        val dates = fixture("unicampus-oben").days.map { it.date }
        assertEquals(
            listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14)),
            dates,
        )
    }

    @Test
    fun blub_hasOneDishPerDay() {
        val page = fixture("blub-wernigerode")
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), page.days.map { it.date })
        assertEquals(listOf(1, 1), page.days.map { it.dishes.size })
        val first = page.days[0].dishes.single()
        assertEquals("Gabelspaghetti alla Diavolo mit Salami, Paprika und Zwiebel in Tomaten-Chilisauce", first.name.de)
        assertTrue(first.name.en.startsWith("cut up shortened spaghetti alla Diavolo"))
        assertEquals(listOf(200, 380, 520), first.prices)
        assertEquals(listOf("schwein"), first.tags.map { it.key })
        assertEquals(listOf("1", "2", "3", "a1", "Q"), first.codes)
        assertEquals(412, first.co2Grams)
        assertEquals(78, first.waterLiters)
    }

    @Test
    fun everyTableRowBecomesADish() {
        // Independent count straight from the raw HTML: each dish row is "<tr >", heading rows are "<tr>".
        for (name in allFixtures) {
            val expected = Regex("<tr >").findAll(fixtureHtml(name)).count()
            val parsed = fixture(name).days.sumOf { it.dishes.size }
            assertEquals(name, expected, parsed)
            assertTrue("$name has days", fixture(name).days.isNotEmpty())
        }
    }

    // ---- dish fields ------------------------------------------------------------------------

    @Test
    fun firstDishOfUnten_hasAllFields() {
        val dish = fixture("unicampus-unten").days.first().dishes.first()
        assertEquals("Crispy Fish-Burger, Pommes Frites, mediterrane Gemüsesalsa", dish.name.de)
        assertEquals("crispy fish burger, french fries, mediterranean vegetable salsa", dish.name.en)
        assertTrue(dish.badges.isEmpty())
        assertEquals(listOf(340, 510, 650), dish.prices)
        assertEquals(listOf("fisch"), dish.tags.map { it.key }) // <noscript> duplicate is not counted twice
        assertEquals("Fisch", dish.tags.single().label)
        assertEquals(listOf("1", "2", "9", "a1", "c", "d", "f", "g", "j", "k"), dish.codes)
        assertEquals(1119, dish.co2Grams)
        assertEquals(16, dish.waterLiters)
    }

    @Test
    fun noData_hidesCo2AndWater() {
        val dishes = fixture("stendal").days.flatMap { it.dishes }
        val noData = dishes.filter { it.co2Grams == null }
        assertEquals(12, noData.size) // 24 "Keine Daten" blocks = 2 per dish
        assertTrue(noData.all { it.waterLiters == null })
        assertTrue(dishes.any { it.co2Grams != null })
    }

    @Test
    fun brokenAltTextIsKeptAsLabelButKeyIsStable() {
        val tags = allFixtures.flatMap { fixture(it).days }.flatMap { it.dishes }.flatMap { it.tags }
        val alcohol = tags.first { it.key == "alkohol" }
        assertEquals("enth lt Alkohol", alcohol.label) // the site really emits this
    }

    @Test
    fun allKnownTagKeysAppear() {
        val keys = allFixtures.flatMap { fixture(it).days }.flatMap { it.dishes }.flatMap { it.tags }.map { it.key }.toSet()
        assertEquals(
            setOf("vegan", "vegetarisch", "knoblauch", "gefluegel", "schwein", "fisch", "rind", "alkohol", "suppe"),
            keys,
        )
    }

    @Test
    fun prefixes_areStrippedIntoBadges() {
        val dishes = allFixtures.flatMap { fixture(it).days }.flatMap { it.dishes }

        val grab = dishes.first { it.badges.any { b -> b.de == "Grab the prep" } }
        assertEquals("Rindergulasch, Rotkohl, Kartoffelklöße", grab.name.de)
        assertEquals("beef goulash, red cabbage, potato dumplings", grab.name.en)

        val action = dishes.first { it.badges.any { b -> b.de == "Aktionstheke" } }
        assertEquals("Promotional counter", action.badges.first().en)
        assertTrue(action.name.de.startsWith("Putenbruststeak"))

        // nested: "Ausgabe Cafeteria: Pastabuffet: Farfalle ..."
        val nested = dishes.first { it.badges.size == 2 }
        assertEquals(listOf("Ausgabe Cafeteria", "Pastabuffet"), nested.badges.map { it.de })
        assertEquals(listOf("Counter Cafeteria", "pasta buffet"), nested.badges.map { it.en })
        assertEquals("Farfalle al Broccoli mit Brokkoli-Cremesauce", nested.name.de)

        val cake = dishes.first { it.badges.any { b -> b.de.startsWith("Der heutige Lieblingskuchen") } }
        assertTrue(cake.badges.single().en.startsWith("Today's favo"))
        assertTrue("cake name has no label", !cake.name.de.contains("Lieblingskuchen"))

        assertTrue("no colon left in any name", dishes.none { ':' in it.name.de || ':' in it.name.en })
    }

    @Test
    fun bioInsideANameIsKept() {
        val dish = fixture("stendal").days.flatMap { it.dishes }.first { "BIO-Langkornreis" in it.name.de }
        assertTrue(dish.badges.isEmpty())
    }

    // ---- legend -----------------------------------------------------------------------------

    @Test
    fun legend_mergesGermanAndEnglish() {
        val legend = fixture("unicampus-unten").legend
        val byCode = legend.codes.associateBy { it.code }

        assertEquals(CodeCategory.ADDITIVE, byCode.getValue("1").category)
        assertEquals("mit Farbstoff", byCode.getValue("1").text.de)
        assertEquals("with food colouring", byCode.getValue("1").text.en)
        assertEquals(CodeCategory.ALLERGEN, byCode.getValue("g").category)
        assertEquals("contains milk/lactose", byCode.getValue("g").text.en)
        assertEquals(CodeCategory.OTHER, byCode.getValue("Q").category)
        assertEquals("cochineal", byCode.getValue("Q").text.en)

        // German list lacks h7-h9: they come from English only, and German falls back to English.
        for (c in listOf("h7", "h8", "h9")) {
            val entry = byCode.getValue(c)
            assertEquals(CodeCategory.ALLERGEN, entry.category)
            assertEquals(entry.text.en, entry.text.de)
        }
        assertEquals("pistachios", byCode.getValue("h7").text.en)
        val perCategory = legend.codes.groupingBy { it.category }.eachCount()
        assertEquals(10, perCategory[CodeCategory.ADDITIVE])
        assertEquals(27, perCategory[CodeCategory.ALLERGEN]) // a1-a6, b-g, h1-h9, i-n
        assertEquals(6, perCategory[CodeCategory.OTHER])
    }

    @Test
    fun legend_hasPriceGroupsAndNotes() {
        val legend = fixture("unicampus-unten").legend
        assertEquals(listOf("Studenten", "Bedienstete", "Gäste"), legend.priceGroups.map { it.de })
        assertEquals(listOf("students", "staff", "guests"), legend.priceGroups.map { it.en })
        val titles = legend.notes.map { it.title.de }
        assertTrue(titles.containsAll(listOf("Save the meal", "Grab the prep")))
        val save = legend.notes.first { it.title.de == "Save the meal" }
        assertTrue(save.text.de.startsWith("Verkauf einer begrenzten"))
        assertTrue(save.text.en.startsWith("sale of a limited"))
    }

    @Test
    fun legend_codesAreOrderedByCategoryThenNaturally() {
        val codes = fixture("unicampus-unten").legend.codes.map { it.code }
        assertEquals((1..10).map { it.toString() }, codes.take(10))
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5", "a6", "b", "c"), codes.subList(10, 18))
        assertEquals(listOf("A", "P", "Q", "R", "S", "T"), codes.takeLast(6))
        assertTrue(codes.indexOf("h9") < codes.indexOf("i"))
    }

    // ---- synthetic edge cases ---------------------------------------------------------------

    private fun page(body: String) = "<html><body><div class='entry-content'><div class='mensa'>$body</div></div></body></html>"

    private fun table(date: String, rows: String = "") =
        "<table><thead><tr><td colspan='3'><strong>Montag / Monday, $date</strong></td></tr></thead><tbody>$rows</tbody></table>"

    @Test
    fun closedMensa_yieldsNoDaysAndNoLegend() {
        val parsed = MenuPageParser.parse("<html><body><div class='entry-content'><p>Geschlossen</p></div></body></html>")
        assertTrue(parsed.days.isEmpty())
        assertTrue(parsed.legend.codes.isEmpty())
        assertTrue(MenuPageParser.parse("").days.isEmpty())
    }

    @Test
    fun emptyDay_keepsTheDateWithoutDishes() {
        val parsed = MenuPageParser.parse(page(table("05.10.2026")))
        assertEquals(1, parsed.days.size)
        assertTrue(parsed.days[0].dishes.isEmpty())
    }

    @Test
    fun invalidOrMissingDate_dropsTheDay() {
        val row = "<tr ><td>Suppe</td></tr>"
        val parsed = MenuPageParser.parse(page(table("31.02.2026", row) + table("kein Datum", row) + table("06.10.2026", row)))
        assertEquals(listOf(LocalDate.of(2026, 10, 6)), parsed.days.map { it.date })
    }

    @Test
    fun daysAreSortedByDateNotByPosition() {
        val parsed = MenuPageParser.parse(page(table("06.10.2026") + table("05.10.2026")))
        assertEquals(listOf(5, 6), parsed.days.map { it.date.dayOfMonth })
    }

    @Test
    fun minimalDish_hasNoPriceTagsCodesOrEnglish() {
        val parsed = MenuPageParser.parse(page(table("05.10.2026", "<tr ><td style='width:80%;'>Kaffee</td></tr>")))
        val dish = parsed.days.single().dishes.single()
        assertEquals("Kaffee", dish.name.de)
        assertEquals("Kaffee", dish.name.en)
        assertTrue(dish.prices.isEmpty() && dish.tags.isEmpty() && dish.codes.isEmpty())
        assertNull(dish.co2Grams)
        assertNull(dish.waterLiters)
    }

    @Test
    fun twoPricesAndUnknownTag_areKept() {
        val row = """<tr ><td style='width:80%;'>Eis<br /><span class='grau'>ice cream</span><br />
            <span class='mensapreis'>(1,20 | 2,50)</span></td>
            <td><div class='mensakennsymb'><img class="lazyload" data-src='/x/mensasym_neu_ding.png' alt='Symbol Neues Ding' />
            <img data-src='/x/mensasym_neu_ding.png' alt='Symbol Neues Ding' /></div>
            <div class='mensakennzahl'>(a1) (g)</div></td></tr>"""
        val dish = MenuPageParser.parse(page(table("05.10.2026", row))).days.single().dishes.single()
        assertEquals(listOf(120, 250), dish.prices)
        assertEquals(listOf("neu_ding"), dish.tags.map { it.key })
        assertEquals("Neues Ding", dish.tags.single().label)
        assertEquals(listOf("a1", "g"), dish.codes)
    }

    @Test
    fun colonInsideANameWithDigitsIsNotABadge() {
        val row = "<tr ><td>Ab 11:30 Uhr: Suppe</td></tr>"
        val dish = MenuPageParser.parse(page(table("05.10.2026", row))).days.single().dishes.single()
        assertTrue(dish.badges.isEmpty())
        assertNotNull(dish.name.de)
    }
}
