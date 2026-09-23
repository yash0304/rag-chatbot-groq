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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.GoalCheckpointEntity
import com.mindquest.app.data.GoalEntity
import com.mindquest.app.data.GoalProgressEntity
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.cadence
import com.mindquest.app.data.isTarget
import com.mindquest.app.domain.Cadences
import com.mindquest.app.domain.GoalMath
import com.mindquest.app.domain.GoalParse
import com.mindquest.app.domain.Reminders
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val readingFmt = SimpleDateFormat("d MMM", Locale.getDefault())

/**
 * Goals: where you want to be, and by when.
 *
 * Type or say it the way you'd tell someone — "80 kg by March 2027", "earn ₹1 crore by March
 * 2027" — and it becomes a goal with a number, a deadline, a progress bar and a check-in on
 * the 1st of each month. Story arcs (a goal broken into steps) still live here too, below.
 */
@Composable
fun GoalsScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val goals by repo.observeGoals().collectAsState(emptyList())
    val milestones by repo.observeAllMilestones().collectAsState(emptyList())
    val progress by repo.observeGoalProgress().collectAsState(emptyList())
    val photos by repo.observeAttachments("goal").collectAsState(emptyList())
    val checkpoints by repo.observeCheckpoints().collectAsState(emptyList())
    val checkpointsByGoal = checkpoints.groupBy { it.goalId }
    val readingsByGoal = progress.groupBy { it.goalId }
    val photosByGoal = photos.groupBy { it.ownerId }
    val msByGoal = milestones.groupBy { it.goalId }

    val targets = goals.filter { it.isTarget }.sortedBy { if (it.status == "active") 0 else 1 }
    val arcs = goals.filter { !it.isTarget }

    var logging by remember { mutableStateOf<GoalEntity?>(null) }
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var deleting by remember { mutableStateOf<GoalEntity?>(null) }

    logging?.let { goal ->
        LogProgressDialog(
            goal = goal,
            onDismiss = { logging = null },
            onLog = { value, note ->
                logging = null
                scope.launch {
                    val r = repo.logGoalProgress(goal.id, value, note)
                    val unit = goal.unit ?: ""
                    notify(
                        if (r.reached) "🎉 ${goal.title} — reached! +${r.xpAwarded} XP"
                        else buildString {
                            append("Logged ${GoalParse.format(value, unit)}")
                            r.checkpointsHit.lastOrNull()?.let {
                                append(" · 🏁 checkpoint ${GoalParse.format(it.value, unit)} hit!")
                            }
                            r.status?.remaining?.let { append(" · ${GoalMath.describeRemaining(it, unit)}") }
                            append(" · +${r.xpAwarded} XP")
                        },
                    )
                }
            },
        )
    }
    editing?.let { goal ->
        EditGoalDialog(
            goal = goal,
            onDismiss = { editing = null },
            onSave = { title, target, unit, deadline, cadence ->
                editing = null
                scope.launch {
                    repo.editTargetGoal(goal.id, title, target, unit, deadline)
                    if (cadence != goal.cadence) repo.setGoalCheckin(goal.id, goal.checkinMinuteOfDay, cadence)
                    notify("Saved.")
                }
            },
        )
    }
    deleting?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${goal.title}”?") },
            text = { Text("Its readings and photos go with it.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { repo.deleteGoal(goal.id); notify("Goal deleted.") }
                }) { Text("Delete", color = Ember) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Goals", style = MaterialTheme.typography.headlineMedium, color = Parchment)
            Text(
                "Where you want to be, and by when. Write it the way you'd say it.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }
        item { NewGoalCard(repo, notify) }

        if (targets.isEmpty()) {
            item {
                Text(
                    "No goals yet. Try “80 kg by March 2027” or “earn ₹1 crore by March 2027”.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(targets, key = { it.id }) { goal ->
            TargetGoalCard(
                goal = goal,
                readings = readingsByGoal[goal.id].orEmpty(),
                checkpoints = checkpointsByGoal[goal.id].orEmpty(),
                photos = photosByGoal[goal.id].orEmpty(),
                repo = repo,
                notify = notify,
                onLog = { logging = goal },
                onEdit = { editing = goal },
                onDelete = { deleting = goal },
            )
        }

        if (arcs.isNotEmpty()) {
            item { Text("📖 Story arcs", style = MaterialTheme.typography.titleMedium, color = Rune) }
            items(arcs, key = { it.id }) { g -> StoryArcCard(g, msByGoal[g.id].orEmpty(), repo, notify) }
        }
    }
}

/**
 * One box for a new goal. As you type it shows what it understood — "🎯 Reach 80 kg by Mar
 * 2027" — so a misreading is visible before anything is saved. If there's no number or date
 * in it, it says what's missing, and offers the old story-arc form (a goal as a list of steps).
 */
@Composable
private fun NewGoalCard(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var asArc by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf("") }
    val parsed = remember(text) { GoalParse.detect(text) }

    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it },
                label = { Text("New goal") },
                placeholder = { Text("80 kg by March 2027") },
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            MicButton { text = it }
        }
        when {
            text.isBlank() -> Unit
            parsed != null && !asArc -> {
                Text(
                    "🎯 ${parsed.title} · by ${Cadences.formatTarget(parsed.deadline.toString())}" +
                        " · check-in on the 1st of each month",
                    style = MaterialTheme.typography.bodySmall, color = Rune,
                )
                parsed.change?.let {
                    Text(
                        "Log your current weight first; the target is set from it.",
                        style = MaterialTheme.typography.labelSmall, color = Muted,
                    )
                }
            }
            !asArc -> Text(
                "Add a number and a date to track it — “80 kg by March 2027”. Or make it a story arc with steps.",
                style = MaterialTheme.typography.labelSmall, color = Muted,
            )
        }
        if (asArc) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    steps, { steps = it },
                    label = { Text("Steps (one per line)") },
                    modifier = Modifier.weight(1f).heightIn(min = 80.dp),
                )
                MicButton { spoken -> steps = if (steps.isBlank()) spoken else "$steps\n$spoken" }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { asArc = !asArc }) {
                Text(if (asArc) "Track a number instead" else "Story arc with steps", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                enabled = text.isNotBlank() && (asArc || parsed != null),
                onClick = {
                    val t = text.trim()
                    val goal = parsed
                    val stepList = steps.split("\n")
                    val arc = asArc
                    text = ""; steps = ""; asArc = false
                    scope.launch {
                        if (arc || goal == null) {
                            repo.createGoal(t, null, stepList)
                            notify("Story arc begun.")
                        } else {
                            repo.createTargetGoal(goal, narrative = t)
                            notify("🎯 ${goal.title} — check-in on the 1st of each month at 09:00.")
                        }
                    }
                },
            ) { Text(if (asArc) "Begin the arc" else "Create goal") }
        }
    } }
}

