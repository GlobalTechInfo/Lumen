package com.lumen.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.lumen.app.data.AppContainer

class LumenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "lumen_goals",
                "Goals",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "lumen_watch",
                "Watch",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "lumen_recap",
                "Recap",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "lumen_knock",
                "Knocks",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "lumen_celebration",
                "Celebrations",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
