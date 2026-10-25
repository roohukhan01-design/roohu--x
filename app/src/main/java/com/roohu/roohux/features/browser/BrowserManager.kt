package com.roohu.roohux.features.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roohu.roohux.core.database.AppDatabase
import com.roohu.roohux.core.database.BookmarkEntity
import com.roohu.roohux.core.database.HistoryEntity
import com.roohu.roohux.core.network.ContentRange
import com.roohu.roohux.core.network.UrlUtils
import com.roohu.roohux.core.settings.SearchEngine
import com.roohu.roohux.core.settings.SettingsRepository
import com.roohu.roohux.features.downloads.data.DownloadCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONTokener

data class MediaProbe(val sizeBytes: Long?, val contentType: String?)

/**
 * Owns all browser tabs for the life of the process, so switching app sections never destroys them.
 * Media detection only OBSERVES requests (shouldInterceptRequest returns null) and page media elements;
 * it never claims it can see every request.
 */
class BrowserManager(
    private val app: Context,
    private val db: AppDatabase,
    private val downloads: DownloadCoordinator,
    private val http: OkHttpClient,
    settings: SettingsRepository,
    private val emit: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextId = 1L

    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableLongStateOf(-1L)
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    var pendingPanel by mutableStateOf<BrowserPanel?>(null)
    var onPlay: ((url: String, title: String, isVideo: Boolean) -> Unit)? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    val active: BrowserTab? get() = tabs.firstOrNull { it.id == activeId }

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        db.bookmarkDao().observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<HistoryEntity>> =
        db.historyDao().observeRecent(200).stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val searchTemplate: StateFlow<String> =
        settings.settings.map { it.searchEngine.template }.stateIn(scope, SharingStarted.Eagerly, SearchEngine.DUCKDUCKGO.template)

    fun say(message: String) = emit(message)

    // ---- tabs -------------------------------------------------------------------------------

    fun ensureTab() { if (tabs.isEmpty()) newTab() }

    fun newTab(input: String? = null, select: Boolean = true): BrowserTab {
        val webView = WebView(MutableContextWrapper(app))
        val tab = BrowserTab(nextId++, webView)
        configure(tab)
        tabs.add(tab)
        if (select) activeId = tab.id
        if (input != null) load(tab, input)
        return tab
    }

    fun select(id: Long) { activeId = id }

    fun closeTab(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs.removeAt(index)
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.destroy()
        if (activeId == id) activeId = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.id ?: -1L
        if (tabs.isEmpty()) newTab()
    }

    /** Used by Home: loads [input] in the current tab (creating one if needed). */
    fun go(input: String) {
        val tab = active ?: newTab()
        load(tab, input)
    }

    fun load(tab: BrowserTab, input: String) {
        val url = UrlUtils.resolveInput(input, searchTemplate.value) ?: return
        tab.blank = false
        tab.error = null
        tab.url = url
        tab.webView.loadUrl(url)
    }

    fun reloadOrStop(tab: BrowserTab) {
        if (tab.blank) return
        if (tab.loading) tab.webView.stopLoading() else { tab.error = null; tab.webView.reload() }
    }

    fun setDesktop(tab: BrowserTab, enabled: Boolean) {
        tab.desktop = enabled
        tab.webView.settings.userAgentString = if (enabled) DESKTOP_UA else tab.defaultUserAgent
        if (!tab.blank) tab.webView.reload()
    }

    fun toggleBookmark(tab: BrowserTab) {
        val u = tab.url
        if (u.isBlank()) return
        scope.launch {
            if (bookmarks.value.any { it.url == u }) {
                db.bookmarkDao().deleteByUrl(u)
                emit("Bookmark removed.")
            } else {
                db.bookmarkDao().upsert(BookmarkEntity(title = tab.title.ifBlank { u }, url = u, createdAt = System.currentTimeMillis()))
                emit("Bookmark added.")
            }
        }
    }

    fun deleteBookmark(id: Long) { scope.launch { db.bookmarkDao().delete(id) } }
    fun deleteHistory(id: Long) { scope.launch { db.historyDao().delete(id) } }
    fun clearHistory() { scope.launch { db.historyDao().clear() } }
    fun clearCookies() { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
    fun clearCache() { tabs.forEach { it.webView.clearCache(true) } }

    fun exitFullscreen() {
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    // ---- downloads & media ------------------------------------------------------------------

    fun startDownload(url: String, referer: String?, userAgent: String?) {
        scope.launch {
            val r = downloads.enqueue(url, referer, userAgent)
            emit(if (r.alreadyExists) "That file is already in your downloads." else "Download started.")
        }
    }

    fun downloadMedia(tab: BrowserTab, media: DetectedMedia) =
        startDownload(media.url, tab.url.ifBlank { null }, tab.webView.settings.userAgentString)

    fun playMedia(media: DetectedMedia) { onPlay?.invoke(media.url, media.name, media.kind != MediaKind.DIRECT_AUDIO) }

    /** Reads size/type from the server (HEAD, falling back to a 1-byte ranged GET). Returns nulls if unknown. */
    suspend fun probe(url: String, referer: String?, userAgent: String?): MediaProbe = withContext(Dispatchers.IO) {
        fun request(useHead: Boolean): Request = Request.Builder().url(url).apply {
            if (useHead) head() else header("Range", "bytes=0-0")
            header("Accept-Encoding", "identity")
            userAgent?.let { header("User-Agent", it) }
            referer?.let { header("Referer", it) }
        }.build()
        try {
            http.newCall(request(true)).execute().use { r ->
                val type = r.header("Content-Type")?.substringBefore(';')?.trim()
                val normalized = type?.lowercase().orEmpty()
                val looksLikePage = normalized == "text/html" || normalized == "application/xhtml+xml" ||
                    normalized.contains("yt-ump")
                val size = r.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                // Some hosts (notably file-sharing services) answer HEAD with a landing/confirmation
                // page. Only trust HEAD when it looks like a file response; otherwise inspect a ranged GET.
                if (r.isSuccessful && !looksLikePage && (size != null || !type.isNullOrBlank())) {
                    return@withContext MediaProbe(size, type)
                }
            }
            http.newCall(request(false)).execute().use { r ->
                val type = r.header("Content-Type")?.substringBefore(';')?.trim()
                val total = ContentRange.parse(r.header("Content-Range"))?.total
                    ?: if (r.code == 200) r.header("Content-Length")?.toLongOrNull() else null
                MediaProbe(total, type)
            }
        } catch (e: Exception) {
            MediaProbe(null, null)
        }
    }

    /** Returns true only when the response looks like media or a downloadable binary, not a webpage. */
    suspend fun isDownloadableResponse(url: String, referer: String?, userAgent: String?): Boolean {
        val probe = probe(url, referer, userAgent)
        val type = probe.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (type == "text/html" || type == "application/xhtml+xml" || type.contains("yt-ump")) return false
        return type.startsWith("video/") || type.startsWith("audio/") ||
            type in setOf("application/octet-stream", "application/zip", "application/pdf") ||
            type.contains("mpegurl") || type == "application/dash+xml"
    }

    // ---- WebView setup ----------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(tab: BrowserTab) {
        val wv = tab.webView
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        tab.defaultUserAgent = wv.settings.userAgentString
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)

        wv.setDownloadListener { url, userAgent, _, _, _ ->
            if (url.startsWith("http://") || url.startsWith("https://")) {
                startDownload(url, tab.url.ifBlank { null }, userAgent)
            } else {
                emit("This kind of link (${url.substringBefore(':')}:) can't be downloaded.")
            }
        }
        wv.setFindListener { current, total, _ -> tab.findCurrent = current; tab.findTotal = total }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https" || scheme == "about") return false
                // Other schemes (intent:, mailto:, tel:, market: ...) go to other apps, only on a real user tap.
                if (request.hasGesture()) openExternal(uri.toString())
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tab.blank = false
                tab.url = url ?: tab.url
                tab.loading = true
                tab.error = null
                tab.httpStatus = null
                tab.media = emptyList()
                tab.drm = false
                tab.chipDismissed = false
                syncNav(tab)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                tab.loading = false
                tab.url = url ?: tab.url
                view?.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
                syncNav(tab)
                recordHistory(tab)
                scanPage(tab)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (url != null) tab.url = url
                syncNav(tab)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true && error != null) {
                    tab.loading = false
                    tab.error = describeError(error.errorCode)
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 500) {
                    tab.httpStatus = errorResponse?.statusCode
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel() // never proceed past an invalid certificate
                tab.loading = false
                tab.error = "Secure connection failed: the site's certificate isn't trusted, so RoohuX won't load it."
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request?.url?.toString()?.let { observe(tab, it) }
                return null // observe only; never alters or blocks traffic
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { tab.progress = newProgress }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) { callback?.onCustomViewHidden(); return }
                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = view
                fullscreenCallback = callback
            }

            override fun onHideCustomView() { exitFullscreen() }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (!isUserGesture || resultMsg == null) return false // block script-opened popups
                val popup = newTab(null, select = true)
                popup.blank = false
                (resultMsg.obj as WebView.WebViewTransport).webView = popup.webView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                tabs.firstOrNull { it.webView === window }?.let { closeTab(it.id) }
            }

            // Sensitive website permissions are denied; there is no permission UI yet.
            override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }
        }
    }

    private fun syncNav(tab: BrowserTab) {
        tab.canGoBack = tab.webView.canGoBack()
        tab.canGoForward = tab.webView.canGoForward()
    }

    private fun recordHistory(tab: BrowserTab) {
        val u = tab.url
        if (u.isBlank() || tab.error != null || u == tab.lastHistoryUrl) return
        tab.lastHistoryUrl = u
        scope.launch { db.historyDao().insert(HistoryEntity(title = tab.title.ifBlank { u }, url = u, visitedAt = System.currentTimeMillis())) }
    }

    private fun openExternal(url: String) {
        try {
            val intent = if (url.startsWith("intent:", ignoreCase = true)) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null // strip explicit targets from untrusted intent: URIs
            intent.selector = null
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (e: Exception) {
            emit("No app can open that link.")
        }
    }

    private fun describeError(code: Int): String = when {
        !isOnline() -> "No internet connection. Check your network and try again."
        code == WebViewClient.ERROR_HOST_LOOKUP -> "The server can't be found (DNS error). Check the address."
        code == WebViewClient.ERROR_CONNECT || code == WebViewClient.ERROR_IO -> "Couldn't connect to the server."
        code == WebViewClient.ERROR_TIMEOUT -> "The server took too long to respond."
        code == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "Secure connection (TLS) failed."
        code == WebViewClient.ERROR_UNSUPPORTED_SCHEME || code == WebViewClient.ERROR_BAD_URL -> "That address type isn't supported."
        code == WebViewClient.ERROR_REDIRECT_LOOP -> "The page redirects in a loop."
        else -> "The page couldn't be loaded (error $code)."
    }

    private fun isOnline(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    // ---- media detection --------------------------------------------------------------------

    private fun observe(tab: BrowserTab, url: String) {
        val media = MediaClassifier.classify(url) ?: return
        mainHandler.post { addMedia(tab, media) }
    }

    private fun addMedia(tab: BrowserTab, media: DetectedMedia) {
        if (tab.media.size >= MAX_MEDIA || tab.media.any { it.url == media.url }) return
        tab.media = tab.media + media
    }

    private fun scanPage(tab: BrowserTab) {
        for (delayMs in longArrayOf(0L, 2500L, 6000L)) {
            mainHandler.postDelayed({ if (tab in tabs) runScan(tab) }, delayMs)
        }
    }

    private fun runScan(tab: BrowserTab) {
        val expected = tab.url
        tab.webView.evaluateJavascript(SCAN_JS) { raw ->
            if (tab.url != expected || raw == null) return@evaluateJavascript
            try {
                val json = JSONTokener(raw).nextValue() as? String ?: return@evaluateJavascript
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optBoolean("d")) tab.drm = true
                    val u = o.optString("u")
                    if (u.isNotEmpty()) MediaClassifier.classifyElement(u, o.optString("t"))?.let { addMedia(tab, it) }
                }
            } catch (e: Exception) {
                // Pages can return odd values; detection is best-effort.
            }
        }
    }

    companion object {
        private const val MAX_MEDIA = 20
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        // Reports media element sources and whether the element uses Encrypted Media Extensions (DRM).
        private const val SCAN_JS =
            "(function(){var r=[];document.querySelectorAll('video,audio').forEach(function(m){" +
                "var t=m.tagName.toLowerCase();var s=m.currentSrc||m.src;var d=!!m.mediaKeys;" +
                "if(s)r.push({u:s,t:t,d:d});" +
                "m.querySelectorAll('source').forEach(function(x){if(x.src)r.push({u:x.src,t:t,d:d});});" +
                "if(!s&&d)r.push({u:'',t:t,d:true});});return JSON.stringify(r);})()"
    }
}
