@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.get
import org.w3c.dom.url.URL
import ui.LifecycleEvent
import ui.editor.KeyboardAction
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME
import kotlin.time.Duration.Companion.seconds

// NOTE: because Github Pages serves .wasm files with wrong mime type https://stackoverflow.com/a/54320709/7143065
//  to open in mobile/firefox use netlify version
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // example:
    // https://pier-bezuhoff.github.io/Dodeclusters?theme=dark&sample=apollonius
    val url = URL(window.location.href)
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
    val coroutineScope = CoroutineScope(Dispatchers.Default)
    val keyboardActions: MutableSharedFlow<KeyboardAction> = MutableSharedFlow(replay = 1)
    document.addEventListener("keydown") { event: Event ->
        (event as? KeyboardEvent)?.let { keyboardEvent ->
            keyboardEventTranslator(keyboardEvent)?.let { keyboardAction ->
                event.stopPropagation()
                coroutineScope.launch {
                    keyboardActions.emit(keyboardAction)
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
            isA11YEnabled = false
        }
    ) {
        App(
            sampleName = sampleName,
            colorTheme = colorTheme,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
        )
        // BUG: after pressing Tab or Delete the canvas gets focused
        //  with a distracting white outline
        //  and i cannot style it normally as it's in shadow dom
        //  canvas:focus { outline: 0px solid transparent; }
        //  It can be focused bc of the canvas 'tabindex' property
        //  Ticket: https://youtrack.jetbrains.com/issue/CMP-9040
        LaunchedEffect(Unit) { // hack to fix ^^^
            (document.getElementById("compose-root")
                ?.shadowRoot
                ?.querySelector("canvas") as? HTMLCanvasElement)
                ?.let { canvasElement ->
                    canvasElement.style.outline = "0px none transparent"
                }
        }
    }
}

private val UNDER_MAC = WasmPlatform.underlyingPlatform == UnderlyingPlatform.MAC

/** Mirrors [keyEventTranslator] from ui.editor.KeyboardAction.kt */
private fun keyboardEventTranslator(event: KeyboardEvent): KeyboardAction? =
    if (!event.altKey && !event.shiftKey) {
        // meta key is apparently macos equiv of ctrl, BUT is also Win on Windows/Linux
        if (event.ctrlKey || UNDER_MAC && event.metaKey) {
            // using KeyEvent.code is language-invariant
            // reference: https://developer.mozilla.org/en-US/docs/Web/API/KeyboardEvent/code
            when (event.code) {
                "KeyV" -> KeyboardAction.PASTE
                "KeyA" -> KeyboardAction.SELECT_ALL
                // these 2 don't work well with normal scaling
//                "Equal" -> KeyboardAction.ZOOM_IN
//                "Minus" -> KeyboardAction.ZOOM_OUT
                "KeyZ" -> KeyboardAction.UNDO
                "KeyY" -> KeyboardAction.REDO
                // Ctrl-S, Ctrl-O, Ctrl-N are not available in browser
                else -> null
            }

        } else {
            when (event.code) {
                "Delete", "Backspace" -> KeyboardAction.DELETE
                "Enter" -> KeyboardAction.CONFIRM
                "Escape" -> KeyboardAction.CANCEL
                "KeyO" -> KeyboardAction.OPEN
                "KeyS" -> KeyboardAction.SAVE
                "KeyM" -> KeyboardAction.MOVE
                "KeyL" -> KeyboardAction.SELECT
                "KeyR" -> KeyboardAction.REGION
                "KeyP" -> KeyboardAction.PALETTE
                "KeyT" -> KeyboardAction.TRANSFORM
                "KeyC" -> KeyboardAction.CREATE
                else -> null
            }
        }
    } else if (event.code == "Slash" && event.shiftKey)
        KeyboardAction.HELP
    else null

fun alert(message: String): Unit =
    js("alert(message)")