package com.lumen.app.ui.reports

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.lumen.app.domain.DayStats
import com.lumen.app.domain.TimeUtils
import java.io.File
import java.io.FileOutputStream

object ReportImageRenderer {

    private val bg = 0xFF0B1226.toInt()
    private val card = 0xFF121A30.toInt()
    private val text = 0xFFF3F4F8.toInt()
    private val muted = 0xFF9AA3B8.toInt()
    private val amber = 0xFFFFD166.toInt()
    private val mint = 0xFF7BE0A8.toInt()
    private val periwinkle = 0xFF8FA8FF.toInt()

    fun render(
        title: String,
        subtitle: String,
        days: List<DayStats>,
        goalMinutes: Int,
        streak: Int,
    ): Bitmap {
        val w = 1080
        val pad = 64f
        val titleSize = 44f
        val baseHeight = 900f
        val metricY = 330f
        val appsStart = 470f
        val appRowH = 78f
        val rows = minOf(days.size, 5)
        val h = (baseHeight + rows * appRowH).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text
            textSize = titleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 30f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 28f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = amber }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22304F.toInt() }

        canvas.drawText(title, pad, 100f, titlePaint)
        canvas.drawText(subtitle, pad, 150f, subPaint)
        canvas.drawText("Streak: $streak day${if (streak == 1) "" else "s"}", pad, 210f, subPaint)

        val totalMs = days.sumOf { it.screenTimeMs }
        val unlocks = days.sumOf { it.unlocks.toLong() }
        val alerts = days.sumOf { it.notificationCount.toLong() }
        val focus = days.sumOf { it.focusMinutes.toLong() }
        drawMetric(canvas, pad, metricY, "Screen time", TimeUtils.formatDuration(totalMs), amber, w)
        drawMetric(canvas, pad, metricY + 80f, "Pickups", unlocks.toString(), periwinkle, w)
        drawMetric(canvas, pad, metricY + 160f, "Alerts", alerts.toString(), mint, w)
        drawMetric(canvas, pad, metricY + 240f, "Focus", "$focus min", text, w)

        val goalMs = goalMinutes * 60_000L
        val fraction = if (goalMs > 0) (totalMs.toFloat() / goalMs).coerceIn(0f, 1f) else 0f
        canvas.drawRoundRect(
            RectF(pad, metricY + 320f, w - pad, metricY + 332f),
            10f, 10f, trackPaint,
        )
        canvas.drawRoundRect(
            RectF(pad, metricY + 320f, pad + (w - 2 * pad) * fraction, metricY + 332f),
            10f, 10f, barPaint,
        )
        canvas.drawText(
            "Goal: ${TimeUtils.formatDuration(totalMs)} of ${goalMinutes} min",
            pad,
            metricY + 380f,
            labelPaint,
        )

        val apps = days
            .flatMap { it.apps }
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                com.lumen.app.domain.AppUsage(
                    packageName = pkg,
                    label = list.first().label,
                    timeMs = list.sumOf { it.timeMs },
                    notifications = list.sumOf { it.notifications },
                    hue = list.first().hue,
                )
            }
            .sortedByDescending { it.timeMs }
            .take(5)
        if (apps.isNotEmpty()) {
            canvas.drawText("Top apps", pad, appsStart, valuePaint)
            val maxMs = apps.first().timeMs.coerceAtLeast(1L)
            apps.forEachIndexed { i, app ->
                val y = appsStart + 40f + i * appRowH
                canvas.drawText(app.label, pad, y, labelPaint)
                canvas.drawText(TimeUtils.formatDuration(app.timeMs), w - pad, y, valuePaint)
                val barTop = y + 18f
                val barW = (w - 2 * pad) * (app.timeMs.toFloat() / maxMs)
                canvas.drawRoundRect(RectF(pad, barTop, pad + barW, barTop + 10f), 5f, 5f, barPaint)
            }
        }

        canvas.drawText(
            "Made with Lumen — offline, no account, no cloud.",
            pad,
            h - 48f,
            subPaint,
        )
        return bitmap
    }

    private fun drawMetric(
        canvas: Canvas,
        pad: Float,
        y: Float,
        label: String,
        value: String,
        tint: Int,
        w: Int,
    ) {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 28f }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(label, pad, y, labelPaint)
        canvas.drawText(value, w - pad, y, valuePaint)
    }
}

fun shareReport(context: Context, bitmap: Bitmap, title: String) {
    val file = File(context.cacheDir, "lumen-report.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Lumen report"))
}
