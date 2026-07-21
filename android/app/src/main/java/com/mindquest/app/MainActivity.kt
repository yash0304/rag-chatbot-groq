package com.mindquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.ui.Abyss
import com.mindquest.app.ui.DashboardScreen
import com.mindquest.app.ui.HabitsScreen
import com.mindquest.app.ui.QuestsScreen
import com.mindquest.app.ui.Realm
import com.mindquest.app.ui.Rune
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

private enum class Tab(val label: String, val icon: String) {
    Dashboard("Home", "🏰"),
    Quests("Quests", "⚔️"),
    Habits("Missions", "🔥"),
}

@Composable
fun MindQuestApp() {
    val context = LocalContext.current
    val repo = remember { MindQuestRepository(context.applicationContext) }
    var state by remember { mutableStateOf(AppState.Loading) }

    // First run: seed catalogs, then route to onboarding or the app. Fully offline.
    LaunchedEffect(Unit) {
        repo.seedIfEmpty()
        state = if (repo.hasProfile()) AppState.Ready else AppState.Onboarding
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("MindQuest", style = MaterialTheme.typography.headlineLarge, color = Rune)
        Text("Your knowledge, made legend. Everything lives on this device.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            name, { name = it },
            label = { Text("Name your hero") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    repo.createProfile(name)
                    onDone()
                }
            },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Forging your legend…" else "Begin your saga") }
    }
}

@Composable
private fun HomeShell(repo: MindQuestRepository) {
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    val profile by repo.observeProfile().collectAsState(initial = null)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MindQuest", style = MaterialTheme.typography.titleLarge, color = Rune)
                profile?.let { Text("Lv ${it.level} · ${it.skillPoints}✨", style = MaterialTheme.typography.bodySmall) }
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.icon) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val p = profile
            when (tab) {
                Tab.Dashboard -> if (p != null) DashboardScreen(repo, p)
                Tab.Quests -> QuestsScreen(repo, notify)
                Tab.Habits -> HabitsScreen(repo, notify)
            }
        }
    }
}
