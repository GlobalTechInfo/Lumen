package com.lumen.app.data

import android.content.Context
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lumen.app.data.db.LumenDatabase
import com.lumen.app.data.focus.FocusRepository
import com.lumen.app.data.link.LinkRepository
import com.lumen.app.data.notifications.NotificationRepository
import com.lumen.app.data.prefs.LumenSettings
import com.lumen.app.data.prefs.SettingsRepository
import com.lumen.app.data.usage.UsageRepository
import com.lumen.app.util.AppInfo
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(val context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository = SettingsRepository(context)
    val currentSettings = MutableStateFlow(LumenSettings())

    private val database: LumenDatabase by lazy {
        Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db")
            .addMigrations(LumenDatabase.MIGRATION_1_2)
            .build()
    }

    val appInfo = AppInfo(context)
    val notificationRepository = NotificationRepository(database.notificationEventDao())
    val focusRepository = FocusRepository(database.focusSessionDao())
    val usageRepository: UsageRepository by lazy {
        UsageRepository(
            context = context,
            appInfo = appInfo,
            summaryDao = database.dailySummaryDao(),
            appUsageDao = database.appUsageDayDao(),
            focusDao = database.focusSessionDao(),
            notifDao = database.notificationEventDao(),
        )
    }

    val linkRepository: LinkRepository by lazy {
        LinkRepository(
            context = context,
            usageRepository = usageRepository,
            settingsRepository = settingsRepository,
            peerDayDao = database.peerDayDao(),
        )
    }

    init {
        appScope.launch {
            settingsRepository.settings.collect { currentSettings.value = it }
        }
        appScope.launch {
            settingsRepository.settings.first().let { usageRepository.backfillIfMissing(it) }
        }
        appScope.launch { linkRepository.loadInitial() }
        scheduleNightlySummary()
        scheduleBackgroundSync()
    }

    private fun scheduleNightlySummary() {
        val request = PeriodicWorkRequestBuilder<SummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "lumen_summary",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<LinkSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "lumen_bg_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun initialDelayMs(): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(2, 35)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(60_000)
    }
}
