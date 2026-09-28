package com.mindquest.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mindquest.app.data.PhotoStore
import java.io.File

/**
 * Take a photo with the phone's camera app, into the app's own storage. Returns the function
 * to call; [onTaken] gets the file, or nothing if the photo was cancelled.
 *
 * The camera permission is asked for first when it isn't held. The app declares that
 * permission for the rep counter, and Android then refuses the camera app to any app that
 * declares it without holding it — so every photo button goes through here.
 */
@Composable
fun rememberTakePhoto(onTaken: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val latest by rememberUpdatedState(onTaken)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pending
        pending = null
        // A cancelled capture leaves an empty file behind; clear it.
        if (saved && file != null && file.length() > 0) latest(file) else file?.delete()
    }
    fun open() {
        val file = PhotoStore.newFile(context)
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull()
        if (uri == null) {
            file.delete()
        } else {
            pending = file
            camera.launch(uri)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) open() }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open()
        else permission.launch(Manifest.permission.CAMERA)
    }
}
