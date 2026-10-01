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
import de.luwify.mensamd.MainActivity
import de.luwify.mensamd.R
import de.luwify.mensamd.favorites.NotificationPlan
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.ui.forLanguage

/** Posts the favorites notification. One notification id: a new run replaces the previous one. */
object FavoriteNotifier {
    private const val CHANNEL_ID = "favorites"
    private const val NOTIFICATION_ID = 1

    /** Intent extra for [MainActivity]: the Mensa whose tab to open. */
    const val EXTRA_MENSA_ID = "de.luwify.mensamd.extra.MENSA_ID"

    /** The app may post: the user didn't block it, and on Android 13+ the runtime permission is granted. */
    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Returns false when it couldn't be shown (permission missing or notifications blocked). */
    fun post(context: Context, plan: NotificationPlan): Boolean {
        val language = context.resources.configuration.locales[0].language
        val lines = plan.hits.map { hit ->
            val mensa = MensaCatalog.byId(hit.mensaId)?.name?.forLanguage(language) ?: hit.mensaId
            context.getString(R.string.notify_line, hit.dish.name.forLanguage(language), mensa)
        }
        val builder = baseBuilder(context, plan.openMensaId)
        if (plan.isSingle) {
            builder.setContentTitle(context.getString(R.string.notify_title_single))
                .setContentText(lines.single())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.single()))
        } else {
            val title = context.resources.getQuantityString(R.plurals.notify_title_many, lines.size, lines.size)
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
            lines.forEach(style::addLine)
            builder.setContentTitle(title)
                .setContentText(lines.joinToString(", "))
                .setNumber(lines.size)
                .setStyle(style)
        }
        return show(context, builder)
    }

    /** The settings button: a sample in the real format, opening [mensaId]. */
    fun postTest(context: Context, mensaId: String): Boolean {
        val language = context.resources.configuration.locales[0].language
        val mensa = MensaCatalog.byId(mensaId)?.name?.forLanguage(language) ?: mensaId
        val text = context.getString(R.string.notify_line, context.getString(R.string.notify_test_dish), mensa)
        val builder = baseBuilder(context, mensaId)
            .setContentTitle(context.getString(R.string.notify_title_single))
            .setContentText(text)
        return show(context, builder)
    }

    private fun baseBuilder(context: Context, mensaId: String): NotificationCompat.Builder {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_MENSA_ID, mensaId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_favorite_filled)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun show(context: Context, builder: NotificationCompat.Builder): Boolean {
        if (!canPost(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false // already covered by canPost; spelled out for lint
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        return true
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notify_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
