package com.mindquest.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.HabitEntity
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.ProfileEntity
import com.mindquest.app.data.QuestEntity
import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.mindquest.app.domain.Cadences
import com.mindquest.app.domain.Catalogs
import com.mindquest.app.domain.Reminders
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.mindquest.app.domain.Categories
import com.mindquest.app.domain.GameEngine
import kotlinx.coroutines.launch

private val questStamp = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

// ---------- shared bits ----------

@Composable
fun XpBar(profile: ProfileEntity) {
    val floor = GameEngine.xpRequiredForLevel(profile.level)
    val next = GameEngine.xpRequiredForLevel(profile.level + 1)
    val span = (next - floor).coerceAtLeast(1)
    val pct = ((profile.xp - floor).toFloat() / span).coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${profile.level}", color = Rune, fontWeight = FontWeight.Bold)
            Text("${profile.xp} XP · ${next - profile.xp} to next", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = Rune,
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, color = Parchment)
                Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

// ---------- Dashboard ----------

@Composable
fun DashboardScreen(repo: MindQuestRepository, profile: ProfileEntity) {
    // Recompute aggregates whenever XP changes (i.e., after any award).
    val xp7d by produceState(0L, profile.xp) { value = repo.xpLast7Days() }
    val questsDone by produceState(0, profile.xp) { value = repo.completedQuestCount() }
    val bestStreak by produceState(0, profile.xp) { value = repo.maxStreak() }
    val quests by repo.observeActiveQuests().collectAsState(emptyList())
    val habits by repo.observeHabits().collectAsState(emptyList())
    val pending = habits.filter { !repo.isCheckedInThisPeriod(it) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Hail, ${profile.heroName}", style = MaterialTheme.typography.headlineMedium, color = Parchment)
            Spacer(Modifier.height(8.dp))
            Card { Box(Modifier.padding(16.dp)) { XpBar(profile) } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("XP this week", "$xp7d", "⚡", Modifier.weight(1f))
                StatTile("Quests done", "$questsDone", "⚔️", Modifier.weight(1f))
                StatTile("Best streak", "$bestStreak", "🔥", Modifier.weight(1f))
            }
        }
        item { Text("⚔️ Active quests", style = MaterialTheme.typography.titleMedium, color = Rune) }
        if (quests.isEmpty()) item { Text("No active quests — visit the Quests tab.", color = Muted) }
        items(quests.take(5)) { q ->
            Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(q.title); Text("+${q.xpReward} XP", color = Rune)
            } }
        }
        item { Text("🔥 Today's missions", style = MaterialTheme.typography.titleMedium, color = Rune) }
        if (habits.isEmpty()) item { Text("No missions yet — forge one in the Habits tab.", color = Muted) }
        else if (pending.isEmpty()) item { Text("All missions complete. The campfires stay lit.", color = Verdant) }
        items(pending.take(5)) { h ->
            Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(h.title); Text("streak ${h.streak}", color = Muted)
            } }
        }
    }
}

// ---------- Quests ----------

private val DIFFICULTIES = listOf("trivial", "easy", "normal", "hard", "epic")

