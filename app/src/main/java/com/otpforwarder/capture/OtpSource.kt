package com.otpforwarder.capture

data class IncomingSms(val sender: String, val body: String, val timestampMs: Long)

/** A way of obtaining incoming messages. Sideload build uses [SmsReceiver]; a Play build could swap in another. */
interface OtpSource {
    val name: String
}