@Composable
private fun TargetGoalCard(
    goal: GoalEntity,
    readings: List<GoalProgressEntity>,
    checkpoints: List<GoalCheckpointEntity>,
    photos: List<com.mindquest.app.data.AttachmentEntity>,
    repo: MindQuestRepository,
    notify: (String) -> Unit,
    onLog: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unit = goal.unit ?: ""
    val deadline = goal.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val status = deadline?.let { GoalMath.status(goal.targetValue, unit, it, readings.map { r -> r.value }) }
    val done = goal.status == "completed"
    var showAll by remember { mutableStateOf(false) }

    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.title, color = Parchment, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).clickable(onClick = onEdit),
            )
            TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("✎", color = Muted) }
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("✕", color = Muted) }
        }
        goal.narrative?.let { Text("“$it”", style = MaterialTheme.typography.labelSmall, color = Muted) }

        deadline?.let { d ->
            Text(
                buildString {
                    goal.targetValue?.let { append("🎯 ${GoalParse.format(it, unit)} ") }
                    append("by ${Cadences.formatTarget(d.toString())}")
                    if (!done) Cadences.timeLeft("monthly", d.toString())?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall, color = Rune,
            )
        }

        when {
            done -> Text("🎉 Reached", color = Verdant, fontWeight = FontWeight.Bold)
            goal.targetValue == null && goal.changeValue != null -> Text(
                "Log where you are now to set the target (${GoalParse.format(kotlin.math.abs(goal.changeValue), unit)} " +
                    "${if (goal.changeValue < 0) "down" else "up"} from it).",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            status != null -> {
                status.fraction?.let { f ->
                    LinearProgressIndicator(
                        progress = { f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Rune,
                    )
                }
                Text(
                    buildString {
                        status.latest?.let { append("Now ${GoalParse.format(it, unit)}") }
                        status.remaining?.let { append(" · ${GoalMath.describeRemaining(it, unit)}") }
                        status.perMonth?.let { append(" · ${GoalMath.describePace(it, unit)}") }
                        if (readings.isEmpty() && !GoalParse.accumulates(unit)) append("Log your first reading to see progress.")
                    },
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
            }
        }
        EditedStamp(goal.updatedAt)

        if (!done) {
            Button(onClick = onLog) { Text(if (readings.isEmpty()) "Log where I am now" else "Log progress") }
        }

        CheckpointSection(goal, checkpoints, readings, done, repo, notify)

        // Readings, newest first. Three is enough to see the direction; the rest on request.
        val newestFirst = readings.asReversed()
        (if (showAll) newestFirst else newestFirst.take(3)).forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${readingFmt.format(Date(r.createdAt))} · ${GoalParse.format(r.value, unit)}" +
                        (r.note?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { scope.launch { repo.deleteGoalProgress(r.id) } },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text("✕", style = MaterialTheme.typography.labelSmall, color = Muted) }
            }
        }
        if (readings.size > 3) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Show fewer" else "All ${readings.size} readings", style = MaterialTheme.typography.labelSmall)
            }
        }

        // The check-in nudge: on by default, changeable, and off with one tap.
        if (!done) {
            val every = Cadences.of(goal.cadence)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    pickTimeOfDay(context, goal.checkinMinuteOfDay) { minute ->
                        scope.launch {
                            repo.setGoalCheckin(goal.id, minute)
                            notify("Check-in ${checkinWhen(goal.cadence)} at ${Reminders.formatTimeOfDay(minute)}.")
                        }
                    }
                }) {
                    Text(
                        goal.checkinMinuteOfDay?.let { "⏰ Check-in ${checkinWhen(goal.cadence)}, ${Reminders.formatTimeOfDay(it)}" }
                            ?: "⏰ Turn on ${every.label} check-in",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (goal.checkinMinuteOfDay != null) {
                    TextButton(onClick = {
                        scope.launch { repo.setGoalCheckin(goal.id, null); notify("Check-in off.") }
                    }) { Text("off", style = MaterialTheme.typography.labelSmall, color = Muted) }
                }
            }
        }

        PhotoStrip(
            photos = photos,
            onAdd = { path -> scope.launch { repo.addAttachment("goal", goal.id, path) } },
            onRemove = { scope.launch { repo.deleteAttachment(it.id) } },
        )
    } }
}

