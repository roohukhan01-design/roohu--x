package com.roohu.roohux.features.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roohu.roohux.AppContainer
import com.roohu.roohux.core.database.DownloadEntity
import com.roohu.roohux.core.database.DownloadStatus
import com.roohu.roohux.features.browser.MediaClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibSort { DATE, NAME, SIZE }
enum class LibFilter { ALL, VIDEO, AUDIO }

data class LibraryItem(
    val uri: String,
    val title: String,
    val mime: String,
    val size: Long,
    val dateMs: Long,
    val isVideo: Boolean,
    val downloadId: Long?,
    val favorite: Boolean,
)

/**
 * Library = completed audio/video downloads (found via RoohuX's own records, so no storage permission is needed)
 * plus files the user added through the system file picker.
 */
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(LibSort.DATE)
    val filter = MutableStateFlow(LibFilter.ALL)
    val favoritesOnly = MutableStateFlow(false)

    private val source = combine(
        container.database.downloadDao().observeAll(),
        container.settings.imports,
        container.settings.favorites,
    ) { downloads, imports, favorites -> build(downloads, imports, favorites) }.flowOn(Dispatchers.IO)

    /** null while the first scan is running. */
    val items: StateFlow<List<LibraryItem>?> = combine(source, query, sort, filter, favoritesOnly) { all, q, s, f, fav ->
        all.filter { item ->
            (q.isBlank() || item.title.contains(q.trim(), ignoreCase = true)) &&
                (f == LibFilter.ALL || (f == LibFilter.VIDEO) == item.isVideo) &&
                (!fav || item.favorite)
        }.let { list ->
            when (s) {
                LibSort.DATE -> list.sortedByDescending { it.dateMs }
                LibSort.NAME -> list.sortedBy { it.title.lowercase() }
                LibSort.SIZE -> list.sortedByDescending { it.size }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(downloads: List<DownloadEntity>, imports: Set<String>, favorites: Set<String>): List<LibraryItem> {
        val out = ArrayList<LibraryItem>()
        for (d in downloads) {
            val uri = d.fileUri ?: continue
            if (d.status != DownloadStatus.COMPLETED) continue
            val kind = mediaKind(d.mimeType.orEmpty(), d.fileName) ?: continue
            out += LibraryItem(uri, d.fileName, d.mimeType.orEmpty(), d.downloadedBytes, d.completedAt ?: d.createdAt, kind, d.id, uri in favorites)
        }
        val resolver = container.appContext.contentResolver
        for (s in imports) {
            try {
                val uri = Uri.parse(s)
                var name = "Media file"
                var size = -1L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        size = c.getLong(1)
                    }
                }
                val mime = resolver.getType(uri).orEmpty()
                val kind = mediaKind(mime, name) ?: continue
                out += LibraryItem(s, name, mime, size, 0L, kind, null, s in favorites)
            } catch (e: Exception) {
                // File no longer accessible; skip it.
            }
        }
        return out
    }

    /** true = video, false = audio, null = not media. */
    private fun mediaKind(mime: String, name: String): Boolean? {
        val ext = MediaClassifier.extensionOf(name)
        return when {
            mime.startsWith("video/") || ext in MediaClassifier.VIDEO_EXT -> true
            mime.startsWith("audio/") || ext in MediaClassifier.AUDIO_EXT -> false
            else -> null
        }
    }

    fun toggleFavorite(item: LibraryItem) { viewModelScope.launch { container.settings.toggleFavorite(item.uri) } }

    fun addFromDevice(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val keep = uris.filter { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    true
                } catch (e: Exception) { false }
            }
            if (keep.isNotEmpty()) container.settings.addImports(keep.map { it.toString() })
            if (keep.size < uris.size) container.messages.tryEmit("Some files couldn't be added (access wasn't granted).")
        }
    }

    /** Downloads are deleted from storage; imported files are only removed from the library, never deleted. */
    fun remove(item: LibraryItem) {
        if (item.downloadId != null) container.downloads.delete(item.downloadId, deleteFile = true)
        else viewModelScope.launch { container.settings.removeImport(item.uri) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container) }
        }
    }
}
