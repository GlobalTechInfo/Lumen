package com.lumen.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        DailySummaryEntity::class,
        AppUsageDayEntity::class,
        FocusSessionEntity::class,
        NotificationEventEntity::class,
        PeerDayEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun appUsageDayDao(): AppUsageDayDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun notificationEventDao(): NotificationEventDao
    abstract fun peerDayDao(): PeerDayDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `peer_day` (" +
                        "`peerId` TEXT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, " +
                        "`screenTimeMs` INTEGER NOT NULL, " +
                        "`unlocks` INTEGER NOT NULL, " +
                        "`notificationCount` INTEGER NOT NULL, " +
                        "`focusMinutes` INTEGER NOT NULL, " +
                        "`syncedAtMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`peerId`, `dateEpochDay`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_peer_day_peerId` ON `peer_day` (`peerId`)")
            }
        }
    }
}