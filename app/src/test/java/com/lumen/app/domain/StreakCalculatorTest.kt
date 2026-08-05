package com.lumen.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private val goalMinutes = 180L

    private fun day(
        date: LocalDate,
        screenMinutes: Long,
    ) = DayStats(
        date = date,
        screenTimeMs = screenMinutes * 60_000,
        unlocks = 0,
        notificationCount = 0,
        focusMinutes = 0,
        apps = emptyList(),
    )

    @Test
    fun emptyHistoryHasNoStreak() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), goalMinutes))
        assertEquals(0, StreakCalculator.bestStreak(emptyList(), goalMinutes))
    }

    @Test
    fun countsConsecutiveUnderGoalDays() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(2), 100),
            day(today.minusDays(1), 120),
            day(today, 90),
        )
        assertEquals(3, StreakCalculator.currentStreak(days, goalMinutes))
    }

    @Test
    fun streakBreaksOnOverGoalDay() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(3), 100),
            day(today.minusDays(2), 260),
            day(today.minusDays(1), 120),
            day(today, 90),
        )
        assertEquals(2, StreakCalculator.currentStreak(days, goalMinutes))
    }

    @Test
    fun streakStopsBeforeGapInHistory() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(2), 100),
            day(today, 90),
        )
        assertEquals(1, StreakCalculator.currentStreak(days, goalMinutes))
    }

    @Test
    fun bestStreakIgnoresGaps() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(5), 100),
            day(today.minusDays(4), 100),
            day(today.minusDays(3), 100),
            day(today.minusDays(2), 300),
            day(today.minusDays(1), 100),
        )
        assertEquals(3, StreakCalculator.bestStreak(days, goalMinutes))
    }

    @Test
    fun todayOverGoalBreaksCurrentStreakImmediately() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(2), 100),
            day(today.minusDays(1), 120),
            day(today, 190),
        )
        assertEquals(0, StreakCalculator.currentStreak(days, goalMinutes))
    }

    @Test
    fun restDaysSkipOverGoalDay() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(2), 100),
            day(today.minusDays(1), 100),
            day(today, 90),
        )
        val restDay = today.minusDays(1).dayOfWeek.value
        assertEquals(2, StreakCalculator.currentStreak(days, goalMinutes, setOf(restDay)))
    }

    @Test
    fun restDayDoesNotCountTowardStreak() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(1), 100),
            day(today, 90),
        )
        val restDay = today.dayOfWeek.value
        assertEquals(1, StreakCalculator.currentStreak(days, goalMinutes, setOf(restDay)))
    }
}
