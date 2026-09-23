package com.mindquest.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.mindquest.app.data.Backup
import com.mindquest.app.data.DbEncryption
import com.mindquest.app.data.GlobalKind
import com.mindquest.app.data.MindQuestDatabase
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.ui.*
import kotlinx.coroutines.launch

// FragmentActivity (not ComponentActivity) because androidx BiometricPrompt attaches itself
// to the fragment manager. FragmentActivity extends ComponentActivity, so setContent is unaffected.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = MindQuestLightColors) {
                MindQuestApp()
            }
        }
    }
}

private enum class AppState { Loading, Onboarding, Locked, Ready }

private enum class Dest(val label: String, val icon: String) {
    Dashboard("Dashboard", "🏰"),
    Inbox("Inbox", "📥"),
    Quests("Quests", "⚔️"),
    Habits("Missions", "🔥"),
    Goals("Story Arcs", "📖"),
    Archives("Archives", "📜"),
    Narrator("Narrator", "🔮"),
    WorldMap("World Map", "🗺️"),
    Skills("Skills", "✨"),
    Achievements("Hall of Deeds", "🏆"),
    Analytics("Chronicles", "📊"),
    Review("Weekly Review", "🕯️"),
    PersonalBests("Personal Bests", "🏅"),
    Data("Backup", "💾"),
    Settings("Settings", "⚙️"),
}

@Composable
fun MindQuestApp() {
    val context = LocalContext.current
    // Opening the data can fail in exactly one serious way: an encrypted file whose key the
    // phone no longer has. Catch it here and explain, rather than crash on every launch.
    val opened = remember { runCatching { MindQuestRepository(context.applicationContext) } }
    val repo = opened.getOrNull()
    if (repo == null) {
        CannotOpenScreen(opened.exceptionOrNull())
        return
    }
    var state by remember { mutableStateOf(AppState.Loading) }
    var startupError by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(Unit) {
        try {
            startUp(repo, context.applicationContext)
        } catch (e: Exception) {
            startupError = e
            return@LaunchedEffect
        }
        state = when {
            !repo.hasProfile() -> AppState.Onboarding
            repo.settings.hasPin() -> AppState.Locked
            else -> AppState.Ready
        }
        // After the app is on screen, not before: embedding every note the first time after
        // an update can take a few seconds, and nobody should wait for it to open the app.
        launch { runCatching { repo.indexNotes() } }
    }

    startupError?.let {
        CannotOpenScreen(it)
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AppState.Onboarding -> OnboardingScreen(repo) { state = AppState.Ready }
            AppState.Locked -> LockScreen(repo) { state = AppState.Ready }
            AppState.Ready -> HomeShell(repo)
        }
    }
}

/**
 * Shown only if the data can't be opened. Nothing here deletes anything: the one action
 * renames the unreadable file and keeps it, so the app can start empty and a backup can be
 * restored into it.
 */
