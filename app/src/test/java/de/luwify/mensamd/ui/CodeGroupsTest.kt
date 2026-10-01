package de.luwify.mensamd.ui

import androidx.compose.ui.state.ToggleableState
import de.luwify.mensamd.model.CodeCategory
import de.luwify.mensamd.model.LegendCode
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.codeCategory
import de.luwify.mensamd.parser.MenuPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeGroupsTest {
    private fun lc(code: String) = LegendCode(code, codeCategory(code), LocalizedText(code))

    private val realLegend by lazy {
        val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/unicampus-unten.html")).use { it.readBytes().toString(Charsets.UTF_8) }
        MenuPageParser.parse(html).legend.codes
    }

    @Test
    fun sectionsOrder_allergensAdditivesOther() {
        val sections = codeSections(realLegend)
        assertEquals(listOf(CodeCategory.ALLERGEN, CodeCategory.ADDITIVE, CodeCategory.OTHER), sections.map { it.category })
    }

    @Test
    fun realLegend_groupsGlutenAndNutsAsFamilies_restAreSingles() {
        val allergens = codeSections(realLegend).first { it.category == CodeCategory.ALLERGEN }.groups
        val families = allergens.filter { it.family != null }
        assertEquals(listOf("a", "h"), families.map { it.family })
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5", "a6"), families[0].codes.map { it.code })
        assertEquals((1..9).map { "h$it" }, families[1].codes.map { it.code })
        val singles = allergens.filter { it.family == null }.flatMap { it.codes }.map { it.code }
        assertEquals(listOf("b", "c", "d", "e", "f", "g", "i", "j", "k", "l", "m", "n"), singles)
    }

    @Test
    fun everyLegendCodeAppearsExactlyOnce() {
        val all = codeSections(realLegend).flatMap { it.groups }.flatMap { it.codes }.map { it.code }
        assertEquals(realLegend.map { it.code }.sorted(), all.sorted())
    }

    @Test
    fun familyOfOne_isNotAFamily() {
        val groups = codeSections(listOf(lc("a1"), lc("b"))).single().groups
        assertTrue(groups.all { it.family == null })
    }

    @Test
    fun emptyLegend_noSections() = assertEquals(emptyList<ExcludeSection>(), codeSections(emptyList()))

    @Test
    fun groupState_andToggle() {
        val gluten = listOf(lc("a1"), lc("a2"), lc("a3"))
        assertEquals(ToggleableState.Off, groupState(gluten, emptySet()))
        assertEquals(ToggleableState.Indeterminate, groupState(gluten, setOf("a2")))
        assertEquals(ToggleableState.On, groupState(gluten, setOf("a1", "a2", "a3", "x")))
        assertTrue(groupToggleSelects(gluten, emptySet()))
        assertTrue(groupToggleSelects(gluten, setOf("a2")))
        assertFalse(groupToggleSelects(gluten, setOf("a1", "a2", "a3")))
    }
}
