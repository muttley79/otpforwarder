package com.otpforwarder.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpParserRuleWordTest {
    @Test fun withoutAKeywordNothingIsFound() = assertNull(OtpParser.parse("ניסיון 1234"))

    @Test fun aWordFromTheRuleCountsAsTheKeyword() =
        assertEquals("1234", OtpParser.parse("ניסיון 1234", listOf("ניסיון")))

    @Test fun ruleWordIsCaseInsensitive() =
        assertEquals("5521", OtpParser.parse("Your Trial number 5521", listOf("trial")))

    @Test fun ruleWordStillNeedsANumber() = assertNull(OtpParser.parse("trial period", listOf("trial")))

    @Test fun amountsAreStillIgnored() =
        assertEquals("7788", OtpParser.parse("trial charge 1500 ₪, number 7788", listOf("trial")))

    @Test fun blankRuleWordsAreIgnored() = assertNull(OtpParser.parse("hello 1234", listOf(" ", "")))
}
