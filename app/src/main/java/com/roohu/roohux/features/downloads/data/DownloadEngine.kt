package com.roohu.roohux.features.downloads.data

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.roohu.roohux.core.database.DownloadDao
import com.roohu.roohux.core.database.DownloadEntity
import com.roohu.roohux.core.database.DownloadError
import com.roohu.roohux.core.database.DownloadStatus
import com.roohu.roohux.core.network.ContentRange
import com.roohu.roohux.core.settings.SettingsRepository
import com.roohu.roohux.core.storage.FileNames
import com.roohu.roohux.services.download.DownloadNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Single-connection HTTP(S) downloader with resume (HTTP Range), retry with backoff, disk-space check,
 * and atomic publication into MediaStore Downloads/RoohuX. Segmented/parallel download is NOT implemented.
 */
class DownloadEngine(
    private val context: Context,
    private val dao: DownloadDao,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val notifier: DownloadNotifier,
) {
    fun partFile(id: Long): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "partial")
        return File(dir, "$id.part")
    }

    fun validatorFile(id: Long): File = File(partFile(id).parentFile, "$id.validator")

    /** Removes the partial file and its validator. */
    fun cleanup(id: Long) {
        partFile(id).delete()
        validatorFile(id).delete()
    }

    /** Runs until the download completes, fails, or the calling coroutine is cancelled (pause/cancel). */
    suspend fun run(id: Long) {
        val item = dao.get(id) ?: return
        val part = partFile(id)
        dao.setStatus(id, DownloadStatus.DOWNLOADING, null, null)
        try {
            if (settings.settings.first().wifiOnly && !isUnmetered()) throw DownloadException(DownloadError.WIFI_REQUIRED)
            var attempt = 0
            while (true) {
                try {
                    transfer(item, part)
                    break
                } catch (e: DownloadException) {
                    if (!e.retryable || attempt >= MAX_RETRIES) throw e
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    val mapped = mapIo(e)
                    if (!mapped.retryable || attempt >= MAX_RETRIES) throw mapped
                }
                attempt++
                delay(1000L shl attempt) // 2s, 4s, 8s
            }
            publish(id, part)
            dao.get(id)?.let { notifier.completed(id, it.fileName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            fail(id, e.error, e.detail)
        } catch (e: IOException) {
            val m = mapIo(e)
            fail(id, m.error, m.detail)
        } catch (e: Exception) {
            fail(id, DownloadError.UNKNOWN, e.javaClass.simpleName)
        }
    }

    private suspend fun fail(id: Long, error: DownloadError, detail: String?) {
        dao.setStatus(id, DownloadStatus.FAILED, error, detail)
        dao.get(id)?.let { notifier.failed(id, it.fileName, error.userMessage) }
    }

    private suspend fun transfer(item: DownloadEntity, part: File) {
        withContext(Dispatchers.IO) {
            val latest = dao.get(item.id) ?: return@withContext
            var existing = if (part.exists()) part.length() else 0L
            if (existing > 0 && latest.resumable == 0) { part.delete(); existing = 0 }

            val request = Request.Builder()
                .url(item.url)
                .header("Accept-Encoding", "identity") // keep Content-Length/Range byte-accurate
                .header("User-Agent", item.userAgent ?: DEFAULT_USER_AGENT)
                .apply {
                    item.referer?.let { header("Referer", it) }
                    if (existing > 0) {
                        header("Range", "bytes=$existing-")
                        // If the remote file changed since we started, the server answers 200 (full body) and we restart cleanly.
                        validatorFile(item.id).takeIf { it.exists() }?.readText()?.trim()
                            ?.takeIf { it.isNotEmpty() }?.let { header("If-Range", it) }
                    }
                }
                .build()
            val call = client.newCall(request)
            // Blocking OkHttp reads aren't interruptible by coroutine cancellation; cancel the call explicitly.
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                call.execute().use { resp -> receive(item, latest, resp, part, existing) }
            } finally {
                watcher.cancel()
            }
        }
    }

    private suspend fun receive(item: DownloadEntity, latest: DownloadEntity, resp: Response, part: File, existing: Long) {
        val code = resp.code
        if (code == 416 && existing > 0) {
            val serverTotal = Regex("bytes\\s+\\*/(\\d+)").find(resp.header("Content-Range") ?: "")
                ?.groupValues?.get(1)?.toLongOrNull()
            if (serverTotal != null && serverTotal == existing) {
                // Everything is already on disk (e.g. we were interrupted while publishing): just publish it.
                dao.setProgress(item.id, existing, existing, 0)
                return
            }
            part.delete()
            throw DownloadException(DownloadError.CONNECTION_LOST, "Range not satisfiable; restarting", retryable = true)
        }
        if (code !in 200..299) throw httpError(code)
        val body = resp.body ?: throw DownloadException(DownloadError.SERVER_REJECTED, "Empty response")
        val contentType = resp.header("Content-Type")
        val ctype = contentType?.substringBefore(';')?.trim()?.lowercase()
        // Reject server landing/error pages before trusting their URL or Content-Disposition filename.
        // File-sharing services sometimes return HTML confirmation pages with a misleading filename.
        if (ctype == "text/html" || ctype == "application/xhtml+xml" || ctype == "application/vnd.yt-ump") {
            throw DownloadException(
                DownloadError.NOT_A_FILE,
                "The server returned a webpage, not the media file. Open the page and use its official download option, then retry the resulting direct link.",
            )
        }

        var start = existing
        val total: Long
        val resumable: Boolean
        if (code == 206) {
            val range = ContentRange.parse(resp.header("Content-Range"))
            if (range == null || range.start != existing) {
                part.delete()
                throw DownloadException(DownloadError.CONNECTION_LOST, "Unexpected range response", retryable = true)
            }
            val bodyLen = body.contentLength()
            total = range.total ?: if (bodyLen > 0) existing + bodyLen else -1L
            resumable = true
        } else {
            if (existing > 0) { part.delete(); start = 0 } // server ignored Range: start over
            total = body.contentLength()
            resumable = total > 0 && resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
        }

        val fresh = start == 0L
        val name: String
        val mime: String
        if (fresh) {
            val streamUrl = item.url.substringBefore('?').lowercase().let { it.endsWith(".m3u8") || it.endsWith(".mpd") }
            if (streamUrl || (ctype != null && (ctype.contains("mpegurl") || ctype.contains("dash+xml")))) {
                throw DownloadException(DownloadError.UNSUPPORTED_STREAM, ctype)
            }
            val determined = determineName(item, resp)
            mime = resolveMime(contentType, determined)
            val ext = determined.substringAfterLast('.', "").lowercase()
            if (ctype != null && (ctype == "text/html" || ctype == "application/xhtml+xml") && ext !in HTML_EXT) {
                throw DownloadException(DownloadError.NOT_A_FILE, ctype)
            }
            // Keep the name (and its extension) exactly as the website/server gave it; MIME is stored separately.
            name = determined
        } else {
            name = latest.fileName
            mime = latest.mimeType ?: OCTET
        }

        val dir = part.parentFile ?: throw DownloadException(DownloadError.STORAGE_WRITE)
        dir.mkdirs()
        if (total > 0 && dir.usableSpace < (total - start) + SPACE_MARGIN) {
            throw DownloadException(DownloadError.INSUFFICIENT_STORAGE)
        }

        if (fresh) {
            val validator = resp.header("ETag")?.takeIf { !it.startsWith("W/") } ?: resp.header("Last-Modified")
            val vf = validatorFile(item.id)
            if (validator != null) vf.writeText(validator) else vf.delete()
        }
        dao.setMeta(item.id, if (resumable) 1 else 0, total, mime, name)
        dao.setProgress(item.id, start, total, 0)

        var downloaded = start
        var lastTick = System.nanoTime()
        var lastBytes = downloaded
        var speed = 0L
        FileOutputStream(part, start > 0).use { out ->
            val buf = ByteArray(BUFFER)
            body.byteStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    downloaded += n
                    val now = System.nanoTime()
                    if (now - lastTick >= TICK_NS) {
                        val inst = (downloaded - lastBytes) * 1_000_000_000L / (now - lastTick)
                        speed = if (speed == 0L) inst else (speed * 7 + inst * 3) / 10
                        dao.setProgress(item.id, downloaded, total, speed)
                        lastTick = now
                        lastBytes = downloaded
                    }
                }
            }
            out.flush()
        }
        if (total > 0 && downloaded != total) {
            throw DownloadException(DownloadError.CONNECTION_LOST, "Received $downloaded of $total bytes", retryable = true)
        }
        dao.setProgress(item.id, downloaded, if (total > 0) total else downloaded, 0)
    }

    private fun determineName(item: DownloadEntity, resp: Response): String {
        val fromHeader = FileNames.fromContentDisposition(resp.header("Content-Disposition"))
        if (fromHeader != null) return fromHeader
        val fromFinal = FileNames.fromUrl(resp.request.url.toString())
        if ('.' in fromFinal) return fromFinal
        val fromOriginal = FileNames.fromUrl(item.url)
        return if ('.' in fromOriginal) fromOriginal else fromFinal
    }

    private fun resolveMime(header: String?, name: String): String {
        val ct = header?.substringBefore(';')?.trim()?.lowercase()
        if (!ct.isNullOrEmpty() && ct != OCTET && ct != "binary/octet-stream") return ct
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: OCTET
    }

    /** Copies the finished .part file into MediaStore as a pending item, then clears IS_PENDING (atomic for other apps). */
    private suspend fun publish(id: Long, part: File) = withContext(Dispatchers.IO) {
        val e = dao.get(id) ?: return@withContext
        if (e.totalBytes > 0 && part.length() != e.totalBytes) {
            cleanup(id) // never publish a truncated/oversized file; Retry starts clean
            throw DownloadException(DownloadError.INTEGRITY, "Expected ${e.totalBytes} bytes, found ${part.length()}")
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, e.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, e.mimeType ?: OCTET)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RoohuX")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw DownloadException(DownloadError.STORAGE_WRITE, "MediaStore insert returned null")
        try {
            val out = resolver.openOutputStream(uri) ?: throw DownloadException(DownloadError.STORAGE_WRITE, "No output stream")
            out.use { o -> part.inputStream().use { i ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    ensureActive()
                    val n = i.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                }
            } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            if (t is IOException && t.message?.contains("ENOSPC") == true) {
                throw DownloadException(DownloadError.INSUFFICIENT_STORAGE)
            }
            throw t
        }
        val finalName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: e.fileName
        val size = part.length()
        cleanup(id)
        dao.complete(id, DownloadStatus.COMPLETED, uri.toString(), finalName, size, System.currentTimeMillis())
    }

    private fun httpError(code: Int): DownloadException = when (code) {
        401, 407 -> DownloadException(DownloadError.AUTH_REQUIRED, "HTTP $code")
        403 -> DownloadException(DownloadError.FORBIDDEN, "HTTP 403")
        404, 410 -> DownloadException(DownloadError.NOT_FOUND, "HTTP $code")
        429 -> DownloadException(DownloadError.SERVER_ERROR, "HTTP 429 (rate limited)", retryable = true)
        in 500..599 -> DownloadException(DownloadError.SERVER_ERROR, "HTTP $code", retryable = true)
        else -> DownloadException(DownloadError.SERVER_REJECTED, "HTTP $code")
    }

    private fun mapIo(e: IOException): DownloadException = when {
        e is UnknownHostException -> DownloadException(DownloadError.NO_NETWORK, "Host not found", retryable = true)
        e is SocketTimeoutException -> DownloadException(DownloadError.TIMEOUT, "Timed out", retryable = true)
        e is SSLException -> DownloadException(DownloadError.SSL_ERROR, e.javaClass.simpleName)
        e.message?.contains("ENOSPC") == true -> DownloadException(DownloadError.INSUFFICIENT_STORAGE)
        else -> DownloadException(DownloadError.CONNECTION_LOST, e.javaClass.simpleName, retryable = true)
    }

    private fun isUnmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val BUFFER = 64 * 1024
        const val TICK_NS = 500_000_000L
        const val SPACE_MARGIN = 8L * 1024 * 1024
        const val OCTET = "application/octet-stream"
        val HTML_EXT = setOf("htm", "html", "xhtml")
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) RoohuX/0.1"
    }
}
