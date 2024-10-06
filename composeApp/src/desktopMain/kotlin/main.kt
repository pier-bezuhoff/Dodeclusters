import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.icon_256
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import ui.edit_cluster.KeyboardAction
import ui.edit_cluster.keyboardActionsHandler

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
//    windowState.placement = WindowPlacement.Fullscreen
//    val icon = painterResource(Res.drawable.icon_128) // broken?
    val icon = painterResource(Res.drawable.icon_256) // looks fine
    val keyboardActions: MutableSharedFlow<KeyboardAction> = remember { MutableSharedFlow() }
    val keyboardActionsScope = rememberCoroutineScope()
    val handler = keyboardActionsHandler { action ->
        keyboardActionsScope.launch {
            keyboardActions.emit(action)
        }
    }
    Window(
        state = windowState,
        onCloseRequest = ::exitApplication,
        title = "Dodeclusters",
        icon = icon,
        onPreviewKeyEvent = handler,
    ) {
        App(
            keyboardActions = keyboardActions
        )
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}