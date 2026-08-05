package com.lumen.app.data.notifications

import com.lumen.app.data.db.NotificationEventDao
import com.lumen.app.data.db.NotificationEventEntity

class NotificationRepository(private val dao: NotificationEventDao) {
    @Volatile
    var enabled: Boolean = true

    suspend fun recordPost(packageName: String, epochMs: Long) {
        if (!enabled) return
        dao.insert(NotificationEventEntity(packageName = packageName, epochMs = epochMs))
    }

    suspend fun countBetween(start: Long, end: Long): Int = dao.countBetween(start, end)

    suspend fun countForApp(packageName: String, start: Long, end: Long): Int =
        dao.countForApp(packageName, start, end)
}
