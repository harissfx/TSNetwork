package com.textsocial.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.textsocial.app.MainActivity
import com.textsocial.app.R

/**
 * Builds and shows the system tray notification for an incoming FCM push, and owns the
 * two notification channels the app uses. Shared by [com.textsocial.app.push.FcmService]
 * (real pushes) so the same code path is exercised regardless of where it's called from.
 */
object NotificationHelper {

    const val CHANNEL_GENERAL = "general_activity"
    const val CHANNEL_MESSAGES = "messages"

    // Keys used both when building the PendingIntent extras here and when MainActivity
    // reads them back to decide where to navigate.
    const val EXTRA_NOTIF_TYPE = "notif_type"
    const val EXTRA_NOTIF_POST_ID = "notif_post_id"
    const val EXTRA_NOTIF_COMMENT_ID = "notif_comment_id"
    const val EXTRA_NOTIF_SENDER_ID = "notif_sender_id"
    const val EXTRA_NOTIF_SENDER_USERNAME = "notif_sender_username"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GENERAL,
                context.getString(R.string.notification_channel_general_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_general_desc)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.notification_channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_messages_desc)
            }
        )
    }

    /**
     * Shows a system notification for a push payload. [data] is the FCM `data` map, using
     * the keys the backend Edge Function agrees to send: type, title, body, post_id,
     * comment_id, sender_id, sender_username.
     */
    fun showFromPushData(context: Context, data: Map<String, String>) {
        val type = data["type"] ?: "general"
        val title = data["title"] ?: context.getString(R.string.app_name)
        val body = data["body"].orEmpty()
        val channelId = if (type == "dm") CHANNEL_MESSAGES else CHANNEL_GENERAL

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIF_TYPE, type)
            putExtra(EXTRA_NOTIF_POST_ID, data["post_id"])
            putExtra(EXTRA_NOTIF_COMMENT_ID, data["comment_id"])
            putExtra(EXTRA_NOTIF_SENDER_ID, data["sender_id"])
            putExtra(EXTRA_NOTIF_SENDER_USERNAME, data["sender_username"])
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // unique-enough request code so notifications don't collide
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(if (type == "dm") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        // Group DM notifications by sender, and other activity by type, so repeated pushes
        // of the same kind update in place rather than piling up as separate entries.
        val notifId = (data["sender_id"] ?: type).hashCode()
        manager?.notify(notifId, notification)
    }
}
