package com.mindquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mindquest.app.api.ApiClient
import com.mindquest.app.api.ApiException
import com.mindquest.app.api.Habit
import com.mindquest.app.api.Profile
import com.mindquest.app.api.Quest
import com.mindquest.app.api.TokenStore
import com.mindquest.app.ui.ChatScreen
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

private enum class SessionState { Restoring, LoggedOut, LoggedIn }

private enum class Tab(val label: String, val icon: String) {
    Hub("Quest Hub", "⚔️"),
    Narrator("Narrator", "🔮"),
}

@Composable
fun MindQuestApp() {
    val context = LocalContext.current
    val api = remember { ApiClient(TokenStore(context.applicationContext)) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(SessionState.Restoring) }
    var tab by remember { mutableStateOf(Tab.Hub) }

    LaunchedEffect(Unit) {
        session = if (api.restoreSession()) SessionState.LoggedIn else SessionState.LoggedOut
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (session) {
            SessionState.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            SessionState.LoggedOut -> AuthScreen(api) { session = SessionState.LoggedIn }

            SessionState.LoggedIn -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("MindQuest", style = MaterialTheme.typography.titleLarge, color = Rune)
                        TextButton(onClick = {
                            scope.launch {
                                api.logout()
                                session = SessionState.LoggedOut
                            }
                        }) { Text("Sign out") }
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
                    when (tab) {
                        Tab.Hub -> QuestHubScreen(api)
                        Tab.Narrator -> ChatScreen(api)
                    }
                }
            }
        }
    }
}

@Composable
fun AuthScreen(api: ApiClient, onAuthed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var registerMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showServer by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(api.currentServerUrl()) }

    fun submit() {
        error = null
        if (registerMode && name.isBlank()) {
            error = "Every hero needs a name."
            return
        }
        if (email.isBlank() || password.isBlank()) {
            error = "Email and password are required."
            return
        }
        if (registerMode && password.length < 8) {
            error = "Password must be at least 8 characters."
            return
        }
        busy = true
        scope.launch {
            try {
                api.setServerUrl(serverUrl)
                if (registerMode) {
                    api.register(email.trim(), password, name.trim())
                } else {
                    api.login(email.trim(), password)
                }
                onAuthed()
            } catch (e: ApiException) {
                error = when {
                    e.isConnectionError ->
                        "Can't reach the server. Check it's running and the server URL below is correct."
                    e.code == 401 -> "Invalid email or password."
                    e.code == 409 -> "That email is already registered — try signing in."
                    e.code == 422 -> "Please check your details and try again."
                    else -> "Something went wrong (${e.code}). Please try again."
                }
            } catch (e: Exception) {
                error = "Something went wrong. Please try again."
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("MindQuest", style = MaterialTheme.typography.headlineLarge, color = Rune)
        Text(
            if (registerMode) "Begin your saga." else "Welcome back, hero.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        if (registerMode) {
            OutlinedTextField(
                name, { name = it }, label = { Text("Your name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            password, { password = it },
            label = { Text(if (registerMode) "Password (8+ characters)" else "Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    busy && registerMode -> "Forging your legend…"
                    busy -> "Opening the gates…"
                    registerMode -> "Create account"
                    else -> "Sign in"
                }
            )
        }

        TextButton(
            onClick = { registerMode = !registerMode; error = null },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (registerMode) "Already have a hero? Sign in"
                else "New to the realm? Begin your saga",
                color = Rune,
            )
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { showServer = !showServer },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(if (showServer) "Hide server settings" else "Server settings", color = Rune)
        }
        if (showServer) {
            OutlinedTextField(
                serverUrl, { serverUrl = it },
                label = { Text("Server URL") },
                singleLine = true,
                supportingText = { Text("Emulator: http://10.0.2.2:8000 · Device: http://<PC-LAN-IP>:8000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
