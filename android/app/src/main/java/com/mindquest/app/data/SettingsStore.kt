package com.mindquest.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted local settings: the user's Sarvam API key + chosen model, and a small
 * usage ledger (per references/sarvam.md — know where the credits go). The key never
 * leaves the device except in the auth header of a Sarvam request the user initiated.
 * Everything here is optional; with no key the app stays fully offline.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "mindquest_settings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("mindquest_settings_fallback", Context.MODE_PRIVATE)
    }

    fun sarvamKey(): String? = prefs.getString(KEY_SARVAM, null)?.ifBlank { null }
    fun hasSarvamKey(): Boolean = sarvamKey() != null
    fun saveSarvamKey(key: String) { prefs.edit().putString(KEY_SARVAM, key.trim()).apply() }
    fun clearSarvamKey() { prefs.edit().remove(KEY_SARVAM).apply() }

    fun sarvamModel(): String = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    fun saveSarvamModel(model: String) { prefs.edit().putString(KEY_MODEL, model.trim()).apply() }

    // ---- usage ledger ----
    fun recordUsage(charsIn: Int, charsOut: Int) {
        prefs.edit()
            .putInt(KEY_CALLS, prefs.getInt(KEY_CALLS, 0) + 1)
            .putLong(KEY_CHARS_IN, prefs.getLong(KEY_CHARS_IN, 0) + charsIn)
            .putLong(KEY_CHARS_OUT, prefs.getLong(KEY_CHARS_OUT, 0) + charsOut)
            .apply()
    }

    fun usage(): Triple<Int, Long, Long> = Triple(
        prefs.getInt(KEY_CALLS, 0), prefs.getLong(KEY_CHARS_IN, 0), prefs.getLong(KEY_CHARS_OUT, 0),
    )

    private companion object {
        const val KEY_SARVAM = "sarvam_key"
        const val KEY_MODEL = "sarvam_model"
        const val KEY_CALLS = "sarvam_calls"
        const val KEY_CHARS_IN = "sarvam_chars_in"
        const val KEY_CHARS_OUT = "sarvam_chars_out"
        const val DEFAULT_MODEL = "sarvam-m"
    }
}
