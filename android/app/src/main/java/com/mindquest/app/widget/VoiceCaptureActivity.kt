package com.mindquest.app.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import com.mindquest.app.data.MindQuestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Speak a note straight onto the home screen: press the mic, say "buy sugar by 21st
 * September", and it is filed with its reminder set before you have put the phone down.
 *
 * A transparent activity rather than a service because speech recognition has to be started
 * from one. It shows no UI of its own — the recogniser's own dialog is the whole interface —
 * and finishes the moment it has spoken back what it did.
 */
class VoiceCaptureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            runCatching { startActivityForResult(speechIntent(), REQUEST_SPEECH) }
                .onFailure {
                    toast("No speech recogniser on this device.")
                    finish()
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return finish()

        val spoken = data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

        if (resultCode != RESULT_OK || spoken == null) return finish()

        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                MindQuestRepository(applicationContext).captureNote(spoken)
            }.getOrNull()

            withContext(Dispatchers.Main) {
                if (result == null) {
                    toast("Couldn't save that.")
                } else {
                    // Say back what was understood and where it went, so a misheard word or a
                    // wrongly-read date is caught now rather than discovered next week.
                    toast(result.describe { dueFmt.format(Date(it)) })
                }
                TodayWidget.refresh(applicationContext)
                finish()
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val REQUEST_SPEECH = 1
        val dueFmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
    }
}

private fun speechIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I remember?")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
