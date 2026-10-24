package com.roohu.roohux.core.storage

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import java.nio.charset.Charset

object FileNames {
    private val illegal = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val dispositionStar = Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE)
    private val dispositionPlain = Regex("filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]+))", RegexOption.IGNORE_CASE)
    private const val MAX_LEN = 120

    fun sanitize(raw: String, fallback: String = "download"): String {
        var n = raw.replace(illegal, "_").trim().trim('.').trim()
        if (n.isEmpty()) return fallback
        if (n.length > MAX_LEN) {
            val dot = n.lastIndexOf('.')
            val ext = if (dot > 0 && n.length - dot <= 10) n.substring(dot) else ""
            n = n.take(MAX_LEN - ext.length) + ext
        }
        return n
    }

    fun fromUrl(url: String): String {
        val seg = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull { it.isNotEmpty() }
        return sanitize(seg ?: "")
    }

    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val star = dispositionStar.find(header)
        val raw = if (star != null) {
            val charset = runCatching { Charset.forName(star.groupValues[1].ifBlank { "UTF-8" }) }.getOrDefault(Charsets.UTF_8)
            runCatching { URLDecoder.decode(star.groupValues[2].trim().replace("+", "%2B"), charset.name()) }.getOrNull()
        } else {
            dispositionPlain.find(header)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] }.trim() }
        } ?: return null
        val last = raw.substringAfterLast('/').substringAfterLast('\\')
        return sanitize(last, "").ifBlank { null }
    }
}
