package com.mindquest.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.data.PhotoStore
import com.mindquest.app.data.ScrapItemEntity
import com.mindquest.app.data.ScrapPageEntity
import com.mindquest.app.domain.CutoutMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Page colours: paper, kraft, blush, sage, sky, butter, slate, night. */
private val PAGE_COLOURS = listOf(
    0xFFF7F1E3.toInt(), 0xFFD9C3A0.toInt(), 0xFFF4D6D6.toInt(), 0xFFD5E3D0.toInt(),
    0xFFD4E4F1.toInt(), 0xFFF6E7A8.toInt(), 0xFF5B6770.toInt(), 0xFF1E2230.toInt(),
)

/** Pages are 3:4, like a printed photo book page. */
private const val PAGE_ASPECT = 3f / 4f

/**
 * The scrapbook: pick a photo, touch what you want, and it's cut out and put on a page —
 * the ticket from the concert, the dog without the sofa, the cake without the table.
 * Cutouts are moved with a finger, sized and turned with two, and a page can be shared as
 * an image. Everything happens on the phone.
 */
@Composable
fun ScrapbookPane(repo: MindQuestRepository, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val pages by repo.observeScrapPages().collectAsState(emptyList())
    val items by repo.observeScrapItems().collectAsState(emptyList())
    val images by repo.observeAttachments(MindQuestRepository.SCRAP_KIND).collectAsState(emptyList())
    val paths = images.associate { it.id to it.path }
    val itemsByPage = items.groupBy { it.pageId }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    val openPage = pages.firstOrNull { it.id == openId }

    if (naming) {
        NewFolderDialog(
            title = "New page",
            confirm = "Create",
            onDismiss = { naming = false },
            onCreate = { name ->
                naming = false
                scope.launch { openId = repo.createScrapPage(name, PAGE_COLOURS[pages.size % PAGE_COLOURS.size]) }
            },
        )
    }

    if (openPage != null) {
        PageEditor(
            repo = repo,
            page = openPage,
            items = itemsByPage[openPage.id].orEmpty(),
            paths = paths,
            notify = notify,
            onBack = { openId = null },
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scrapbook", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                Text(
                    "Touch anything in a photo to cut it out, then arrange it on a page.",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
            }
            Button(onClick = { naming = true }) { Text("＋ Page") }
        }
        Spacer(Modifier.height(12.dp))
        if (pages.isEmpty()) {
            Text(
                "No pages yet. Make one — “Goa trip”, “Diwali 2026”, “Before & after” — and add cutouts to it.",
                style = MaterialTheme.typography.bodyMedium, color = Muted,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pages, key = { it.id }) { p ->
                Column(Modifier.clickable { openId = p.id }) {
                    PageCanvas(
                        background = p.background,
                        items = itemsByPage[p.id].orEmpty(),
                        paths = paths,
                        decodePx = 384,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(p.title, color = Parchment, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        "${itemsByPage[p.id].orEmpty().size} cutouts",
                        style = MaterialTheme.typography.labelSmall, color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageEditor(
    repo: MindQuestRepository,
    page: ScrapPageEntity,
    items: List<ScrapItemEntity>,
    paths: Map<String, String>,
    notify: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<String?>(null) }
    // The picture being moved, ahead of the database: written back once the fingers rest.
    var live by remember { mutableStateOf<ScrapItemEntity?>(null) }
    var making by remember { mutableStateOf<Boolean?>(null) } // true = cutout, false = whole photo
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(live) {
        val pending = live ?: return@LaunchedEffect
        delay(400)
        repo.updateScrapItem(pending)
    }
    val shown = items.map { if (it.id == live?.id) live!! else it }

    making?.let { cutout ->
        CutoutMaker(
            wholePhoto = !cutout,
            onDismiss = { making = null },
            onDone = { path, aspect ->
                making = null
                scope.launch {
                    selected = repo.addScrapItem(page.id, path, aspect)
                    notify(if (cutout) "✂️ Cut out — drag it into place, pinch to size and turn." else "🖼 Added.")
                }
            },
        )
    }
    if (renaming) {
        NewFolderDialog(
            initial = page.title, title = "Rename page", confirm = "Rename",
            onDismiss = { renaming = false },
            onCreate = { name -> renaming = false; scope.launch { repo.renameScrapPage(page.id, name) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${page.title}”?") },
            text = { Text("The page and its ${items.size} cutouts go. Your original photos in the gallery are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onBack()
                    scope.launch { repo.deleteScrapPage(page.id); notify("Page deleted.") }
                }) { Text("Delete", color = Ember) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("← Pages") }
            Text(
                page.title,
                style = MaterialTheme.typography.titleMedium, color = Parchment, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.weight(1f).clickable { renaming = true },
            )
            TextButton(enabled = !sharing && items.isNotEmpty(), onClick = {
                sharing = true
                scope.launch {
                    val ok = runCatching { sharePage(context, page, shown, paths) }.isSuccess
                    sharing = false
                    if (!ok) notify("Couldn't make an image of this page.")
                }
            }) { Text(if (sharing) "…" else "Share") }
            var menu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { menu = true }) { Text("⋯", color = Muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete page") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PageCanvas(
                background = page.background,
                items = shown,
                paths = paths,
                decodePx = 1024,
                selected = selected,
                onSelect = { id ->
                    live?.let { scope.launch { repo.updateScrapItem(it) } }
                    live = null
                    selected = id
                },
                onTransform = { pan, zoom, turn, pageW, pageH ->
                    val base = live?.takeIf { it.id == selected } ?: items.firstOrNull { it.id == selected }
                    if (base != null) {
                        live = base.copy(
                            x = (base.x + pan.first / pageW).coerceIn(0f, 1f),
                            y = (base.y + pan.second / pageH).coerceIn(0f, 1f),
                            scale = (base.scale * zoom).coerceIn(0.05f, 2f),
                            rotation = (base.rotation + turn) % 360f,
                        )
                    }
                },
                modifier = Modifier.fillMaxHeight(),
            )
            if (items.isEmpty()) {
                Text(
                    "Empty page.\nTap ✂️ Cutout below and pick a photo.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PAGE_COLOURS.forEach { c ->
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == page.background) 3.dp else 1.dp, if (c == page.background) Rune else Muted, CircleShape)
                        .clickable { scope.launch { repo.setScrapBackground(page.id, c) } },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { making = true }, modifier = Modifier.weight(1f)) { Text("✂️ Cutout") }
            OutlinedButton(onClick = { making = false }, modifier = Modifier.weight(1f)) { Text("🖼 Photo") }
        }
        if (selected != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val id = selected!!
                TextButton(onClick = { scope.launch { repo.bringScrapToFront(id) } }) { Text("⬆ To front") }
                TextButton(onClick = {
                    val base = live?.takeIf { it.id == id } ?: items.firstOrNull { it.id == id }
                    if (base != null) live = base.copy(rotation = 0f)
                }) { Text("↻ Straighten") }
                TextButton(onClick = {
                    selected = null
                    live = null
                    scope.launch { repo.deleteScrapItem(id) }
                }) { Text("Remove", color = Ember) }
            }
        } else if (items.isNotEmpty()) {
            Text(
                "Tap a cutout to select it — drag to move, pinch to size and turn.",
                style = MaterialTheme.typography.labelSmall, color = Muted,
            )
        }
    }
}

/**
 * A page: its colour and its cutouts, each at its place, size and angle. Interactive when
 * [onSelect] is given — a tap picks the top-most picture under the finger, and gestures
 * anywhere on the page move the selected one, so a small cutout is still easy to pinch.
 */
@Composable
private fun PageCanvas(
    background: Int,
    items: List<ScrapItemEntity>,
    paths: Map<String, String>,
    decodePx: Int,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onSelect: ((String?) -> Unit)? = null,
    onTransform: ((pan: Pair<Float, Float>, zoom: Float, turn: Float, pageW: Float, pageH: Float) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val latestItems by rememberUpdatedState(items)
    val latestTransform by rememberUpdatedState(onTransform)
    val latestSelect by rememberUpdatedState(onSelect)
    BoxWithConstraints(
        modifier.aspectRatio(PAGE_ASPECT).clip(RoundedCornerShape(10.dp)).background(Color(background))
            .border(1.dp, Muted.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
    ) {
        val pageW = constraints.maxWidth.toFloat()
        val pageH = constraints.maxHeight.toFloat()
        val gestures = if (onSelect == null) Modifier else Modifier
            .pointerInput(pageW, pageH) {
                detectTapGestures { tap ->
                    val hit = latestItems.sortedByDescending { it.z }.firstOrNull { s ->
                        val w = s.scale * pageW
                        CutoutMath.hits(tap.x, tap.y, s.x * pageW, s.y * pageH, w, w * s.aspect, s.rotation)
                    }
                    latestSelect?.invoke(hit?.id)
                }
            }
            .pointerInput(pageW, pageH) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    latestTransform?.invoke(pan.x to pan.y, zoom, rotation, pageW, pageH)
                }
            }
        Box(Modifier.fillMaxSize().then(gestures)) {
            items.sortedBy { it.z }.forEach { item ->
                key(item.id) {
                    val path = paths[item.id]
                    val bitmap by produceState<ImageBitmap?>(null, path, decodePx) {
                        value = path?.let { p -> withContext(Dispatchers.IO) { PhotoStore.thumbnail(p, decodePx)?.asImageBitmap() } }
                    }
                    val w = item.scale * pageW
                    val h = w * item.aspect
                    val img = bitmap
                    if (img != null) {
                        Image(
                            bitmap = img,
                            contentDescription = null,
                            modifier = Modifier
                                .offset { IntOffset((item.x * pageW - w / 2).roundToInt(), (item.y * pageH - h / 2).roundToInt()) }
                                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                                .graphicsLayer { rotationZ = item.rotation }
                                .then(
                                    if (item.id == selected) Modifier.border(2.dp, Rune, RoundedCornerShape(2.dp))
                                    else Modifier,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Draws the page at 1080 × 1440 and hands it to the share sheet. */
private suspend fun sharePage(
    context: Context,
    page: ScrapPageEntity,
    items: List<ScrapItemEntity>,
    paths: Map<String, String>,
) {
    val file = withContext(Dispatchers.Default) {
        val w = 1080
        val h = (w / PAGE_ASPECT).roundToInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(page.background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        items.sortedBy { it.z }.forEach { item ->
            val bmp = paths[item.id]?.let { PhotoStore.thumbnail(it, 1600) } ?: return@forEach
            val iw = item.scale * w
            val ih = iw * item.aspect
            canvas.save()
            canvas.translate(item.x * w, item.y * h)
            canvas.rotate(item.rotation)
            canvas.drawBitmap(bmp, null, RectF(-iw / 2, -ih / 2, iw / 2, ih / 2), paint)
            canvas.restore()
        }
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // one share at a time; no pile-up in cache
        val safe = page.title.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "page" }
        File(dir, "Scrapbook-$safe.png").also { f -> f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, page.title))
}
