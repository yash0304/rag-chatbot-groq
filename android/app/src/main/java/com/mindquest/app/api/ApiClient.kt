package com.mindquest.app.api

import com.mindquest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// ---------- wire models ----------

@Serializable
data class TokenPair(val access_token: String, val refresh_token: String)

@Serializable
private data class LoginRequest(val email: String, val password: String)

@Serializable
private data class RefreshRequest(val refresh_token: String)

@Serializable
data class Profile(
    val xp: Long,
    val level: Int,
    val progress_pct: Double,
    val skill_points: Int,
    val current_streak_max: Int,
)

@Serializable
data class Quest(
    val id: String,
    val title: String,
    val difficulty: String,
    val xp_reward: Int,
    val status: String,
)

@Serializable
data class Habit(
    val id: String,
    val title: String,
    val streak: Int,
    val checked_in_today: Boolean,
)

@Serializable
data class ChatSession(val id: String, val title: String)

@Serializable
private data class ChatSessionRequest(val title: String? = null)

@Serializable
data class Citation(
    val index: Int,
    val title: String,
    val snippet: String,
    val location: String? = null,
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val citations: List<Citation> = emptyList(),
)

@Serializable
private data class ChatMessageRequest(val content: String)

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Client for the MindQuest REST API (contract: docs/API_SPECIFICATION.md).
 *
 * Session handling mirrors the web client: the refresh token persists in
 * [TokenStore] and is single-use — every /auth/refresh rotates it — while the
 * access token lives in memory. Authenticated calls transparently refresh and
 * retry once on 401, so callers never deal with token lifecycle.
 */
class ApiClient(
    private val tokenStore: TokenStore,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()
    private val refreshMutex = Mutex()

    @Volatile
    private var accessToken: String? = null

    // ---------- session lifecycle ----------

    /** Silent sign-in on app start. True if a stored refresh token yielded a session. */
    suspend fun restoreSession(): Boolean = refreshTokens()

    suspend fun login(email: String, password: String) {
        val body = rawCall("/auth/login", "POST", json.encodeToString(LoginRequest(email, password)))
        applyTokens(json.decodeFromString<TokenPair>(body))
    }

    suspend fun logout() {
        tokenStore.refreshToken()?.let { stored ->
            runCatching { rawCall("/auth/logout", "POST", json.encodeToString(RefreshRequest(stored))) }
        }
        accessToken = null
        tokenStore.clear()
    }

    private fun applyTokens(pair: TokenPair) {
        accessToken = pair.access_token
        tokenStore.saveRefreshToken(pair.refresh_token)
    }

    private suspend fun refreshTokens(): Boolean = refreshMutex.withLock {
        val stored = tokenStore.refreshToken() ?: return false
        try {
            val body = rawCall("/auth/refresh", "POST", json.encodeToString(RefreshRequest(stored)))
            applyTokens(json.decodeFromString<TokenPair>(body))
            true
        } catch (e: Exception) {
            // Rotation means a rejected token is dead; clear it so the UI shows login.
            accessToken = null
            tokenStore.clear()
            false
        }
    }

    // ---------- transport ----------

    private suspend fun rawCall(path: String, method: String = "GET", body: String? = null): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url("$baseUrl/api/v1$path")
            accessToken?.let { builder.header("Authorization", "Bearer $it") }
            when (method) {
                "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMedia))
                "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonMedia))
                "DELETE" -> builder.delete()
            }
            http.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, text)
                text
            }
        }

    /** Authenticated call: on 401, refresh the session once and retry. */
    private suspend fun call(path: String, method: String = "GET", body: String? = null): String =
        try {
            rawCall(path, method, body)
        } catch (e: ApiException) {
            if (e.code == 401 && refreshTokens()) rawCall(path, method, body) else throw e
        }

    // ---------- gamification & quests ----------

    suspend fun profile(): Profile = json.decodeFromString(call("/gamification/profile"))

    suspend fun activeQuests(): List<Quest> =
        json.decodeFromString(call("/quests?status_filter=active"))

    suspend fun completeQuest(id: String): String = call("/quests/$id/complete", "POST")

    suspend fun habits(): List<Habit> = json.decodeFromString(call("/habits"))

    suspend fun checkin(id: String): String = call("/habits/$id/checkin", "POST")

    // ---------- narrator chat ----------

    suspend fun chatSessions(): List<ChatSession> = json.decodeFromString(call("/chat/sessions"))

    suspend fun createChatSession(title: String? = null): ChatSession =
        json.decodeFromString(
            call("/chat/sessions", "POST", json.encodeToString(ChatSessionRequest(title)))
        )

    suspend fun chatMessages(sessionId: String): List<ChatMessage> =
        json.decodeFromString(call("/chat/sessions/$sessionId/messages"))

    suspend fun sendChatMessage(sessionId: String, content: String): ChatMessage =
        json.decodeFromString(
            call("/chat/sessions/$sessionId/messages", "POST", json.encodeToString(ChatMessageRequest(content)))
        )
}
