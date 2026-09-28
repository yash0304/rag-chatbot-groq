package com.mindquest.app.domain

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Gemma 4 on the phone — the model Google's AI Edge Gallery runs — for the assistant, reading
 * photos, transcribing voice, and sorting what you say into the right place. Nothing is sent
 * anywhere; it works in airplane mode.
 *
 * The model is 2.6 GB, so it is never bundled: the user asks for it in Settings and the
 * system download manager fetches it (resumable, with its own notification, Wi-Fi only
 * unless allowed otherwise) into the app's own storage. Loading it takes a few seconds and
 * a couple of GB of memory, so the engine is started on first use and let go after a
 * couple of minutes of quiet.
 */
object LocalAi {

    private const val TAG = "LocalAi"

    const val MODEL_NAME = "Gemma 4 E2B"
    private const val FILE = "gemma-4-E2B-it.litertlm"
    private const val PART = "$FILE.part"
    const val SIZE_BYTES = 2_588_147_712L
    private const val URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
            "6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm"

    /** Gemma takes at most about 30 seconds of audio per clip. */
    const val AUDIO_CLIP_SECONDS = 30

    /** Let go of the model after this long unused — it holds a lot of memory. */
    private const val IDLE_MS = 120_000L

    sealed class State {
        object NotInstalled : State()
        data class Downloading(val done: Long, val total: Long, val waitingForWifi: Boolean) : State()
        data class Failed(val reason: String) : State()
        object Installed : State()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("mindquest_local_ai", Context.MODE_PRIVATE)

    private fun dir(context: Context): File =
        (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }

    private fun modelFile(context: Context) = File(dir(context), FILE)

    fun isInstalled(context: Context): Boolean = modelFile(context).let { it.isFile && it.length() == SIZE_BYTES }

    /** On unless turned off in Settings; only matters once the model is installed. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
        if (!on) release()
    }

    /** Ready to answer: installed and switched on. */
    fun ready(context: Context): Boolean = enabled(context) && isInstalled(context)

    /** "Sort what I say": captures go through Gemma before the usual rules. */
    fun smartCapture(context: Context): Boolean = prefs(context).getBoolean("smart_capture", true)
    fun setSmartCapture(context: Context, on: Boolean) { prefs(context).edit().putBoolean("smart_capture", on).apply() }

    // ---------- download ----------

    fun startDownload(context: Context, allowMobileData: Boolean) {
        val dm = context.getSystemService(DownloadManager::class.java)
        prefs(context).getLong("download_id", -1L).takeIf { it >= 0 }?.let { dm.remove(it) }
        File(dir(context), PART).delete()
        val request = DownloadManager.Request(Uri.parse(URL))
            .setTitle("MindQuest on-phone AI")
            .setDescription("$MODEL_NAME · 2.6 GB")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(allowMobileData)
            .setAllowedOverRoaming(false)
            // The no-permission way to write into the app's own storage.
            .setDestinationInExternalFilesDir(context, "models", PART)
        val id = dm.enqueue(request)
        prefs(context).edit().putLong("download_id", id).apply()
    }

    fun cancelDownload(context: Context) {
        val id = prefs(context).getLong("download_id", -1L)
        if (id >= 0) context.getSystemService(DownloadManager::class.java).remove(id)
        prefs(context).edit().remove("download_id").apply()
        File(dir(context), PART).delete()
    }

    /** Frees the 2.6 GB. The app keeps working exactly as it did before. */
    fun uninstall(context: Context) {
        release()
        cancelDownload(context)
        modelFile(context).delete()
    }

