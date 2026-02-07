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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ui.editor.EditorViewModel
import java.io.File
import java.io.FileOutputStream

// FIX: pngs are smudged nonsense
// reference: https://github.com/android/snippets/blob/latest/compose/snippets/src/main/java/com/example/compose/snippets/graphics/AdvancedGraphicsSnippets.kt
@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun SaveBitmapAsPngButton(
    viewModel: EditorViewModel,
    saveData: SaveData<Unit>,
    buttonContent: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    onSaved: (SaveResult) -> Unit
) {
//    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val writeStorageAccessState = rememberMultiplePermissionsState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No permissions are needed on Android 10+ to add files in the shared storage
            emptyList()
        } else {
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    )
    if (false) {
        Button(
            onClick = {
                if (writeStorageAccessState.allPermissionsGranted) {
                    coroutineScope.launch(Dispatchers.IO) {
    //                    saveData.prepareContent(saveData.name).fold(
    //                        onSuccess = { bitmap ->
    //                            // something aint right
    //                            saveBitmapToDisk(context, bitmap.asAndroidBitmap(), saveData.name)
    //                            // way too silent, notifs doko
    //                            onSaved(true)
    //                        },
    //                        onFailure = {
    //                        }
    //                    )
                        onSaved(SaveResult.Success(saveData.filename))
                    }
                } else if (writeStorageAccessState.shouldShowRationale) {
    //                coroutineScope.launch {
    //                val result = snackbarHostState.showSnackbar(
    //                    message = "The storage permission is needed to save the image",
    //                    actionLabel = "Grant Access"
    //                )
    //                if (result == SnackbarResult.ActionPerformed) {
                    writeStorageAccessState.launchMultiplePermissionRequest()
    //                }
    //                }
                } else {
                    writeStorageAccessState.launchMultiplePermissionRequest()
                }
            },
            modifier = modifier,
            shape = shape,
            colors = ButtonDefaults.buttonColors().copy(
                containerColor = containerColor,
                contentColor = contentColor,
            )
        ) {
            buttonContent()
            Text("Does NOT work") // TODO: fix this
        }
    }
}

private suspend fun saveBitmapToDisk(context: Context, bitmap: Bitmap, name: String): Uri? {
    val filename = "$name-${System.currentTimeMillis()}.png"
    val picsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    if (!picsDir.exists())
        picsDir.mkdirs()
    val file = File(picsDir, filename)
    return withContext(Dispatchers.IO) {
        FileOutputStream(file).buffered().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
        }
        val uri = scanFilePath(context, file.path)
//        shareBitmap(context, uri)
        uri
    }
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

private fun shareBitmap(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(createChooser(intent, "Share your image"), null)
}