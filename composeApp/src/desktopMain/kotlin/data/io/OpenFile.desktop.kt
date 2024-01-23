package data.io

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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

// works on Linux, todo: test on Windows
@Composable
actual fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    onOpen: (content: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fileDialogIsOpen by remember { mutableStateOf(false) }
    IconButton(onClick = {
        fileDialogIsOpen = true
    }) {
        Icon(iconPainter, contentDescription)
    }
    if (fileDialogIsOpen) {
        FileDialog { directory, filename ->
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
private fun FileDialog(
    parent: Frame? = null,
    onCloseRequest: (directory: String?, filename: String?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(parent, "Choose a file", LOAD) {
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