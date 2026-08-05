package com.lumen.app.data.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lumen.app.LumenApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LumenNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        val container = (application as LumenApp).container
        container.notificationRepository.enabled = container.currentSettings.value.notificationTracking
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val container = (application as LumenApp).container
        if (!container.notificationRepository.enabled) return
        if (sbn.isOngoing) return
        val pkg = sbn.packageName ?: return
        val posted = sbn.postTime
        scope.launch {
            container.notificationRepository.recordPost(pkg, posted)
        }
    }
}
