import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import domain.LoadingState
import domain.io.DdcSharing
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import ui.LifecycleEvent
import ui.editor.KeyboardAction
import ui.navigation.NavigationRoot
import ui.theme.DodeclustersTheme

/**
 * @param[ddcFlow] external ddc requests (url params or android implicit intent)
 * @param[titleFlow] controls window title
 * @param[keyboardActions] used to pipe keyboard events, null means they will be caught using
 * `Modifier.onPreviewKeyEvent
 * @param[lifecycleEvents] emits SaveUiState events that prompt to autosave the current state,
 * mechanism, analogous to SavedStateHand
 * @param[ddcSharing] state-backed ddc-sharing implementation, presently only
 * supplied on Wasm after the request to register current user is answered
 * (null -> smol delay -> real implementation)
 */
@Composable
fun App(
    titleFlow: MutableStateFlow<String> = MutableStateFlow("Dodeclusters"),
    ddcFlow: SharedFlow<LoadingState<String>?> = MutableSharedFlow(),
    keyboardActions: SharedFlow<KeyboardAction>? = null,
    lifecycleEvents: SharedFlow<LifecycleEvent> = MutableSharedFlow(),
    ddcSharing: DdcSharing? = null,
) {
    val colorTheme by getPlatform().colorThemeAsState()
    DodeclustersTheme(colorTheme) {
        NavigationRoot(
            titleFlow = titleFlow,
            ddcFlow = ddcFlow,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
            ddcSharing = ddcSharing,
        )
    }
}