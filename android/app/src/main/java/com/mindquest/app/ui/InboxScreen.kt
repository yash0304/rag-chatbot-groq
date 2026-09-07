package com.mindquest.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.NoteEntity
import com.mindquest.app.domain.Reminders
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val notes by repo.observeNotes().collectAsState(emptyList())
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var pendingRemind by remember { mutableStateOf<Long?>(null) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) notify("Without notification permission reminders won't alert you.") }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.hasPermission(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(notes.size) {
        if (notes.isNotEmpty()) listState.animateScrollToItem(notes.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Inbox", style = MaterialTheme.typography.headlineMedium, color = Parchment)
        Text(
            "Jot an errand or checklist item. Add a reminder, or turn it into a quest.",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (notes.isEmpty()) {
                item {
                    Text(
                        "Nothing captured yet. Type below — “call the plumber”, “milk, eggs, rice”…",
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(notes) { note ->
                NoteCard(
                    note = note,
                    onToggleDone = { scope.launch { repo.setNoteDone(note.id, !note.done) } },
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
                )
            }
        }

        pendingRemind?.let {
            Text(
                "⏰ Reminder on send: ${timeFmt.format(Date(it))}  (tap the clock to clear)",
                style = MaterialTheme.typography.labelSmall, color = Rune,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Capture a thought…") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            Spacer(Modifier.width(4.dp))
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
                    input = ""; pendingRemind = null
                    scope.launch { repo.addNote(t, at) }
                },
            ) { Text("Add") }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onToggleDone: () -> Unit,
    onRemind: () -> Unit,
    onClearRemind: () -> Unit,
    onQuest: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleDone, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (note.done) "✓" else "○", color = if (note.done) Sage else Rune)
                }
                Text(
                    note.text,
                    modifier = Modifier.weight(1f),
                    color = if (note.done) Muted else Parchment,
                    textDecoration = if (note.done) TextDecoration.LineThrough else null,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
                        if (note.questId != null) append("  ·  ⚔️")
                        if (note.docId != null) append("  ·  📜")
                    },
                    style = MaterialTheme.typography.labelSmall, color = Muted,
                )
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

/** Native date → time picker chain; returns the chosen instant in epoch millis. */
private fun pickDateTime(context: Context, onPicked: (Long) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val c = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(c.timeInMillis)
                },
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false,
            ).show()
        },
        now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH),
    ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
}