/** "on the 1st of each month", "every Monday", "every day". */
private fun checkinWhen(cadence: String): String = when (cadence) {
    "daily" -> "every day"
    "weekdays" -> "every weekday"
    "weekly" -> "every Monday"
    "halfmonthly" -> "on the 1st and 16th"
    "monthly" -> "on the 1st of each month"
    "quarterly" -> "on the 1st of each quarter"
    "halfyearly" -> "on 1 Jan and 1 Jul"
    "yearly" -> "on 1 January"
    else -> Cadences.of(cadence).label
}

@Composable
private fun LogProgressDialog(
    goal: GoalEntity,
    onDismiss: () -> Unit,
    onLog: (Double, String?) -> Unit,
) {
    val unit = goal.unit ?: ""
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val value = remember(text) { GoalParse.parseValue(text, unit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(goal.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        text, { text = it },
                        label = { Text(if (unit == "₹") "Amount so far" else "Now ($unit)") },
                        placeholder = { Text(if (unit == "₹") "12 lakh" else "84.5") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    MicButton { text = it }
                }
                value?.let {
                    Text("= ${GoalParse.format(it, unit)}", style = MaterialTheme.typography.labelSmall, color = Rune)
                }
                OutlinedTextField(
                    note, { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = { value?.let { onLog(it, note) } }) { Text("Log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditGoalDialog(
    goal: GoalEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, target: Double?, unit: String, deadline: String?, cadence: String) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(goal.title) }
    var unit by remember { mutableStateOf(goal.unit.orEmpty()) }
    var targetText by remember {
        mutableStateOf(goal.targetValue?.let { GoalParse.format(it, goal.unit ?: "").removeSuffix(" ${goal.unit}") }.orEmpty())
    }
    var deadline by remember { mutableStateOf(goal.deadline) }
    var cadence by remember { mutableStateOf(goal.cadence) }
    val target = remember(targetText, unit) { GoalParse.parseValue(targetText, unit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(title, { title = it }, label = { Text("Goal") }, modifier = Modifier.weight(1f), maxLines = 2)
                    MicButton { title = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        targetText, { targetText = it },
                        label = { Text("Target") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        unit, { unit = it },
                        label = { Text("Unit") },
                        singleLine = true, modifier = Modifier.width(96.dp),
                    )
                }
                target?.let { Text("= ${GoalParse.format(it, unit)}", style = MaterialTheme.typography.labelSmall, color = Rune) }
                TextButton(onClick = { pickDate(context, deadline) { deadline = it } }) {
                    Text(
                        deadline?.let { "📅 by ${Cadences.formatTarget(it)}" } ?: "📅 Set a deadline",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text("Check in", style = MaterialTheme.typography.labelSmall, color = Muted)
                CadenceRow(selected = cadence, onSelect = { cadence = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), target, unit.trim(), deadline, cadence) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The original kind of goal: a list of steps, each worth XP when ticked. */
@Composable
private fun StoryArcCard(
    g: GoalEntity,
    ms: List<com.mindquest.app.data.MilestoneEntity>,
    repo: MindQuestRepository,
    notify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sorted = ms.sortedBy { it.seq }
    val done = sorted.count { it.completed }
    Card { Column(Modifier.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(g.title, color = Parchment, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (g.status == "completed") "✓ complete" else "$done/${sorted.size}",
                color = if (g.status == "completed") Verdant else Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        sorted.forEach { m ->
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


/**
 * Mini goals inside the main one. The next checkpoint leads, because "88 kg by 1 November"
 * is something to act on this week in a way that "80 kg by March 2027" never quite is.
 */
@Composable
private fun CheckpointSection(
    goal: GoalEntity,
    checkpoints: List<GoalCheckpointEntity>,
    readings: List<GoalProgressEntity>,
    done: Boolean,
    repo: MindQuestRepository,
    notify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val unit = goal.unit ?: ""
    var adding by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val states = GoalMath.checkpointStates(
        checkpoints.map { LocalDate.parse(it.dueDate) to (it.reachedAt != null) },
    )
    val rows = checkpoints.zip(states)
    val latest = readings.lastOrNull()?.value

    if (adding) {
        AddCheckpointDialog(
            goal = goal,
            onDismiss = { adding = false },
            onAdd = { value, date ->
                adding = false
                scope.launch {
                    val met = repo.addCheckpoint(goal.id, value, date)
                    notify(
                        "🏁 Checkpoint ${GoalParse.format(value, unit)} by ${Cadences.formatDay(date)}" +
                            if (met) " — you're already there ✓" else " added.",
                    )
                }
            },
        )
    }

    if (checkpoints.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        rows.firstOrNull { it.second == GoalMath.CheckpointState.NEXT }?.let { (cp, _) ->
            Text(
                buildString {
                    append("🏁 Next: ${GoalParse.format(cp.value, unit)} by ${Cadences.formatDay(LocalDate.parse(cp.dueDate))}")
                    latest?.let { append(" · ${GoalMath.describeRemaining(cp.value - it, unit)}") }
                },
                style = MaterialTheme.typography.bodySmall, color = Rune, fontWeight = FontWeight.Bold,
            )
        }
        val hit = states.count { it == GoalMath.CheckpointState.HIT }
        val missed = states.count { it == GoalMath.CheckpointState.MISSED }
        val ahead = states.size - hit - missed
        Text(
            "Checkpoints: ✓ $hit hit · ✗ $missed missed · $ahead ahead",
            style = MaterialTheme.typography.labelSmall, color = Muted,
        )
        // The last two behind you and the next three ahead: enough to see the trend and
        // what's coming, without a weekly plan's twenty-seven rows filling the card.
        val nextIndex = rows.indexOfFirst { it.second == GoalMath.CheckpointState.NEXT }.let { if (it < 0) rows.size else it }
        val visible = if (showAll) rows else rows.subList((nextIndex - 2).coerceAtLeast(0), (nextIndex + 3).coerceAtMost(rows.size))
        visible.forEach { (cp, state) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (mark, color) = when (state) {
                    GoalMath.CheckpointState.HIT -> "✓" to Verdant
                    GoalMath.CheckpointState.MISSED -> "✗" to Ember
                    GoalMath.CheckpointState.NEXT -> "▶" to Rune
                    GoalMath.CheckpointState.LATER -> "○" to Muted
                }
                Text(
                    "$mark ${GoalParse.format(cp.value, unit)} by ${Cadences.formatDay(LocalDate.parse(cp.dueDate))}" +
                        if (cp.planned) "" else "  · yours",
                    style = MaterialTheme.typography.labelSmall, color = color,
                    fontWeight = if (state == GoalMath.CheckpointState.NEXT) FontWeight.Bold else null,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { scope.launch { repo.deleteCheckpoint(cp.id) } },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text("✕", style = MaterialTheme.typography.labelSmall, color = Muted) }
            }
        }
        if (rows.size > visible.size || showAll) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Show fewer" else "All ${rows.size} checkpoints", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (done) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { adding = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text("+ Checkpoint", style = MaterialTheme.typography.labelSmall)
        }
        if (checkpoints.any { it.planned && it.reachedAt == null }) {
            TextButton(onClick = {
                scope.launch { repo.clearPlan(goal.id); notify("Plan cleared. Your own checkpoints are kept.") }
            }) { Text("clear plan", style = MaterialTheme.typography.labelSmall, color = Muted) }
        }
    }
    // One tap lays the whole road out: a checkpoint every week, half-month or month, on a
    // straight line from today's reading to the target.
    Text("Split into steps:", style = MaterialTheme.typography.labelSmall, color = Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("weekly", "halfmonthly", "monthly").forEach { c ->
            AssistChip(
                onClick = {
                    scope.launch {
                        val n = repo.planCheckpoints(goal.id, c)
                        notify(
                            when {
                                n == null -> "Log where you are now first — the steps start from today's number."
                                n == 0 -> "The deadline is too close for ${Cadences.of(c).label} steps."
                                else -> "$n ${Cadences.of(c).label} checkpoints laid out. Check-in is now ${Cadences.of(c).label} too."
                            },
                        )
                    }
                },
                label = { Text(Cadences.of(c).label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/** "90 kg by 1st October" — said or typed, or a number and a date picked by hand. */
@Composable
private fun AddCheckpointDialog(
    goal: GoalEntity,
    onDismiss: () -> Unit,
    onAdd: (Double, LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val unit = goal.unit ?: ""
    var text by remember { mutableStateOf("") }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val value = remember(text) { GoalParse.parseValue(text, unit) }
    val readDate = remember(text) { GoalParse.dateOf(text) }
    val date = pickedDate ?: readDate
    val deadline = goal.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val problem = when {
        date == null -> null
        date.isBefore(LocalDate.now()) -> "That date has passed."
        deadline != null && !date.isBefore(deadline) -> "That's on or after the goal's own deadline."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Checkpoint for ${goal.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        text, { text = it },
                        label = { Text("Number and date") },
                        placeholder = { Text(if (unit == "₹") "10 lakh by December" else "90 kg by 1st October") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    MicButton { text = it }
                }
                TextButton(onClick = { pickDate(context, date?.toString()) { pickedDate = LocalDate.parse(it) } }) {
                    Text(
                        date?.let { "📅 by ${Cadences.formatDay(it)} ${it.year}" } ?: "📅 Pick a date",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (value != null && date != null) {
                    Text(
                        "= ${GoalParse.format(value, unit)} by ${Cadences.formatDay(date)}",
                        style = MaterialTheme.typography.labelSmall, color = Rune,
                    )
                }
                problem?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Ember) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null && date != null && problem == null,
                onClick = { if (value != null && date != null) onAdd(value, date) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
