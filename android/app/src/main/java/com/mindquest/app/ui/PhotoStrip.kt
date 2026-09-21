package com.mindquest.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mindquest.app.data.AttachmentEntity
import com.mindquest.app.data.PhotoStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val photoStamp = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/**
 * The photo trail on a quest or mission.
 *
 * A monthly weigh-in is a number you will not remember arguing with in eighteen months, so
 * the evidence goes next to the goal: a row of thumbnails in the order they were taken, each
 * captioned with its date, which is the whole point — the strip is the progress.
 *
 * Photos can come from the camera or the gallery. Neither route needs a permission: the
 * gallery picker hands back a single image the user chose, and the camera writes into a file
 * we own and share for that one call. Declaring CAMERA or storage access would have bought
 * nothing and cost an install prompt.
 */
@Composable
fun PhotoStrip(
    photos: List<AttachmentEntity>,
    onAdd: (path: String) -> Unit,
    onRemove: (AttachmentEntity) -> Unit,
) {
    val context = LocalContext.current
    var viewing by remember { mutableStateOf<AttachmentEntity?>(null) }
    var pendingCapture by remember { mutableStateOf<File?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { PhotoStore.importFrom(context, it)?.let { file -> onAdd(file.absolutePath) } }
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = pendingCapture
        pendingCapture = null
        // A cancelled capture leaves an empty file behind; clear it rather than keeping a
        // zero-byte photo that would render as a blank square forever.
        if (saved && file != null && file.length() > 0) onAdd(file.absolutePath) else file?.delete()
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            onDismiss = { viewing = null },
            onRemove = {
                viewing = null
                onRemove(photo)
            },
        )
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        photos.forEach { photo ->
            Thumbnail(photo.path, Modifier.size(64.dp).clickable { viewing = photo })
        }
        TextButton(onClick = {
            val file = PhotoStore.newFile(context)
            pendingCapture = file
            val uri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            }.getOrNull()
            if (uri == null) {
                pendingCapture = null
                file.delete()
            } else {
                takePhoto.launch(uri)
            }
        }) { Text("📷", style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = { pickImage.launch("image/*") }) {
            Text("🖼", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Decoding happens off the composition thread: a full-size camera photo takes long enough
 * to decode that doing it inline would stutter the list it is scrolling in.
 */
@Composable
private fun Thumbnail(
    path: String,
    modifier: Modifier = Modifier,
    maxPx: Int = 256,
    scale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxPx) {
        value = withContext(Dispatchers.IO) { PhotoStore.thumbnail(path, maxPx)?.asImageBitmap() }
    }
    val shaped = modifier.clip(RoundedCornerShape(6.dp))
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "attached photo",
            modifier = shaped,
            contentScale = scale,
        )
    } ?: Box(shaped.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun PhotoViewer(
    photo: AttachmentEntity,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(photoStamp.format(Date(photo.createdAt))) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Thumbnail(
                    photo.path,
                    Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    maxPx = 1024,
                    scale = ContentScale.Fit,
                )
                photo.caption?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = onRemove) { Text("Delete photo", color = Ember) }
        },
    )
}
