package com.mindquest.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.AttachmentEntity
import com.mindquest.app.data.FolderEntity
import com.mindquest.app.data.KIND_THOUGHT
import com.mindquest.app.data.isThought
import com.mindquest.app.data.goalForCheckpoint
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.NoteEntity
import com.mindquest.app.domain.Cadences
import com.mindquest.app.domain.Categories
import com.mindquest.app.domain.DateParse
import com.mindquest.app.domain.FolderMatch
import com.mindquest.app.domain.GoalParse
import com.mindquest.app.domain.HabitParse
import com.mindquest.app.domain.LocalAi
import com.mindquest.app.domain.Reminders
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

/**
 * The Inbox, in three parts: the Checklist (things to do, once or again and again), Goals
 * (daily goals with a streak, and long-term targets), and Thoughts (things to remember,
 * kept in folders). Each part has its own box to type into, so a line lands where it was
 * typed.
 */
@Composable
fun InboxScreen(
    repo: MindQuestRepository,
    notify: (String) -> Unit,
    segment: Int,
    onSegment: (Int) -> Unit,
) {
    val notes by repo.observeNotes().collectAsState(emptyList())
    val open = notes.count { !it.done && !it.isThought }
    val thoughts = notes.count { it.isThought }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Inbox", style = MaterialTheme.typography.headlineMedium, color = Parchment,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        TabRow(
            selectedTabIndex = segment,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Rune,
        ) {
            Tab(selected = segment == 0, onClick = { onSegment(0) }, text = { Text("✅ Checklist ($open)", maxLines = 1) })
            Tab(selected = segment == 1, onClick = { onSegment(1) }, text = { Text("🎯 Goals", maxLines = 1) })
            Tab(selected = segment == 2, onClick = { onSegment(2) }, text = { Text("💭 Thoughts ($thoughts)", maxLines = 1) })
        }
        Box(Modifier.weight(1f)) {
            when (segment) {
                0 -> ChecklistPane(repo, notify)
                1 -> GoalsScreen(repo, notify, header = { DailyGoalsSection(repo, notify) })
                else -> ThoughtsPane(repo, notify)
            }
        }
    }
}

/** Which slice of the Checklist is showing. */
private enum class ChecklistFilter(val label: String) {
    All("All"), Repeating("🔁 Repeating"), Once("Once"), Done("✓ Done"),
}

/** Cadences in the order a person thinks of them, for the Repeating view's headings. */
private val CADENCE_ORDER = listOf("daily", "weekdays", "weekly", "halfmonthly", "monthly", "quarterly", "halfyearly", "yearly")

/**
 * Where an open item sits by its date: late first, then today, and on out to "anytime" for
 * the ones with no date at all.
 */
