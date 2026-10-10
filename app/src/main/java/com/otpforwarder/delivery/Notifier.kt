package com.otpforwarder.delivery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.otpforwarder.MainActivity

object Notifier {
    private const val EVENTS = "events"
    private const val CODES = "codes"
    private var counter = 100

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel(EVENTS, "Forwarding events", NotificationManager.IMPORTANCE_DEFAULT))
        it.createNotificationChannel(NotificationChannel(CODES, "Received codes", NotificationManager.IMPORTANCE_HIGH))
    }

    /** Owner is told about every forward; this cannot be turned off. */
    fun forwarded(c: Context, sender: String, recipient: String, viaSms: Boolean) {
        val n = Notification.Builder(c, EVENTS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Code forwarded")
            .setContentText("A code from $sender was sent to $recipient" + if (viaSms) " (also by SMS)" else "")
            .build()
        nm(c).notify(counter++, n)
    }

    /** Recipient notification; hidden on the lock screen and removed when the code expires. */
    fun received(c: Context, ownerName: String?, code: String, ttlMs: Long) {
        val id = counter++
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        // Tapping opens the Inbox; the button copies the code without opening the app.
        val open = PendingIntent.getActivity(
            c, id,
            Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_INBOX)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags,
        )
        val copy = PendingIntent.getBroadcast(
            c, id,
            Intent(c, CopyCodeReceiver::class.java).putExtra(CopyCodeReceiver.EXTRA_CODE, code).putExtra(CopyCodeReceiver.EXTRA_ID, id),
            flags,
        )
        val publicVersion = Notification.Builder(c, CODES)
            .setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("New one-time code").setContentIntent(open).build()
        val n = Notification.Builder(c, CODES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Code: $code")
            .setContentText("Shared with you" + (ownerName?.let { " by $it" } ?: ""))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Copy code", copy).build())
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setTimeoutAfter(ttlMs)
            .build()
        nm(c).notify(id, n)
    }
}
