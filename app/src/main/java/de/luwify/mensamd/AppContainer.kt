package de.luwify.mensamd

import android.content.Context
import de.luwify.mensamd.data.MenuRepository
import de.luwify.mensamd.data.cache.FileHostMemory
import de.luwify.mensamd.data.cache.FileMenuCache
import de.luwify.mensamd.data.remote.HttpMenuFetcher
import de.luwify.mensamd.data.settings.SettingsRepository
import de.luwify.mensamd.data.settings.settingsRepository
import de.luwify.mensamd.data.update.CompanionLauncher
import de.luwify.mensamd.data.update.Updates
import de.luwify.mensamd.data.update.buildUpdateSource
import de.luwify.mensamd.data.update.updateSettings
import de.luwify.mensamd.notify.FavoriteNotifier
import de.luwify.mensamd.notify.FavoriteScheduler
import de.luwify.mensamd.notify.CardReminderScheduler
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.resolveOrder
import de.luwify.mensamd.stats.DayMenu
import de.luwify.mensamd.stats.MealContext
import de.luwify.mensamd.stats.MenuDishes
import de.luwify.mensamd.stats.StatsRecorder
import de.luwify.mensamd.stats.StatsStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.io.File
import de.luwify.mensamd.backup.BackupRepository
import java.time.LocalDate
import java.time.LocalTime

/** Manual DI: one instance per process, owned by [MensaApp]. Everything heavy is lazy. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings: SettingsRepository by lazy { settingsRepository(app) }

    /** The in-app updater (GitHub releases of this app), shared by every screen; see `data/update`. */
    val updates: Updates by lazy { Updates(app, updateSettings(app), buildUpdateSource(app)) }

    /** Stundenplan MD for the settings banner: installed or not, opening it, its release page. */
    val companion: CompanionLauncher by lazy { CompanionLauncher(app) }

    val menuRepository: MenuRepository by lazy {
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"
        // cacheDir: it is a cache, and it stays out of backups.
        val dir = File(app.cacheDir, "menus")
        MenuRepository(HttpMenuFetcher(version), FileMenuCache(dir), hostMemory = FileHostMemory(File(dir, "fallback-host.txt")))
    }

    /** Work that must outlive a screen (a scan from the popup is stored after the popup closed). */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            // A failed save (disk full, I/O error) must not crash the app. Only the exception type, never data.
            android.util.Log.w("MensaApp", "background task failed: ${e.javaClass.simpleName}")
        },
    )

    /** The card statistics (files dir: kept like settings, not a cache; excluded from cloud backups). */
    val statsStore: StatsStore by lazy { StatsStore(File(File(app.filesDir, "stats"), "stats.json")) }

    val statsRecorder: StatsRecorder by lazy {
        StatsRecorder(
            store = statsStore,
            settings = settings.cardStats,
            mealContext = { MealContext(settings.defaultMensaId.first(), settings.priceGroup.first()) },
            cachedMenu = ::cachedMenu,
            loadMenu = ::menuSnapshot,
            scope = appScope,
        )
    }

    /** Every Mensa id, default Mensa first, then tab order. */
    private suspend fun orderedMensaIds(): List<String> =
        (listOfNotNull(settings.defaultMensaId.first()) + resolveOrder(settings.mensaOrder.first()).map { it.id }).distinct()

    /** The menu page of every Mensa the app has one of, with its kept past days, default Mensa first; never fetches. */
    suspend fun cachedPages(): List<Pair<String, MenuPage>> =
        orderedMensaIds().mapNotNull { id -> menuRepository.current(id).pageWithPast?.let { id to it } }

    /** Every dish on [date] the app has now, without fetching; Mensen whose day isn't known yet are named as missing. */
    private suspend fun cachedMenu(date: LocalDate): DayMenu {
        val missing = orderedMensaIds().filterNot { menuRepository.knowsDay(it, date) }
        return DayMenu(MenuDishes.onDay(cachedPages(), date), missing)
    }

    /**
     * Every dish on [date] of every Mensa. Mensen the app can't answer for yet are loaded first, in parallel (usual
     * fallback and timeouts); those that still fail are named, so nothing is saved on a half-known menu.
     */
    private suspend fun menuSnapshot(date: LocalDate): DayMenu {
        val ids = orderedMensaIds()
        val missing = coroutineScope {
            ids.map { id -> async { id.takeUnless { menuRepository.loadDay(id, date) } } }.awaitAll()
        }.filterNotNull()
        return DayMenu(MenuDishes.onDay(cachedPages(), date), missing)
    }

    private val versionName: String? by lazy { runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() }

    /** Backup export/import (settings page). */
    val backups: BackupRepository by lazy {
        BackupRepository(
            settings, statsStore,
            language = { AppLanguage.current(app).backupKey },
            setLanguage = { key ->
                val wanted = AppLanguage.fromBackupKey(key)
                if (wanted != null && wanted != AppLanguage.current(app)) AppLanguage.set(app, wanted)
            },
            appVersion = versionName,
        )
    }

    /** Reads a whole document (a backup file the user picked); null when it can't be read or is over 20 MB. */
    fun readDocument(uri: android.net.Uri): String? = runCatching {
        app.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytesCompat(20 * 1024 * 1024 + 1)
            if (bytes.size > 20 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8)
        }
    }.getOrNull()

    /** Writes [text] to a document the user created; false on failure. */
    fun writeDocument(uri: android.net.Uri, text: String): Boolean = runCatching {
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    }.getOrDefault(false)

    /** The daily favorites job runs only while notifications are on and there is at least one favorite. */
    fun syncFavoriteSchedule(active: Boolean, runAt: LocalTime, timeChanged: Boolean) {
        when {
            !active -> FavoriteScheduler.cancel(app)
            timeChanged -> FavoriteScheduler.reschedule(app, runAt)
            else -> FavoriteScheduler.ensureScheduled(app, runAt)
        }
    }

    /** The daily card reminder (card settings), like [syncFavoriteSchedule]. */
    fun syncCardReminder(active: Boolean, runAt: LocalTime, timeChanged: Boolean) {
        when {
            !active -> CardReminderScheduler.cancel(app)
            timeChanged -> CardReminderScheduler.reschedule(app, runAt)
            else -> CardReminderScheduler.ensureScheduled(app, runAt)
        }
    }

    fun canPostNotifications(): Boolean = FavoriteNotifier.canPost(app)

    fun postTestNotification(mensaId: String, today: Boolean): Boolean = FavoriteNotifier.postTest(app, mensaId, today)
}

/** Reads at most [limit] bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
