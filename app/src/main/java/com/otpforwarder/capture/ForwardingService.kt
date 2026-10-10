package com.otpforwarder.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.otpforwarder.MainActivity
import com.otpforwarder.data.ForwardingPrefs

/** Keeps a persistent, non-dismissable notification up for as long as forwarding is active. */
class ForwardingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ForwardingPrefs.setEnabled(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ForwardingPrefs.enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Forwarding active", NotificationManager.IMPORTANCE_LOW)
        )
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), piFlags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ForwardingService::class.java).setAction(ACTION_STOP), piFlags
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("OTP forwarding is ON")
            .setContentText("Incoming one-time codes may be shared with your approved recipients.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop now", stop).build())
            .build()
        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "forwarding"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.otpforwarder.STOP"

        fun start(c: Context) = c.startForegroundService(Intent(c, ForwardingService::class.java))
        fun stop(c: Context) = c.startService(Intent(c, ForwardingService::class.java).setAction(ACTION_STOP))
    }
}
