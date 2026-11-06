package de.luwify.mensamd.data

import de.luwify.mensamd.data.cache.CachedMenu
import de.luwify.mensamd.data.cache.MenuCache
import de.luwify.mensamd.data.remote.MenuFetcher
import de.luwify.mensamd.data.remote.MenuHosts
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.PageFormat
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.parser.MenuPageParser
import de.luwify.mensamd.parser.PageCheck
import de.luwify.mensamd.parser.PierParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    /** [page] came from a fallback host ([MenuHosts]), not the main site. */
    val fromFallback: Boolean = false,
    /** Recent days the site no longer lists (see [PAST_DAYS_KEPT]). Only the card statistics use them. */
    val past: List<MenuDay> = emptyList(),
) {
    /** [page] plus the [past] days, oldest first: what the statistics can look up. */
    val pageWithPast: MenuPage? get() = page?.let { if (past.isEmpty()) it else it.copy(days = past + it.days) }
}

/** How many days back a menu stays cached after the site dropped it: corrections of recent payments still find it. */
const val PAST_DAYS_KEPT = 14L

/**
 * What to keep of [old] next to the [fresh] page: its days up to [today] that [fresh] no longer lists, at most
 * [PAST_DAYS_KEPT] days back. A future day the site dropped was cancelled, so it goes.
 */
fun keptPastDays(old: MenuState, fresh: MenuPage, today: LocalDate): List<MenuDay> {
    val listed = fresh.days.mapTo(HashSet()) { it.date }
    val oldest = today.minusDays(PAST_DAYS_KEPT)
    return (old.page?.days.orEmpty() + old.past).distinctBy { it.date } // the page's copy of a day is the newer one
        .filter { it.date in oldest..today && it.date !in listed && it.dishes.isNotEmpty() }
        .sortedBy { it.date }
}

private class EmptyResultException : IOException("page parsed to zero days")

/**
 * Stale-while-revalidate over a JSON cache. The page has no ETag/Last-Modified, so freshness is our
 * own timer ([staleAfterMillis]). Each fetch tries the [hosts] in [HostFallback] order.
 */
