package de.luwify.mensamd.data

import de.luwify.mensamd.data.cache.CachedMenu
import de.luwify.mensamd.data.cache.FileMenuCache
import de.luwify.mensamd.data.cache.MenuCache
import de.luwify.mensamd.data.remote.MenuFetcher
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LocalizedText
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.MenuPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneOffset

/** Past days stay cached for 14 days, for the statistics only: `page` remains what the site gave. */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuRepositoryPastDaysTest {
    @get:Rule val tmp = TemporaryFolder()
    private val id = "unicampus-unten"
    private val monday = LocalDate.of(2026, 11, 2)
    private var today = monday

    private fun day(date: LocalDate, name: String = "Gericht $date") =
        MenuDay(date, listOf(Dish(LocalizedText(name), emptyList(), listOf(310), emptyList(), emptyList(), null, null)))

    private fun row(d: MenuDay) = """<table><thead><tr><th>Tag, ${d.date.dayOfMonth}.${d.date.monthValue}.${d.date.year}</th></tr></thead>
        <tbody><tr><td>${d.dishes.single().name.de}<br/><span class='mensapreis'>(3,10)</span></td></tr></tbody></table>"""

    /** Serves a page with these days. */
    private class Site(var html: String) : MenuFetcher {
        override suspend fun fetch(url: String) = html
    }

    private fun page(vararg days: MenuDay) =
        "<div id='content'><div class='entry-content'><div class='mensa'>" + days.joinToString("", transform = ::row) + "</div></div></div>"

    private class Memory(var stored: CachedMenu? = null) : MenuCache {
        override suspend fun read(mensaId: String) = stored
        override suspend fun write(mensaId: String, menu: CachedMenu) {
            stored = menu
        }
    }

    private fun TestScope.repo(site: MenuFetcher, cache: MenuCache) = MenuRepository(
        site, cache, { today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }, UnconfinedTestDispatcher(testScheduler),
        hosts = listOf("https://main.example/"), zone = { ZoneOffset.UTC },
    )

    @Test fun aDayTheSiteDroppedStaysInPastNotInThePage() = runTest {
        val site = Site(page(day(monday), day(monday.plusDays(1))))
        val cache = Memory()
        val repo = repo(site, cache)
        repo.refresh(id)
        today = monday.plusDays(1)
        site.html = page(day(monday.plusDays(1)))
        repo.refresh(id)

        val state = repo.current(id)
        assertEquals(listOf(monday.plusDays(1)), state.page!!.days.map { it.date })
        assertEquals(listOf(monday), state.past.map { it.date })
        assertEquals(listOf(monday, monday.plusDays(1)), state.pageWithPast!!.days.map { it.date })
        assertEquals(state.past, cache.stored!!.past)
    }

    @Test fun keptForExactly14Days() {
        val old = MenuState(MenuPage(listOf(day(monday)), Legend.EMPTY))
        val fresh = MenuPage(emptyList(), Legend.EMPTY)
        assertEquals(listOf(monday), keptPastDays(old, fresh, monday.plusDays(14)).map { it.date })
        assertTrue(keptPastDays(old, fresh, monday.plusDays(15)).isEmpty())
        // ... also once it sits in the past list.
        val kept = MenuState(MenuPage(emptyList(), Legend.EMPTY), past = listOf(day(monday)))
        assertEquals(listOf(monday), keptPastDays(kept, fresh, monday.plusDays(14)).map { it.date })
        assertTrue(keptPastDays(kept, fresh, monday.plusDays(15)).isEmpty())
    }

    @Test fun aFutureDayTheSiteDroppedIsNotKept() {
        val old = MenuState(MenuPage(listOf(day(monday.plusDays(3))), Legend.EMPTY))
        assertTrue(keptPastDays(old, MenuPage(emptyList(), Legend.EMPTY), monday).isEmpty())
    }

    @Test fun aDayStillListedIsNotDuplicatedAndTheNewestCopyWins() {
        val fresh = MenuPage(listOf(day(monday)), Legend.EMPTY)
        val old = MenuState(fresh, past = listOf(day(monday, "alt")))
        assertTrue(keptPastDays(old, fresh, monday).isEmpty())
        val moved = MenuState(MenuPage(listOf(day(monday, "neu")), Legend.EMPTY), past = listOf(day(monday, "alt")))
        assertEquals("neu", keptPastDays(moved, MenuPage(emptyList(), Legend.EMPTY), monday).single().dishes.single().name.de)
    }

    @Test fun aFailedRefreshKeepsThePastDays() = runTest {
        val cache = Memory(CachedMenu(1, MenuPage(listOf(day(monday)), Legend.EMPTY), past = listOf(day(monday.minusDays(1)))))
        val repo = repo(object : MenuFetcher { override suspend fun fetch(url: String) = error("down") }, cache)
        repo.refresh(id)
        assertEquals(listOf(monday.minusDays(1)), repo.current(id).past.map { it.date })
    }

    @Test fun survivesAColdStartAndAnOldCacheFileStillLoads() = runTest {
        val cache = FileMenuCache(tmp.root)
        cache.write(id, CachedMenu(1, MenuPage(emptyList(), Legend.EMPTY), past = listOf(day(monday))))
        assertEquals(listOf(monday), repo(Site(""), cache).current(id).past.map { it.date })

        // A file from before this field: same schema, nothing to migrate.
        tmp.root.resolve("menu-$id.json").writeText("""{"fetchedAt":1,"page":{"days":[],"legend":{"priceGroups":[],"codes":[],"notes":[]}},"schema":1}""")
        assertEquals(emptyList<MenuDay>(), FileMenuCache(tmp.root).read(id)!!.past)
    }
}
