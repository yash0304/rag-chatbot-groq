package com.mindquest.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import com.mindquest.app.data.Backup
import java.io.File
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.domain.BiometricLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Backup / restore ----------

private val backupStamp = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

@Composable
fun DataScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmImport by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    // Re-read after anything on this screen changes them.
    var refresh by remember { mutableIntStateOf(0) }
    val last = remember(refresh) { repo.lastBackup() }
    val folder = remember(refresh) { repo.settings.backupFolder() }
    val autoAt = remember(refresh) { repo.settings.lastAutoBackupAt() }
    val autoError = remember(refresh) { repo.settings.lastAutoBackupError() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Saving backup…"
            try {
                val photos = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { Backup.write(repo, it) }
                } ?: error("Couldn't open the file for writing.")
                notify("Backup saved — everything, plus $photos photo${if (photos == 1) "" else "s"}.")
            } catch (e: Exception) {
                notify("Backup failed: ${e.message}")
            } finally {
                busy = null; refresh++
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "Restoring…"
            try {
                val n = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { Backup.restore(context, repo, it) }
                } ?: error("Couldn't open the file.")
                notify("Restored — $n items, with folders, photos and reminders.")
            } catch (e: Exception) {
                notify("Restore failed: ${e.message}")
            } finally {
                busy = null; refresh++
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                Backup.enableAutoBackup(context, uri)
                notify("Weekly backups on. Making the first one now…")
                scope.launch {
                    try {
                        val name = withContext(Dispatchers.IO) { Backup.writeToFolder(context, repo, uri) }
                        repo.settings.recordAutoBackup(name, error = null)
                        notify("First backup saved: $name")
                    } catch (e: Exception) {
                        repo.settings.recordAutoBackup(null, error = e.message)
                        notify("Couldn't write to that folder: ${e.message}")
                    } finally {
                        refresh++
                    }
                }
            } catch (e: Exception) {
                notify("Couldn't use that folder: ${e.message}")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Backup & Restore", style = MaterialTheme.typography.headlineMedium, color = Parchment)
        Text(
            "Your data lives only on this phone. A backup is one file holding all of it — notes, " +
                "quests, missions, folders, archives and every photo.",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
        Text(
            "Last backup: " + if (last == 0L) "never" else backupStamp.format(Date(last)),
            style = MaterialTheme.typography.labelSmall,
            color = if (last == 0L) Ember else Verdant,
        )
        busy?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = Rune)
            }
        }

        // Automatic — the backup that happens whether or not anyone remembers.
        Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weekly automatic backup", fontWeight = FontWeight.Bold, color = Parchment)
            if (folder == null) {
                Text(
                    "Pick a folder on this phone (Documents is a good choice). A backup goes " +
                        "there every week and the newest four are kept. It stays even if the " +
                        "app is uninstalled.",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
                Button(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose folder & turn on")
                }
            } else {
                Text(
                    if (autoAt == 0L) "On — the first weekly backup is pending."
                    else "On · last run ${backupStamp.format(Date(autoAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (autoError == null) Verdant else Ember,
                )
                autoError?.let {
                    Text("Last attempt failed: $it", style = MaterialTheme.typography.labelSmall, color = Ember)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = "Backing up to your folder…"
                            try {
                                val name = withContext(Dispatchers.IO) {
                                    Backup.writeToFolder(context, repo, Uri.parse(folder))
                                }
                                repo.settings.recordAutoBackup(name, error = null)
                                notify("Saved: $name")
                            } catch (e: Exception) {
                                repo.settings.recordAutoBackup(null, error = e.message)
                                notify("Backup failed: ${e.message}")
                            } finally {
                                busy = null; refresh++
                            }
                        }
                    }) { Text("Back up now") }
                    TextButton(onClick = {
                        Backup.disableAutoBackup(context)
                        refresh++
                        notify("Weekly backups off.")
                    }) { Text("Turn off", color = Muted) }
                }
            }
        } }

        Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keep a copy off the phone", fontWeight = FontWeight.Bold, color = Parchment)
            Text(
                "A backup on the phone won't help if the phone is lost. Send one to Google Drive, " +
                    "email or WhatsApp now and then.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Button(onClick = {
                scope.launch {
                    busy = "Preparing backup…"
                    try {
                        val file = withContext(Dispatchers.IO) {
                            val dir = File(context.cacheDir, "backups").apply {
                                mkdirs()
                                listFiles()?.forEach { it.delete() } // only ever the latest
                            }
                            File(dir, Backup.fileName()).also { f -> f.outputStream().use { Backup.write(repo, it) } }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Send backup to…"))
                    } catch (e: Exception) {
                        notify("Couldn't prepare the backup: ${e.message}")
                    } finally {
                        busy = null; refresh++
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Share backup (Drive, email…)") }
            OutlinedButton(onClick = { exporter.launch(Backup.fileName()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save backup to a file…")
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val md = repo.exportMarkdown()
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, md) }
                    context.startActivity(Intent.createChooser(intent, "Share summary"))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Share summary (Markdown)") }
        } }

        Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Restore", fontWeight = FontWeight.Bold, color = Parchment)
            Text("Importing replaces ALL current data on this device.", style = MaterialTheme.typography.bodySmall, color = Ember)
            Text(
                "Takes the new .zip backups and older .json ones alike.",
                style = MaterialTheme.typography.labelSmall, color = Muted,
            )
            OutlinedButton(onClick = { confirmImport = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Restore from backup…")
            }
        } }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Replace all data?") },
            text = { Text("This wipes your current hero and restores everything from the chosen backup file. This cannot be undone.") },
            // Any type: file managers variously call a .zip application/zip, x-zip-compressed or
            // octet-stream, and the restore tells zip from json by content anyway.
            confirmButton = { TextButton(onClick = { confirmImport = false; importer.launch(arrayOf("*/*")) }) { Text("Choose file & replace") } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }
}

// ---------- PIN lock ----------

@Composable
fun LockScreen(repo: MindQuestRepository, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricOffered = repo.settings.biometricEnabled() &&
        activity != null && BiometricLock.isAvailable(context)

    fun askBiometric() {
        val a = activity ?: return
        biometricError = null
        BiometricLock.prompt(a, onSuccess = onUnlock, onFail = { biometricError = it })
    }

    // Offer the sensor straight away — the PIN field stays underneath for the cancel path.
    LaunchedEffect(biometricOffered) { if (biometricOffered) askBiometric() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒 MindQuest", style = MaterialTheme.typography.headlineLarge, color = Rune)
        Text("Enter your PIN", color = Muted)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            pin, { pin = it; error = false },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error,
            singleLine = true,
        )
        if (error) Text("Incorrect PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (repo.settings.verifyPin(pin)) onUnlock() else { error = true; pin = "" } }) {
            Text("Unlock")
        }
        if (biometricOffered) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { askBiometric() }) { Text("Use fingerprint") }
            biometricError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
