package de.luwify.mensamd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagMarkersTest {
    @Test
    fun everyKnownTagExceptVeganHasADistinctGlyph() {
        val keys = listOf("vegetarisch", "knoblauch", "gefluegel", "schwein", "fisch", "rind", "alkohol", "suppe")
        val glyphs = keys.map { assertNotNull(it, tagMarkerGlyph(it)); tagMarkerGlyph(it)!! }
        assertEquals(keys.size, glyphs.toSet().size)
        assertTrue(glyphs.all { it.isNotEmpty() })
    }

    @Test
    fun veganAndUnknownTagsHaveNoGlyph() {
        assertNull(tagMarkerGlyph("vegan"))
        assertNull(tagMarkerGlyph("neu_ding"))
    }

    @Test
    fun letterFallback() {
        assertEquals("N", tagMarkerLetter("Neues Ding", "neu_ding"))
        assertEquals("E", tagMarkerLetter("  enth lt Alkohol", "alkohol"))
        assertEquals("X", tagMarkerLetter("", "xyz"))
        assertEquals("?", tagMarkerLetter("", ""))
    }
}
