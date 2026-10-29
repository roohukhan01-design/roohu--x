package com.roohu.roohux

import com.roohu.roohux.core.storage.FileNames
import org.junit.Assert.*
import org.junit.Test

class FileNameExtensionTest {
    @Test fun extensionlessMediaGetsExtensionFromMime() {
        assertEquals("download.mp4", FileNames.ensureExtension("download", "video/mp4"))
        assertEquals("song.mp3", FileNames.ensureExtension("song", "audio/mpeg"))
    }

    @Test fun mimeParametersAreIgnored() =
        assertEquals("clip.webm", FileNames.ensureExtension("clip", "Video/WebM; codecs=vp9"))

    @Test fun ordinaryExtensionIsKept() {
        assertEquals("clip.mp4", FileNames.ensureExtension("clip.mp4", "video/mp4"))
        assertEquals("archive.zip", FileNames.ensureExtension("archive.zip", "video/mp4"))
        assertEquals("My.Movie.mkv", FileNames.ensureExtension("My.Movie.mkv", "video/mp4"))
    }

    @Test fun serverScriptExtensionIsReplaced() {
        assertEquals("get.mp4", FileNames.ensureExtension("get.php", "video/mp4"))
        assertEquals("stream.m4a", FileNames.ensureExtension("stream.aspx", "audio/mp4"))
    }

    @Test fun unknownOrGenericMimeLeavesNameAlone() {
        assertEquals("file", FileNames.ensureExtension("file", "application/octet-stream"))
        assertEquals("file", FileNames.ensureExtension("file", null))
    }

    @Test fun trailingDotIsNotDoubled() =
        assertEquals("name.mp4", FileNames.ensureExtension("name.", "video/mp4"))
}
