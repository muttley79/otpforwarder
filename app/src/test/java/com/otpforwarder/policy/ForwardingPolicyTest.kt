package com.otpforwarder.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingPolicyTest {
    private val now = 1_000_000L
    private val son1 = PolicyPairing("son1", true)
    private val son2 = PolicyPairing("son2", true)
    private val gone = PolicyPairing("gone", false)
    private val all = listOf(son1, son2, gone)

    private fun cfg(vararg rules: Rule, until: Long = 0, paused: Set<String> = emptySet()) =
        PolicyConfig(rules.toList(), until, paused)

    private fun ids(r: List<PolicyPairing>) = r.map { it.id }
    private val clinicToSon1 = Rule("r1", "Clinic", null, setOf("son1"))
    private val accountToSon2 = Rule("r2", null, "account", setOf("son2"))

    @Test fun noRulesForwardsNothing() =
        assertTrue(ForwardingPolicy.targets("Clinic", "code 1234", cfg(), all, now).isEmpty())

    @Test fun senderRuleRoutesToItsRecipientOnly() =
        assertEquals(listOf("son1"), ids(ForwardingPolicy.targets("Clinic", "code 1234", cfg(clinicToSon1, accountToSon2), all, now)))

    @Test fun textRuleRoutesOnMessageContent() =
        assertEquals(listOf("son2"), ids(ForwardingPolicy.targets("MyBank", "Your Account code is 5521", cfg(clinicToSon1, accountToSon2), all, now)))

    @Test fun oneMessageCanMatchSeveralRules() =
        assertEquals(listOf("son1", "son2"), ids(ForwardingPolicy.targets("Clinic", "Account verification 9911", cfg(clinicToSon1, accountToSon2), all, now)))

    @Test fun senderMatchIsCaseAndSpaceInsensitive() =
        assertEquals(listOf("son1"), ids(ForwardingPolicy.targets("  clinic ", "x 1234", cfg(clinicToSon1), all, now)))

    @Test fun senderMustBeExact() =
        assertTrue(ForwardingPolicy.targets("Clinic Ltd", "x 1234", cfg(clinicToSon1), all, now).isEmpty())

    @Test fun textMatchWorksInHebrew() {
        val rule = Rule("h", null, "בנק", setOf("son1"))
        assertEquals(listOf("son1"), ids(ForwardingPolicy.targets("X", "קוד האימות מבנק הוא 7731", cfg(rule), all, now)))
    }

    @Test fun senderAndTextBothMustMatch() {
        val rule = Rule("b", "Clinic", "appointment", setOf("son1"))
        assertTrue(ForwardingPolicy.targets("Clinic", "code 1234", cfg(rule), all, now).isEmpty())
        assertEquals(listOf("son1"), ids(ForwardingPolicy.targets("Clinic", "appointment code 1234", cfg(rule), all, now)))
    }

    @Test fun ruleWithoutConditionsMatchesNothing() {
        val rule = Rule("e", "", " ", setOf("son1"))
        assertTrue(ForwardingPolicy.targets("Anyone", "code 1234", cfg(rule), all, now).isEmpty())
    }

    @Test fun revokedAndPausedExcluded() {
        val both = Rule("r", "Clinic", null, setOf("son1", "son2", "gone"))
        assertEquals(listOf("son2"), ids(ForwardingPolicy.targets("Clinic", "x 1234", cfg(both, paused = setOf("son1")), all, now)))
    }

    @Test fun timeWindowExpired() =
        assertTrue(ForwardingPolicy.targets("Clinic", "x 1234", cfg(clinicToSon1, until = now), all, now).isEmpty())

    @Test fun timeWindowStillOpen() =
        assertEquals(listOf("son1"), ids(ForwardingPolicy.targets("Clinic", "x 1234", cfg(clinicToSon1, until = now + 1), all, now)))

    @Test fun rateLimiterBlocksThenRecovers() {
        val r = RateLimiter(2, 1000)
        assertTrue(r.tryAcquire("x", 0)); assertTrue(r.tryAcquire("x", 10))
        assertTrue(!r.tryAcquire("x", 20))
        assertTrue(r.tryAcquire("y", 20))
        assertTrue(r.tryAcquire("x", 1001))
    }
}
