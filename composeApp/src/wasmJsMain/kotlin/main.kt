import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import org.w3c.dom.url.URL

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val url = getCurrentURL()
    val sampleIndex: Int? = url.searchParams.get("sample")?.toIntOrNull()
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        App(sampleIndex = sampleIndex)
    }
}

fun getCurrentURL(): URL =
    js("new URL(window.location.href)")