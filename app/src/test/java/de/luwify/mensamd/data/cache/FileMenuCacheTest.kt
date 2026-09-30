package de.luwify.mensamd.data.cache

import de.luwify.mensamd.parser.MenuPageParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileMenuCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun page(name: String) = MenuPageParser.parse(
        checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/$name.html")).use { it.readBytes().toString(Charsets.UTF_8) },
    )

    @Test
    fun roundTrip_keepsEverythingIncludingLegendAndDates() = runTest {
        val cache = FileMenuCache(File(tmp.root, "cache"))
        val cached = CachedMenu(123L, page("unicampus-oben")) // gap in dates, legend, tags, prefixes
        cache.write("m", cached)
        assertEquals(cached, cache.read("m"))
        assertTrue(cached.page.legend.codes.isNotEmpty())
    }

    @Test
    fun oneFilePerMensa_andOverwriteLeavesNoTempFile() = runTest {
        val dir = File(tmp.root, "cache")
        val cache = FileMenuCache(dir)
        cache.write("a", CachedMenu(1, page("blub-wernigerode")))
        cache.write("b", CachedMenu(2, page("stendal")))
        cache.write("a", CachedMenu(3, page("blub-wernigerode")))
        assertEquals(listOf("menu-a.json", "menu-b.json"), dir.list()!!.sorted())
        assertEquals(3L, cache.read("a")?.fetchedAt)
        assertEquals(2L, cache.read("b")?.fetchedAt)
    }

    @Test
    fun missingCorruptAndOldSchemaFiles_readAsNull() = runTest {
        val dir = tmp.newFolder("c")
        val cache = FileMenuCache(dir)
        assertNull(cache.read("none"))

        File(dir, "menu-bad.json").writeText("{ not json")
        assertNull(cache.read("bad"))

        cache.write("old", CachedMenu(1, page("blub-wernigerode")))
        val f = File(dir, "menu-old.json")
        f.writeText(f.readText().replace("\"schema\":1", "\"schema\":0"))
        assertNull(cache.read("old"))
    }
}
