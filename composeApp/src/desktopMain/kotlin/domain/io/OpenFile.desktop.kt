package domain.io

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.AwtWindow
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.open_file
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

// works on Linux, todo: test on Windows
@Composable
actual fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    lookupData: LookupData,
    modifier: Modifier,
    onOpen: (content: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fileDialogIsOpen by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            fileDialogIsOpen = true
        },
        modifier = modifier
    ) {
        Icon(iconPainter, contentDescription, modifier)
    }
    if (fileDialogIsOpen) {
        LoadFileDialog(lookupData) { directory, filename ->
            fileDialogIsOpen = false
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val content = filename?.let {
                        val file =
                            if (directory == null)
                                File(filename)
                            else File(directory, filename)
                        file.bufferedReader().use { reader ->
                            reader.readText()
                        }
                    }
                    onOpen(content)
                } catch (e: IOException) {
                    onOpen(null)
                }
            }
        }
    }
}

@Composable
private fun LoadFileDialog(
    lookupData: LookupData,
    parent: Frame? = null,
    onCloseRequest: (directory: String?, filename: String?) -> Unit
) {
    val title = stringResource(Res.string.open_file)
    AwtWindow(
        create = {
            object : FileDialog(parent, title, LOAD) {
                init {
                    setFilenameFilter { dir, name ->
                        lookupData.extensions.any { extension ->
                            name.endsWith(".$extension")
                        }
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