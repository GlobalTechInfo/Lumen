package com.lumen.app.domain

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
)

object Achievements {
    private val FIRST_GOAL = Achievement(
        "first_goal", "🪄", "First goal kept",
        "A full day stayed under your daily goal.",
    )
    private val GOAL_WEEK = Achievement(
        "goal_week", "🏆", "Goal week",
        "Seven days in a row all under your goal.",
    )
    private val STREAK_7 = Achievement(
        "streak_7", "🔥", "Week streak",
        "A seven-day streak.",
    )
    private val STREAK_30 = Achievement(
        "streak_30", "🌟", "Month streak",
        "A thirty-day streak.",
    )
    private val CALM_DAY = Achievement(
        "calm_day", "🍃", "Calm day",
        "A day with under 30 minutes of screen time.",
    )
    private val FOCUS_10 = Achievement(
        "focus_10", "🧘", "Ten quiet sessions",
        "Ten days with focused minutes to your name.",
    )
    private val NO_ALERTS = Achievement(
        "no_alerts", "🔕", "Quiet phone",
        "A full day with zero notification alerts.",
    )
    private val PICKUPS_10 = Achievement(
        "pickups_10", "🚫", "Hands off",
        "A day with fewer than ten pickups.",
    )

    val ALL: List<Achievement> = listOf(
        FIRST_GOAL,
        GOAL_WEEK,
        STREAK_7,
        STREAK_30,
        CALM_DAY,
        FOCUS_10,
        NO_ALERTS,
        PICKUPS_10,
    )

    fun evaluate(
        history: List<DayStats>,
        goalMinutes: Int,
        restDays: Set<Int>,
    ): Set<String> {
        if (history.isEmpty()) return emptySet()
        val goalMs = goalMinutes * 60_000L
        val underGoal = history.filter { it.screenTimeMs <= goalMs }
        val earned = mutableSetOf<String>()
        if (underGoal.isNotEmpty()) earned += FIRST_GOAL.id
        var run = 0
        var maxRun = 0
        for (day in history) {
            run = if (day.screenTimeMs <= goalMs) run + 1 else 0
            maxRun = maxOf(maxRun, run)
        }
        if (maxRun >= 7) earned += GOAL_WEEK.id
        val streak = StreakCalculator.currentStreak(history, goalMs, restDays)
        if (streak >= 7) earned += STREAK_7.id
        if (streak >= 30) earned += STREAK_30.id
        if (history.any { it.screenTimeMs <= 30 * 60_000L }) earned += CALM_DAY.id
        if (history.count { it.focusMinutes > 0 } >= 10) earned += FOCUS_10.id
        if (history.any { it.notificationCount == 0 }) earned += NO_ALERTS.id
        if (history.any { it.unlocks < 10 }) earned += PICKUPS_10.id
        return earned
    }
}
