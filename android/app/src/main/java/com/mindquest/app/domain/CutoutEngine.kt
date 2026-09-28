package com.mindquest.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import java.io.Closeable
import java.nio.ByteOrder

/**
 * Cuts an object out of a photo where you touch it, on the phone, with MediaPipe's
 * interactive segmenter — the engine behind the Scrapbook in Google's AI Edge Gallery.
 * Taps and scribbles say "this" (keep) or "not this" (remove); the answer is a confidence
 * per pixel, which becomes the cutout's transparency.
 *
 * The model ships inside the app (fetched at build time), so nothing leaves the phone.
 * Calls are serialised: the segmenter is one native object and must not be used from two
 * threads at once.
 */
class CutoutEngine private constructor(private val segmenter: InteractiveSegmenter) : Closeable {

    /** A touch on the picture: points as fractions of its width and height. */
    data class Mark(val points: List<PointF>, val keep: Boolean)

    private var image: Bitmap? = null

    @Synchronized
    fun setImage(bitmap: Bitmap) {
        segmenter.setImage(BitmapImageBuilder(bitmap).build())
        image = bitmap
    }

    /** Confidence per pixel of the current picture, or null if there's nothing to go on. */
    @Synchronized
    fun segment(marks: List<Mark>): FloatArray? {
        val img = image ?: return null
        val strokes = marks.filter { it.points.isNotEmpty() }.map { m ->
            Stroke.builder()
                .setBrushMode(if (m.keep) Stroke.BrushMode.POSITIVE else Stroke.BrushMode.NEGATIVE)
                .setPoints(m.points.map { NormalizedKeypoint.create(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) })
                .setCompleted(true)
                .build()
        }
        // Only "not this" marks select nothing; don't ask the model to guess.
        if (marks.none { it.keep && it.points.isNotEmpty() }) return null
        return try {
            val result = segmenter.segment(strokes)
            val buffer = ByteBufferExtractor.extract(result)
            buffer.order(ByteOrder.nativeOrder())
            val mw = result.width
            val mh = result.height
            val floats = FloatArray(mw * mh)
            buffer.asFloatBuffer().get(floats)
            if (mw == img.width && mh == img.height) floats
            else CutoutMath.resample(floats, mw, mh, img.width, img.height)
        } catch (e: Throwable) {
            Log.w(TAG, "Segmenting failed", e)
            null
        }
    }

    @Synchronized
    override fun close() {
        runCatching { segmenter.close() }
        image = null
    }

    companion object {
        private const val TAG = "CutoutEngine"
        const val MODEL_ASSET = "magic_touch.tflite"

        /** Largest side a picture is worked on at: plenty for a scrapbook, quick to segment. */
        const val WORK_PX = 1280

        /** Null when the model isn't in this build or the device can't run it. */
        fun create(context: Context): CutoutEngine? = try {
            context.assets.open(MODEL_ASSET).close()
            val options = InteractiveSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .build()
            CutoutEngine(InteractiveSegmenter.createFromOptions(context, options))
        } catch (e: Throwable) {
            Log.w(TAG, "Cutouts unavailable", e)
            null
        }

        /** The picture, cropped to the selection, with everything else see-through. */
        fun cut(src: Bitmap, mask: FloatArray): Bitmap? {
            val w = src.width
            val h = src.height
            val b = CutoutMath.bounds(mask, w, h) ?: return null
            val pad = 4
            val left = (b[0] - pad).coerceAtLeast(0)
            val top = (b[1] - pad).coerceAtLeast(0)
            val right = (b[2] + pad).coerceAtMost(w - 1)
            val bottom = (b[3] + pad).coerceAtMost(h - 1)
            val cw = right - left + 1
            val ch = bottom - top + 1
            val pixels = IntArray(cw * ch)
            src.getPixels(pixels, 0, cw, left, top, cw, ch)
            for (y in 0 until ch) {
                val maskRow = (top + y) * w + left
                val row = y * cw
                for (x in 0 until cw) {
                    val a = CutoutMath.alpha(mask[maskRow + x])
                    pixels[row + x] = (a shl 24) or (pixels[row + x] and 0x00FFFFFF)
                }
            }
            return Bitmap.createBitmap(pixels, cw, ch, Bitmap.Config.ARGB_8888)
        }

        /** What the editor shows over the picture: everything not selected, dimmed. */
        fun overlay(mask: FloatArray, w: Int, h: Int): Bitmap {
            val pixels = IntArray(w * h) { i -> if (mask[i] > 0.5f) 0 else 0xB0101010.toInt() }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }
    }
}
