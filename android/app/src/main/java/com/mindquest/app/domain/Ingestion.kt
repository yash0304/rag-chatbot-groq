package com.mindquest.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device document ingestion — fully offline.
 * extract (text / OCR) → chunk → summary + tags + domain (simple heuristics, no network).
 * Images and PDFs go through ML Kit OCR (bundled Latin model). txt/md read directly.
 */
object Ingestion {
    private const val CHUNK_SIZE = 1000
    private const val OVERLAP = 150

    data class Extracted(val pages: List<Pair<Int, String>>, val ocrUsed: Boolean)

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun extract(context: Context, uri: Uri, filename: String, mime: String): Extracted =
        withContext(Dispatchers.IO) {
            val ext = filename.substringAfterLast('.', "").lowercase()
            // Decide by extension first, then fall back to MIME type (some pickers hide it).
            val kind = when {
                ext in setOf("txt", "md", "markdown", "text", "log", "csv") -> "text"
                ext in setOf("png", "jpg", "jpeg", "webp") -> "image"
                ext == "pdf" -> "pdf"
                ext == "docx" -> "docx"
                ext == "doc" -> "doc"
                mime == "application/pdf" -> "pdf"
                mime.startsWith("image/") -> "image"
                mime.contains("wordprocessingml") -> "docx"
                mime == "application/msword" -> "doc"
                mime.startsWith("text/") -> "text"
                else -> "text" // best-effort: try to read as plain text
            }
            when (kind) {
                "text" -> {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }.orEmpty()
                    Extracted(listOf(1 to text), ocrUsed = false)
                }
                "image" -> {
                    val bmp = context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    } ?: throw IllegalStateException("Could not read image")
                    Extracted(listOf(1 to ocr(bmp)), ocrUsed = true)
                }
                "pdf" -> extractPdf(context, uri)
                "docx" -> {
                    val text = context.contentResolver.openInputStream(uri)?.use { extractDocx(it) }.orEmpty()
                    Extracted(listOf(1 to text), ocrUsed = false)
                }
                "doc" -> throw IllegalStateException(
                    "Legacy .doc isn't supported — please save it as .docx or PDF and re-upload.",
                )
                else -> Extracted(listOf(1 to ""), ocrUsed = false)
            }
        }

    /** Extract text from a .docx (a ZIP whose word/document.xml holds the body). */
    private fun extractDocx(input: java.io.InputStream): String {
        java.util.zip.ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    return xml
                        .replace(Regex("</w:p>"), "\n") // paragraph breaks
                        .replace(Regex("<w:tab[^>]*/>"), "\t")
                        .replace(Regex("<[^>]+>"), "") // strip all tags (keeps <w:t> text)
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\"").replace("&apos;", "'")
                        .trim()
                }
                entry = zis.nextEntry
            }
        }
        return ""
    }

    private fun extractPdf(context: Context, uri: Uri): Extracted {
        val pages = mutableListOf<Pair<Int, String>>()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Could not open PDF")
        try {
            val renderer = PdfRenderer(pfd)
            try {
                val scale = 2
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    try {
                        val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, Matrix().apply { setScale(scale.toFloat(), scale.toFloat()) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add((i + 1) to ocr(bmp))
                        bmp.recycle()
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        } finally {
            pfd.close()
        }
        return Extracted(pages, ocrUsed = true)
    }

    private fun ocr(bitmap: Bitmap): String =
        try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
        } catch (e: Exception) {
            ""
        }

    /** Sentence-aware sliding window per page. Returns (seq, text, location). */
    fun chunk(pages: List<Pair<Int, String>>): List<Triple<Int, String, String>> {
        val out = mutableListOf<Triple<Int, String, String>>()
        var seq = 0
        for ((pageNo, pageText) in pages) {
            if (pageText.isBlank()) continue
            val sentences = pageText.split(Regex("(?<=[.!?])\\s+"))
            val buf = StringBuilder()
            for (s in sentences) {
                if (buf.isNotEmpty() && buf.length + s.length + 1 > CHUNK_SIZE) {
                    out.add(Triple(seq++, buf.toString().trim(), "p. $pageNo"))
                    val tail = buf.toString()
                    buf.clear()
                    if (OVERLAP > 0 && tail.length > OVERLAP) buf.append(tail.takeLast(OVERLAP))
                }
                if (buf.isNotEmpty()) buf.append(" ")
                buf.append(s)
            }
            if (buf.toString().isNotBlank()) out.add(Triple(seq++, buf.toString().trim(), "p. $pageNo"))
        }
        return out
    }

    fun summarize(text: String): String {
        val sentences = text.trim().split(Regex("(?<=[.!?])\\s+"))
        val s = sentences.take(2).joinToString(" ").take(400)
        return if (s.isBlank()) "A tome of gathered knowledge." else s
    }

    fun tags(text: String): List<String> {
        val words = Regex("[a-zA-Z]{5,}").findAll(text.lowercase()).map { it.value }
        return words.distinct().take(5).toList().ifEmpty { listOf("knowledge") }
    }

    fun domain(text: String): String {
        val first = Regex("[a-zA-Z]{5,}").find(text.lowercase())?.value
        return if (first != null) "${first.replaceFirstChar { it.uppercase() }} Realm" else "Uncharted Lands"
    }
}
