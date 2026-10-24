package com.roohu.roohux.services.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.roohu.roohux.MainActivity
import com.roohu.roohux.R
import com.roohu.roohux.core.database.DownloadEntity
import com.roohu.roohux.core.database.DownloadStatus

class DownloadNotifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Active downloads", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, "Download results", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(active: List<DownloadEntity>): Notification {
        val running = active.filter { it.status == DownloadStatus.DOWNLOADING }
        val b = NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        when {
            active.isEmpty() -> b.setContentTitle("RoohuX").setContentText("Preparing downloads…")
            active.size == 1 -> {
                val d = active[0]
                b.setContentTitle(d.fileName)
                if (d.status == DownloadStatus.QUEUED) b.setContentText("Queued")
                else if (d.totalBytes > 0) {
                    val pct = (d.downloadedBytes * 100 / d.totalBytes).toInt().coerceIn(0, 100)
                    b.setContentText("$pct%").setProgress(100, pct, false)
                } else b.setContentText("Downloading…").setProgress(0, 0, true)
            }
            else -> {
                b.setContentTitle("${active.size} downloads")
                val known = running.filter { it.totalBytes > 0 }
                val total = known.sumOf { it.totalBytes }
                if (total > 0) {
                    val pct = (known.sumOf { it.downloadedBytes } * 100 / total).toInt().coerceIn(0, 100)
                    b.setContentText("${running.size} active · $pct%").setProgress(100, pct, false)
                } else b.setContentText("${running.size} active").setProgress(0, 0, true)
            }
        }
        return b.build()
    }

    fun update(active: List<DownloadEntity>) { nm.notify(FOREGROUND_ID, progress(active)) }

    fun completed(id: Long, name: String) = result(id, name, "Download complete")
    fun failed(id: Long, name: String, reason: String) = result(id, name, "Download failed: $reason")

    private fun result(id: Long, title: String, text: String) {
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CH_RESULT)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        nm.notify(RESULT_BASE + id.toInt(), n)
    }

    companion object {
        const val FOREGROUND_ID = 1001
        private const val RESULT_BASE = 2000
        private const val CH_PROGRESS = "downloads_progress"
        private const val CH_RESULT = "downloads_result"
    }
}
