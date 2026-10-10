package com.otpforwarder.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderMatchingTest {
    private fun same(a: String, b: String) = ForwardingPolicy.sameSender(a, b)

    @Test fun sameNumberInAnyFormat() {
        assertTrue(same("052-123-4567", "+972521234567"))
        assertTrue(same("0521234567", "+972 52 123 4567"))
        assertTrue(same("+972521234567", "0521234567"))
        assertTrue(same("(052) 123-4567", "972521234567"))
    }

    @Test fun differentNumbersDoNotMatch() {
        assertFalse(same("0521234567", "0521234568"))
        assertFalse(same("0521234567", "+972541234567"))
    }

    @Test fun shortCodesMustBeExact() {
        assertTrue(same("37773", "37773"))
        assertFalse(same("37773", "737773"))
        assertFalse(same("7773", "37773"))
    }

    @Test fun namesAreExactAndCaseInsensitive() {
        assertTrue(same("Clinic", " clinic "))
        assertFalse(same("Clinic", "Clinic Ltd"))
        assertFalse(same("Clinic", "0521234567"))
    }

    @Test fun pickedContactNumberRoutesLikeASender() {
        val rule = Rule("r", "052-123-4567", null, setOf("son"), label = "Clinic")
        val cfg = PolicyConfig(listOf(rule), 0, emptySet())
        val result = ForwardingPolicy.targets("+972521234567", "code 1234", cfg, listOf(PolicyPairing("son", true)), 1L)
        assertEquals(listOf("son"), result.map { it.id })
    }
}
