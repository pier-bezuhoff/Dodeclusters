import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
//    val icon = painterResource("icon-256.png") // for w/e reason looks awful
    val icon = painterResource("icon-128.svg") // for w/e reason looks awful
    Window(
        state = windowState,
        onCloseRequest = ::exitApplication,
        title = "Dodeclusters",
        icon = icon,
    ) {
        App()
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}