@Composable
fun QuestsScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val quests by repo.observeAllQuests().collectAsState(emptyList())
    // One subscription for the whole board rather than one per card: a flow per quest would
    // mean a new Room query every time the list re-composed.
    val allPhotos by repo.observeAttachments("quest").collectAsState(emptyList())
    val questPhotos = allPhotos.groupBy { it.ownerId }
    var title by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("normal") }
    var editingQuest by remember { mutableStateOf<QuestEntity?>(null) }

    val drafts = quests.filter { it.status == "draft" }
    val active = quests.filter { it.status == "active" }
    val done = quests.filter { it.status == "completed" }
    var generating by remember { mutableStateOf(false) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    // Same rule as the Inbox: the category is visible and changeable before the quest exists.
    var newCategory by remember { mutableStateOf("general") }
    var categoryChosen by remember { mutableStateOf(false) }
    LaunchedEffect(title, categoryChosen) {
        if (!categoryChosen) newCategory = Categories.classify(title)
    }
    val shownActive = active.filter { categoryFilter == null || it.category == categoryFilter }

    editingQuest?.let { quest ->
        EditQuestDialog(
            initialTitle = quest.title,
            initialDifficulty = quest.difficulty,
            initialDueAt = quest.dueAt,
            difficulties = DIFFICULTIES,
            xpFor = { Catalogs.difficultyXp[it] },
            onDismiss = { editingQuest = null },
            onSave = { newTitle, newDifficulty, dueAt ->
                editingQuest = null
                scope.launch { repo.editQuest(quest.id, newTitle, newDifficulty, dueAt) }
            },
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Quest Board", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                TextButton(enabled = !generating, onClick = {
                    generating = true
                    scope.launch {
                        val n = repo.generateQuests(3)
                        generating = false
                        notify("The Questmaster drafted $n quest(s) — accept the ones you'll take.")
                    }
                }) { Text(if (generating) "…" else "🔮 Generate") }
            }
        }
        if (drafts.isNotEmpty()) {
            item { Text("Questmaster drafts", style = MaterialTheme.typography.titleMedium, color = Rune) }
            items(drafts) { q ->
                Card { Column(Modifier.padding(12.dp)) {
                    Text(q.title, color = Parchment, fontWeight = FontWeight.Bold)
                    q.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${q.difficulty} · +${q.xpReward} XP", style = MaterialTheme.typography.labelSmall, color = Rune)
                        Row {
                            TextButton(onClick = { scope.launch { repo.abandonQuest(q.id) } }) { Text("Decline", color = Muted) }
                            Button(onClick = { scope.launch { repo.acceptQuest(q.id) } }) { Text("Accept") }
                        }
                    }
                } }
            }
        }
        item {
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(title, { title = it }, label = { Text("New quest") }, singleLine = true, modifier = Modifier.weight(1f))
                    // Dictation fills the field rather than submitting: a quest still needs its
                    // difficulty chosen, so the spoken words wait here for that one tap.
                    MicButton { title = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    DIFFICULTIES.forEach { d ->
                        FilterChip(
                            selected = difficulty == d,
                            onClick = { difficulty = d },
                            label = { Text("$d·${Catalogs.difficultyXp[d]}", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (title.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (categoryChosen) "Filing under" else "Looks like",
                            style = MaterialTheme.typography.labelSmall, color = Muted,
                        )
                        Spacer(Modifier.width(6.dp))
                        CategoryChip(newCategory) { newCategory = it; categoryChosen = true }
                    }
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val t = title; title = ""
                            val cat = newCategory
                            val chosen = categoryChosen
                            categoryChosen = false
                            scope.launch { repo.createQuest(t, difficulty, category = cat, categoryChosen = chosen) }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Post quest") }
            } }
        }
        item { Text("Active", style = MaterialTheme.typography.titleMedium, color = Rune) }
        item {
            CategoryFilterRow(
                present = active.mapNotNull { it.category }.toSet(),
                selected = categoryFilter,
                onSelect = { categoryFilter = it },
            )
        }
        if (active.isEmpty()) item { Text("The board is clear.", color = Muted) }
        items(shownActive) { q ->
            Card { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        q.title,
                        color = Parchment,
                        modifier = Modifier.weight(1f).clickable { editingQuest = q },
                    )
                    TextButton(
                        onClick = { editingQuest = q },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text("✎", color = Muted) }
                }
                Text(
                    buildString {
                        append("${q.difficulty} · +${q.xpReward} XP")
                        q.dueAt?.let { append(" · ⏳ ${questStamp.format(Date(it))}") }
                    },
                    style = MaterialTheme.typography.bodySmall, color = Rune,
                )
                EditedStamp(q.updatedAt)
                CategoryChip(q.category) { scope.launch { repo.setQuestCategory(q.id, it) } }
                // The photo trail: proof of the run, the receipt, the before-and-after.
                PhotoStrip(
                    photos = questPhotos[q.id].orEmpty(),
                    onAdd = { path -> scope.launch { repo.addAttachment("quest", q.id, path) } },
                    onRemove = { scope.launch { repo.deleteAttachment(it.id) } },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { scope.launch { repo.abandonQuest(q.id) } }) { Text("Abandon") }
                    Button(onClick = {
                        scope.launch {
                            val r = repo.completeQuest(q.id)
                            notify(buildString {
                                append("Quest complete! +${r.xpAwarded} XP")
                                if (r.levelUp) append(" · ⭐ Level ${r.newLevel}!")
                                r.achievementsUnlocked.forEach { append(" · ${it.icon} ${it.name}") }
                            })
                        }
                    }) { Text("Complete") }
                }
            } }
        }
        if (done.isNotEmpty()) {
            item { Text("Completed (${done.size})", style = MaterialTheme.typography.titleMedium, color = Rune) }
            items(done.take(10)) { q ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(q.title, color = Muted); Text("+${q.xpReward}", color = Muted)
                }
            }
        }
    }
}

// ---------- Habits / recurring missions ----------

