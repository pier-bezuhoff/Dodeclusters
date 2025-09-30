import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.SharedFlow
import ui.LifecycleEvent
import ui.editor.EditorScreen
import ui.editor.KeyboardAction
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
        EditorScreen(
            sampleName = sampleName,
            ddcContent = ddcContent,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
        )
    }
}