package com.roohu.roohux.features.browser

import com.roohu.roohux.core.storage.FileNames
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class MediaKind { DIRECT_VIDEO, DIRECT_AUDIO, STREAM_PLAYLIST, BLOB }

data class DetectedMedia(val url: String, val kind: MediaKind, val name: String)

/** Classifies direct media URLs and sources explicitly attached to HTML media elements. */
object MediaClassifier {
    val VIDEO_EXT = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp")
    val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac")
    private val PLAYLIST_EXT = setOf("m3u8", "mpd")

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    private fun isBlockedStreamingHost(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host.endsWith(".youtu.be") ||
            host == "googlevideo.com" || host.endsWith(".googlevideo.com")

    fun classify(url: String): DetectedMedia? {
        val http = url.toHttpUrlOrNull() ?: return null
        if (isBlockedStreamingHost(http.host.lowercase())) return null
        val last = http.pathSegments.lastOrNull().orEmpty()
        val extensionKind = when (extensionOf(last)) {
            in VIDEO_EXT -> MediaKind.DIRECT_VIDEO
            in AUDIO_EXT -> MediaKind.DIRECT_AUDIO
            in PLAYLIST_EXT -> MediaKind.STREAM_PLAYLIST
            else -> null
        }
        val kind = extensionKind ?: classifyByDeclaredFormat(
            http.queryParameterNames.associateWith { key -> http.queryParameterValues(key).lastOrNull().orEmpty() },
        ) ?: return null
        val fallback = when (kind) {
            MediaKind.STREAM_PLAYLIST -> "stream-playlist"
            MediaKind.DIRECT_AUDIO -> "audio"
            else -> "video"
        }
        return DetectedMedia(url, kind, FileNames.sanitize(last.ifBlank { fallback }, fallback))
    }

    private fun classifyByDeclaredFormat(parameters: Map<String, String>): MediaKind? {
        val hints = parameters.entries.joinToString(" ") { (key, value) -> "$key=$value" }.lowercase()
        return when {
            "application/vnd.apple.mpegurl" in hints || "application/x-mpegurl" in hints ||
                "format=m3u8" in hints || "type=m3u8" in hints ||
                "application/dash+xml" in hints || "format=mpd" in hints || "type=mpd" in hints -> MediaKind.STREAM_PLAYLIST
            "video/mp4" in hints || "format=mp4" in hints -> MediaKind.DIRECT_VIDEO
            "audio/mpeg" in hints || "audio/mp4" in hints || "format=mp3" in hints || "format=m4a" in hints -> MediaKind.DIRECT_AUDIO
            else -> null
        }
    }

    /** Classifies URLs actually assigned to a video/audio element, including extensionless signed URLs. */
    fun classifyElement(url: String, tag: String): DetectedMedia? {
        if (url.startsWith("blob:", ignoreCase = true)) return DetectedMedia(url, MediaKind.BLOB, "Blob stream")
        classify(url)?.let { return it }
        val http = url.toHttpUrlOrNull() ?: return null
        if (isBlockedStreamingHost(http.host.lowercase())) return null
        val declared = classifyByDeclaredFormat(
            http.queryParameterNames.associateWith { key -> http.queryParameterValues(key).lastOrNull().orEmpty() },
        )
        if (declared != null) {
            val fallback = if (declared == MediaKind.DIRECT_AUDIO) "audio" else "video"
            return DetectedMedia(url, declared, FileNames.sanitize(http.pathSegments.lastOrNull().orEmpty(), fallback))
        }
        // The element itself is the media declaration. Many legitimate hosts use signed URLs with no extension.
        val kind = when (tag.lowercase()) {
            "audio" -> MediaKind.DIRECT_AUDIO
            "video" -> MediaKind.DIRECT_VIDEO
            else -> return null
        }
        val fallback = if (kind == MediaKind.DIRECT_AUDIO) "audio" else "video"
        val pathName = http.pathSegments.lastOrNull().orEmpty()
        val safeName = FileNames.sanitize(pathName.substringBefore('?').ifBlank { fallback }, fallback)
        return DetectedMedia(url, kind, safeName)
    }
}
