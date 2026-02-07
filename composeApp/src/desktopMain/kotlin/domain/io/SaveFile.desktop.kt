package domain.io

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.AwtWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.save_cluster_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

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
    var dialogIsOpen by remember { mutableStateOf(false) }
    var lastDir by remember { mutableStateOf<String?>(saveData.lastDir) }
    Button(
        onClick = {
            dialogIsOpen = true
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
    if (dialogIsOpen) {
        SaveFileDialog(
            defaultDir = lastDir,
            defaultFilename = saveData.filename,
            displayedExtensions = setOf(saveData.extension) + saveData.otherDisplayedExtensions
        ) { directory, filename ->
            dialogIsOpen = false
            coroutineScope.launch(Dispatchers.IO) {
                if (filename != null) {
                    if (directory != null)
                        lastDir = directory
                    val file = File(directory, filename)
                    try {
                        saveTextFile(saveData.prepareContent(file.nameWithoutExtension), file)
                        onSaved(SaveResult.Success(
                            filename = filename,
                            dir = lastDir,
                        ))
                    } catch (e: IOException) {
                        onSaved(SaveResult.Failure(
                            filename = filename,
                            dir = lastDir,
                            error = e.message,
                        ))
                    }
                } else {
                    onSaved(SaveResult.Cancelled(
                        dir = lastDir,
                    ))
                }
            }
        }
    }
    LaunchedEffect(saveRequests) {
        saveRequests?.collect {
            dialogIsOpen = true
        }
    }
}

fun saveTextFile(content: String, originalFile: File) {
    // NOTE: we want to allow overriding old files
//    val name = originalFile.nameWithoutExtension
//    val extension = if (originalFile.extension.isNotBlank()) "." + originalFile.extension else ""
//    var suffix: Int? = null
//    fun newFilename(): String =
//        name + (suffix ?: "") + extension
//    while (File(originalFile.parent, newFilename()).exists()) {
//        if (suffix == null)
//            suffix = 1
//        else
//            suffix += 1
//    }
//    val file = File(originalFile.parent, newFilename())
    val file = originalFile
    if (!file.exists())
        file.createNewFile()
    file.bufferedWriter().use { out ->
        content.lines().forEach { line ->
            out.write(line)
            out.newLine()
        }
    }
}

@Composable
fun SaveFileDialog(
    parent: Frame? = null,
    defaultDir: String? = null,
    defaultFilename: String,
    displayedExtensions: Set<String>,
    onCloseRequest: (directory: String?, filename: String?) -> Unit
) {
    val title = stringResource(Res.string.save_cluster_title)
    AwtWindow(
        create = {
            // MAYBE: use JFileChooser instead
            object : FileDialog(parent, title, SAVE) {
                init {
                    directory = defaultDir
                    file = defaultFilename
                    setFilenameFilter { dir, name ->
                        displayedExtensions.any { ext -> name.lowercase().endsWith(".$ext") }
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
}
