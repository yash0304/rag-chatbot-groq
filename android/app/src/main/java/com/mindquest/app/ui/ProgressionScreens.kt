package com.mindquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.*
import kotlinx.coroutines.launch


// ---------- Goals / Story Arcs ----------

@Composable
fun GoalsScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val goals by repo.observeGoals().collectAsState(emptyList())
    val milestones by repo.observeAllMilestones().collectAsState(emptyList())
    val byGoal = milestones.groupBy { it.goalId }
    var title by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Story Arcs", style = MaterialTheme.typography.headlineMedium, color = Parchment) }
        item {
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Goal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    lines, { lines = it },
                    label = { Text("Milestones (one per line)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                )
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val t = title; val ms = lines.split("\n"); title = ""; lines = ""
                            scope.launch { repo.createGoal(t, null, ms) }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Begin the arc") }
            } }
        }
        if (goals.isEmpty()) item { Text("No arcs yet. Every legend starts with a first chapter.", color = Muted) }
        items(goals) { g ->
            val ms = byGoal[g.id].orEmpty().sortedBy { it.seq }
            val done = ms.count { it.completed }
            Card { Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(g.title, color = Parchment, fontWeight = FontWeight.Bold)
                    Text(
                        if (g.status == "completed") "✓ complete" else "$done/${ms.size}",
                        color = if (g.status == "completed") Verdant else Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ms.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (m.completed) {
                            Text("✓ ", color = Verdant)
                            Text(m.title, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            TextButton(onClick = {
                                scope.launch {
                                    val r = repo.completeMilestone(m.id)
                                    if (!r.alreadyDone) notify(buildString {
                                        append("Chapter done! +${r.xpAwarded} XP")
                                        if (r.goalCompleted) append(" · 📖 Arc complete!")
                                        if (r.levelUp) append(" · ⭐ Level up!")
                                        r.achievementsUnlocked.forEach { append(" · ${it.icon} ${it.name}") }
                                    })
                                }
                            }, contentPadding = PaddingValues(0.dp)) { Text("○ ${m.title}") }
                        }
                    }
                }
            } }
        }
    }
}

// ---------- Skills ----------

private val TREE_META = linkedMapOf(
    "scholar" to ("📚" to "Scholar"),
    "explorer" to ("🧭" to "Explorer"),
    "strategist" to ("♟️" to "Strategist"),
    "forger" to ("🔥" to "Forger"),
)

@Composable
fun SkillsScreen(repo: MindQuestRepository, skillPoints: Int, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val skills by repo.observeSkills().collectAsState(emptyList())
    val ownedCodes = skills.filter { it.unlockedAt != null }.map { it.code }.toSet()

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Skill Trees", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                AssistChip(onClick = {}, label = { Text("✨ $skillPoints points") })
            }
        }
        TREE_META.forEach { (tree, meta) ->
            item { Text("${meta.first} ${meta.second}", style = MaterialTheme.typography.titleMedium, color = Rune) }
            items(skills.filter { it.tree == tree }) { s ->
                val owned = s.unlockedAt != null
                val available = !owned && (s.parentCode == null || s.parentCode in ownedCodes) && skillPoints >= s.cost
                Card(colors = CardDefaults.cardColors(containerColor = if (owned) Rune.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tier ${s.tier} — ${s.name}", color = Parchment, fontWeight = FontWeight.Bold)
                            Text(s.description, style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        if (owned) Text("owned", color = Rune, style = MaterialTheme.typography.labelSmall)
                        else TextButton(enabled = available, onClick = {
                            scope.launch { notify(repo.unlockSkill(s.code).message) }
                        }) { Text("${s.cost} pt") }
                    }
                }
            }
        }
    }
}

// ---------- Achievements + Collectibles ----------

private val RARITY_COLOR = mapOf(
    "common" to RarityCommon, "rare" to RarityRare,
    "epic" to RarityEpic, "legendary" to Rune,
)

