package com.mindquest.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

/**
 * Optional encryption of the database file, off unless the user turns it on.
 *
 * Off by default on purpose. Android already encrypts app storage while the phone is locked;
 * this adds protection for the file itself, but it also ties the data to a key kept in the
 * phone's secure hardware — and if that key is ever lost (some phones reset it after a
 * factory-level update or a failed restore), the database can't be opened again and only a
 * backup brings it back. For a user who said their data here is unmissable, that trade has
 * to be their choice, made with a backup already in place, not something an update does.
 *
 * Converting either way follows one rule: the original file is never touched until the
 * converted copy has been opened with its key and shown to hold the same rows. Any failure
 * leaves the original exactly as it was.
 */
object DbEncryption {

    private const val TAG = "DbEncryption"
    private const val PREFS = "mindquest_db_key"
    private const val KEY_PASSPHRASE = "passphrase"
    private const val KEY_WANT = "want_encrypted"
    private const val KEY_LAST_ERROR = "last_error"

    /** Tables whose row counts must survive a conversion unchanged. */
    private val CHECKED_TABLES = listOf("notes", "quests", "habits", "habit_checkins", "documents", "folders", "xp_events")

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    @Volatile private var libraryLoaded = false

    fun loadLibrary() {
        if (!libraryLoaded) {
            System.loadLibrary("sqlcipher")
            libraryLoaded = true
        }
    }

    /**
     * Its own preferences file with no plain fallback. SettingsStore quietly falls back to
     * unencrypted preferences if the secure store fails to open; doing that here would mean
     * looking for the key in the wrong place and concluding it had been lost.
     */
    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Whether the user has asked for the database to be encrypted. */
    fun wanted(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_WANT, false) }.getOrDefault(false)

    fun setWanted(context: Context, want: Boolean) {
        prefs(context).edit().putBoolean(KEY_WANT, want).commit()
    }

    /** Why the last conversion didn't happen, for the Settings screen to show. */
    fun lastError(context: Context): String? =
        runCatching { prefs(context).getString(KEY_LAST_ERROR, null) }.getOrNull()

    private fun recordError(context: Context, message: String?) {
        runCatching { prefs(context).edit().putString(KEY_LAST_ERROR, message).commit() }
    }

    /** The existing passphrase, or null if there isn't one (or it can't be read). */
    private fun existingPassphrase(context: Context): String? =
        runCatching { prefs(context).getString(KEY_PASSPHRASE, null) }.getOrNull()

    /** The passphrase, created on first use: 32 random bytes as hex. */
    private fun passphrase(context: Context): String {
        existingPassphrase(context)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        // commit(), not apply(): the key must be on disk before anything is encrypted with it.
        check(prefs(context).edit().putString(KEY_PASSPHRASE, hex).commit()) { "Couldn't store the key" }
        return hex
    }

    fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return true
        val head = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { it.read(head) }
        return head.contentEquals(SQLITE_HEADER)
    }

    /** Thrown when the file is encrypted and its key can't be found. */
    class LockedOut : Exception(
        "Your data is encrypted, but its key on this phone couldn't be read.",
    )

    /**
     * Called once, before the database is opened. Brings the file into the state the user
     * asked for, and returns the passphrase Room should open it with — or null for a plain
     * file. Never throws for a failed conversion: the file is left as it was and opened as
     * it is. Only throws [LockedOut], when an encrypted file's key is simply gone.
     */
    fun prepare(context: Context, dbFile: File): ByteArray? {
        val want = wanted(context)
        val exists = dbFile.isFile
        val plain = isPlaintext(dbFile)

        if (want) {
            val key = runCatching { passphrase(context) }.getOrElse {
                recordError(context, "Couldn't create a key: ${it.message}")
                setWantedQuietly(context, false)
                return if (exists && !plain) throw LockedOut() else null
            }
            if (exists && plain) {
                if (!convert(context, dbFile, fromKey = "", toKey = key)) {
                    // Stay plain rather than half-way: the user can try again from Settings.
                    setWantedQuietly(context, false)
                    return null
                }
            }
            loadLibrary()
            return key.toByteArray(Charsets.UTF_8)
        }

        // Not wanted. A plain file needs nothing; an encrypted one is decrypted back.
        if (!exists || plain) return null
        val key = existingPassphrase(context) ?: throw LockedOut()
        if (convert(context, dbFile, fromKey = key, toKey = "")) return null
        // Couldn't decrypt: keep it encrypted and working rather than lose access.
        setWantedQuietly(context, true)
        loadLibrary()
        return key.toByteArray(Charsets.UTF_8)
    }

    private fun setWantedQuietly(context: Context, want: Boolean) {
        runCatching { setWanted(context, want) }
    }

    /**
     * Copy [dbFile] into a new file under a different key, check the copy, then swap it in.
     * An empty key means unencrypted. Returns false — with the original untouched — on any
     * failure along the way.
     */
    private fun convert(context: Context, dbFile: File, fromKey: String, toKey: String): Boolean {
        val tmp = File(dbFile.parentFile, dbFile.name + ".converting")
        tmp.delete()
        return try {
            loadLibrary()
            val expected: Map<String, Long>
            val version: Int
            val src = open(dbFile, fromKey)
            try {
                version = src.rawQuery("PRAGMA user_version", emptyArray<String>())
                    .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                expected = counts(src)
                src.execSQL("ATTACH DATABASE ? AS converted KEY ?", arrayOf<Any>(tmp.path, toKey))
                src.rawExecSQL("SELECT sqlcipher_export('converted')")
                // sqlcipher_export copies tables, not the schema version Room migrates by.
                src.execSQL("PRAGMA converted.user_version = $version")
                src.execSQL("DETACH DATABASE converted")
            } finally {
                src.close()
            }

            // Prove the copy opens with its key and holds what the original held.
            val dst = open(tmp, toKey)
            try {
                val gotVersion = dst.rawQuery("PRAGMA user_version", emptyArray<String>())
                    .use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
                check(gotVersion == version) { "Schema version changed during conversion" }
                val got = counts(dst)
                check(got == expected) { "Row counts differ after conversion" }
            } finally {
                dst.close()
            }

            // Only now does the original go. Its WAL and shared-memory files belong to it and
            // must not be left beside the new file, which would read them as its own.
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            check(tmp.renameTo(dbFile)) { "Couldn't move the converted file into place" }
            recordError(context, null)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Conversion failed; original left as it was", e)
            tmp.delete()
            recordError(context, e.message ?: e.javaClass.simpleName)
            false
        }
    }

    private fun open(file: File, key: String): CipherDatabase =
        CipherDatabase.openDatabase(
            file.path, key.toByteArray(Charsets.UTF_8), null,
            CipherDatabase.OPEN_READWRITE, null, null,
        )

    private fun counts(db: CipherDatabase): Map<String, Long> {
        val present = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table'", emptyArray<String>(),
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        return CHECKED_TABLES.filter { it in present }.associateWith { table ->
            db.rawQuery("SELECT COUNT(*) FROM `$table`", emptyArray<String>())
                .use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        }
    }

    /**
     * Last-resort recovery when [LockedOut]: set the unreadable file aside — renamed, never
     * deleted, in case the key turns up — and let the app start empty so a backup can be
     * restored into it.
     */
    fun setAside(context: Context, dbFile: File): File {
        val aside = File(dbFile.parentFile, dbFile.name + ".locked-" + System.currentTimeMillis())
        dbFile.renameTo(aside)
        listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        setWantedQuietly(context, false)
        return aside
    }
}
