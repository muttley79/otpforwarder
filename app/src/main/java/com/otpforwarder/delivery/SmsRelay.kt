package com.otpforwarder.delivery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.otpforwarder.policy.RateLimiter

/** Opt-in secondary channel. Plain SMS is weaker than the encrypted path, so it is capped and per-recipient. */
object SmsRelay {
    private val limiter = RateLimiter(max = 5, windowMs = 60 * 60_000L)

    fun canSend(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun send(c: Context, pairingId: String, phone: String, text: String): Boolean {
        if (!canSend(c) || !limiter.tryAcquire(pairingId, System.currentTimeMillis())) return false
        val sms = c.getSystemService(SmsManager::class.java)
        sms.sendTextMessage(phone, null, text, null, null)
        return true
    }
}
