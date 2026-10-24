package com.roohu.roohux.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roohu.roohux.AppContainer
import com.roohu.roohux.core.settings.AppSettings
import com.roohu.roohux.core.settings.SearchEngine
import com.roohu.roohux.core.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { container.settings.setTheme(mode) } }
    fun setWifiOnly(v: Boolean) { viewModelScope.launch { container.settings.setWifiOnly(v) } }
    fun setReduceMotion(v: Boolean) { viewModelScope.launch { container.settings.setReduceMotion(v) } }
    fun setAutoplay(v: Boolean) { viewModelScope.launch { container.settings.setAutoplay(v) } }
    fun clearPlaybackHistory() { viewModelScope.launch { container.playback.clear() }; container.messages.tryEmit("Playback history cleared.") }
    fun setSearchEngine(e: SearchEngine) { viewModelScope.launch { container.settings.setSearchEngine(e) } }
    fun setMaxSimultaneous(n: Int) {
        viewModelScope.launch {
            container.settings.setMaxSimultaneous(n)
            container.downloads.pump() // a higher limit may release queued items immediately
        }
    }

    fun clearHistory() { container.browser.clearHistory(); container.messages.tryEmit("Browsing history cleared.") }
    fun clearCookies() { container.browser.clearCookies(); container.messages.tryEmit("Cookies cleared.") }
    fun clearCache() { container.browser.clearCache(); container.messages.tryEmit("Browser cache cleared.") }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
