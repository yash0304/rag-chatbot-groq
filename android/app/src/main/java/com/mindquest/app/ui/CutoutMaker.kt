package com.mindquest.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.mindquest.app.data.PhotoStore
import com.mindquest.app.domain.CutoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Pick or take a photo and cut something out of it. Tap the thing you want; scribble over
 * more of it if the first guess missed a bit; switch to Remove and tap what shouldn't be
 * there. Everything outside the selection is dimmed, so what you see is what you'll get.
 *
 * With [wholePhoto] it skips the cutting and adds the picture as it is.
 */
@Composable
fun CutoutMaker(
    wholePhoto: Boolean,
    onDismiss: () -> Unit,
    onDone: (path: String, aspect: Float) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    // The engine is made once per visit and closed on the way out.
    val engineHolder = remember { arrayOfNulls<CutoutEngine>(1) }
    var engineReady by remember { mutableStateOf<Boolean?>(null) } // null = not yet known
    DisposableEffect(Unit) { onDispose { engineHolder[0]?.close() } }

    val marks = remember { mutableStateListOf<CutoutEngine.Mark>() }
    var keep by remember { mutableStateOf(true) }
    var mask by remember { mutableStateOf<FloatArray?>(null) }
    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    var working by remember { mutableStateOf(false) }

    fun useBitmap(bitmap: Bitmap?) {
        loading = false
        if (bitmap == null) {
            failed = "That picture couldn't be opened."
            return
        }
        failed = null
        marks.clear(); mask = null; overlay = null
        if (wholePhoto) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { save(context, bitmap, png = false) }
                onDone(path, bitmap.height.toFloat() / bitmap.width)
            }
        } else {
            source = bitmap
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            loading = true
            scope.launch { useBitmap(withContext(Dispatchers.IO) { decode(context, uri) }) }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraFile
        cameraFile = null
        if (saved && file != null && file.length() > 0) {
            loading = true
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    PhotoStore.thumbnail(file.absolutePath, CutoutEngine.WORK_PX)?.let(::argb).also { file.delete() }
                }
                useBitmap(bmp)
            }
        } else {
            file?.delete()
        }
    }
    fun takePhoto() {
        val file = PhotoStore.newFile(context)
        cameraFile = file
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
    }

    // Load the model and hand it the picture whenever a new one arrives.
    LaunchedEffect(source) {
        val bmp = source ?: return@LaunchedEffect
        val engine = engineHolder[0] ?: withContext(Dispatchers.Default) { CutoutEngine.create(context) }
            .also { engineHolder[0] = it }
        engineReady = engine != null
        if (engine != null) withContext(Dispatchers.Default) { engine.setImage(bmp) }
    }
    // Re-run the segmenter whenever the marks change.
    LaunchedEffect(marks.toList(), engineReady) {
        val bmp = source
        val engine = engineHolder[0]
        if (bmp == null || engine == null || engineReady != true || marks.isEmpty()) {
            mask = null; overlay = null
            return@LaunchedEffect
        }
        working = true
        val m = withContext(Dispatchers.Default) { engine.segment(marks.toList()) }
        mask = m
        overlay = m?.let { withContext(Dispatchers.Default) { CutoutEngine.overlay(it, bmp.width, bmp.height).asImageBitmap() } }
        working = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (wholePhoto) "Add a photo" else "✂️ Cut out",
                        style = MaterialTheme.typography.titleLarge, color = Parchment,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                val bmp = source
                when {
                    loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    bmp == null -> Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (wholePhoto) "Pick a photo to put on the page."
                            else "Pick a photo, then tap what you want to keep.",
                            color = Muted,
                        )
                        failed?.let { Text(it, color = Ember) }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { pick.launch("image/*") }) { Text("🖼 From gallery") }
                        OutlinedButton(onClick = { takePhoto() }) { Text("📷 Take a photo") }
                    }
                    else -> {
                        if (engineReady == false) {
                            Text(
                                "Cutting out isn't available in this build or on this phone. You can still add the whole photo.",
                                color = Ember, style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = {
                                scope.launch {
                                    val path = withContext(Dispatchers.IO) { save(context, bmp, png = false) }
                                    onDone(path, bmp.height.toFloat() / bmp.width)
                                }
                            }) { Text("Add the whole photo") }
                        }
                        Editor(
                            bitmap = bmp,
                            overlay = overlay,
                            marks = marks,
                            onMark = { points -> if (engineReady == true) marks.add(CutoutEngine.Mark(points, keep)) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Text(
                            when {
                                engineReady == null -> "Getting ready…"
                                working -> "Cutting…"
                                marks.isEmpty() -> "Tap the thing you want. Scribble over more of it if needed."
                                mask == null -> "Nothing picked yet — tap right on it."
                                keep -> "Tap or scribble to add more."
                                else -> "Tap or scribble on what to take away."
                            },
                            style = MaterialTheme.typography.labelMedium, color = Muted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = keep, onClick = { keep = true }, label = { Text("➕ Keep") })
                            FilterChip(selected = !keep, onClick = { keep = false }, label = { Text("➖ Remove") })
                            TextButton(enabled = marks.isNotEmpty(), onClick = { marks.removeAt(marks.lastIndex) }) { Text("↶ Undo") }
                            TextButton(onClick = { source = null; marks.clear() }) { Text("New photo") }
                        }
                        Button(
                            enabled = mask != null && !working,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val m = mask ?: return@Button
                                working = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        CutoutEngine.cut(bmp, m)?.let { cut ->
                                            save(context, cut, png = true) to cut.height.toFloat() / cut.width
                                        }
                                    }
                                    working = false
                                    if (result != null) onDone(result.first, result.second)
                                }
                            },
                        ) { Text("Save cutout") }
                    }
                }
            }
        }
    }
}

