package com.mindquest.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.HabitEntity
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.domain.Cadences
import com.mindquest.app.domain.Reminders
import kotlinx.coroutines.launch

/**
 * Habits — what used to be Missions — as a block at the top of the Inbox. They're added the
 * same way as anything else ("walk 5000 steps every day at 9pm"), ticked once per day or week,
 * and keep their streaks. Everything else about one lives behind its ⋯.
 */
@Composable
fun HabitsBlock(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val habits by repo.observeHabits().collectAsState(emptyList())
    if (habits.isEmpty()) return
    val pending = habits.count { !repo.isCheckedInThisPeriod(it) }
    var expanded by remember { mutableStateOf(true) }
    var options by remember { mutableStateOf<HabitEntity?>(null) }

    options?.let { h -> HabitOptionsDialog(repo, h, notify) { options = null } }

    Card(colors = CardDefaults.cardColors(containerColor = Rune.copy(alpha = 0.08f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🔁 Habits",
                    style = MaterialTheme.typography.titleSmall, color = Rune, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pending == 0) "all done ✓" else "$pending to do",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pending == 0) Verdant else Muted,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) "▲" else "▼", color = Muted)
            }
            if (expanded) {
                habits.forEach { h ->
                    val cadence = Cadences.of(h.cadence)
                    val done = repo.isCheckedInThisPeriod(h)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            enabled = !done,
                            onClick = {
                                scope.launch {
                                    val r = repo.checkin(h.id)
                                    if (!r.alreadyDone) notify(buildString {
                                        append("🔥 ${h.title} · +${r.xpAwarded} XP")
                                        if (r.newStreak > 1) append(" · streak ${r.newStreak}")
                                        if (r.levelUp) append(" · ⭐ Level up!")
                                        r.achievementsUnlocked.forEach { append(" · ${it.icon} ${it.name}") }
                                    })
                                }
                            },
                        ) { Text(if (done) "✅" else "⬜") }
                        Column(Modifier.weight(1f).clickable { options = h }) {
                            Text(h.title, color = if (done) Muted else Parchment)
                            Text(
                                buildString {
                                    append("🔥 ${h.streak} · ${cadence.label}")
                                    h.remindMinuteOfDay?.let { append(" · ⏰ ${Reminders.formatTimeOfDay(it)}") }
                                    if (done) append(" · done this ${cadence.unit}")
                                },
                                style = MaterialTheme.typography.labelSmall, color = Muted,
                            )
                        }
                        TextButton(
                            onClick = { options = h },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text("⋯", color = Muted) }
                    }
                }
            }
        }
    }
}

/** Edit, nudge time, photos and delete for one habit — the rarely-used bits, kept off the list. */
@Composable
private fun HabitOptionsDialog(
    repo: MindQuestRepository,
    habit: HabitEntity,
    notify: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cadence = Cadences.of(habit.cadence)
    val allPhotos by repo.observeAttachments("habit").collectAsState(emptyList())
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) notify("Without notification permission the nudge stays silent.") }

    if (editing) {
        EditMissionDialog(
            initialTitle = habit.title,
            initialCadence = habit.cadence,
            initialTargetNote = habit.targetNote,
            initialTargetDate = habit.targetDate,
            title = "Edit habit",
            onDismiss = { editing = false },
            onSave = { name, newCadence, targetNote, targetDate ->
                editing = false
                onDismiss()
                scope.launch {
                    repo.editHabit(habit.id, name, newCadence, targetNote, targetDate)
                    notify("Saved — ${Cadences.of(newCadence).label}.")
                }
            },
        )
        return
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${habit.title}”?") },
            text = { Text("Its streak (${habit.streak}, best ${habit.bestStreak}) goes with it.") },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    scope.launch { repo.deleteHabit(habit.id); notify("Habit removed.") }
                }) { Text("Delete", color = Ember) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habit.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "🔥 streak ${habit.streak} · 🏔️ best ${habit.bestStreak} · ${cadence.label}",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
                habit.targetNote?.let { target ->
                    Text(
                        buildString {
                            append("🎯 $target")
                            habit.targetDate?.let { append(" by ${Cadences.formatTarget(it)}") }
                        },
                        style = MaterialTheme.typography.bodySmall, color = Rune,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.hasPermission(context)) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        pickTimeOfDay(context, habit.remindMinuteOfDay) { minute ->
                            scope.launch {
                                repo.setHabitReminder(habit.id, minute)
                                notify("Nudging you ${cadence.label} at ${Reminders.formatTimeOfDay(minute)}.")
                            }
                            onDismiss()
                        }
                    }) {
                        Text(
                            habit.remindMinuteOfDay?.let { "⏰ ${Reminders.formatTimeOfDay(it)} — change" }
                                ?: "⏰ Add a nudge",
                        )
                    }
                    if (habit.remindMinuteOfDay != null) {
                        TextButton(onClick = {
                            onDismiss()
                            scope.launch { repo.setHabitReminder(habit.id, null); notify("Nudge off.") }
                        }) { Text("off", color = Muted) }
                    }
                }
                PhotoStrip(
                    photos = allPhotos.filter { it.ownerId == habit.id },
                    onAdd = { path -> scope.launch { repo.addAttachment("habit", habit.id, path) } },
                    onRemove = { scope.launch { repo.deleteAttachment(it.id) } },
                )
                EditedStamp(habit.updatedAt)
            }
        },
        confirmButton = { TextButton(onClick = { editing = true }) { Text("✎ Edit") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = Ember) }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
