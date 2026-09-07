package com.mindquest.app.ui

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
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.ProfileEntity
import com.mindquest.app.domain.Catalogs
import com.mindquest.app.domain.GameEngine
import kotlinx.coroutines.launch

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
    val pending = habits.filter { !repo.isCheckedInToday(it) }

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
    var title by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("normal") }

    val drafts = quests.filter { it.status == "draft" }
    val active = quests.filter { it.status == "active" }
    val done = quests.filter { it.status == "completed" }
    var generating by remember { mutableStateOf(false) }

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
                OutlinedTextField(title, { title = it }, label = { Text("New quest") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    DIFFICULTIES.forEach { d ->
                        FilterChip(
                            selected = difficulty == d,
                            onClick = { difficulty = d },
                            label = { Text("$d·${Catalogs.difficultyXp[d]}", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val t = title; title = ""
                            scope.launch { repo.createQuest(t, difficulty) }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Post quest") }
            } }
        }
        item { Text("Active", style = MaterialTheme.typography.titleMedium, color = Rune) }
        if (active.isEmpty()) item { Text("The board is clear.", color = Muted) }
        items(active) { q ->
            Card { Column(Modifier.padding(12.dp)) {
                Text(q.title, color = Parchment)
                Text("${q.difficulty} · +${q.xpReward} XP", style = MaterialTheme.typography.bodySmall, color = Rune)
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

// ---------- Habits ----------

private val CADENCES = listOf("daily", "weekdays", "weekly")

@Composable
fun HabitsScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val habits by repo.observeHabits().collectAsState(emptyList())
    var title by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf("daily") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Daily Missions", style = MaterialTheme.typography.headlineMedium, color = Parchment) }
        item {
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("New mission") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CADENCES.forEach { c ->
                        FilterChip(selected = cadence == c, onClick = { cadence = c }, label = { Text(c) })
                    }
                }
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
        if (habits.isEmpty()) item { Text("No missions yet. Small daily deeds build legends.", color = Muted) }
        items(habits) { h ->
            val doneToday = repo.isCheckedInToday(h)
            Card { Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(h.title, color = Parchment)
                    TextButton(onClick = { scope.launch { repo.deleteHabit(h.id) } }) { Text("remove", color = Muted) }
                }
                Text("🔥 streak ${h.streak} · 🏔️ best ${h.bestStreak} · ${h.cadence}", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(6.dp))
                if (doneToday) {
                    Text("✓ Completed today", color = Verdant)
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
                    }) { Text("Complete today's mission") }
                }
            } }
        }
    }
}
