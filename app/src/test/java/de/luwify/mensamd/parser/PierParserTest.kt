package de.luwify.mensamd.parser

import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PierParserTest {
    private fun fixture(path: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$path")) { "missing fixture $path" }.use { it.readBytes().toString(Charsets.UTF_8) }

    private val fixtures = listOf("2026-09-30/pier16.html", "2026-10-01/pier16.html")

    private fun dishes(page: MenuPage) = page.fixed.flatMap { it.dishes }
    private fun byName(page: MenuPage, start: String): Dish = dishes(page).first { it.name.de.startsWith(start) }
    private fun page(body: String) = "<html><body><div class='entry-content'>$body</div></body></html>"
    private fun row(inner: String) = "<table><tbody><tr><td>$inner</td></tr></tbody></table>"

    // ---- the real pages ---------------------------------------------------------------------

    @Test
    fun bothFixtures_haveNineDishesInOneUntitledSection() {
        for (f in fixtures) {
            val p = PierParser.parse(fixture(f))
            assertEquals(f, 9, dishes(p).size)
            assertEquals(f, 1, p.fixed.size)
            assertNull("no category titles on the page today", p.fixed.single().title)
            assertTrue(p.days.isEmpty())
        }
    }

    @Test
    fun theLiveCopyParsesLikeTheEarlierFixture() {
        assertEquals(PierParser.parse(fixture(fixtures[0])), PierParser.parse(fixture(fixtures[1])))
    }

    @Test
    fun names_germanAndOptionalEnglish() {
        val p = PierParser.parse(fixture(fixtures[1]))
        val first = dishes(p).first()
        assertEquals("Magde-Burger – BBQ Beef Style mit Pommes frites und Coleslaw", first.name.de)
        assertEquals(first.name.de, first.name.en) // no English line: falls back to German
        val wrap = byName(p, "BBQ-Chicken-Wrap")
        assertEquals("BBQ-Chicken-Wrap mit Blattsalat, Paprika, Zwiebel und Mais", wrap.name.de)
        assertEquals("with lettuce, peppers, onions and corn", wrap.name.en)
        assertEquals("Mediterraner Gemüse-Wrap mit Avocadocreme", dishes(p).last().name.de)
    }

    @Test
    fun threePrices_areKnownGroups_twoPricesAreNot() {
        val p = PierParser.parse(fixture(fixtures[1]))
        val burger = dishes(p).first()
        assertEquals(listOf(420, 590, 730), burger.prices)
        assertTrue(burger.priceGroupsKnown)
        assertEquals(6, dishes(p).count { it.prices.size == 3 && it.priceGroupsKnown })
        val twoPrice = dishes(p).filter { it.prices.size == 2 }
        assertEquals(3, twoPrice.size)
        assertTrue(twoPrice.none { it.priceGroupsKnown })
        assertEquals(listOf(250, 350), byName(p, "BBQ-Chicken-Wrap").prices)
        assertEquals(listOf(300, 400), byName(p, "Caprese-Wrap").prices)
        assertEquals(listOf(200, 300), byName(p, "Mediterraner").prices)
    }

    @Test
    fun tagAbbreviations_mapToOurTagKeys() {
        val p = PierParser.parse(fixture(fixtures[1]))
        fun keys(start: String) = byName(p, start).tags.map { it.key }
        assertEquals(listOf("rind"), keys("Magde-Burger – BBQ"))
        assertEquals(listOf("rind"), keys("Magde-Burger – Cheesy"))
        assertEquals(listOf("vegan"), keys("Pier 16-Burger – Veggie"))
        assertEquals(listOf("vegetarisch"), keys("Pier 16-Burger – Cheesy"))
        assertEquals(listOf("schwein"), keys("Curry-Bratwurst"))
        assertEquals(listOf("knoblauch", "vegan"), keys("Vegane Curry"))
        assertEquals(listOf("gefluegel"), keys("BBQ-Chicken-Wrap"))
        assertEquals(listOf("knoblauch", "vegetarisch"), keys("Caprese"))
        assertEquals(listOf("knoblauch", "vegan"), keys("Mediterraner"))
        assertEquals("Knoblauch", byName(p, "Caprese").tags.first().label)
    }

    @Test
    fun codes_areSeparatedFromTags_andTrimmed() {
        val p = PierParser.parse(fixture(fixtures[1]))
        assertEquals(listOf("3", "9", "a1", "i", "j", "k"), dishes(p).first().codes)
        assertEquals(listOf("3", "a1", "f", "i", "j"), byName(p, "Vegane Curry").codes) // "a1, f" had a stray space
        assertEquals(listOf("3", "5", "a1", "g", "1"), byName(p, "Caprese").codes)
        assertTrue(dishes(p).all { d -> d.codes.none { it in setOf("kno", "rin", "sch", "get", "veg", "vgt") } })
    }

    @Test
    fun everyCodeOnPierIsExplainedByTheLegendOfAnotherMensa() {
        val pier = PierParser.parse(fixture(fixtures[1]))
        val legend = MenuPageParser.parse(fixture("2026-09-30/unicampus-unten.html")).legend.codes.map { it.code }.toSet()
        val unknown = dishes(pier).flatMap { it.codes }.filter { it !in legend }.toSet()
        assertEquals(emptySet<String>(), unknown)
    }

    @Test
    fun noFootprintData_andNoBadges() {
        val p = PierParser.parse(fixture(fixtures[1]))
        assertTrue(dishes(p).all { it.co2Grams == null && it.waterLiters == null && it.badges.isEmpty() })
    }

    // ---- unknown / odd input, tolerated ------------------------------------------------------

    @Test
    fun unknownTagKey_isKeptAsText_neverDropped() {
        val p = PierParser.parse(page(row("<strong>Suppe</strong> (3, xyz, rin, Fis)<br /> 1,00 l 2,00 l 3,00")))
        val d = dishes(p).single()
        assertEquals(listOf("3"), d.codes)
        assertEquals(listOf("xyz", "rind", "fis"), d.tags.map { it.key })
        assertEquals("xyz", d.tags[0].label)
        assertEquals("Fis", d.tags[2].label) // original spelling is the display text
    }

    @Test
    fun otherPriceSeparators_euroSigns_andNbsp() {
        val html = page(
            row("<strong>A</strong> (g)<br /> 1,00 | 2,00 | 3,00") +
                row("<strong>B</strong> (g)<br /> 1,00 &euro; / 2,00 &euro;") +
                row("<strong>C</strong> (g)<br />&nbsp;4,50&nbsp;l&nbsp;5,50&nbsp;l&nbsp;6,50"),
        )
        val d = dishes(PierParser.parse(html))
        assertEquals(listOf(100, 200, 300), d[0].prices)
        assertEquals(listOf(100, 200), d[1].prices)
        assertEquals(listOf(450, 550, 650), d[2].prices)
    }

    @Test
    fun singleLineDish_andDishWithoutPrices() {
        val html = page("<p><strong>Kaffee</strong> (g) 1,50 l 2,00 l 2,50</p><p><strong>Tagessuppe</strong> (a1)<br />soup of the day</p>")
        val d = dishes(PierParser.parse(html))
        assertEquals(listOf(150, 200, 250), d[0].prices)
        assertEquals("Kaffee", d[0].name.de)
        assertTrue(d[1].prices.isEmpty())
        assertFalse(d[1].priceGroupsKnown)
        assertEquals("soup of the day", d[1].name.en)
    }

    @Test
    fun uppercaseAndLetterNumberCodes() {
        val d = dishes(PierParser.parse(page(row("<strong>X</strong> (A, Q, h1, n, 10)<br /> 1,00 l 2,00 l 3,00")))).single()
        assertEquals(listOf("A", "Q", "h1", "n", "10"), d.codes)
    }

    // ---- sections ----------------------------------------------------------------------------

    @Test
    fun headingsStartSections() {
        val html = page(
            "<h3>Frühstück:</h3>" + row("<strong>Brötchen</strong> (a1)<br /> 0,50 l 0,70 l 0,90") +
                "<h3>Kuchen</h3>" + row("<strong>Apfelkuchen</strong> (a1, g, c)<br /> 1,50 l 1,70 l 1,90"),
        )
        val sections = PierParser.parse(html).fixed
        assertEquals(listOf("Frühstück", "Kuchen"), sections.map { it.title?.de })
        assertEquals(listOf("Brötchen"), sections[0].dishes.map { it.name.de })
        assertEquals(listOf("Apfelkuchen"), sections[1].dishes.map { it.name.de })
    }

    @Test
    fun aBoldOnlyRow_isACategoryHeader_notADish() {
        val html = page(
            "<table><tbody><tr><td><strong>Getränke</strong></td></tr>" +
                "<tr><td><strong>Cola</strong> (1, 2, 4)<br /> 1,50 l 2,00 l 2,50</td></tr></tbody></table>",
        )
        val p = PierParser.parse(html)
        assertEquals(1, p.fixed.size)
        assertEquals("Getränke", p.fixed[0].title?.de)
        assertEquals(listOf("Cola"), p.fixed[0].dishes.map { it.name.de })
    }

    @Test
    fun dishesBeforeTheFirstHeading_formAnUntitledSection() {
        val html = page(row("<strong>A</strong> (g)<br /> 1,00 l 2,00 l 3,00") + "<h3>Kuchen</h3>" + row("<strong>B</strong> (g)<br /> 1,00 l 2,00 l 3,00"))
        val sections = PierParser.parse(html).fixed
        assertEquals(listOf(null, "Kuchen"), sections.map { it.title?.de })
    }

    // ---- empty and garbled pages: never throw, return an empty result ------------------------

    @Test
    fun emptyAndChangedPages_returnAnEmptyResult() {
        val empties = listOf(
            "",
            "   ",
            "<html></html>",
            page(""),
            page("<table><tbody></tbody></table>"),
            "<html><body><h1>Wartungsarbeiten</h1></body></html>", // layout without entry-content
            "<html><body><div class='entry-content'><p>Heute geschlossen</p></div></body></html>",
        )
        for (html in empties) {
            val p = PierParser.parse(html)
            assertTrue("blank for: ${html.take(40)}", p.isBlank)
            assertTrue(p.days.isEmpty())
        }
    }

    @Test
    fun garbledPages_neverThrow() {
        val real = fixture(fixtures[1])
        val inputs = mutableListOf(
            "\u0000\u0001\u0002 not html at all <<<>>> ((( ))) ,,, 1,2 l 3,4",
            "<div class='entry-content'><strong>X (3, <br /> 1,00",
            page("<tr><td><strong></strong> (,,,)<br /> l l l</td></tr>"),
            page("<strong>(</strong>)<strong>)(</strong>"),
            page("<table>".repeat(500) + "<strong>A</strong> (g) 1,00 l 2,00"),
            page("<p><strong>" + "x".repeat(50_000) + "</strong> (a1) 1,00 l 2,00 l 3,00</p>"),
        )
        // the real page (the fixture is just the menu block) cut off at many points, and with chunks removed
        for (cut in 0..real.length step 37) inputs += real.substring(0, cut)
        for (start in 0..real.length - 150 step 41) inputs += real.removeRange(start, start + 150)
        for (html in inputs) {
            val p = PierParser.parse(html) // must not throw
            assertTrue(p.days.isEmpty())
            for (d in p.fixed.flatMap { it.dishes }) {
                assertTrue(d.name.de.isNotEmpty())
                assertTrue(d.prices.size <= 3)
            }
        }
    }

    @Test
    fun aDishWithOnlyUnusualTokens_stillProducesNoCrash() {
        val d = dishes(PierParser.parse(page(row("<strong>Rätsel</strong> (???, ,, ä, 3x)<br /> 9,99 l 8,88")))).single()
        assertEquals("Rätsel", d.name.de)
        assertEquals(listOf(999, 888), d.prices)
        assertTrue(d.tags.none { it.key.isEmpty() })
    }

    @Test
    fun tagMap_isNotMutatedByUnknownKeys() {
        val a = dishes(PierParser.parse(page(row("<strong>A</strong> (xyz)<br /> 1,00 l 2,00 l 3,00")))).single().tags
        assertEquals(listOf(Tag("xyz", "xyz")), a)
        val b = dishes(PierParser.parse(page(row("<strong>B</strong> (kno)<br /> 1,00 l 2,00 l 3,00")))).single().tags
        assertEquals(listOf(Tag("knoblauch", "Knoblauch")), b)
    }

    @Test
    fun handTypedVariants_uppercaseAllergens_trailingDots_andFullWordTags() {
        val d = dishes(PierParser.parse(page(row("<strong>X</strong> (A1, G1, 3., vegan, kno.)<br /> 1,00 l 2,00 l 3,00")))).single()
        assertEquals(listOf("a1", "g1", "3"), d.codes)
        // "vegan" typed out is its own tag key and lands on our vegan filter; "kno." still maps
        assertEquals(listOf("vegan", "knoblauch"), d.tags.map { it.key })
    }

    @Test
    fun anEnglishLineInParentheses_isNotTakenForTheCodeGroup() {
        val d = dishes(PierParser.parse(page(row("<strong>Wrap</strong> (3, g)<br /> (with a long english remark)<br /> 1,00 l 2,00 l 3,00")))).single()
        assertEquals(listOf("3", "g"), d.codes)
        assertEquals("(with a long english remark)", d.name.en)
    }
}