/** The picture with the dimmed overlay, the marks made so far, and the touch handling. */
@Composable
private fun Editor(
    bitmap: Bitmap,
    overlay: ImageBitmap?,
    marks: List<CutoutEngine.Mark>,
    onMark: (List<PointF>) -> Unit,
    modifier: Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    // Where the picture actually sits inside the box once fitted: left, top, width, height.
    val fit = remember(box, bitmap) {
        if (box.width == 0 || box.height == 0) null else {
            val s = minOf(box.width.toFloat() / bitmap.width, box.height.toFloat() / bitmap.height)
            val w = bitmap.width * s
            val h = bitmap.height * s
            floatArrayOf((box.width - w) / 2, (box.height - h) / 2, w, h)
        }
    }
    fun norm(o: Offset): PointF? {
        val f = fit ?: return null
        val x = (o.x - f[0]) / f[2]
        val y = (o.y - f[1]) / f[3]
        return if (x in 0f..1f && y in 0f..1f) PointF(x, y) else null
    }
    // The scribble under the finger right now; it becomes a mark when the finger lifts.
    val drawing = remember { mutableStateListOf<PointF>() }
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.05f))
            .onSizeChanged { box = it }
            .pointerInput(fit) {
                detectTapGestures { o -> norm(o)?.let { onMark(listOf(it)) } }
            }
            .pointerInput(fit) {
                detectDragGestures(
                    onDragStart = { o -> drawing.clear(); norm(o)?.let { drawing.add(it) } },
                    onDrag = { change, _ -> norm(change.position)?.let { drawing.add(it) } },
                    onDragEnd = {
                        val pts = drawing.toList()
                        drawing.clear()
                        if (pts.isNotEmpty()) onMark(pts)
                    },
                    onDragCancel = { drawing.clear() },
                )
            },
    ) {
        Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        overlay?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        Canvas(Modifier.fillMaxSize()) {
            val f = fit ?: return@Canvas
            fun at(p: PointF) = Offset(f[0] + p.x * f[2], f[1] + p.y * f[3])
            (marks.map { it.points to it.keep } + listOf(drawing.toList() to true)).forEach { (pts, isKeep) ->
                if (pts.isEmpty()) return@forEach
                val colour = if (isKeep) Color(0xFF2E9E5B) else Color(0xFFD64545)
                if (pts.size == 1) {
                    drawCircle(Color.White, radius = 9.dp.toPx(), center = at(pts[0]))
                    drawCircle(colour, radius = 7.dp.toPx(), center = at(pts[0]))
                } else {
                    val path = Path().apply {
                        moveTo(at(pts[0]).x, at(pts[0]).y)
                        pts.drop(1).forEach { lineTo(at(it).x, at(it).y) }
                    }
                    drawPath(path, colour.copy(alpha = 0.7f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}

/** A picked picture at working size, the right way up. */
private fun decode(context: Context, uri: Uri): Bitmap? {
    val temp = File(context.cacheDir, "scrap-source-${UUID.randomUUID()}")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
            ?: return null
        PhotoStore.thumbnail(temp.absolutePath, CutoutEngine.WORK_PX)?.let(::argb)
    } catch (e: Exception) {
        null
    } finally {
        temp.delete()
    }
}

/** The segmenter wants 8-bit ARGB; some decoders hand back other layouts. */
private fun argb(b: Bitmap): Bitmap =
    if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)

/** Keep an image in the app's photo store; PNG for cutouts, whose see-through edges matter. */
private fun save(context: Context, bitmap: Bitmap, png: Boolean): String {
    val file = File(PhotoStore.dir(context), "${UUID.randomUUID()}.${if (png) "png" else "jpg"}")
    file.outputStream().use {
        bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 90, it)
    }
    return file.absolutePath
}
