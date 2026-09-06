package com.mindquest.app.domain

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Optional fingerprint / face unlock in front of the PIN.
 *
 * The PIN stays the source of truth and the only recoverable secret: biometrics are a
 * convenience layer, never a replacement. If the sensor is missing, disabled, or the user's
 * enrolled prints change, the PIN is always still there — so nobody can be locked out of
 * their own second brain by a hardware quirk while travelling.
 */
object BiometricLock {

    /** Why biometric unlock isn't offered right now, or null when it is available. */
    fun unavailableReason(context: Context): String? {
        val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (BiometricManager.from(context).canAuthenticate(allowed)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> "This device has no usable biometric sensor."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No fingerprint or face is enrolled. Add one in your device settings first."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "A security update is required before biometrics can be used."
            else -> "Biometric unlock isn't available on this device."
        }
    }

    fun isAvailable(context: Context): Boolean = unavailableReason(context) == null

    /**
     * Show the system biometric prompt. [onFail] carries a message worth showing only when
     * something actually went wrong — a plain cancel (the user tapping "Use PIN" or the back
     * button) reports null so the caller can quietly fall back to the PIN field.
     */
    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFail: (String?) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    val quiet = code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        code == BiometricPrompt.ERROR_USER_CANCELED ||
                        code == BiometricPrompt.ERROR_CANCELED
                    onFail(if (quiet) null else message.toString())
                }
                // onAuthenticationFailed (a non-matching finger) is deliberately not handled:
                // the system prompt already says "not recognised" and stays open to retry.
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MindQuest")
            .setSubtitle("Your archives are sealed")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()

        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            onFail("Couldn't start biometric unlock. Use your PIN.")
        }
    }
}
