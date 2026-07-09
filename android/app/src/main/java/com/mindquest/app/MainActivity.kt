package com.mindquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mindquest.app.api.ApiClient
import com.mindquest.app.api.Habit
import com.mindquest.app.api.Profile
import com.mindquest.app.api.Quest
import kotlinx.coroutines.launch

private val Rune = Color(0xFFF5B942)
private val Abyss = Color(0xFF0B0E1A)
private val Realm = Color(0xFF141A2E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Rune,
                    background = Abyss,
                    surface = Realm,
                )
            ) {
                MindQuestApp()
            }
        }
    }
}

@Composable
fun MindQuestApp() {
    val api = remember { ApiClient() }
    var loggedIn by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!loggedIn) {
            LoginScreen(api) { loggedIn = true }
        } else {
            QuestHubScreen(api)
        }
    }
}

@Composable
fun LoginScreen(api: ApiClient, onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("MindQuest", style = MaterialTheme.typography.headlineLarge, color = Rune)
        Text("Welcome back, hero.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    try {
                        api.login(email, password)
                        onLoggedIn()
                    } catch (e: Exception) {
                        error = "Sign-in failed — check credentials and API_BASE_URL."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign in") }
    }
}

@Composable
fun QuestHubScreen(api: ApiClient) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var quests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }

    fun refresh() {
        scope.launch {
            runCatching {
                profile = api.profile()
                quests = api.activeQuests()
                habits = api.habits()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            profile?.let { p ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Level ${p.level}", style = MaterialTheme.typography.headlineSmall, color = Rune)
                        Text("${p.xp} XP · ${p.skill_points} skill points · best streak ${p.current_streak_max}")
                        LinearProgressIndicator(
                            progress = { (p.progress_pct / 100.0).toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        item { Text("⚔️ Active quests", style = MaterialTheme.typography.titleMedium) }
        items(quests) { q ->
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(q.title)
                        Text("${q.difficulty} · +${q.xp_reward} XP", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { scope.launch { runCatching { api.completeQuest(q.id) }; refresh() } }) {
                        Text("Complete")
                    }
                }
            }
        }
        item { Text("🔥 Daily missions", style = MaterialTheme.typography.titleMedium) }
        items(habits) { h ->
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(h.title)
                        Text("streak ${h.streak}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (h.checked_in_today) {
                        Text("✓ done", color = Rune)
                    } else {
                        TextButton(onClick = { scope.launch { runCatching { api.checkin(h.id) }; refresh() } }) {
                            Text("Check in")
                        }
                    }
                }
            }
        }
    }
}
