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
//        hijacked()
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

external interface JsFigure1 : JsAny

external interface JsCircle1 : JsFigure1 {
    val circle: Int?
    val x: Int?
}

external interface JsCluster1 : JsFigure1 {
    val cluster: JsArray<JsNumber>
    val circles: JsArray<JsNumber>
}

external interface MyObj : JsAny {
    val p1: Int
    val content: JsArray<JsFigure1>
}

fun hijacked() {
    println("heyo")
//    val json = load("[1,2,3]")
    val json = loadYaml("""
        p1: 1
        content:
        - circle: 0
          x: 5
        - cluster: [1,1]
          circles: []
    """.trimIndent())
    val x = json as MyObj
    val y = x.content[1] as JsCluster1
    val z = (0 until y.circles.length).map { y.circles[it] }
    println(z)

}

fun queryFile(callback: (file: File?) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
//    input.accept = "text/plain" // doesnt detect custom formats
//    input.accept = "*/*"
    input.onchange = { event ->
        val file = input.files?.get(0)
//        val file = extractFileFromEvent(event)
        callback(file)
    }
    input.setAttribute("style", "display: none") // this should work
    input.click()
    // NOTE: apparently js-yaml is already in the yarn.lock, try using it
}

fun extractFileFromEvent(event: Event): File =
    js("event.target.files[0]")
