package com.mindquest.app.domain

import java.text.Normalizer

/**
 * BERT WordPiece tokenizer for the uncased MiniLM model.
 *
 * This has to reproduce HuggingFace's `BertTokenizer` exactly — a tokenizer that disagrees
 * with the one the model was trained under produces embeddings that are quietly wrong
 * rather than obviously broken, which is the worst failure mode available here. So the
 * steps below mirror the reference implementation in order: clean, strip accents,
 * lowercase, split on punctuation, then greedy longest-match WordPiece.
 */
class WordPiece(private val vocab: Map<String, Int>) {

    val clsId = vocab[CLS] ?: 101
    val sepId = vocab[SEP] ?: 102
    val padId = vocab[PAD] ?: 0
    private val unkId = vocab[UNK] ?: 100

    /** Token ids for [text], already wrapped in [CLS]/[SEP] and truncated to [maxLen]. */
    fun encode(text: String, maxLen: Int): IntArray {
        val pieces = ArrayList<Int>(maxLen)
        pieces.add(clsId)
        outer@ for (word in basicTokenize(text)) {
            for (id in wordPiece(word)) {
                if (pieces.size >= maxLen - 1) break@outer // leave room for [SEP]
                pieces.add(id)
            }
        }
        pieces.add(sepId)
        return pieces.toIntArray()
    }

    /** Whitespace + punctuation splitting, accent stripping, lowercasing. */
    private fun basicTokenize(text: String): List<String> {
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .lowercase()

        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) { out.add(current.toString()); current.clear() }
        }
        for (ch in stripped) {
            when {
                ch.isWhitespace() || isControl(ch) -> flush()
                isPunctuation(ch) -> { flush(); out.add(ch.toString()) }
                else -> current.append(ch)
            }
        }
        flush()
        return out
    }

    /** Greedy longest-match-first, with "##" marking continuations of a word. */
    private fun wordPiece(word: String): List<Int> {
        if (word.length > MAX_WORD_CHARS) return listOf(unkId)
        val ids = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var matched: Int? = null
            while (start < end) {
                val piece = if (start == 0) word.substring(start, end) else "##" + word.substring(start, end)
                val id = vocab[piece]
                if (id != null) { matched = id; break }
                end--
            }
            // One unmatchable span makes the whole word [UNK], per the reference tokenizer —
            // not just the offending piece.
            if (matched == null) return listOf(unkId)
            ids.add(matched)
            start = end
        }
        return ids
    }

    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        return when (Character.getType(ch).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE -> true
            else -> false
        }
    }

    private fun isPunctuation(ch: Char): Boolean {
        val code = ch.code
        // The reference treats all ASCII non-alphanumerics as punctuation, not just the
        // Unicode punctuation categories.
        if (code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126) return true
        return when (Character.getType(ch).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION, Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            -> true
            else -> false
        }
    }

    companion object {
        private const val CLS = "[CLS]"
        private const val SEP = "[SEP]"
        private const val PAD = "[PAD]"
        private const val UNK = "[UNK]"
        private const val MAX_WORD_CHARS = 100

        /** vocab.txt is one token per line; the line number is the id. */
        fun fromVocabText(lines: Sequence<String>): WordPiece {
            val vocab = HashMap<String, Int>(32_000)
            lines.forEachIndexed { index, line -> vocab[line.trim()] = index }
            return WordPiece(vocab)
        }
    }
}
