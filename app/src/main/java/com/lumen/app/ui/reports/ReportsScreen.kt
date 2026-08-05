package com.lumen.app.ui.reports

import android.content.Context
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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumen.app.domain.AppUsage
import com.lumen.app.domain.DayStats
import com.lumen.app.domain.TimeUtils
import com.lumen.app.ui.ReportsViewModel
import com.lumen.app.ui.viewModelFactoryFor
import com.lumen.app.ui.components.AppUsageRow
import com.lumen.app.ui.components.EmptyState
import com.lumen.app.ui.components.GlassCard
import com.lumen.app.ui.components.InsightRow
import com.lumen.app.ui.components.LocalContainer
import com.lumen.app.ui.components.PeriodBarChart
import com.lumen.app.ui.components.SectionTitle
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.Mint
import com.lumen.app.ui.theme.Periwinkle
import com.lumen.app.ui.theme.Rose
import com.lumen.app.ui.theme.TextMuted

@Composable
fun ReportsScreen(onAppClick: (String, String) -> Unit) {
    val container = LocalContainer.current
    val vm: ReportsViewModel = viewModel(factory = viewModelFactoryFor(container))
    val state by vm.state.collectAsStateWithLifecycle()
    var monthMode by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    val period: List<DayStats> = if (monthMode) state.history.takeLast(30) else state.history.takeLast(7)
    val periodGoal: Int = period.lastOrNull()?.date
        ?.let { state.settings.dailyGoalFor(it) }
        ?: state.settings.dailyGoalMinutes

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
        item { SectionTitle("Reports") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentedPills(monthMode, Modifier.weight(1f)) { monthMode = it }
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = { shareCurrentReport(context, state.history, monthMode, periodGoal) }) {
                    Icon(Icons.Rounded.Share, null, tint = Amber)
                }
            }
        }

        when {
            state.loading && state.history.isEmpty() -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Amber, strokeWidth = 3.dp)
                    }
                }
            }
            state.permissionMissing -> {
                item {
                    GlassCard {
                        EmptyState(
                            icon = Icons.Rounded.SettingsInputAntenna,
                            title = "Screen access needed",
                            body = "Grant usage access to see your reports.",
                        )
                    }
                }
            }
            period.isEmpty() -> {
                item {
                    GlassCard {
                        EmptyState(
                            icon = Icons.Rounded.HorizontalRule,
                            title = "Nothing to report yet",
                            body = "Your charts fill in as you use your phone.",
                        )
                    }
                }
            }
            else -> {
                item {
                    GlassCard {
                        PeriodBarChart(
                            days = period,
                            goalMinutes = periodGoal.toLong(),
                            labelEvery = if (monthMode) 5 else 1,
                            barFill = if (monthMode) 0.32f else 0.55f,
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryTile(
                            label = "Average",
                            value = TimeUtils.formatDuration(period.map { it.screenTimeMs }.average().toLong()),
                            tint = Periwinkle,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryTile(
                            label = "Best day",
                            value = period.minByOrNull { it.screenTimeMs }?.let {
                                it.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }
                            } ?: "—",
                            tint = Mint,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryTile(
                            label = "Focus",
                            value = "${period.sumOf { it.focusMinutes }} min",
                            tint = Amber,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    val apps = aggregateApps(period)
                    SectionTitle(
                        "Top apps",
                        trailing = {
                            Text(
                                TimeUtils.formatDuration(period.sumOf { it.screenTimeMs }),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        val maxMs = (apps.maxOfOrNull { it.timeMs } ?: 1L).coerceAtLeast(1L)
                        apps.take(6).forEachIndexed { i, app ->
                            AppUsageRow(app, maxMs, onClick = { onAppClick(app.packageName, app.label) })
                            if (i < 5) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                item {
                    if (!monthMode && state.hourly.isNotEmpty()) {
                        SectionTitle("Usage heatmap")
                        Spacer(Modifier.height(10.dp))
                        GlassCard {
                            val maxMinutes = state.hourly.values.maxOfOrNull { row -> row.max() ?: 0 } ?: 0
                            Column {
                                val days = state.hourly.keys
                                days.forEach { date ->
                                    val buckets = state.hourly[date] ?: return@forEach
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextMuted,
                                            modifier = Modifier.width(38.dp),
                                        )
                                        for (h in 0..23) {
                                            val minutes = buckets[h]
                                            val alpha = if (maxMinutes > 0) {
                                                (minutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
                                            } else {
                                                0f
                                            }
                                            Box(
                                                Modifier
                                                    .weight(1f)
                                                    .height(16.dp)
                                                    .padding(horizontal = 1.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(
                                                        if (minutes > 0) {
                                                            Amber.copy(alpha = 0.15f + 0.85f * alpha)
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Low",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "High",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    if (state.weekComparison.isNotEmpty() && !monthMode) {
                        SectionTitle("vs last week")
                        Spacer(Modifier.height(10.dp))
                        GlassCard {
                            state.weekComparison.forEachIndexed { i, row ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        row.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        row.value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (row.better) Mint else Rose,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (i < state.weekComparison.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }

                item {
                    val offenders = if (monthMode) state.offendersMonth else state.offendersWeek
                    if (offenders.isNotEmpty()) {
                        SectionTitle("Notification offenders")
                        Spacer(Modifier.height(10.dp))
                        GlassCard {
                            offenders.take(6).forEachIndexed { i, app ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${app.notifications} pings",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (app.notifications > 50) Rose else TextMuted,
                                    )
                                }
                                if (i < 5) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                item {
                    SectionTitle("Insights")
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        if (state.insights.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.HorizontalRule,
                                title = "Insights are brewing",
                                body = "Give Lumen a few days to find your patterns.",
                            )
                        } else {
                            state.insights.forEachIndexed { i, insight ->
                                InsightRow(insight)
                                if (i < state.insights.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareCurrentReport(
    context: Context,
    history: List<DayStats>,
    monthMode: Boolean,
    goalMinutes: Int,
) {
    val days = if (monthMode) history.takeLast(30) else history.takeLast(7)
    if (days.isEmpty()) return
    val today = days.last()
    val title = if (monthMode) "Lumen · Monthly report" else "Lumen · Weekly report"
    val subtitle = "${days.first().date} — ${today.date}"
    val streak = com.lumen.app.domain.StreakCalculator.currentStreak(days, goalMinutes.toLong())
    val bitmap = ReportImageRenderer.render(title, subtitle, days, goalMinutes, streak)
    shareReport(context, bitmap, title)
}

@Composable
private fun SegmentedPills(
    monthMode: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        listOf("Week" to false, "Month" to true).forEach { (label, mode) ->
            val selected = monthMode == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Amber else Color.Transparent)
                    .clickable { onChange(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) com.lumen.app.ui.theme.Ink else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun aggregateApps(days: List<DayStats>): List<AppUsage> {
    val map = LinkedHashMap<String, AppUsage>()
    for (day in days) {
        for (app in day.apps) {
            val existing = map[app.packageName]
            if (existing == null) {
                map[app.packageName] = app.copy(notifications = app.notifications)
            } else {
                map[app.packageName] = existing.copy(
                    timeMs = existing.timeMs + app.timeMs,
                    notifications = existing.notifications + app.notifications,
                )
            }
        }
    }
    return map.values.sortedByDescending { it.timeMs }
}
