package com.mindquest.app.domain

import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Deterministic bag-of-words hashing embeddings, ported from
 * backend/app/services/ai/hashing.py. Dev/test-quality vectors: tokens hash into a
 * fixed-size bucket vector, L2-normalized, so texts sharing vocabulary land near each
 * other under cosine. Fully offline; the query is embedded the same way as chunks, so
 * results are self-consistent (real embedding model is an optional later upgrade).
 */
object Embeddings {
    const val DIM = 256
    private val tokenRe = Regex("[a-z0-9]+")

    fun embed(text: String, dim: Int = DIM): FloatArray {
        val vec = FloatArray(dim)
        for (m in tokenRe.findAll(text.lowercase())) {
            val bucket = (((hash8(m.value) % dim) + dim) % dim).toInt()
            vec[bucket] += 1f
        }
        var norm = 0.0
        for (v in vec) norm += (v * v).toDouble()
        norm = sqrt(norm)
        if (norm > 0) for (i in vec.indices) vec[i] = (vec[i] / norm).toFloat()
        return vec
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0f || nb == 0f) return 0f
        return dot / (sqrt(na) * sqrt(nb))
    }

    fun toCsv(v: FloatArray): String = v.joinToString(",")
    fun fromCsv(s: String): FloatArray =
        if (s.isEmpty()) FloatArray(0) else s.split(",").map { it.toFloat() }.toFloatArray()

    private fun hash8(token: String): Long {
        val d = MessageDigest.getInstance("MD5").digest(token.toByteArray())
        var h = 0L
        for (i in 0 until 8) h = (h shl 8) or (d[i].toLong() and 0xff)
        return h
    }
}
