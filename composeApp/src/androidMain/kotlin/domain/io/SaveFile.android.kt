package domain.io

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

@Composable
actual fun SaveFileButton(
    saveData: SaveData<String>,
    buttonContent: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    saveRequests: SharedFlow<Unit>?,
    onSaved: (success: Boolean?, filename: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(saveData.mimeType)
    ) { uri ->
        coroutineScope.launch(Dispatchers.IO) {
            try {
                uri?.let {
                    var name: String? = null
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        val displayName = cursor.getString(nameIndex)
                        //  reference: https://developer.android.com/privacy-and-security/risks/untrustworthy-contentprovider-provided-filename
                        val filename = sanitizeFilename(displayName)
                        name = File(filename).nameWithoutExtension
                    }
                    if (name == null) {
                        uri.lastPathSegment?.let { filename ->
                            name = File(filename).nameWithoutExtension
                        }
                    }
                    context.contentResolver.openFileDescriptor(uri, "w")?.use { parcelFileDescriptor ->
                        FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                            val content = saveData.prepareContent(name ?: DdcV4.DEFAULT_NAME)
                            outputStream.write(content.toByteArray())
                            onSaved(true, name ?: "")
                        }
                    } ?: onSaved(false, name ?: "")
                } ?: onSaved(false, "")
            } catch (e: IOException) {
                e.printStackTrace()
                onSaved(false, "")
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                onSaved(false, "")
            }
        }
    }
    Button(
        onClick = {
            coroutineScope.launch {
                launcher.launch(saveData.filename)
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
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(saveRequests) {
        saveRequests
            ?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            ?.collect {
                coroutineScope.launch {
                    launcher.launch(saveData.filename)
                }
            }
    }
}

/** reference: https://developer.android.com/privacy-and-security/risks/untrustworthy-contentprovider-provided-filename */
private fun sanitizeFilename(displayName: String): String {
    val badCharacters = arrayOf("..", "/")
    val segments = displayName.split("/")
    var fileName = segments[segments.size - 1]
    for (suspString in badCharacters) {
        fileName = fileName.replace(suspString, "_")
    }
    return fileName
}