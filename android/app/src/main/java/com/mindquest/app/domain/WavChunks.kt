package com.mindquest.app.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cuts a 16 kHz mono 16-bit WAV into clips the on-phone model can take — about 30 seconds
 * each — so a five-minute voice note is transcribed in pieces and stitched back together.
 */
object WavChunks {

    const val SAMPLE_RATE = 16_000
    private const val BYTES_PER_SAMPLE = 2
    private const val HEADER = 44

    /** The PCM samples of a WAV written by [WavRecorder] (a plain 44-byte header). */
    fun pcm(wav: ByteArray): ByteArray =
        if (wav.size > HEADER && String(wav, 0, 4, Charsets.US_ASCII) == "RIFF") wav.copyOfRange(HEADER, wav.size) else wav

    fun split(wav: ByteArray, seconds: Int): List<ByteArray> {
        val data = pcm(wav)
        val step = SAMPLE_RATE * BYTES_PER_SAMPLE * seconds
        if (data.isEmpty()) return emptyList()
        return (data.indices step step).map { from -> wrap(data.copyOfRange(from, minOf(from + step, data.size))) }
    }

    /** A complete WAV file around raw 16 kHz mono PCM. */
    fun wrap(pcm: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(HEADER + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcm.size)
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1)
        b.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * BYTES_PER_SAMPLE).putShort(BYTES_PER_SAMPLE.toShort()).putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size)
        b.put(pcm)
        return b.array()
    }
}
