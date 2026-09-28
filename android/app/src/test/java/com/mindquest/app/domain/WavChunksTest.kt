package com.mindquest.app.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavChunksTest {

    @Test fun seventySecondsIsThreeClips() {
        val pcm = ByteArray(16_000 * 2 * 70) { (it % 251).toByte() }
        val clips = WavChunks.split(WavChunks.wrap(pcm), 30)
        assertEquals(3, clips.size)
        assertEquals(44 + 16_000 * 2 * 30, clips[0].size)
        assertEquals(44 + 16_000 * 2 * 10, clips[2].size)
    }

    @Test fun theSoundSurvivesTheRoundTrip() {
        val pcm = ByteArray(1000) { it.toByte() }
        val clips = WavChunks.split(WavChunks.wrap(pcm), 30)
        assertArrayEquals(pcm, WavChunks.pcm(clips.single()))
        assertEquals("RIFF", String(clips.single(), 0, 4, Charsets.US_ASCII))
    }
}
