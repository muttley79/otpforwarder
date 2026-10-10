package com.otpforwarder.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpParserTest {
    @Test fun english() = assertEquals("482913", OtpParser.parse("Your verification code is 482913. Do not share it."))
    @Test fun googleStyle() = assertEquals("123456", OtpParser.parse("G-123456 is your Google verification code."))
    @Test fun spaced() = assertEquals("123456", OtpParser.parse("Your code: 123 456"))
    @Test fun hebrew() = assertEquals("7731", OtpParser.parse("קוד האימות שלך הוא 7731"))
    @Test fun hebrewOneTime() = assertEquals("902144", OtpParser.parse("סיסמה חד פעמית: 902144 תקפה ל-5 דקות"))
    @Test fun ignoresAmount() = assertEquals("5521", OtpParser.parse("Charge of 1500 ₪ at Shop. Your OTP is 5521"))
    @Test fun ignoresPhone() = assertEquals("3344", OtpParser.parse("Call +972501234567 if not you. Code 3344"))
    @Test fun noKeyword() = assertNull(OtpParser.parse("Meeting moved to 1830, see you there"))
    @Test fun noDigits() = assertNull(OtpParser.parse("Your verification code will arrive soon"))
}
