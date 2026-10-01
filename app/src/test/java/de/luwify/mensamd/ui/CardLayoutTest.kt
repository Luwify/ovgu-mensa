package de.luwify.mensamd.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLayoutTest {
    @Test fun normalPhone_allowsTagsBesidePrice() {
        assertTrue(tagsBesidePrice(windowWidthDp = 411, fontScale = 1.0f))
        assertTrue(tagsBesidePrice(360, 1.15f)) // both limits inclusive
    }

    @Test fun narrowWindow_alwaysTagsBelow() {
        assertFalse(tagsBesidePrice(359, 1.0f))
        assertFalse(tagsBesidePrice(320, 1.0f))
    }

    @Test fun largeFontScale_alwaysTagsBelow() {
        assertFalse(tagsBesidePrice(411, 1.16f))
        assertFalse(tagsBesidePrice(411, 1.3f))
    }

    @Test fun fits_whenPillGapAndTagsFitExactlyOrLess() {
        assertTrue(fitsBesidePrice(priceWidth = 300, gap = 30, tagsWidth = 500, maxWidth = 830))
        assertTrue(fitsBesidePrice(300, 30, 400, 830))
    }

    @Test fun wraps_whenOnePixelTooWide() {
        assertFalse(fitsBesidePrice(300, 30, 501, 830))
        assertFalse(fitsBesidePrice(300, 30, 900, 830))
    }
}
