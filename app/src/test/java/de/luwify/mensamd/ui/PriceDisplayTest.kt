package de.luwify.mensamd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceDisplayTest {
    private val three = listOf(340, 510, 650)

    @Test fun studentsByDefault() = assertEquals(PriceSplit(0, listOf(1, 2)), splitPrices(three, 0))

    @Test fun staffAndGuests() {
        assertEquals(PriceSplit(1, listOf(0, 2)), splitPrices(three, 1))
        assertEquals(PriceSplit(2, listOf(0, 1)), splitPrices(three, 2))
    }

    @Test fun groupMissingOnThisDish_fallsBackToFirst() {
        assertEquals(PriceSplit(0, listOf(1)), splitPrices(listOf(420, 590), 2))
        assertEquals(PriceSplit(0, listOf(1, 2)), splitPrices(three, 7))
    }

    @Test fun singlePrice_hasNothingToExpand() = assertEquals(PriceSplit(0, emptyList()), splitPrices(listOf(200), 0))

    @Test fun noPrices_isNull() = assertNull(splitPrices(emptyList(), 0))
}
