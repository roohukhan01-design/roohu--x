package com.roohu.roohux

import com.roohu.roohux.core.network.UrlUtils
import com.roohu.roohux.features.browser.MediaClassifier
import com.roohu.roohux.features.browser.MediaKind
import org.junit.Assert.*
import org.junit.Test

class BrowserLogicTest {
    private val tpl = "https://duckduckgo.com/?q=%s"

    @Test fun urlLikeInputIsLoaded() {
        assertEquals("https://example.com/", UrlUtils.resolveInput("example.com", tpl))
        assertEquals("http://example.com/a", UrlUtils.resolveInput("http://example.com/a", tpl))
        assertEquals("https://localhost:8080/", UrlUtils.resolveInput("localhost:8080", tpl))
    }

    @Test fun otherInputBecomesSearch() {
        assertEquals("https://duckduckgo.com/?q=hello+world", UrlUtils.resolveInput("hello world", tpl))
        assertEquals("https://duckduckgo.com/?q=cats", UrlUtils.resolveInput("cats", tpl))
        assertNull(UrlUtils.resolveInput("  ", tpl))
    }

    @Test fun dangerousSchemesAreSearchedNotLoaded() {
        val r = UrlUtils.resolveInput("javascript:alert(1)", tpl)!!
        assertTrue(r.startsWith("https://duckduckgo.com/"))
    }

    @Test fun classifiesByExtension() {
        assertEquals(MediaKind.DIRECT_VIDEO, MediaClassifier.classify("https://x.com/a/b.MP4?token=1")?.kind)
        assertEquals(MediaKind.DIRECT_AUDIO, MediaClassifier.classify("https://x.com/song.mp3")?.kind)
        assertEquals(MediaKind.STREAM_PLAYLIST, MediaClassifier.classify("https://x.com/live/index.m3u8")?.kind)
        assertNull(MediaClassifier.classify("https://x.com/index.html"))
        assertNull(MediaClassifier.classify("not a url"))
    }

    @Test fun elementClassification() {
        assertEquals(MediaKind.BLOB, MediaClassifier.classifyElement("blob:https://x.com/123", "video")?.kind)
        // A URL attached to a real audio/video element is a media candidate even without a file extension.
        assertEquals(MediaKind.DIRECT_AUDIO, MediaClassifier.classifyElement("https://x.com/stream?id=7", "audio")?.kind)
        assertEquals(MediaKind.DIRECT_VIDEO, MediaClassifier.classifyElement("https://x.com/stream?id=7", "video")?.kind)
        assertEquals(MediaKind.DIRECT_VIDEO, MediaClassifier.classifyElement("https://x.com/stream?type=video%2Fmp4", "video")?.kind)
    }
}
