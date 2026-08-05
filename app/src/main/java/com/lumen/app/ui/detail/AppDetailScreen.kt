package com.lumen.app.ui.detail

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumen.app.domain.AppDailyPoint
import com.lumen.app.domain.AppTimelineSlice
import com.lumen.app.domain.Palette
import com.lumen.app.domain.TimeUtils
import com.lumen.app.ui.components.AppIcon
import com.lumen.app.ui.components.EmptyState
import com.lumen.app.ui.components.GlassCard
import com.lumen.app.ui.components.LocalContainer
import com.lumen.app.ui.components.SectionTitle
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.TextFaint
import java.time.LocalDate

@Composable
fun AppDetailScreen(
    packageName: String,
    label: String,
    onBack: () -> Unit,
) {
    val container = LocalContainer.current
    var week by remember { mutableStateOf<List<AppDailyPoint>?>(null) }
    var timeline by remember { mutableStateOf<List<AppTimelineSlice>?>(null) }

    LaunchedEffect(packageName) {
        val settings = container.currentSettings.value
        val from = if (settings.installedEpochDay > 0) {
            LocalDate.ofEpochDay(settings.installedEpochDay)
        } else {
            TimeUtils.today().minusDays(13)
        }
        week = container.usageRepository.appHistory(packageName, from)
        timeline = container.usageRepository.dayTimeline(packageName, TimeUtils.today())
    }

    val hue = Palette.hueFor(packageName)
    val accent = Color.hsv(hue, 0.72f, 1f)
    val todayMs = week?.lastOrNull()?.timeMs ?: 0L

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0B1226), MaterialTheme.colorScheme.background)),
            )
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(4.dp))
                AppIcon(packageName, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        "${TimeUtils.formatDuration(todayMs)} today",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                }
            }
        }

        item {
            SectionTitle("Today's timeline")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                val slices = timeline
                if (slices.isNullOrEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.HorizontalRule,
                        title = "Not used today",
                        body = "Open this app for a bit and its moments will show here.",
                    )
                } else {
                    DayTimeline(slices)
                }
            }
        }

        item {
            SectionTitle("Recent days")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                val points = week
                if (points.isNullOrEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.HorizontalRule,
                        title = "No history yet",
                        body = "This chart fills in over the coming days.",
                    )
                } else {
                    TrendChart(points, accent)
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(slices: List<AppTimelineSlice>) {
    val dayStart = TimeUtils.startOfDay(TimeUtils.today())
    val dayEnd = TimeUtils.endOfDay(TimeUtils.today())
    val range = (dayEnd - dayStart).toFloat()
    val tint = Color.hsv(Palette.hueFor("com.lumen.app"), 0.0f, 1f)
    Column {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val barY = size.height - 34.dp.toPx()
            val barH = 10.dp.toPx()
            drawRoundRect(
                color = Color(0xFF1A2440),
                topLeft = Offset(0f, barY),
                size = Size(size.width, barH),
                cornerRadius = CornerRadius(barH / 2),
            )
            for (slice in slices) {
                val startX = ((slice.startMs - dayStart).toFloat() / range) * size.width
                val endX = ((slice.endMs - dayStart).toFloat() / range) * size.width
                val w = (endX - startX).coerceAtLeast(3f)
                drawRoundRect(
                    color = Amber,
                    topLeft = Offset(startX, barY),
                    size = Size(w, barH),
                    cornerRadius = CornerRadius(barH / 2),
                )
            }
            for (h in 0..4) {
                val x = h / 4f * size.width
                drawLine(
                    color = TextFaint.copy(alpha = 0.5f),
                    start = Offset(x, barY + barH + 8.dp.toPx()),
                    end = Offset(x, barY + barH + 20.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12a", "6a", "12p", "6p", "12a").forEach { t ->
                Text(
                    t,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextFaint,
                )
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<AppDailyPoint>, accent: Color) {
    val maxMs = points.maxOfOrNull { it.timeMs }?.coerceAtLeast(1L) ?: 1L
    val today = TimeUtils.today()
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val step = size.width / points.size
            val barW = (step * 0.55f).coerceAtMost(18.dp.toPx())
            points.forEachIndexed { i, point ->
                val h = (point.timeMs.toFloat() / maxMs) * (size.height - 6.dp.toPx())
                val x = i * step + (step - barW) / 2
                val y = size.height - h
                val isToday = point.date == today
                drawRoundRect(
                    color = if (isToday) Amber else accent.copy(alpha = 0.55f),
                    topLeft = Offset(x, y),
                    size = Size(barW, h.coerceAtLeast(3.dp.toPx())),
                    cornerRadius = CornerRadius(barW / 2, barW / 2),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("14 days ago", style = MaterialTheme.typography.labelMedium, color = TextFaint)
            Text("Today", style = MaterialTheme.typography.labelMedium, color = TextFaint)
        }
    }
}