@Composable
private fun CannotOpenScreen(error: Throwable?) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Your data couldn't be opened", style = MaterialTheme.typography.headlineSmall, color = Ember)
        Spacer(Modifier.height(8.dp))
        Text(error?.message ?: "Unknown error", style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(16.dp))
        Text(
            "Nothing has been deleted. First try opening the app again. If this keeps happening, " +
                "set the unreadable file aside — it is kept, renamed, not erased — and start " +
                "fresh, then restore your latest backup from the Backup screen. Weekly backups " +
                "are in the folder you chose for them.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { restartApp(context) }, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Set it aside & start fresh")
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Start fresh?") },
            text = { Text("The app will open empty. Go to Backup → Restore to bring your data back from a backup.") },
            confirmButton = {
                TextButton(onClick = {
                    DbEncryption.setAside(context, context.getDatabasePath(MindQuestDatabase.NAME))
                    restartApp(context)
                }) { Text("Set aside & restart") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Everything that runs once at launch, before the first screen is shown. */
private suspend fun startUp(repo: MindQuestRepository, context: Context) {
    repo.seedIfEmpty()
    // Give a category to anything captured before categories existed. Cheap keyword
    // work, and it never overwrites a category the user has already set.
    repo.backfillCategories()
    // WorkManager keeps pending work across reboots itself; this repairs the case where
    // its records were cleared, and is a no-op when everything is already scheduled.
    repo.rearmHabitReminders()
    repo.rearmNoteReminders()
    Backup.ensureScheduled(context)
}

@Composable
private fun OnboardingScreen(repo: MindQuestRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("MindQuest", style = MaterialTheme.typography.headlineLarge, color = Rune)
        Text("Your knowledge, made legend. Everything lives on this device.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name your hero") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { busy = true; scope.launch { repo.createProfile(name); onDone() } },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Forging your legend…" else "Begin your saga") }
    }
}

@Composable
private fun HomeShell(repo: MindQuestRepository) {
    // A real back stack, so Back returns to the previous screen instead of quitting.
    // Dashboard is the floor: pressing Back there falls through to the system, which is
    // the one place leaving the app is the expected outcome.
    val backStack = remember { mutableStateListOf(Dest.Dashboard) }
    val dest = backStack.last()
    val profile by repo.observeProfile().collectAsState(initial = null)
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchOpen by remember { mutableStateOf(false) }
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    fun go(target: Dest) {
        // Revisiting a screen moves it to the top rather than stacking duplicates, so Back
        // never walks through the same screen twice.
        backStack.remove(target)
        backStack.add(target)
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }

    if (searchOpen) {
        GlobalSearchSheet(
            repo = repo,
            onDismiss = { searchOpen = false },
            onOpen = { kind ->
                go(
                    when (kind) {
                        GlobalKind.Note -> Dest.Inbox
                        GlobalKind.Document -> Dest.Archives
                        GlobalKind.Quest -> Dest.Quests
                        GlobalKind.Habit -> Dest.Habits
                        GlobalKind.Goal -> Dest.Goals
                        GlobalKind.Folder -> Dest.Inbox
                    },
                )
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.statusBarsPadding().padding(16.dp)) {
                    Text("MindQuest", style = MaterialTheme.typography.titleLarge, color = Rune)
                    profile?.let {
                        Text(it.heroName, color = Parchment)
                        Spacer(Modifier.height(8.dp))
                        XpBar(it)
                    }
                }
                HorizontalDivider()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Dest.entries.forEach { d ->
                        NavigationDrawerItem(
                            icon = { Text(d.icon) },
                            label = { Text(d.label) },
                            selected = dest == d,
                            onClick = { go(d); scope.launch { drawer.close() } },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { drawer.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Rune)
                    }
                    Text(dest.label, style = MaterialTheme.typography.titleLarge, color = Rune, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search everything", tint = Rune)
                    }
                    profile?.let { Text("Lv ${it.level} · ${it.skillPoints}✨", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.width(8.dp))
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val p = profile
                when (dest) {
                    Dest.Dashboard -> if (p != null) DashboardScreen(repo, p)
                    Dest.Inbox -> InboxScreen(repo, notify)
                    Dest.Quests -> QuestsScreen(repo, notify)
                    Dest.Habits -> HabitsScreen(repo, notify)
                    Dest.Goals -> GoalsScreen(repo, notify)
                    Dest.Archives -> ArchivesScreen(repo, notify)
                    Dest.Narrator -> NarratorScreen(repo, notify)
                    Dest.WorldMap -> WorldMapScreen(repo, (p?.xp ?: 0L).toInt())
                    Dest.Skills -> SkillsScreen(repo, p?.skillPoints ?: 0, notify)
                    Dest.Achievements -> AchievementsScreen(repo)
                    Dest.Analytics -> AnalyticsScreen(repo, p?.xp ?: 0L)
                    Dest.Review -> WeeklyReviewScreen(repo, notify)
                    Dest.PersonalBests -> PersonalBestsScreen(repo, p?.xp ?: 0L)
                    Dest.Data -> DataScreen(repo, notify)
                    Dest.Settings -> SettingsScreen(repo, notify)
                }
            }
        }
    }
}
