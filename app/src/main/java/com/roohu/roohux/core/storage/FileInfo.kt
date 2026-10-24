package com.roohu.roohux.core.storage

import com.roohu.roohux.features.browser.MediaClassifier
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Pure helpers that describe a download for the details card. Nothing here is guessed beyond what the label says. */
object FileInfo {
    private val pHint = Regex("(?<![0-9])(144|240|360|480|540|576|720|900|1080|1440|2160|4320)[pP](?![a-zA-Z])")
    private val dims = Regex("(?<![0-9])(\\d{3,4})[xX](\\d{3,4})(?![0-9])")
    private val kHint = Regex("(?<![a-zA-Z0-9])([248])[kK](?![a-zA-Z0-9])")

    /** "1080p", "1920x1080" or "4K" if (and only if) the filename says so. Callers must label it "filename hint". */
    fun resolutionHint(name: String): String? {
        pHint.find(name)?.let { return it.groupValues[1] + "p" }
        dims.find(name)?.let { return it.groupValues[1] + "x" + it.groupValues[2] }
        kHint.find(name)?.let { return it.groupValues[1] + "K" }
        return null
    }

    /** Format from the file's own extension; falls back to the MIME subtype only when there is no usable extension. */
    fun formatLabel(name: String, mime: String?): String {
        val ext = name.substringAfterLast('.', "")
        if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) return ext.uppercase()
        val sub = mime?.substringAfter('/', "")?.substringBefore(';')?.trim().orEmpty()
        return if (sub.isNotEmpty() && sub != "octet-stream") sub.removePrefix("x-").uppercase() else "Unknown"
    }

    /** "Video", "Audio", "Image" or "File". */
    fun category(mime: String?, name: String): String {
        val m = mime.orEmpty().lowercase()
        val ext = MediaClassifier.extensionOf(name)
        return when {
            m.startsWith("video/") || ext in MediaClassifier.VIDEO_EXT -> "Video"
            m.startsWith("audio/") || ext in MediaClassifier.AUDIO_EXT -> "Audio"
            m.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> "Image"
            else -> "File"
        }
    }

    /** The page the file came from (referer) if known, otherwise the host that served it. */
    fun sourceHost(url: String, referer: String?): String? {
        val host = referer?.toHttpUrlOrNull()?.host ?: url.toHttpUrlOrNull()?.host
        return host?.removePrefix("www.")
    }
}
