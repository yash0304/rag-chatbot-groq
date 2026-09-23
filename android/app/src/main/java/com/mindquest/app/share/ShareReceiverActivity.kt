package com.mindquest.app.share

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.PhotoStore
import com.mindquest.app.domain.Cadences
import com.mindquest.app.domain.Categories
import com.mindquest.app.widget.TodayWidget
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Share → MindQuest" from any app: a WhatsApp message, a link from Chrome, a photo from the
 * gallery, a PDF ticket from email.
 *
 * Where each lands follows what it is:
 *  - text and links become an Inbox note, through the same capture path as typing or the mic
 *    widget, so "renew passport by 3rd October" arrives with its reminder set;
 *  - photos become a note carrying the photos, so a picture of a parking spot or a receipt
 *    sits in the Inbox where it will be seen, not buried in the gallery;
 *  - documents go to the Archives, where they are read and made searchable like any upload.
 *
 * Like the mic widget it has no screen of its own. It files the thing, says where it went,
 * and gets out of the way — sharing is something done mid-task in another app.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return finish()

        val repo = MindQuestRepository(applicationContext)
        val shared = intent

        CoroutineScope(Dispatchers.IO).launch {
            val message = runCatching { handle(repo, shared) }
                .getOrElse { "Couldn't save that: ${it.message ?: "unknown error"}" }
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                TodayWidget.refresh(applicationContext)
                finish()
            }
        }
    }

    /**
     * Everything that reads a shared URI happens here, before the activity finishes: the
     * permission to read what another app shared lasts only as long as this activity does,
     * so files are copied into our own storage first and processed from the copy.
     */
    private suspend fun handle(repo: MindQuestRepository, intent: Intent): String {
        val type = intent.type.orEmpty()
        val streams = streamsOf(intent).filter(::isSafeToRead)
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.getStringExtra(Intent.EXTRA_TEXT),
        )
            // Chrome sends the page title as the subject and the URL as the text; others
            // repeat the text in both. Keep each distinct piece once.
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .let { parts -> parts.filterNot { p -> parts.any { it != p && it.contains(p) } } }
            .joinToString(" — ")

        return when {
            type.startsWith("image/") && streams.isNotEmpty() -> savePhotos(repo, streams, text)
            streams.isNotEmpty() -> saveDocuments(repo, streams)
            text.isNotBlank() -> saveText(repo, text)
            else -> "Nothing to save in what was shared."
        }
    }

    private suspend fun saveText(repo: MindQuestRepository, text: String): String {
        val r = repo.captureNote(text)
        val where = Categories.of(r.category).label
        val due = r.dueAt?.let { " · ⏰ ${stamp.format(Date(it))}" } ?: ""
        val repeat = r.repeat?.let { " · 🔁 ${Cadences.of(it).label}" } ?: ""
        return "Saved to Inbox → $where$due$repeat"
    }

    private suspend fun savePhotos(repo: MindQuestRepository, uris: List<Uri>, caption: String): String {
        val files = uris.mapNotNull { PhotoStore.importFrom(applicationContext, it) }
        if (files.isEmpty()) return "Couldn't read the shared photo."
        val label = caption.ifBlank {
            if (files.size == 1) "📷 Photo" else "📷 ${files.size} photos"
        }
        val r = repo.captureNote(label)
        files.forEach { repo.addAttachment("note", r.id, it.absolutePath) }
        return "Saved to Inbox with ${files.size} photo${if (files.size == 1) "" else "s"}"
    }

    private suspend fun saveDocuments(repo: MindQuestRepository, uris: List<Uri>): String {
        var saved = 0
        uris.forEach { uri ->
            val name = runCatching { repo.displayName(uri) }.getOrDefault("Shared document")
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val copy = copyToCache(uri, name) ?: return@forEach
            // Processing (OCR on a scanned PDF can take a while) runs on after this screen
            // has gone; it reads our own copy, so losing the sender's permission can't stop it.
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { repo.importDocument(Uri.fromFile(copy), name, mime) }
                copy.delete()
            }
            saved++
        }
        return if (saved == 0) {
            "Couldn't read the shared file."
        } else {
            "Saving $saved file${if (saved == 1) "" else "s"} to Archives — searchable in a moment"
        }
    }

    private fun copyToCache(uri: Uri, name: String): File? = runCatching {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        // A fresh name on disk, keeping the extension so the reader still knows a .pdf from
        // a .docx; the real name travels separately as the document's title.
        val ext = name.substringAfterLast('.', "").take(8)
        val target = File(dir, UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else "")
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return@runCatching null
        target.takeIf { it.length() > 0 }
    }.getOrNull()

    private fun streamsOf(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(parcelable(intent, Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE -> parcelableList(intent, Intent.EXTRA_STREAM)
        else -> emptyList()
    }

    /**
     * Only content:// URIs from other apps. Anything else is refused: a file:// path, or a
     * URI pointing back into this app's own provider, is not something another app has
     * legitimately shared — it would be a way to make MindQuest read its own private files.
     */
    private fun isSafeToRead(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT &&
            uri.authority?.startsWith(packageName) != true

    @Suppress("DEPRECATION")
    private fun parcelable(intent: Intent, key: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra<Parcelable>(key) as? Uri
        }

    @Suppress("DEPRECATION")
    private fun parcelableList(intent: Intent, key: String): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Parcelable>(key).orEmpty().mapNotNull { it as? Uri }
        }

    private companion object {
        val stamp = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
    }
}
