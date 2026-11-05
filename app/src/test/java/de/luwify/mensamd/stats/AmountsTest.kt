package de.luwify.mensamd.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountsTest {
    @Test fun parsesEuroInputs() {
        assertEquals(10_000, Amounts.parseEuro("10"))
        assertEquals(10_500, Amounts.parseEuro("10,5"))
        assertEquals(3_100, Amounts.parseEuro(" 3,10 € "))
        assertEquals(250, Amounts.parseEuro("0.25"))
        assertNull(Amounts.parseEuro("0"))
        assertNull(Amounts.parseEuro("-3"))
        assertNull(Amounts.parseEuro("3,105"))
        assertNull(Amounts.parseEuro("abc"))
        assertNull(Amounts.parseEuro(""))
        assertNull(Amounts.parseEuro("12345"))
    }

    @Test fun inputText() {
        assertEquals("5,60", Amounts.toInput(5_600, true))
        assertEquals("20", Amounts.toInput(20_000, true))
        assertEquals("0.25", Amounts.toInput(250, false))
    }
}
