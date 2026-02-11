import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import domain.LoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import ui.LifecycleEvent
import ui.editor.EditorScreen
import ui.editor.KeyboardAction
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME
import ui.theme.DodeclustersTheme

@Composable
fun App(
    ddcContent: LoadingState<String>? = null,
    themeFlow: MutableStateFlow<ColorTheme> = MutableStateFlow(DEFAULT_COLOR_THEME),
    keyboardActions: SharedFlow<KeyboardAction>? = null,
    lifecycleEvents: SharedFlow<LifecycleEvent>? = null,
) {
    val colorTheme by themeFlow.collectAsStateWithLifecycle()
    DodeclustersTheme(colorTheme) {
        EditorScreen(
            ddcContent = ddcContent,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
        )
    }
}