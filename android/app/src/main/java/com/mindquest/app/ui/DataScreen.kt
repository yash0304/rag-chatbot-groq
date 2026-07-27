package com.mindquest.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.MindQuestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Backup / restore ----------

@Composable
fun DataScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmImport by remember { mutableStateOf(false) }
    val last = remember { repo.lastBackup() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val jsonStr = repo.exportJson()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(jsonStr.toByteArray()) } }
            notify("Backup saved.")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val txt = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            if (txt != null) {
                try { val n = repo.importJson(txt); notify("Restored — $n items.") }
                catch (e: Exception) { notify("Import failed: ${e.message}") }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Backup & Restore", style = MaterialTheme.typography.headlineMedium, color = Parchment)
        Text("Your data lives only on this device. Export regularly so it outlives the app.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(
            "Last backup: " + if (last == 0L) "never" else SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(last)),
            style = MaterialTheme.typography.labelSmall,
            color = if (last == 0L) Color(0xFFF87171) else Color(0xFF6EE7B7),
        )

        Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Export", fontWeight = FontWeight.Bold, color = Parchment)
            Button(onClick = { exporter.launch("mindquest-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                Text("Save full backup (.json)")
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
            Text("Importing replaces ALL current data on this device.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF87171))
            OutlinedButton(onClick = { confirmImport = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Import backup (.json)")
            }
        } }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Replace all data?") },
            text = { Text("This wipes your current hero and restores everything from the chosen backup file. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmImport = false; importer.launch(arrayOf("application/json")) }) { Text("Choose file & replace") } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }
}

// ---------- PIN lock ----------

@Composable
fun LockScreen(repo: MindQuestRepository, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒 MindQuest", style = MaterialTheme.typography.headlineLarge, color = Rune)
        Text("Enter your PIN", color = Color.Gray)
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
    }
}
