package com.mindquest.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.util.Locale

/**
 * Dictation for anywhere you can type something into this app.
 *
 * Uses the system recogniser rather than the Sarvam speech API that the Archives use for
 * voice notes. That one is for transcribing a recording into a document and needs a key and
 * a network; this is for a sentence at a time, should cost nothing, and on most phones works
 * with the screen half-read and no signal. Different jobs, different tools.
 */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onText)
        }
    }
    val intent = remember { speechIntent() }
    return { runCatching { launcher.launch(intent) } }
}

/**
 * The mic itself. Deliberately a plain glyph button rather than an icon: it sits inside
 * text-entry rows all over the app, and anything heavier would crowd them.
 */
@Composable
fun MicButton(modifier: Modifier = Modifier, onText: (String) -> Unit) {
    val speak = rememberVoiceInput(onText)
    TextButton(onClick = speak, modifier = modifier) {
        Text("🎙", style = MaterialTheme.typography.titleMedium)
    }
}

internal fun speechIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your note")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Ask for on-device recognition where the phone supports it, so dictation keeps
        // working with no signal. Phones without it fall back to the online recogniser.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