@Composable
fun AchievementsScreen(repo: MindQuestRepository) {
    val achievements by repo.observeAchievements().collectAsState(emptyList())
    val collectibles by repo.observeOwnedCollectibles().collectAsState(emptyList())
    val unlocked = achievements.count { it.unlockedAt != null }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Hall of Deeds", style = MaterialTheme.typography.headlineMedium, color = Parchment)
            Text("$unlocked/${achievements.size} earned", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        items(achievements.filter { !it.secret || it.unlockedAt != null }) { a ->
            val on = a.unlockedAt != null
            Card { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.icon, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = if (on) Parchment else Muted, fontWeight = FontWeight.Bold)
                    Text(a.description, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Text(if (on) "+${a.xpBonus}" else "🔒", color = if (on) Rune else Muted)
            } }
        }
        item { Text("Collectibles", style = MaterialTheme.typography.titleMedium, color = Rune) }
        if (collectibles.isEmpty()) item { Text("No relics yet — rare deeds earn rare things.", color = Muted) }
        items(collectibles) { c ->
            Card { Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.name, color = RARITY_COLOR[c.rarity] ?: Parchment, fontWeight = FontWeight.Bold)
                    Text(c.rarity, style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Text("“${c.lore}”", style = MaterialTheme.typography.bodySmall, color = Muted)
            } }
        }
    }
}

// ---------- Analytics ----------

@Composable
fun AnalyticsScreen(repo: MindQuestRepository, refreshKey: Long) {
    val summary by produceState<SummaryStats?>(null, refreshKey) { value = repo.summary() }
    val series by produceState<List<DayXp>>(emptyList(), refreshKey) { value = repo.xpDaily(30) }
    val heat by produceState<List<DayCount>>(emptyList(), refreshKey) { value = repo.activityHeatmap(12) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Chronicles", style = MaterialTheme.typography.headlineMedium, color = Parchment) }
        summary?.let { s ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniTile("Total XP", "${s.xpTotal}", Modifier.weight(1f))
                    MiniTile("Quests", "${s.questsCompleted}", Modifier.weight(1f))
                    MiniTile("Missions", "${s.checkins}", Modifier.weight(1f))
                }
            }
        }
        item {
            Card { Column(Modifier.padding(14.dp)) {
                Text("XP — last 30 days", style = MaterialTheme.typography.titleSmall, color = Rune)
                Spacer(Modifier.height(8.dp))
                val maxXp = (series.maxOfOrNull { it.xp } ?: 1).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom) {
                    series.forEach { d ->
                        Box(
                            Modifier.weight(1f).padding(horizontal = 1.dp)
                                .fillMaxHeight((d.xp.toFloat() / maxXp).coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(2.dp)).background(Rune),
                        )
                    }
                }
            } }
        }
        item {
            Card { Column(Modifier.padding(14.dp)) {
                Text("Activity — last 12 weeks", style = MaterialTheme.typography.titleSmall, color = Rune)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    heat.chunked(7).forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            week.forEach { day ->
                                val c = when {
                                    day.count == 0 -> Trough
                                    day.count < 3 -> Rune.copy(alpha = 0.35f)
                                    day.count < 6 -> Rune.copy(alpha = 0.65f)
                                    else -> Rune
                                }
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(c))
                            }
                        }
                    }
                }
            } }
        }
    }
}

// ---------- Personal Bests ----------

@Composable
fun PersonalBestsScreen(repo: MindQuestRepository, refreshKey: Long) {
    val pb by produceState<PersonalBests?>(null, refreshKey) { value = repo.personalBests() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Personal Bests", style = MaterialTheme.typography.headlineMedium, color = Parchment)
        Text("Your records — no one to beat but yesterday's you.", color = Muted, style = MaterialTheme.typography.bodySmall)
        pb?.let { b ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniTile("Highest level", "${b.highestLevel}", Modifier.weight(1f))
                MiniTile("Total XP", "${b.totalXp}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniTile("Best streak", "${b.bestStreakEver}", Modifier.weight(1f))
                MiniTile("Most XP / day", "${b.mostXpInADay}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniTile("Quests won", "${b.questsCompleted}", Modifier.weight(1f))
                MiniTile("Missions kept", "${b.missionsCompleted}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = Parchment)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}
