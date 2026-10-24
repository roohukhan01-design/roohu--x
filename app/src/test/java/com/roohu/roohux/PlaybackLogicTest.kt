package com.roohu.roohux

import com.roohu.roohux.features.player.PlayedCodec
import com.roohu.roohux.features.player.PlayedItem
import com.roohu.roohux.features.player.ResumePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLogicTest {
    @Test fun resumeSkipsBarelyStarted() = assertEquals(0L, ResumePolicy.startPosition(3_000, 600_000))
    @Test fun resumeSkipsNearlyFinished() = assertEquals(0L, ResumePolicy.startPosition(595_000, 600_000))
    @Test fun resumeKeepsMiddle() = assertEquals(120_000L, ResumePolicy.startPosition(120_000, 600_000))
    @Test fun resumeUnknownDuration() = assertEquals(60_000L, ResumePolicy.startPosition(60_000, 0))

    @Test fun codecRoundTrip() {
        val items = listOf(
            PlayedItem("content://a/1", "Song one", false, 1000, 200000, 5),
            PlayedItem("content://a/2", "Tab\tand\nnewline", true, 0, 0, 9),
        )
        val back = PlayedCodec.decode(PlayedCodec.encode(items))
        assertEquals(2, back.size)
        assertEquals("Song one", back[0].title)
        assertEquals("Tab and newline", back[1].title)
        assertEquals(true, back[1].isVideo)
    }

    @Test fun codecIgnoresGarbage() = assertEquals(0, PlayedCodec.decode("nonsense\nmore").size)

    @Test fun upsertMovesToFrontAndCaps() {
        val a = PlayedItem("u1", "A", false, 1, 1, 1)
        val b = PlayedItem("u2", "B", false, 1, 1, 2)
        val list = PlayedCodec.upsert(PlayedCodec.upsert(emptyList(), a), b)
        assertEquals(listOf("u2", "u1"), list.map { it.uri })
        assertEquals(listOf("u1", "u2"), PlayedCodec.upsert(list, a.copy(positionMs = 9)).map { it.uri })
        assertEquals(2, PlayedCodec.upsert(list, a.copy(uri = "u3"), max = 2).size)
    }
}
