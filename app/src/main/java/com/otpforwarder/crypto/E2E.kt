package com.otpforwarder.crypto

import android.content.Context
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.ByteArrayOutputStream

/** Platform-independent HPKE (X25519 / AES-256-GCM) helpers so they can be unit-tested on the JVM. */
object E2ECore {
    const val TEMPLATE = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"

    init { HybridConfig.register() }

    fun newKeyset(): KeysetHandle = KeysetHandle.generateNew(KeyTemplates.get(TEMPLATE))

    fun publicKeyB64(private: KeysetHandle): String {
        val out = ByteArrayOutputStream()
        CleartextKeysetHandle.write(private.publicKeysetHandle, BinaryKeysetWriter.withOutputStream(out))
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray())
    }

    fun encrypt(recipientPublicB64: String, plaintext: ByteArray, context: ByteArray): String {
        val pub = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(java.util.Base64.getDecoder().decode(recipientPublicB64)))
        val enc = pub.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        return java.util.Base64.getEncoder().encodeToString(enc.encrypt(plaintext, context))
    }

    fun decrypt(private: KeysetHandle, cipherB64: String, context: ByteArray): ByteArray {
        val dec = private.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        return dec.decrypt(java.util.Base64.getDecoder().decode(cipherB64), context)
    }
}

/** Device key pair, wrapped by the Android Keystore. The private key never leaves the device. */
object E2E {
    private fun handle(ctx: Context): KeysetHandle {
        E2ECore  // ensure registration
        return AndroidKeysetManager.Builder()
            .withSharedPref(ctx, "otp_keyset", "otp_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get(E2ECore.TEMPLATE))
            .withMasterKeyUri("android-keystore://otp_master_key")
            .build()
            .keysetHandle
    }

    fun publicKey(ctx: Context) = E2ECore.publicKeyB64(handle(ctx))
    fun decrypt(ctx: Context, cipherB64: String, pairingId: String) =
        E2ECore.decrypt(handle(ctx), cipherB64, pairingId.toByteArray())
    fun encrypt(recipientPublicB64: String, plaintext: ByteArray, pairingId: String) =
        E2ECore.encrypt(recipientPublicB64, plaintext, pairingId.toByteArray())
}
