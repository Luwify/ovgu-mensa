package de.luwify.mensamd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLayoutTest {
    @Test fun normalPhone_usesSideTags() {
        assertTrue(tagsOnRight(screenWidthDp = 411, fontScale = 1.0f))
        assertTrue(tagsOnRight(360, 1.15f)) // both limits inclusive
    }

    @Test fun narrowScreen_fallsBackToTagsBelow() {
        assertFalse(tagsOnRight(359, 1.0f))
        assertFalse(tagsOnRight(320, 1.0f))
    }

    @Test fun largeFontScale_fallsBackToTagsBelow() {
        assertFalse(tagsOnRight(411, 1.3f))
        assertFalse(tagsOnRight(411, 1.16f))
    }

    @Test fun tagColumnIsAboutAThirdOfTheCardContent() {
        assertEquals((411 - 64) / 3f, tagColumnMaxWidthDp(411), 0.001f)
        // and the name always keeps at least the other two thirds
        assertTrue(tagColumnMaxWidthDp(360) * 2 <= 360 - 64)
    }
}
