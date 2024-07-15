package data.io

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

@Composable
actual fun SaveFileButton(
    iconPainter: Painter,
    contentDescription: String,
    saveDataProvider: () -> SaveData,
    modifier: Modifier,
    onSaved: (successful: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.CreateDocument("text/plain") // forces .txt extension; when used with filename ending in .txt creates empty file
//        contract = ActivityResultContracts.CreateDocument("*/*")
        contract = ActivityResultContracts.CreateDocument("application/yaml")
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
//                        uri.path?.substringAfterLast('/')?.let { filename ->
                        uri.lastPathSegment?.let { filename ->
                            name = File(filename).nameWithoutExtension
                        }
                    }
                    context.contentResolver.openFileDescriptor(uri, "w")?.use { parcelFileDescriptor ->
                        FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                            val saveData = saveDataProvider()
                            val content = saveData.content(name ?: Ddc.DEFAULT_NAME)
                            outputStream.write(content.toByteArray())
                            onSaved(true)
                        }
                    } ?: onSaved(false)
                } ?: onSaved(false)
            } catch (e: IOException) {
                e.printStackTrace()
                onSaved(false)

            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                onSaved(false)
            }
        }
    }
    IconButton(
        onClick = {
            coroutineScope.launch {
                val saveData = saveDataProvider()
                launcher.launch(saveData.filename)
            }
        },
        modifier = modifier,
    ) {
        Icon(iconPainter, contentDescription, modifier)
    }
}