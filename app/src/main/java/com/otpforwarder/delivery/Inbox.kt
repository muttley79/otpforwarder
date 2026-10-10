package com.otpforwarder.delivery

import android.content.Context
import android.util.Log
import com.otpforwarder.crypto.E2E
import com.otpforwarder.data.Cloud
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class InboxItem(val id: String, val code: String, val sender: String, val owner: String?, val receivedMs: Long)

/** Recipient-side codes. Memory only, and they expire after [TTL_MS]. */
object Inbox {
    const val TTL_MS = 5 * 60_000L
    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items

    fun add(item: InboxItem) {
        if (_items.value.any { it.id == item.id }) return
        _items.value = (listOf(item) + _items.value.filter { System.currentTimeMillis() - it.receivedMs < TTL_MS })
    }

    fun prune(nowMs: Long) {
        val kept = _items.value.filter { nowMs - it.receivedMs < TTL_MS }
        if (kept.size != _items.value.size) _items.value = kept
    }
}

object RecipientSync {
    // The live listener, a push and "Check now" can all see the same message; each one is handled once.
    private val seen = LinkedHashSet<String>()

    /** Decrypts one message, shows it, and deletes it from the server. Safe to call more than once for the same message. */
    suspend fun handle(ctx: Context, m: Cloud.Incoming) {
        synchronized(seen) {
            if (!seen.add(m.docId)) return
            if (seen.size > 200) seen.remove(seen.first())
        }
        try {
            val j = JSONObject(String(E2E.decrypt(ctx, m.ciphertext, m.pairingId)))
            val code = j.getString("code")
            val sender = j.optString("sender")
            val owner = j.optString("owner").ifBlank { null }
            Inbox.add(InboxItem(m.docId, code, sender, owner, System.currentTimeMillis()))
            Notifier.received(ctx, owner, code, Inbox.TTL_MS)
        } catch (e: Exception) {
            Log.w("RecipientSync", "could not decrypt ${m.docId}", e)
        } finally {
            runCatching { Cloud.deleteMessage(m.docId) }
        }
    }

    /** Fetches every pending message addressed to this user. Idempotent. */
    suspend fun pull(ctx: Context) {
        if (!Cloud.configured(ctx) || Cloud.uid == null) return
        for (m in Cloud.pendingMessages()) handle(ctx, m)
    }
}
