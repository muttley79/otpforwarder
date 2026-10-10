package com.otpforwarder.delivery

import android.content.Context
import android.util.Log
import com.otpforwarder.capture.IncomingSms
import com.otpforwarder.crypto.E2E
import com.otpforwarder.data.Cloud
import com.otpforwarder.data.ForwardingPrefs
import com.otpforwarder.data.PolicyStore
import com.otpforwarder.parse.OtpParser
import com.otpforwarder.policy.ForwardingPolicy
import com.otpforwarder.policy.PolicyPairing
import org.json.JSONObject

/** Owner-side pipeline: parse -> policy -> encrypt per recipient -> upload (+ optional SMS relay) -> notify owner. */
object Forwarder {
    suspend fun forward(ctx: Context, sms: IncomingSms) {
        if (!ForwardingPrefs.enabled(ctx) || !Cloud.configured(ctx) || Cloud.uid == null) return
        val code = OtpParser.parse(sms.body) ?: return

        val pairings = Cloud.activePairingsAsOwner()
        val targets = ForwardingPolicy.targets(
            sms.sender, sms.body, PolicyStore.config(ctx), pairings.map { PolicyPairing(it.id, it.active) }, System.currentTimeMillis(),
        )
        val payload = JSONObject().put("code", code).put("sender", sms.sender).put("owner", Cloud.myName)
            .put("ts", sms.timestampMs).toString().toByteArray()

        for (t in targets) {
            val p = pairings.first { it.id == t.id }
            try {
                val ct = E2E.encrypt(p.recipientPublicKey, payload, p.id)
                Cloud.sendMessage(p, ct, sms.sender)
                val phone = PolicyStore.relayPhone(ctx, p.id)
                val viaSms = phone != null &&
                    SmsRelay.send(ctx, p.id, phone, "Code from ${sms.sender}: $code (shared by ${Cloud.myName})")
                Notifier.forwarded(ctx, sms.sender, p.recipientName, viaSms)
            } catch (e: Exception) {
                Log.w("Forwarder", "forward to ${p.id} failed", e)
            }
        }
    }
}
