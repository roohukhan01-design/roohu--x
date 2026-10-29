package com.roohu.roohux

import com.roohu.roohux.features.browser.MediaClassifier
import com.roohu.roohux.features.browser.MediaKind
import org.junit.Assert.*
import org.junit.Test

class MediaDetectionTest {
    @Test fun fragmentDoesNotCreateDuplicates() =
        assertEquals(MediaClassifier.dedupeKey("https://x.com/a.mp4"), MediaClassifier.dedupeKey("https://x.com/a.mp4#t=0.1"))

    @Test fun samePageIgnoresFragmentOnly() {
        assertTrue(MediaClassifier.samePage("https://a.com/b#x", "https://a.com/b"))
        assertFalse(MediaClassifier.samePage("https://a.com/b?x=1", "https://a.com/b?x=2"))
        assertFalse(MediaClassifier.samePage(null, "https://a.com/b"))
    }

    @Test fun contentTypeOverridesGuess() {
        assertNull(MediaClassifier.refineByContentType(MediaKind.DIRECT_VIDEO, "text/html; charset=utf-8"))
        assertEquals(MediaKind.STREAM_PLAYLIST, MediaClassifier.refineByContentType(MediaKind.DIRECT_VIDEO, "application/vnd.apple.mpegurl"))
        assertEquals(MediaKind.STREAM_PLAYLIST, MediaClassifier.refineByContentType(MediaKind.DIRECT_VIDEO, "application/dash+xml"))
        assertEquals(MediaKind.DIRECT_AUDIO, MediaClassifier.refineByContentType(MediaKind.DIRECT_VIDEO, "audio/mpeg"))
        assertEquals(MediaKind.DIRECT_VIDEO, MediaClassifier.refineByContentType(MediaKind.DIRECT_VIDEO, null))
        assertEquals(MediaKind.DIRECT_VIDEO, MediaClassifier.refineByContentType(MediaKind.DIRECT_VIDEO, "application/octet-stream"))
        assertEquals(MediaKind.BLOB, MediaClassifier.refineByContentType(MediaKind.BLOB, "video/mp4"))
    }

    @Test fun ordinaryLinksAndPagesAreNotMedia() {
        assertNull(MediaClassifier.classify("https://x.com/watch?v=1"))
        assertNull(MediaClassifier.classifyElement("https://x.com/data", "resource"))
        assertNull(MediaClassifier.classifyElement("https://rr1.googlevideo.com/videoplayback?id=1", "video"))
    }

    @Test fun scanReportMixesElementsAndResources() {
        val json = """{"p":"https://x.com/page","m":[
            {"u":"https://cdn.x.com/stream?id=7","t":"video","d":false},
            {"u":"https://cdn.x.com/stream?id=7#t=0","t":"video","d":false},
            {"u":"blob:https://x.com/1","t":"video","d":true}],
            "r":["https://x.com/app.js","https://cdn.x.com/a.mp3","https://x.com/index.html"]}"""
        val report = MediaClassifier.parseScanReport(json)!!
        assertEquals("https://x.com/page", report.page)
        assertTrue(report.drm)
        assertEquals(
            listOf(MediaKind.DIRECT_VIDEO, MediaKind.BLOB, MediaKind.DIRECT_AUDIO),
            report.items.map { it.kind },
        )
    }

    @Test fun scanReportWithoutMediaIsEmpty() {
        val report = MediaClassifier.parseScanReport("""{"p":"https://x.com/","m":[],"r":["https://x.com/a.css"]}""")!!
        assertTrue(report.items.isEmpty())
        assertFalse(report.drm)
    }

    @Test fun garbageReportIsIgnored() {
        assertNull(MediaClassifier.parseScanReport("not json"))
        assertNull(MediaClassifier.parseScanReport(""))
    }
}
