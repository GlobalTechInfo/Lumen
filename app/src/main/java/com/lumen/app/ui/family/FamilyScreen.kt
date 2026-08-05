package com.lumen.app.ui.family

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanOptions
import com.lumen.app.data.link.NearbyChild
import com.lumen.app.data.link.PeerInfo
import com.lumen.app.data.link.formatAge
import com.lumen.app.domain.TimeUtils
import com.lumen.app.ui.FamilyState
import com.lumen.app.ui.FamilyViewModel
import com.lumen.app.ui.viewModelFactoryFor
import com.lumen.app.ui.components.GlassCard
import com.lumen.app.ui.components.LocalContainer
import com.lumen.app.ui.components.MetricChip
import com.lumen.app.ui.components.SectionTitle
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.Ink
import com.lumen.app.ui.theme.Mint
import com.lumen.app.ui.theme.Periwinkle
import com.lumen.app.ui.theme.Rose
import com.lumen.app.ui.theme.TextMuted

@Composable
fun FamilyScreen() {
    val container = LocalContainer.current
    val vm: FamilyViewModel = viewModel(factory = viewModelFactoryFor(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0B1226), MaterialTheme.colorScheme.background)),
            ),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { SectionTitle("Watch") }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Person,
                        null,
                        tint = Periwinkle,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Other phones can see this one", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Turn this on and someone who cares about you — a partner, a sister, a friend — " +
                                "can see this phone's usage report from their own Lumen app. They scan your " +
                                "code once, face-to-face. Nothing is shared unless you switch this on.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.nickname,
                            onValueChange = vm::setNickname,
                            label = { Text("Name shown to friends") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Allow access",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = state.linkEnabled,
                                onCheckedChange = vm::toggleLink,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Amber,
                                    checkedTrackColor = Amber.copy(alpha = 0.3f),
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
                if (state.linkEnabled) {
                    LinkCodeCard(state.qrPayload, vm)
                }
            }
        }

        item {
            SectionTitle("Your watchlist")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                if (state.peers.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.ChildCare,
                            null,
                            tint = Amber,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Link a phone", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "On the other phone, open Watch → turn on Allow access, then scan its code " +
                                "with this phone. One-time, in person — exactly like scanning a boarding pass.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                } else {
                    state.peers.forEach { peer ->
                        PeerCard(peer, state, vm)
                        Spacer(Modifier.height(14.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                scanButtons(vm)
                Spacer(Modifier.height(8.dp))
                importButtons(vm)
            }
        }

        state.message?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.startsWith("Could not") || message.startsWith("Link to")) {
                        Rose
                    } else {
                        Mint
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.busy) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = Amber,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Syncing…", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun LinkCodeCard(payload: String?, vm: FamilyViewModel) {
    if (payload == null) return
    val context = LocalContext.current
    var showExportPin by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }.onFailure {
            vm.notify("Could not save the export: ${it.message ?: "try again"}")
        }.onSuccess {
            vm.notify("Report exported — share the file and your PIN with the other phone.")
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = remember(payload) {
            runCatching {
                val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 480, 480)
                val width = matrix.width
                val height = matrix.height
                val pixels = IntArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        pixels[y * width + x] = if (matrix[x, y]) {
                            android.graphics.Color.BLACK
                        } else {
                            android.graphics.Color.WHITE
                        }
                    }
                }
                Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
        if (bitmap != null) {
            Box(
                Modifier
                    .padding(10.dp)
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Pairing code",
                    modifier = Modifier.size(190.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The person watching this phone scans this code with their Lumen app.",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Lumen link code", payload))
                vm.notify("Link code copied — paste it into the other phone's Lumen app.")
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Copy link code", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showExportPin = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Icon(Icons.Rounded.Upload, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export long-distance report", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showExportPin) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExportPin = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Encrypt an export") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isLetterOrDigit() }.take(16) },
                        label = { Text("PIN (4+ characters)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The file holds your last 30 days, locked with this PIN. Send it any way " +
                            "you like — the PIN is the only key.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length >= 4,
                    onClick = {
                        showExportPin = false
                        vm.exportReport(pin) { bytes ->
                            pendingExport = bytes
                            exportLauncher.launch("LumenReport.lumenlink")
                        }
                    },
                ) { Text("Encrypt & export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportPin = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun scanButtons(vm: FamilyViewModel) {
    var showPaste by remember { mutableStateOf(false) }
    var confirmCamera by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val contents = result.data?.getStringExtra("SCAN_RESULT")
        if (!contents.isNullOrEmpty()) vm.pair(contents)
    }
    val launchScanner = {
        if (activity != null) {
            val intent = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Point at their Lumen code")
                .setBeepEnabled(false)
                .createScanIntent(activity)
            scanLauncher.launch(intent)
        }
    }

    Button(
        onClick = { confirmCamera = true },
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(Icons.Rounded.QrCodeScanner, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Scan their code", fontWeight = FontWeight.SemiBold)
    }

    if (confirmCamera) {
        AlertDialog(
            onDismissRequest = { confirmCamera = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Open the camera?") },
            text = {
                Text(
                    "Lumen uses your camera for one thing only: reading the other phone's link code. " +
                        "Nothing is recorded, stored, or sent anywhere — the scan happens entirely on this phone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCamera = false
                        launchScanner()
                    },
                ) { Text("Yes, scan") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCamera = false }) { Text("Not now") }
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { showPaste = true },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text("Paste a code instead")
    }

    if (showPaste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPaste = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Paste link code") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("lumenlink://pair?p=…") },
                    supportingText = { Text("On the other phone: Watch → Allow access on → tap Copy link code.") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) vm.pair(text)
                        showPaste = false
                    },
                ) { Text("Pair") }
            },
            dismissButton = {
                TextButton(onClick = { showPaste = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun importButtons(vm: FamilyViewModel) {
    val context = LocalContext.current
    var showImportPin by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            vm.notify("Could not read that file.")
        } else {
            pendingImport = bytes
            showImportPin = true
        }
    }

    OutlinedButton(
        onClick = { importLauncher.launch(arrayOf("*/*")) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Import long-distance report", fontWeight = FontWeight.SemiBold)
    }

    if (showImportPin) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showImportPin = false
                pendingImport = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Unlock the report") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isLetterOrDigit() }.take(16) },
                        label = { Text("PIN from the other phone") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Their phone locked this file with this PIN — without it the report " +
                            "stays unreadable.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length >= 4,
                    onClick = {
                        showImportPin = false
                        val bytes = pendingImport
                        pendingImport = null
                        if (bytes != null) vm.importReport(bytes, pin)
                    },
                ) { Text("Unlock & import") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportPin = false
                    pendingImport = null
                }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PeerCard(peer: PeerInfo, state: FamilyState, vm: FamilyViewModel) {
    val snapshot = state.snapshots[peer.id]
    var manualHost by remember { mutableStateOf("") }
    var showKnockDialog by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                peer.nickname,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.removePeer(peer.id) }) {
                Text("Unlink", color = TextMuted)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))

        if (snapshot == null) {
            Text(
                "Not synced yet. Ask them to open Watch → Allow access, then tap Sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!snapshot.hasAccess) {
            Text(
                "This phone hasn't allowed Lumen screen access yet — its report is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = Rose,
            )
        } else {
            snapshot.today?.let { today ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MetricChip(
                        icon = Icons.Rounded.TouchApp,
                        value = TimeUtils.formatDuration(today.screenTimeMs),
                        label = "Today",
                        tint = Periwinkle,
                    )
                    MetricChip(
                        icon = Icons.Rounded.Person,
                        value = "${today.unlocks}",
                        label = "Pickups",
                        tint = Amber,
                    )
                    MetricChip(
                        icon = Icons.Rounded.Notifications,
                        value = "${today.notificationCount}",
                        label = "Alerts",
                        tint = Mint,
                    )
                }
                if (today.apps.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Top apps", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                    today.apps.take(3).forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                TimeUtils.formatDuration(app.timeMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
            if (snapshot.week.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "This week: ${TimeUtils.formatDuration(snapshot.week.sumOf { it.screenTimeMs })}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val myWeek = state.myWeekTotal
                if (myWeek > 0L) {
                    val theirWeek = snapshot.week.sumOf { it.screenTimeMs }
                    val diff = myWeek - theirWeek
                    val leader = if (diff > 0) peer.nickname else "you"
                    val margin = TimeUtils.formatDuration(kotlin.math.abs(diff))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Duel · you ${TimeUtils.formatDuration(myWeek)} vs ${TimeUtils.formatDuration(theirWeek)} — " +
                            (if (diff > 0) "$leader lead by $margin" else "$leader winning by $margin"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (diff > 0) Mint else Amber,
                    )
                    if (state.celebrations[peer.id] == true) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Sync celebration — you both stayed under your goals this week.",
                            style = MaterialTheme.typography.labelMedium,
                            color = Mint,
                        )
                    }
                }
            }
            val history = state.histories[peer.id].orEmpty()
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Last 7 days", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                Spacer(Modifier.height(6.dp))
                val maxMs = history.maxOf { it.screenTimeMs }.coerceAtLeast(1L)
                history.takeLast(7).forEach { day ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            day.date.dayOfWeek.name.take(3).lowercase()
                                .replaceFirstChar { c -> c.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                            modifier = Modifier.width(36.dp),
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(0.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(day.screenTimeMs.toFloat() / maxMs)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Amber),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            TimeUtils.formatDuration(day.screenTimeMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Synced ${formatAge(snapshot.syncedAtMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = Mint,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row {
            Button(
                onClick = {
                    val nearby = state.nearby.firstOrNull { it.nickname == peer.nickname }
                    if (nearby != null) {
                        vm.syncWithNearby(nearby)
                    } else {
                        vm.manualSync(manualHost, 8787)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sync now", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = { showKnockDialog = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Knock", fontWeight = FontWeight.SemiBold)
            }
        }
        if (showKnockDialog) {
            KnockDialog(
                peer = peer,
                nearby = state.nearby.firstOrNull { it.nickname == peer.nickname },
                vm = vm,
                onDismiss = { showKnockDialog = false },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.nearby.any { it.nickname == peer.nickname }) {
                "This phone is on your network right now — reports auto-sync every 2 minutes."
            } else {
                "Tip: for long-distance sync, a private tunnel is in the works. For now, sync over the " +
                    "same Wi-Fi, a hotspot, or enter their phone's IP address below."
            },
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        OutlinedTextField(
            value = manualHost,
            onValueChange = { manualHost = it },
            label = { Text("Their phone's IP address") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun KnockDialog(
    peer: PeerInfo,
    nearby: NearbyChild?,
    vm: FamilyViewModel,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Knock on ${peer.nickname}'s phone") },
        text = {
            Column {
                Text(
                    "A gentle, encrypted message delivered as a notification. " +
                        "They don't even need Lumen open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 140) text = it },
                    placeholder = { Text("e.g. time for our daily check-in?") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${text.length}/140",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && !sending,
                onClick = {
                    sending = true
                    val host = nearby?.host ?: peer.host
                    if (host == null) {
                        vm.notify("No host known — sync once over Wi-Fi first.")
                        sending = false
                        return@TextButton
                    }
                    vm.sendKnock(peer, host, nearby?.port ?: 8787, text)
                    onDismiss()
                },
            ) {
                Text("Send", color = Amber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) {
                Text("Cancel", color = TextMuted)
            }
        },
    )
}