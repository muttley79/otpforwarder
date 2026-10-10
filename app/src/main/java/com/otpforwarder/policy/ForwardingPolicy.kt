package com.otpforwarder.policy

/**
 * One routing rule: when a message matches, its code goes to [pairingIds].
 * [sender] and [textContains] are both optional but at least one must be set; if both are set, both must match.
 */
data class Rule(
    val id: String,
    val sender: String?,
    val textContains: String?,
    val pairingIds: Set<String>,
    /** Contact name shown in the rule when the sender was picked from contacts; display only, never used for matching. */
    val label: String? = null,
)

data class PolicyConfig(
    val rules: List<Rule>,
    /** Epoch ms after which nothing is forwarded; 0 means no time limit. */
    val activeUntilMs: Long,
    val pausedPairings: Set<String>,
)

data class PolicyPairing(val id: String, val active: Boolean)

/** Pure decision logic: which pairings (if any) may receive a code from this message right now. */
object ForwardingPolicy {
    fun normalize(s: String) = s.trim().lowercase()

    private val phoneLike = Regex("^\\+?[\\d\\s\\-().]{5,}$")

    /**
     * Sender names must match exactly (ignoring case and edge spaces). Phone numbers match regardless of formatting and
     * of a country code, so 052-123-4567, 0521234567 and +972521234567 are the same sender. Short codes must match exactly.
     */
    fun sameSender(wanted: String, actual: String): Boolean {
        if (normalize(wanted) == normalize(actual)) return true
        if (!phoneLike.matches(wanted.trim()) || !phoneLike.matches(actual.trim())) return false
        val a = wanted.filter { it.isDigit() }.trimStart('0')
        val b = actual.filter { it.isDigit() }.trimStart('0')
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return short.length >= 8 && long.endsWith(short)
    }

    fun matches(rule: Rule, sender: String, body: String): Boolean {
        val s = rule.sender?.takeIf { it.isNotBlank() }
        val t = rule.textContains?.takeIf { it.isNotBlank() }
        // A rule with no condition would match everything, so it matches nothing.
        if (s == null && t == null) return false
        if (s != null && !sameSender(s, sender)) return false
        if (t != null && !body.lowercase().contains(normalize(t))) return false
        return true
    }

    /** The rules this message satisfies, regardless of time window or who is paused. */
    fun matchingRules(sender: String, body: String, config: PolicyConfig): List<Rule> =
        config.rules.filter { matches(it, sender, body) }

    fun targets(sender: String, body: String, config: PolicyConfig, pairings: List<PolicyPairing>, nowMs: Long): List<PolicyPairing> {
        // No rules = nothing is forwarded: there is deliberately no "forward everything" mode.
        if (config.activeUntilMs != 0L && nowMs >= config.activeUntilMs) return emptyList()
        val wanted = config.rules.filter { matches(it, sender, body) }.flatMapTo(HashSet()) { it.pairingIds }
        return pairings.filter { it.active && it.id in wanted && it.id !in config.pausedPairings }
    }
}

/** Sliding-window limiter, used to cap SMS relay volume per recipient. */
class RateLimiter(private val max: Int, private val windowMs: Long) {
    private val events = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun tryAcquire(key: String, nowMs: Long): Boolean {
        val q = events.getOrPut(key) { ArrayDeque() }
        while (q.isNotEmpty() && nowMs - q.first() >= windowMs) q.removeFirst()
        if (q.size >= max) return false
        q.addLast(nowMs)
        return true
    }
}
