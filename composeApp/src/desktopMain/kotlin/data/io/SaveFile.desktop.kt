package data.io

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

fun saveTextFile(content: String, filename: String) {
    val originalFile = File(filename)
    val name = originalFile.nameWithoutExtension
    val extension = if (originalFile.extension.isNotBlank()) "." + originalFile.extension else ""
    var suffix: Int? = null
    fun newFilename(): String =
        name + (suffix ?: "") + extension
    while (File(newFilename()).exists()) {
        if (suffix == null)
            suffix = 1
        else
            suffix += 1
    }
    val file = File(newFilename())
    file.createNewFile()
    file.bufferedWriter().use { out ->
        content.lines().forEach { line ->
            out.write(line)
            out.newLine()
        }
    }
}

@Composable
actual fun SaveFileButton(
    iconPainter: Painter,
    contentDescription: String,
    saveDataProvider: () -> SaveData,
    onSaved: (successful: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    IconButton(onClick = {
        coroutineScope.launch(Dispatchers.IO) {
            val saveData = saveDataProvider()
            try {
                saveTextFile(saveData.content, saveData.filename)
                onSaved(true)
            } catch (e: IOException) {
                onSaved(false)
            }
        }
    }) {
        Icon(iconPainter, contentDescription)
    }
}