package com.mindquest.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Where photos attached to a quest or mission actually live.
 *
 * Picked images are copied in rather than linked: a note that says "this is what the scales
 * said in March" is worthless if clearing your gallery empties it, and a copy also sidesteps
 * having to hold a permission on someone else's URI for the next eighteen months. Everything
 * stays inside the app's private files directory, so nothing here is visible to other apps
 * and it all leaves with an uninstall.
 */
object PhotoStore {

    private const val DIR = "attachments"

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** A fresh empty file for the camera to write into. */
    fun newFile(context: Context): File = File(dir(context), "${UUID.randomUUID()}.jpg")

    /** Copy a picked image into our own storage. Returns null if it couldn't be read. */
    fun importFrom(context: Context, uri: Uri): File? = try {
        val target = newFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (target.length() == 0L) {
            target.delete()
            null
        } else {
            target
        }
    } catch (e: Exception) {
        Log.w("PhotoStore", "Could not import photo", e)
        null
    }

    /**
     * A small bitmap for the thumbnail strip. Full-size camera photos are tens of megapixels
     * and decoding one per row would run the app out of memory long before it ran out of
     * photos, so the bounds are read first and the decoder told to subsample.
     */
    fun thumbnail(path: String, maxPx: Int = 512): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > maxPx) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
        decoded?.let { rotateToUpright(path, it) }
    } catch (e: Exception) {
        Log.w("PhotoStore", "Could not decode photo", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w("PhotoStore", "Photo too large to decode", e)
        null
    }

    /** Phones record orientation in EXIF rather than rotating the pixels; honour it. */
    private fun rotateToUpright(path: String, bitmap: Bitmap): Bitmap = try {
        val degrees = when (
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) {
            bitmap
        } else {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(degrees) }, true,
            )
        }
    } catch (e: Exception) {
        bitmap
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }
}
