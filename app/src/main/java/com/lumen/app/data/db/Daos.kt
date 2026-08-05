package com.lumen.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class PackageCount(
    @ColumnInfo(name = "packageName") val packageName: String,
    @ColumnInfo(name = "total") val count: Int,
)

@Dao
interface DailySummaryDao {
    @Query("SELECT * FROM daily_summary WHERE dateEpochDay BETWEEN :start AND :end ORDER BY dateEpochDay")
    suspend fun getBetween(start: Long, end: Long): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summary ORDER BY dateEpochDay DESC LIMIT 400")
    suspend fun getAll(): List<DailySummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailySummaryEntity)
}

@Dao
interface AppUsageDayDao {
    @Query("SELECT * FROM app_usage_day WHERE dateEpochDay = :day ORDER BY timeMs DESC")
    suspend fun getForDay(day: Long): List<AppUsageDayEntity>

    @Query("SELECT * FROM app_usage_day WHERE dateEpochDay BETWEEN :start AND :end ORDER BY dateEpochDay, timeMs DESC")
    suspend fun getBetween(start: Long, end: Long): List<AppUsageDayEntity>

    @Query("SELECT * FROM app_usage_day WHERE dateEpochDay BETWEEN :start AND :end AND packageName = :packageName ORDER BY dateEpochDay")
    suspend fun getForApp(packageName: String, start: Long, end: Long): List<AppUsageDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AppUsageDayEntity>)

    @Query("DELETE FROM app_usage_day WHERE dateEpochDay < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("UPDATE focus_session SET endEpochMs = :endMs, completed = :completed WHERE id = :id")
    suspend fun finish(id: Long, endMs: Long, completed: Boolean)

    @Query("SELECT * FROM focus_session WHERE startEpochMs BETWEEN :start AND :end ORDER BY startEpochMs DESC")
    suspend fun getBetween(start: Long, end: Long): List<FocusSessionEntity>
}

@Dao
interface NotificationEventDao {
    @Insert
    suspend fun insert(event: NotificationEventEntity)

    @Query("SELECT COUNT(*) FROM notification_event WHERE epochMs BETWEEN :start AND :end")
    suspend fun countBetween(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM notification_event WHERE epochMs BETWEEN :start AND :end AND packageName = :packageName")
    suspend fun countForApp(packageName: String, start: Long, end: Long): Int

    @Query(
        "SELECT packageName, COUNT(*) AS total FROM notification_event " +
            "WHERE epochMs BETWEEN :start AND :end GROUP BY packageName ORDER BY total DESC",
    )
    suspend fun countByPackage(start: Long, end: Long): List<PackageCount>

    @Query("DELETE FROM notification_event WHERE epochMs < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface PeerDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PeerDayEntity>)

    @Query("SELECT * FROM peer_day WHERE peerId = :peerId AND dateEpochDay BETWEEN :start AND :end ORDER BY dateEpochDay")
    suspend fun getBetween(peerId: String, start: Long, end: Long): List<PeerDayEntity>

    @Query("DELETE FROM peer_day WHERE dateEpochDay < :before")
    suspend fun deleteOlderThanDay(before: Long)

    @Query("DELETE FROM peer_day WHERE peerId = :peerId")
    suspend fun deleteForPeer(peerId: String)
}
