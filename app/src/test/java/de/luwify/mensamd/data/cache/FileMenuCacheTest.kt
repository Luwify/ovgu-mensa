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

    @Test
    fun fixedPage_roundTrips_andOldFilesWithoutTheNewFieldsStillRead() = runTest {
        val cache = FileMenuCache(File(tmp.root, "fixed"))
        val pier = de.luwify.mensamd.parser.PierParser.parse(
            checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-10-01/pier16.html")).use { it.readBytes().toString(Charsets.UTF_8) },
        )
        val cached = CachedMenu(7L, pier)
        cache.write("pier16", cached)
        assertEquals(cached, cache.read("pier16"))
        assertTrue(cache.read("pier16")!!.page.fixed.flatMap { it.dishes }.any { !it.priceGroupsKnown })

        // a cache file written before PIER 16 existed has no "fixed" and no "priceGroupsKnown"
        val old = """{"fetchedAt":5,"page":{"days":[{"date":"2026-10-05","dishes":[{"name":{"de":"A","en":"A"},"badges":[],"prices":[100],"tags":[],"codes":[],"co2Grams":null,"waterLiters":null}]}],"legend":{"priceGroups":[],"codes":[],"notes":[]}},"schema":1}"""
        File(tmp.root, "fixed/menu-old.json").writeText(old)
        val read = cache.read("old")!!
        assertEquals(emptyList<de.luwify.mensamd.model.FixedSection>(), read.page.fixed)
        assertTrue(read.page.days.single().dishes.single().priceGroupsKnown)
    }
}
