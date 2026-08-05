package com.lumen.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.lumen.app.MainActivity
import com.lumen.app.R

class LumenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        const val PREFS_NAME = "lumen_widget"
        const val KEY_MINUTES = "minutes"
        const val KEY_GOAL = "goalMinutes"
        const val KEY_STREAK = "streak"
        const val ACTION_REFRESH = "com.lumen.app.WIDGET_REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, LumenWidgetProvider::class.java),
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val minutes = prefs.getInt(KEY_MINUTES, 0)
            val goal = prefs.getInt(KEY_GOAL, 0)
            val streak = prefs.getInt(KEY_STREAK, 0)

            val views = RemoteViews(context.packageName, R.layout.widget_lumen)
            views.setTextViewText(R.id.widget_time, formatTime(minutes))
            views.setTextViewText(
                R.id.widget_goal,
                if (goal > 0) "of ${formatTime(goal)} goal" else "no goal set",
            )
            views.setTextViewText(R.id.widget_streak, "$streak-day streak")
            val progress = if (goal > 0) {
                ((minutes.toFloat() / goal) * 1000).toInt().coerceIn(0, 1000)
            } else {
                0
            }
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            manager.updateAppWidget(id, views)
        }

        private fun formatTime(totalMinutes: Int): String {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                else -> "${minutes}m"
            }
        }
    }
}
