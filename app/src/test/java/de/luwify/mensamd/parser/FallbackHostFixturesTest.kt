package de.luwify.mensamd.parser

import de.luwify.mensamd.model.MenuPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The pages of the fallback host (www.studiwerkmagdeburg.de, the Studentenwerk's test server) on 2026-10-22, when
 * the main site was down: the same markup, so the same parsers read them completely.
 */
class FallbackHostFixturesTest {
    private fun html(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-10-22-fallback/$name.html")) { "missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private val dated = listOf(
        "unicampus-unten", "unicampus-oben", "kellercafe", "herrenkrug",
        "stendal", "halberstadt", "wernigerode", "blub-wernigerode",
    )

    private fun parse(name: String): MenuPage = MenuPageParser.parse(html(name))

    @Test
    fun everyDatedPage_parsesWithDishesPricesAndTheFullLegend() {
        for (name in dated) {
            val page = parse(name)
            assertTrue("$name: no days", page.days.isNotEmpty())
            val dishes = page.days.flatMap { it.dishes }
            assertTrue("$name: no dishes", dishes.isNotEmpty())
            assertTrue("$name: dish without German name", dishes.all { it.name.de.isNotBlank() })
            assertTrue("$name: dish without three prices", dishes.all { it.prices.size == 3 })
            assertEquals("$name: price groups", 3, page.legend.priceGroups.size)
            assertEquals("$name: legend codes", 43, page.legend.codes.size)
        }
    }

    @Test
    fun unten_hasTheWeekAfterTheTwentySecond() {
        assertEquals(
            listOf(23, 26, 27, 28, 29, 30).map { LocalDate.of(2026, 10, it) },
            parse("unicampus-unten").days.map { it.date },
        )
        assertEquals(43, parse("unicampus-unten").days.sumOf { it.dishes.size })
    }

    @Test
    fun stendal_keepsTheSaturdayAndTheNewTagIcons() {
        val page = parse("stendal")
        // The parser keeps every dated day; the pager drops weekends (visibleDays).
        assertTrue(LocalDate.of(2026, 10, 24) in page.days.map { it.date })
        val tags = page.days.flatMap { it.dishes }.flatMap { it.tags }.associate { it.key to it.label }
        assertEquals("Lamm", tags["lamm"])
        assertEquals("Wild", tags["wild"])
    }

    @Test
    fun pier16_isTheSameListAsOnTheMainHost() {
        val main = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-10-01/pier16.html")).use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals(PierParser.parse(main), PierParser.parse(html("pier16")))
        assertEquals(9, PierParser.parse(html("pier16")).fixed.sumOf { it.dishes.size })
    }
}
