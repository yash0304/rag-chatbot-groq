package com.mindquest.app.domain

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream

/**
 * Minimal on-device recorder: captures 16 kHz mono PCM-16 and writes a WAV file — the format
 * Sarvam's speech-to-text accepts. Caller must hold RECORD_AUDIO before calling [start].
 */
class WavRecorder {
    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var recording = false
    private var rawFile: File? = null
    private var wavFile: File? = null

    @SuppressLint("MissingPermission")
    fun start(output: File) {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
            .coerceAtLeast(4096)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioEncoding, minBuf)
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        val raw = File(output.parentFile, output.nameWithoutExtension + ".pcm")
        recorder = rec
        rawFile = raw
        wavFile = output
        rec.startRecording()
        recording = true
        worker = Thread {
            FileOutputStream(raw).use { out ->
                val buf = ByteArray(minBuf)
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) out.write(buf, 0, n)
                }
            }
        }.also { it.start() }
    }

    /** Stops recording and returns the finished WAV file (or null if nothing was captured). */
    fun stop(): File? {
        if (!recording) return null
        recording = false
        worker?.join()
        recorder?.run { stop(); release() }
        recorder = null
        val raw = rawFile ?: return null
        val wav = wavFile ?: return null
        writeWav(raw, wav)
        raw.delete()
        return wav
    }

    private fun writeWav(pcm: File, wav: File) {
        val pcmLen = pcm.length().toInt()
        val totalLen = pcmLen + 36
        val byteRate = sampleRate * 2 // mono * 16-bit
        FileOutputStream(wav).use { out ->
            val header = ByteArray(44)
            fun putStr(off: Int, s: String) { for (i in s.indices) header[off + i] = s[i].code.toByte() }
            fun putInt(off: Int, v: Int) {
                header[off] = (v and 0xff).toByte()
                header[off + 1] = ((v shr 8) and 0xff).toByte()
                header[off + 2] = ((v shr 16) and 0xff).toByte()
                header[off + 3] = ((v shr 24) and 0xff).toByte()
            }
            fun putShort(off: Int, v: Int) {
                header[off] = (v and 0xff).toByte()
                header[off + 1] = ((v shr 8) and 0xff).toByte()
            }
            putStr(0, "RIFF"); putInt(4, totalLen); putStr(8, "WAVE")
            putStr(12, "fmt "); putInt(16, 16); putShort(20, 1); putShort(22, 1)
            putInt(24, sampleRate); putInt(28, byteRate); putShort(32, 2); putShort(34, 16)
            putStr(36, "data"); putInt(40, pcmLen)
            out.write(header)
            pcm.inputStream().use { it.copyTo(out) }
        }
    }
}
