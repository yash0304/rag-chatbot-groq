package com.mindquest.app.domain

import com.mindquest.app.data.SettingsStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SarvamException(message: String) : Exception(message)

@Serializable private data class SvMsg(val role: String, val content: String)
@Serializable private data class SvReq(
    val model: String,
    val messages: List<SvMsg>,
    val temperature: Double = 0.7,
)
@Serializable private data class SvChoice(val message: SvMsg)
@Serializable private data class SvResp(val choices: List<SvChoice> = emptyList())

/**
 * Single wrapper for Sarvam AI chat completions (references/sarvam.md).
 * Auth via the `api-subscription-key` header; OpenAI-compatible body. Every call logs
 * usage to the local ledger and any failure throws [SarvamException] so callers fall
 * back to the offline path — the app never hard-depends on the network.
 *
 * NOTE: verify the endpoint + current model IDs at https://docs.sarvam.ai if calls 4xx.
 * Default model `sarvam-m` is accepted; Sarvam-30B/105B are upgrades (set in Settings).
 */
class SarvamClient(private val settings: SettingsStore) {
    private val http = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    val isConfigured: Boolean get() = settings.hasSarvamKey()

    suspend fun complete(system: String, user: String): String = withContext(Dispatchers.IO) {
        val key = settings.sarvamKey() ?: throw SarvamException("No Sarvam API key set")
        val payload = json.encodeToString(
            SvReq(
                model = settings.sarvamModel(),
                messages = listOf(SvMsg("system", system), SvMsg("user", user)),
            ),
        )
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("api-subscription-key", key)
            .post(payload.toRequestBody(jsonMedia))
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw SarvamException("Sarvam error ${resp.code}: ${text.take(200)}")
                }
                val content = json.decodeFromString<SvResp>(text)
                    .choices.firstOrNull()?.message?.content
                    ?: throw SarvamException("Empty response from Sarvam")
                settings.recordUsage(system.length + user.length, content.length)
                content.trim()
            }
        } catch (e: SarvamException) {
            throw e
        } catch (e: Exception) {
            throw SarvamException(e.message ?: "Network error reaching Sarvam")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.sarvam.ai/v1/chat/completions"
    }
}
