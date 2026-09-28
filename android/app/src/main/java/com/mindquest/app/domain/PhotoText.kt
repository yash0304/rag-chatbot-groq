package com.mindquest.app.domain

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * The words in a picture, read on the phone by ML Kit — the same reader the Archives use for
 * scanned documents. Blocking; call it off the main thread.
 */
object PhotoText {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun read(bitmap: Bitmap): String = try {
        Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
    } catch (e: Exception) {
        ""
    }

    /** A first draft of a note from a photo's words, for when there's no model to describe it. */
    fun summaryLine(text: String): String =
        text.lines().map { it.trim() }.filter { it.length >= 3 }.take(3).joinToString(" · ").take(160)
}
