package com.roohu.roohux.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

object UrlUtils {
    sealed interface Result {
        data class Ok(val url: String) : Result
        data class Invalid(val reason: String) : Result
    }

    private val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val hostPort = Regex("^[^/]+:\\d+")

    /** Accepts http/https links; a bare host or path gets https:// (HTTPS-first). Anything else is rejected. */
    fun normalize(input: String): Result {
        val t = input.trim()
        if (t.isEmpty()) return Result.Invalid("Enter a link first.")
        val withScheme = when {
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            hasScheme.containsMatchIn(t) && !hostPort.containsMatchIn(t) ->
                return Result.Invalid("Only http and https links are supported.")
            else -> "https://$t"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return Result.Invalid("That doesn't look like a valid link.")
        return Result.Ok(parsed.toString())
    }

    /**
     * Browser address-bar logic: a URL-looking input is loaded (HTTPS-first); anything else becomes a web search.
     * [searchTemplate] contains %s for the URL-encoded query.
     */
    fun resolveInput(input: String, searchTemplate: String): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        if (t.none { it.isWhitespace() }) {
            val ok = normalize(t)
            if (ok is Result.Ok) {
                val host = ok.url.toHttpUrlOrNull()?.host
                if (host != null && (host.contains('.') || host == "localhost" || host.contains(':'))) return ok.url
            }
        }
        return searchTemplate.replace("%s", URLEncoder.encode(t, "UTF-8"))
    }
}
