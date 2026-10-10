package com.otpforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.size
import com.otpforwarder.data.ThemeMode
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.otpforwarder.data.Cloud
import kotlinx.coroutines.launch

private data class Dest(val label: String, val icon: ImageVector)

@Composable
fun AppRoot(pendingInvite: String?, onInviteHandled: () -> Unit, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val ctx = LocalContext.current
    if (!Cloud.configured(ctx)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenTitle("Not connected")
            Text("Add app/google-services.json from your Firebase project and rebuild.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val user by Cloud.userFlow().collectAsState(initial = Cloud.currentUser)
    var rev by remember { mutableIntStateOf(0) }  // bumped when the profile name changes (no auth event fires for that)
    if (user == null || rev < 0 || !Cloud.hasName()) { WelcomeScreen { rev++ }; return }

    LaunchedEffect(user?.uid, rev) { runCatching { Cloud.registerDevice(ctx) } }

    var tab by remember { mutableIntStateOf(if (pendingInvite != null) 1 else 0) }
    LaunchedEffect(pendingInvite) { if (pendingInvite != null) tab = 1 }
    val dests = listOf(Dest("Sharing", Icons.Outlined.Share), Dest("Inbox", Icons.Outlined.Inbox), Dest("Account", Icons.Outlined.Person))

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                dests.forEachIndexed { i, d ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(d.icon, d.label) }, label = { Text(d.label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> OwnerTab()
                1 -> RecipientTab(pendingInvite, onInviteHandled)
                else -> AccountTab(themeMode, onThemeMode)
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        LampDot(lit = true, size = 18.dp)
        Text("Share a code, not your phone.", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Let people you trust receive the one-time codes sent to your number, with your say over every one.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Promise(Icons.Outlined.Lock, "Codes are encrypted on your phone before they leave it.")
        Promise(Icons.Outlined.CheckCircle, "Nobody joins until they accept your invitation.")
        Promise(Icons.AutoMirrored.Outlined.ExitToApp, "Either of you can end it at any time.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("This is what the people you pair with will see, for example “Dan (son)”. No email or password needed.") },
        )
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                scope.launch {
                    busy = true; err = null
                    try { Cloud.start(name); onDone() } catch (e: Exception) { err = e.message } finally { busy = false }
                }
            },
        ) { Text("Continue") }
    }
}

@Composable
private fun Promise(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccountTab(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        ScreenTitle("Account")
        SoftCard {
            SpaceRow {
                Avatar(Cloud.myName, 48.dp)
                RowLabel(Cloud.myName, "This is the name people see when you pair.")
            }
            Text(
                "Your identity lives on this phone. If you uninstall the app or change phones you start fresh and pair again.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionTitle("Appearance", "Follow your phone's setting, or choose a look.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.SYSTEM to "Match phone", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    label = { Text(label) },
                    leadingIcon = if (themeMode == mode) ({ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }) else null,
                )
            }
        }
        SectionTitle("Delete my data", "Ends every pairing, removes waiting messages and your profile, and deletes your identity.")
        if (!confirm) {
            OutlinedButton(onClick = { confirm = true }) { Text("Delete account and data") }
        } else {
            Button(
                onClick = { scope.launch { try { Cloud.deleteAccountAndData() } catch (e: Exception) { err = e.message } } },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Yes, delete everything") }
        }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}
