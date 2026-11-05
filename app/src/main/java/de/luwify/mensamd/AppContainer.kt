package de.luwify.mensamd

import android.content.Context
import de.luwify.mensamd.data.MenuRepository
import de.luwify.mensamd.data.cache.FileMenuCache
import de.luwify.mensamd.data.remote.HttpMenuFetcher
import de.luwify.mensamd.data.settings.SettingsRepository
import de.luwify.mensamd.data.settings.settingsRepository
import de.luwify.mensamd.data.update.CompanionInstaller
import de.luwify.mensamd.data.update.Updates
import de.luwify.mensamd.data.update.buildUpdateSource
import de.luwify.mensamd.data.update.updateSettings
import de.luwify.mensamd.notify.FavoriteNotifier
import de.luwify.mensamd.notify.FavoriteScheduler
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.PageFormat
import de.luwify.mensamd.model.resolveOrder
import de.luwify.mensamd.stats.MealContext
import de.luwify.mensamd.stats.SnapDish
import de.luwify.mensamd.stats.StatsRecorder
import de.luwify.mensamd.stats.StatsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Manual DI: one instance per process, owned by [MensaApp]. Everything heavy is lazy. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings: SettingsRepository by lazy { settingsRepository(app) }

    /** The in-app updater (GitHub releases of this app), shared by every screen; see `data/update`. */
    val updates: Updates by lazy { Updates(app, updateSettings(app), buildUpdateSource(app)) }

    /** Stundenplan MD for the banner at the bottom of the settings: installed or not, and its checked install. */
    val companion: CompanionInstaller by lazy { CompanionInstaller(app, updateSettings(app)) }

    val menuRepository: MenuRepository by lazy {
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"
        // cacheDir: it is a cache, and it stays out of backups.
        MenuRepository(HttpMenuFetcher(version), FileMenuCache(File(app.cacheDir, "menus")))
    }

    /** Work that must outlive a screen (a scan from the popup is stored after the popup closed). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The card statistics (files dir: kept like settings, not a cache; excluded from cloud backups). */
    val statsStore: StatsStore by lazy { StatsStore(File(File(app.filesDir, "stats"), "stats.json")) }

    val statsRecorder: StatsRecorder by lazy {
        StatsRecorder(
            store = statsStore,
            settings = settings.cardStats,
            mealContext = { MealContext(settings.defaultMensaId.first(), settings.priceGroup.first()) },
            snapshot = ::menuSnapshot,
            scope = appScope,
        )
    }

    /**
     * Every dish on [date] of every Mensa, default Mensa first, then tab order; PIER 16's fixed list counts for every
     * day. For today, stale menus are refreshed first (at most 8 s in all; a failure uses the cache).
     */
    private suspend fun menuSnapshot(date: LocalDate): List<SnapDish> {
        val default = settings.defaultMensaId.first()
        val order = resolveOrder(settings.mensaOrder.first())
        val ids = (listOfNotNull(default) + order.map { it.id }).distinct()
        if (date == LocalDate.now()) {
            withTimeoutOrNull(8_000) {
                coroutineScope { ids.map { id -> async { runCatching { menuRepository.refreshIfStale(id) } } }.awaitAll() }
            }
        }
        return ids.flatMap { id ->
            val page = menuRepository.current(id).page ?: return@flatMap emptyList()
            val dishes = when (MensaCatalog.byId(id)?.format) {
                PageFormat.FIXED -> page.fixed.flatMap { it.dishes }
                else -> page.days.firstOrNull { it.date == date }?.dishes.orEmpty()
            }
            dishes.filter { it.prices.isNotEmpty() }.map { SnapDish(id, it.name, it.prices, it.priceGroupsKnown, it.tags) }
        }
    }

    /** The daily favorites job runs only while notifications are on and there is at least one favorite. */
    fun syncFavoriteSchedule(active: Boolean, runAt: LocalTime, timeChanged: Boolean) {
        when {
            !active -> FavoriteScheduler.cancel(app)
            timeChanged -> FavoriteScheduler.reschedule(app, runAt)
            else -> FavoriteScheduler.ensureScheduled(app, runAt)
        }
    }

    fun canPostNotifications(): Boolean = FavoriteNotifier.canPost(app)

    fun postTestNotification(mensaId: String, today: Boolean): Boolean = FavoriteNotifier.postTest(app, mensaId, today)
}
