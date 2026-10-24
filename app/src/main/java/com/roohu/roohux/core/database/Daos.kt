package com.roohu.roohux.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','DOWNLOADING') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC")
    suspend fun byStatus(status: DownloadStatus): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE url = :url AND status IN ('QUEUED','DOWNLOADING','PAUSED') LIMIT 1")
    suspend fun findOpenByUrl(url: String): DownloadEntity?

    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE downloads SET status = :status, error = :error, errorDetail = :detail, speedBps = 0 WHERE id = :id")
    suspend fun setStatus(id: Long, status: DownloadStatus, error: DownloadError?, detail: String?)

    /** Only changes rows still QUEUED/DOWNLOADING, so a late pause can't overwrite COMPLETED. */
    @Query("UPDATE downloads SET status = :status, error = :error, errorDetail = :detail, speedBps = 0 WHERE id = :id AND status IN ('QUEUED','DOWNLOADING')")
    suspend fun setStatusIfActive(id: Long, status: DownloadStatus, error: DownloadError?, detail: String?)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, speedBps = :speed WHERE id = :id")
    suspend fun setProgress(id: Long, downloaded: Long, total: Long, speed: Long)

    @Query("UPDATE downloads SET resumable = :resumable, totalBytes = :total, mimeType = :mime, fileName = :name WHERE id = :id")
    suspend fun setMeta(id: Long, resumable: Int, total: Long, mime: String?, name: String)

    @Query("UPDATE downloads SET status = :status, fileUri = :uri, fileName = :name, downloadedBytes = :size, totalBytes = :size, speedBps = 0, error = NULL, errorDetail = NULL, completedAt = :at WHERE id = :id")
    suspend fun complete(id: Long, status: DownloadStatus, uri: String, name: String, size: Long, at: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}
