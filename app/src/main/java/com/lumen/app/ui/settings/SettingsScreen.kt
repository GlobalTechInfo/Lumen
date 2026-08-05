package com.lumen.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumen.app.ui.AppCandidate
import com.lumen.app.ui.SettingsViewModel
import com.lumen.app.ui.viewModelFactoryFor
import com.lumen.app.ui.components.AppIcon
import com.lumen.app.ui.components.GlassCard
import com.lumen.app.ui.components.LocalContainer
import com.lumen.app.ui.components.SectionTitle
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.Ink
import com.lumen.app.ui.theme.Mint
import com.lumen.app.ui.theme.Rose
import com.lumen.app.ui.theme.TextMuted

@Composable
fun SettingsScreen() {
    val container = LocalContainer.current
    val vm: SettingsViewModel = viewModel(factory = viewModelFactoryFor(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = state.settings
    var showTimePicker by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.exportData { text ->
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.onFailure {
                android.widget.Toast.makeText(
                    context,
                    "Export failed: ${it.message ?: "try again"}",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }.onSuccess {
                android.widget.Toast.makeText(
                    context,
                    "Backup saved.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (text == null) {
            android.widget.Toast.makeText(context, "Could not read that file.", android.widget.Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        vm.importData(text) { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0B1226), MaterialTheme.colorScheme.background)),
            ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 24.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { SectionTitle("Settings") }

        item {
            SectionTitle("Screen time access")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.SettingsInputAntenna,
                        null,
                        tint = if (state.usageAccessEnabled) Mint else Amber,
                        modifier = Modifier.width(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Usage access", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.usageAccessEnabled) {
                                "Active — Lumen sees your screen time"
                            } else {
                                "Needed before Lumen can track screen time"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!state.usageAccessEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber,
                            contentColor = com.lumen.app.ui.theme.Ink,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Open phone settings", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Find Lumen in the list and switch it on. Nothing leaves your phone.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        item {
            SectionTitle("Daily goal")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Text(
                    "${settings.dailyGoalMinutes} minutes of screen time",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = settings.dailyGoalMinutes.toFloat(),
                    onValueChange = { vm.setGoal(it.toInt()) },
                    valueRange = 30f..480f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = Amber,
                        activeTrackColor = Amber,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("30 min", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text("8 h", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                }
            }
        }

        item {
            SectionTitle("Focus sessions")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(15, 25, 50).forEach { minutes ->
                        val selected = settings.focusMinutes == minutes
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) Amber else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { vm.setFocus(minutes) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$minutes min",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) com.lumen.app.ui.theme.Ink else TextMuted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "The length of your focus ritual. Sessions land in your daily report.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionTitle("Notifications")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                SwitchRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Count notifications",
                    subtitle = if (state.notificationAccessEnabled) {
                        "Active — Lumen records how many arrive"
                    } else {
                        "Needs the notification listener permission"
                    },
                    checked = settings.notificationTracking,
                    onChecked = vm::setNotificationTracking,
                )
                if (settings.notificationTracking && !state.notificationAccessEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Android hides this behind \"restricted settings\". Three quick steps:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber,
                            contentColor = com.lumen.app.ui.theme.Ink,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("1 · Open Lumen's app page", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "2 · On that page tap ⋮ (top right) → \"Allow restricted settings\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("3 · Open notification access", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            SectionTitle("Appearance")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                SwitchRow(
                    icon = if (settings.darkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    title = "Dark theme",
                    subtitle = "Lumen was designed to glow in the dark",
                    checked = settings.darkTheme,
                    onChecked = vm::setDark,
                )
            }
        }

        item {
            SectionTitle("Daily limits")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                val limits = settings.appLimitsMinutes
                if (limits.isEmpty()) {
                    Text(
                        "No app limits yet. Set a daily cap on Instagram, YouTube, or any app — " +
                            "Lumen nudges you when you get close.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                } else {
                    limits.entries.forEachIndexed { i, (pkg, minutes) ->
                        LimitRow(
                            label = container.appInfo.label(pkg),
                            minutesPerDay = minutes,
                            onRemove = { vm.removeAppLimit(pkg) },
                        )
                        if (i < limits.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(6.dp))
                }
                AddLimitButton(state.candidateApps, settings.appLimitsMinutes, vm::setAppLimit)
            }
        }

        item {
            SectionTitle("Bedtime mode")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                SwitchRow(
                    icon = Icons.Rounded.NightsStay,
                    title = "Wind down",
                    subtitle = "After bedtime, Lumen reminds you to put the phone down",
                    checked = settings.bedtimeEnabled,
                    onChecked = { enabled ->
                        vm.setBedtime(enabled, settings.bedtimeHour, settings.bedtimeMinute)
                    },
                )
                if (settings.bedtimeEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Bedtime: ${timeLabel(settings.bedtimeHour, settings.bedtimeMinute)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showTimePicker = true }) {
                            Text("Change", color = Amber)
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Rest days")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Text(
                    "Your streak skips these days — a break doesn't break it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val days = listOf(
                    1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
                    5 to "Fri", 6 to "Sat", 7 to "Sun",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { (value, label) ->
                        val selected = value in settings.restDays
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Amber else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { vm.toggleRestDay(value) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Ink else TextMuted,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Weekend mode")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                SwitchRow(
                    icon = Icons.Rounded.LightMode,
                    title = "Relaxed weekends",
                    subtitle = "A gentler goal on Saturdays and Sundays",
                    checked = settings.weekendEnabled,
                    onChecked = vm::setWeekendMode,
                )
                if (settings.weekendEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Weekend goal: ${settings.weekendGoalMinutes} minutes",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Slider(
                        value = settings.weekendGoalMinutes.toFloat(),
                        onValueChange = { vm.setWeekendGoal(it.toInt()) },
                        valueRange = 30f..480f,
                        steps = 17,
                        colors = SliderDefaults.colors(
                            thumbColor = Amber,
                            activeTrackColor = Amber,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("30 min", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text("8 h", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    }
                }
            }
        }

        item {
            SectionTitle("Mindful extensions")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Text(
                    "Every time you take +15 minutes past a limit, it lands here — with a reason.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (state.extensions.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No extensions yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.extensions.take(10).forEachIndexed { i, (day, pkg, note) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                java.time.LocalDate.ofEpochDay(day).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                                modifier = Modifier.width(66.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    container.appInfo.label(pkg),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!note.isNullOrBlank()) {
                                    Text(
                                        note,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text("+15", style = MaterialTheme.typography.labelLarge, color = Amber)
                        }
                        if (i < minOf(state.extensions.size, 10) - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Declutter")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                val installed = state.installedCount
                val used = state.usedCount
                if (installed > 0) {
                    Text(
                        "You actually open $used of $installed installed apps in a typical week " +
                            "(${used * 100 / installed}%). The rest might be noise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (state.orphans.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No orphan apps — everything installed earns its place.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mint,
                        )
                    }
                } else {
                    Text(
                        "Not opened in 30 days:",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    state.orphans.take(8).forEachIndexed { i, orphan ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(orphan.packageName, 20.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                orphan.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { uninstallApp(context, orphan.packageName) }) {
                                Text("Uninstall", color = Rose)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Your data")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Text(
                    "Everything stays on this phone. Back it up or move it freely — " +
                        "a readable JSON file with 30 days of history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedButton(onClick = { exportLauncher.launch("lumen-export.json") }) {
                        Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export")
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                }
            }
        }

        if (!state.underTracking && state.candidateApps.isNotEmpty()) {
            item {
                SectionTitle(
                    "Ignored apps",
                    trailing = {
                        Text(
                            "${settings.excludedPackages.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                Spacer(Modifier.height(10.dp))
                GlassCard {
                    Text(
                        "Ignored apps don't count toward your screen time — " +
                            "useful for work tools or music players that stay on in the background.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    state.candidateApps.forEachIndexed { i, candidate ->
                        AppToggleRow(candidate, vm::toggleExcluded)
                        if (i < state.candidateApps.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = Mint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Private by design", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "All your usage data, focus sessions, and notification counts " +
                                "live only on this device. Lumen has no account, no cloud, no trackers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Lumen 1.0 — See your day in a new light.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showTimePicker) {
        var hour by remember { mutableStateOf(settings.bedtimeHour) }
        var minute by remember { mutableStateOf(settings.bedtimeMinute) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Set bedtime") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hour.toString(),
                        onValueChange = { hour = it.filter { c -> c.isDigit() }.take(2).toIntOrNull() ?: 0 },
                        label = { Text("Hour (0-23)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minute.toString(),
                        onValueChange = { minute = it.filter { c -> c.isDigit() }.take(2).toIntOrNull() ?: 0 },
                        label = { Text("Minute") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        vm.setBedtime(true, hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                    },
                ) { Text("Save", color = Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}

private fun timeLabel(hour: Int, minute: Int): String {
    val h = hour % 24
    val period = if (h < 12) "AM" else "PM"
    val display = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(display, minute, period)
}

@Composable
private fun LimitRow(label: String, minutesPerDay: Int, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.HourglassBottom,
            null,
            tint = Amber,
            modifier = Modifier.width(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            "$minutesPerDay min/day",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onRemove) {
            Text("Remove", color = TextMuted)
        }
    }
}

@Composable
private fun AddLimitButton(
    apps: List<AppCandidate>,
    limits: Map<String, Int>,
    onSet: (String, Int) -> Unit,
) {
    var showApps by remember { mutableStateOf(false) }
    var minutesFor by remember { mutableStateOf<String?>(null) }
    var minutesText by remember { mutableStateOf("") }

    OutlinedButton(
        onClick = { showApps = true },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) {
        Text("Add a limit", fontWeight = FontWeight.SemiBold, color = Amber)
    }

    val pickable = apps.filter { it.packageName !in limits }
    if (showApps) {
        AlertDialog(
            onDismissRequest = { showApps = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Choose an app") },
            text = {
                Column {
                    if (pickable.isEmpty()) {
                        Text(
                            "Every app already has a limit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        pickable.take(12).forEach { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        minutesFor = app.packageName
                                        minutesText = ""
                                        showApps = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApps = false }) { Text("Close") }
            },
        )
    }

    minutesFor?.let { pkg ->
        AlertDialog(
            onDismissRequest = { minutesFor = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Daily limit") },
            text = {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Minutes per day (e.g. 45)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minutes = minutesText.toIntOrNull()
                        if (minutes != null && minutes in 1..999) {
                            onSet(pkg, minutes)
                        }
                        minutesFor = null
                    },
                ) { Text("Save", color = Amber) }
            },
            dismissButton = {
                TextButton(onClick = { minutesFor = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Amber,
                checkedTrackColor = Amber.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun AppToggleRow(candidate: AppCandidate, onToggle: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(candidate.packageName) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(candidate.packageName, 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            candidate.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Switch(
            checked = candidate.excluded,
            onCheckedChange = { onToggle(candidate.packageName) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Amber,
                checkedTrackColor = Amber.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

private fun uninstallApp(context: android.content.Context, packageName: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName")),
        )
    }
}
