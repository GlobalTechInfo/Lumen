package com.lumen.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumen.app.data.AppContainer
import com.lumen.app.data.prefs.LumenSettings
import com.lumen.app.domain.AppUsage
import com.lumen.app.domain.DayStats
import com.lumen.app.domain.Insight
import com.lumen.app.domain.InsightEngine
import com.lumen.app.domain.StreakCalculator
import com.lumen.app.domain.StreakInfo
import com.lumen.app.domain.TimeUtils
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardState(
    val settings: LumenSettings = LumenSettings(),
    val loading: Boolean = true,
    val permissionMissing: Boolean = false,
    val today: DayStats? = null,
    val week: List<DayStats> = emptyList(),
    val streak: StreakInfo = StreakInfo(0, 0),
    val insights: List<Insight> = emptyList(),
    val limitNudges: List<String> = emptyList(),
    val bedtimeActive: Boolean = false,
    val forecast: String? = null,
    val achievements: Set<String> = emptySet(),
    val mood: String? = null,
    val extensionPending: AppUsage? = null,
    val extendedToday: Set<String> = emptySet(),
    val vowLookAhead: List<AppUsage> = emptyList(),
    val vowCheckIn: String? = null,
    val patternShift: Boolean = false,
    val timeBankMs: Long = 0,
)

