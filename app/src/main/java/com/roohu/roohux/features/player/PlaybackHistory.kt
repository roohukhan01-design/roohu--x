package com.roohu.roohux.features.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class PlayedItem(
    val uri: String,
    val title: String,
    val isVideo: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayed: Long,
)

object ResumePolicy {
    /** Where to start a saved item: from the beginning if barely started or nearly finished. */
    fun startPosition(savedMs: Long, durationMs: Long): Long = when {
        savedMs < 5_000L -> 0L
        durationMs > 0 && savedMs > durationMs - 10_000L -> 0L
        else -> savedMs
    }
}

/** Tab-separated, one item per line (pure Kotlin so it is unit-testable). */
object PlayedCodec {
    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun encode(items: List<PlayedItem>): String = items.joinToString("\n") {
        listOf(clean(it.uri), clean(it.title), if (it.isVideo) "v" else "a", it.positionMs, it.durationMs, it.lastPlayed).joinToString("\t")
    }

    fun decode(text: String?): List<PlayedItem> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size != 6) return@mapNotNull null
            val pos = p[3].toLongOrNull() ?: return@mapNotNull null
            val dur = p[4].toLongOrNull() ?: return@mapNotNull null
            val last = p[5].toLongOrNull() ?: return@mapNotNull null
            PlayedItem(p[0], p[1], p[2] == "v", pos, dur, last)
        }
    }

    /** Newest first, one entry per URI, capped. */
    fun upsert(current: List<PlayedItem>, item: PlayedItem, max: Int = 50): List<PlayedItem> =
        (listOf(item) + current.filter { it.uri != item.uri }).take(max)
}

private val Context.playbackStore by preferencesDataStore(name = "playback")

/** Local-only playback history (resume positions + "Continue" list). Nothing leaves the device. */
class PlaybackHistory(private val context: Context) {
    private val key = stringPreferencesKey("items")

    val recents: Flow<List<PlayedItem>> = context.playbackStore.data.map { PlayedCodec.decode(it[key]) }

    suspend fun positionFor(uri: String): Long =
        recents.first().firstOrNull { it.uri == uri }?.let { ResumePolicy.startPosition(it.positionMs, it.durationMs) } ?: 0L

    suspend fun record(item: PlayedItem) {
        context.playbackStore.edit { p -> p[key] = PlayedCodec.encode(PlayedCodec.upsert(PlayedCodec.decode(p[key]), item)) }
    }

    suspend fun clear() { context.playbackStore.edit { it.remove(key) } }
}
