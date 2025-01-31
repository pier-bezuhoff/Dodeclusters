package domain.io

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.Intent.createChooser
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

@Composable
actual fun SaveBitmapAsPngButton(
    iconPainter: Painter,
    contentDescription: String,
    saveData: SaveData<Result<ImageBitmap>>,
    modifier: Modifier,
    onSaved: (successful: Boolean) -> Unit
) {
    // TODO
}

// reference: https://github.com/android/snippets/blob/latest/compose/snippets/src/main/java/com/example/compose/snippets/graphics/AdvancedGraphicsSnippets.kt
//@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BitmapFromComposableFullSnippet() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
//    val writeStorageAccessState = rememberMultiplePermissionsState(
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            // No permissions are needed on Android 10+ to add files in the shared storage
//            emptyList()
//        } else {
//            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//        }
//    )
    // This logic should live in your ViewModel - trigger a side effect to invoke URI sharing.
    // checks permissions granted, and then saves the bitmap from a Picture that is already capturing content
    // and shares it with the default share sheet.
    fun shareBitmapFromComposable() {
//        if (writeStorageAccessState.allPermissionsGranted) {
//            coroutineScope.launch {
//                val bitmap: ImageBitmap = TODO()
//                val uri = bitmap.asAndroidBitmap().saveToDisk(context)
//                shareBitmap(context, uri)
//            }
//        } else if (writeStorageAccessState.shouldShowRationale) {
//            coroutineScope.launch {
////                val result = snackbarHostState.showSnackbar(
////                    message = "The storage permission is needed to save the image",
////                    actionLabel = "Grant Access"
////                )
////                if (result == SnackbarResult.ActionPerformed) {
////                    writeStorageAccessState.launchMultiplePermissionRequest()
////                }
//            }
//        } else {
////            writeStorageAccessState.launchMultiplePermissionRequest()
//        }
    }
}

private suspend fun Bitmap.saveToDisk(context: Context): Uri {
    val file = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "screenshot-${System.currentTimeMillis()}.png"
    )
    file.writeBitmap(this, Bitmap.CompressFormat.PNG, 100)
    return scanFilePath(context, file.path) ?: throw Exception("File could not be saved")
}

/**
 * We call [MediaScannerConnection] to index the newly created image inside MediaStore to be visible
 * for other apps, as well as returning its [MediaStore] Uri
 */
private suspend fun scanFilePath(context: Context, filePath: String): Uri? {
    return suspendCancellableCoroutine { continuation ->
        MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            arrayOf("image/png")
        ) { _, scannedUri ->
            if (scannedUri == null) {
                continuation.cancel(Exception("File $filePath could not be scanned"))
            } else {
                continuation.resume(scannedUri) { cause, _, _ -> }
            }
        }
    }
}

private fun File.writeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
    outputStream().use { out ->
        bitmap.compress(format, quality, out)
        out.flush()
    }
}

private fun shareBitmap(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(context, createChooser(intent, "Share your image"), null)
}