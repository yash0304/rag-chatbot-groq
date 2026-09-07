package com.mindquest.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.GlobalHit
import com.mindquest.app.data.GlobalKind
import com.mindquest.app.data.MindQuestRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search across the whole app from anywhere.
 *
 * Results say which screen each hit lives on, because the point is finding a half
 * remembered thing without knowing where you filed it. Tapping a result takes you there.
 */
@Composable
fun GlobalSearchSheet(
    repo: MindQuestRepository,
    onDismiss: () -> Unit,
    onOpen: (GlobalKind) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<GlobalHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Debounced: the archives run a transformer per query, so firing on every keystroke
    // would queue inference the user has already typed past.
    LaunchedEffect(query) {
        if (query.isBlank()) { hits = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(300)
        hits = repo.searchEverything(query)
        searching = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search everything") },
                placeholder = { Text("a note, a document, a quest…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            when {
                query.isBlank() -> Text(
                    "Searches your inbox, archives, quests, missions and arcs at once.",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
                searching -> Text("Searching…", style = MaterialTheme.typography.bodySmall, color = Muted)
                hits.isEmpty() -> Text(
                    "Nothing matches “$query”.",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
                else -> LazyColumn(
                    Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(hits) { hit ->
                        Card(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { onOpen(hit.kind); onDismiss() }
                            },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(hit.kind.icon)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        hit.kind.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Rune, fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(hit.title, style = MaterialTheme.typography.bodyMedium, color = Parchment)
                                if (hit.subtitle.isNotBlank()) {
                                    Text(
                                        hit.subtitle,
                                        style = MaterialTheme.typography.labelSmall, color = Muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
