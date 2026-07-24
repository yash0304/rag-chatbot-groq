package com.mindquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Rune, background = Abyss, surface = Realm),
            ) {
                MindQuestApp()
            }
        }
    }
}

private enum class AppState { Loading, Onboarding, Ready }

private enum class Dest(val label: String, val icon: String) {
    Dashboard("Dashboard", "🏰"),
    Quests("Quests", "⚔️"),
    Habits("Daily Missions", "🔥"),
    Goals("Story Arcs", "📖"),
    Archives("Archives", "📜"),
    Narrator("Narrator", "🔮"),
    WorldMap("World Map", "🗺️"),
    Skills("Skills", "✨"),
    Achievements("Hall of Deeds", "🏆"),
    Analytics("Chronicles", "📊"),
    Review("Weekly Review", "🕯️"),
    PersonalBests("Personal Bests", "🏅"),
    Settings("Settings", "⚙️"),
}

@Composable
fun MindQuestApp() {
    val context = LocalContext.current
    val repo = remember { MindQuestRepository(context.applicationContext) }
    var state by remember { mutableStateOf(AppState.Loading) }

    LaunchedEffect(Unit) {
        repo.seedIfEmpty()
        state = if (repo.hasProfile()) AppState.Ready else AppState.Onboarding
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AppState.Onboarding -> OnboardingScreen(repo) { state = AppState.Ready }
            AppState.Ready -> HomeShell(repo)
        }
    }
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
    var dest by remember { mutableStateOf(Dest.Dashboard) }
    val profile by repo.observeProfile().collectAsState(initial = null)
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(16.dp)) {
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
                            onClick = { dest = d; scope.launch { drawer.close() } },
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
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { drawer.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Rune)
                    }
                    Text(dest.label, style = MaterialTheme.typography.titleLarge, color = Rune, modifier = Modifier.weight(1f))
                    profile?.let { Text("Lv ${it.level} · ${it.skillPoints}✨", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.width(8.dp))
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val p = profile
                when (dest) {
                    Dest.Dashboard -> if (p != null) DashboardScreen(repo, p)
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
                    Dest.Settings -> SettingsScreen(repo, notify)
                }
            }
        }
    }
}
