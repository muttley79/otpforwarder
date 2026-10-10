package com.otpforwarder.delivery

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.otpforwarder.data.Cloud
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        runBlocking { withTimeoutOrNull(10_000) { runCatching { Cloud.saveToken(token) } } }
    }

    // The push is only a wake-up signal; the code itself is fetched as ciphertext and decrypted locally.
    override fun onMessageReceived(message: RemoteMessage) {
        runBlocking { withTimeoutOrNull(15_000) { runCatching { RecipientSync.pull(applicationContext) } } }
    }
}
