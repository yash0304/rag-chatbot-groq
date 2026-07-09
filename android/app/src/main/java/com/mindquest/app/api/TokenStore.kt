package com.mindquest.app.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the refresh token across app launches in EncryptedSharedPreferences
 * (hardware-backed keystore where available). The short-lived access token is
 * kept in memory only — it is re-minted via /auth/refresh on each cold start.
 */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "mindquest_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Keystore corruption on some OEM builds: recreate rather than brick sign-in.
        context.getSharedPreferences("mindquest_tokens_fallback", Context.MODE_PRIVATE)
    }

    fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH, token).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_REFRESH).apply()
    }

    private companion object {
        const val KEY_REFRESH = "refresh_token"
    }
}
