package de.luwify.mensamd.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.luwify.mensamd.MainActivity
import de.luwify.mensamd.MensaApp
import de.luwify.mensamd.R
import de.luwify.mensamd.data.settings.CardStatsSettings
import de.luwify.mensamd.stats.CardReminderRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The daily "scan your card" reminder: one unique one-time job for the next run time, which schedules its successor
 * as its last action (like the favorites job, so the time doesn't drift). No exact alarm: Doze may delay it, and a run
 * that comes too late reminds of nothing (see [CardReminderRules]). No network needed (it tries a stale menu refresh).
 */
object CardReminderScheduler {
    private const val WORK_NAME = "card-reminder"

    fun ensureScheduled(context: Context, runAt: LocalTime) = enqueue(context, runAt, ExistingWorkPolicy.KEEP)

    fun reschedule(context: Context, runAt: LocalTime) = enqueue(context, runAt, ExistingWorkPolicy.REPLACE)

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(context: Context, runAt: LocalTime, policy: ExistingWorkPolicy) {
        val delay = CardReminderRules.delayUntilNextRun(ZonedDateTime.now(), runAt)
        val request = OneTimeWorkRequestBuilder<CardReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }
}

class CardReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MensaApp).container
        val settings = runCatching { container.settings.cardStats.first() }.getOrNull()
        try {
            if (settings != null && settings.reminderActive) check(settings.reminderTime)
        } catch (e: CancellationException) {
            throw e // stopped by the system: WorkManager runs it again later
        } catch (_: Exception) {
            // a failed run is skipped; the next one is still scheduled
        }
        when {
            settings == null -> CardReminderScheduler.reschedule(applicationContext, CardStatsSettings.DEFAULT_REMINDER_TIME) // retried; that run reads the settings again
            settings.reminderActive -> CardReminderScheduler.reschedule(applicationContext, settings.reminderTime)
        }
        return Result.success()
    }

    private suspend fun check(runAt: LocalTime) {
        val container = (applicationContext as MensaApp).container
        val now = LocalDateTime.now()
        val data = container.statsStore.load()
        val defaultId = container.settings.defaultMensaId.first()
        val menuToday = defaultId?.let { id ->
            withTimeoutOrNull(15_000) { runCatching { container.menuRepository.refreshIfStale(id) } }
            val state = container.menuRepository.current(id)
            val fetchedOn = state.fetchedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            CardReminderRules.menuToday(state.page, fetchedOn, now.toLocalDate())
        }
        if (CardReminderRules.shouldRemind(now, runAt, data, menuToday)) CardReminderNotifier.post(applicationContext)
    }
}

/** The reminder notification; a tap opens the card screen. */
object CardReminderNotifier {
    private const val CHANNEL_ID = "card_reminder"
    private const val NOTIFICATION_ID = 2

    /** Intent extra for [MainActivity]: open the card screen. */
    const val EXTRA_OPEN_CARD = "de.luwify.mensamd.extra.OPEN_CARD"

    fun post(context: Context): Boolean {
        if (!FavoriteNotifier.canPost(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.card_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.card_reminder_channel_description) },
        )
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_CARD, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_card_notification)
            .setContentTitle(context.getString(R.string.card_reminder_notification_title))
            .setContentText(context.getString(R.string.card_reminder_notification_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }
}
