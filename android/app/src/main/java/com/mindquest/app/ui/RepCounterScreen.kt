package com.mindquest.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.mindquest.app.data.HabitEntity
import com.mindquest.app.data.MindQuestRepository
import com.mindquest.app.domain.RepCounter
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Point the camera at yourself and do squats, push-ups, curls or jumping jacks; it counts.
 * MediaPipe finds the body in each frame on the phone — no video is saved or sent — and the
 * count goes to a daily goal and your XP when you're done.
 */
@Composable
fun RepCounterScreen(repo: MindQuestRepository, notify: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var exercise by remember { mutableStateOf(RepCounter.Exercise.Squats) }
    val counter = remember(exercise) { RepCounter(exercise) }
    var count by remember { mutableIntStateOf(0) }
    var angle by remember { mutableStateOf<Float?>(null) }
    var pose by remember { mutableStateOf<List<RepCounter.Point>>(emptyList()) }
    var frontCamera by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf(false) }
    LaunchedEffect(exercise) { count = 0; angle = null }

    if (finishing) {
        LogWorkoutDialog(
            repo = repo, exercise = exercise, reps = count,
            onDismiss = { finishing = false },
            onLog = { habit ->
                finishing = false
                val reps = count
                scope.launch {
                    val xp = repo.logWorkout(exercise.label, reps, habit?.id)
                    notify("${exercise.emoji} $reps ${exercise.label.lowercase()} · +$xp XP" + (habit?.let { " · ✓ ${it.title}" } ?: ""))
                    onDismiss()
                }
            },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rep counter", style = MaterialTheme.typography.titleLarge, color = Parchment, modifier = Modifier.weight(1f))
                    TextButton(onClick = { frontCamera = !frontCamera }) { Text("🔄") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                        .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RepCounter.Exercise.entries.forEach { e ->
                        FilterChip(selected = exercise == e, onClick = { exercise = e }, label = { Text("${e.emoji} ${e.label}") })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !granted -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("The camera is needed to see your movement. Nothing is recorded.", color = Color.White)
                            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                        }
                        failed != null -> Text(failed!!, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                        else -> {
                            CameraWithPose(
                                front = frontCamera,
                                onPose = { points, t ->
                                    pose = points
                                    if (counter.update(points, t)) count = counter.count
                                    angle = counter.lastAngle
                                },
                                onError = { failed = it },
                            )
                            Skeleton(pose, mirrored = frontCamera)
                        }
                    }
                    Column(Modifier.align(Alignment.TopCenter).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$count", color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (pose.isEmpty()) "Step back until your whole body is in view"
                            else angle?.let { "${exercise.label} · ${it.toInt()}°" } ?: "Turn a little so your joints show",
                            color = Color.White, style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { counter.reset(); count = 0 }, modifier = Modifier.weight(1f)) { Text("Reset") }
                    Button(enabled = count > 0, onClick = { finishing = true }, modifier = Modifier.weight(1f)) { Text("Done · $count") }
                }
            }
        }
    }
}

/** The camera, with every frame handed to MediaPipe's pose model on a background thread. */
@Composable
private fun CameraWithPose(
    front: Boolean,
    onPose: (List<RepCounter.Point>, Long) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPose by rememberUpdatedState(onPose)
    val main = remember { Handler(Looper.getMainLooper()) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    DisposableEffect(front) {
        val executor = Executors.newSingleThreadExecutor()
        var landmarker: PoseLandmarker? = null
        var lastTs = 0L
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener onError("Camera unavailable.")
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                try {
                    val lm = landmarker ?: PoseLandmarker.createFromOptions(
                        context,
                        PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                            .setRunningMode(RunningMode.VIDEO)
                            .setNumPoses(1)
                            .build(),
                    ).also { landmarker = it }
                    val raw = proxy.toBitmap()
                    val rotation = proxy.imageInfo.rotationDegrees
                    val upright = if (rotation == 0) raw else Bitmap.createBitmap(
                        raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true,
                    )
                    // Timestamps must only ever go up in video mode.
                    val ts = maxOf(SystemClock.uptimeMillis(), lastTs + 1).also { lastTs = it }
                    val result = lm.detectForVideo(BitmapImageBuilder(upright).build(), ts)
                    val points = result.landmarks().firstOrNull().orEmpty().map {
                        RepCounter.Point(it.x(), it.y(), it.visibility().orElse(0f))
                    }
                    main.post { latestOnPose(points, ts) }
                } catch (e: Throwable) {
                    main.post { onError("The pose model couldn't run on this phone (${e.message}).") }
                } finally {
                    proxy.close()
                }
            }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                onError("Couldn't open the ${if (front) "front" else "back"} camera.")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            // Close the model on the analysis thread, after any frame still in progress.
            executor.execute { runCatching { landmarker?.close() } }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** The joints the counter is watching, drawn over the picture. */
@Composable
private fun Skeleton(points: List<RepCounter.Point>, mirrored: Boolean) {
    if (points.size < 33) return
    Canvas(Modifier.fillMaxSize()) {
        fun at(p: RepCounter.Point) = Offset((if (mirrored) 1f - p.x else p.x) * size.width, p.y * size.height)
        RepCounter.BONES.forEach { (a, b) ->
            val pa = points[a]
            val pb = points[b]
            if (pa.visibility >= RepCounter.MIN_VISIBILITY && pb.visibility >= RepCounter.MIN_VISIBILITY) {
                drawLine(Color(0xFF7CFFB2), at(pa), at(pb), strokeWidth = 6.dp.toPx())
            }
        }
    }
}

@Composable
private fun LogWorkoutDialog(
    repo: MindQuestRepository,
    exercise: RepCounter.Exercise,
    reps: Int,
    onDismiss: () -> Unit,
    onLog: (HabitEntity?) -> Unit,
) {
    val habits by repo.observeHabits().collectAsState(emptyList())
    var chosen by remember { mutableStateOf<HabitEntity?>(null) }
    // Suggest the daily goal that sounds like this exercise.
    LaunchedEffect(habits) {
        if (chosen == null) {
            val word = exercise.label.lowercase().split(" ").first().trimEnd('s')
            chosen = habits.firstOrNull { it.title.lowercase().contains(word) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${exercise.emoji} $reps ${exercise.label.lowercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tick a daily goal with it?", style = MaterialTheme.typography.bodySmall, color = Muted)
                (listOf<HabitEntity?>(null) + habits).forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen?.id == h?.id, onClick = { chosen = h })
                        Text(h?.title ?: "No, just the XP")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onLog(chosen) }) { Text("Log it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep going") } },
    )
}
