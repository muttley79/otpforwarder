package com.otpforwarder.data

import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.otpforwarder.crypto.E2E
import java.security.SecureRandom
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class Pairing(
    val id: String,
    val ownerUid: String,
    val ownerName: String,
    val recipientUid: String,
    val recipientName: String,
    val active: Boolean,
    val recipientPublicKey: String,
)

data class InvitePreview(val id: String, val ownerUid: String, val ownerName: String)

data class AuditItem(val pairingId: String, val sender: String, val ownerName: String, val recipientName: String, val timeMs: Long)

/** Thin wrapper over Firebase Auth + Firestore. Everything sensitive in here is already ciphertext. */
object Cloud {
    const val INVITE_TTL_MS = 10 * 60_000L

    fun configured(c: Context) = FirebaseApp.getApps(c).isNotEmpty()
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    val uid: String? get() = auth.currentUser?.uid
    /** Name the user chose; stored on the Firebase profile. There is no email or password anywhere. */
    val myName: String get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "user"
    /** Synchronous read, so the first frame already knows whether someone is signed in. */
    val currentUser: FirebaseUser? get() = auth.currentUser
    fun hasName() = !auth.currentUser?.displayName.isNullOrBlank()

    fun userFlow(): Flow<FirebaseUser?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    /** Creates an anonymous identity if needed and sets the display name shown to the people you pair with. */
    suspend fun start(name: String) {
        if (auth.currentUser == null) auth.signInAnonymously().await()
        auth.currentUser!!.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
    }

    /** Publishes this device's public key and push token. Safe to call repeatedly. */
    suspend fun registerDevice(ctx: Context) {
        val u = uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        db.collection("otp_users").document(u).set(
            mapOf("publicKey" to E2E.publicKey(ctx), "fcmToken" to token, "displayName" to myName),
            SetOptions.merge(),
        ).await()
    }

    suspend fun saveToken(token: String) {
        val u = uid ?: return
        db.collection("otp_users").document(u).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
    }

    // ---- invites & pairing ----

    suspend fun createInvite(): String {
        val u = uid ?: error("not signed in")
        val raw = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val id = Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        db.collection("otp_invites").document(id).set(
            mapOf(
                "ownerUid" to u,
                "ownerName" to myName,
                "redeemedBy" to null,
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + INVITE_TTL_MS)),
            )
        ).await()
        return id
    }

    suspend fun previewInvite(id: String): InvitePreview? {
        val d = db.collection("otp_invites").document(id).get().await()
        if (!d.exists() || d.get("redeemedBy") != null) return null
        val exp = d.getTimestamp("expiresAt")?.toDate()?.time ?: return null
        if (exp < System.currentTimeMillis()) return null
        return InvitePreview(id, d.getString("ownerUid") ?: return null, d.getString("ownerName") ?: "someone")
    }

    /** Recipient explicitly accepts: marks the invite used and creates the pairing in one atomic batch. */
    suspend fun acceptInvite(ctx: Context, inv: InvitePreview) {
        val u = uid ?: error("not signed in")
        registerDevice(ctx)
        db.runBatch { b ->
            b.update(db.collection("otp_invites").document(inv.id), mapOf("redeemedBy" to u, "recipientName" to myName))
            b.set(
                db.collection("otp_pairings").document(inv.id),
                mapOf(
                    "ownerUid" to inv.ownerUid, "ownerName" to inv.ownerName,
                    "recipientUid" to u, "recipientName" to myName,
                    "recipientPublicKey" to E2E.publicKey(ctx),
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    suspend fun revoke(pairingId: String) {
        val u = uid ?: return
        db.collection("otp_pairings").document(pairingId)
            .update(mapOf("status" to "revoked", "revokedBy" to u, "revokedAt" to FieldValue.serverTimestamp())).await()
    }

    private fun DocumentSnapshot.toPairing() = Pairing(
        id, getString("ownerUid") ?: "", getString("ownerName") ?: "", getString("recipientUid") ?: "",
        getString("recipientName") ?: "", getString("status") == "active", getString("recipientPublicKey") ?: "",
    )

    fun pairingsFlow(asOwner: Boolean): Flow<List<Pairing>> = callbackFlow {
        val u = uid
        if (u == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val reg = db.collection("otp_pairings").whereEqualTo(if (asOwner) "ownerUid" else "recipientUid", u)
            .addSnapshotListener { s, _ -> if (s != null) trySend(s.documents.map { it.toPairing() }) }
        awaitClose { reg.remove() }
    }

    /** Always asks the server so a revocation is honoured immediately. */
    suspend fun activePairingsAsOwner(): List<Pairing> {
        val u = uid ?: return emptyList()
        return db.collection("otp_pairings").whereEqualTo("ownerUid", u).get(Source.SERVER).await()
            .documents.map { it.toPairing() }.filter { it.active }
    }

    // ---- messages & audit ----

    suspend fun sendMessage(p: Pairing, ciphertext: String, senderLabel: String) {
        val u = uid ?: error("not signed in")
        db.runBatch { b ->
            b.set(
                db.collection("otp_messages").document(),
                mapOf(
                    "pairingId" to p.id, "ownerUid" to u, "recipientUid" to p.recipientUid,
                    "ciphertext" to ciphertext, "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            b.set(
                db.collection("otp_audit").document(),
                mapOf(
                    "pairingId" to p.id, "ownerUid" to u, "recipientUid" to p.recipientUid,
                    "ownerName" to myName, "recipientName" to p.recipientName,
                    "sender" to senderLabel, "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    /** The audit log never contains the code itself, only who/when/from-which-sender. */
    fun auditFlow(asOwner: Boolean): Flow<List<AuditItem>> = callbackFlow {
        val u = uid
        if (u == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val reg = db.collection("otp_audit").whereEqualTo(if (asOwner) "ownerUid" else "recipientUid", u)
            .addSnapshotListener { s, _ ->
                if (s != null) trySend(
                    s.documents.map {
                        AuditItem(
                            it.getString("pairingId") ?: "", it.getString("sender") ?: "",
                            it.getString("ownerName") ?: "", it.getString("recipientName") ?: "",
                            it.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                        )
                    }.sortedByDescending { it.timeMs }.take(50)
                )
            }
        awaitClose { reg.remove() }
    }

    class Incoming(val docId: String, val pairingId: String, val ciphertext: String)

    suspend fun pendingMessages(): List<Incoming> {
        val u = uid ?: return emptyList()
        return db.collection("otp_messages").whereEqualTo("recipientUid", u).get(Source.SERVER).await().documents.map {
            Incoming(it.id, it.getString("pairingId") ?: "", it.getString("ciphertext") ?: "")
        }
    }

    suspend fun deleteMessage(docId: String) { db.collection("otp_messages").document(docId).delete().await() }

    // ---- account / data deletion ----

    suspend fun deleteAccountAndData() {
        val u = uid ?: return
        for (field in listOf("ownerUid", "recipientUid")) {
            db.collection("otp_pairings").whereEqualTo(field, u).get().await().documents
                .filter { it.getString("status") == "active" }.forEach { revoke(it.id) }
        }
        db.collection("otp_messages").whereEqualTo("recipientUid", u).get().await().documents.forEach { it.reference.delete().await() }
        db.collection("otp_users").document(u).delete().await()
        auth.currentUser?.delete()?.await()
    }
}
