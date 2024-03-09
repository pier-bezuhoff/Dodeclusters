import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.icon_256
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
//    val icon = painterResource(Res.drawable.icon_128) // broken?
    val icon = painterResource(Res.drawable.icon_256) // looks fine
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