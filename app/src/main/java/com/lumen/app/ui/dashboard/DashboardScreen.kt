package com.lumen.app.ui.dashboard

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumen.app.domain.TimeUtils
import com.lumen.app.ui.DashboardViewModel
import com.lumen.app.ui.viewModelFactoryFor
import com.lumen.app.ui.components.AppUsageRow
import com.lumen.app.ui.components.EmptyState
import com.lumen.app.ui.components.GlassCard
import com.lumen.app.ui.components.GlowRing
import com.lumen.app.ui.components.InsightRow
import com.lumen.app.ui.components.LocalContainer
import com.lumen.app.ui.components.MetricChip
import com.lumen.app.ui.components.PeriodBarChart
import com.lumen.app.ui.components.SectionTitle
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.Ink
import com.lumen.app.ui.theme.Ink
import com.lumen.app.ui.theme.Mint
import com.lumen.app.ui.theme.Periwinkle
import com.lumen.app.ui.theme.Rose
import com.lumen.app.ui.theme.TextMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    onOpenReports: () -> Unit,
    onAppClick: (String, String) -> Unit,
) {
    val container = LocalContainer.current
    val vm: DashboardViewModel = viewModel(factory = viewModelFactoryFor(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        vm.refresh()
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
        item { DashboardHeader(state.streak.currentDays) }

        if (state.loading && state.today == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber, strokeWidth = 3.dp)
                }
            }
        } else if (state.permissionMissing) {
            item {
                GlassCard {
                    EmptyState(
                        icon = Icons.Rounded.SettingsInputAntenna,
                        title = "Allow screen access",
                        body = "Lumen can't see your screen time until you switch it on " +
                            "in your phone settings. Everything stays on your phone.",
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber,
                            contentColor = Ink,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text("Open phone settings", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Find Lumen in the list and switch it on. You can also do this any time in Settings.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            state.limitNudges.forEach { nudge ->
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Rounded.Flag,
                                null,
                                tint = Amber,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                nudge,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (state.bedtimeActive) {
                item {
                    val quote = remember { sleepQuotes.random() }
                    GlassCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Rounded.NightsStay,
                                null,
                                tint = Periwinkle,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "It's past your bedtime (${bedtimeLabel(state.settings.bedtimeHour, state.settings.bedtimeMinute)}). " +
                                        "Tonight's screen time so far: " +
                                        "${TimeUtils.formatDuration(state.today?.screenTimeMs ?: 0L)}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "\"$quote\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Periwinkle,
                                )
                            }
                        }
                    }
                }
            }
            state.forecast?.let { forecast ->
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Rounded.Insights,
                                null,
                                tint = Amber,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                forecast,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            state.today?.let { today ->
                val goalMs = state.settings.dailyGoalMinutes * 60_000L
                item { TodayRing(today.screenTimeMs, goalMs, today) }

                item {
                    SectionTitle("This week")
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        PeriodBarChart(
                            days = state.week,
                            goalMinutes = state.settings.dailyGoalMinutes.toLong(),
                        )
                    }
                }

                item {
                    SectionTitle("Top apps", trailing = {
                        Text(
                            TimeUtils.formatDuration(today.screenTimeMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    })
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        val maxMs = (today.apps.maxOfOrNull { it.timeMs } ?: 1L).coerceAtLeast(1L)
                        if (today.apps.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.Timer,
                                title = "No apps yet today",
                                body = "Use your phone a little and your apps will appear here.",
                            )
                        } else {
                            today.apps.take(4).forEachIndexed { i, app ->
                                AppUsageRow(app, maxMs, onClick = { onAppClick(app.packageName, app.label) })
                                if (i < 3) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                item {
                    SectionTitle("Life report")
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        if (state.insights.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.HorizontalRule,
                                title = "Your first insights are brewing",
                                body = "After a couple of days, Lumen starts noticing patterns.",
                            )
                        } else {
                            state.insights.take(2).forEachIndexed { i, insight ->
                                InsightRow(insight)
                                if (i < 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                item {
                    SectionTitle("Achievements")
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        val earnedCount = com.lumen.app.domain.Achievements.ALL.count { it.id in state.achievements }
                        Text(
                            "$earnedCount of ${com.lumen.app.domain.Achievements.ALL.size} earned",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        com.lumen.app.domain.Achievements.ALL.forEachIndexed { i, achievement ->
                            val unlocked = achievement.id in state.achievements
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (unlocked) achievement.emoji else "🔒",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.width(30.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        achievement.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (unlocked) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            TextMuted
                                        },
                                    )
                                    Text(
                                        achievement.subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted,
                                    )
                                }
                            }
                            if (i < com.lumen.app.domain.Achievements.ALL.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                item {
                    var showExtendNote by remember { mutableStateOf(false) }
                    var extendNote by remember { mutableStateOf("") }
                    val pending = state.extensionPending
                    if (pending != null) {
                        GlassCard {
                            Text(
                                "You're past your ${state.settings.appLimitsMinutes[pending.packageName]} -minute limit on ${pending.label}.",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Extensions are mindful, not loopholes — they get logged in your diary.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row {
                                Button(
                                    onClick = { showExtendNote = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Amber,
                                        contentColor = Ink,
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("+15 mindful minutes")
                                }
                                Spacer(Modifier.width(10.dp))
                                TextButton(onClick = { vm.dismissExtension() }) {
                                    Text("No thanks", color = TextMuted)
                                }
                            }
                        }
                        if (showExtendNote) {
                            AlertDialog(
                                onDismissRequest = { showExtendNote = false },
                                title = { Text("Why +15 more?") },
                                text = {
                                    OutlinedTextField(
                                        value = extendNote,
                                        onValueChange = { extendNote = it },
                                        placeholder = { Text("e.g. finishing a work email…") },
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            vm.extendLimit(pending.packageName, extendNote)
                                            showExtendNote = false
                                            extendNote = ""
                                        },
                                    ) {
                                        Text("Extend", color = Amber)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showExtendNote = false }) {
                                        Text("Cancel", color = TextMuted)
                                    }
                                },
                            )
                        }
                    }
                }

                item {
                    if (state.patternShift) {
                        GlassCard {
                            Text(
                                "Pattern shift",
                                style = MaterialTheme.typography.titleMedium,
                                color = Rose,
                            )
                            Spacer(Modifier.height(4.dp))
                            val today = state.today
                            if (today != null) {
                                Text(
                                    "You're at ${TimeUtils.formatDuration(today.screenTimeMs)} today — " +
                                        "well past your usual rhythm at this hour. " +
                                        "Busy day, or a quiet spiral?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row {
                                Button(
                                    onClick = { vm.ackPatternShift() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Mint,
                                        contentColor = Ink,
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Just a busy day")
                                }
                                Spacer(Modifier.width(10.dp))
                                TextButton(onClick = { vm.ackPatternShift() }) {
                                    Text("Noted", color = TextMuted)
                                }
                            }
                        }
                    }
                }

                item {
                    val checkIn = state.vowCheckIn
                    if (checkIn != null) {
                        GlassCard {
                            Text(
                                "Today's vow: ${checkIn.ifEmpty { "no phone first" }}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "How did the morning go?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row {
                                Button(
                                    onClick = { vm.ackVow() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Mint,
                                        contentColor = Ink,
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Kept it")
                                }
                                Spacer(Modifier.width(10.dp))
                                TextButton(onClick = { vm.ackVow() }) {
                                    Text("Broke it — it happens", color = TextMuted)
                                }
                            }
                        }
                    }
                }

                item {
                    val lookAhead = state.vowLookAhead
                    if (lookAhead.isNotEmpty()) {
                        GlassCard {
                            Text(
                                "Nightly look-ahead",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "What will you open first tomorrow? A vow is a tiny promise to yourself.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                            lookAhead.forEach { app ->
                                OutlinedButton(
                                    onClick = { vm.makeVow(app.packageName) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                ) {
                                    Text(app.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            TextButton(
                                onClick = { vm.makeVow(null) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("No phone first", color = Mint)
                            }
                        }
                    }
                }

                item {
                    val mood = state.mood
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "How's your day?",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            listOf(
                                "great" to "Great",
                                "hard" to "Hard",
                                "mixed" to "Mixed",
                            ).forEach { (value, label) ->
                                val selected = mood == value
                                val tint = when (value) {
                                    "great" -> Mint
                                    "hard" -> Rose
                                    else -> Periwinkle
                                }
                                Box(
                                    Modifier
                                        .padding(start = 6.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) tint.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                        .clickable { vm.setMood(value) }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) tint else TextMuted,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val timeBank = state.timeBankMs
                    if (timeBank > 0L) {
                        val targetMs = 10L * 60L * 60_000L
                        val fraction = (timeBank.toFloat() / targetMs).coerceIn(0f, 1f)
                        GlassCard {
                            Text(
                                "Time bank",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "You've saved ${TimeUtils.formatDuration(timeBank)} this week — " +
                                    "every minute under your goal is yours again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(fraction)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Amber),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "toward a 10-hour pot",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted,
                                )
                                Text(
                                    "${(fraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Amber,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(streakDays: Int) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = greeting(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", locale)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (streakDays > 0) {
            Row(
                modifier = Modifier
                    .background(Mint.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    null,
                    tint = Mint,
                    modifier = Modifier.height(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "$streakDays-day",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mint,
                )
            }
        }
    }
}

@Composable
private fun TodayRing(screenTimeMs: Long, goalMs: Long, today: com.lumen.app.domain.DayStats) {
    val progress = if (goalMs > 0) (screenTimeMs.toFloat() / goalMs).coerceIn(0f, 1f) else 0f
    GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlowRing(progress = progress, size = 208.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = TimeUtils.formatHoursMinutes(screenTimeMs),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "of ${TimeUtils.formatDuration(goalMs)} goal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricChip(
                    icon = Icons.Rounded.TouchApp,
                    value = "${today.unlocks}",
                    label = "Pickups",
                    tint = Periwinkle,
                )
                MetricChip(
                    icon = Icons.Rounded.Notifications,
                    value = "${today.notificationCount}",
                    label = "Notifications",
                    tint = Amber,
                )
                MetricChip(
                    icon = Icons.Rounded.Schedule,
                    value = "${today.focusMinutes}",
                    label = "Focus min",
                    tint = Mint,
                )
            }
        }
    }
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night light"
    }
}

private fun bedtimeLabel(hour: Int, minute: Int): String {
    val h = hour % 24
    val period = if (h < 12) "AM" else "PM"
    val display = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(display, minute, period)
}

private val sleepQuotes = listOf(
    "The best bridge between despair and hope is a good night's sleep.",
    "Sleep is the golden chain that ties health and our bodies together.",
    "A good laugh and a long sleep are the best cures in the doctor's book.",
    "Each night, when I go to sleep, I die. And the next morning, when I wake up, I am reborn.",
    "Sleep is the best meditation.",
    "There is a time for many words, and there is also a time for sleep.",
    "Your future depends on your dreams, so go to sleep.",
    "The day will be what you make it, so rise and greet it with a smile — after a good night.",
    "Rest is not idleness, and to lie sometimes on the grass under trees on a summer's day is a habit to be envied.",
    "Man is a genius when he is dreaming.",
)
