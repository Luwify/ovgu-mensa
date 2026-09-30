package de.luwify.mensamd.data

import de.luwify.mensamd.data.cache.CachedMenu
import de.luwify.mensamd.data.cache.MenuCache
import de.luwify.mensamd.data.remote.MenuFetcher
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.parser.MenuPageParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * What the repository knows about one Mensa right now.
 * [page] null means nothing cached and nothing fetched yet; with [isRefreshing] false and
 * [refreshFailed] true that is the "empty cache, offline" case.
 */
data class MenuState(
    val page: MenuPage? = null,
    /** Epoch millis of the last successful fetch: the "last updated" value. */
    val fetchedAt: Long? = null,
    val isRefreshing: Boolean = false,
    /** The most recent refresh attempt failed. Cleared when the next attempt starts. */
    val refreshFailed: Boolean = false,
)

/**
 * Stale-while-revalidate over a JSON cache. The page has no ETag/Last-Modified, so freshness is our
 * own timer ([staleAfterMillis]).
 */
class MenuRepository(
    private val fetcher: MenuFetcher,
    private val cache: MenuCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val staleAfterMillis: Long = 30 * 60 * 1000L,
) {
    private class Entry {
        val state = MutableStateFlow(MenuState())
        val refreshLock = Mutex()
        val loadLock = Mutex()
        @Volatile var cacheLoaded = false
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private fun entry(id: String) = entries.getOrPut(id) { Entry() }

    /**
     * Emits the cached menu immediately (or an empty state), then every change. If the data is
     * missing or stale a background refresh starts, so a failure arrives as a later emission.
     * Never completes; cancel by cancelling the collector.
     */
    fun observeMenu(id: String): Flow<MenuState> = channelFlow {
        val e = entry(id)
        loadCache(id)
        launch { refreshIfStale(id) }
        e.state.collect { send(it) }
    }

    /** For app foreground: refreshes only when missing or older than the stale threshold. */
    suspend fun refreshIfStale(id: String) {
        val fetchedAt = loadCache(id).fetchedAt
        // A negative age means the device clock moved backwards; treat that as stale too.
        if (fetchedAt == null || clock() - fetchedAt !in 0..staleAfterMillis) refresh(id)
    }

    /** For pull-to-refresh: always fetches, unless a fetch for this Mensa is already running. */
    suspend fun refresh(id: String) {
        val e = entry(id)
        loadCache(id)
        if (!e.refreshLock.tryLock()) return // a running fetch serves this request too
        try {
            e.state.update { it.copy(isRefreshing = true, refreshFailed = false) }
            val url = MensaCatalog.byId(id)?.url
            val result = runCatching {
                requireNotNull(url) { "unknown Mensa id: $id" }
                val html = fetcher.fetch(url)
                withContext(workDispatcher) {
                    val cached = CachedMenu(fetchedAt = clock(), page = MenuPageParser.parse(html))
                    // A failing cache write must not hide a good fetch from the UI.
                    runCatching { cache.write(id, cached) }
                    cached
                }
            }
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
            val fresh = result.getOrNull()
            e.state.update {
                if (fresh != null) MenuState(fresh.page, fresh.fetchedAt)
                else it.copy(isRefreshing = false, refreshFailed = true)
            }
        } finally {
            // Also covers cancellation, so the spinner never sticks.
            e.state.update { it.copy(isRefreshing = false) }
            e.refreshLock.unlock()
        }
    }

    /** Fills the state from disk once; returns the state after that. */
    private suspend fun loadCache(id: String): MenuState {
        val e = entry(id)
        if (e.cacheLoaded) return e.state.value
        e.loadLock.lock()
        try {
            if (!e.cacheLoaded) {
                val cached = withContext(workDispatcher) { cache.read(id) }
                // A refresh that already finished is newer than anything on disk.
                if (cached != null) e.state.update { if (it.fetchedAt == null) it.copy(page = cached.page, fetchedAt = cached.fetchedAt) else it }
                e.cacheLoaded = true
            }
        } finally {
            e.loadLock.unlock()
        }
        return e.state.value
    }
}
