package com.lumen.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumen.app.LumenApp

class LinkSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LumenApp).container
        val repo = container.linkRepository
        val peers = repo.peers.value
        if (peers.isEmpty()) return Result.success()
        var anySuccess = false
        for (peer in peers) {
            val host = peer.host ?: continue
            if (repo.syncWith(peer, host, 8787).isSuccess) {
                anySuccess = true
            }
        }
        return if (anySuccess) Result.success() else Result.retry()
    }
}
