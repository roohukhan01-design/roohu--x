package com.roohu.roohux

import com.roohu.roohux.core.network.ContentRange
import com.roohu.roohux.core.network.UrlUtils
import com.roohu.roohux.core.storage.FileInfo
import com.roohu.roohux.core.storage.FileNames
import org.junit.Assert.*
import org.junit.Test

class UtilsTest {
    @Test fun bareHostGetsHttps() =
        assertEquals(UrlUtils.Result.Ok("https://example.com/a.zip"), UrlUtils.normalize("example.com/a.zip"))

    @Test fun httpIsKept() =
        assertEquals(UrlUtils.Result.Ok("http://example.com/"), UrlUtils.normalize("http://example.com"))

    @Test fun hostWithPortIsNotAScheme() =
        assertEquals(UrlUtils.Result.Ok("https://localhost:8080/a.bin"), UrlUtils.normalize("localhost:8080/a.bin"))

    @Test fun otherSchemesRejected() {
        assertTrue(UrlUtils.normalize("ftp://x.com/a") is UrlUtils.Result.Invalid)
        assertTrue(UrlUtils.normalize("javascript:alert(1)") is UrlUtils.Result.Invalid)
        assertTrue(UrlUtils.normalize("   ") is UrlUtils.Result.Invalid)
    }

    @Test fun sanitizeReplacesIllegalChars() = assertEquals("a_b_c_.txt", FileNames.sanitize("a/b:c*.txt"))
    @Test fun sanitizeFallback() = assertEquals("download", FileNames.sanitize("..."))
    @Test fun sanitizeKeepsExtensionWhenTruncating() {
        val n = FileNames.sanitize("x".repeat(200) + ".mp4")
        assertEquals(120, n.length); assertTrue(n.endsWith(".mp4"))
    }

    @Test fun dispositionQuoted() =
        assertEquals("report 2024.pdf", FileNames.fromContentDisposition("attachment; filename=\"report 2024.pdf\""))
    @Test fun dispositionStar() =
        assertEquals("na\u00efve file.txt", FileNames.fromContentDisposition("attachment; filename*=UTF-8''na%C3%AFve%20file.txt"))
    @Test fun dispositionTraversalStripped() =
        assertEquals("passwd", FileNames.fromContentDisposition("attachment; filename=\"../../etc/passwd\""))
    @Test fun dispositionNull() = assertNull(FileNames.fromContentDisposition(null))

    @Test fun nameFromUrl() {
        assertEquals("My Video.mp4", FileNames.fromUrl("https://example.com/files/My%20Video.mp4?x=1"))
        assertEquals("download", FileNames.fromUrl("https://example.com/"))
    }

    @Test fun contentRange() {
        assertEquals(ContentRange(100, 999, 1000), ContentRange.parse("bytes 100-999/1000"))
        assertEquals(ContentRange(0, 9, null), ContentRange.parse("bytes 0-9/*"))
        assertNull(ContentRange.parse("garbage")); assertNull(ContentRange.parse(null))
    }
}

class FileInfoTest {
    @Test fun resolutionFromName() {
        assertEquals("1080p", FileInfo.resolutionHint("my_video_1080p.mp4"))
        assertEquals("720p", FileInfo.resolutionHint("Show.S01E01.720P.WEB.mkv"))
        assertEquals("1920x1080", FileInfo.resolutionHint("clip_1920x1080.mp4"))
        assertEquals("4K", FileInfo.resolutionHint("trailer 4K.mp4"))
        assertNull(FileInfo.resolutionHint("holiday.mp4"))
        assertNull(FileInfo.resolutionHint("track1080pro.mp3"))
    }

    @Test fun formatPrefersExtension() {
        assertEquals("MP4", FileInfo.formatLabel("a.mp4", "video/x-matroska"))
        assertEquals("MATROSKA", FileInfo.formatLabel("noext", "video/x-matroska"))
        assertEquals("Unknown", FileInfo.formatLabel("noext", "application/octet-stream"))
    }

    @Test fun categoryAndSource() {
        assertEquals("Video", FileInfo.category("video/mp4", "x"))
        assertEquals("Audio", FileInfo.category(null, "song.flac"))
        assertEquals("File", FileInfo.category("application/zip", "a.zip"))
        assertEquals("example.com", FileInfo.sourceHost("https://cdn.other.net/a.mp4", "https://www.example.com/page"))
        assertEquals("cdn.other.net", FileInfo.sourceHost("https://cdn.other.net/a.mp4", null))
    }
}
