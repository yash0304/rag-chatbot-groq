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
import com.mindquest.app.data.isTarget
import com.mindquest.app.domain.GoalMath
import com.mindquest.app.domain.GoalParse
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
fun DashboardScreen(repo: MindQuestRepository, profile: ProfileEntity, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val notes by repo.observeNotes().collectAsState(emptyList())
    // Recompute aggregates whenever XP changes (i.e., after any award).
    val xp7d by produceState(0L, profile.xp) { value = repo.xpLast7Days() }
    val questsDone by produceState(0, profile.xp) { value = repo.completedQuestCount() }
    val bestStreak by produceState(0, profile.xp) { value = repo.maxStreak() }
    val habits by repo.observeHabits().collectAsState(emptyList())
    val pending = habits.filter { !repo.isCheckedInThisPeriod(it) }
    val goals by repo.observeGoals().collectAsState(emptyList())
    val progress by repo.observeGoalProgress().collectAsState(emptyList())
    val activeGoals = goals.filter { it.isTarget && it.status == "active" }
    val readings = progress.groupBy { it.goalId }
    val checkpoints by repo.observeCheckpoints().collectAsState(emptyList())
    val today = java.time.LocalDate.now()
    // The next open checkpoint per goal — the week's real target.
    val nextCheckpoint = checkpoints
        .filter { it.reachedAt == null && !java.time.LocalDate.parse(it.dueDate).isBefore(today) }
        .groupBy { it.goalId }
        .mapValues { (_, cps) -> cps.minByOrNull { it.dueDate } }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Hail, ${profile.heroName}", style = MaterialTheme.typography.headlineMedium, color = Parchment)
            Spacer(Modifier.height(8.dp))
            Card { Box(Modifier.padding(16.dp)) { XpBar(profile) } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("XP this week", "$xp7d", "⚡", Modifier.weight(1f))
                StatTile("Tasks done", "$questsDone", "✅", Modifier.weight(1f))
                StatTile("Best streak", "$bestStreak", "🔥", Modifier.weight(1f))
            }
        }
        // Goals first: the long game is the easiest thing to lose sight of, so it gets the
        // top of the page every time the app opens.
        if (activeGoals.isNotEmpty()) {
            item { Text("🎯 Goals", style = MaterialTheme.typography.titleMedium, color = Rune) }
            items(activeGoals, key = { it.id }) { g ->
                val unit = g.unit ?: ""
                val deadline = g.deadline?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                val s = deadline?.let { GoalMath.status(g.targetValue, unit, it, readings[g.id].orEmpty().map { r -> r.value }) }
                Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(g.title, color = Parchment, fontWeight = FontWeight.Bold)
                        deadline?.let {
                            Text(
                                Cadences.timeLeft("monthly", it.toString()) ?: "",
                                style = MaterialTheme.typography.labelSmall, color = Muted,
                            )
                        }
                    }
                    s?.fraction?.let { f ->
                        LinearProgressIndicator(
                            progress = { f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Rune,
                        )
                    }
                    val latest = s?.latest
                    Text(
                        when {
                            s == null -> ""
                            latest == null || (readings[g.id].isNullOrEmpty() && !GoalParse.accumulates(unit)) ->
                                "No reading yet — log one in Goals."
                            else -> buildString {
                                append("Now ${GoalParse.format(latest, unit)}")
                                s.perMonth?.let { append(" · ${GoalMath.describePace(it, unit)}") }
                            }
                        },
                        style = MaterialTheme.typography.labelSmall, color = Muted,
                    )
                    nextCheckpoint[g.id]?.let { cp ->
                        Text(
                            "🏁 Next: ${GoalParse.format(cp.value, unit)} by " +
                                Cadences.formatDay(java.time.LocalDate.parse(cp.dueDate)),
                            style = MaterialTheme.typography.labelSmall, color = Rune,
                        )
                    }
                } }
            }
        }
        // Today's list: anything due by tonight or already late, then the starred ones.
        // Ticking here is the same tick as in the Inbox.
        val endOfToday = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val due = notes.filter { !it.done && it.remindAt != null && it.remindAt <= endOfToday }
            .sortedBy { it.remindAt }
        val starred = notes.filter { !it.done && it.starred && it !in due }
        val todayList = (due + starred).take(8)
        item { Text("📥 Today", style = MaterialTheme.typography.titleMedium, color = Rune) }
        if (todayList.isEmpty()) item { Text("Nothing due today. Star an Inbox item to pin it here.", color = Muted) }
        items(todayList, key = { "n-" + it.id }) { n ->
            val late = n.remindAt != null && n.remindAt < System.currentTimeMillis()
            Card { Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    scope.launch {
                        val next = repo.setNoteDone(n.id, true)
                        notify(
                            if (next == null) "✓ Done · +${repo.xpFor(n)} XP"
                            else "✓ +${repo.xpFor(n)} XP — next one ${questStamp.format(Date(next))}",
                        )
                    }
                }) { Text("⬜") }
                Column(Modifier.weight(1f)) {
                    Text((if (n.starred) "★ " else "") + n.text, color = Parchment)
                    n.remindAt?.let {
                        Text(
                            (if (late) "overdue · " else "") + questStamp.format(Date(it)),
                            style = MaterialTheme.typography.labelSmall, color = if (late) Ember else Muted,
                        )
                    }
                }
            } }
        }
        if (pending.isNotEmpty()) {
            item { Text("🔁 Habits to do", style = MaterialTheme.typography.titleMedium, color = Rune) }
            items(pending.take(6), key = { "h-" + it.id }) { h ->
                Card { Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        scope.launch {
                            val r = repo.checkin(h.id)
                            if (!r.alreadyDone) notify("🔥 ${h.title} · +${r.xpAwarded} XP" + if (r.levelUp) " · ⭐ Level up!" else "")
                        }
                    }) { Text("⬜") }
                    Text(h.title, color = Parchment, modifier = Modifier.weight(1f))
                    Text("🔥 ${h.streak}", color = Muted, modifier = Modifier.padding(end = 12.dp))
                } }
            }
        } else if (habits.isNotEmpty()) {
            item { Text("🔁 Every habit done. The campfires stay lit.", color = Verdant) }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}


/**
 * Pick a time of day for a daily nudge, returning minutes past midnight. Uses the framework
 * dialog rather than the Compose time picker, which is still experimental.
 */
internal fun pickTimeOfDay(context: Context, currentMinuteOfDay: Int?, onPicked: (Int) -> Unit) {
    val now = Calendar.getInstance()
    val hour = currentMinuteOfDay?.div(60) ?: now.get(Calendar.HOUR_OF_DAY)
    val minute = currentMinuteOfDay?.rem(60) ?: 0
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(h * 60 + m) },
        hour, minute, false,
    ).show()
}
