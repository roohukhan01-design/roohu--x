package com.roohu.roohux.features.browser

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class BrowserPanel { TABS, BOOKMARKS, HISTORY, MEDIA }

/** One browser tab. Observable fields drive the Compose UI; the WebView itself lives as long as the tab. */
class BrowserTab(val id: Long, val webView: WebView) {
    var title by mutableStateOf("New tab")
    var url by mutableStateOf("")
    var blank by mutableStateOf(true)
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var httpStatus by mutableStateOf<Int?>(null)
    var media by mutableStateOf<List<DetectedMedia>>(emptyList())
    var drm by mutableStateOf(false)
    var chipDismissed by mutableStateOf(false)
    var findCurrent by mutableIntStateOf(0)
    var findTotal by mutableIntStateOf(0)

    var defaultUserAgent: String = ""
    var lastHistoryUrl: String? = null
}
