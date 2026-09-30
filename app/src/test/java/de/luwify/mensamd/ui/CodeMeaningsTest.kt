package de.luwify.mensamd.ui

import de.luwify.mensamd.model.CodeCategory
import de.luwify.mensamd.model.LegendCode
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.codeCategory
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeMeaningsTest {
    private fun lc(code: String, de: String, en: String) =
        code to LegendCode(code, codeCategory(code), LocalizedText(de, en))

    private val legend = mapOf(
        lc("1", "mit Farbstoff", "with food colouring"),
        lc("9", "mit Süßungsmitteln", "with sweetener"),
        lc("a1", "Weizen", "wheat"),
        lc("g", "Milch", "milk"),
        lc("Q", "echtes Karmin", "cochineal"),
    )

    @Test
    fun categories() {
        assertEquals(CodeCategory.ADDITIVE, codeCategory("10"))
        assertEquals(CodeCategory.ALLERGEN, codeCategory("h7"))
        assertEquals(CodeCategory.OTHER, codeCategory("Q"))
    }

    @Test
    fun groupsByCategoryInOrder_withLocalizedText() {
        val groups = groupCodes(listOf("g", "Q", "9", "a1", "1"), legend, "de")
        assertEquals(listOf(CodeCategory.ADDITIVE, CodeCategory.ALLERGEN, CodeCategory.OTHER), groups.map { it.category })
        assertEquals(listOf("1", "9"), groups[0].codes.map { it.code })
        assertEquals(listOf("a1", "g"), groups[1].codes.map { it.code })
        assertEquals("Weizen", groups[1].codes[0].text)
        assertEquals("cochineal", groupCodes(listOf("Q"), legend, "en").single().codes.single().text)
    }

    @Test
    fun additivesSortNumerically_notAlphabetically() {
        val codes = groupCodes(listOf("10", "2", "1"), emptyMap(), "de").single().codes.map { it.code }
        assertEquals(listOf("1", "2", "10"), codes)
    }

    @Test
    fun unknownCodeIsKeptWithoutText_duplicatesCollapse() {
        val groups = groupCodes(listOf("zz", "g", "g"), legend, "en")
        val all = groups.flatMap { it.codes }
        assertEquals(listOf("g", "zz"), all.map { it.code }.sorted())
        assertNull(all.first { it.code == "zz" }.text)
    }

    @Test
    fun noCodes_noGroups() = assertEquals(emptyList<CodeGroup>(), groupCodes(emptyList(), legend, "de"))

    @Test
    fun everyCodeOnTheRealPagesResolves() {
        // Guards the legend merge: if a page's dish uses a code its own legend lacks, we'd show a bare code.
        for (name in listOf("unicampus-unten", "unicampus-oben", "kellercafe", "herrenkrug", "stendal", "halberstadt", "wernigerode", "blub-wernigerode")) {
            val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/$name.html")).use { it.readBytes().toString(Charsets.UTF_8) }
            val page = MenuPageParser.parse(html)
            val index = page.legend.codes.associateBy { it.code }
            val unknown = page.days.flatMap { it.dishes }.flatMap { it.codes }.filter { it !in index }.toSet()
            assertEquals("$name unknown codes", emptySet<String>(), unknown)
        }
    }
}
