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
import java.io.File
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
