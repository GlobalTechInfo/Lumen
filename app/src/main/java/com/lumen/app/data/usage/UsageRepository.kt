package com.lumen.app.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.lumen.app.data.db.AppUsageDayDao
import com.lumen.app.data.db.AppUsageDayEntity
import com.lumen.app.data.db.DailySummaryDao
import com.lumen.app.data.db.DailySummaryEntity
import com.lumen.app.data.db.FocusSessionDao
import com.lumen.app.data.db.NotificationEventDao
import com.lumen.app.domain.AppDailyPoint
import com.lumen.app.domain.AppTimelineSlice
import com.lumen.app.domain.AppUsage
import com.lumen.app.domain.DayStats
import com.lumen.app.domain.Palette
import com.lumen.app.domain.TimeUtils
import com.lumen.app.util.AppInfo
import java.time.LocalDate
import kotlinx.coroutines.withContext

class UsageRepository(
    private val context: Context,
    private val appInfo: AppInfo,
    private val summaryDao: DailySummaryDao,
    private val appUsageDao: AppUsageDayDao,
    private val focusDao: FocusSessionDao,
    private val notifDao: NotificationEventDao,
) {
    private val usm: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isNotificationAccessEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.split(':').any { it.contains(context.packageName) }
    }

    suspend fun todayStats(excluded: Set<String>, trackNotifications: Boolean): DayStats {
        val date = TimeUtils.today()
        val start = TimeUtils.startOfDay(date)
        val now = System.currentTimeMillis()
        val eventUsage = usageFromEvents(start, now, excluded)
        return buildDayStats(
            date,
            start,
            now,
            excluded,
            trackNotifications,
            if (eventUsage.isNotEmpty()) eventUsage else null,
        )
    }

    suspend fun history(from: LocalDate, excluded: Set<String>, trackNotifications: Boolean): List<DayStats> {
        val today = TimeUtils.today()
        if (from > today) return emptyList()
        val summaries = summaryDao.getBetween(from.toEpochDay(), today.toEpochDay())
            .associateBy { it.dateEpochDay }
        val appRows = appUsageDao.getBetween(from.toEpochDay(), today.toEpochDay())
            .groupBy { it.dateEpochDay }
        return TimeUtils.daysInRange(from, today).map { date ->
            if (date == today) {
                todayStats(excluded, trackNotifications)
            } else {
                val day = date.toEpochDay()
                val summary = summaries[day]
                DayStats(
                    date = date,
                    screenTimeMs = summary?.screenTimeMs ?: 0L,
                    unlocks = summary?.unlocks ?: 0,
                    notificationCount = summary?.notificationCount ?: 0,
                    focusMinutes = summary?.focusMinutes ?: 0,
                    apps = (appRows[day] ?: emptyList()).map { it.toAppUsage() },
                )
            }
        }
    }

    suspend fun persistDay(date: LocalDate, excluded: Set<String>, trackNotifications: Boolean) {
        val now = System.currentTimeMillis()
        val start = TimeUtils.startOfDay(date)
        val end = TimeUtils.endOfDay(date)
        if (start > now) return
        val endMs = minOf(end, now + 1)
        val (appMap, pickups) = computeEvents(start, endMs, excluded)
        val notifCount = if (trackNotifications) notifDao.countBetween(start, endMs) else 0
        val focusMinutes = focusDao.getBetween(start, endMs)
            .sumOf { (it.endEpochMs ?: endMs).minus(it.startEpochMs).coerceAtLeast(0) / 60000 }
        val screenTime = appMap.values.sum()
        summaryDao.upsert(
            DailySummaryEntity(
                dateEpochDay = date.toEpochDay(),
                screenTimeMs = screenTime,
                unlocks = pickups,
                notificationCount = notifCount,
                focusMinutes = focusMinutes.toInt(),
            ),
        )
        val top = appMap.entries.sortedByDescending { it.value }.take(20)
        appUsageDao.upsertAll(
            top.map { (pkg, ms) ->
                AppUsageDayEntity(
                    dateEpochDay = date.toEpochDay(),
                    packageName = pkg,
                    label = appInfo.label(pkg),
                    timeMs = ms,
                    notifications = 0,
                )
            },
        )
    }

    suspend fun notificationOffenders(start: Long, end: Long): List<AppUsage> =
        notifDao.countByPackage(start, end)
            .mapNotNull { row ->
                if (row.count <= 0) return@mapNotNull null
                AppUsage(
                    packageName = row.packageName,
                    label = appInfo.label(row.packageName),
                    timeMs = 0L,
                    notifications = row.count,
                    hue = Palette.hueFor(row.packageName),
                )
            }
            .sortedByDescending { it.notifications }

    suspend fun hourlyUsage(days: List<LocalDate>): Map<LocalDate, IntArray> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val zone = java.time.ZoneId.systemDefault()
            val result = LinkedHashMap<LocalDate, IntArray>()
            for (date in days) {
                val buckets = IntArray(24)
                try {
                    val start = TimeUtils.startOfDay(date)
                    val end = TimeUtils.endOfDay(date)
                    val events = usm.queryEvents(start, end)
                    val e = UsageEvents.Event()
                    var opened: Long? = null
                    while (events.hasNextEvent()) {
                        events.getNextEvent(e)
                        when (e.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED -> opened = e.timeStamp
                            UsageEvents.Event.ACTIVITY_PAUSED,
                            UsageEvents.Event.ACTIVITY_STOPPED -> {
                                opened?.let { from ->
                                    val to = e.timeStamp
                                    var t = from
                                    while (t < to) {
                                        val hour = java.time.Instant.ofEpochMilli(t).atZone(zone).hour
                                        val nextHour = ((t / 3_600_000L) + 1L) * 3_600_000L
                                        val segmentEnd = minOf(to, nextHour)
                                        buckets[hour] = buckets[hour] +
                                            (segmentEnd - t).coerceAtLeast(0).toInt() / 60_000
                                        t = segmentEnd
                                    }
                                }
                                opened = null
                            }
                        }
                    }
                } catch (_: SecurityException) {
                }
                result[date] = buckets
            }
            result
        }

    suspend fun exportJson(excluded: Set<String>, trackNotifications: Boolean): String {
        val from = TimeUtils.today().minusDays(29)
        val days = history(from, excluded, trackNotifications)
        val json = org.json.JSONObject()
            .put("v", 1)
            .put("app", "Lumen")
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "days",
                org.json.JSONArray().apply {
                    days.forEach { put(com.lumen.app.data.link.LinkProtocol.dayJson(it)) }
                },
            )
        return json.toString(2)
    }

    suspend fun importJson(json: String): Int {
        val obj = org.json.JSONObject(json)
        if (obj.optInt("v") != 1) return -1
        val arr = obj.optJSONArray("days") ?: return -1
        val today = TimeUtils.today().toEpochDay()
        val summaries = mutableListOf<DailySummaryEntity>()
        val appRows = mutableListOf<AppUsageDayEntity>()
        var imported = 0
        for (i in 0 until arr.length()) {
            val day = com.lumen.app.data.link.LinkProtocol.parseDay(arr.getJSONObject(i)) ?: continue
            if (day.date.toEpochDay() > today) continue
            summaries += DailySummaryEntity(
                dateEpochDay = day.date.toEpochDay(),
                screenTimeMs = day.screenTimeMs,
                unlocks = day.unlocks,
                notificationCount = day.notificationCount,
                focusMinutes = day.focusMinutes,
            )
            appRows += day.apps.map { app ->
                AppUsageDayEntity(
                    dateEpochDay = day.date.toEpochDay(),
                    packageName = app.packageName,
                    label = app.label,
                    timeMs = app.timeMs,
                    notifications = app.notifications,
                )
            }
            imported++
        }
        if (imported > 0) {
            summaries.forEach { summaryDao.upsert(it) }
            appUsageDao.upsertAll(appRows)
        }
        return imported
    }

    suspend fun purgeOldNotificationEvents() {
        notifDao.deleteOlderThan(System.currentTimeMillis() - 45L * 24 * 3600 * 1000)
    }

    suspend fun backfillIfMissing(settings: com.lumen.app.data.prefs.LumenSettings) {
        val today = TimeUtils.today()
        val earliest = if (settings.installedEpochDay > 0) {
            maxOf(settings.installedEpochDay, today.toEpochDay() - 13)
        } else {
            today.toEpochDay() - 13
        }
        val existing = summaryDao.getBetween(earliest, today.toEpochDay())
            .mapTo(mutableSetOf()) { it.dateEpochDay }
        for (date in TimeUtils.daysInRange(LocalDate.ofEpochDay(earliest), today)) {
            if (date.toEpochDay() !in existing) {
                persistDay(date, settings.excludedPackages, settings.notificationTracking)
            }
        }
    }

    suspend fun appHistory(packageName: String, from: LocalDate): List<AppDailyPoint> {        val today = TimeUtils.today()
        val rows = appUsageDao.getForApp(packageName, from.toEpochDay(), today.toEpochDay())
        return TimeUtils.daysInRange(from, today).map { date ->
            val row = rows.find { it.dateEpochDay == date.toEpochDay() }
            AppDailyPoint(date, row?.timeMs ?: 0L)
        }
    }

    suspend fun dayTimeline(packageName: String, date: LocalDate): List<AppTimelineSlice> {
        val slices = mutableListOf<AppTimelineSlice>()
        val events = usm.queryEvents(TimeUtils.startOfDay(date), TimeUtils.endOfDay(date))
        val e = UsageEvents.Event()
        var activeStart = 0L
        var inForeground = false
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName != packageName) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    inForeground = true
                    activeStart = e.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (inForeground) {
                        slices += AppTimelineSlice(activeStart, e.timeStamp)
                        inForeground = false
                    }
                }
            }
        }
        if (inForeground) slices += AppTimelineSlice(activeStart, TimeUtils.endOfDay(date))
        return slices
    }

    suspend fun allAppPackages(): List<String> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = context.packageManager.queryIntentActivities(launcher, 0)
        return resolved.map { it.activityInfo.packageName }
            .distinct()
            .sorted()
    }

    suspend fun usedPackagesSince(from: LocalDate): Set<String> {
        val today = TimeUtils.today()
        if (from > today) return emptySet()
        return appUsageDao.getBetween(from.toEpochDay(), today.toEpochDay())
            .mapTo(mutableSetOf()) { it.packageName }
    }

    suspend fun orphanApps(days: Int, excluded: Set<String>): List<String> {
        val from = TimeUtils.today().minusDays(days.toLong())
        val used = usedPackagesSince(from)
        return allAppPackages().filter { it !in used && it !in excluded }
    }

    private suspend fun buildDayStats(
        date: LocalDate,
        start: Long,
        end: Long,
        excluded: Set<String>,
        trackNotifications: Boolean,
        screenOverride: Map<String, Long>? = null,
    ): DayStats {
        val (appMap, pickups) =
            if (screenOverride != null) Pair(screenOverride, pickupsFromEvents(start, end))
            else computeEvents(start, end, excluded)
        val notifCount = if (trackNotifications) notifDao.countBetween(start, end) else 0
        val focusMinutes = focusDao.getBetween(start, end)
            .sumOf { (it.endEpochMs ?: end).minus(it.startEpochMs).coerceAtLeast(0) / 60000 }
        val apps = appMap.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (pkg, ms) ->
                AppUsage(
                    packageName = pkg,
                    label = appInfo.label(pkg),
                    timeMs = ms,
                    notifications = if (trackNotifications) notifDao.countForApp(pkg, start, end) else 0,
                    hue = Palette.hueFor(pkg),
                )
            }
        return DayStats(
            date = date,
            screenTimeMs = appMap.values.sum(),
            unlocks = pickups,
            notificationCount = notifCount,
            focusMinutes = focusMinutes.toInt(),
            apps = apps,
        )
    }

    private fun computeEvents(
        start: Long,
        end: Long,
        excluded: Set<String>,
    ): Pair<Map<String, Long>, Int> {
        val appMap = aggregatedUsage(start, end, excluded)
        return Pair(appMap, pickupsFromEvents(start, end))
    }

    private fun usageFromEvents(start: Long, end: Long, excluded: Set<String>): Map<String, Long> {
        val result = HashMap<String, Long>()
        val activeStart = HashMap<String, Long>()
        try {
            val events = usm.queryEvents(start, end)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                if (pkg in excluded) continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> activeStart[pkg] = e.timeStamp
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val from = activeStart.remove(pkg) ?: continue
                        val to = e.timeStamp
                        val segStart = maxOf(from, start)
                        val segEnd = minOf(to, end)
                        if (segEnd > segStart) {
                            result[pkg] = (result[pkg] ?: 0L) + (segEnd - segStart)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        }
        return result
    }

    private fun aggregatedUsage(start: Long, end: Long, excluded: Set<String>): Map<String, Long> {
        val map = HashMap<String, Long>()
        try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            for (entry in stats) {
                val pkg = entry.packageName ?: continue
                if (pkg in excluded) continue
                if (entry.totalTimeInForeground > 0L) {
                    map[pkg] = (map[pkg] ?: 0L) + entry.totalTimeInForeground
                }
            }
        } catch (_: SecurityException) {
        }
        return map
    }

    private fun pickupsFromEvents(start: Long, end: Long): Int {
        var pickups = 0
        var screenInteractive = false
        try {
            val events = usm.queryEvents(start, end)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if (!screenInteractive) pickups++
                        screenInteractive = true
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        screenInteractive = false
                    }
                }
            }
        } catch (_: SecurityException) {
        }
        return pickups
    }

    private fun AppUsageDayEntity.toAppUsage() = AppUsage(
        packageName = packageName,
        label = label,
        timeMs = timeMs,
        notifications = notifications,
        hue = Palette.hueFor(packageName),
    )
}
