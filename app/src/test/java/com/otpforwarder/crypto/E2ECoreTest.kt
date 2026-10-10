package com.otpforwarder.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class E2ECoreTest {
    @Test fun roundTrip() {
        val recipient = E2ECore.newKeyset()
        val pub = E2ECore.publicKeyB64(recipient)
        val msg = """{"code":"123456"}""".toByteArray()
        val ct = E2ECore.encrypt(pub, msg, "pair1".toByteArray())
        assertNotEquals(String(msg), ct)
        assertArrayEquals(msg, E2ECore.decrypt(recipient, ct, "pair1".toByteArray()))
    }

    @Test fun wrongContextRejected() {
        val recipient = E2ECore.newKeyset()
        val ct = E2ECore.encrypt(E2ECore.publicKeyB64(recipient), "x".toByteArray(), "pair1".toByteArray())
        try { E2ECore.decrypt(recipient, ct, "pair2".toByteArray()); fail() } catch (_: Exception) {}
    }

    @Test fun otherKeyCannotDecrypt() {
        val ct = E2ECore.encrypt(E2ECore.publicKeyB64(E2ECore.newKeyset()), "x".toByteArray(), "p".toByteArray())
        try { E2ECore.decrypt(E2ECore.newKeyset(), ct, "p".toByteArray()); fail() } catch (_: Exception) {}
    }
}
