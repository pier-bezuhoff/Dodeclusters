import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.icon_256
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import ui.LifecycleEvent
import ui.editor.KeyboardAction
import ui.editor.KeyboardActionMapping
import kotlin.time.Duration.Companion.milliseconds

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
//    windowState.placement = WindowPlacement.Fullscreen
//    val icon = painterResource(Res.drawable.icon_128) // broken?
    val icon = painterResource(Res.drawable.icon_256) // looks fine
    val titleFlow: MutableStateFlow<String> = MutableStateFlow("Dodeclusters")
    val title: String by titleFlow.collectAsState(initial = "Dodeclusters")
    val keyboardActions: MutableSharedFlow<KeyboardAction> = remember { MutableSharedFlow() }
    val keyboardActionsScope = rememberCoroutineScope()
    val keyEventHandler = KeyboardActionMapping.Default.keyEventHandler { action ->
        keyboardActionsScope.launch {
            keyboardActions.emit(action)
        }
    }
    val lifecycleEvents: MutableSharedFlow<LifecycleEvent> = MutableSharedFlow(replay = 1)
    Window(
        onCloseRequest = {
            runBlocking {
                lifecycleEvents.emit(LifecycleEvent.SaveUIState)
                // we forcefully allot some time to save state
                delay(100.milliseconds)
            }
            exitApplication()
        },
        state = windowState,
        title = title,
        icon = icon,
        onPreviewKeyEvent = keyEventHandler,
    ) {
        App(
            titleFlow = titleFlow,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
        )
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}