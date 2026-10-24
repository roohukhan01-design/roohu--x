package com.roohu.roohux.services.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.roohu.roohux.RoohuXApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service whose only job is keeping the process alive and the progress notification current
 * while downloads are QUEUED/DOWNLOADING. The transfers themselves run in DownloadCoordinator.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as RoohuXApp).container
        try {
            ServiceCompat.startForeground(
                this,
                DownloadNotifier.FOREGROUND_ID,
                container.notifier.progress(emptyList()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            // Android refused to promote us (e.g. started from the background); downloads still run while the process lives.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        observer?.cancel()
        observer = scope.launch {
            container.database.downloadDao().observeActive().collect { active ->
                if (active.isEmpty()) stopSelf(startId) else container.notifier.update(active)
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 limits dataSync foreground services (~6h/day). Pause cleanly so the user can resume. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as RoohuXApp).container.downloads.pauseAll()
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
