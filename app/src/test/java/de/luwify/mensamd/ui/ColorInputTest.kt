package de.luwify.mensamd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorInputTest {
    @Test
    fun hex_parsesSixDigitsWithOrWithoutHash_anyCase() {
        assertEquals(0x7839D0, ColorInput.parseHex("#7839D0"))
        assertEquals(0x7839D0, ColorInput.parseHex("7839d0"))
        assertEquals(0x7839D0, ColorInput.parseHex("  #7839d0 "))
        assertNull(ColorInput.parseHex("#7839D")) // mid-typing: not applied yet
        assertNull(ColorInput.parseHex("#abc")) // no shorthand: it would fire while typing #abcdef
        assertNull(ColorInput.parseHex("#GGGGGG"))
        assertEquals("#7839D0", ColorInput.formatHex(0x7839D0))
        assertEquals("#000000", ColorInput.formatHex(0))
    }

    @Test
    fun rgb_channelsParseAndReplace() {
        assertEquals(255, ColorInput.parseChannel("255"))
        assertEquals(0, ColorInput.parseChannel("0"))
        assertNull(ColorInput.parseChannel("256"))
        assertNull(ColorInput.parseChannel(""))
        assertNull(ColorInput.parseChannel("-1"))
        val c = 0x112233
        assertEquals(0x11, ColorInput.red(c))
        assertEquals(0x22, ColorInput.green(c))
        assertEquals(0x33, ColorInput.blue(c))
        assertEquals(0xFF2233, ColorInput.withChannel(c, 0, 255))
        assertEquals(0x110033, ColorInput.withChannel(c, 1, 0))
        assertEquals(0x1122AB, ColorInput.withChannel(c, 2, 0xAB))
    }

    @Test
    fun hueSlider_keepsSaturationAndLightness_andGivesGrayAColor() {
        val pastel = 0xF4C2C2 // light pink
        val turned = ColorInput.withHue(pastel, 210f)
        assertEquals(210, ColorInput.hue(turned))
        // still pastel: every channel high
        assert(ColorInput.red(turned) > 150 && ColorInput.green(turned) > 150 && ColorInput.blue(turned) > 150)
        val fromGray = ColorInput.withHue(0x808080, 120f)
        assertEquals(120, ColorInput.hue(fromGray))
        assertEquals(ColorInput.hue(de.luwify.mensamd.data.settings.hueToAccent(120)), ColorInput.hue(fromGray))
    }

    @Test
    fun defaultAccent_isTheVioletSwatch() {
        assertEquals(de.luwify.mensamd.data.settings.CustomThemeSpec.DEFAULT_ACCENT, de.luwify.mensamd.data.settings.hueToAccent(265))
    }
}