    /** Where things stand; also finishes a completed download by moving it into place. */
    fun state(context: Context): State {
        if (isInstalled(context)) return State.Installed
        val id = prefs(context).getLong("download_id", -1L)
        if (id < 0) return State.NotInstalled
        val dm = context.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c == null || !c.moveToFirst()) {
                prefs(context).edit().remove("download_id").apply()
                return State.NotInstalled
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                .takeIf { it > 0 } ?: SIZE_BYTES
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val part = File(dir(context), PART)
                    prefs(context).edit().remove("download_id").apply()
                    if (part.length() == SIZE_BYTES && part.renameTo(modelFile(context))) State.Installed
                    else {
                        part.delete()
                        State.Failed("The download finished but the file is incomplete. Try again.")
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs(context).edit().remove("download_id").apply()
                    State.Failed("Download failed (code $reason). Check the connection and try again.")
                }
                DownloadManager.STATUS_PAUSED -> State.Downloading(
                    done, total,
                    waitingForWifi = reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI ||
                        reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK,
                )
                else -> State.Downloading(done, total, waitingForWifi = false)
            }
        }
    }

    // ---------- the engine ----------

    private val lock = Mutex()
    private var engine: Engine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null

    /**
     * GPU first, the way the Gallery runs Gemma 4; CPU if this phone's GPU can't take it.
     * Vision on the same accelerator, audio always on CPU.
     */
    private fun open(context: Context): Engine {
        engine?.let { return it }
        val path = modelFile(context).absolutePath
        val attempts = listOf(
            EngineConfig(modelPath = path, backend = Backend.GPU(), visionBackend = Backend.GPU(),
                audioBackend = Backend.CPU(), maxNumTokens = 4096, cacheDir = context.cacheDir.path),
            EngineConfig(modelPath = path, backend = Backend.CPU(), visionBackend = Backend.CPU(),
                audioBackend = Backend.CPU(), maxNumTokens = 4096, cacheDir = context.cacheDir.path),
        )
        var last: Throwable? = null
        for (config in attempts) {
            try {
                val e = Engine(config)
                e.initialize()
                engine = e
                Log.i(TAG, "Gemma ready on ${config.backend.name}")
                return e
            } catch (t: Throwable) {
                Log.w(TAG, "Couldn't start on ${config.backend.name}", t)
                last = t
            }
        }
        throw IllegalStateException("The on-phone AI couldn't start: ${last?.message}", last)
    }

    /** Let the model go now, e.g. when switched off. */
    fun release() {
        scope.launch {
            lock.withLock {
                runCatching { engine?.close() }
                engine = null
            }
        }
    }

    /**
     * One question, one answer. A fresh conversation each time, so nothing from one feature
     * leaks into another. [temperature] low for reading and sorting, higher for writing.
     */
    suspend fun generate(
        context: Context,
        prompt: String,
        system: String? = null,
        image: Bitmap? = null,
        audioWav: ByteArray? = null,
        temperature: Double = 0.7,
    ): String = withContext(Dispatchers.Default) {
        check(ready(context)) { "The on-phone AI isn't installed." }
        lock.withLock {
            idleJob?.cancel()
            val e = open(context)
            try {
                e.createConversation(
                    ConversationConfig(
                        systemInstruction = system?.let { Contents.of(it) },
                        samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = temperature),
                    ),
                ).use { conversation ->
                    val parts = mutableListOf<Content>()
                    // Pictures and sound before the words, the order the model expects.
                    image?.let { parts.add(Content.ImageBytes(it.toPng())) }
                    audioWav?.let { parts.add(Content.AudioBytes(it)) }
                    parts.add(Content.Text(prompt))
                    val reply = conversation.sendMessage(Contents.of(parts), mapOf("enable_thinking" to false))
                    clean(reply.toString())
                }
            } finally {
                idleJob = scope.launch {
                    delay(IDLE_MS)
                    lock.withLock { runCatching { engine?.close() }; engine = null }
                }
            }
        }
    }

    /** Drop any visible reasoning the model wrote before its answer. */
    internal fun clean(text: String): String =
        text.replace(Regex("(?s)<think>.*?</think>"), "").trim()

    private fun Bitmap.toPng(): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
