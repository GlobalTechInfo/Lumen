package com.lumen.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lumen_settings")

data class LumenSettings(
    val dailyGoalMinutes: Int = 180,
    val excludedPackages: Set<String> = emptySet(),
    val notificationTracking: Boolean = true,
    val onboarded: Boolean = false,
    val installedEpochDay: Long = 0L,
    val darkTheme: Boolean = true,
    val focusMinutes: Int = 25,
    val appLimitsMinutes: Map<String, Int> = emptyMap(),
    val bedtimeEnabled: Boolean = false,
    val bedtimeHour: Int = 22,
    val bedtimeMinute: Int = 0,
    val restDays: Set<Int> = emptySet(),
    val weekendEnabled: Boolean = false,
    val weekendGoalMinutes: Int = 120,
) {
    fun dailyGoalFor(date: java.time.LocalDate): Int {
        val weekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
            date.dayOfWeek == java.time.DayOfWeek.SUNDAY
        return if (weekendEnabled && weekend && weekendGoalMinutes > 0) {
            weekendGoalMinutes
        } else {
            dailyGoalMinutes
        }
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
        val EXCLUDED = stringSetPreferencesKey("excluded_packages")
        val NOTIFICATION_TRACKING = booleanPreferencesKey("notification_tracking")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val INSTALLED_DAY = longPreferencesKey("installed_epoch_day")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val FOCUS_MINUTES = intPreferencesKey("focus_minutes")
        val APP_LIMITS = stringPreferencesKey("app_limits")
        val BEDTIME_ENABLED = booleanPreferencesKey("bedtime_enabled")
        val BEDTIME_HOUR = intPreferencesKey("bedtime_hour")
        val BEDTIME_MINUTE = intPreferencesKey("bedtime_minute")
        val REST_DAYS = stringSetPreferencesKey("rest_days")
        val WEEKEND_ENABLED = booleanPreferencesKey("weekend_enabled")
        val WEEKEND_GOAL = intPreferencesKey("weekend_goal_minutes")
        val MOOD_TAGS = stringSetPreferencesKey("mood_tags")
        val ACHIEVEMENTS = stringSetPreferencesKey("achievements_unlocked")
        val EXTENSIONS = stringSetPreferencesKey("mindful_extensions")
        val GOAL_NOTIFIED_DAY = longPreferencesKey("goal_notified_epoch_day")
        val VOW_DAY = longPreferencesKey("vow_epoch_day")
        val VOW_APP = stringPreferencesKey("vow_app")
        val VOW_CHECKED_DAY = longPreferencesKey("vow_checked_epoch_day")
        val PATTERN_ACK_DAY = longPreferencesKey("pattern_ack_epoch_day")
    }

    val settings: Flow<LumenSettings> = context.dataStore.data.map { prefs ->
        LumenSettings(
            dailyGoalMinutes = prefs[Keys.DAILY_GOAL] ?: 180,
            excludedPackages = prefs[Keys.EXCLUDED] ?: emptySet(),
            notificationTracking = prefs[Keys.NOTIFICATION_TRACKING] ?: true,
            onboarded = prefs[Keys.ONBOARDED] ?: false,
            installedEpochDay = prefs[Keys.INSTALLED_DAY] ?: 0L,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            focusMinutes = prefs[Keys.FOCUS_MINUTES] ?: 25,
            appLimitsMinutes = parseLimits(prefs[Keys.APP_LIMITS]),
            bedtimeEnabled = prefs[Keys.BEDTIME_ENABLED] ?: false,
            bedtimeHour = prefs[Keys.BEDTIME_HOUR] ?: 22,
            bedtimeMinute = prefs[Keys.BEDTIME_MINUTE] ?: 0,
            restDays = (prefs[Keys.REST_DAYS] ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet(),
            weekendEnabled = prefs[Keys.WEEKEND_ENABLED] ?: false,
            weekendGoalMinutes = prefs[Keys.WEEKEND_GOAL] ?: 120,
        )
    }

    private fun parseLimits(raw: String?): Map<String, Int> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            val arr = org.json.JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    put(obj.getString("p"), obj.getInt("m"))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeLimits(limits: Map<String, Int>): String {
        val arr = org.json.JSONArray()
        limits.forEach { (pkg, minutes) ->
            arr.put(org.json.JSONObject().put("p", pkg).put("m", minutes))
        }
        return arr.toString()
    }

    suspend fun setDailyGoal(minutes: Int) {
        context.dataStore.edit { it[Keys.DAILY_GOAL] = minutes }
    }

    suspend fun setExcluded(packages: Set<String>) {
        context.dataStore.edit { it[Keys.EXCLUDED] = packages }
    }

    suspend fun setNotificationTracking(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATION_TRACKING] = enabled }
    }

    suspend fun setOnboarded() {
        context.dataStore.edit {
            it[Keys.ONBOARDED] = true
            it[Keys.INSTALLED_DAY] = java.time.LocalDate.now().toEpochDay()
        }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setFocusMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.FOCUS_MINUTES] = minutes }
    }

    suspend fun setAppLimit(packageName: String, minutes: Int) {
        context.dataStore.edit { prefs ->
            val next = parseLimits(prefs[Keys.APP_LIMITS]) + (packageName to minutes)
            prefs[Keys.APP_LIMITS] = encodeLimits(next)
        }
    }

    suspend fun removeAppLimit(packageName: String) {
        context.dataStore.edit { prefs ->
            val next = parseLimits(prefs[Keys.APP_LIMITS]) - packageName
            prefs[Keys.APP_LIMITS] = encodeLimits(next)
        }
    }

    suspend fun setBedtime(enabled: Boolean, hour: Int = 22, minute: Int = 0) {
        context.dataStore.edit {
            it[Keys.BEDTIME_ENABLED] = enabled
            it[Keys.BEDTIME_HOUR] = hour
            it[Keys.BEDTIME_MINUTE] = minute
        }
    }

    suspend fun setRestDays(days: Set<Int>) {
        context.dataStore.edit { it[Keys.REST_DAYS] = days.map { it.toString() }.toSet() }
    }

    suspend fun setWeekendMode(enabled: Boolean, goalMinutes: Int = 120) {
        context.dataStore.edit {
            it[Keys.WEEKEND_ENABLED] = enabled
            it[Keys.WEEKEND_GOAL] = goalMinutes
        }
    }

    suspend fun goalNotifiedDay(): Long = context.dataStore.data.first()[Keys.GOAL_NOTIFIED_DAY] ?: 0L

    suspend fun setGoalNotifiedDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.GOAL_NOTIFIED_DAY] = epochDay }
    }

    suspend fun setMood(epochDay: Long, mood: String?) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.MOOD_TAGS] ?: emptySet()
            val without = current.filterNot { it.startsWith("$epochDay:") }.toSet()
            prefs[Keys.MOOD_TAGS] = if (mood == null) without else without + "$epochDay:$mood"
        }
    }

    suspend fun moodFor(epochDay: Long): String? {
        val prefs = context.dataStore.data.first()
        return (prefs[Keys.MOOD_TAGS] ?: emptySet())
            .firstOrNull { it.startsWith("$epochDay:") }
            ?.substringAfter(':')
    }

    suspend fun unlockedAchievements(): Set<String> {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.ACHIEVEMENTS] ?: emptySet()
    }

    suspend fun unlockAchievement(id: String) {
        context.dataStore.edit { prefs ->
            val next = (prefs[Keys.ACHIEVEMENTS] ?: emptySet()) + id
            prefs[Keys.ACHIEVEMENTS] = next
        }
    }

    suspend fun addExtension(epochDay: Long, packageName: String, note: String?) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.EXTENSIONS] ?: emptySet()
            val key = "$epochDay|$packageName"
            val entry = if (note.isNullOrBlank()) key else "$key|$note"
            prefs[Keys.EXTENSIONS] = current.filterNot { it.startsWith("$key|") }.toSet() + entry
        }
    }

    suspend fun extensionsFor(epochDay: Long): List<String> {
        val prefs = context.dataStore.data.first()
        return (prefs[Keys.EXTENSIONS] ?: emptySet())
            .filter { it.startsWith("$epochDay|") }
            .sorted()
    }

    suspend fun extensionCount(epochDay: Long, packageName: String): Int {
        val prefs = context.dataStore.data.first()
        return (prefs[Keys.EXTENSIONS] ?: emptySet()).count { it.startsWith("$epochDay|$packageName|") }
    }

    suspend fun allExtensions(): List<Triple<Long, String, String?>> {
        val prefs = context.dataStore.data.first()
        return (prefs[Keys.EXTENSIONS] ?: emptySet())
            .map { entry ->
                val parts = entry.split('|')
                val day = parts.getOrNull(0)?.toLongOrNull() ?: return@map null
                val pkg = parts.getOrNull(1) ?: return@map null
                Triple(day, pkg, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
            .filterNotNull()
            .sortedByDescending { it.first }
    }

    suspend fun setVow(epochDay: Long, app: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VOW_DAY] = epochDay
            prefs[Keys.VOW_APP] = app ?: ""
        }
    }

    suspend fun vowFor(epochDay: Long): String? {
        val prefs = context.dataStore.data.first()
        val day = prefs[Keys.VOW_DAY] ?: 0L
        if (day != epochDay) return null
        return prefs[Keys.VOW_APP]?.takeIf { it.isNotEmpty() }
    }

    suspend fun setVowChecked(epochDay: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.VOW_CHECKED_DAY] = epochDay }
    }

    suspend fun vowChecked(epochDay: Long): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.VOW_CHECKED_DAY] == epochDay
    }

    suspend fun setPatternAck(epochDay: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.PATTERN_ACK_DAY] = epochDay }
    }

    suspend fun patternAcked(epochDay: Long): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.PATTERN_ACK_DAY] == epochDay
    }
}
