package com.otpforwarder.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.otpforwarder.capture.ForwardingService
import com.otpforwarder.data.Cloud
import com.otpforwarder.data.ForwardingPrefs
import com.otpforwarder.data.Pairing
import com.otpforwarder.data.PolicyStore
import com.otpforwarder.delivery.SmsRelay
import com.otpforwarder.policy.Rule
import com.otpforwarder.ui.theme.Lamp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

const val INVITE_SCHEME = "otpforwarder://invite/"

private fun qr(text: String): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
    return Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565).also { b ->
        for (x in 0 until 512) for (y in 0 until 512) b.setPixel(x, y, if (m[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS)
    fun hasPerms() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    var consent by remember { mutableStateOf(ForwardingPrefs.consentAccepted(ctx)) }
    var enabled by remember { mutableStateOf(ForwardingPrefs.enabled(ctx)) }
    var rules by remember { mutableStateOf(PolicyStore.rules(ctx)) }
    var until by remember { mutableStateOf(PolicyStore.activeUntil(ctx)) }
    var preset by remember { mutableStateOf(PolicyStore.windowPreset(ctx)) }
    var paused by remember { mutableStateOf(PolicyStore.paused(ctx)) }
    var invite by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var showRuleSheet by remember { mutableStateOf(false) }
    var relayOpen by remember { mutableStateOf<String?>(null) }

    val pairings by remember { Cloud.pairingsFlow(true) }.collectAsState(emptyList())
    val people = pairings.filter { it.active }
    val audit by remember { Cloud.auditFlow(true) }.collectAsState(emptyList())

    // The pairing id equals the invite id, so the popup closes as soon as the recipient accepts.
    LaunchedEffect(pairings, invite) {
        val id = invite
        if (id != null && pairings.any { it.id == id }) invite = null
    }

    fun turnOff() { ForwardingPrefs.setEnabled(ctx, false); ForwardingService.stop(ctx); enabled = false }
    fun turnOn() { ForwardingPrefs.setEnabled(ctx, true); ForwardingService.start(ctx); enabled = true }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (hasPerms()) turnOn() }
    val smsPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        ScreenTitle("Sharing", "Choose which codes reach which people.")

        // ---- status: the lamp is lit only while sharing is on ----
        val hero = heroColors()
        val onCard = if (enabled) hero.on else MaterialTheme.colorScheme.onSurface
        Surface(
            Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            color = if (enabled) hero.bg else MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SpaceRow {
                    LampDot(enabled, 14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (enabled) "Sharing is on" else "Sharing is off", style = MaterialTheme.typography.titleLarge, color = onCard)
                        Text(
                            if (enabled) "Codes that match a rule go to the people it names." else "No code leaves this phone.",
                            style = MaterialTheme.typography.bodyMedium, color = onCard.copy(alpha = 0.8f),
                        )
                    }
                    Switch(
                        checked = enabled, enabled = consent,
                        onCheckedChange = { on -> if (!on) turnOff() else if (hasPerms()) turnOn() else permLauncher.launch(perms) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Lamp, checkedThumbColor = Color(0xFF2B1B00), checkedBorderColor = Lamp,
                        ),
                    )
                }
                if (!consent) {
                    Text(
                        "Many banks and services forbid sharing codes. Use this only where it is allowed, and only with the account holder's consent.",
                        style = MaterialTheme.typography.bodyMedium, color = onCard.copy(alpha = 0.85f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(consent, { consent = it; ForwardingPrefs.setConsentAccepted(ctx, it); if (!it) turnOff() })
                        Text("I understand and agree", style = MaterialTheme.typography.bodyLarge, color = onCard)
                    }
                }
            }
        }

        // ---- time window ----
        SectionTitle("Sharing window", "Stop sharing automatically after a while.")
        val now = System.currentTimeMillis()
        @Composable
        fun windowChip(key: String, label: String, durationMs: Long) {
            val selected = preset == key
            FilterChip(
                selected = selected,
                onClick = {
                    until = if (durationMs == 0L) 0L else System.currentTimeMillis() + durationMs
                    preset = key
                    PolicyStore.setWindow(ctx, key, until)
                },
                label = { Text(label) },
                leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }) else null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            windowChip("30m", "30 minutes", 30 * 60_000L)
            windowChip("2h", "2 hours", 2 * 3_600_000L)
            windowChip("none", "No limit", 0L)
        }
        if (until != 0L) {
            Text(
                if (until > now) "Sharing stops at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(until))}." else "The window has ended, so nothing is shared.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ---- routing rules ----
        SectionTitle("Who gets what", "A rule sends matching codes to the people you choose. With no rules, nothing is shared.")
        val nameById = people.associate { it.id to it.recipientName }
        rules.forEach { r ->
            SoftCard(Modifier.padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.sender?.let { Pill("From $it") }
                            r.textContains?.let { Pill("Message has “$it”") }
                        }
                        val names = r.pairingIds.mapNotNull { nameById[it] }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (names.isEmpty()) "No active recipient" else names.joinToString(", "),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (names.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(onClick = { PolicyStore.removeRule(ctx, r.id); rules = PolicyStore.rules(ctx) }) {
                        Icon(Icons.Outlined.Delete, "Delete rule", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (rules.isEmpty()) {
            EmptyNote(
                "Nothing is shared yet",
                if (people.isEmpty()) "Invite someone first, then add a rule to choose which codes they receive." else "Add a rule to choose which codes each person receives.",
            )
        }
        FilledTonalButton(enabled = people.isNotEmpty(), onClick = { showRuleSheet = true }) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Add a rule")
        }

        // ---- people ----
        SectionTitle("People", "Everyone here accepted your invitation. They can leave at any time, and so can you remove them.")
        people.forEach { p ->
            key(p.id) {
                val isPaused = p.id in paused
                var phone by remember { mutableStateOf(PolicyStore.relayPhone(ctx, p.id) ?: "") }
                SoftCard(Modifier.padding(bottom = 10.dp)) {
                    SpaceRow {
                        Avatar(p.recipientName, 44.dp)
                        RowLabel(p.recipientName, if (isPaused) "Paused, receives nothing" else "Receiving", Modifier.weight(1f))
                        Switch(!isPaused, { on -> PolicyStore.setPaused(ctx, p.id, !on); paused = PolicyStore.paused(ctx) })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { relayOpen = if (relayOpen == p.id) null else p.id }) {
                            Icon(Icons.Outlined.Sms, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                            Text(if (phone.isBlank()) "Also send by text" else "Text backup on")
                        }
                        TextButton(
                            onClick = { scope.launch { try { Cloud.revoke(p.id) } catch (e: Exception) { err = e.message } } },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Remove access") }
                    }
                    if (relayOpen == p.id) {
                        OutlinedTextField(
                            phone, { phone = it; PolicyStore.setRelayPhone(ctx, p.id, it) }, singleLine = true,
                            label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("The code is also sent as a normal text message. It is not encrypted. Limit: 5 per hour.") },
                        )
                        if (phone.isNotBlank() && !SmsRelay.canSend(ctx)) {
                            TextButton(onClick = { smsPermLauncher.launch(Manifest.permission.SEND_SMS) }) { Text("Allow this app to send texts") }
                        }
                    }
                }
            }
        }
        if (people.isEmpty()) EmptyNote("Nobody yet", "Invite someone you trust. They join only when they accept.")
        Button(onClick = { scope.launch { try { invite = Cloud.createInvite(); err = null } catch (e: Exception) { err = e.message } } }) {
            Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Invite someone")
        }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

        // ---- activity ----
        SectionTitle("Activity", "Every shared code is logged here. This cannot be turned off, and it never records the code.")
        if (audit.isEmpty()) EmptyNote("Nothing shared yet", "Shared codes appear here with the time and who received them.")
        else SoftCard {
            audit.take(10).forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${it.sender} to ${it.recipientName}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timeMs)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Keep sharing reliable: battery settings")
        }
    }

    // ---- invite popup ----
    invite?.let { id ->
        val link = INVITE_SCHEME + id
        val bmp = remember(id) { qr(link) }
        AlertDialog(
            onDismissRequest = { invite = null },
            title = { Text("Invite someone", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.medium, color = Color.White) {
                        Image(bmp.asImageBitmap(), "Invitation QR code", Modifier.padding(10.dp).size(190.dp))
                    }
                    Text(
                        "Valid for 10 minutes, one use. They must open the app and accept.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer { Text(link, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("invite", link))
                        }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.size(6.dp)); Text("Copy link") }
                        OutlinedButton(onClick = {
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), "Share invitation"))
                        }) { Text("Share") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { invite = null }) { Text("Close") } },
        )
    }

    // ---- add rule sheet ----
    if (showRuleSheet) {
        ModalBottomSheet(onDismissRequest = { showRuleSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AddRuleForm(
                people = people,
                onCancel = { showRuleSheet = false },
                onSave = { sender, text, ids ->
                    PolicyStore.addRule(ctx, sender, text, ids)
                    rules = PolicyStore.rules(ctx)
                    showRuleSheet = false
                },
            )
        }
    }
}

@Composable
private fun AddRuleForm(people: List<Pairing>, onCancel: () -> Unit, onSave: (String?, String?, Set<String>) -> Unit) {
    var sender by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    val valid = (sender.isNotBlank() || text.isNotBlank()) && chosen.isNotEmpty()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New rule", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Fill in one or both. If you fill in both, a message must match both.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            sender, { sender = it }, label = { Text("From this sender") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("The name or number the message comes from, for example Clinic.") },
        )
        OutlinedTextField(
            text, { text = it }, label = { Text("Message contains") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("A word or phrase in the message text. Capital letters don't matter.") },
        )
        Text("Send to", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        people.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(p.id in chosen, { chosen = if (it) chosen + p.id else chosen - p.id })
                Avatar(p.recipientName, 36.dp)
                Text(p.recipientName, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(enabled = valid, onClick = { onSave(sender.ifBlank { null }, text.ifBlank { null }, chosen) }) { Text("Save rule") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
