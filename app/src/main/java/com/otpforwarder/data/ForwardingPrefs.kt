package com.otpforwarder.data

import android.content.Context

/** Local consent state. Nothing is captured unless the owner has accepted the notice and turned forwarding on. */
object ForwardingPrefs {
    private const val FILE = "forwarding"
    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun consentAccepted(c: Context) = sp(c).getBoolean("consent", false)
    fun setConsentAccepted(c: Context, v: Boolean) = sp(c).edit().putBoolean("consent", v).apply()

    /** Recipient side: keep a live connection so codes arrive without tapping "Check now". */
    fun listening(c: Context) = sp(c).getBoolean("listening", false)
    fun setListening(c: Context, v: Boolean) = sp(c).edit().putBoolean("listening", v).apply()

    fun enabled(c: Context) = consentAccepted(c) && sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()
}
