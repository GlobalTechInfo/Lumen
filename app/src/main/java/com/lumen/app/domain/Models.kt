package com.lumen.app.domain

import java.time.LocalDate

enum class InsightIcon { TIME, PICKUPS, APPS, NOTIFICATIONS, STREAK, FOCUS, PRAISE, WARNING }

data class AppUsage(
    val packageName: String,
    val label: String,
    val timeMs: Long,
    val notifications: Int,
    val hue: Float,
)

data class DayStats(
    val date: LocalDate,
    val screenTimeMs: Long,
    val unlocks: Int,
    val notificationCount: Int,
    val focusMinutes: Int,
    val apps: List<AppUsage>,
) {
    val isComplete: Boolean get() = date.isBefore(LocalDate.now())
}

data class Insight(
    val icon: InsightIcon,
    val title: String,
    val body: String,
)

data class FocusSession(
    val id: Long,
    val startEpochMs: Long,
    val endEpochMs: Long?,
    val targetMinutes: Int,
    val completed: Boolean,
) {
    fun elapsedMs(now: Long): Long = (endEpochMs ?: now) - startEpochMs
}

data class AppDailyPoint(
    val date: LocalDate,
    val timeMs: Long,
)

data class AppTimelineSlice(
    val startMs: Long,
    val endMs: Long,
)

data class StreakInfo(
    val currentDays: Int,
    val bestDays: Int,
)

object Palette {
    private val hues = floatArrayOf(
        38f, 168f, 215f, 280f, 330f, 95f, 20f, 200f, 60f, 250f,
        145f, 320f, 180f, 5f, 230f, 110f, 290f, 75f, 350f, 160f,
    )

    fun hueFor(packageName: String): Float {
        val h = packageName.hashCode()
        return hues[((h % hues.size) + hues.size) % hues.size]
    }
}
