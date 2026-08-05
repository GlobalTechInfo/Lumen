package com.lumen.app.domain

object StreakCalculator {
    fun currentStreak(days: List<DayStats>, goalMinutes: Long, restDays: Set<Int> = emptySet()): Int {
        if (days.isEmpty()) return 0
        val goalMs = goalMinutes * 60_000L
        val byDate = days.associateBy { it.date }
        var streak = 0
        var d = byDate.keys.max()
        while (true) {
            val day = byDate[d] ?: break
            if (day.date.dayOfWeek.value in restDays) {
                d = d.minusDays(1)
                continue
            }
            if (day.screenTimeMs > goalMs) break
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    fun bestStreak(days: List<DayStats>, goalMinutes: Long, restDays: Set<Int> = emptySet()): Int {
        if (days.isEmpty()) return 0
        val goalMs = goalMinutes * 60_000L
        val sorted = days.sortedBy { it.date }
        var best = 0
        var run = 0
        var lastDate: java.time.LocalDate? = null
        for (day in sorted) {
            if (day.date.dayOfWeek.value in restDays) {
                lastDate = day.date
                continue
            }
            if (day.screenTimeMs > goalMs) {
                run = 0
            } else {
                run = if (lastDate == null || day.date == lastDate.plusDays(1)) run + 1 else 1
            }
            lastDate = day.date
            if (run > best) best = run
        }
        return best
    }

    fun info(days: List<DayStats>, goalMinutes: Long, restDays: Set<Int> = emptySet()): StreakInfo =
        StreakInfo(currentStreak(days, goalMinutes, restDays), bestStreak(days, goalMinutes, restDays))
}
