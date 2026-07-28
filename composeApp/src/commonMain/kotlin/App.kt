import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import domain.LoadingState
import domain.io.DdcSharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import ui.LifecycleEvent
import ui.editor.KeyboardAction
import ui.navigation.NavigationRoot
import ui.theme.DEFAULT_COLOR_THEME
import ui.theme.DodeclustersTheme

@Composable
fun App(
    ddcContent: LoadingState<String>? = null,
    titleFlow: MutableStateFlow<String> = MutableStateFlow("Dodeclusters"),
    keyboardActions: SharedFlow<KeyboardAction>? = null,
    lifecycleEvents: SharedFlow<LifecycleEvent>? = null,
    ddcSharing: DdcSharing? = null,
) {
    val colorTheme by getPlatform().colorThemeAsState()
    DodeclustersTheme(colorTheme) {
        NavigationRoot(
            ddcContent = ddcContent,
            titleFlow = titleFlow,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
            ddcSharing = ddcSharing,
        )
    }
}