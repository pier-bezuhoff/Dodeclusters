package data.io

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.AwtWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

@Composable
actual fun SaveFileButton(
    iconPainter: Painter,
    contentDescription: String,
    saveDataProvider: () -> SaveData,
    onSaved: (successful: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fileDialogIsOpen by remember { mutableStateOf(false) }
    var lastDir by remember { mutableStateOf<String?>(null) }
    IconButton(onClick = {
        fileDialogIsOpen = true
    }) {
        Icon(iconPainter, contentDescription)
    }
    if (fileDialogIsOpen) {
        val saveData = saveDataProvider()
        SaveFileDialog(
            defaultDir = lastDir,
            defaultFilename = saveData.filename,
        ) { directory, filename ->
            fileDialogIsOpen = false
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    if (filename != null) {
                        if (directory != null)
                            lastDir = directory
                        saveTextFile(saveData.content, File(directory, filename))
                        onSaved(true)
                    } else
                        onSaved(false)
                } catch (e: IOException) {
                    onSaved(false)
                }
            }
        }
    }
}

fun saveTextFile(content: String, originalFile: File) {
    val name = originalFile.nameWithoutExtension
    val extension = if (originalFile.extension.isNotBlank()) "." + originalFile.extension else ""
    var suffix: Int? = null
    fun newFilename(): String =
        name + (suffix ?: "") + extension
    while (File(originalFile.parent, newFilename()).exists()) {
        if (suffix == null)
            suffix = 1
        else
            suffix += 1
    }
    val file = File(originalFile.parent, newFilename())
    file.createNewFile()
    file.bufferedWriter().use { out ->
        content.lines().forEach { line ->
            out.write(line)
            out.newLine()
        }
    }
}

@Composable
private fun SaveFileDialog(
    parent: Frame? = null,
    defaultDir: String? = null,
    defaultFilename: String,
    onCloseRequest: (directory: String?, filename: String?) -> Unit
) = AwtWindow(
    create = {
        // MAYBE: use JFileChooser instead
        object : FileDialog(parent, "Save this cluster", SAVE) {
            init {
                directory = defaultDir
                file = defaultFilename
                setFilenameFilter { dir, name ->
                    name.endsWith(".ddc") || name.endsWith(".yml") || name.endsWith(".yaml")
                }
            }

            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    onCloseRequest(directory, file)
                }
            }
        }
    },
    dispose = FileDialog::dispose
)
