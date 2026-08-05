package com.lumen.app.domain

import java.time.LocalDate

object InsightEngine {
    fun generate(
        days: List<DayStats>,
        goalMinutes: Long,
    ): List<Insight> {
        if (days.isEmpty()) return emptyList()
        val insights = mutableListOf<Insight>()
        val today = LocalDate.now()
        val goalMs = goalMinutes * 60_000L
        val yesterday = days.firstOrNull { it.date == today.minusDays(1) }

        val streak = StreakCalculator.currentStreak(days, goalMinutes)
        if (streak >= 3) {
            insights += Insight(
                InsightIcon.STREAK,
                "$streak-day streak",
                "You've stayed under your goal $streak days in a row. The light is staying on.",
            )
        }

        yesterday?.let { y ->
            if (y.screenTimeMs > 0) {
                val ratio = y.screenTimeMs.toDouble() / goalMs
                if (y.isComplete && ratio > 1.2) {
                    insights += Insight(
                        InsightIcon.WARNING,
                        "Over your goal",
                        "Yesterday you used ${TimeUtils.formatDuration(y.screenTimeMs)} — " +
                            "${(ratio * 100).toInt()}% of your daily allowance.",
                    )
                } else if (y.isComplete && ratio < 0.6) {
                    insights += Insight(
                        InsightIcon.PRAISE,
                        "A light day",
                        "Yesterday you used just ${TimeUtils.formatDuration(y.screenTimeMs)}. Your focus is showing.",
                    )
                }
                val top = y.apps.firstOrNull()
                if (top != null && y.screenTimeMs > 60_000) {
                    val share = top.timeMs.toDouble() / y.screenTimeMs
                    if (share > 0.35) {
                        insights += Insight(
                            InsightIcon.APPS,
                            "${top.label} dominates",
                            "${top.label} took ${(share * 100).toInt()}% of yesterday's screen time.",
                        )
                    }
                }
                if (y.unlocks > 15 && y.screenTimeMs > 60_000) {
                    val per = y.screenTimeMs / 60000 / y.unlocks
                    insights += Insight(
                        InsightIcon.PICKUPS,
                        "Frequent pickups",
                        "You picked up your phone ${y.unlocks} times yesterday — every $per minutes or so.",
                    )
                }
            }
            if (y.notificationCount > 100) {
                insights += Insight(
                    InsightIcon.NOTIFICATIONS,
                    "Notification overload",
                    "${y.notificationCount} notifications pulled at you yesterday.",
                )
            } else if (y.isComplete && y.notificationCount in 1..10) {
                insights += Insight(
                    InsightIcon.PRAISE,
                    "A quiet phone",
                    "Only ${y.notificationCount} notifications yesterday. Beautifully quiet.",
                )
            }
        }

        val week = days.filter { it.date >= today.minusDays(7) }
        if (week.size >= 3 && week.first().date == today) {
            val past = week.drop(1)
            val avg = past.map { it.screenTimeMs }.average().toLong()
            val todayStats = week.first()
            if (avg > 0 && todayStats.screenTimeMs in 1..avg) {
                insights += Insight(
                    InsightIcon.PRAISE,
                    "Ahead of your week",
                    "So far today you're ${TimeUtils.formatDuration(avg - todayStats.screenTimeMs)} below your weekly average.",
                )
            }
        }

        val weekFocus = week.sumOf { it.focusMinutes }
        if (weekFocus >= 30) {
            insights += Insight(
                InsightIcon.FOCUS,
                "$weekFocus focused minutes",
                "Your focus sessions this week add up to $weekFocus minutes of intentional work.",
            )
        }

        return insights.take(5)
    }
}
