import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import org.w3c.dom.url.URL
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // example:
    // https://pier-bezuhoff.github.io/Dodeclusters?theme=dark&sample=apollonius
    val url = getCurrentURL()
    val sampleName: String? = url.searchParams.get("sample")
    val colorTheme: ColorTheme = when (url.searchParams.get("theme")?.lowercase()) {
        "light" -> ColorTheme.LIGHT
        "dark" -> ColorTheme.DARK
        "auto" -> ColorTheme.AUTO
        else -> DEFAULT_COLOR_THEME
    }
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        App(sampleName = sampleName, colorTheme = colorTheme)
    }
}

fun getCurrentURL(): URL =
    js("new URL(window.location.href)")