package com.lumen.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TimeUtils {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun today(): LocalDate = LocalDate.now(zone)

    fun toDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun daysInRange(start: LocalDate, end: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var d = start
        while (!d.isAfter(end)) {
            result += d
            d = d.plusDays(1)
        }
        return result
    }

    fun formatDuration(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun formatHoursMinutes(ms: Long): String {
        val totalMin = ms / 60000
        return "${totalMin / 60}:${(totalMin % 60).toString().padStart(2, '0')}"
    }
}
