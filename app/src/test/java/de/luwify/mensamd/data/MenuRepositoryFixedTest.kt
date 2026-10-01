package de.luwify.mensamd.data

import de.luwify.mensamd.data.cache.CachedMenu
import de.luwify.mensamd.data.cache.MenuCache
import de.luwify.mensamd.data.remote.MenuFetcher
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.parser.MenuPageParser
import de.luwify.mensamd.parser.PierParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Repository behavior for PIER 16, the fixed-format page: same cache and refresh rules as the others. */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuRepositoryFixedTest {
    private fun resource(path: String) =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$path")).use { it.readBytes().toString(Charsets.UTF_8) }

    private val id = "pier16"
    private val pierHtml = resource("2026-10-01/pier16.html")
    private val pierPage = PierParser.parse(pierHtml)
    private val donorLegend = MenuPageParser.parse(resource("2026-09-30/unicampus-unten.html")).legend
    private val minute = 60_000L
    private val now = 1_000_000_000_000L

    /** Per-Mensa in-memory cache (the repository reads other Mensen's caches for the legend). */
    private class FakeCache(vararg initial: Pair<String, CachedMenu>) : MenuCache {
        val stored = initial.toMap().toMutableMap()
        var writes = 0
        override suspend fun read(mensaId: String) = stored[mensaId]
        override suspend fun write(mensaId: String, menu: CachedMenu) {
            writes++
            stored[mensaId] = menu
        }
    }

    private class FakeFetcher(vararg results: Any, val gate: CompletableDeferred<Unit>? = null) : MenuFetcher {
        private val queue = ArrayDeque(results.toList())
        var calls = 0
        var lastUrl: String? = null
        override suspend fun fetch(url: String): String {
            calls++
            lastUrl = url
            gate?.await()
            val next = queue.removeFirst()
            if (next is Throwable) throw next
            return next as String
        }
    }

    private fun TestScope.repo(fetcher: MenuFetcher, cache: MenuCache) =
        MenuRepository(fetcher, cache, { now }, UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.collect(repo: MenuRepository): List<MenuState> {
        val states = mutableListOf<MenuState>()
        backgroundScope.launch { repo.observeMenu(id).toList(states) }
        runCurrent()
        return states
    }

    private fun donorEntry() = "unicampus-unten" to CachedMenu(now - 5 * minute, MenuPageParser.parse(resource("2026-09-30/unicampus-unten.html")))

    @Test
    fun emptyCache_fetchesFixedPage_parsesItWithPierParser_andCachesIt() = runTest {
        val cache = FakeCache()
        val fetcher = FakeFetcher(pierHtml)
        val states = collect(repo(fetcher, cache))

        val last = states.last()
        assertEquals(9, last.page!!.fixed.sumOf { it.dishes.size })
        assertTrue(last.page!!.days.isEmpty())
        assertEquals(now, last.fetchedAt)
        assertTrue(fetcher.lastUrl!!.endsWith("pier-16-speiseplan/"))
        assertEquals(1, cache.writes)
        assertEquals(last.page, cache.stored.getValue(id).page)
    }

    @Test
    fun withoutAnyOtherMensaCached_codesStayRaw_legendIsEmpty() = runTest {
        val states = collect(repo(FakeFetcher(pierHtml), FakeCache()))
        val page = states.last().page!!
        assertEquals(Legend.EMPTY, page.legend)
        assertEquals(listOf("3", "9", "a1", "i", "j", "k"), page.fixed.first().dishes.first().codes) // raw codes survive
    }

    @Test
    fun legendIsBorrowedFromAnotherMensasCache() = runTest {
        val cache = FakeCache(donorEntry())
        val states = collect(repo(FakeFetcher(pierHtml), cache))
        val page = states.last().page!!
        assertEquals(donorLegend, page.legend)
        assertEquals(donorLegend, cache.stored.getValue(id).page.legend) // stored with it
        assertTrue(page.legend.codes.any { it.code == "a1" })
        assertEquals(listOf("Studenten", "Bedienstete", "Gäste"), page.legend.priceGroups.map { it.de })
    }

    @Test
    fun cachedPierPageWithoutLegend_getsTheLegendWhenAnotherMensaIsCachedLater() = runTest {
        // PIER cached while no donor existed; now a donor is cached: loading PIER picks it up.
        val cache = FakeCache(id to CachedMenu(now - 5 * minute, pierPage), donorEntry())
        val states = collect(repo(FakeFetcher(), cache))
        assertEquals(donorLegend, states.last().page!!.legend)
        assertEquals(now - 5 * minute, states.last().fetchedAt)
    }

    @Test
    fun freshCache_isShownAndNotRefetched() = runTest {
        val fetcher = FakeFetcher()
        val states = collect(repo(fetcher, FakeCache(id to CachedMenu(now - 5 * minute, pierPage))))
        assertEquals(0, fetcher.calls)
        assertEquals(pierPage.fixed, states.last().page!!.fixed)
        assertEquals(now - 5 * minute, states.last().fetchedAt)
    }

    @Test
    fun staleCache_isShownFirst_thenRefreshed() = runTest {
        val gate = CompletableDeferred<Unit>()
        val changed = pierHtml.replace("Curry-Bratwurst", "Currywurst")
        val cache = FakeCache(id to CachedMenu(now - 40 * minute, pierPage))
        val states = collect(repo(FakeFetcher(changed, gate = gate), cache))

        assertTrue(states.last().isRefreshing)
        assertEquals(pierPage.fixed, states.last().page!!.fixed) // old data while the fetch runs
        gate.complete(Unit)
        runCurrent()
        val last = states.last()
        assertFalse(last.isRefreshing)
        assertEquals(now, last.fetchedAt)
        assertTrue(last.page!!.fixed.flatMap { it.dishes }.any { it.name.de.startsWith("Currywurst") })
    }

    @Test
    fun failedFetch_keepsTheCachedMenu_andFlagsTheFailure() = runTest {
        val cache = FakeCache(id to CachedMenu(now - 40 * minute, pierPage))
        val states = collect(repo(FakeFetcher(IOException("offline")), cache))
        val last = states.last()
        assertEquals(pierPage.fixed, last.page!!.fixed)
        assertEquals(now - 40 * minute, last.fetchedAt)
        assertTrue(last.refreshFailed)
        assertEquals(0, cache.writes)
    }

    @Test
    fun aPageThatParsesToNothing_overACachedMenu_keepsTheCache() = runTest {
        val cache = FakeCache(id to CachedMenu(now - 40 * minute, pierPage))
        val states = collect(repo(FakeFetcher("<html><body><h1>Wartung</h1></body></html>"), cache))
        val last = states.last()
        assertEquals(pierPage.fixed, last.page!!.fixed)
        assertTrue(last.refreshFailed)
        assertEquals(0, cache.writes)
    }

    @Test
    fun emptyCache_andGarbledPage_isAcceptedAsABlankPage() = runTest {
        val cache = FakeCache()
        val states = collect(repo(FakeFetcher("\u0000<<garbage"), cache))
        assertTrue(states.last().page!!.isBlank)
        assertEquals(now, states.last().fetchedAt)
        assertEquals(1, cache.writes)
    }

    @Test
    fun pullToRefresh_forcesAFetchForPierToo() = runTest {
        val fetcher = FakeFetcher(pierHtml)
        val repo = repo(fetcher, FakeCache(id to CachedMenu(now - minute, pierPage)))
        val states = collect(repo)
        assertEquals(0, fetcher.calls)
        repo.refresh(id)
        runCurrent()
        assertEquals(1, fetcher.calls)
        assertEquals(now, states.last().fetchedAt)
    }
}
