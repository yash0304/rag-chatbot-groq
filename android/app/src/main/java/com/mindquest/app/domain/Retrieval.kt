package com.mindquest.app.domain

import kotlin.math.ln

/**
 * Hybrid ranking: BM25 lexical scoring fused with the hashing-embedding cosine.
 *
 * The hashing embeddings alone are weak — they only know which buckets a token hashed
 * into, so "quarterly revenue" and "revenue quarterly" look identical to them but so do
 * plenty of unrelated pairs, and an exact name or term carries no more weight than a
 * stopword. BM25 fixes exactly that: rare terms dominate, term frequency saturates, and
 * long chunks stop winning on length alone.
 *
 * The two are combined with Reciprocal Rank Fusion rather than a weighted sum of scores.
 * RRF only reads each signal's *ranking*, so a degenerate score distribution on one side
 * (which the noisy hashing vectors often produce — every cosine landing in a narrow band)
 * can't drown out the other. Pure on-device, no model, no network.
 */
object Retrieval {

    private const val K1 = 1.2 // term-frequency saturation
    private const val B = 0.75 // length normalisation
    private const val RRF_K = 60.0 // standard RRF damping

    private val TOKEN = Regex("[a-z0-9]+")

    private val STOP = setOf(
        "the", "a", "an", "and", "or", "but", "if", "of", "to", "in", "on", "for", "with",
        "as", "by", "at", "from", "is", "are", "was", "were", "be", "been", "being", "it",
        "its", "this", "that", "these", "those", "you", "he", "she", "they", "we", "my",
        "your", "their", "our", "not", "no", "do", "does", "did", "so", "than", "then",
        "there", "here", "what", "which", "who", "how", "when", "where", "why", "can",
        "will", "would", "should", "could", "have", "has", "had", "about", "into", "over",
        "after", "before", "up", "down", "out", "also", "just", "more", "most", "some",
        "any", "all", "each", "other", "such", "only", "very", "too", "own", "same",
    )

    data class Scored<T>(val item: T, val score: Float)

    fun tokenize(text: String): List<String> =
        TOKEN.findAll(text.lowercase())
            .map { stem(it.value) }
            .filter { it.length > 1 && it !in STOP }
            .toList()

    /**
     * Conservative suffix stripping — enough that "planning", "planned" and "plans" all
     * meet at "plan", without the over-reach of a full Porter stemmer (which would also
     * collapse "universe" and "university"). Applied to queries and chunks alike, so the
     * two always agree.
     */
    private fun stem(word: String): String {
        if (word.length <= 3) return word
        val singular = when {
            word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
            word.endsWith("sses") -> word.dropLast(2)
            word.endsWith("es") && word.length > 4 -> word.dropLast(2)
            word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }
        return when {
            singular.endsWith("ing") && singular.length > 5 -> singular.dropLast(3)
            singular.endsWith("edly") && singular.length > 6 -> singular.dropLast(4)
            singular.endsWith("ed") && singular.length > 4 -> singular.dropLast(2)
            singular.endsWith("ly") && singular.length > 4 -> singular.dropLast(2)
            else -> singular
        }
    }

    /**
     * Rank [items] against [query]. The returned score is relative relevance in 0..1
     * (best hit = 1.0), suitable for display — it is a fused rank, not a cosine, so it
     * should never be read as an absolute similarity.
     */
    fun <T> hybridRank(
        query: String,
        items: List<T>,
        textOf: (T) -> String,
        vectorOf: (T) -> FloatArray,
        limit: Int,
    ): List<Scored<T>> {
        if (items.isEmpty()) return emptyList()

        val queryTerms = tokenize(query).distinct()
        val queryVector = Embeddings.embed(query)

        val vectorScores = items.map { Embeddings.cosine(queryVector, vectorOf(it)) }
        val lexicalScores = bm25(queryTerms, items.map { textOf(it) })

        // Rank positions per signal. A chunk with no lexical hit at all is left out of the
        // BM25 list entirely rather than given a bad rank, so it contributes nothing there.
        val vectorRank = rankPositions(items.indices.map { it to vectorScores[it].toDouble() })
        val lexicalRank = rankPositions(items.indices.mapNotNull { i ->
            if (lexicalScores[i] > 0.0) i to lexicalScores[i] else null
        })

        val fused = items.indices.map { i ->
            var score = 0.0
            vectorRank[i]?.let { score += 1.0 / (RRF_K + it) }
            lexicalRank[i]?.let { score += 1.0 / (RRF_K + it) }
            i to score
        }.filter { it.second > 0.0 }.sortedByDescending { it.second }

        val best = fused.firstOrNull()?.second ?: return emptyList()
        return fused.take(limit).map { (i, score) ->
            Scored(items[i], (score / best).toFloat())
        }
    }

    /** index -> 1-based rank, highest score first. */
    private fun rankPositions(scored: List<Pair<Int, Double>>): Map<Int, Int> =
        scored.sortedByDescending { it.second }
            .mapIndexed { position, (index, _) -> index to position + 1 }
            .toMap()

    /** Standard Okapi BM25 over the already-tokenised corpus. */
    private fun bm25(queryTerms: List<String>, texts: List<String>): DoubleArray {
        val scores = DoubleArray(texts.size)
        if (queryTerms.isEmpty()) return scores

        val docTerms = texts.map { tokenize(it) }
        val lengths = docTerms.map { it.size }
        val avgLength = lengths.average().takeIf { it > 0 } ?: return scores
        val termFreqs = docTerms.map { terms -> terms.groupingBy { it }.eachCount() }

        for (term in queryTerms) {
            val docFreq = termFreqs.count { it.containsKey(term) }
            if (docFreq == 0) continue
            // BM25's probabilistic IDF, in the +1 form that can't go negative for terms
            // appearing in more than half the corpus.
            val idf = ln(1.0 + (texts.size - docFreq + 0.5) / (docFreq + 0.5))
            for (i in texts.indices) {
                val freq = termFreqs[i][term] ?: continue
                val norm = K1 * (1 - B + B * lengths[i] / avgLength)
                scores[i] += idf * (freq * (K1 + 1)) / (freq + norm)
            }
        }
        return scores
    }
}
