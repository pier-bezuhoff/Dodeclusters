package domain.io

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.core.net.toUri
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

// for overwriting previously created file at uri see
// https://developer.android.com/training/data-storage/shared/documents-files#persist-permissions

@Composable
actual fun SaveFileButton(
    saveData: SaveData<String>,
    buttonContent: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    saveRequests: SharedFlow<SaveRequest>?,
    onSaved: (SaveResult) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(saveData.mimeType)
    ) { uri ->
        coroutineScope.launch(Dispatchers.IO) {
            if (uri == null) {
                onSaved(SaveResult.Failure(
                    error = "ActivityResultContracts.CreateDocument launch returned uri = null"
                ))
            } else {
                try {
                    saveToUri(context, uri, saveData, onSaved)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    onSaved(SaveResult.Failure(error = e.message))
                } catch (e: FileNotFoundException) { // technically is a subclass of IOException
                    e.printStackTrace()
                    onSaved(SaveResult.Failure(error = e.message))
                } catch (e: IOException) {
                    e.printStackTrace()
                    onSaved(SaveResult.Failure(error = e.message))
                }
            }
        }
    }
    Button(
        onClick = {
            coroutineScope.launch {
                createDocumentLauncher.launch(saveData.filename)
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
            ?.collect { saveRequest ->
                coroutineScope.launch {
                    val filename = saveData.filename
                    when (saveRequest) {
                        SaveRequest.QUICK_SAVE -> {
                            val uri = saveData.uri?.toUri()
                            if (uri == null) {
                                createDocumentLauncher.launch(filename)
                            } else {
                                try {
                                    saveToUri(context, uri, saveData, onSaved)
                                } catch (e: FileNotFoundException) {
                                    e.printStackTrace()
                                    createDocumentLauncher.launch(filename)
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                    createDocumentLauncher.launch(filename)
                                } catch (e: SecurityException) {
                                    e.printStackTrace()
                                    createDocumentLauncher.launch(filename)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    onSaved(SaveResult.Failure(error = e.message))
                                }
                            }
                        }
                        SaveRequest.SAVE_AS -> {
                            createDocumentLauncher.launch(filename)
                        }
                    }
                }
            }
    }
}

@Throws(FileNotFoundException::class, IOException::class, SecurityException::class)
private suspend fun saveToUri(
    context: Context,
    uri: Uri,
    saveData: SaveData<String>,
    onSaved: (SaveResult) -> Unit,
    defaultName: String = DdcV4.DEFAULT_NAME,
) {
    var filename: String? = null
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst()) {
            val displayName = cursor.getString(nameIndex)
            //  reference: https://developer.android.com/privacy-and-security/risks/untrustworthy-contentprovider-provided-filename
            filename = sanitizeFilename(displayName)
            name = File(filename).nameWithoutExtension
        }
    }
    if (name == null) {
        filename = uri.lastPathSegment
        if (filename != null) {
            name = File(filename).nameWithoutExtension
        }
    }
    val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "w")
    if (fileDescriptor == null) {
        onSaved(SaveResult.Failure(
            filename = filename,
            error = "contentResolver.openFileDescriptor(uri, 'w') returned null"
        ))
    } else {
        fileDescriptor.use { parcelFileDescriptor ->
            FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                val content = saveData.prepareContent(name ?: defaultName)
                outputStream.write(content.toByteArray())
            }
        }
        persistReadWritePermissions(context, uri)
        onSaved(SaveResult.Success(
            filename = filename ?: "null-filename",
            uri = uri.toString(),
        ))
    }
}

private fun persistReadWritePermissions(context: Context, uri: Uri) {
    context.applicationContext.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )
}

/** reference: https://developer.android.com/privacy-and-security/risks/untrustworthy-contentprovider-provided-filename */
private fun sanitizeFilename(displayName: String): String {
    val badCharacters = arrayOf("..", "/")
    val segments = displayName.split("/")
    var fileName = segments.last()
    for (susString in badCharacters) {
        fileName = fileName.replace(susString, "_")
    }
    return fileName
}