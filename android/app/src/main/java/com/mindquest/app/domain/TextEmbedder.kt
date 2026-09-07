package com.mindquest.app.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * How chunk and query text becomes a vector.
 *
 * Two implementations, chosen at runtime: the real MiniLM transformer when its assets are
 * present and ONNX Runtime loads cleanly, and the old hashing embedding otherwise. The
 * fallback is not decoration — the model is fetched at build time, so any build that
 * couldn't reach the network still produces a working app with slightly duller search.
 */
interface TextEmbedder {
    val dim: Int
    val label: String
    fun embed(text: String): FloatArray
}

/** Deterministic bag-of-words hashing vectors. Always available, no assets, no native code. */
object HashingEmbedder : TextEmbedder {
    override val dim = Embeddings.DIM
    override val label = "Hashing (built-in)"
    override fun embed(text: String): FloatArray = Embeddings.embed(text)
}

/**
 * all-MiniLM-L6-v2, quantised, running on device through ONNX Runtime. 384-dim sentence
 * embeddings with mean pooling over the attention mask, then L2 normalised so cosine and
 * dot product agree. Everything is local; nothing is sent anywhere.
 */
class MiniLmEmbedder private constructor(
    private val session: OrtSession,
    private val tokenizer: WordPiece,
    private val env: OrtEnvironment,
    private val inputNames: Set<String>,
) : TextEmbedder {

    override val dim = 384
    override val label = "MiniLM-L6-v2 (on-device)"

    override fun embed(text: String): FloatArray {
        val ids = tokenizer.encode(text, MAX_TOKENS)
        val length = ids.size
        val inputIds = LongArray(length) { ids[it].toLong() }
        val mask = LongArray(length) { 1L }
        val shape = longArrayOf(1, length.toLong())

        val tensors = HashMap<String, OnnxTensor>()
        return try {
            tensors["input_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
            tensors["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape)
            // Some exports drop token_type_ids; only feed what this graph actually declares.
            if ("token_type_ids" in inputNames) {
                tensors["token_type_ids"] =
                    OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(length)), shape)
            }
            session.run(tensors).use { result ->
                @Suppress("UNCHECKED_CAST")
                val hidden = result.get(0).value as Array<Array<FloatArray>> // [1, seq, 384]
                meanPool(hidden[0])
            }
        } catch (e: Exception) {
            Log.w(TAG, "MiniLM inference failed; falling back to hashing for this text", e)
            HashingEmbedder.embed(text)
        } finally {
            tensors.values.forEach { runCatching { it.close() } }
        }
    }

    /** Average the token vectors, then L2 normalise. Every token here is real (no padding). */
    private fun meanPool(tokens: Array<FloatArray>): FloatArray {
        val out = FloatArray(dim)
        if (tokens.isEmpty()) return out
        for (token in tokens) for (i in out.indices) out[i] += token[i]
        for (i in out.indices) out[i] /= tokens.size

        var norm = 0.0
        for (v in out) norm += (v * v).toDouble()
        norm = sqrt(norm)
        if (norm > 0) for (i in out.indices) out[i] = (out[i] / norm).toFloat()
        return out
    }

    companion object {
        private const val TAG = "MiniLmEmbedder"
        private const val MAX_TOKENS = 256
        const val MODEL_ASSET = "minilm.onnx"
        const val VOCAB_ASSET = "minilm_vocab.txt"

        /** Returns null when the assets are missing or the runtime won't load. */
        fun tryCreate(context: Context): MiniLmEmbedder? = try {
            val assets = context.assets
            val available = assets.list("")?.toSet().orEmpty()
            if (MODEL_ASSET !in available || VOCAB_ASSET !in available) {
                Log.i(TAG, "Model assets absent — staying on hashing embeddings")
                null
            } else {
                val tokenizer = assets.open(VOCAB_ASSET).bufferedReader().use { reader ->
                    WordPiece.fromVocabText(reader.readLines().asSequence())
                }
                val bytes = assets.open(MODEL_ASSET).use { it.readBytes() }
                val env = OrtEnvironment.getEnvironment()
                val session = env.createSession(bytes, OrtSession.SessionOptions())
                MiniLmEmbedder(session, tokenizer, env, session.inputNames.toSet())
            }
        } catch (e: Throwable) {
            // Throwable, not Exception: a missing native library surfaces as UnsatisfiedLinkError.
            Log.w(TAG, "Could not start MiniLM; falling back to hashing embeddings", e)
            null
        }
    }
}

/** Process-wide embedder. Resolved once — loading the model costs ~100ms and some memory. */
object Embedders {
    @Volatile private var cached: TextEmbedder? = null

    fun active(context: Context): TextEmbedder =
        cached ?: synchronized(this) {
            cached ?: (MiniLmEmbedder.tryCreate(context.applicationContext) ?: HashingEmbedder)
                .also { cached = it }
        }
}
