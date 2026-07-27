package com.mindquest.app.ui

import android.Manifest
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindquest.app.data.GraphData
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.SearchHit
import com.mindquest.app.domain.WavRecorder
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val StatusColor = mapOf(
    "ready" to Color(0xFF6EE7B7), "processing" to Rune, "failed" to Color(0xFFF87171),
)

// ---------- Archives (documents + semantic search) ----------

@Composable
fun ArchivesScreen(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val docs by repo.observeDocuments().collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>?>(null) }

    val recorder = remember { WavRecorder() }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            notify("Tome received — the scribes are at work.")
            repo.importDocument(uri)
        }
    }

    fun startRecording() {
        try {
            recorder.start(File(context.cacheDir, "voice-${System.currentTimeMillis()}.wav"))
            recording = true
        } catch (e: Exception) {
            notify("Cannot record: ${e.message}")
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else notify("Microphone permission is needed for voice notes.")
    }
    fun onMic() {
        if (!repo.aiConfigured()) { notify("Add a Sarvam key in Settings to use voice notes."); return }
        if (recording) {
            recording = false
            val wav = recorder.stop()
            if (wav != null) {
                transcribing = true
                scope.launch {
                    try { repo.transcribeAndImport(wav); notify("Voice note transcribed & added.") }
                    catch (e: Exception) { notify("Voice note failed: ${e.message}") }
                    finally { transcribing = false }
                }
            }
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("The Archives", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onMic() }) { Text(if (recording) "⏹ Stop" else "🎤") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        picker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
                                "application/msword", // .doc (shown; handled with a helpful message)
                                "text/*", // .txt, .md, .csv, .log, …
                                "image/*",
                            ),
                        )
                    }) { Text("＋ Upload") }
                }
            }
            Text("PDF, Word (.docx), text, markdown, images — OCR & embeddings on-device.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            when {
                recording -> Text("● Recording… tap Stop when done.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF87171))
                transcribing -> Text("Transcribing your note via Sarvam…", style = MaterialTheme.typography.bodySmall, color = Rune)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query, { query = it }, label = { Text("Search your archives") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { results = repo.search(query) } }) { Text("Search") }
            }
        }
        results?.let { hits ->
            item { Text("Semantic results", style = MaterialTheme.typography.titleSmall, color = Rune) }
            if (hits.isEmpty()) item { Text("The archives hold nothing on this — yet.", color = Color.Gray) }
            items(hits) { h ->
                Card { Column(Modifier.padding(12.dp)) {
                    Text("${h.title}${h.location?.let { " · $it" } ?: ""} · ${"%.2f".format(h.score)}", style = MaterialTheme.typography.labelSmall, color = Rune)
                    Text("${h.snippet}…", style = MaterialTheme.typography.bodySmall, color = Parchment)
                } }
            }
        }
        item { Text("Documents", style = MaterialTheme.typography.titleMedium, color = Rune) }
        if (docs.isEmpty()) item { Text("The shelves are empty. Upload your first tome.", color = Color.Gray) }
        items(docs) { d ->
            Card { Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(d.title, color = Parchment, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(d.status, color = StatusColor[d.status] ?: Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
                d.domain?.let { Text("🗺️ $it", style = MaterialTheme.typography.labelSmall, color = Rune) }
                d.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                d.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF87171)) }
                if (d.tagsCsv.isNotBlank()) {
                    Text(d.tagsCsv.split(",").joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${d.chunkCount} passages${if (d.ocrUsed) " · OCR" else ""}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    TextButton(onClick = { scope.launch { repo.deleteDocument(d.id) } }) { Text("delete", color = Color(0xFFF87171)) }
                }
            } }
        }
    }
}

// ---------- World Map (knowledge graph) ----------

@Composable
fun WorldMapScreen(repo: MindQuestRepository, refreshKey: Int) {
    val graph by produceState<GraphData?>(null, refreshKey) { value = repo.buildGraph() }
    val labelPaint = remember { Paint().apply { color = Parchment.toArgb(); textSize = 26f; isAntiAlias = true } }
    val domainPaint = remember { Paint().apply { color = Rune.toArgb(); textSize = 30f; isFakeBoldText = true; isAntiAlias = true } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("World Map", style = MaterialTheme.typography.headlineMedium, color = Parchment)
        Text("🟡 domains · 🔵 documents · ⚪ tags", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        val g = graph
        if (g == null || g.nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Blank parchment. Upload documents to chart your first territory.", color = Color.Gray)
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val pos = ringLayout(g, size.width, size.height)
                g.edges.forEach { e ->
                    val s = pos[e.source]; val t = pos[e.target]
                    if (s != null && t != null) drawLine(Color(0x33CBD5E1), s, t, strokeWidth = 1.5f)
                }
                g.nodes.forEach { n ->
                    val p = pos[n.id] ?: return@forEach
                    val col = when (n.type) {
                        "domain" -> Rune
                        "document" -> Color(0xFF7DD3FC)
                        else -> Color(0xFF64748B)
                    }
                    val r = when (n.type) { "domain" -> 12f + n.size * 2; "document" -> 9f; else -> 5f }
                    drawCircle(col, r, p)
                    if (n.type != "tag") {
                        drawContext.canvas.nativeCanvas.drawText(
                            n.label.take(18), p.x + r + 6f, p.y + 8f,
                            if (n.type == "domain") domainPaint else labelPaint,
                        )
                    }
                }
            }
        }
    }
}

/** Deterministic concentric-ring layout: domains inner, documents middle, tags outer. */
private fun ringLayout(graph: GraphData, w: Float, h: Float): Map<String, Offset> {
    val cx = w / 2; val cy = h / 2
    val base = min(w, h)
    val rings = mapOf("domain" to base * 0.16f, "document" to base * 0.30f, "tag" to base * 0.44f)
    val out = HashMap<String, Offset>()
    graph.nodes.groupBy { it.type }.forEach { (type, list) ->
        val r = rings[type] ?: base * 0.3f
        list.forEachIndexed { i, n ->
            val a = 2.0 * Math.PI * i / list.size.coerceAtLeast(1)
            out[n.id] = Offset(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat())
        }
    }
    return out
}
