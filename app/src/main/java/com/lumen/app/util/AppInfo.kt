package com.lumen.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

class AppInfo(private val context: Context) {
    private val cache = ConcurrentHashMap<String, String>()

    fun label(packageName: String): String = cache.getOrPut(packageName) {
        try {
            val ai: ApplicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            humanize(packageName)
        }
    }

    fun icon(packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun humanize(packageName: String): String {
        val raw = packageName.substringAfterLast('.')
        val words = raw.split('_', '-')
            .flatMap { it.split(Regex("(?<=[a-z0-9])(?=[A-Z])")) }
            .filter { it.isNotBlank() }
            .take(3)
        return words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
}
