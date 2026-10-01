package de.luwify.mensamd.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.luwify.mensamd.favorites.FavoriteNotifications
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The daily favorites check: one unique one-time job for the next 18:00, which schedules its successor when it ends.
 * One-time instead of periodic work so the time doesn't drift (a periodic job's next run counts from when the last
 * one actually ran). Needs a network connection; no exact alarm, no wake lock: Android may run it later in Doze.
 */
object FavoriteScheduler {
    private const val WORK_NAME = "favorite-notifications"

    /** Called on every app start while enabled and favorites exist: keeps a pending job, creates one if missing. */
    fun ensureScheduled(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

    /**
     * From the job itself, as its last action: replaces the (running) job with the next one. Replacing cancels
     * the running job, which is harmless at that point and leaves exactly one pending job.
     */
    fun scheduleNext(context: Context) = enqueue(context, ExistingWorkPolicy.REPLACE)

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        val delay = FavoriteNotifications.delayUntilNextRun(ZonedDateTime.now())
        val request = OneTimeWorkRequestBuilder<FavoriteWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }
}
