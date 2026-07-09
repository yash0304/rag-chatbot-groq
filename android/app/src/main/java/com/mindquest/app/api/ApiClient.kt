package com.mindquest.app.api

import com.mindquest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class TokenPair(val access_token: String, val refresh_token: String)

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

/**
 * Minimal client for the MindQuest REST API (see docs/API_SPECIFICATION.md).
 * The Android app is a companion: quest hub, daily missions, character sheet.
 */
class ApiClient(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    var accessToken: String? = null
        private set

    private suspend fun call(path: String, method: String = "GET", body: String? = null): String =
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

    suspend fun login(email: String, password: String) {
        val payload = """{"email":${Json.encodeToString(kotlinx.serialization.serializer(), email)},""" +
            """"password":${Json.encodeToString(kotlinx.serialization.serializer(), password)}}"""
        val tokens = json.decodeFromString<TokenPair>(call("/auth/login", "POST", payload))
        accessToken = tokens.access_token
    }

    suspend fun profile(): Profile = json.decodeFromString(call("/gamification/profile"))

    suspend fun activeQuests(): List<Quest> =
        json.decodeFromString(call("/quests?status_filter=active"))

    suspend fun completeQuest(id: String): String = call("/quests/$id/complete", "POST")

    suspend fun habits(): List<Habit> = json.decodeFromString(call("/habits"))

    suspend fun checkin(id: String): String = call("/habits/$id/checkin", "POST")
}

class ApiException(val code: Int, message: String) : Exception(message)
