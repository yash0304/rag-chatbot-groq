package com.mindquest.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mindquest.app.domain.Cadences
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Editing, wherever the thing happens to be shown.
 *
 * Anything you wrote you can rewrite — the wording, when it goes off, how often it comes
 * round, what it is aiming at. Every dialog carries a mic for the same reason the composers
 * do: correcting a misheard word by thumb is exactly the moment dictation was supposed to
 * save you from.
 */

private val editStamp = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

/** "edited 21 Sep, 14:03" — shown only once something has actually been changed. */
@Composable
fun EditedStamp(updatedAt: Long?) {
    updatedAt ?: return
    Text(
        "edited ${editStamp.format(Date(updatedAt))}",
        style = MaterialTheme.typography.labelSmall,
        color = Muted,
    )
}

/** Rewrite a note and reset its reminder, in one place. */
@Composable
fun EditNoteDialog(
    initialText: String,
    initialRemindAt: Long?,
    onDismiss: () -> Unit,
    onSave: (text: String, remindAt: Long?) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    var remindAt by remember { mutableStateOf(initialRemindAt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit note") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    // Dictation replaces the line rather than appending: you are correcting
                    // what is there, not adding to it.
                    MicButton { text = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickDateTime(context, remindAt) { remindAt = it } }) {
                        Text(
                            remindAt?.let { "⏰ ${editStamp.format(Date(it))}" } ?: "⏰ Add a reminder",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (remindAt != null) {
                        TextButton(onClick = { remindAt = null }) {
                            Text("clear", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSave(text.trim(), remindAt) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Rewrite a quest: what it is, what it's worth, and when it's due. */
@Composable
fun EditQuestDialog(
    initialTitle: String,
    initialDifficulty: String,
    initialDueAt: Long?,
    difficulties: List<String>,
    xpFor: (String) -> Int?,
    onDismiss: () -> Unit,
    onSave: (title: String, difficulty: String, dueAt: Long?) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var difficulty by remember { mutableStateOf(initialDifficulty) }
    var dueAt by remember { mutableStateOf(initialDueAt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit quest") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                    )
                    MicButton { title = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    difficulties.forEach { d ->
                        FilterChip(
                            selected = difficulty == d,
                            onClick = { difficulty = d },
                            label = {
                                Text(
                                    "$d·${xpFor(d) ?: 0}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickDateTime(context, dueAt) { dueAt = it } }) {
                        Text(
                            dueAt?.let { "⏳ due ${editStamp.format(Date(it))}" } ?: "⏳ Set a deadline",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (dueAt != null) {
                        TextButton(onClick = { dueAt = null }) {
                            Text("clear", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), difficulty, dueAt) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Rewrite a mission: what it is, how often it comes round, and what it's working towards.
 *
 * The target is two fields because the two halves do different work — "80 kg" is what the
 * reminder should say back to you, "March 2027" is what makes it urgent.
 */
@Composable
fun EditMissionDialog(
    initialTitle: String,
    initialCadence: String,
    initialTargetNote: String?,
    initialTargetDate: String?,
    title: String = "Edit mission",
    onDismiss: () -> Unit,
    onSave: (title: String, cadence: String, targetNote: String?, targetDate: String?) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialTitle) }
    var cadence by remember { mutableStateOf(initialCadence) }
    var targetNote by remember { mutableStateOf(initialTargetNote.orEmpty()) }
    var targetDate by remember { mutableStateOf(initialTargetDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Mission") },
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    MicButton { name = it }
                }
                Spacer(Modifier.height(8.dp))
                CadenceRow(selected = cadence, onSelect = { cadence = it })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetNote,
                    onValueChange = { targetNote = it },
                    label = { Text("Working towards (optional)") },
                    placeholder = { Text("80 kg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickDate(context, targetDate) { targetDate = it } }) {
                        Text(
                            targetDate?.let { "🎯 by ${Cadences.formatTarget(it)}" } ?: "🎯 Set a target date",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (targetDate != null) {
                        TextButton(onClick = { targetDate = null }) {
                            Text("clear", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
                targetDate?.let { date ->
                    Cadences.timeLeft(cadence, date)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Rune)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), cadence, targetNote.trim().ifBlank { null }, targetDate) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Daily through yearly, in one scrollable row so the long list doesn't squash the labels. */
@Composable
fun CadenceRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Cadences.all.forEach { c ->
            FilterChip(
                selected = selected == c.id,
                onClick = { onSelect(c.id) },
                label = { Text(c.label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/**
 * Native date → time picker chain; returns the chosen instant in epoch millis. The framework
 * dialogs rather than the Compose ones, which are still experimental.
 */
internal fun pickDateTime(context: Context, current: Long? = null, onPicked: (Long) -> Unit) {
    val start = Calendar.getInstance().apply { current?.let { timeInMillis = it } }
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
                start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), false,
            ).show()
        },
        start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH),
    ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
}

/** A plain date, returned as ISO yyyy-MM-dd. Used for a target that is a month, not a time. */
internal fun pickDate(context: Context, currentIso: String?, onPicked: (String) -> Unit) {
    val start = Calendar.getInstance()
    currentIso?.let { iso ->
        runCatching { LocalDate.parse(iso) }.getOrNull()?.let {
            start.set(it.year, it.monthValue - 1, it.dayOfMonth)
        }
    }
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day).toString()) },
        start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH),
    ).show()
}
