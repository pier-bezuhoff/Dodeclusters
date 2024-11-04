import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import org.w3c.dom.url.URL
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // example:
    // https://pier-bezuhoff.github.io/Dodeclusters?theme=dark#apollonius
    val url = getCurrentURL()
    // "?sample=0" etc
//    val sampleIndex: Int? = url.searchParams.get("sample")?.toIntOrNull()
    val hash = url.hash.trimStart('#')
    val colorTheme: ColorTheme = when (url.searchParams.get("theme")?.lowercase()) {
        "light" -> ColorTheme.LIGHT
        "dark" -> ColorTheme.DARK
        "auto" -> ColorTheme.AUTO
        else -> DEFAULT_COLOR_THEME
    }
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        App(sampleName = hash, colorTheme = colorTheme)
    }
}

fun getCurrentURL(): URL =
    js("new URL(window.location.href)")