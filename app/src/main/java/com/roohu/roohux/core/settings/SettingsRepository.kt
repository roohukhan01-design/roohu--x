package com.roohu.roohux.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class SearchEngine(val label: String, val template: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val wifiOnly: Boolean = false,
    val maxSimultaneous: Int = 3,
    val reduceMotion: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val autoplay: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val maxSimultaneous = intPreferencesKey("max_simultaneous")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val searchEngine = stringPreferencesKey("search_engine")
        val autoplay = booleanPreferencesKey("autoplay")
        val favorites = stringSetPreferencesKey("library_favorites")
        val imports = stringSetPreferencesKey("library_imports")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "") }.getOrDefault(d.theme),
            wifiOnly = p[Keys.wifiOnly] ?: d.wifiOnly,
            maxSimultaneous = (p[Keys.maxSimultaneous] ?: d.maxSimultaneous).coerceIn(1, 5),
            reduceMotion = p[Keys.reduceMotion] ?: d.reduceMotion,
            autoplay = p[Keys.autoplay] ?: d.autoplay,
            searchEngine = runCatching { SearchEngine.valueOf(p[Keys.searchEngine] ?: "") }.getOrDefault(d.searchEngine),
        )
    }

    /** Library favourites and user-imported media (content URIs as strings). */
    val favorites: Flow<Set<String>> = context.dataStore.data.map { it[Keys.favorites] ?: emptySet() }
    val imports: Flow<Set<String>> = context.dataStore.data.map { it[Keys.imports] ?: emptySet() }

    suspend fun setTheme(mode: ThemeMode) { context.dataStore.edit { it[Keys.theme] = mode.name } }
    suspend fun setWifiOnly(value: Boolean) { context.dataStore.edit { it[Keys.wifiOnly] = value } }
    suspend fun setMaxSimultaneous(value: Int) { context.dataStore.edit { it[Keys.maxSimultaneous] = value.coerceIn(1, 5) } }
    suspend fun setReduceMotion(value: Boolean) { context.dataStore.edit { it[Keys.reduceMotion] = value } }
    suspend fun setAutoplay(value: Boolean) { context.dataStore.edit { it[Keys.autoplay] = value } }
    suspend fun setSearchEngine(engine: SearchEngine) { context.dataStore.edit { it[Keys.searchEngine] = engine.name } }

    suspend fun toggleFavorite(key: String) {
        context.dataStore.edit { p ->
            val s = (p[Keys.favorites] ?: emptySet()).toMutableSet()
            if (!s.add(key)) s.remove(key)
            p[Keys.favorites] = s
        }
    }

    suspend fun addImports(keys: Collection<String>) {
        context.dataStore.edit { p -> p[Keys.imports] = (p[Keys.imports] ?: emptySet()) + keys }
    }

    suspend fun removeImport(key: String) {
        context.dataStore.edit { p ->
            p[Keys.imports] = (p[Keys.imports] ?: emptySet()) - key
            p[Keys.favorites] = (p[Keys.favorites] ?: emptySet()) - key
        }
    }
}
