package com.mindquest.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.AttachmentEntity
import com.mindquest.app.data.FolderEntity
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.NoteEntity
import com.mindquest.app.domain.Cadences
import com.mindquest.app.domain.Categories
import com.mindquest.app.domain.Reminders
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

/**
 * Quick-capture inbox. Type a line, send it, and it lands as a note — with an optional
 * reminder. Notes can graduate into a Quest (earns XP) or into the Archives (searchable).
 */
@Composable
fun InboxScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val allNotes by repo.observeNotes().collectAsState(emptyList())
    val notePhotos by repo.observeAttachments("note").collectAsState(emptyList())
    val photosByNote = notePhotos.groupBy { it.ownerId }
    val categoryCounts by repo.observeNoteFolders().collectAsState(emptyMap())
    val customFolders by repo.observeFolders().collectAsState(emptyList())
    val customCounts by repo.observeFolderCounts().collectAsState(emptyMap())
    // A selection is either a user-made folder or a category; the two never mix, so one
    // nullable id each is clearer than a sealed type for two cases.
    var folder by remember { mutableStateOf<String?>(null) }
    var customFolder by remember { mutableStateOf<String?>(null) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderEntity?>(null) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    val openFolder = customFolders.firstOrNull { it.id == customFolder }
    val notes = allNotes.filter {
        when {
            customFolder != null -> it.folderId == customFolder
            folder != null -> it.category == folder && it.folderId == null
            else -> true
        }
    }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var pendingRemind by remember { mutableStateOf<Long?>(null) }
    // The category is shown before the note is sent, not applied silently afterwards. It
    // tracks what you type until you touch it, and once touched it stops second-guessing you.
    var pendingCategory by remember { mutableStateOf("general") }
    var categoryChosen by remember { mutableStateOf(false) }

    LaunchedEffect(input, categoryChosen, folder) {
        // Typing inside a folder files it there: opening Shopping and adding a line plainly
        // means "this is shopping", whatever the words happen to look like.
        if (!categoryChosen) pendingCategory = folder ?: Categories.classify(input)
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) notify("Without notification permission reminders won't alert you.") }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.hasPermission(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Speaking a line goes through the same capture path as the widget, so dictation picks
    // up the date in the sentence: "buy sugar by 21st September" arrives with its reminder.
    // Saying back what was heard matters more here than on the keyboard — a misheard word is
    // invisible until you read it.
    fun capture(spoken: String) {
        ensureNotifPermission()
        scope.launch {
            val r = repo.captureNote(spoken, folderId = customFolder, category = folder)
            notify(
                buildString {
                    append(r.text)
                    r.dueAt?.let { append(" · ⏰ ${timeFmt.format(Date(it))}") }
                    r.repeat?.let { append(" · 🔁 ${Cadences.of(it).label}") }
                    if (r.folderId == null) append(" · ${Categories.of(r.category).label}")
                },
            )
        }
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onDismiss = { newFolderOpen = false },
            onCreate = { name ->
                newFolderOpen = false
                scope.launch {
                    val id = repo.createFolder(name)
                    customFolder = id; folder = null
                    notify("Folder “$name” created — everything you add now goes inside it.")
                }
            },
        )
    }

    editing?.let { note ->
        EditNoteDialog(
            initialText = note.text,
            initialRemindAt = note.remindAt,
            initialRepeat = note.repeat,
            onDismiss = { editing = null },
            onSave = { text, remindAt, repeat ->
                editing = null
                ensureNotifPermission()
                scope.launch {
                    repo.editNote(note.id, text, remindAt, repeat)
                    notify(
                        buildString {
                            append("Saved")
                            remindAt?.let { append(" · ⏰ ${timeFmt.format(Date(it))}") }
                            repeat?.let { append(" · 🔁 ${Cadences.of(it).label}") }
                        },
                    )
                }
            },
        )
    }

    renaming?.let { target ->
        NewFolderDialog(
            initial = target.name,
            title = "Rename folder",
            confirm = "Rename",
            onDismiss = { renaming = null },
            onCreate = { name ->
                renaming = null
                scope.launch { repo.renameFolder(target.id, name) }
            },
        )
    }

    LaunchedEffect(notes.size) {
        if (notes.isNotEmpty()) listState.animateScrollToItem(notes.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Inbox", style = MaterialTheme.typography.headlineMedium, color = Parchment)
        Text(
            when {
                openFolder != null ->
                    "${openFolder.name} — anything you add or say now lands in this folder."
                folder != null ->
                    "Showing ${Categories.of(folder).label}. Reminders stay exactly as you set them."
                else ->
                    "Jot an errand or checklist item. Add a reminder, or turn it into a quest."
            },
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
        Spacer(Modifier.height(6.dp))
        CustomFolderRow(
            folders = customFolders,
            counts = customCounts,
            selected = customFolder,
            onSelect = { customFolder = it; if (it != null) folder = null },
            onCreate = { newFolderOpen = true },
            onRename = { renaming = it },
            onDelete = { target ->
                customFolder = null
                scope.launch {
                    repo.deleteFolder(target.id)
                    notify("Folder “${target.name}” removed. Its lines are still in the Inbox.")
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        FolderRow(
            counts = categoryCounts,
            selected = folder,
            onSelect = { folder = it; if (it != null) customFolder = null },
        )
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (notes.isEmpty()) {
                item {
                    Text(
                        when {
                            openFolder != null ->
                                "“${openFolder.name}” is empty. Add the first item below, by thumb or by mic."
                            folder != null -> "Nothing in ${Categories.of(folder).label} yet."
                            else -> "Nothing captured yet. Type below — “call the plumber”, “milk, eggs, rice”…"
                        },
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(notes) { note ->
                NoteCard(
                    note = note,
                    photos = photosByNote[note.id].orEmpty(),
                    onAddPhoto = { path -> scope.launch { repo.addAttachment("note", note.id, path) } },
                    onRemovePhoto = { scope.launch { repo.deleteAttachment(it.id) } },
                    onToggleDone = {
                        scope.launch {
                            // A repeating note rolls forward instead of ticking; say where to.
                            repo.setNoteDone(note.id, !note.done)?.let { next ->
                                notify("Done — next one ${timeFmt.format(Date(next))}.")
                            }
                        }
                    },
                    onEdit = { editing = note },
                    onRemind = {
                        ensureNotifPermission()
                        pickDateTime(context) { at ->
                            scope.launch {
                                repo.setNoteReminder(note.id, at)
                                notify("Reminder set for ${timeFmt.format(Date(at))}")
                            }
                        }
                    },
                    onClearRemind = {
                        scope.launch { repo.setNoteReminder(note.id, null); notify("Reminder cleared.") }
                    },
                    onQuest = {
                        scope.launch {
                            if (repo.noteToQuest(note.id)) notify("Added to your quest board — complete it for XP.")
                            else notify("Already a quest.")
                        }
                    },
                    onArchive = {
                        scope.launch {
                            if (repo.noteToArchive(note.id)) notify("Saved to Archives — now searchable.")
                            else notify("Already in the Archives.")
                        }
                    },
                    onDelete = { scope.launch { repo.deleteNote(note.id) } },
                    onCategory = { scope.launch { repo.setNoteCategory(note.id, it) } },
                )
            }
        }

        pendingRemind?.let {
            Text(
                "⏰ Reminder on send: ${timeFmt.format(Date(it))}  (tap the clock to clear)",
                style = MaterialTheme.typography.labelSmall, color = Rune,
            )
        }

        if (input.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (categoryChosen) "Filing under" else "Looks like",
                    style = MaterialTheme.typography.labelSmall, color = Muted,
                )
                Spacer(Modifier.width(6.dp))
                CategoryChip(pendingCategory) { pendingCategory = it; categoryChosen = true }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(openFolder?.let { "Add to ${it.name}…" } ?: "Capture a thought…")
                },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            MicButton { spoken -> capture(spoken) }
            TextButton(onClick = {
                if (pendingRemind != null) {
                    pendingRemind = null
                } else {
                    ensureNotifPermission()
                    pickDateTime(context) { pendingRemind = it }
                }
            }) { Text(if (pendingRemind != null) "⏰✕" else "⏰") }
            Button(
                enabled = input.isNotBlank(),
                onClick = {
                    val t = input.trim()
                    val at = pendingRemind
                    val cat = pendingCategory
                    val chosen = categoryChosen
                    input = ""; pendingRemind = null; categoryChosen = false
                    scope.launch {
                        val id = repo.addNote(t, at, cat, chosen)
                        customFolder?.let { repo.setNoteFolder(id, it) }
                    }
                },
            ) { Text("Add") }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    photos: List<AttachmentEntity>,
    onAddPhoto: (String) -> Unit,
    onRemovePhoto: (AttachmentEntity) -> Unit,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onRemind: () -> Unit,
    onClearRemind: () -> Unit,
    onQuest: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onCategory: (String) -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleDone, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (note.done) "✓" else "○", color = if (note.done) Sage else Rune)
                }
                // Tapping the line itself opens it for editing — the obvious gesture, and it
                // keeps the row from filling up with buttons.
                Text(
                    note.text,
                    modifier = Modifier.weight(1f).clickable(onClick = onEdit),
                    color = if (note.done) Muted else Parchment,
                    textDecoration = if (note.done) TextDecoration.LineThrough else null,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("✎", color = Muted)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append(timeFmt.format(Date(note.createdAt)))
                        note.remindAt?.let { append("  ·  ⏰ ${timeFmt.format(Date(it))}") }
                        note.repeat?.let { append(" 🔁 ${Cadences.of(it).label}") }
                        if (note.questId != null) append("  ·  ⚔️")
                        if (note.docId != null) append("  ·  📜")
                    },
                    style = MaterialTheme.typography.labelSmall, color = Muted,
                )
                EditedStamp(note.updatedAt)
            }
            CategoryChip(note.category, onCategory)
            // Only notes that have photos show the strip — most notes are a line of text,
            // and a camera button on every one of them would turn the Inbox into clutter.
            if (photos.isNotEmpty()) {
                PhotoStrip(photos = photos, onAdd = onAddPhoto, onRemove = onRemovePhoto)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = if (note.remindAt == null) onRemind else onClearRemind) {
                    Text(if (note.remindAt == null) "Remind" else "Unremind", style = MaterialTheme.typography.labelSmall)
                }
                if (note.questId == null) {
                    TextButton(onClick = onQuest) { Text("→ Quest", style = MaterialTheme.typography.labelSmall) }
                }
                if (note.docId == null) {
                    TextButton(onClick = onArchive) { Text("→ Archive", style = MaterialTheme.typography.labelSmall) }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", style = MaterialTheme.typography.labelSmall, color = Ember)
                }
            }
        }
    }
}
