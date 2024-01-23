package data.io

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

@Composable
actual fun SaveFileButton(
    iconPainter: Painter,
    contentDescription: String,
    saveDataProvider: () -> SaveData,
    onSaved: (successful: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    IconButton(onClick = {
        coroutineScope.launch {
            val saveData = saveDataProvider()
            try {
                downloadTextFile3(saveData.filename, saveData.content)
                onSaved(true)
            } catch (e: Exception) {
                onSaved(false)
            }
        }
    }) {
        Icon(iconPainter, contentDescription)
    }
}

// global js function
external fun encodeURIComponent(str: String): String

// saves as "download"
fun downloadTextFile1(content: String) {
    val contentType = "data:application/octet-stream"
    val uriContent = contentType + "," + encodeURIComponent(content)
    val newWindow = window.open(uriContent, "New document")
}

// saves as "download"
fun downloadTextFile2(content: String) {
    val contentType = "data:application/octet-stream"
    val uriContent = contentType + "," + encodeURIComponent(content)
    window.location.href = uriContent
}

// saves properly with given filename
fun downloadTextFile3(content: String, filename: String) {
    val blobContent = JsArray<JsAny?>()
    blobContent[0] = content.toJsString()
    val file = Blob(blobContent, BlobPropertyBag("text/plain"))
    (document.createElement("a") as? HTMLAnchorElement)?.let { a ->
        val url = URL.Companion.createObjectURL(file)
        a.href = url
        a.download = filename
        document.body?.appendChild(a)
        a.click()
        document.body?.removeChild(a)
        URL.revokeObjectURL(url)
    }
}

// saves properly with given filename
fun downloadTextFile4(content: String, filename: String) {
    val contentType = "data:application/octet-stream"
    val uriContent = contentType + ";charset=utf-8," + encodeURIComponent(content)
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = uriContent
    a.download = filename
    (document.createEvent("MouseEvent") as? Event)?.let { event ->
        event.initEvent("click", bubbles = true, cancelable = true)
        a.dispatchEvent(event)
    } ?: run {
        a.click()
    }
}