@Composable
fun HabitsScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) notify("Without notification permission the daily nudge stays silent.") }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.hasPermission(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val scope = rememberCoroutineScope()
    val habits by repo.observeHabits().collectAsState(emptyList())
    val allPhotos by repo.observeAttachments("habit").collectAsState(emptyList())
    val photosByHabit = allPhotos.groupBy { it.ownerId }
    var title by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf("daily") }
    var editing by remember { mutableStateOf<HabitEntity?>(null) }

    editing?.let { habit ->
        EditMissionDialog(
            initialTitle = habit.title,
            initialCadence = habit.cadence,
            initialTargetNote = habit.targetNote,
            initialTargetDate = habit.targetDate,
            onDismiss = { editing = null },
            onSave = { name, newCadence, targetNote, targetDate ->
                editing = null
                scope.launch {
                    repo.editHabit(habit.id, name, newCadence, targetNote, targetDate)
                    notify("Saved — ${Cadences.of(newCadence).label}.")
                }
            },
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Missions", style = MaterialTheme.typography.headlineMedium, color = Parchment) }
        item {
            Text(
                "Anything that comes round again — every evening, every Monday, or the 1st of " +
                    "each month, quarter, half-year or year.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }
        item {
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(title, { title = it }, label = { Text("New mission") }, singleLine = true, modifier = Modifier.weight(1f))
                    MicButton { title = it }
                }
                CadenceRow(selected = cadence, onSelect = { cadence = it })
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val t = title; title = ""
                            scope.launch { repo.createHabit(t, cadence) }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Forge") }
            } }
        }
        if (habits.isEmpty()) item { Text("No missions yet. Small repeated deeds build legends.", color = Muted) }
        items(habits) { h ->
            val cadenceOf = Cadences.of(h.cadence)
            val doneThisPeriod = repo.isCheckedInThisPeriod(h)
            Card { Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(h.title, color = Parchment, modifier = Modifier.weight(1f).clickable { editing = h })
                    TextButton(
                        onClick = { editing = h },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text("✎", color = Muted) }
                    TextButton(onClick = { scope.launch { repo.deleteHabit(h.id) } }) { Text("remove", color = Muted) }
                }
                Text(
                    "🔥 streak ${h.streak} · 🏔️ best ${h.bestStreak} · ${cadenceOf.label}",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
                // What it is all for, and how much runway is left. The countdown is the part
                // that does the work — "80 kg" alone never made anyone get on the scales.
                h.targetNote?.let { target ->
                    Text(
                        buildString {
                            append("🎯 $target")
                            h.targetDate?.let { append(" by ${Cadences.formatTarget(it)}") }
                            h.targetDate?.let { date ->
                                Cadences.timeLeft(h.cadence, date)?.let { append(" · $it") }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall, color = Rune,
                    )
                }
                EditedStamp(h.updatedAt)

                // The nudge. It stays quiet in periods the mission is already done, so it
                // only ever speaks up about the ones that would otherwise slip.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        ensureNotifPermission()
                        pickTimeOfDay(context, h.remindMinuteOfDay) { minute ->
                            scope.launch {
                                repo.setHabitReminder(h.id, minute)
                                notify("Nudging you ${cadenceOf.label} at ${Reminders.formatTimeOfDay(minute)}.")
                            }
                        }
                    }) {
                        Text(
                            h.remindMinuteOfDay?.let { "⏰ ${Reminders.formatTimeOfDay(it)} ${cadenceOf.label}" }
                                ?: "⏰ Remind ${cadenceOf.label}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (h.remindMinuteOfDay != null) {
                        TextButton(onClick = {
                            scope.launch { repo.setHabitReminder(h.id, null); notify("Nudge off.") }
                        }) { Text("off", style = MaterialTheme.typography.labelSmall, color = Muted) }
                    }
                }
                PhotoStrip(
                    photos = photosByHabit[h.id].orEmpty(),
                    onAdd = { path -> scope.launch { repo.addAttachment("habit", h.id, path) } },
                    onRemove = { scope.launch { repo.deleteAttachment(it.id) } },
                )
                Spacer(Modifier.height(6.dp))
                if (doneThisPeriod) {
                    Text("✓ Done this ${cadenceOf.unit}", color = Verdant)
                } else {
                    Button(onClick = {
                        scope.launch {
                            val r = repo.checkin(h.id)
                            if (!r.alreadyDone) notify(buildString {
                                append("Mission done! +${r.xpAwarded} XP (×${"%.2f".format(r.multiplier)})")
                                if (r.levelUp) append(" · ⭐ Level up!")
                                r.achievementsUnlocked.forEach { append(" · ${it.icon} ${it.name}") }
                            })
                        }
                    }) { Text("Complete this ${cadenceOf.unit}'s mission") }
                }
            } }
        }
    }
}


/**
 * Pick a time of day for a daily nudge, returning minutes past midnight. Uses the framework
 * dialog rather than the Compose time picker, which is still experimental.
 */
private fun pickTimeOfDay(context: Context, currentMinuteOfDay: Int?, onPicked: (Int) -> Unit) {
    val now = Calendar.getInstance()
    val hour = currentMinuteOfDay?.div(60) ?: now.get(Calendar.HOUR_OF_DAY)
    val minute = currentMinuteOfDay?.rem(60) ?: 0
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(h * 60 + m) },
        hour, minute, false,
    ).show()
}
