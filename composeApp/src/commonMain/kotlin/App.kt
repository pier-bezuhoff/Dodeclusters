import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.SharedFlow
import ui.LifecycleEvent
import ui.edit_cluster.EditClusterScreen
import ui.edit_cluster.KeyboardAction
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME
import ui.theme.DodeclustersTheme

@Composable
fun App(
    sampleName: String? = null,
    ddcContent: String? = null,
    colorTheme: ColorTheme = DEFAULT_COLOR_THEME,
    keyboardActions: SharedFlow<KeyboardAction>? = null,
    lifecycleEvents: SharedFlow<LifecycleEvent>? = null,
) {
    DodeclustersTheme(colorTheme) {
        EditClusterScreen(
            sampleName = sampleName,
            ddcContent = ddcContent,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
        )
    }
}