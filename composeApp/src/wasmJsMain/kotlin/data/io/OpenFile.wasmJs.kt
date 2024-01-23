package data.io

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
actual fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    onOpen: (content: String?) -> Unit
) {
    IconButton(onClick = {
        queryFile { file ->
            file?.let {
                val reader = FileReader()
                reader.readAsText(file, "UTF-8")
                reader.onload = {
                    val content = reader.result?.toString()
                    onOpen(content)
                }
            } ?: onOpen(null)
        }
    }) {
        Icon(iconPainter, contentDescription)
    }
}

fun queryFile(callback: (file: File?) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "text/plain"
    input.onchange = { event ->
        val file = input.files?.get(0)
//        val file = extractFileFromEvent(event)
        callback(file)
    }
//    input.style = "display: none" // no idea how to assign css lol
    input.click()
}

fun extractFileFromEvent(event: Event): File =
    js("event.target.files[0]")