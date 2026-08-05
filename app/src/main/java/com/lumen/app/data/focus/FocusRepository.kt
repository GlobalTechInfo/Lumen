package com.lumen.app.data.focus

import com.lumen.app.data.db.FocusSessionDao
import com.lumen.app.data.db.FocusSessionEntity
import com.lumen.app.domain.FocusSession

class FocusRepository(private val dao: FocusSessionDao) {
    suspend fun start(targetMinutes: Int): Long =
        dao.insert(
            FocusSessionEntity(
                startEpochMs = System.currentTimeMillis(),
                endEpochMs = null,
                targetMinutes = targetMinutes,
                completed = false,
            ),
        )

    suspend fun finish(id: Long, completed: Boolean) {
        dao.finish(id, System.currentTimeMillis(), completed)
    }

    suspend fun getBetween(startMs: Long, endMs: Long): List<FocusSession> =
        dao.getBetween(startMs, endMs).map {
            FocusSession(
                id = it.id,
                startEpochMs = it.startEpochMs,
                endEpochMs = it.endEpochMs,
                targetMinutes = it.targetMinutes,
                completed = it.completed,
            )
        }
}
