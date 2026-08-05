package com.lumen.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumen.app.LumenApp
import com.lumen.app.domain.StreakCalculator
import com.lumen.app.domain.TimeUtils
import java.time.LocalDate

class SummaryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LumenApp).container
        val settings = container.currentSettings.value
        val repo = container.usageRepository
        repo.persistDay(TimeUtils.today(), settings.excludedPackages, settings.notificationTracking)
        repo.persistDay(TimeUtils.today().minusDays(1), settings.excludedPackages, settings.notificationTracking)
        repo.purgeOldNotificationEvents()
        if (TimeUtils.today().dayOfWeek == java.time.DayOfWeek.MONDAY) {
            postWeeklyRecap(container, settings.excludedPackages, settings.notificationTracking, settings)
        }
        return Result.success()
    }

    private suspend fun postWeeklyRecap(
        container: AppContainer,
        excluded: Set<String>,
        trackNotifications: Boolean,
        settings: com.lumen.app.data.prefs.LumenSettings,
    ) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val today = TimeUtils.today()
        val weekStart = today.minusDays(7)
        val week = container.usageRepository.history(weekStart, excluded, trackNotifications)
            .filter { it.date in weekStart..today.minusDays(1) }
        if (week.isEmpty()) return
        val totalMs = week.sumOf { it.screenTimeMs }
        val bestDay = week.minByOrNull { it.screenTimeMs }
        val worstDay = week.maxByOrNull { it.screenTimeMs }
        val topApp = week.flatMap { it.apps }
            .groupBy { it.packageName }
            .maxByOrNull { (_, apps) -> apps.sumOf { it.timeMs } }
            ?.let { (_, apps) -> apps.maxByOrNull { it.timeMs }?.label }
        val streak = StreakCalculator.currentStreak(week, settings.dailyGoalFor(weekStart).toLong(), settings.restDays)
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val bestLine = bestDay?.let { "· best day ${it.date.dayOfWeek.name.lowercase().take(3)} (${fmt(it.screenTimeMs)})" }
        val worstLine = worstDay?.let { "· heaviest ${it.date.dayOfWeek.name.lowercase().take(3)} (${fmt(it.screenTimeMs)})" }
        val streakLine = if (streak > 0) "· $streak-day streak" else null
        val notification = NotificationCompat.Builder(applicationContext, "lumen_recap")
            .setSmallIcon(com.lumen.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("Last week in review")
            .setContentText(
                buildString {
                    append("You used ")
                    append(if (hours > 0) "$hours h " else "")
                    append("$minutes min on screens")
                    listOfNotNull(bestLine, worstLine, streakLine).forEach { append(" $it") }
                },
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append("Last week (${weekStart.dayOfWeek.name.lowercase().take(3)}–sun):\n")
                        append("Total: ${fmt(totalMs)}\n")
                        bestDay?.let { append("Best day: ${it.date.dayOfWeek.name} — ${fmt(it.screenTimeMs)}\n") }
                        worstDay?.let { append("Heaviest day: ${it.date.dayOfWeek.name} — ${fmt(it.screenTimeMs)}\n") }
                        topApp?.let { append("Most used: $it\n") }
                        append("Streak: $streak day(s) under goal")
                    },
                ),
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(4001, notification)
    }

    private fun fmt(ms: Long): String {
        val h = ms / 3_600_000L
        val m = (ms % 3_600_000L) / 60_000L
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
