package domain.io

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
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
                        name = File(cursor.getString(nameIndex)).nameWithoutExtension
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
}