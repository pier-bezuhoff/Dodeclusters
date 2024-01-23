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
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        try {
            // BUG: only creates empty file it seems
            uri?.let {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val saveData = saveDataProvider()
                    outputStream.bufferedWriter().write(saveData.content)
                    onSaved(true)
                } ?: onSaved(false)
            } ?: onSaved(false)
        } catch (e: IOException) {
            onSaved(false)
        } catch (e: FileNotFoundException) {
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