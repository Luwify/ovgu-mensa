package de.luwify.mensamd.data

import de.luwify.mensamd.data.cache.CachedMenu
import de.luwify.mensamd.data.cache.MenuCache
import de.luwify.mensamd.data.remote.MenuFetcher
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.parser.MenuPageParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MenuRepositoryTest {
    private val id = "blub-wernigerode" // any id from MensaCatalog
    private val html = checkNotNull(javaClass.getResourceAsStream("/fixtures/2026-09-30/blub-wernigerode.html"))
        .use { it.readBytes().toString(Charsets.UTF_8) }
    private val freshPage = MenuPageParser.parse(html)

    private val minute = 60_000L
    private val now = 1_000_000_000_000L

    private class FakeCache(var stored: CachedMenu? = null) : MenuCache {
        var writes = 0
        override suspend fun read(mensaId: String) = stored
        override suspend fun write(mensaId: String, menu: CachedMenu) {
            writes++
            stored = menu
        }
    }

    /** Suspends until [gate] completes, then returns the next queued result (a throwable is thrown). */
    private class FakeFetcher(vararg results: Any, val gate: CompletableDeferred<Unit>? = null) : MenuFetcher {
        private val queue = ArrayDeque(results.toList())
        var calls = 0
        override suspend fun fetch(url: String): String {
            calls++
            gate?.await()
            val next = queue.removeFirst()
            if (next is Throwable) throw next
            return next as String
        }
    }

    // A page that differs from the fixture, so "cache" and "fresh" are distinguishable.
    private val oldPage = MenuPage(freshPage.days.take(1), freshPage.legend)

    private fun TestScope.repo(fetcher: MenuFetcher, cache: MenuCache, clock: () -> Long = { now }) =
        MenuRepository(fetcher, cache, clock, UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.collect(repo: MenuRepository, mensaId: String = id): List<MenuState> {
        val states = mutableListOf<MenuState>()
        backgroundScope.launch { repo.observeMenu(mensaId).toList(states) }
        runCurrent()
        return states
    }

    @Test
    fun cacheHit_freshCache_isEmittedAndNotRefetched() = runTest {
        val cache = FakeCache(CachedMenu(now - 5 * minute, oldPage))
        val fetcher = FakeFetcher()
        val states = collect(repo(fetcher, cache))
        runCurrent()

        assertEquals(1, states.size)
        assertEquals(MenuState(oldPage, now - 5 * minute), states.single())
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun staleCache_isEmittedFirstThenRefreshed() = runTest {
        val cache = FakeCache(CachedMenu(now - 31 * minute, oldPage))
        val gate = CompletableDeferred<Unit>()
        val fetcher = FakeFetcher(html, gate = gate)
        val states = collect(repo(fetcher, cache))
        runCurrent()

        // Fetch is in flight: the UI already has the cached page, marked as refreshing.
        assertEquals(MenuState(oldPage, now - 31 * minute, isRefreshing = true), states.last())

        gate.complete(Unit)
        runCurrent()

        assertEquals(MenuState(freshPage, now), states.last())
        assertEquals(1, fetcher.calls)
        assertEquals(1, cache.writes)
        assertEquals(CachedMenu(now, freshPage), cache.stored)
    }

    @Test
    fun failedFetch_keepsCacheAndLastUpdated() = runTest {
        val cache = FakeCache(CachedMenu(now - 40 * minute, oldPage))
        val states = collect(repo(FakeFetcher(IOException("offline")), cache))
        runCurrent()

        val last = states.last()
        assertEquals(oldPage, last.page)
        assertEquals(now - 40 * minute, last.fetchedAt)
        assertTrue(last.refreshFailed)
        assertFalse(last.isRefreshing)
        assertEquals(0, cache.writes)
        assertEquals(CachedMenu(now - 40 * minute, oldPage), cache.stored)
    }

    @Test
    fun emptyCache_failedFetch_hasNoPageAndReportsFailure() = runTest {
        val cache = FakeCache()
        val states = collect(repo(FakeFetcher(IOException("offline")), cache))
        runCurrent()

        assertEquals(MenuState(page = null, fetchedAt = null, isRefreshing = false, refreshFailed = true), states.last())
        assertNull(cache.stored)
    }

    @Test
    fun emptyCache_successfulFetch_fillsStateAndCache() = runTest {
        val cache = FakeCache()
        val states = collect(repo(FakeFetcher(html), cache))
        runCurrent()

        assertEquals(MenuState(freshPage, now), states.last())
        assertEquals(CachedMenu(now, freshPage), cache.stored)
    }

    @Test
    fun manualRefresh_fetchesEvenWhenCacheIsFresh() = runTest {
        val cache = FakeCache(CachedMenu(now - minute, oldPage))
        val fetcher = FakeFetcher(html)
        val repo = repo(fetcher, cache)
        val states = collect(repo)
        runCurrent()
        assertEquals(0, fetcher.calls)

        repo.refresh(id)
        runCurrent()

        assertEquals(1, fetcher.calls)
        assertEquals(MenuState(freshPage, now), states.last())
    }

    @Test
    fun refreshIfStale_onForeground_onlyFetchesWhenOld() = runTest {
        var time = now
        val cache = FakeCache(CachedMenu(now - 10 * minute, oldPage))
        val fetcher = FakeFetcher(html)
        val repo = repo(fetcher, cache) { time }

        repo.refreshIfStale(id)
        assertEquals(0, fetcher.calls)

        time = now + 25 * minute // cache is now 35 min old
        repo.refreshIfStale(id)
        assertEquals(1, fetcher.calls)
        assertEquals(time, cache.stored?.fetchedAt)
    }

    @Test
    fun clockMovedBackwards_countsAsStale() = runTest {
        val cache = FakeCache(CachedMenu(now + 5 * minute, oldPage))
        val fetcher = FakeFetcher(html)
        repo(fetcher, cache).refreshIfStale(id)
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun concurrentRefreshes_shareOneFetch() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fetcher = FakeFetcher(html, gate = gate)
        val repo = repo(fetcher, FakeCache())

        backgroundScope.launch { repo.refresh(id) }
        backgroundScope.launch { repo.refresh(id) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, fetcher.calls)
    }

    @Test
    fun retryAfterFailure_clearsTheFailureFlag() = runTest {
        val repo = repo(FakeFetcher(IOException("offline"), html), FakeCache())
        val states = collect(repo)
        runCurrent()
        assertTrue(states.last().refreshFailed)

        repo.refresh(id)
        runCurrent()

        assertEquals(MenuState(freshPage, now), states.last())
    }

    @Test
    fun cacheWriteFailure_doesNotHideFreshData() = runTest {
        val broken = object : MenuCache {
            override suspend fun read(mensaId: String): CachedMenu? = null
            override suspend fun write(mensaId: String, menu: CachedMenu) = throw IOException("disk full")
        }
        val states = collect(repo(FakeFetcher(html), broken))
        runCurrent()
        assertEquals(MenuState(freshPage, now), states.last())
    }

    @Test
    fun unknownMensa_reportsFailureInsteadOfThrowing() = runTest {
        val repo = repo(FakeFetcher(), FakeCache())
        val states = collect(repo, "does-not-exist")
        assertTrue(states.last().refreshFailed)
        assertNull(states.last().page)
    }
}
