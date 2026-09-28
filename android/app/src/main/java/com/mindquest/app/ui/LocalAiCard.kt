package com.mindquest.app.ui

import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindquest.app.domain.LocalAi
import kotlinx.coroutines.delay

/**
 * Install, watch and remove the on-phone model. The download is the system's, so it carries
 * on with the app closed; this card just reports on it every second while it's on screen.
 */
@Composable
fun LocalAiCard(notify: (String) -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(LocalAi.state(context)) }
    var enabled by remember { mutableStateOf(LocalAi.enabled(context)) }
    var smart by remember { mutableStateOf(LocalAi.smartCapture(context)) }
    var mobileData by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            state = LocalAi.state(context)
            delay(1000)
        }
    }
    val freeGb = remember(state) {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            StatFs(dir.path).availableBytes / 1_000_000_000.0
        }.getOrDefault(0.0)
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove the on-phone AI?") },
            text = { Text("Frees 2.6 GB. Your notes are untouched; AI features go back to Sarvam or offline.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    LocalAi.uninstall(context)
                    state = LocalAi.state(context)
                    notify("On-phone AI removed.")
                }) { Text("Remove", color = Ember) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }

    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✨ On-phone AI", fontWeight = FontWeight.Bold, color = Parchment)
        Text(
            "${LocalAi.MODEL_NAME} by Google runs on this phone — private, free, and it works with no " +
                "internet. It answers in Ask, reads photos into notes, writes down voice notes offline, " +
                "sorts what you say, and writes the weekly review.",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
        when (val s = state) {
            LocalAi.State.NotInstalled, is LocalAi.State.Failed -> {
                if (s is LocalAi.State.Failed) Text(s.reason, style = MaterialTheme.typography.bodySmall, color = Ember)
                Text(
                    "Download: 2.6 GB · free space: ${"%.1f".format(freeGb)} GB. Needs a phone with 6 GB+ RAM.",
                    style = MaterialTheme.typography.labelSmall, color = Muted,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = mobileData, onCheckedChange = { mobileData = it })
                    Text("Also use mobile data (otherwise waits for Wi-Fi)", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = freeGb > 3.0,
                    onClick = {
                        LocalAi.startDownload(context, mobileData)
                        state = LocalAi.state(context)
                        notify("Downloading — you can leave this screen; it carries on.")
                    },
                ) { Text("Download ${LocalAi.MODEL_NAME}") }
                if (freeGb <= 3.0) Text("Free up at least 3 GB first.", style = MaterialTheme.typography.labelSmall, color = Ember)
            }
            is LocalAi.State.Downloading -> {
                val pct = if (s.total > 0) s.done.toFloat() / s.total else 0f
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Rune,
                )
                Text(
                    "${s.done / 1_000_000} / ${s.total / 1_000_000} MB" +
                        if (s.waitingForWifi) " · waiting for Wi-Fi" else "",
                    style = MaterialTheme.typography.labelSmall, color = Muted,
                )
                TextButton(onClick = { LocalAi.cancelDownload(context); state = LocalAi.state(context) }) {
                    Text("Cancel download", color = Ember)
                }
            }
            LocalAi.State.Installed -> {
                Text("✓ Installed (2.6 GB)", color = Verdant, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use the on-phone AI", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it; LocalAi.setEnabled(context, it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sort what I say")
                        Text(
                            "Voice notes and ✨ go to Checklist, Thoughts, daily goals or Goals by meaning.",
                            style = MaterialTheme.typography.labelSmall, color = Muted,
                        )
                    }
                    Switch(checked = smart, onCheckedChange = { smart = it; LocalAi.setSmartCapture(context, it) })
                }
                TextButton(onClick = { confirmRemove = true }) { Text("Remove (free 2.6 GB)", color = Ember) }
            }
        }
    } }
}
