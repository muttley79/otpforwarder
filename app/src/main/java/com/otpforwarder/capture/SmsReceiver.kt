package com.otpforwarder.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.otpforwarder.data.ForwardingPrefs
import com.otpforwarder.delivery.Forwarder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SmsReceiver : BroadcastReceiver(), OtpSource {
    override val name = "sms-receiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!ForwardingPrefs.enabled(context)) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multipart messages arrive as several PDUs from the same sender; merge them.
        // Segments delivered as separate broadcasts are re-joined by SmsAssembler.
        val messages = parts.groupBy { it.originatingAddress.orEmpty() }.map { (sender, pdus) ->
            SmsAssembler.shared.add(
                IncomingSms(sender, pdus.joinToString("") { it.messageBody.orEmpty() }, pdus.first().timestampMillis)
            )
        }
        messages.forEach { CapturedOtps.onSms(it) }

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000) { messages.forEach { Forwarder.forward(app, it) } }
            } finally {
                pending.finish()
            }
        }
    }
}
