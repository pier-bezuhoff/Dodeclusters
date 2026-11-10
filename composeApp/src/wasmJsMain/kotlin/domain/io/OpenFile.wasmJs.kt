@file:OptIn(ExperimentalWasmJsInterop::class)

package domain.io

import androidx.compose.runtime.Composable
import domain.collectLatestWithLifecycle
import kotlinx.browser.document
import kotlinx.coroutines.flow.Flow
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
actual fun OpenFileRequests(
    lookupData: LookupData,
    openRequests: Flow<Unit>,
    onOpen: (content: String?, filename: String?) -> Unit,
) {
    openRequests.collectLatestWithLifecycle(lookupData) {
        onClick(lookupData, onOpen)
    }
}

private fun onClick(
    lookupData: LookupData,
    onOpen: (content: String?, filename: String?) -> Unit
) {
    queryFile(lookupData) { file ->
        file?.let {
            val reader = FileReader()
            reader.onload = {
                val content = reader.result?.toString()
                onOpen(content, file.name)
            }
            reader.readAsText(file, "UTF-8")
        } ?: onOpen(null, null)
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
