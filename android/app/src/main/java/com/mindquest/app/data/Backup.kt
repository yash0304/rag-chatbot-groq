package com.mindquest.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One file that holds everything: the data and the photos.
 *
 * The old backup was a JSON file, which is text, so photos — the weigh-in trail, the receipts
 * — could never be in it; a lost phone meant losing every one. A backup is now a zip with the
 * data as `mindquest.json` and each photo under `photos/`. It restores anywhere: photo paths
 * are rewritten to wherever this phone keeps them, so a backup from the old phone lands whole
 * on a new one. Old .json backups still restore exactly as before.
 */
object Backup {

    private const val DATA_ENTRY = "mindquest.json"
    private const val PHOTO_PREFIX = "photos/"
    private const val FILE_PREFIX = "MindQuest-backup-"

    /** How many automatic backups to keep in the chosen folder before pruning the oldest. */
    private const val KEEP = 4

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

    fun fileName(): String = FILE_PREFIX + LocalDateTime.now().format(stamp) + ".zip"

    /** Write a complete backup to [out]. Returns how many photos went in with it. */
    suspend fun write(repo: MindQuestRepository, out: OutputStream): Int {
        val bundle = repo.exportBundle()
        var photos = 0
        ZipOutputStream(out.buffered()).use { zip ->
            // Data first, so a restore can read it before any photos.
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(repo.encodeBundle(bundle).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            bundle.attachments.map { File(it.path) }.distinctBy { it.name }.forEach { file ->
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry(PHOTO_PREFIX + file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    photos++
                }
            }
        }
        repo.settings.recordBackup()
        return photos
    }

    /**
     * Restore from a backup, zip or legacy JSON — told apart by their first bytes, since
     * file managers disagree about what to call a .zip. Replaces all current data.
     */
    suspend fun restore(context: Context, repo: MindQuestRepository, input: InputStream): Int {
        val stream = BufferedInputStream(input)
        stream.mark(4)
        val head = ByteArray(2).also { stream.read(it) }
        stream.reset()
        val isZip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        if (!isZip) {
            return repo.importJson(stream.readBytes().toString(Charsets.UTF_8))
        }

        var dataJson: String? = null
        val photoDir = PhotoStore.dir(context)
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == DATA_ENTRY -> dataJson = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith(PHOTO_PREFIX) && !entry.isDirectory -> {
                        // Only the bare file name is used, never the path in the archive, so a
                        // crafted entry like "photos/../../databases/x" can't write outside
                        // the photo folder.
                        val name = File(entry.name).name
                        if (name.isNotBlank() && !name.startsWith(".")) {
                            File(photoDir, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                entry = zip.nextEntry
            }
        }
        val json = dataJson ?: error("This zip isn't a MindQuest backup (no $DATA_ENTRY inside).")
        val bundle = repo.decodeBundle(json)
        // Point each photo record at where the file now lives on this phone.
        val relocated = bundle.attachments.mapNotNull { a ->
            val here = File(photoDir, File(a.path).name)
            if (here.isFile) a.copy(path = here.absolutePath) else null
        }
        return repo.importBundle(bundle.copy(attachments = relocated))
    }

    // ---------- automatic weekly backups ----------

    /**
     * Back up into the folder the user picked, then keep only the newest few. Returns the
     * file name written. The folder is a Storage Access Framework tree, so the backups sit in
     * ordinary phone storage — they survive the app being uninstalled or its data cleared,
     * which is precisely when they are needed.
     */
    suspend fun writeToFolder(context: Context, repo: MindQuestRepository, treeUri: Uri): String {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri),
        )
        val name = fileName()
        val doc = DocumentsContract.createDocument(resolver, parent, "application/zip", name)
            ?: error("Couldn't create a file in the backup folder.")
        resolver.openOutputStream(doc)?.use { write(repo, it) }
            ?: error("Couldn't write to the backup folder.")
        prune(context, treeUri)
        return name
    }

    /** Delete all but the newest [KEEP] automatic backups. Other files are never touched. */
    private fun prune(context: Context, treeUri: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri),
        )
        val ours = mutableListOf<Pair<String, String>>() // documentId to name
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(FILE_PREFIX) && name.endsWith(".zip")) ours += c.getString(0) to name
            }
        }
        // The timestamp in the name sorts chronologically as plain text.
        ours.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching {
                DocumentsContract.deleteDocument(
                    resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                )
            }
        }
    }

    /** Remember the chosen folder across reboots and start the weekly schedule. */
    fun enableAutoBackup(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        SettingsStore(context).saveBackupFolder(treeUri.toString())
        schedule(context, ExistingPeriodicWorkPolicy.UPDATE)
    }

    fun disableAutoBackup(context: Context) {
        val settings = SettingsStore(context)
        settings.backupFolder()?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settings.clearBackupFolder()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Called at start-up. KEEP, so opening the app doesn't restart the seven-day clock —
     * otherwise someone who opens it daily would never get a backup at all.
     */
    fun ensureScheduled(context: Context) {
        if (SettingsStore(context).backupFolder() != null) {
            schedule(context, ExistingPeriodicWorkPolicy.KEEP)
        }
    }

    private fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
            // A backup is a big write; wait for a moment the phone isn't about to die.
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    private const val WORK_NAME = "mindquest-auto-backup"
}

/** The weekly backup itself. Records failures so the Backup screen can say what went wrong. */
class AutoBackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        val folder = settings.backupFolder() ?: return Result.success()
        return try {
            val name = Backup.writeToFolder(
                applicationContext, MindQuestRepository(applicationContext), Uri.parse(folder),
            )
            settings.recordAutoBackup(name, error = null)
            Result.success()
        } catch (e: Exception) {
            Log.w("AutoBackup", "Automatic backup failed", e)
            settings.recordAutoBackup(null, error = e.message ?: e.javaClass.simpleName)
            // Retry later rather than wait a full week: a full disk or a briefly unmounted
            // card are the usual causes, and both tend to clear up.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
