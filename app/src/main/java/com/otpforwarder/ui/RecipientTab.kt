package com.otpforwarder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.otpforwarder.data.Cloud
import com.otpforwarder.data.ForwardingPrefs
import com.otpforwarder.data.InvitePreview
import com.otpforwarder.delivery.ListenerService
import androidx.compose.material3.Switch
import com.otpforwarder.delivery.Inbox
import com.otpforwarder.delivery.RecipientSync
import com.otpforwarder.ui.theme.CodeStyle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun inviteId(raw: String) = raw.trim().removePrefix(INVITE_SCHEME)

@Composable
fun RecipientTab(pendingInvite: String?, onInviteHandled: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pairings by remember { Cloud.pairingsFlow(false) }.collectAsState(emptyList())
    val audit by remember { Cloud.auditFlow(false) }.collectAsState(emptyList())
    val items by Inbox.items.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var code by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<InvitePreview?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var copiedId by remember { mutableStateOf<String?>(null) }
    val owners = pairings.filter { it.active }
    val hero = heroColors()
    var listening by remember { mutableStateOf(ForwardingPrefs.listening(ctx)) }

    fun load(raw: String) = scope.launch {
        try {
            preview = Cloud.previewInvite(inviteId(raw))
            err = if (preview == null) "That invitation is invalid, expired or already used. Ask for a new one." else null
        } catch (e: Exception) { err = e.message }
    }

    // A recipient has to be told when a code arrives, so ask for notification permission as soon as someone is sharing with them.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notificationsOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    LaunchedEffect(owners.isNotEmpty()) {
        if (owners.isNotEmpty() && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(pendingInvite) { if (pendingInvite != null) { load(pendingInvite); onInviteHandled() } }
    LaunchedEffect(Unit) {
        runCatching { RecipientSync.pull(ctx) }
        while (true) { delay(1000); now = System.currentTimeMillis(); Inbox.prune(now) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        ScreenTitle("Inbox", "Codes people have shared with you.")

        if (owners.isNotEmpty() && !notificationsOn) {
            SoftCard(Modifier.padding(bottom = 12.dp), container = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    "Notifications are off, so you won't be told when a code arrives.",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Button(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }) { Text("Turn on notifications") }
            }
        }

        if (owners.isNotEmpty()) {
            SoftCard(Modifier.padding(bottom = 12.dp)) {
                SpaceRow {
                    RowLabel(
                        "Get new codes automatically",
                        if (listening) "On. A notification stays visible while the app listens." else "Off. Tap Check now to look for codes.",
                        Modifier.weight(1f),
                    )
                    Switch(listening, { on ->
                        listening = on
                        ForwardingPrefs.setListening(ctx, on)
                        if (on) ListenerService.start(ctx) else ListenerService.stop(ctx)
                    })
                }
                if (listening) {
                    Text(
                        "Android requires a notification while the app listens. It is silent, and you can hide it without stopping the listening.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, ListenerService.CHANNEL)
                        )
                    }) { Text("Hide the listening notification") }
                    TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                        Text("Keep it reliable: battery settings")
                    }
                }
            }
        }

        // ---- codes: the code is the hero, set large and grouped for reading aloud ----
        items.forEach { i ->
            val left = ((Inbox.TTL_MS - (now - i.receivedMs)) / 1000).coerceAtLeast(0)
            Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.extraLarge, color = hero.bg) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        buildString {
                            append(if (i.sender.isBlank()) "A code" else "From ${i.sender}")
                            if (i.owner != null) append(", shared by ${i.owner}")
                        },
                        style = MaterialTheme.typography.bodyMedium, color = hero.on.copy(alpha = 0.85f),
                    )
                    Text(groupCode(i.code), style = CodeStyle, color = hero.on)
                    LinearProgressIndicator(
                        progress = { left.toFloat() / (Inbox.TTL_MS / 1000f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = hero.accent,
                        trackColor = hero.on.copy(alpha = 0.2f),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Disappears in ${left / 60}:${(left % 60).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodySmall, color = hero.on.copy(alpha = 0.85f),
                        )
                        Button(
                            onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("code", i.code))
                                copiedId = i.id
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = hero.accent,
                                contentColor = hero.accentOn,
                            ),
                        ) {
                            Icon(if (copiedId == i.id) Icons.Outlined.Check else Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(if (copiedId == i.id) "Copied" else "Copy")
                        }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            SoftCard {
                EmptyNote(
                    "No codes right now",
                    "When someone shares a code with you it appears here and disappears after 5 minutes.",
                )
                TextButton(onClick = { scope.launch { runCatching { RecipientSync.pull(ctx) } } }) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Check now")
                }
            }
        } else {
            TextButton(onClick = { scope.launch { runCatching { RecipientSync.pull(ctx) } } }) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Check now")
            }
        }

        // ---- join ----
        SectionTitle("Join someone's sharing", "Scan the QR code they show you, or paste the link they sent.")
        SoftCard {
            OutlinedButton(onClick = {
                GmsBarcodeScanning.getClient(ctx).startScan()
                    .addOnSuccessListener { b -> b.rawValue?.let { load(it) } }
                    .addOnFailureListener { err = it.message }
            }) { Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Scan QR code") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(code, { code = it }, label = { Text("Invitation link") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(enabled = code.isNotBlank(), onClick = { load(code) }) { Text("Check") }
            }
            preview?.let { p ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(p.ownerName, 44.dp)
                            RowLabel(p.ownerName, "wants to share their one-time codes with you")
                        }
                        Text("You can leave at any time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        Cloud.acceptInvite(ctx, p); preview = null; code = ""; err = null
                                        // Joining means wanting the codes: start listening right away (can be switched off).
                                        ForwardingPrefs.setListening(ctx, true); listening = true; ListenerService.start(ctx)
                                    } catch (e: Exception) { err = e.message }
                                }
                            }) { Text("Accept") }
                            OutlinedButton(onClick = { preview = null }) { Text("Decline") }
                        }
                    }
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }

        // ---- people sharing with me ----
        SectionTitle("Sharing with you")
        if (owners.isEmpty()) EmptyNote("Nobody yet", "Once you accept an invitation, the person appears here.")
        owners.forEach { p ->
            SoftCard(Modifier.padding(bottom = 10.dp)) {
                SpaceRow {
                    Avatar(p.ownerName, 44.dp)
                    RowLabel(p.ownerName, "Sharing codes with you", Modifier.weight(1f))
                    TextButton(
                        onClick = { scope.launch { try { Cloud.revoke(p.id) } catch (e: Exception) { err = e.message } } },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Leave") }
                }
            }
        }

        // ---- history ----
        SectionTitle("History", "What you've received. The codes themselves are never kept.")
        if (audit.isEmpty()) EmptyNote("Nothing yet", "Received codes are listed here with the time and sender.")
        else SoftCard {
            audit.take(10).forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${it.sender}, via ${it.ownerName}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timeMs)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
