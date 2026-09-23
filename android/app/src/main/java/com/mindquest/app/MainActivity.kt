package com.mindquest.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** The five bottom tabs. Everything else is reached from one of them. */
private enum class HomeTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Inbox("Inbox", Icons.Filled.Inbox),
    Goals("Goals", Icons.Filled.Flag),
    Archives("Archives", Icons.AutoMirrored.Filled.MenuBook),
    Progress("Progress", Icons.Filled.Insights),
}

/** Pages opened over a tab, from the gear. */
private enum class Page { Settings, Backup }

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
    repo.rearmGoalCheckins()
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
    var tab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    // Settings → Backup is the only nesting; Back walks it, then returns to Home, then leaves.
    val pages = remember { mutableStateListOf<Page>() }
    var archivesTab by rememberSaveable { mutableIntStateOf(0) }
    val profile by repo.observeProfile().collectAsState(initial = null)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchOpen by remember { mutableStateOf(false) }
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val page = pages.lastOrNull()

    fun open(target: HomeTab) {
        pages.clear()
        tab = target
    }

    BackHandler(enabled = pages.isNotEmpty()) { pages.removeAt(pages.lastIndex) }
    BackHandler(enabled = pages.isEmpty() && tab != HomeTab.Home) { tab = HomeTab.Home }

    if (searchOpen) {
        GlobalSearchSheet(
            repo = repo,
            onDismiss = { searchOpen = false },
            onOpen = { kind ->
                when (kind) {
                    GlobalKind.Document -> { archivesTab = 0; open(HomeTab.Archives) }
                    GlobalKind.Goal -> open(HomeTab.Goals)
                    GlobalKind.Note, GlobalKind.Quest, GlobalKind.Habit, GlobalKind.Folder -> open(HomeTab.Inbox)
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page != null) {
                    IconButton(onClick = { pages.removeAt(pages.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Rune)
                    }
                }
                Text(
                    "M I N D Q U E S T",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    color = Rune,
                    modifier = Modifier.weight(1f),
                )
                profile?.let {
                    Surface(shape = RoundedCornerShape(50), color = Rune.copy(alpha = 0.12f)) {
                        Text(
                            "Lv ${it.level} · ${it.xp} XP",
                            style = MaterialTheme.typography.labelMedium,
                            color = Rune,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                IconButton(onClick = { searchOpen = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search everything", tint = Rune)
                }
                IconButton(onClick = { if (page != Page.Settings) { pages.clear(); pages.add(Page.Settings) } }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Rune)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = page == null && tab == t,
                        onClick = { open(t) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Rune,
                            selectedTextColor = Rune,
                            indicatorColor = Rune.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            // The assistant, one tap from Home — it answers from your own notes and documents.
            if (page == null && tab == HomeTab.Home) {
                FloatingActionButton(
                    onClick = { archivesTab = 1; open(HomeTab.Archives) },
                    containerColor = Rune,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask") }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val p = profile
            when (page) {
                Page.Settings -> SettingsScreen(repo, notify, onOpenBackup = { pages.add(Page.Backup) })
                Page.Backup -> DataScreen(repo, notify)
                null -> when (tab) {
                    HomeTab.Home -> if (p != null) DashboardScreen(repo, p, notify)
                    HomeTab.Inbox -> InboxScreen(repo, notify)
                    HomeTab.Goals -> GoalsScreen(repo, notify)
                    HomeTab.Archives -> ArchivesHub(repo, notify, archivesTab) { archivesTab = it }
                    HomeTab.Progress -> ProgressScreen(repo, p, notify)
                }
            }
        }
    }
}
