package com.otpforwarder.data

import android.content.Context
import com.otpforwarder.policy.PolicyConfig
import com.otpforwarder.policy.Rule
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Owner-side forwarding rules. They live only on the owner's device and are never uploaded. */
object PolicyStore {
    private fun sp(c: Context) = c.getSharedPreferences("policy", Context.MODE_PRIVATE)

    // ---- routing rules ----

    fun rules(c: Context): List<Rule> {
        val raw = sp(c).getString("rules", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Rule(
                    id = o.getString("id"),
                    sender = o.optString("sender").ifBlank { null },
                    textContains = o.optString("text").ifBlank { null },
                    pairingIds = o.getJSONArray("to").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(c: Context, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("sender", it.sender ?: "").put("text", it.textContains ?: "")
                    .put("to", JSONArray(it.pairingIds.toList()))
            )
        }
        sp(c).edit().putString("rules", arr.toString()).apply()
    }

    fun addRule(c: Context, sender: String?, text: String?, pairingIds: Set<String>) {
        val s = sender?.trim()?.ifBlank { null }
        val t = text?.trim()?.ifBlank { null }
        if ((s == null && t == null) || pairingIds.isEmpty()) return
        save(c, rules(c) + Rule(UUID.randomUUID().toString(), s, t, pairingIds))
    }

    fun removeRule(c: Context, id: String) = save(c, rules(c).filterNot { it.id == id })

    // ---- time window ----

    fun activeUntil(c: Context) = sp(c).getLong("until", 0L)

    /** Which window the owner picked ("none", "30m", "2h"), so the UI can show it as selected. */
    fun windowPreset(c: Context) = sp(c).getString("preset", "none") ?: "none"
    fun setWindow(c: Context, preset: String, untilMs: Long) =
        sp(c).edit().putString("preset", preset).putLong("until", untilMs).apply()

    // ---- per-recipient pause and SMS relay ----

    fun paused(c: Context): Set<String> = sp(c).getStringSet("paused", emptySet()) ?: emptySet()
    fun setPaused(c: Context, pairingId: String, paused: Boolean) {
        val s = paused(c)
        sp(c).edit().putStringSet("paused", if (paused) s + pairingId else s - pairingId).apply()
    }

    /** Phone number for the opt-in SMS relay of one pairing; null/blank means relay is off. */
    fun relayPhone(c: Context, pairingId: String): String? = sp(c).getString("relay_$pairingId", null)?.ifBlank { null }
    fun setRelayPhone(c: Context, pairingId: String, phone: String?) =
        sp(c).edit().apply { if (phone.isNullOrBlank()) remove("relay_$pairingId") else putString("relay_$pairingId", phone.trim()) }.apply()

    fun config(c: Context) = PolicyConfig(rules(c), activeUntil(c), paused(c))
}
