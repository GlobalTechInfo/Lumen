package com.lumen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.app.domain.DayStats
import com.lumen.app.domain.TimeUtils
import com.lumen.app.ui.theme.Amber
import com.lumen.app.ui.theme.AmberSoft
import com.lumen.app.ui.theme.GlowAmber
import com.lumen.app.ui.theme.Mint
import com.lumen.app.ui.theme.Rose
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GlowRing(
    progress: Float,
    size: Dp = 216.dp,
    strokeWidth: Dp = 14.dp,
    content: @Composable () -> Unit = {},
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val radius = (size.toPx() - stroke) / 2
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            drawCircle(
                brush = Brush.radialGradient(listOf(GlowAmber, Color.Transparent)),
                radius = radius + stroke * 1.7f,
                center = center,
            )
            drawArc(
                color = Color(0xFF1A2440),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Amber, AmberSoft, Mint, Amber),
                        center = center,
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun PeriodBarChart(
    days: List<DayStats>,
    goalMinutes: Long,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1,
    barFill: Float = 0.55f,
) {
    if (days.isEmpty()) {
        Box(modifier.height(120.dp), contentAlignment = Alignment.Center) {
            Text(
                "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val goalMs = goalMinutes * 60_000f
    val maxMs = maxOf(goalMs, days.maxOfOrNull { it.screenTimeMs }?.toFloat() ?: 1f)
    val today = TimeUtils.today()
    Column(modifier) {
        val goalLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val step = size.width / days.size
            val barW = step * barFill
            val topPad = 8.dp.toPx()
            val usable = size.height - topPad
            val goalY = usable - (goalMs / maxMs) * usable + topPad
            repeat(4) { i ->
                val y = topPad + (usable - topPad) * (i + 1) / 4
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawLine(
                color = goalLineColor,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
            days.forEachIndexed { i, day ->
                val h = (day.screenTimeMs.toFloat() / maxMs) * usable
                val x = i * step + (step - barW) / 2
                val y = usable - h + topPad
                val isToday = day.date == today
                val over = day.screenTimeMs > goalMs
                val base = when {
                    isToday -> Amber
                    over -> Rose.copy(alpha = 0.75f)
                    else -> Color(0xFF2C3A5E)
                }
                val brush = Brush.verticalGradient(listOf(base, base.copy(alpha = 0.45f)))
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, y),
                    size = Size(barW, h.coerceAtLeast(12.dp.toPx())),
                    cornerRadius = CornerRadius(barW / 2, barW / 2),
                )
                if (isToday) {
                    drawCircle(
                        color = GlowAmber,
                        radius = barW * 1.4f,
                        center = Offset(x + barW / 2, y),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, day ->
                val showLabel = labelEvery == 1 || i % labelEvery == 0 || day.date == today
                Text(
                    text = if (showLabel) dayLabel(day.date) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day.date == today) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (day.date == today) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(Amber)
            Text(
                "Today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegendDot(Rose.copy(alpha = 0.75f))
            Text(
                "Over goal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.weight(1f))
            Text(
                "- - - goal",
                style = MaterialTheme.typography.labelMedium,
                color = goalLineColor,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

private fun dayLabel(date: java.time.LocalDate): String = when (date) {
    TimeUtils.today() -> "Now"
    TimeUtils.today().minusDays(1) -> "Yst"
    else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())).take(3)
}
