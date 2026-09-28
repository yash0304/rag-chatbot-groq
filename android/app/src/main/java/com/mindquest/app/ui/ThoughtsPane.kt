package com.mindquest.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.AttachmentEntity
import com.mindquest.app.data.FolderEntity
import com.mindquest.app.data.KIND_THOUGHT
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.NoteEntity
import com.mindquest.app.data.isThought
import com.mindquest.app.domain.FolderMatch
import com.mindquest.app.domain.PhoneFind
import kotlinx.coroutines.launch

/**
 * Thoughts: things to remember rather than do — the HDFC ATM in sector 104, Starbucks and
 * Theos on the way to the office, the puncture man in sector 98 and his number. Nothing
 * here is ticked or reminded; it is kept, in folders, and a number in it can be tapped
 * to call.
 */
@Composable
fun ThoughtsPane(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val allNotes by repo.observeNotes().collectAsState(emptyList())
    val allFolders by repo.observeFolders().collectAsState(emptyList())
    val photos by repo.observeAttachments("note").collectAsState(emptyList())
    val photosByNote = photos.groupBy { it.ownerId }
    val folders = allFolders.filter { it.isThought }
    val folderIds = folders.map { it.id }.toSet()
    val thoughts = allNotes.filter { it.isThought }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val openFolder = folders.firstOrNull { it.id == openId }
    var newFolderOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderEntity?>(null) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var moving by remember { mutableStateOf<NoteEntity?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var snapping by remember { mutableStateOf(false) }
    if (snapping) SnapDialog(repo, startAsThought = true, notify = notify, onDismiss = { snapping = false })
    var skipFolder by remember { mutableStateOf(false) }
    LaunchedEffect(input.isBlank()) { if (input.isBlank()) skipFolder = false }
    val folderGuess = remember(input, openId, folders) {
        if (openId != null) null else FolderMatch.best(input, folders.map { it.name })?.let { folders[it] }
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onDismiss = { newFolderOpen = false },
            onCreate = { name ->
                newFolderOpen = false
                scope.launch {
                    openId = repo.createFolder(name, kind = KIND_THOUGHT)
                    notify("🗂 $name — add what you want to remember in it.")
                }
            },
        )
    }
    renaming?.let { target ->
        NewFolderDialog(
            initial = target.name, title = "Rename folder", confirm = "Rename",
            onDismiss = { renaming = null },
            onCreate = { name -> renaming = null; scope.launch { repo.renameFolder(target.id, name) } },
        )
    }
    editing?.let { note ->
        EditThoughtDialog(
            initial = note.text,
            onDismiss = { editing = null },
            onSave = { text ->
                editing = null
                scope.launch { repo.editNote(note.id, text, note.remindAt, note.repeat) }
            },
        )
    }
    moving?.let { note ->
        MoveToFolderDialog(
            folders = folders,
            current = note.folderId,
            onDismiss = { moving = null },
            onPick = { folderId ->
                moving = null
                scope.launch { repo.setNoteFolder(note.id, folderId) }
            },
        )
    }

    val shown = if (openFolder != null) thoughts.filter { it.folderId == openFolder.id }
    else thoughts.filter { it.folderId == null || it.folderId !in folderIds }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp)) {
        if (openFolder == null) {
            Text(
                "Things to remember, not to do — places, people, numbers. Tap a number to call it.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openId = null }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("← All")
                }
                Text(
                    "${openFolder.icon} ${openFolder.name}",
                    style = MaterialTheme.typography.titleMedium, color = Parchment, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                var menu by remember(openFolder.id) { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋯", color = Muted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = openFolder })
                        DropdownMenuItem(
                            text = { Text("Move to ✅ Checklist") },
                            onClick = {
                                menu = false
                                openId = null
                                scope.launch {
                                    repo.setFolderKind(openFolder.id, null)
                                    notify("🗂 ${openFolder.name} → ✅ Checklist, with everything in it.")
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete folder") },
                            onClick = {
                                menu = false
                                openId = null
                                scope.launch {
                                    repo.deleteFolder(openFolder.id)
                                    notify("Folder removed. Its thoughts are still here, loose.")
                                }
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (openFolder == null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🗂 Folders", style = MaterialTheme.typography.labelLarge, color = Rune, modifier = Modifier.weight(1f))
                        AssistChip(onClick = { newFolderOpen = true }, label = { Text("+ New folder") })
                    }
                }
                if (folders.isEmpty()) {
                    item {
                        Text(
                            "Make one for anything that belongs together — “Way to office”, “Doctors”, “Home repairs”.",
                            style = MaterialTheme.typography.bodySmall, color = Muted,
                        )
                    }
                }
                items(folders, key = { "f-" + it.id }) { f ->
                    val inside = thoughts.filter { it.folderId == f.id }
                    Card(Modifier.fillMaxWidth().clickable { openId = f.id }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.icon, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, color = Parchment, fontWeight = FontWeight.Bold)
                                Text(
                                    if (inside.isEmpty()) "empty"
                                    else inside.take(3).joinToString(" · ") { it.text.take(28) },
                                    style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1,
                                )
                            }
                            Text("${inside.size} ›", color = Muted)
                        }
                    }
                }
                item {
                    Text(
                        "💭 Loose thoughts (${shown.size})",
                        style = MaterialTheme.typography.labelLarge, color = Rune,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (openFolder != null) "Nothing in “${openFolder.name}” yet. Add below, by thumb or by mic."
                        else "Nothing loose. Type below — “HDFC ATM, sector 104”.",
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                }
            }
            items(shown, key = { it.id }) { note ->
                ThoughtCard(
                    note = note,
                    photos = photosByNote[note.id].orEmpty(),
                    expanded = expanded == note.id,
                    onToggle = { expanded = if (expanded == note.id) null else note.id },
                    onAddPhoto = { path -> scope.launch { repo.addAttachment("note", note.id, path) } },
                    onRemovePhoto = { scope.launch { repo.deleteAttachment(it.id) } },
                    onEdit = { editing = note },
                    onMove = { moving = note },
                    onToChecklist = {
                        scope.launch { repo.setNoteKind(note.id, null); notify("✅ Moved to the Checklist.") }
                    },
                    onArchive = {
                        scope.launch {
                            if (repo.noteToArchive(note.id)) notify("Saved to Archives — now searchable.")
                            else notify("Already in the Archives.")
                        }
                    },
                    onDelete = { scope.launch { repo.deleteNote(note.id) } },
                )
            }
        }

        if (input.isNotBlank() && folderGuess != null && !skipFolder) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🗂 Into “${folderGuess.name}”",
                    style = MaterialTheme.typography.labelSmall, color = Rune, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { skipFolder = true }) {
                    Text("✕", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(openFolder?.let { "Add to ${it.name}…" } ?: "Remember…") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            MicButton { spoken ->
                scope.launch {
                    val r = repo.captureNote(spoken, folderId = openId, kind = KIND_THOUGHT)
                    notify(r.describe { "" })
                }
            }
            TextButton(onClick = { snapping = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("📷") }
            Button(
                enabled = input.isNotBlank(),
                onClick = {
                    val text = input.trim()
                    val into = openId ?: folderGuess?.takeIf { !skipFolder }?.id
                    val intoName = if (openId == null) folderGuess?.takeIf { !skipFolder }?.name else null
                    input = ""
                    scope.launch {
                        val id = repo.addNote(text, kind = KIND_THOUGHT)
                        into?.let { repo.setNoteFolder(id, it) }
                        intoName?.let { notify("💭 → 🗂 $it") }
                    }
                },
            ) { Text("Add") }
        }
    }
}

/** A thought, with any phone number in it underlined and tappable to call. */
private fun linked(text: String): AnnotatedString {
    val phones = PhoneFind.find(text)
    if (phones.isEmpty()) return AnnotatedString(text)
    val style = TextLinkStyles(SpanStyle(color = Rune, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var at = 0
        phones.forEach { p ->
            append(text.substring(at, p.range.first))
            withLink(LinkAnnotation.Url("tel:${p.dial}", style)) { append(text.substring(p.range.first, p.range.last + 1)) }
            at = p.range.last + 1
        }
        append(text.substring(at))
    }
}

@Composable
private fun ThoughtCard(
    note: NoteEntity,
    photos: List<AttachmentEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddPhoto: (String) -> Unit,
    onRemovePhoto: (AttachmentEntity) -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onToChecklist: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val phone = remember(note.text) { PhoneFind.find(note.text).firstOrNull() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    linked(note.text),
                    style = MaterialTheme.typography.bodyMedium, color = Parchment,
                    modifier = Modifier.weight(1f).clickable(onClick = onToggle),
                )
                if (photos.isNotEmpty() && !expanded) {
                    Text("📷${photos.size}", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (expanded) "▲" else "⋯", color = Muted)
                }
            }
            if (expanded) {
                PhotoStrip(photos = photos, onAdd = onAddPhoto, onRemove = onRemovePhoto)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (phone != null) {
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.dial}"))) }
                        }) { Text("📞 Call", style = MaterialTheme.typography.labelSmall) }
                    }
                    TextButton(onClick = {
                        val q = Uri.encode(PhoneFind.withoutPhones(note.text))
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q"))) }
                    }) { Text("📍 Map", style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = onEdit) { Text("✎ Edit", style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = onMove) { Text("🗂 Move", style = MaterialTheme.typography.labelSmall) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = onToChecklist) { Text("→ ✅ To-do", style = MaterialTheme.typography.labelSmall) }
                    if (note.docId == null) {
                        TextButton(onClick = onArchive) { Text("→ Archive", style = MaterialTheme.typography.labelSmall) }
                    }
                    TextButton(onClick = onDelete) {
                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = Ember)
                    }
                }
                EditedStamp(note.updatedAt)
            }
        }
    }
}

@Composable
private fun EditThoughtDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit thought") },
        text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), maxLines = 6) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column {
                (listOf<FolderEntity?>(null) + folders).forEach { f ->
                    val selected = f?.id == current
                    TextButton(onClick = { onPick(f?.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            (if (selected) "✓ " else "") + (f?.let { "${it.icon} ${it.name}" } ?: "💭 No folder (loose)"),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (folders.isEmpty()) {
                    Text("No folders yet — make one with “+ New folder”.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
