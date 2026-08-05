package com.lumen.app.ui.focus

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumen.app.domain.FocusSession
import com.lumen.app.domain.TimeUtils
import com.lumen.app.ui.FocusViewModel
import com.lumen.app.ui.viewModelFactoryFor
import com.lumen.app.ui.components.GlassCard
import com.lumen.app.ui.components.GlowRing
import com.lumen.app.ui.components.LocalContainer
import com.lumen.app.ui.components.SectionTitle
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.Ink
import com.lumen.app.ui.theme.Mint
import com.lumen.app.ui.theme.Rose
import com.lumen.app.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FocusScreen() {
    val container = LocalContainer.current
    val vm: FocusViewModel = viewModel(factory = viewModelFactoryFor(container))
    val state by vm.state.collectAsStateWithLifecycle()

    val totalSeconds = state.settings.focusMinutes * 60L
    val progress = if (totalSeconds > 0) {
        (totalSeconds - state.remainingSeconds).toFloat() / totalSeconds
    } else 0f

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
        item { SectionTitle("Focus ritual") }
        item {
            GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlowRing(progress = progress, size = 220.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatClock(state.remainingSeconds),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = if (state.sessionId != null) "focusing" else "${state.settings.focusMinutes} minute session",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.sessionId == null) {
                            PrimaryFocusButton("Start", Icons.Rounded.PlayArrow) { vm.start() }
                        } else {
                            if (state.running) {
                                PrimaryFocusButton("Pause", Icons.Rounded.Pause) { vm.pause() }
                            } else {
                                PrimaryFocusButton("Resume", Icons.Rounded.PlayArrow) { vm.resume() }
                            }
                            Spacer(Modifier.width(12.dp))
                            IconButton(
                                onClick = { vm.abandon() },
                                modifier = Modifier
                                    .background(Rose.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                    .padding(10.dp),
                            ) {
                                Icon(Icons.Rounded.RestartAlt, "Give up", tint = Rose)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Info,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Every finished session is recorded in your daily report " +
                                "as focus minutes — the most valuable time you log.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            SectionTitle("Step away")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                var endsAt by androidx.compose.runtime.remember { mutableStateOf<Long?>(null) }
                var done by androidx.compose.runtime.remember { mutableStateOf(false) }
                var now by androidx.compose.runtime.remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(endsAt) {
                    while (true) {
                        val end = endsAt ?: break
                        now = System.currentTimeMillis()
                        if (now >= end) {
                            done = true
                            endsAt = null
                        }
                        kotlinx.coroutines.delay(500)
                    }
                }
                if (done) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(
                            "Done — your phone survived.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Mint,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The apps waited. You didn't.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { done = false }) {
                            Text("Close", color = TextMuted)
                        }
                    }
                } else if (endsAt != null) {
                    val remaining = ((endsAt!! - now) / 1000).coerceAtLeast(0)
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatClock(remaining),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "no-phone challenge running",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { endsAt = null }) {
                            Text("Cancel", color = Rose)
                        }
                    }
                } else {
                    Text(
                        "Put the phone down for a while. No tracking, no streaks — just distance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(5, 15, 30, 60).forEach { minutes ->
                            Button(
                                onClick = {
                                    done = false
                                    now = System.currentTimeMillis()
                                    endsAt = now + minutes * 60_000L
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Mint.copy(alpha = 0.14f),
                                    contentColor = Mint,
                                ),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("${minutes}m")
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(
                "Today",
                trailing = {
                    Text(
                        "${state.todaySessions.count { it.completed }} sessions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Spacer(Modifier.height(10.dp))
            GlassCard {
                if (state.todaySessions.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No sessions yet today. Your first one takes 25 minutes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.todaySessions.forEachIndexed { i, session ->
                        SessionRow(session)
                        if (i < state.todaySessions.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(
                    if (session.completed) Mint.copy(alpha = 0.12f) else Rose.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp),
                )
                .padding(8.dp),
        ) {
            Icon(
                if (session.completed) Icons.Rounded.Check else Icons.Rounded.RestartAlt,
                null,
                tint = if (session.completed) Mint else Rose,
                modifier = Modifier.width(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (session.completed) "Completed" else "Abandoned",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                SimpleDateFormat("h:mm a", locale).format(Date(session.startEpochMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${session.targetMinutes}m goal",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrimaryFocusButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(52.dp),
    ) {
        Icon(icon, null, modifier = Modifier.width(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
