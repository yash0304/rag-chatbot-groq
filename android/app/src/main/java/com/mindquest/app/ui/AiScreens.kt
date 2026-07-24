package com.mindquest.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.WeeklyReviewEntity
import kotlinx.coroutines.launch

// ---------- Narrator (chat) ----------

@Composable
fun NarratorScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val messages by repo.observeChatMessages().collectAsState(emptyList())
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val aiOn = remember { repo.aiConfigured() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("The Narrator", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                Text(
                    if (aiOn) "Answers from your archives, via Sarvam." else "Retrieval mode — add a Sarvam key in Settings for spoken answers.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                )
            }
            if (messages.isNotEmpty()) TextButton(onClick = { scope.launch { repo.clearChat() } }) { Text("Clear") }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item {
                Text("Ask the Narrator about anything you've archived. Every answer cites your own documents.", color = Color.Gray)
            }
            items(messages) { m ->
                val isUser = m.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Card(
                        Modifier.widthIn(max = 320.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) Rune.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.content, style = MaterialTheme.typography.bodyMedium, color = Parchment)
                            if (!isUser) {
                                val cites = repo.citationsOf(m)
                                if (cites.isNotEmpty()) {
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    cites.forEach { c ->
                                        Text(
                                            "[${c.index}] ${c.title}${c.location?.let { " · $it" } ?: ""} — “${c.snippet.take(90)}…”",
                                            style = MaterialTheme.typography.labelSmall, color = Rune,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (busy) item { Text("The Narrator consults the archives…", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("Ask about your knowledge…") }, modifier = Modifier.weight(1f), enabled = !busy, maxLines = 3)
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    val t = input.trim(); input = ""; busy = true
                    scope.launch {
                        try { repo.sendNarratorMessage(t) } catch (e: Exception) { notify("Something went wrong.") } finally { busy = false }
                    }
                },
            ) { Text("Send") }
        }
    }
}

// ---------- Weekly Review ----------

@Composable
fun WeeklyReviewScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val reviews by repo.observeReviews().collectAsState(emptyList())
    var busy by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Weekly Review", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try { repo.generateWeeklyReview(); notify("This week's chronicle is written.") }
                        catch (e: Exception) { notify("Could not write the review.") } finally { busy = false }
                    }
                }) { Text(if (busy) "Writing…" else "🕯️ Chronicle") }
            }
        }
        if (reviews.isEmpty()) item { Text("No chronicles yet. Generate this week's review.", color = Color.Gray) }
        items(reviews) { r -> ReviewCard(repo, r) }
    }
}

@Composable
private fun ReviewCard(repo: MindQuestRepository, r: WeeklyReviewEntity) {
    val stats = remember(r) { repo.weekStatsOf(r) }
    val suggestions = remember(r) { repo.suggestionsOf(r) }
    Card { Column(Modifier.padding(14.dp)) {
        Text("Week of ${r.weekStart}", style = MaterialTheme.typography.titleSmall, color = Rune)
        Spacer(Modifier.height(6.dp))
        Text(r.narrative, style = MaterialTheme.typography.bodyMedium, color = Parchment)
        Spacer(Modifier.height(8.dp))
        Text(
            "⚡ ${stats.xpEarned} XP · ⚔️ ${stats.questsCompleted} · 🔥 ${stats.habitCheckins} · 📜 ${stats.documentsProcessed}",
            style = MaterialTheme.typography.labelSmall, color = Color.Gray,
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Next week: ${suggestions.joinToString(" ")}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6EE7B7))
        }
    } }
}

// ---------- Settings (Sarvam key) ----------

@Composable
fun SettingsScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val s = repo.settings
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(s.sarvamModel()) }
    var configured by remember { mutableStateOf(s.hasSarvamKey()) }
    val usage = remember(configured) { s.usage() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Parchment)
        Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sarvam AI", fontWeight = FontWeight.Bold, color = Parchment)
            Text(
                if (configured) "✓ Key set — the Narrator, reviews and quest generation use Sarvam." else "No key — everything works offline (retrieval + templates).",
                style = MaterialTheme.typography.bodySmall, color = if (configured) Color(0xFF6EE7B7) else Color.Gray,
            )
            OutlinedTextField(
                key, { key = it }, label = { Text("Sarvam API key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(model, { model = it }, label = { Text("Model (e.g. sarvam-m)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (key.isNotBlank()) s.saveSarvamKey(key)
                    s.saveSarvamModel(model)
                    key = ""; configured = s.hasSarvamKey(); notify("Saved.")
                }) { Text("Save") }
                if (configured) OutlinedButton(onClick = { s.clearSarvamKey(); configured = false; notify("Key removed.") }) { Text("Remove key") }
            }
        } }
        Card { Column(Modifier.padding(14.dp)) {
            Text("Sarvam usage", fontWeight = FontWeight.Bold, color = Parchment)
            Text("${usage.first} calls · ${usage.second} chars in · ${usage.third} chars out", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } }
        Text("Your key is stored encrypted on this device and only sent to Sarvam on requests you initiate.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}
