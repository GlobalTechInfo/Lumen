package com.lumen.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val dateEpochDay: Long,
    val screenTimeMs: Long,
    val unlocks: Int,
    val notificationCount: Int,
    val focusMinutes: Int,
)

@Entity(
    tableName = "app_usage_day",
    primaryKeys = ["dateEpochDay", "packageName"],
    indices = [Index("dateEpochDay")],
)
data class AppUsageDayEntity(
    val dateEpochDay: Long,
    val packageName: String,
    val label: String,
    val timeMs: Long,
    val notifications: Int,
)

@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochMs: Long,
    val endEpochMs: Long?,
    val targetMinutes: Int,
    val completed: Boolean,
)

@Entity(tableName = "notification_event")
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val epochMs: Long,
)

@Entity(
    tableName = "peer_day",
    primaryKeys = ["peerId", "dateEpochDay"],
    indices = [Index("peerId")],
)
data class PeerDayEntity(
    val peerId: String,
    val dateEpochDay: Long,
    val screenTimeMs: Long,
    val unlocks: Int,
    val notificationCount: Int,
    val focusMinutes: Int,
    val syncedAtMs: Long,
)
