package com.lumen.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightEngineTest {

    private val goalMinutes = 180L

    private fun day(
        date: LocalDate,
        screenMinutes: Long,
        unlocks: Int = 0,
        notifications: Int = 0,
        focusMinutes: Int = 0,
        apps: List<AppUsage> = emptyList(),
    ) = DayStats(
        date = date,
        screenTimeMs = screenMinutes * 60_000,
        unlocks = unlocks,
        notificationCount = notifications,
        focusMinutes = focusMinutes,
        apps = apps,
    )

    @Test
    fun emptyHistoryProducesNoInsights() {
        assertTrue(InsightEngine.generate(emptyList(), goalMinutes).isEmpty())
    }

    @Test
    fun threeDayStreakProducesStreakInsight() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(2), 100),
            day(today.minusDays(1), 110),
            day(today, 80),
        )
        val insights = InsightEngine.generate(days, goalMinutes)
        assertTrue(insights.any { it.icon == InsightIcon.STREAK })
    }

    @Test
    fun overGoalYesterdayProducesWarning() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(1), 300),
            day(today, 30),
        )
        val insights = InsightEngine.generate(days, goalMinutes)
        assertTrue(insights.any { it.icon == InsightIcon.WARNING })
    }

    @Test
    fun veryLightDayProducesPraise() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(1), 60),
            day(today, 30),
        )
        val insights = InsightEngine.generate(days, goalMinutes)
        assertTrue(insights.any { it.icon == InsightIcon.PRAISE })
    }

    @Test
    fun dominantAppProducesAppsInsight() {
        val today = LocalDate.now()
        val days = listOf(
            day(
                today.minusDays(1),
                screenMinutes = 200,
                apps = listOf(
                    AppUsage("com.insta", "Instagram", 150 * 60_000, 0, 300f),
                    AppUsage("com.spotify", "Spotify", 50 * 60_000, 0, 150f),
                ),
            ),
            day(today, 30),
        )
        val insights = InsightEngine.generate(days, goalMinutes)
        assertTrue(insights.any { it.icon == InsightIcon.APPS })
    }

    @Test
    fun notificationOverloadProducesNotificationInsight() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(1), 120, notifications = 140),
            day(today, 30),
        )
        val insights = InsightEngine.generate(days, goalMinutes)
        assertTrue(insights.any { it.icon == InsightIcon.NOTIFICATIONS })
    }

    @Test
    fun focusWeekProducesFocusInsight() {
        val today = LocalDate.now()
        val days = listOf(
            day(today.minusDays(2), 120, focusMinutes = 25),
            day(today.minusDays(1), 120, focusMinutes = 25),
            day(today, 30),
        )
        val insights = InsightEngine.generate(days, goalMinutes)
        assertTrue(insights.any { it.icon == InsightIcon.FOCUS })
    }

    @Test
    fun insightListIsCappedAtFive() {
        val today = LocalDate.now()
        val days = (4 downTo 0).map { offset ->
            day(
                today.minusDays(offset.toLong()),
                screenMinutes = 100,
                unlocks = 60,
                notifications = 150,
                focusMinutes = 25,
                apps = listOf(AppUsage("com.a", "A", 80 * 60_000, 0, 10f)),
            )
        }
        val insights = InsightEngine.generate(days, goalMinutes)
        assertTrue(insights.size <= 5)
    }
}
