package com.otpforwarder.parse

/** Extracts a one-time code from an SMS body. Returns null when the message doesn't look like an OTP. */
object OtpParser {
    private val keywords = listOf(
        "otp", "code", "verification", "verify", "passcode", "password", "one-time", "one time", "pin", "2fa",
        "קוד", "סיסמה", "סיסמא", "אימות", "חד פעמי", "חד-פעמי",
    )

    // 4-8 digits, not embedded in a longer number, decimal, or phone-like sequence.
    private val candidate = Regex("""(?<![\d.,+])(?<!\d-)(\d{4,8})(?![\d]|[.,]\d)""")
    private val spacedGroups = Regex("""\b(\d{3})[ -](\d{3})\b""")
    private val currencyAfter = Regex("""^\s*(%|₪|\$|€|nis|usd|eur|ש"ח|שקל)""", RegexOption.IGNORE_CASE)
    private val currencyBefore = Regex("""[₪$€]\s*$""")

    /**
     * [extraKeywords] are words the owner wrote into a matching rule ("Message contains"). They count as code keywords too,
     * so a rule on a word like "trial" works without the message also saying "code".
     */
    fun parse(body: String, extraKeywords: List<String> = emptyList()): String? {
        val text = spacedGroups.replace(body) { it.groupValues[1] + it.groupValues[2] }
        val lower = text.lowercase()
        val allKeywords = keywords + extraKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val keywordPositions = allKeywords.flatMap { k ->
            Regex(Regex.escape(k)).findAll(lower).map { it.range.first }.toList()
        }
        if (keywordPositions.isEmpty()) return null

        val valid = candidate.findAll(text).filter { m ->
            !currencyAfter.containsMatchIn(text.substring(m.range.last + 1)) &&
                !currencyBefore.containsMatchIn(text.substring(0, m.range.first))
        }.toList()
        if (valid.isEmpty()) return null

        return valid.minBy { m -> keywordPositions.minOf { kotlin.math.abs(it - m.range.first) } }.value
    }
}