data class VowChoice(
    val packageName: String?,
    val label: String,
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                refresh(settings)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refresh(_state.value.settings) }
    }

    private suspend fun refresh(settings: LumenSettings) {
        val usage = container.usageRepository
        if (!usage.hasUsageAccess()) {
            _state.update { it.copy(loading = false, permissionMissing = true) }
            return
        }
        _state.update { it.copy(loading = true) }
        val installDay = if (settings.installedEpochDay > 0) {
            LocalDate.ofEpochDay(settings.installedEpochDay)
        } else {
            TimeUtils.today().minusDays(14)
        }
        val history = usage.history(installDay, settings.excludedPackages, settings.notificationTracking)
        val week = history.takeLast(7)
        val today = week.lastOrNull()
        val todayEpoch = today?.date?.toEpochDay() ?: 0L
        val extendedToday = container.settingsRepository.extensionsFor(todayEpoch)
            .mapTo(mutableSetOf()) { it.substringBefore('|') }
        val crossed = if (today != null) {
            today.apps.filter { app ->
                settings.appLimitsMinutes[app.packageName]
                    ?.let { minutes -> app.timeMs >= minutes * 60_000L } == true
            }
        } else {
            emptyList()
        }
        val nudges = if (today != null) {
            settings.appLimitsMinutes.mapNotNull { (pkg, minutes) ->
                val app = today.apps.firstOrNull { it.packageName == pkg } ?: return@mapNotNull null
                if (crossed.any { it.packageName == pkg }) return@mapNotNull null
                val used = app.timeMs
                val limitMs = minutes * 60_000L
                when {
                    used >= limitMs ->
                        "You hit your $minutes-minute daily limit on ${app.label}."
                    used >= limitMs * 8 / 10 ->
                        "Close to your $minutes-minute limit on ${app.label} — " +
                            "${TimeUtils.formatDuration(used)} used."
                    else -> null
                }
            }
        } else {
            emptyList()
        }
        val extensionPending = crossed.firstOrNull { it.packageName !in extendedToday }
        val now = java.time.LocalTime.now()
        val bedtime = java.time.LocalTime.of(settings.bedtimeHour.coerceIn(0, 23), settings.bedtimeMinute.coerceIn(0, 59))
        val bedtimeActive = settings.bedtimeEnabled && !now.isBefore(bedtime)
        val effectiveGoal = settings.dailyGoalFor(TimeUtils.today())
        notifyGoalIfReached(today, settings, effectiveGoal)
        val streakInfo = StreakCalculator.info(history, effectiveGoal.toLong(), settings.restDays)
        val earned = com.lumen.app.domain.Achievements.evaluate(history, effectiveGoal, settings.restDays)
        val known = container.settingsRepository.unlockedAchievements()
        val newlyEarned = earned - known
        if (newlyEarned.isNotEmpty()) {
            newlyEarned.forEach { container.settingsRepository.unlockAchievement(it) }
        }
        val mood = today?.let { container.settingsRepository.moodFor(it.date.toEpochDay()) }
        val epochDay = today?.date?.toEpochDay() ?: 0L
        val nowHour = java.time.LocalTime.now().hour
        val vowCheckIn = if (epochDay != 0L && nowHour < 12 && !container.settingsRepository.vowChecked(epochDay)) {
            container.settingsRepository.vowFor(epochDay)
        } else {
            null
        }
        val vowLookAhead = if (epochDay != 0L && nowHour >= 20 &&
            container.settingsRepository.vowFor(epochDay + 1L) == null
        ) {
            week.flatMap { it.apps }
                .groupBy { it.packageName }
                .map { (_, apps) -> apps.maxByOrNull { it.timeMs } ?: apps.first() }
                .sortedByDescending { it.timeMs }
                .take(5)
        } else {
            emptyList()
        }
        val patternShift = if (today != null) {
            val startMs = TimeUtils.startOfDay(today.date)
            val nowMs = System.currentTimeMillis()
            val elapsedMin = (nowMs - startMs) / 60_000.0
            val baseline = history
                .filter { it.date < today.date }
                .takeLast(14)
                .map { it.screenTimeMs }
            val baseAvg = if (baseline.isEmpty()) 0L else baseline.sum() / baseline.size
            elapsedMin >= 180.0 && baseAvg > 0L &&
                today.screenTimeMs >= 60L * 60_000L &&
                today.screenTimeMs > (baseAvg * elapsedMin / 1440.0) * 1.5 &&
                !container.settingsRepository.patternAcked(epochDay)
        } else {
            false
        }
        val timeBankMs = week.sumOf { day ->
            (settings.dailyGoalFor(day.date) * 60_000L - day.screenTimeMs).coerceAtLeast(0L)
        }
        _state.update {
            it.copy(
                loading = false,
                permissionMissing = false,
                today = today,
                week = week,
                streak = streakInfo,
                insights = InsightEngine.generate(history.asReversed(), effectiveGoal.toLong()),
                limitNudges = nudges,
                bedtimeActive = bedtimeActive,
                forecast = forecastFor(today, effectiveGoal),
                achievements = earned,
                mood = mood,
                extensionPending = extensionPending,
                extendedToday = extendedToday,
                vowLookAhead = vowLookAhead,
                vowCheckIn = vowCheckIn,
                patternShift = patternShift,
                timeBankMs = timeBankMs,
            )
        }
        pushWidgetSnapshot(container.context, today, settings, streakInfo)
    }

    fun ackPatternShift() {
        val today = _state.value.today ?: return
        viewModelScope.launch {
            container.settingsRepository.setPatternAck(today.date.toEpochDay())
            _state.update { it.copy(patternShift = false) }
        }
    }

    fun extendLimit(pkg: String, note: String?) {
        val today = _state.value.today ?: return
        viewModelScope.launch {
            container.settingsRepository.addExtension(today.date.toEpochDay(), pkg, note)
            _state.update {
                it.copy(
                    extensionPending = null,
                    extendedToday = it.extendedToday + pkg,
                )
            }
        }
    }

    fun dismissExtension() {
        _state.update { it.copy(extensionPending = null) }
    }

    fun makeVow(pkg: String?) {
        val today = _state.value.today ?: return
        viewModelScope.launch {
            container.settingsRepository.setVow(today.date.toEpochDay() + 1L, pkg)
            _state.update { it.copy(vowLookAhead = emptyList()) }
        }
    }

    fun ackVow() {
        val today = _state.value.today ?: return
        viewModelScope.launch {
            container.settingsRepository.setVowChecked(today.date.toEpochDay())
            _state.update { it.copy(vowCheckIn = null) }
        }
    }

    fun setMood(mood: String?) {
        val today = _state.value.today
        if (today == null) return
        viewModelScope.launch {
            val next = if (mood == _state.value.mood) null else mood
            container.settingsRepository.setMood(today.date.toEpochDay(), next)
            _state.update { it.copy(mood = next) }
        }
    }

    private fun forecastFor(today: DayStats?, goalMinutes: Int): String? {
        if (today == null || goalMinutes <= 0) return null
        val startOfDay = TimeUtils.startOfDay(today.date)
        val nowMs = System.currentTimeMillis()
        val elapsedMin = (nowMs - startOfDay) / 60_000.0
        if (elapsedMin < 90.0) return null
        val usedMs = today.screenTimeMs.toDouble()
        if (usedMs <= 0.0) return null
        val rate = usedMs / elapsedMin
        val projectedMs = rate * 1440.0
        val projected = TimeUtils.formatDuration(projectedMs.toLong())
        val goalMs = goalMinutes * 60_000L
        return when {
            usedMs >= goalMs -> "Already past your $goalMinutes-minute goal — you're projected to close the day at $projected."
            projectedMs <= goalMs -> "On pace for $projected today — a comfortable finish under your $goalMinutes-minute goal."
            else -> {
                val minsToGoal = (goalMs - usedMs) / rate
                val at = java.time.Instant.ofEpochMilli(nowMs + (minsToGoal * 60_000).toLong())
                    .atZone(java.time.ZoneId.systemDefault())
                val timeStr = String.format(java.util.Locale.ROOT, "%02d:%02d", at.hour, at.minute)
                "On pace for $projected today — you'll pass your $goalMinutes-minute goal around $timeStr."
            }
        }
    }

    private fun pushWidgetSnapshot(
        context: Context,
        today: DayStats?,
        settings: LumenSettings,
        streak: StreakInfo,
    ) {
        val minutes = (today?.screenTimeMs ?: 0L) / 60_000L
        val goal = today?.let { settings.dailyGoalFor(it.date) } ?: settings.dailyGoalMinutes
        context.getSharedPreferences(com.lumen.app.widget.LumenWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(com.lumen.app.widget.LumenWidgetProvider.KEY_MINUTES, minutes.toInt())
            .putInt(com.lumen.app.widget.LumenWidgetProvider.KEY_GOAL, goal)
            .putInt(com.lumen.app.widget.LumenWidgetProvider.KEY_STREAK, streak.currentDays)
            .apply()
        com.lumen.app.widget.LumenWidgetProvider.updateAll(context)
    }

    private suspend fun notifyGoalIfReached(today: DayStats?, settings: LumenSettings, goalMinutes: Int) {
        val goalMs = goalMinutes * 60_000L
        if (today == null || goalMinutes <= 0 || today.screenTimeMs < goalMs) return
        val epochDay = today.date.toEpochDay()
        val repo = container.settingsRepository
        if (repo.goalNotifiedDay() == epochDay) return
        repo.setGoalNotifiedDay(epochDay)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                container.context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = androidx.core.app.NotificationCompat.Builder(container.context, "lumen_goals")
            .setSmallIcon(com.lumen.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("Daily goal reached")
            .setContentText(
                "You've hit your ${settings.dailyGoalMinutes}-minute goal — nice work. " +
                    "Take a breath away from the screen.",
            )
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(container.context).notify(2001, notification)
    }
}

data class ReportsState(
    val settings: LumenSettings = LumenSettings(),
    val loading: Boolean = true,
    val permissionMissing: Boolean = false,
    val history: List<DayStats> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val weekComparison: List<ComparisonRow> = emptyList(),
    val offendersWeek: List<AppUsage> = emptyList(),
    val offendersMonth: List<AppUsage> = emptyList(),
    val hourly: Map<java.time.LocalDate, IntArray> = emptyMap(),
)

data class ComparisonRow(
    val label: String,
    val value: String,
    val better: Boolean,
)

private fun buildComparison(week: List<DayStats>, previous: List<DayStats>): List<ComparisonRow> {
    fun sum(list: List<DayStats>, select: (DayStats) -> Long): Long = list.sumOf { select(it) }
    if (week.isEmpty() || previous.isEmpty()) return emptyList()
    val screenDelta = sum(week) { it.screenTimeMs } - sum(previous) { it.screenTimeMs }
    val unlockDelta = sum(week) { it.unlocks.toLong() } - sum(previous) { it.unlocks.toLong() }
    val notifDelta = sum(week) { it.notificationCount.toLong() } - sum(previous) { it.notificationCount.toLong() }
    val focusDelta = sum(week) { it.focusMinutes.toLong() } - sum(previous) { it.focusMinutes.toLong() }
    fun signedCount(value: Long): String =
        if (value == 0L) "Same as last week"
        else "${if (value > 0) "+" else "−"}${kotlin.math.abs(value)}"
    fun signedDuration(ms: Long): String =
        if (ms == 0L) "Same as last week"
        else "${if (ms > 0) "+" else "−"}${TimeUtils.formatDuration(kotlin.math.abs(ms))}"
    return listOf(
        ComparisonRow("Screen time", signedDuration(screenDelta), screenDelta <= 0L),
        ComparisonRow("Pickups", signedCount(unlockDelta), unlockDelta <= 0L),
        ComparisonRow("Alerts", signedCount(notifDelta), notifDelta <= 0L),
        ComparisonRow("Focus", signedCount(focusDelta), focusDelta >= 0L),
    )
}

class ReportsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                refresh(settings)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refresh(_state.value.settings) }
    }

    private suspend fun refresh(settings: LumenSettings) {
        val usage = container.usageRepository
        if (!usage.hasUsageAccess()) {
            _state.update { it.copy(loading = false, permissionMissing = true) }
            return
        }
        _state.update { it.copy(loading = true) }
        val installDay = if (settings.installedEpochDay > 0) {
            LocalDate.ofEpochDay(settings.installedEpochDay)
        } else {
            TimeUtils.today().minusDays(30)
        }
        val history = usage.history(installDay, settings.excludedPackages, settings.notificationTracking)
        val today = TimeUtils.today()
        val week = history.takeLast(7)
        val previous = history.takeLast(14).take(7)
        val nowMs = System.currentTimeMillis()
        val monthStart = TimeUtils.startOfDay(today.minusDays(29))
        val weekStart = TimeUtils.startOfDay(today.minusDays(6))
        _state.update {
            it.copy(
                loading = false,
                permissionMissing = false,
                history = history.asReversed(),
                insights = InsightEngine.generate(history.asReversed(), settings.dailyGoalMinutes.toLong()),
                weekComparison = buildComparison(week, previous),
                offendersWeek = usage.notificationOffenders(weekStart, nowMs),
                offendersMonth = usage.notificationOffenders(monthStart, nowMs),
                hourly = usage.hourlyUsage((0L..6L).map { today.minusDays(it) }),
            )
        }
    }
}

fun viewModelFactoryFor(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { DashboardViewModel(container) }
    initializer { ReportsViewModel(container) }
    initializer { FocusViewModel(container) }
    initializer { SettingsViewModel(container) }
    initializer { OnboardingViewModel(container) }
    initializer { FamilyViewModel(container) }
}
