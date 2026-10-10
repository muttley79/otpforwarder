package com.otpforwarder.delivery

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.widget.Toast

/** The "Copy code" button on a received-code notification. */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        val clip = ClipData.newPlainText("code", code).apply {
            // Ask the system not to show the code in clipboard previews (Android 13+).
            description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
        context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_ID, -1))
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_ID = "notification_id"
    }
}
