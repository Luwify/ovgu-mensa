package de.luwify.mensamd.scraper

import de.luwify.mensamd.data.MenuRepository
import de.luwify.mensamd.data.cache.CachedMenu
import de.luwify.mensamd.data.cache.MenuCache
import de.luwify.mensamd.data.remote.MenuFetcher
import de.luwify.mensamd.data.remote.MenuHosts
import de.luwify.mensamd.model.Mensa
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.parser.PageCheck
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate

/** What happened to one Mensa in a run. [fetched] is false when every host failed. */
data class MensaResult(
    val mensa: Mensa,
    val fetched: Boolean,
    val source: String? = null,
    val days: Int = 0,
    val written: Int = 0,
    val unchanged: Int = 0,
    /** Days the rules in [Scraper.keep] didn't let this run write. */
    val kept: Int = 0,
) {
    val hasDishes: Boolean get() = days > 0
}

/**
 * Fetches every Mensa one after the other and writes its days below [outDir]. Host choice, fallback and page checks
 * are the app's ([MenuRepository]); a new repository per Mensa means every page asks the main site first, so one
 * timeout doesn't send the rest of the run to the test server. A Mensa that fails is tried once more at the end and
 * never stops the others.
 */
class Scraper(
    private val fetcher: MenuFetcher,
    private val outDir: File,
    private val today: LocalDate,
    private val catalog: List<Mensa> = MensaCatalog.all,
    private val hosts: List<String> = MenuHosts.all,
    private val pauseMillis: Long = 1_000,
    private val retryAfterMillis: Long = 30_000,
    private val log: (String) -> Unit = {},
) {
    suspend fun run(): List<MensaResult> {
        val results = catalog.mapIndexed { i, mensa ->
            if (i > 0) delay(pauseMillis)
            scrape(mensa)
        }.toMutableList()
        val failed = results.indices.filter { !results[it].fetched }
        if (failed.isNotEmpty()) delay(retryAfterMillis)
        failed.forEach { i -> results[i] = scrape(results[i].mensa) }
        return results
    }

    private suspend fun scrape(mensa: Mensa): MensaResult {
        val repository = MenuRepository(
            fetcher = { url ->
                try {
                    fetcher.fetch(url).also { html ->
                        // Maintenance and PHP error pages, often sent with status 200: the server failed, not the Mensa.
                        if (!PageCheck.hasContentBlock(html)) throw IOException("no content block in $url")
                    }
                } catch (e: IOException) {
                    log("${mensa.id}: $url: ${e.message ?: e.javaClass.simpleName}")
                    throw e
                }
            },
            cache = NoCache,
            catalog = listOf(mensa),
            hosts = hosts,
        )
        repository.refresh(mensa.id)
        val state = repository.current(mensa.id)
        val page = state.page ?: return MensaResult(mensa, fetched = false)
        val source = if (state.fromFallback) DayFile.SOURCE_FALLBACK else DayFile.SOURCE_MAIN
        return write(mensa, source, filesOf(mensa, page, source))
    }

    private fun filesOf(mensa: Mensa, page: MenuPage, source: String): List<Pair<LocalDate, DayFile>> {
        fun file(date: LocalDate, fixed: Boolean, dishes: List<DishEntry>) =
            date to DayFile(date = date.toString(), mensa = mensa.toRef(), fixed = fixed, source = source, dishes = dishes)

        val days = page.days.filter { it.dishes.isNotEmpty() }
            .map { day -> file(day.date, fixed = false, day.dishes.map { it.toEntry() }) }
        val fixed = page.fixed.flatMap { section -> section.dishes.map { it.toEntry(section.title) } }
        // A fixed list has no dates: it is recorded as what was on offer today, like the app on working days only.
        val snapshot = if (fixed.isEmpty() || today.dayOfWeek in WEEKEND) emptyList() else listOf(file(today, true, fixed))
        return days + snapshot
    }

    private fun write(mensa: Mensa, source: String, files: List<Pair<LocalDate, DayFile>>): MensaResult {
        var written = 0
        var unchanged = 0
        var kept = 0
        for ((date, day) in files) {
            val target = File(outDir, dayPath(date, mensa.id))
            val text = json.encodeToString(day) + "\n"
            when {
                target.isFile && target.readText() == text -> unchanged++
                !keep(source, date, target) -> {
                    target.parentFile.mkdirs()
                    target.writeText(text)
                    written++
                }
                else -> kept++
            }
        }
        return MensaResult(mensa, fetched = true, source = source, days = files.size, written = written, unchanged = unchanged, kept = kept)
    }

    /**
     * The test server can lag behind the main site, so it adds nothing to the past and never replaces what the main
     * site said. Everything from the main site is written: the latest plan for a day is the best one.
     */
    private fun keep(source: String, date: LocalDate, target: File): Boolean {
        if (source != DayFile.SOURCE_FALLBACK) return false
        return date < today || storedSource(target) == DayFile.SOURCE_MAIN
    }

    private fun storedSource(target: File): String? = if (!target.isFile) null else runCatching {
        reader.decodeFromString<DayFile>(target.readText()).source
    }.getOrNull()

    /** Every run starts empty: the data directory, not a cache, is what the run compares with. */
    private object NoCache : MenuCache {
        override suspend fun read(mensaId: String): CachedMenu? = null
        override suspend fun write(mensaId: String, menu: CachedMenu) = Unit
    }

    private companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
        }
        val reader = Json { ignoreUnknownKeys = true }
    }
}

/**
 * 1 when no Mensa could be fetched from any host (network or server errors), else 0. Pages that load without dishes are
 * a normal result: closed Mensen and holidays simply write nothing.
 */
fun exitCode(results: List<MensaResult>): Int = if (results.any { it.fetched }) 0 else 1
