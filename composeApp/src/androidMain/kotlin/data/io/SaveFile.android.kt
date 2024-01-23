package data.io

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

@Composable
actual fun SaveFileButton(
    iconPainter: Painter,
    contentDescription: String,
    saveDataProvider: () -> SaveData,
    onSaved: (successful: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.CreateDocument("text/plain") // forces .txt extension; when used with filename ending in .txt creates empty file
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        try {
            uri?.let {
                context.contentResolver.openFileDescriptor(uri, "w")?.use { parcelFileDescriptor ->
                    FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                        val saveData = saveDataProvider()
                        outputStream.write(saveData.content.toByteArray())
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
    IconButton(onClick = {
        coroutineScope.launch {
            val saveData = saveDataProvider()
            launcher.launch(saveData.filename)
        }
    }) {
        Icon(iconPainter, contentDescription)
    }
}