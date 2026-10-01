package de.luwify.mensamd.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.luwify.mensamd.MensaApp
import de.luwify.mensamd.favorites.FavoriteNotifications
import de.luwify.mensamd.model.PageFormat
import de.luwify.mensamd.model.resolveOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Refreshes the watched Mensen through the normal repository and cache, then announces favorites on tomorrow's
 * menu (or today's, for a morning run time). Always schedules the next run, whatever happens here. Never logs favorites or dish names.
 */
class FavoriteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = (applicationContext as MensaApp).container.settings
        val runAt = runCatching { settings.notifyTime.first() }.getOrDefault(FavoriteNotifications.DEFAULT_RUN_AT)
        try {
            check(ZonedDateTime.now(), runAt)
        } catch (e: CancellationException) {
            throw e // stopped by the system: WorkManager runs this job again later, no rescheduling here
        } catch (_: Exception) {
            // A failed run is skipped; the next one is still scheduled below.
        }
        FavoriteScheduler.scheduleNext(applicationContext, runAt)
        return Result.success()
    }

    private suspend fun check(now: ZonedDateTime, runAt: LocalTime) {
        val date = FavoriteNotifications.targetDate(now, runAt) ?: return
        val container = (applicationContext as MensaApp).container
        val settings = container.settings
        if (!settings.notificationsEnabled.first()) return
        val favorites = settings.favorites.first()
        if (favorites.isEmpty()) return
        val watched = settings.notifyMensen.first()
        val mensas = resolveOrder(settings.mensaOrder.first()).filter { it.id in watched && it.format == PageFormat.DATED }

        val menus = mensas.map { mensa ->
            container.menuRepository.refreshIfStale(mensa.id) // a failed fetch keeps the cache, which is still used
            mensa.id to container.menuRepository.current(mensa.id).page
        }
        val hits = FavoriteNotifications.select(menus, favorites, date, settings.notifiedFavorites())
        val plan = FavoriteNotifications.plan(hits, today = date == now.toLocalDate())
        // Only what was really shown counts as notified, so granting the permission later still brings it.
        val shown = plan != null && FavoriteNotifier.post(applicationContext, plan)
        settings.recordNotified(if (shown) hits.mapTo(HashSet()) { it.notifiedKey } else emptySet(), now.toLocalDate(), FavoriteNotifications::prune)
    }
}
