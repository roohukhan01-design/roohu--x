package com.roohu.roohux.core.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Sends the browser's cookies (matched per host, including across redirects) with downloads so
 * logged-in/session downloads work. Read-only: nothing is stored by RoohuX and nothing is logged.
 */
object WebViewCookieJar : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> = try {
        val raw = CookieManager.getInstance().getCookie(url.toString())
        if (raw.isNullOrBlank()) emptyList() else raw.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
    } catch (e: Exception) {
        emptyList()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
}
