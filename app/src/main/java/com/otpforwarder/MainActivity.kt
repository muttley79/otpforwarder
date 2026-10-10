package com.otpforwarder

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.otpforwarder.capture.ForwardingService
import com.otpforwarder.data.ForwardingPrefs
import com.otpforwarder.delivery.ListenerService
import com.otpforwarder.data.ThemeMode
import com.otpforwarder.data.ThemePrefs
import com.otpforwarder.ui.AppRoot
import com.otpforwarder.ui.INVITE_SCHEME
import com.otpforwarder.ui.theme.OtpTheme

class MainActivity : ComponentActivity() {
    private var pendingInvite by mutableStateOf<String?>(null)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var requestedTab by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash just long enough for the lamp to finish lighting; it is the app's only launch animation.
        val start = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition { SystemClock.uptimeMillis() - start < 750 }
        super.onCreate(savedInstanceState)
        themeMode = ThemePrefs.get(this)
        readInvite(intent)
        if (ForwardingPrefs.enabled(this)) ForwardingService.start(this)
        if (ForwardingPrefs.listening(this)) ListenerService.start(this)
        setContent {
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Keep status and navigation bar icons readable when the app's choice differs from the system's.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            OtpTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        pendingInvite = pendingInvite,
                        onInviteHandled = { pendingInvite = null },
                        requestedTab = requestedTab,
                        onTabHandled = { requestedTab = null },
                        themeMode = themeMode,
                        onThemeMode = { themeMode = it; ThemePrefs.set(this, it) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readInvite(intent)
    }

    private fun readInvite(i: Intent?) {
        i?.dataString?.takeIf { it.startsWith(INVITE_SCHEME) }?.let { pendingInvite = it }
        // A received-code notification asks for the Inbox.
        if (i?.hasExtra(EXTRA_TAB) == true) requestedTab = i.getIntExtra(EXTRA_TAB, TAB_SHARING)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_SHARING = 0
        const val TAB_INBOX = 1
    }
}
