package com.otpforwarder.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.otpforwarder.data.ForwardingPrefs

/** After a reboot or app update, bring the visible "forwarding is ON" notification back if forwarding was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (ForwardingPrefs.enabled(context)) ForwardingService.start(context)
        }
    }
}
