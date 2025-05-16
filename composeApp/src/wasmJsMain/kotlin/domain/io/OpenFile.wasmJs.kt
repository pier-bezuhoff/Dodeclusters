package domain.io

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.browser.document
import kotlinx.coroutines.flow.SharedFlow
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
actual fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    lookupData: LookupData,
    modifier: Modifier,
    openRequests: SharedFlow<Unit>?,
    onOpen: (content: String?) -> Unit
) {
    IconButton(
        onClick = {
            onClick(lookupData, onOpen)
        },
        modifier = modifier
    ) {
        Icon(iconPainter, contentDescription, modifier)
    }
    LaunchedEffect(openRequests) {
        openRequests?.collect {
            onClick(lookupData, onOpen)
        }
    }
}

private fun onClick(
    lookupData: LookupData,
    onOpen: (content: String?) -> Unit
) {
    queryFile(lookupData) { file ->
        file?.let {
            val reader = FileReader()
            reader.onload = {
                val content = reader.result?.toString()
                onOpen(content)
            }
            reader.readAsText(file, "UTF-8")
        } ?: onOpen(null)
    }
}

fun queryFile(lookupData: LookupData, callback: (file: File?) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = lookupData.htmlFileInputAccept
    input.onchange = { event ->
        val file = input.files?.get(0)
//        val file = extractFileFromEvent(event)
        callback(file)
    }
    input.setAttribute("style", "display: none") // this should work
    input.click()
}

fun extractFileFromEvent(event: Event): File =
    js("event.target.files[0]")
