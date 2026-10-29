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

    private val MIME_TO_EXT = mapOf(
        "video/mp4" to "mp4", "video/webm" to "webm", "video/x-matroska" to "mkv", "video/quicktime" to "mov",
        "video/3gpp" to "3gp", "video/x-m4v" to "m4v", "video/x-flv" to "flv", "video/x-msvideo" to "avi",
        "video/mpeg" to "mpg", "audio/mpeg" to "mp3", "audio/mp3" to "mp3", "audio/mp4" to "m4a", "audio/x-m4a" to "m4a",
        "audio/aac" to "aac", "audio/wav" to "wav", "audio/x-wav" to "wav", "audio/ogg" to "ogg", "audio/opus" to "opus",
        "audio/flac" to "flac", "audio/webm" to "weba", "application/pdf" to "pdf", "application/zip" to "zip",
        "image/jpeg" to "jpg", "image/png" to "png", "image/gif" to "gif", "image/webp" to "webp",
    )

    /** Extensions that say "a script produced this", not what the bytes are. Replaced when the MIME type is specific. */
    private val SERVER_SIDE_EXT = setOf("php", "asp", "aspx", "jsp", "cgi", "do", "action", "bin", "php3", "ashx", "axd", "dat", "tmp", "download")

    fun extensionForMime(mime: String?): String? = MIME_TO_EXT[mime?.substringBefore(';')?.trim()?.lowercase()]

    /**
     * Makes sure a name ends in an extension that matches the content: a missing extension (extensionless signed
     * media URLs) or a server-side script extension is replaced/completed from a specific MIME type. A real,
     * ordinary extension the server chose is never overridden. Returns [name] unchanged when the MIME type is unknown.
     */
    fun ensureExtension(name: String, mime: String?): String {
        val wanted = extensionForMime(mime) ?: return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else ""
        return when {
            ext.isEmpty() -> name.trimEnd('.') + "." + wanted
            ext in SERVER_SIDE_EXT -> name.substring(0, dot) + "." + wanted
            else -> name
        }
    }
}