class MenuRepository(
    private val fetcher: MenuFetcher,
    private val cache: MenuCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val staleAfterMillis: Long = 30 * 60 * 1000L,
    private val catalog: List<Mensa> = MensaCatalog.all,
    private val hosts: List<String> = MenuHosts.all,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    hostMemory: HostMemory? = null,
) {
    private val fallback = HostFallback(hosts, clock, memory = hostMemory)

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

    /**
     * The state of every Mensa in [ids], in that order (caches loaded if needed), updated whenever one changes;
     * never fetches. For the favorites overview, which only uses what the app already has.
     */
    fun observeCached(ids: List<String>): Flow<List<Pair<String, MenuState>>> = flow {
        ids.forEach { loadCache(it) }
        if (ids.isEmpty()) emit(emptyList())
        else emitAll(combine(ids.map { id -> entry(id).state.map { id to it } }) { it.toList() })
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
            val mensa = catalog.firstOrNull { it.id == id }
            val result = runCatching {
                requireNotNull(mensa) { "unknown Mensa id: $id" }
                val (host, parsed) = fetchFromHosts(mensa, cacheHasDishes = e.state.value.page?.isBlank == false)
                withContext(workDispatcher) {
                    val page = withBorrowedLegend(mensa, parsed)
                    val now = clock()
                    val past = keptPastDays(e.state.value, page, Instant.ofEpochMilli(now).atZone(zone()).toLocalDate())
                    val cached = CachedMenu(fetchedAt = now, page = page, fromFallback = host != hosts.first(), past = past)
                    // A failing cache write must not hide a good fetch from the UI.
                    runCatching { cache.write(id, cached) }
                    cached
                }
            }
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
            val fresh = result.getOrNull()
            e.state.update {
                if (fresh != null) MenuState(fresh.page, fresh.fetchedAt, fromFallback = fresh.fromFallback, past = fresh.past)
                else it.copy(isRefreshing = false, refreshFailed = true)
            }
        } finally {
            // Also covers cancellation, so the spinner never sticks.
            e.state.update { it.copy(isRefreshing = false) }
            e.refreshLock.unlock()
        }
    }

    /**
     * The page from the first host that gives a usable one, with that host. A host is skipped when the fetch fails
     * (no answer within the timeouts, an error status) or its page has no dishes and either lacks the content block
     * (maintenance or PHP error page, often status 200) or would replace cached dishes (more likely a broken page
     * or a changed layout than a real change). A page without dishes but with the block over an empty cache is a
     * closed Mensa and accepted. If no host gives a usable page, a page without dishes is still accepted over an
     * empty cache (nothing to protect); otherwise the refresh fails and the cache stays.
     */
    private suspend fun fetchFromHosts(mensa: Mensa, cacheHasDishes: Boolean): Pair<String, MenuPage> {
        var blank: Pair<String, MenuPage>? = null
        var failure: Exception? = null
        withContext(workDispatcher) { fallback.restore() } // off the main thread: it may read a file
        for (host in fallback.order()) {
            val html = try {
                fetcher.fetch(host + mensa.path)
            } catch (c: CancellationException) {
                throw c
            } catch (ex: Exception) {
                failure = ex
                continue
            }
            val parsed = withContext(workDispatcher) { parse(mensa, html) }
            if (!parsed.isBlank || (!cacheHasDishes && withContext(workDispatcher) { PageCheck.hasContentBlock(html) })) {
                withContext(workDispatcher) { fallback.succeeded(host) }
                return host to parsed
            }
            if (blank == null) blank = host to parsed
        }
        if (!cacheHasDishes) blank?.let { return it }
        throw if (blank != null) EmptyResultException() else checkNotNull(failure)
    }

    private fun parse(mensa: Mensa, html: String): MenuPage = when (mensa.format) {
        PageFormat.DATED -> MenuPageParser.parse(html)
        PageFormat.FIXED -> PierParser.parse(html)
    }

    /** The current state (cache loaded if needed), without fetching: for the background favorites check. */
    suspend fun current(id: String): MenuState = loadCache(id)

    /**
     * Makes sure the app knows what Mensa [id] served on [date], loading the page if it can't tell yet (the usual
     * fallback rules and timeouts). Returns false when it still can't tell: the fetch failed, or the day is past and
     * was never cached. A running refresh is waited for.
     */
    suspend fun loadDay(id: String, date: LocalDate): Boolean {
        val e = entry(id)
        val mensa = catalog.firstOrNull { it.id == id }
        if (loadCache(id).knows(mensa, date)) return true
        e.refreshLock.withLock {} // a fetch already running may bring the day
        if (e.state.value.knows(mensa, date)) return true
        refreshIfStale(id)
        e.refreshLock.withLock {} // refreshIfStale doesn't wait when another fetch started in between
        return e.state.value.knows(mensa, date)
    }

    /** A fixed list holds for every day. Otherwise the day is there, or the page fetched that day doesn't list it: closed. */
    private fun MenuState.knows(mensa: Mensa?, date: LocalDate): Boolean {
        val page = page ?: return false
        if (mensa?.format == PageFormat.FIXED) return true
        if (page.days.any { it.date == date } || past.any { it.date == date }) return true
        return fetchedAt?.let { Instant.ofEpochMilli(it).atZone(zone()).toLocalDate() } == date
    }

    /** Fills the state from disk once; returns the state after that. */
    private suspend fun loadCache(id: String): MenuState {
        val e = entry(id)
        if (e.cacheLoaded) return e.state.value
        e.loadLock.lock()
        try {
            if (!e.cacheLoaded) {
                val cached = withContext(workDispatcher) {
                    cache.read(id)?.let { c -> c.copy(page = withBorrowedLegend(catalog.firstOrNull { it.id == id }, c.page)) }
                }
                // A refresh that already finished is newer than anything on disk.
                if (cached != null) {
                    e.state.update {
                        if (it.fetchedAt == null) {
                            it.copy(page = cached.page, fetchedAt = cached.fetchedAt, fromFallback = cached.fromFallback, past = cached.past)
                        } else {
                            it
                        }
                    }
                }
                e.cacheLoaded = true
            }
        } finally {
            e.loadLock.unlock()
        }
        return e.state.value
    }

    /**
     * Fixed-format pages (PIER 16) have no legend of their own, but use the same codes. Borrow the
     * legend of the first dated Mensa that has one cached; without any, the codes show as they are.
     * Re-done when the cache is loaded, so a legend cached later still reaches the page.
     */
    private suspend fun withBorrowedLegend(mensa: Mensa?, page: MenuPage): MenuPage {
        if (mensa?.format != PageFormat.FIXED || page.legend.codes.isNotEmpty()) return page
        val donor = catalog.filter { it.format == PageFormat.DATED }
            .firstNotNullOfOrNull { cache.read(it.id)?.page?.legend?.takeIf { l -> l.codes.isNotEmpty() } }
        return if (donor != null) page.copy(legend = donor) else page.copy(legend = Legend.EMPTY)
    }
}
