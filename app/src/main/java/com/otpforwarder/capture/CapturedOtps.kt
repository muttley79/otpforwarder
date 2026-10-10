package com.otpforwarder.capture

import com.otpforwarder.parse.OtpParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CapturedOtp(val sender: String, val code: String, val timestampMs: Long)

/** In-memory (never persisted) list of recent OTPs, until the delivery layer exists. */
object CapturedOtps {
    private const val MAX = 20
    private val _items = MutableStateFlow<List<CapturedOtp>>(emptyList())
    val items: StateFlow<List<CapturedOtp>> = _items

    fun onSms(sms: IncomingSms) {
        val code = OtpParser.parse(sms.body) ?: return
        _items.value = (listOf(CapturedOtp(sms.sender, code, sms.timestampMs)) + _items.value).take(MAX)
    }

    fun clear() { _items.value = emptyList() }
}
