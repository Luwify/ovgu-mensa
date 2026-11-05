package de.luwify.mensamd.scraper

import de.luwify.mensamd.data.remote.MenuFetcher
import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.parser.MenuPageParser
import de.luwify.mensamd.parser.PierParser
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** The CLI on the real fixture pages: all of them, one failing Mensa, the fallback host, reruns. */
class ScraperTest {
    @get:Rule val tmp = TemporaryFolder()

    private val main = "https://main.example/"
    private val backup = "https://backup.example/"
    private val mainDir = "2026-09-30"
    private val backupDir = "2026-10-22-fallback"
    private val mainToday: LocalDate = LocalDate.of(2026, 9, 30)
    private val backupToday: LocalDate = LocalDate.of(2026, 10, 22)

    private fun fixture(dir: String, mensaId: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$dir/$mensaId.html")) { "$dir/$mensaId" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    /** Serves the fixtures of [dir] on [host]; [broken] URLs of a Mensa fail like a server error. */
    private inner class Site(val pages: Map<String, String>, val broken: MutableSet<String> = mutableSetOf()) : MenuFetcher {
        val asked = mutableListOf<String>()
        override suspend fun fetch(url: String): String {
            asked += url
            if (url in broken) throw IOException("HTTP 500 for $url")
            return pages[url] ?: throw IOException("HTTP 404 for $url")
        }
    }

    private fun pages(host: String, dir: String) =
        MensaCatalog.all.associate { (host + it.path) to fixture(dir, it.id) }

    private fun url(host: String, id: String) = host + MensaCatalog.byId(id)!!.path

    private fun scraper(fetcher: MenuFetcher, out: File, today: LocalDate = mainToday) = Scraper(
        fetcher = fetcher, outDir = out, today = today, hosts = listOf(main, backup), pauseMillis = 0, retryAfterMillis = 0,
    )

    private fun File.snapshot(): Map<String, String> =
        walkTopDown().filter { it.isFile }.associate { it.relativeTo(this).path to it.readText() }

    private fun File.day(path: String): DayFile =
        Json.decodeFromString(File(this, path).readText())

    @Test
    fun `every Mensa with dishes gets one file per day`() = runTest {
        val out = tmp.newFolder()
        val results = scraper(Site(pages(main, mainDir)), out).run()

        assertEquals(MensaCatalog.all.size, results.size)
        assertEquals(0, exitCode(results))
        for (mensa in MensaCatalog.all.filter { it.format == de.luwify.mensamd.model.PageFormat.DATED }) {
            val parsed = MenuPageParser.parse(fixture(mainDir, mensa.id))
            for (day in parsed.days.filter { it.dishes.isNotEmpty() }) {
                val file = out.day(dayPath(day.date, mensa.id))
                assertEquals(day.date.toString(), file.date)
                assertEquals(mensa.id, file.mensa.id)
                assertEquals(DayFile.SOURCE_MAIN, file.source)
                assertFalse(file.fixed)
                assertEquals(day.dishes.map { it.name }, file.dishes.map { it.name })
            }
        }
    }

    @Test
    fun `path is year month day mensa`() {
        assertEquals("2026/03/09/stendal.json", dayPath(LocalDate.of(2026, 3, 9), "stendal"))
    }

    @Test
    fun `a dish keeps prices per group, tags, labels and the key of the app`() = runTest {
        val out = tmp.newFolder()
        scraper(Site(pages(main, mainDir)), out).run()

        val day = MenuPageParser.parse(fixture(mainDir, "unicampus-unten")).days.first { it.dishes.isNotEmpty() }
        val dish = day.dishes.first { it.prices.size == 3 }
        val entry = out.day(dayPath(day.date, "unicampus-unten")).dishes.first { it.name == dish.name }
        assertEquals(mapOf("students" to dish.prices[0], "staff" to dish.prices[1], "guests" to dish.prices[2]), entry.prices)
        assertEquals(emptyList<Int>(), entry.unassignedPrices)
        assertEquals(dish.tags, entry.tags)
        assertEquals(dish.badges, entry.labels)
        assertEquals(dish.codes, entry.codes)
        assertEquals(Favorites.key(dish), entry.key)
    }

    @Test
    fun `the legend still lists the price groups in the order the files assume`() {
        val groups = MenuPageParser.parse(fixture(mainDir, "unicampus-unten")).legend.priceGroups.map { it.en.lowercase() }
        assertEquals(3, groups.size)
        assertTrue(groups.toString(), "student" in groups[0] && "staff" in groups[1] && "guest" in groups[2])
    }

    @Test
    fun `the fixed list is a snapshot of the day, prices without groups stay unassigned`() = runTest {
        val out = tmp.newFolder()
        scraper(Site(pages(main, mainDir)), out).run()

        val file = out.day(dayPath(mainToday, "pier16"))
        assertTrue(file.fixed)
        val dishes = PierParser.parse(fixture(mainDir, "pier16")).fixed.flatMap { it.dishes }
        assertEquals(dishes.map { it.name }, file.dishes.map { it.name })
        for ((dish, entry) in dishes.zip(file.dishes)) {
            if (dish.priceGroupsKnown && dish.prices.size == 3) assertEquals(dish.prices, entry.prices.values.toList())
            else {
                assertEquals(emptyMap<String, Int>(), entry.prices)
                assertEquals(dish.prices, entry.unassignedPrices)
            }
        }
    }

    @Test
    fun `no fixed snapshot on a weekend`() = runTest {
        val out = tmp.newFolder()
        val saturday = LocalDate.of(2026, 10, 3)
        scraper(Site(pages(main, mainDir)), out, saturday).run()
        assertFalse(File(out, dayPath(saturday, "pier16")).exists())
    }

    @Test
    fun `a rerun changes nothing`() = runTest {
        val out = tmp.newFolder()
        val site = Site(pages(main, mainDir))
        scraper(site, out).run()
        val before = out.snapshot()
        val modified = out.walkTopDown().filter { it.isFile }.associate { it.path to it.lastModified() }

        val results = scraper(site, out).run()

        assertEquals(before, out.snapshot())
        assertEquals(modified, out.walkTopDown().filter { it.isFile }.associate { it.path to it.lastModified() })
        assertEquals(0, results.sumOf { it.written })
        assertEquals(before.size, results.sumOf { it.unchanged })
    }

    @Test
    fun `a changed plan replaces the day`() = runTest {
        val out = tmp.newFolder()
        scraper(Site(pages(main, mainDir)), out).run()
        val path = dayPath(MenuPageParser.parse(fixture(mainDir, "stendal")).days.first().date, "stendal")
        val file = File(out, path)
        val original = file.readText()
        file.writeText(original.replaceFirst("\"dishes\": [", "\"dishes\": [ "))

        scraper(Site(pages(main, mainDir)), out).run()

        assertEquals(original, file.readText())
    }

    @Test
    fun `a Mensa that fails on every host doesn't stop the others`() = runTest {
        val out = tmp.newFolder()
        val site = Site(pages(main, mainDir), mutableSetOf(url(main, "unicampus-oben"), url(backup, "unicampus-oben")))

        val results = scraper(site, out).run()

        assertFalse(results.first { it.mensa.id == "unicampus-oben" }.fetched)
        assertTrue(results.filter { it.mensa.id != "unicampus-oben" }.all { it.fetched })
        assertTrue(results.first { it.mensa.id == "stendal" }.hasDishes)
        assertEquals(0, exitCode(results))
        assertEquals(2, site.asked.count { it == url(main, "unicampus-oben") }) // first try and the retry
    }

    @Test
    fun `a failed Mensa is tried again at the end`() = runTest {
        val out = tmp.newFolder()
        val target = url(main, "herrenkrug")
        val site = Site(pages(main, mainDir), mutableSetOf(target, url(backup, "herrenkrug")))
        val healing = MenuFetcher { fetched ->
            val result = runCatching { site.fetch(fetched) }
            if (fetched == url(backup, "herrenkrug")) site.broken.clear()
            result.getOrThrow()
        }

        val results = scraper(healing, out).run()

        assertEquals(DayFile.SOURCE_MAIN, results.first { it.mensa.id == "herrenkrug" }.source)
    }

    @Test
    fun `nothing from any Mensa fails the run`() = runTest {
        val failing = MenuFetcher { throw IOException("HTTP 503") }
        assertEquals(1, exitCode(scraper(failing, tmp.newFolder()).run()))

        val closed = MenuFetcher { "<html><div class=\"entry-content\"></div></html>" }
        assertEquals(1, exitCode(scraper(closed, tmp.newFolder()).run()))
    }

    @Test
    fun `the fallback host is used and marked, and each Mensa asks the main site first`() = runTest {
        val out = tmp.newFolder()
        val pages = pages(backup, backupDir)
        val site = Site(pages + (url(main, "stendal") to fixture(mainDir, "stendal")))

        val results = scraper(site, out, backupToday).run()

        val first = MenuPageParser.parse(fixture(backupDir, "unicampus-unten")).days.last { it.dishes.isNotEmpty() }
        assertEquals(DayFile.SOURCE_FALLBACK, out.day(dayPath(first.date, "unicampus-unten")).source)
        assertEquals(DayFile.SOURCE_FALLBACK, results.first { it.mensa.id == "unicampus-unten" }.source)
        assertEquals(DayFile.SOURCE_MAIN, results.first { it.mensa.id == "stendal" }.source)
        for (mensa in MensaCatalog.all) assertTrue(mensa.id, url(main, mensa.id) in site.asked)
    }

    @Test
    fun `the fallback host never replaces the main site and adds nothing to the past`() = runTest {
        val out = tmp.newFolder()
        val days = MenuPageParser.parse(fixture(backupDir, "unicampus-unten")).days.filter { it.dishes.isNotEmpty() }
        val past = days.first()
        val stored = days.last()
        assertNotEquals(past.date, stored.date)
        val today = past.date.plusDays(1)
        val target = File(out, dayPath(stored.date, "unicampus-unten")).apply { parentFile.mkdirs() }
        val mainDay = DayFile(
            date = stored.date.toString(),
            mensa = MensaCatalog.byId("unicampus-unten")!!.toRef(),
            fixed = false,
            source = DayFile.SOURCE_MAIN,
            dishes = emptyList(),
        )
        val original = Json.encodeToString(mainDay)
        target.writeText(original)

        val site = Site(pages(backup, backupDir))
        val results = scraper(site, out, today).run()

        assertEquals(original, target.readText())
        assertFalse(File(out, dayPath(past.date, "unicampus-unten")).exists())
        assertEquals(2, results.first { it.mensa.id == "unicampus-unten" }.kept)
    }

    @Test
    fun `the main site replaces what the fallback host wrote`() = runTest {
        val out = tmp.newFolder()
        val day = MenuPageParser.parse(fixture(backupDir, "unicampus-unten")).days.last { it.dishes.isNotEmpty() }
        scraper(Site(pages(backup, backupDir)), out, backupToday).run()
        assertEquals(DayFile.SOURCE_FALLBACK, out.day(dayPath(day.date, "unicampus-unten")).source)

        scraper(Site(pages(main, backupDir)), out, backupToday).run()

        assertEquals(DayFile.SOURCE_MAIN, out.day(dayPath(day.date, "unicampus-unten")).source)
    }
}
