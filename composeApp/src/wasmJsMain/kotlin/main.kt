@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import domain.LoadingState
import domain.io.DdcRepository
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.get
import org.w3c.dom.url.URL
import ui.LifecycleEvent
import ui.editor.KeyboardAction
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME

// NOTE: because Github Pages serves .wasm files with wrong mime type https://stackoverflow.com/a/54320709/7143065
//  to open in mobile/firefox use netlify version
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // example:
    // https://pier-bezuhoff.github.io/Dodeclusters?theme=dark&sample=apollonius
    val url = URL(window.location.href)
    val colorTheme: ColorTheme = when (url.searchParams.get("theme")?.lowercase()) {
        "light" -> ColorTheme.LIGHT
        "dark" -> ColorTheme.DARK
        "auto" -> ColorTheme.AUTO
        else -> DEFAULT_COLOR_THEME
    }
    val sharedId: String? = url.searchParams.get("shared")
    val sampleName: String? = url.searchParams.get("sample")
    val lifecycleEvents: MutableSharedFlow<LifecycleEvent> = MutableSharedFlow(replay = 1)
    document.addEventListener("visibilitychange") {
        if (document["hidden"] == true.toJsBoolean()) {
            lifecycleEvents.tryEmit(LifecycleEvent.SaveUIState)
        }
    }
    val coroutineScope = CoroutineScope(Dispatchers.Default)
    val themeFlow = MutableStateFlow<ColorTheme>(colorTheme)
    val keyboardActions: MutableSharedFlow<KeyboardAction> = MutableSharedFlow(replay = 1)
    document.addEventListener("keydown") { event: Event ->
        (event as? KeyboardEvent)?.let { keyboardEvent ->
            val action = WebKeyboardActionMapping.event2action(keyboardEvent)
            if (action != null) {
                event.stopPropagation()
                coroutineScope.launch {
                    keyboardActions.emit(action)
                }
            }
        }
    }
    // cleanup plain text left as a placeholder/for search engines
    val loadingSpinner = document.getElementById("loading")
    loadingSpinner?.setAttribute("style", "display: none;")
    loadingSpinner?.remove()
    document.querySelector("h2")?.setAttribute("style", "display: none;")
    document.querySelector("h1")?.setAttribute("style", "display: none;")
    ComposeViewport(
        viewportContainerId = "compose-root",
        configure = {
            isA11YEnabled = false // for performance
        }
    ) {
        val sharedDdcContentState: State<LoadingState<String>>? = sharedId?.let {
            val message = "Loading shared cluster '$sharedId'..."
            produceState<LoadingState<String>>(LoadingState.InProgress(message), key1 = sharedId) {
                val ddcContent = fetchSharedDdc(sharedId)
                println("fetched shared ddc $sharedId")
                value = if (ddcContent == null)
                    LoadingState.Error(Error("Fetching shared resource '$sharedId' failed"))
                else
                    LoadingState.Completed(ddcContent)
            }
        }
        val sampleDdcContentState: State<LoadingState<String>>? = sampleName?.let {
            val message = "Loading sample cluster '$sampleName'..."
            produceState<LoadingState<String>>(LoadingState.InProgress(message), key1 = sampleName) {
                val ddcContent = DdcRepository.loadSampleClusterYaml(sampleName)
                println("loaded sample ddc $sampleName")
                value = if (ddcContent == null || true)
                    LoadingState.Error(Error("No sample '$sampleName' found"))
                else
                    LoadingState.Completed(ddcContent)
            }
        }
        App(
            ddcContent = sharedDdcContentState?.value ?: sampleDdcContentState?.value,
            themeFlow = themeFlow,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
        )
    }
}