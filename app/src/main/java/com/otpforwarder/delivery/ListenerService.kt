package com.otpforwarder.delivery

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
import com.otpforwarder.data.Cloud
import com.otpforwarder.data.ForwardingPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Keeps a live connection to Firestore so new codes show up without tapping "Check now", with no server-side function.
 * It costs some battery, so it is opt-in and always shows a notification while it runs.
 */
class ListenerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ForwardingPrefs.setListening(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ForwardingPrefs.listening(this) || !Cloud.configured(this) || Cloud.uid == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        // Android only lets a channel's importance be set when it is created, so the quiet one has a new id.
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Listening for codes (quiet)", NotificationManager.IMPORTANCE_MIN).apply {
                description = "A silent reminder that the app is listening for shared codes. You can turn this off; the app keeps listening."
                setShowBadge(false)
            }
        )
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), piFlags)
        val stop = PendingIntent.getService(this, 3, Intent(this, ListenerService::class.java).setAction(ACTION_STOP), piFlags)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Listening for shared codes")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        job?.cancel()
        job = scope.launch {
            Cloud.incomingFlow().catch { }.collect { RecipientSync.handle(applicationContext, it) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "listening_quiet"
        private const val OLD_CHANNEL = "listening"
        private const val NOTIF_ID = 2
        const val ACTION_STOP = "com.otpforwarder.STOP_LISTENING"

        fun start(c: Context) = c.startForegroundService(Intent(c, ListenerService::class.java))
        fun stop(c: Context) = c.startService(Intent(c, ListenerService::class.java).setAction(ACTION_STOP))
    }
}
