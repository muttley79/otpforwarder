package com.otpforwarder.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsAssemblerTest {
    private fun sms(body: String, t: Long, sender: String = "Clinic") = IncomingSms(sender, body, t)

    @Test fun codeInSecondPartIsJoined() {
        val a = SmsAssembler()
        a.add(sms("Dear patient, your verification code is", 0))
        assertEquals("Dear patient, your verification code is 482913", a.add(sms(" 482913", 2000)).body)
    }

    @Test fun singleMessagePassesThrough() {
        assertEquals("Your code is 1234", SmsAssembler().add(sms("Your code is 1234", 0)).body)
    }

    @Test fun differentSenderNotJoined() {
        val a = SmsAssembler()
        a.add(sms("your verification", 0, "A"))
        assertEquals(" code is 482913", a.add(sms(" code is 482913", 1000, "B")).body)
    }

    @Test fun staleFragmentNotJoined() {
        val a = SmsAssembler(windowMs = 15_000)
        a.add(sms("your verification", 0))
        assertEquals(" code is 482913", a.add(sms(" code is 482913", 60_000)).body)
    }

    @Test fun threeParts() {
        val a = SmsAssembler()
        a.add(sms("Hello. Your", 0)); a.add(sms(" one-time code:", 1000))
        assertEquals("Hello. Your one-time code: 778899", a.add(sms(" 778899", 2000)).body)
    }
}
