package com.mindquest.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.PhotoStore
import com.mindquest.app.domain.CutoutEngine
import com.mindquest.app.domain.LocalAi
import com.mindquest.app.domain.PhotoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SNAP_PROMPT =
    "Look at this photo and write one line for my notes app. Say what it is and keep anything worth " +
        "remembering: names, phone numbers, addresses, amounts, dates. For a receipt or bill: the shop, " +
        "the total and the date. For something I need to act on — a bill due, an appointment card, a " +
        "flyer with a date — start with the action and include the date. Reply with only the line."

/**
 * Photo → note. Snap a receipt, a visiting card, a whiteboard or a shop front, and it comes
 * back as a line you can correct before saving, with the photo kept on the note. Gemma writes
 * the line when it's installed; otherwise the words in the picture are read out as a start.
 */
@Composable
fun SnapDialog(
    repo: MindQuestRepository,
    startAsThought: Boolean,
    notify: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var text by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var thought by remember { mutableStateOf(startAsThought) }
    val usingModel = remember { LocalAi.ready(context) }

    fun read(bmp: Bitmap) {
        photo = bmp
        reading = true
        scope.launch {
            text = try {
                if (usingModel) LocalAi.generate(context, SNAP_PROMPT, image = bmp, temperature = 0.2)
                    .lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                else withContext(Dispatchers.Default) { PhotoText.summaryLine(PhotoText.read(bmp)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Default) { PhotoText.summaryLine(PhotoText.read(bmp)) }
            }
            reading = false
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { decodePicked(context, uri) }?.let(::read) }
    }
    val takePhoto = rememberTakePhoto { file ->
        scope.launch {
            withContext(Dispatchers.IO) {
                PhotoStore.thumbnail(file.absolutePath, CutoutEngine.WORK_PX)?.let(::argb).also { file.delete() }
            }?.let(::read)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📷 Photo → note") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val bmp = photo
                if (bmp == null) {
                    Text(
                        if (usingModel) "Snap a receipt, card, bill or anything — the on-phone AI writes the note."
                        else "Snap it and its words become the note. Install the on-phone AI in Settings for a written summary.",
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = takePhoto) { Text("📷 Camera") }
                        OutlinedButton(onClick = { pick.launch("image/*") }) { Text("🖼 Gallery") }
                    }
                } else {
                    Image(
                        bmp.asImageBitmap(), null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    )
                    if (reading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(if (usingModel) "Reading it…" else "Reading the words…", color = Muted)
                        }
                    }
                    OutlinedTextField(
                        text, { text = it }, label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !thought, onClick = { thought = false }, label = { Text("✅ To-do") })
                        FilterChip(selected = thought, onClick = { thought = true }, label = { Text("💭 Thought") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = photo != null && !reading && text.isNotBlank(),
                onClick = {
                    val bmp = photo ?: return@TextButton
                    val line = text.trim()
                    val asThought = thought
                    reading = true
                    // Finish before closing: the dialog's scope ends with it.
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { saveBitmap(context, bmp, png = false) }
                        repo.addSnapNote(line, asThought, path)
                        notify(if (asThought) "💭 Saved to Thoughts, with the photo." else "✅ Added to the Checklist, with the photo.")
                        onDismiss()
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
