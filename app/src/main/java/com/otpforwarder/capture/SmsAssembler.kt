package com.otpforwarder.capture

import com.otpforwarder.parse.OtpParser

/**
 * Long messages can reach us as several broadcasts. If a message holds no code on its own, it is kept for
 * [windowMs] and joined with the next message from the same sender; the join is used only if it yields a code.
 */
class SmsAssembler(private val windowMs: Long = 15_000, private val maxChars: Int = 1000) {
    private val pending = HashMap<String, IncomingSms>()

    @Synchronized
    fun add(sms: IncomingSms): IncomingSms {
        val prior = pending[sms.sender]?.takeIf { sms.timestampMs - it.timestampMs in 0..windowMs }
        if (OtpParser.parse(sms.body) != null) {
            pending.remove(sms.sender)
            return sms
        }
        if (prior != null) {
            val joined = IncomingSms(sms.sender, (prior.body + sms.body).takeLast(maxChars), sms.timestampMs)
            if (OtpParser.parse(joined.body) != null) {
                pending.remove(sms.sender)
                return joined
            }
            pending[sms.sender] = joined
            return sms
        }
        pending[sms.sender] = sms
        return sms
    }

    companion object {
        val shared = SmsAssembler()
    }
}