private fun whenGroup(n: NoteEntity, now: Long): Int {
    val at = n.remindAt ?: return 5
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now()
    fun startOf(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    return when {
        at < now -> 0
        at < startOf(today.plusDays(1)) -> 1
        at < startOf(today.plusDays(2)) -> 2
        at < startOf(today.plusDays(7)) -> 3
        else -> 4
    }
}

private val WHEN_LABELS = listOf("⚠️ Overdue", "📅 Today", "Tomorrow", "This week", "Later", "📝 Anytime")

@Composable
private fun ChecklistPane(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val allNotes by repo.observeNotes().collectAsState(emptyList())
    val notePhotos by repo.observeAttachments("note").collectAsState(emptyList())
    val photosByNote = notePhotos.groupBy { it.ownerId }
    val allFolders by repo.observeFolders().collectAsState(emptyList())
    val customFolders = allFolders.filter { !it.isThought }
    val customCounts by repo.observeFolderCounts().collectAsState(emptyMap())
    var customFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(ChecklistFilter.All) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderEntity?>(null) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    val openFolder = customFolders.firstOrNull { it.id == customFolder }
    val inView = allNotes.filter { !it.isThought && (customFolder == null || it.folderId == customFolder) }
    val openItems = inView.filter { !it.done }
    val doneItems = inView.filter { it.done }.sortedByDescending { it.completedAt ?: it.updatedAt ?: it.createdAt }
    val notes = when (filter) {
        ChecklistFilter.All -> openItems
        ChecklistFilter.Repeating -> openItems.filter { it.repeat != null }
        ChecklistFilter.Once -> openItems.filter { it.repeat == null }
        ChecklistFilter.Done -> doneItems
    }
    val now = System.currentTimeMillis()
    // Headed sections: by date for most views, by how often for the Repeating one.
    val sections: List<Pair<String, List<NoteEntity>>> = when (filter) {
        ChecklistFilter.Done -> listOf("✓ Done" to notes)
        ChecklistFilter.Repeating -> notes.groupBy { it.repeat!! }
            .toList()
            .sortedBy { (c, _) -> CADENCE_ORDER.indexOf(c).let { if (it < 0) 99 else it } }
            .map { (c, list) -> "🔁 ${Cadences.of(c).label.replaceFirstChar { it.uppercase() }}" to list.sortedBy { it.remindAt } }
        else -> notes.groupBy { whenGroup(it, now) }
            .toList()
            .sortedBy { it.first }
            .map { (g, list) ->
                WHEN_LABELS[g] to list.sortedWith(
                    compareByDescending<NoteEntity> { it.starred }.thenBy { it.remindAt ?: Long.MAX_VALUE }.thenBy { it.createdAt },
                )
            }
    }
    var asThought by remember { mutableStateOf(false) }
    var snapping by remember { mutableStateOf(false) }
    if (snapping) SnapDialog(repo, startAsThought = false, notify = notify, onDismiss = { snapping = false })
    var input by remember { mutableStateOf("") }
    var pendingRemind by remember { mutableStateOf<Long?>(null) }
    // The category is shown before the note is sent, not applied silently afterwards. It
    // tracks what you type until you touch it, and once touched it stops second-guessing you.
    var pendingCategory by remember { mutableStateOf("general") }
    var categoryChosen by remember { mutableStateOf(false) }

    // What the typed line looks like, worked out as you type and shown above the box before
    // anything is saved: a goal (→ Goals), one of your own folders, and a date in the words.
    var keepAsNote by remember { mutableStateOf(false) }
    var skipFolder by remember { mutableStateOf(false) }
    var skipDate by remember { mutableStateOf(false) }
    val goalGuess = remember(input, customFolder) {
        if (customFolder != null) null else GoalParse.detect(input)
    }
    // "90 kg by 1st October" with an 80 kg goal running is a checkpoint on the way to it.
    val allGoals by repo.observeGoals().collectAsState(emptyList())
    val checkpointGuess = remember(input, customFolder, allGoals) {
        if (customFolder != null) null
        else GoalParse.checkpoint(input)?.let { cp -> goalForCheckpoint(cp, allGoals)?.let { cp to it } }
    }
    val asCheckpoint = checkpointGuess != null && !keepAsNote
    val asGoal = !asCheckpoint && goalGuess != null && !keepAsNote
    val habitGuess = remember(input, customFolder) {
        if (customFolder != null) null else HabitParse.detect(input)
    }
    val asHabit = !asCheckpoint && !asGoal && habitGuess != null && !keepAsNote
    val folderGuess = remember(input, customFolder, customFolders) {
        if (customFolder != null) null
        else FolderMatch.best(input, customFolders.map { it.name })?.let { customFolders[it] }
    }
    val dateGuess = remember(input) { DateParse.parse(input) }
    LaunchedEffect(input.isBlank()) {
        // A fresh line starts with fresh guesses.
        if (input.isBlank()) { keepAsNote = false; skipFolder = false; skipDate = false; asThought = false }
    }

    LaunchedEffect(input, categoryChosen) {
        if (!categoryChosen) pendingCategory = Categories.classify(input)
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
            val smart = customFolder == null && LocalAi.ready(context) && LocalAi.smartCapture(context)
            if (smart) {
                notify("✨ Sorting it…")
                repo.captureInBackground(spoken, smart = true) { notify(it.describe { t -> timeFmt.format(Date(t)) }) }
                return@launch
            }
            val r = repo.captureNote(spoken, folderId = customFolder)
            notify(r.describe { timeFmt.format(Date(it)) })
        }
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onDismiss = { newFolderOpen = false },
            onCreate = { name ->
                newFolderOpen = false
                scope.launch {
                    val id = repo.createFolder(name)
                    customFolder = id
                    notify("List “$name” created — everything you add now goes inside it.")
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp)) {
        Text(
            if (openFolder != null) "${openFolder.name} — anything you add now lands in this list."
            else "Errands and to-dos: “milk by 28th September”, “haircut every month on 15”, " +
                "“health checkup every 6 months July 5 then January 5”. ★ what matters.",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
        Spacer(Modifier.height(6.dp))
        CustomFolderRow(
            folders = customFolders,
            counts = customCounts,
            selected = customFolder,
            onSelect = { customFolder = it },
            onCreate = { newFolderOpen = true },
            onRename = { renaming = it },
            onDelete = { target ->
                customFolder = null
                scope.launch {
                    repo.deleteFolder(target.id)
                    notify("List “${target.name}” removed. Its items are still in the Checklist.")
                }
            },
            onMove = { target ->
                customFolder = null
                scope.launch {
                    repo.setFolderKind(target.id, KIND_THOUGHT)
                    notify("🗂 ${target.name} → 💭 Thoughts, with everything in it.")
                }
            },
            moveLabel = "Move to 💭 Thoughts",
            newLabel = "+ New list",
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChecklistFilter.entries.forEach { f ->
                val n = when (f) {
                    ChecklistFilter.All -> openItems.size
                    ChecklistFilter.Repeating -> openItems.count { it.repeat != null }
                    ChecklistFilter.Once -> openItems.count { it.repeat == null }
                    ChecklistFilter.Done -> doneItems.size
                }
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text("${f.label} ($n)", style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (notes.isEmpty()) {
                item {
                    Text(
                        when {
                            filter == ChecklistFilter.Done -> "Nothing ticked off yet."
                            filter == ChecklistFilter.Repeating ->
                                "Nothing repeating yet. Try “pay rent on the 5th every month”."
                            openFolder != null ->
                                "“${openFolder.name}” is empty. Add the first item below, by thumb or by mic."
                            else -> "All clear. Type below — “call the plumber tomorrow”, “milk, eggs, rice”…"
                        },
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            sections.forEach { (heading, list) ->
                item(key = "h-$heading") {
                    Text(
                        "$heading (${list.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (heading.startsWith("⚠️")) Ember else Rune,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(list, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        photos = photosByNote[note.id].orEmpty(),
                        onAddPhoto = { path -> scope.launch { repo.addAttachment("note", note.id, path) } },
                        onRemovePhoto = { scope.launch { repo.deleteAttachment(it.id) } },
                        onToggleDone = {
                            scope.launch {
                                val xp = repo.xpFor(note)
                                val firstTime = !note.done && note.completedAt == null
                                // A repeating note rolls forward instead of ticking; say where to.
                                val next = repo.setNoteDone(note.id, !note.done)
                                when {
                                    next != null -> notify("✓ +$xp XP — next one ${timeFmt.format(Date(next))}.")
                                    firstTime -> notify("✓ Done · +$xp XP")
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
                        onStar = {
                            scope.launch {
                                repo.setNoteStarred(note.id, !note.starred)
                                if (!note.starred) notify("★ Starred — worth ${MindQuestRepository.STARRED_TASK_XP} XP when done.")
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
                        onToThought = {
                            scope.launch {
                                repo.setNoteKind(note.id, KIND_THOUGHT)
                                notify("💭 Moved to Thoughts.")
                            }
                        },
                    )
                }
            }
        }

        pendingRemind?.let {
            Text(
                "⏰ Reminder on send: ${timeFmt.format(Date(it))}  (tap the clock to clear)",
                style = MaterialTheme.typography.labelSmall, color = Rune,
            )
        }

        // Where this line will go, shown before it goes there. Each guess is one tap to undo.
        if (input.isNotBlank()) {
            if (asCheckpoint && checkpointGuess != null) {
                val (cp, main) = checkpointGuess
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🏁 Checkpoint ${GoalParse.format(cp.value, cp.unit)} by ${Cadences.formatDay(cp.date)} → ${main.title}",
                        style = MaterialTheme.typography.labelSmall, color = Rune,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { keepAsNote = true }) {
                        Text("keep as note", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (asHabit && habitGuess != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🔁 Habit: ${habitGuess.title} · ${Cadences.of(habitGuess.cadence).label}" +
                            (habitGuess.minuteOfDay?.let { " · nudge ${Reminders.formatTimeOfDay(it)}" } ?: "") +
                            " · with a streak",
                        style = MaterialTheme.typography.labelSmall, color = Rune,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { keepAsNote = true }) {
                        Text("keep as reminder", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (asGoal && goalGuess != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🎯 Goal: ${goalGuess.title} by ${Cadences.formatTarget(goalGuess.deadline.toString())} → Goals",
                        style = MaterialTheme.typography.labelSmall, color = Rune,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { keepAsNote = true }) {
                        Text("keep as note", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (categoryChosen) "Filing under" else "Looks like",
                        style = MaterialTheme.typography.labelSmall, color = Muted,
                    )
                    Spacer(Modifier.width(6.dp))
                    CategoryChip(pendingCategory) { pendingCategory = it; categoryChosen = true }
                    // Something to remember rather than do — an address, a number — belongs
                    // in Thoughts. Offered only when there's no date that would make it a task.
                    if (dateGuess.dueAt == null && pendingRemind == null && customFolder == null) {
                        FilterChip(
                            selected = asThought,
                            onClick = { asThought = !asThought },
                            label = { Text("💭 Thought", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    // Let the on-phone model decide where it goes, and set any date it reads.
                    if (customFolder == null && LocalAi.ready(context)) {
                        AssistChip(
                            onClick = {
                                val line = input.trim()
                                input = ""
                                notify("✨ Sorting it…")
                                repo.captureInBackground(line, smart = true) { r ->
                                    notify(r.describe { timeFmt.format(Date(it)) })
                                }
                            },
                            label = { Text("✨ Sort it", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    if (goalGuess != null) {
                        TextButton(onClick = { keepAsNote = false }) {
                            Text("🎯 make it a goal", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (folderGuess != null && !skipFolder && !asThought) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🗂 Into “${folderGuess.name}”",
                            style = MaterialTheme.typography.labelSmall, color = Rune,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { skipFolder = true }) {
                            Text("✕", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
                val found = dateGuess.dueAt
                if (found != null && pendingRemind == null && !skipDate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append("⏰ ${timeFmt.format(Date(found))}")
                                dateGuess.repeat?.let { append(" · 🔁 ${Cadences.of(it).label}") }
                                append(" — saved as “${dateGuess.text}”")
                            },
                            style = MaterialTheme.typography.labelSmall, color = Rune,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { skipDate = true }) {
                            Text("✕", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(openFolder?.let { "Add to ${it.name}…" } ?: "Add a to-do…")
                },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            MicButton { spoken -> capture(spoken) }
            TextButton(onClick = { snapping = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("📷") }
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
                    val raw = input.trim()
                    val goal = if (asGoal) goalGuess else null
                    val checkpoint = if (asCheckpoint) checkpointGuess else null
                    val habit = if (asHabit) habitGuess else null
                    // A date typed into the line counts unless it was dismissed or a time was
                    // picked with the clock, which always wins.
                    val typedDate = dateGuess.takeIf { it.dueAt != null && pendingRemind == null && !skipDate }
                    val at = pendingRemind ?: typedDate?.dueAt
                    val text = typedDate?.text?.ifBlank { raw } ?: raw
                    val repeat = typedDate?.repeat
                    val cat = pendingCategory
                    val chosen = categoryChosen
                    val intoFolder = customFolder ?: folderGuess?.takeIf { !skipFolder }?.id
                    val intoFolderName = if (customFolder == null) folderGuess?.takeIf { !skipFolder }?.name else null
                    val thought = asThought && !asGoal && !asHabit && !asCheckpoint && at == null
                    input = ""; pendingRemind = null; categoryChosen = false
                    if (at != null) ensureNotifPermission()
                    scope.launch {
                        if (checkpoint != null) {
                            val (cp, main) = checkpoint
                            val met = repo.addCheckpoint(main.id, cp.value, cp.date)
                            notify(
                                "🏁 Checkpoint ${GoalParse.format(cp.value, cp.unit)} by ${Cadences.formatDay(cp.date)} → ${main.title}" +
                                    if (met) " · already there ✓" else "",
                            )
                            return@launch
                        }
                        if (habit != null) {
                            repo.createHabitFrom(habit)
                            notify("🔁 ${habit.title} — a ${Cadences.of(habit.cadence).label} daily goal → 🎯 Goals.")
                            return@launch
                        }
                        if (goal != null) {
                            repo.createTargetGoal(goal, narrative = raw)
                            ensureNotifPermission()
                            notify("🎯 ${goal.title} → Goals · check-in on the 1st of each month")
                            return@launch
                        }
                        if (thought) {
                            repo.addNote(raw, category = cat, categoryChosen = chosen, kind = KIND_THOUGHT)
                            notify("💭 Saved to Thoughts.")
                            return@launch
                        }
                        val id = repo.addNote(text, at, cat, chosen, repeat)
                        intoFolder?.let { repo.setNoteFolder(id, it) }
                        // Say where it went when that isn't the list on screen.
                        if (intoFolderName != null || at != null) {
                            notify(
                                buildString {
                                    append("Added")
                                    intoFolderName?.let { append(" → 🗂 $it") }
                                    at?.let { append(" · ⏰ ${timeFmt.format(Date(it))}") }
                                },
                            )
                        }
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
    onStar: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onCategory: (String) -> Unit,
    onToThought: () -> Unit,
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
                // A star marks a bigger task — worth 50 XP instead of 10 when ticked off. It is
                // what a hard quest used to be, without a second list to keep it on.
                TextButton(onClick = onStar, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (note.starred) "★" else "☆", color = if (note.starred) Rune else Muted)
                }
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
                if (note.docId == null) {
                    TextButton(onClick = onArchive) { Text("→ Archive", style = MaterialTheme.typography.labelSmall) }
                }
                TextButton(onClick = onToThought) { Text("💭", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = onDelete) {
                    Text("Delete", style = MaterialTheme.typography.labelSmall, color = Ember)
                }
            }
        }
    }
}
