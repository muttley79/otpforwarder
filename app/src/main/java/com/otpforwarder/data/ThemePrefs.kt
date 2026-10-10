package com.otpforwarder.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Appearance choice. SYSTEM follows the phone's own light/dark setting. */
object ThemePrefs {
    private fun sp(c: Context) = c.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun get(c: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(sp(c).getString("theme", ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)

    fun set(c: Context, mode: ThemeMode) = sp(c).edit().putString("theme", mode.name).apply()
}
