package com.roohu.roohux.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED }

enum class DownloadError(val userMessage: String) {
    NO_NETWORK("Can't reach the server. Check your internet connection or DNS."),
    TIMEOUT("The server took too long to respond."),
    CONNECTION_LOST("The connection was interrupted before the file finished."),
    SSL_ERROR("Secure connection failed. The site's certificate isn't trusted, so RoohuX won't download from it."),
    AUTH_REQUIRED("This file requires you to sign in. RoohuX can't download it without authorization."),
    FORBIDDEN("The server refused the request (403). The link may have expired or may require sign-in."),
    NOT_FOUND("The file no longer exists on the server."),
    SERVER_ERROR("The server had a problem. Try again later."),
    SERVER_REJECTED("The server rejected the request."),
    NOT_A_FILE("The link returned a web page instead of a file. It may need sign-in or may have expired."),
    UNSUPPORTED_STREAM("This is a streaming playlist (HLS/DASH). Playlist downloads aren't supported yet."),
    INSUFFICIENT_STORAGE("There isn't enough free storage space."),
    STORAGE_WRITE("Couldn't save the file to Downloads/RoohuX."),
    INTEGRITY("The downloaded size doesn't match what the server reported."),
    WIFI_REQUIRED("Wi-Fi-only mode is on and Wi-Fi isn't available."),
    UNKNOWN("The download failed for an unexpected reason."),
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val speedBps: Long = 0,
    /** -1 unknown, 0 server can't resume, 1 server supports HTTP range requests. */
    val resumable: Int = -1,
    val error: DownloadError? = null,
    val errorDetail: String? = null,
    val fileUri: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
)

// Phase 1 schema for the browser phase; not used by any screen yet.
@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long,
)
