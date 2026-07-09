package com.mindquest.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindquest.app.api.ApiClient
import com.mindquest.app.api.ChatMessage
import kotlinx.coroutines.launch

private val Rune = Color(0xFFF5B942)

/**
 * Narrator chat: RAG-grounded answers from the user's own archives, with the
 * citations the API validated server-side rendered under each reply.
 */
@Composable
fun ChatScreen(api: ApiClient) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var sessionId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val existing = api.chatSessions().firstOrNull()
            if (existing != null) {
                sessionId = existing.id
                messages = api.chatMessages(existing.id)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        busy = true
        error = null
        scope.launch {
            try {
                val sid = sessionId ?: api.createChatSession().id.also { sessionId = it }
                messages = messages + ChatMessage(id = "local-user", role = "user", content = text)
                val reply = api.sendChatMessage(sid, text)
                messages = messages.dropLast(1) +
                    ChatMessage(id = "u-${messages.size}", role = "user", content = text) +
                    reply
            } catch (e: Exception) {
                error = "The Narrator is silent — check your connection and try again."
                messages = messages.filterNot { it.id == "local-user" }
                input = text
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Ask the Narrator about anything in your archives. " +
                            "Every answer cites your own documents.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(messages) { message -> MessageBubble(message) }
            if (busy) {
                item {
                    Text(
                        "The Narrator consults the archives…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about your knowledge…") },
                enabled = !busy,
                maxLines = 3,
            )
            Spacer(Modifier.widthIn(min = 8.dp))
            Button(onClick = { send() }, enabled = !busy && input.isNotBlank()) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Card(Modifier.widthIn(max = 320.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
                if (message.citations.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    message.citations.forEach { citation ->
                        Text(
                            buildString {
                                append("[${citation.index}] ${citation.title}")
                                citation.location?.let { append(" · $it") }
                                append(" — “${citation.snippet}…”")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Rune,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
