package de.luwify.mensamd.data

import de.luwify.mensamd.data.cache.CachedMenu
import de.luwify.mensamd.data.cache.MenuCache
import de.luwify.mensamd.data.remote.MenuFetcher
import de.luwify.mensamd.data.remote.MenuHosts
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.parser.MenuPageParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/** Which host a refresh uses: the main site first, a fallback when it fails, a working fallback remembered for an hour. */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuRepositoryFallbackTest {
    private val id = "unicampus-unten"
    private val path = MensaCatalog.byId(id)!!.path
    private val main = "https://main.example/"
    private val backup = "https://backup.example/"

    private fun fixture(dir: String) =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$dir/unicampus-unten.html")).use { it.readBytes().toString(Charsets.UTF_8) }

    private val mainHtml = fixture("2026-09-30")
    private val backupHtml = fixture("2026-10-22-fallback")
    private val mainPage = MenuPageParser.parse(mainHtml)
    private val backupPage = MenuPageParser.parse(backupHtml)

    private val minute = 60_000L
    private var now = 1_000_000_000_000L

    private class FakeCache(var stored: CachedMenu? = null) : MenuCache {
        override suspend fun read(mensaId: String) = stored
        override suspend fun write(mensaId: String, menu: CachedMenu) {
            stored = menu
        }
    }

    /** Answers per host: a String is the page, a Throwable is thrown. Records the URLs asked for. */
    private class HostFetcher(val answers: MutableMap<String, Any>) : MenuFetcher {
        val urls = mutableListOf<String>()
        override suspend fun fetch(url: String): String {
            urls += url
            val answer = answers.entries.first { url.startsWith(it.key) }.value
            if (answer is Throwable) throw answer
            return answer as String
        }
    }

    private fun TestScope.repo(fetcher: MenuFetcher, cache: MenuCache) =
        MenuRepository(fetcher, cache, { now }, UnconfinedTestDispatcher(testScheduler), hosts = listOf(main, backup))

    private fun MenuRepository.state() = kotlinx.coroutines.runBlocking { current(id) }

    @Test
    fun mainWorks_backupIsNotAsked() = runTest {
        val fetcher = HostFetcher(mutableMapOf(main to mainHtml, backup to backupHtml))
        val cache = FakeCache()
        val repo = repo(fetcher, cache)
        repo.refresh(id)

        assertEquals(listOf(main + path), fetcher.urls)
        assertEquals(mainPage, repo.state().page)
        assertFalse(repo.state().fromFallback)
        assertFalse(cache.stored!!.fromFallback)
    }

    @Test
    fun mainTimesOut_backupIsUsed_andMarked() = runTest {
        val fetcher = HostFetcher(mutableMapOf(main to SocketTimeoutException("connect timed out"), backup to backupHtml))
        val cache = FakeCache()
        val repo = repo(fetcher, cache)
        repo.refresh(id)

        assertEquals(listOf(main + path, backup + path), fetcher.urls)
        val state = repo.state()
        assertEquals(backupPage, state.page)
        assertEquals(now, state.fetchedAt)
        assertTrue(state.fromFallback)
        assertFalse(state.refreshFailed)
        assertTrue("the mark survives in the cache", cache.stored!!.fromFallback)
    }

    @Test
    fun mainErrorStatus_backupIsUsed() = runTest {
        val fetcher = HostFetcher(mutableMapOf(main to IOException("HTTP 503 for $main$path"), backup to backupHtml))
        val repo = repo(fetcher, FakeCache())
        repo.refresh(id)

        assertEquals(backupPage, repo.state().page)
        assertTrue(repo.state().fromFallback)
    }

    @Test
    fun mainSendsAnUnreadablePage_backupIsUsed() = runTest {
        // A PHP error or maintenance page with status 200: no content block, no dishes.
        val error = "<html><body><p>There has been a critical error on this website.</p></body></html>"
        val fetcher = HostFetcher(mutableMapOf(main to error, backup to backupHtml))
        val repo = repo(fetcher, FakeCache())
        repo.refresh(id)

        assertEquals(backupPage, repo.state().page)
        assertTrue(repo.state().fromFallback)
    }

    @Test
    fun mainSendsNoDishesOverCachedDishes_backupIsUsed() = runTest {
        val empty = "<html><body><div id=\"content\"><div class=\"entry-content\"><h1>Speiseplan</h1></div></div></body></html>"
        val fetcher = HostFetcher(mutableMapOf(main to empty, backup to backupHtml))
        val repo = repo(fetcher, FakeCache(CachedMenu(now - 40 * minute, mainPage)))
        repo.refresh(id)

        assertEquals(backupPage, repo.state().page)
        assertTrue(repo.state().fromFallback)
    }

    @Test
    fun closedMensaOnTheMainSite_isAccepted_withoutAskingTheBackup() = runTest {
        // A real page (content block present) without dishes and nothing cached: a closed Mensa, as before.
        val empty = "<html><body><div id=\"content\"><div class=\"entry-content\"><h1>Speiseplan</h1></div></div></body></html>"
        val fetcher = HostFetcher(mutableMapOf(main to empty, backup to backupHtml))
        val repo = repo(fetcher, FakeCache())
        repo.refresh(id)

        assertEquals(listOf(main + path), fetcher.urls)
        assertTrue(repo.state().page!!.isBlank)
        assertFalse(repo.state().fromFallback)
    }

    @Test
    fun bothFail_cacheStays_andTheRefreshFails() = runTest {
        val fetcher = HostFetcher(mutableMapOf(main to SocketTimeoutException(), backup to IOException("HTTP 500")))
        val cache = FakeCache(CachedMenu(now - 40 * minute, mainPage))
        val repo = repo(fetcher, cache)
        repo.refresh(id)

        val state = repo.state()
        assertEquals(mainPage, state.page)
        assertEquals(now - 40 * minute, state.fetchedAt)
        assertTrue(state.refreshFailed)
        assertFalse(state.fromFallback)
        assertEquals(now - 40 * minute, cache.stored!!.fetchedAt)
    }

    @Test
    fun cachedPageFromTheBackup_keepsItsMark_whenBothFail() = runTest {
        val fetcher = HostFetcher(mutableMapOf(main to SocketTimeoutException(), backup to SocketTimeoutException()))
        val repo = repo(fetcher, FakeCache(CachedMenu(now - 40 * minute, backupPage, fromFallback = true)))
        repo.refresh(id)

        assertTrue(repo.state().fromFallback)
        assertTrue(repo.state().refreshFailed)
    }

    @Test
    fun workingBackup_isRemembered_soTheNextRefreshDoesNotWaitForTheMainSite() = runTest {
        val fetcher = HostFetcher(mutableMapOf(main to SocketTimeoutException(), backup to backupHtml))
        val repo = repo(fetcher, FakeCache())
        repo.refresh(id)
        fetcher.urls.clear()

        now += 30 * minute
        repo.refresh(id)
        repo.refresh("kellercafe") // shared by all Mensen
        assertEquals(listOf(backup + path, backup + MensaCatalog.byId("kellercafe")!!.path), fetcher.urls)
    }

    @Test
    fun rememberedBackupFails_theMainSiteIsTried() = runTest {
        val fetcher = HostFetcher(mutableMapOf(main to SocketTimeoutException(), backup to backupHtml))
        val repo = repo(fetcher, FakeCache())
        repo.refresh(id)
        fetcher.answers[backup] = IOException("HTTP 502")
        fetcher.answers[main] = mainHtml
        fetcher.urls.clear()

        repo.refresh(id)
        assertEquals(listOf(backup + path, main + path), fetcher.urls)
        assertEquals(mainPage, repo.state().page)
        assertFalse(repo.state().fromFallback)
    }

    @Test
    fun afterAnHour_theMainSiteIsTriedAgain_andWinsWhenItWorks() = runTest {
        val fetcher = HostFetcher(mutableMapOf(main to SocketTimeoutException(), backup to backupHtml))
        val repo = repo(fetcher, FakeCache())
        repo.refresh(id)
        fetcher.answers[main] = mainHtml
        fetcher.urls.clear()

        now += 60 * minute
        repo.refresh(id)
        assertEquals(listOf(main + path), fetcher.urls)
        assertFalse(repo.state().fromFallback)

        // And it stays first: the backup was forgotten.
        fetcher.urls.clear()
        repo.refresh(id)
        assertEquals(listOf(main + path), fetcher.urls)
    }

    @Test
    fun realHostList_mainSiteFirst_everyHostHttpsWithTrailingSlash() {
        assertEquals("https://www.studentenwerk-magdeburg.de/", MenuHosts.all.first())
        assertTrue(MenuHosts.all.all { it.startsWith("https://") && it.endsWith("/") })
        assertEquals(MenuHosts.all.size, MenuHosts.all.toSet().size)
        assertTrue(MensaCatalog.all.all { !it.path.startsWith("/") && it.path.endsWith("/") })
    }
}
