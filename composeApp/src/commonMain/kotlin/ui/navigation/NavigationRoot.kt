package ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import domain.LoadingState
import domain.io.DdcSharing
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer
import ui.LifecycleEvent
import ui.editor.EditorScreen
import ui.editor.KeyboardAction
import ui.settings.SettingsScreenRoot

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun NavigationRoot(
    titleFlow: MutableStateFlow<String> = MutableStateFlow("Dodeclusters"),
    ddcFlow: SharedFlow<LoadingState<String>?> = MutableSharedFlow(),
    keyboardActions: SharedFlow<KeyboardAction>? = null,
    lifecycleEvents: SharedFlow<LifecycleEvent> = MutableSharedFlow(),
    ddcSharing: DdcSharing? = null,
) {
    // this way of init preserves my sealed interface type
    val backStack: NavBackStack<Route> = rememberSerializable(serializer = serializer()) {
        NavBackStack(Route.Editor)
    }
    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Route.Editor> {
                EditorScreen(
                    openSettings = {
                        backStack.add(Route.Settings)
                    },
                    ddcFlow = ddcFlow,
                    keyboardActions = keyboardActions,
                    lifecycleEvents = lifecycleEvents,
                    ddcSharing = ddcSharing,
                )
            }
            entry<Route.Settings> {
                SettingsScreenRoot(
                    close = {
                        backStack.removeLastOrNull()
                    },
                )
            }
        }
    )
}