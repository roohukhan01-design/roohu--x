package com.roohu.roohux.features.downloads.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.roohu.roohux.core.database.DownloadDao
import com.roohu.roohux.core.database.DownloadEntity
import com.roohu.roohux.core.database.DownloadError
import com.roohu.roohux.core.database.DownloadStatus
import com.roohu.roohux.core.settings.SettingsRepository
import com.roohu.roohux.core.storage.FileNames
import com.roohu.roohux.services.download.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class EnqueueResult(val id: Long, val alreadyExists: Boolean, val rejected: Boolean = false)

/** Owns the queue: starts up to N downloads at once and handles pause/resume/cancel/retry/delete. */
class DownloadCoordinator(
    private val context: Context,
    private val dao: DownloadDao,
    private val engine: DownloadEngine,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val lock = Mutex()
    private val enqueueLock = Mutex()

    /** Refuses to create a second copy of a download that is already queued/running/paused. */
    suspend fun enqueue(url: String, referer: String? = null, userAgent: String? = null): EnqueueResult =
        enqueueLock.withLock {
            val downloadUrl = normalizeKnownFileShareUrl(url)
            val scheme = downloadUrl.toHttpUrlOrNull()?.scheme
            if (scheme != "http" && scheme != "https") {
                // blob:, data:, file: etc. can't be fetched by the download engine; never create a doomed entry.
                return@withLock EnqueueResult(-1L, alreadyExists = false, rejected = true)
            }
            val open = dao.findOpenByUrl(downloadUrl)
            if (open != null) {
                if (open.status == DownloadStatus.PAUSED) resume(open.id)
                return@withLock EnqueueResult(open.id, alreadyExists = true)
            }
            val id = dao.insert(
                DownloadEntity(
                    url = downloadUrl,
                    fileName = FileNames.fromUrl(downloadUrl),
                    referer = referer,
                    userAgent = userAgent,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            kick()
            EnqueueResult(id, alreadyExists = false)
        }

    fun pause(id: Long) {
        scope.launch {
            jobs[id]?.cancelAndJoin()
            dao.setStatusIfActive(id, DownloadStatus.PAUSED, null, null)
        }
    }

    /**
     * Convert common Google Drive share/preview links to the documented direct-download endpoint.
     * This only works for files the current user is allowed to download; Drive may still require
     * confirmation/authentication for large, restricted, or abuse-scanned files.
     */
    private fun normalizeKnownFileShareUrl(raw: String): String {
        val uri = raw.toHttpUrlOrNull() ?: return raw
        val host = uri.host.lowercase()
        if (host != "drive.google.com" && host != "www.drive.google.com") return raw
        val path = uri.encodedPath
        val id = Regex("""/file/d/([^/]+)""").find(path)?.groupValues?.getOrNull(1)
            ?: uri.queryParameter("id")
            ?: return raw
        if (id.isBlank()) return raw
        return uri.newBuilder()
            .encodedPath("/uc")
            .query(null)
            .addQueryParameter("export", "download")
            .addQueryParameter("id", id)
            .build()
            .toString()
    }

    /** Pauses everything (used when Android's foreground-service time limit is hit). */
    fun pauseAll() {
        scope.launch {
            jobs.values.toList().forEach { it.cancelAndJoin() }
            (dao.byStatus(DownloadStatus.DOWNLOADING) + dao.byStatus(DownloadStatus.QUEUED)).forEach {
                dao.setStatus(it.id, DownloadStatus.PAUSED, null, "Paused by Android's background time limit")
            }
        }
    }

    /** Also used for Retry. Keeps the partial file only when the server is known to support Range. */
    fun resume(id: Long) {
        scope.launch {
            val e = dao.get(id) ?: return@launch
            if (e.status != DownloadStatus.PAUSED && e.status != DownloadStatus.FAILED && e.status != DownloadStatus.CANCELED) return@launch
            if (e.resumable != 1) {
                engine.cleanup(id)
                dao.setProgress(id, 0, e.totalBytes, 0)
            }
            dao.setStatus(id, DownloadStatus.QUEUED, null, null)
            kick()
        }
    }

    fun cancel(id: Long) {
        scope.launch {
            jobs[id]?.cancelAndJoin()
            val e = dao.get(id) ?: return@launch
            if (e.status == DownloadStatus.COMPLETED) return@launch
            engine.cleanup(id)
            dao.setProgress(id, 0, e.totalBytes, 0)
            dao.setStatus(id, DownloadStatus.CANCELED, null, null)
        }
    }

    fun delete(id: Long, deleteFile: Boolean) {
        scope.launch {
            jobs[id]?.cancelAndJoin()
            val uriString = dao.get(id)?.fileUri
            engine.cleanup(id)
            if (deleteFile && uriString != null) {
                runCatching { context.contentResolver.delete(uriString.toUri(), null, null) }
                    .onFailure { Log.w(TAG, "Could not delete file: ${it.javaClass.simpleName}") }
            }
            dao.delete(id)
        }
    }

    /** Called when the process starts: anything left DOWNLOADING/QUEUED lost its worker. */
    suspend fun recoverInterrupted() {
        (dao.byStatus(DownloadStatus.DOWNLOADING) + dao.byStatus(DownloadStatus.QUEUED)).forEach { e ->
            if (e.resumable == 1 || e.status == DownloadStatus.QUEUED || e.downloadedBytes == 0L) {
                dao.setStatus(e.id, DownloadStatus.PAUSED, null, null)
            } else {
                dao.setStatus(e.id, DownloadStatus.FAILED, DownloadError.CONNECTION_LOST, "App was closed during the download")
            }
        }
    }

    /** Start queued downloads up to the configured limit. Safe to call any time. */
    fun pump() {
        scope.launch {
            lock.withLock {
                val free = settings.settings.first().maxSimultaneous - jobs.size
                if (free <= 0) return@withLock
                dao.byStatus(DownloadStatus.QUEUED).filter { !jobs.containsKey(it.id) }.take(free).forEach { start(it.id) }
            }
        }
    }

    private fun start(id: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                engine.run(id)
            } finally {
                jobs.remove(id)
                pump()
            }
        }
        jobs[id] = job
        job.start()
    }

    private fun kick() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.javaClass.simpleName}")
        }
        pump()
    }

    private companion object { const val TAG = "DownloadCoordinator" }
}
