package de.luwify.mensamd.ui

import de.luwify.mensamd.ui.theme.Sunflower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SunflowerTest {
    @Test
    fun buildsForBothThemes_withPetalsAndCenter() {
        for (dark in listOf(false, true)) {
            val v = Sunflower.vector(dark)
            assertEquals(24f, v.viewportWidth, 0f)
            assertEquals(24f, v.viewportHeight, 0f)
            // 12 rotated petal groups + 2 center paths
            assertEquals(Sunflower.PETALS + 2, v.root.size)
        }
    }

    @Test
    fun themesUseDifferentVectors_andAreCached() {
        assertNotSame(Sunflower.vector(false), Sunflower.vector(true))
        assertSame(Sunflower.vector(true), Sunflower.vector(true))
    }
}
