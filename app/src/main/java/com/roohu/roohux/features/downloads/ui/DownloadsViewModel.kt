package com.roohu.roohux.features.downloads.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roohu.roohux.AppContainer
import com.roohu.roohux.core.database.DownloadEntity
import com.roohu.roohux.core.network.UrlUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(private val container: AppContainer) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> = container.database.downloadDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Returns an error message for invalid input, or null if the download was queued. */
    fun add(input: String): String? = when (val r = UrlUtils.normalize(input)) {
        is UrlUtils.Result.Invalid -> r.reason
        is UrlUtils.Result.Ok -> {
            viewModelScope.launch {
                val res = container.downloads.enqueue(r.url)
                container.messages.tryEmit(
                    when {
                        res.rejected -> "That link can't be downloaded: only http and https files are supported."
                        res.alreadyExists -> "That file is already in your downloads."
                        else -> "Download started."
                    },
                )
            }
            null
        }
    }

    fun pause(id: Long) = container.downloads.pause(id)
    fun resume(id: Long) = container.downloads.resume(id)
    fun cancel(id: Long) = container.downloads.cancel(id)
    fun delete(id: Long, deleteFile: Boolean) = container.downloads.delete(id, deleteFile)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DownloadsViewModel(container) }
        }
    }
}
