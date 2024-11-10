import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import kotlinx.browser.document
import kotlinx.coroutines.flow.MutableSharedFlow
import org.w3c.dom.get
import org.w3c.dom.url.URL
import ui.LifecycleEvent
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
    val lifecycleEvents: MutableSharedFlow<LifecycleEvent> = MutableSharedFlow(replay = 1)
    document.addEventListener("visibilitychange") {
        if (document["hidden"] == true.toJsBoolean()) {
            lifecycleEvents.tryEmit(LifecycleEvent.SaveUIState)
        }
    }
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        App(
            sampleName = sampleName,
            colorTheme = colorTheme,
            lifecycleEvents = lifecycleEvents,
        )
    }
}

fun getCurrentURL(): URL =
    js("new URL(window.location.href)")

fun alert(): Unit =
    js("alert()")