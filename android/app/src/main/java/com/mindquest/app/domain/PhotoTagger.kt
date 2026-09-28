package com.mindquest.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier

/**
 * Tags a photo with what it shows, on the phone: MediaPipe's image classifier (EfficientNet,
 * a thousand everyday things — "golden retriever", "pizza", "sports car") plus a few tags the
 * words in the picture give away — a receipt, a bill, a phone number.
 */
object PhotoTagger {

    private const val MODEL = "efficientnet_lite0.tflite"
    private var classifier: ImageClassifier? = null
    private var unavailable = false

    @Synchronized
    fun labels(context: Context, bitmap: Bitmap): List<String> {
        if (unavailable) return emptyList()
        val c = classifier ?: try {
            ImageClassifier.createFromOptions(
                context,
                ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setMaxResults(3)
                    .setScoreThreshold(0.2f)
                    .build(),
            ).also { classifier = it }
        } catch (e: Throwable) {
            Log.w("PhotoTagger", "Classifier unavailable", e)
            unavailable = true
            return emptyList()
        }
        return try {
            c.classify(BitmapImageBuilder(bitmap).build())
                .classificationResult().classifications().firstOrNull()?.categories().orEmpty()
                // ImageNet names come as synonym lists: "notebook, notebook computer".
                .map { it.categoryName().substringBefore(',').trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Throwable) {
            emptyList()
        }
    }
}

/** Tags the words in a photo give away. Plain Kotlin, so it is tested without a phone. */
object PhotoTags {
    private val RECEIPT = Regex("""\b(total|subtotal|grand total|gst|cgst|sgst|invoice|receipt|bill no|amount|paid|cash|upi)\b""", RegexOption.IGNORE_CASE)
    private val BILL = Regex("""\b(due date|pay by|last date|amount due|outstanding)\b""", RegexOption.IGNORE_CASE)
    private val MONEY = Regex("""(₹|\brs\.?\s?\d|\binr\b)""", RegexOption.IGNORE_CASE)

    /** Tags that the words in a photo give away. */
    fun tagsFromText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val tags = mutableListOf<String>()
        if (RECEIPT.containsMatchIn(text) && (MONEY.containsMatchIn(text) || Regex("""\d+\.\d{2}""").containsMatchIn(text))) tags += "receipt"
        if (BILL.containsMatchIn(text)) tags += "bill"
        if (PhoneFind.find(text).isNotEmpty()) tags += "phone number"
        if (text.length > 200) tags += "document"
        return tags
    }
}
