package ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import domain.LoadingState
import domain.io.DdcSharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import ui.LifecycleEvent
import ui.editor.EditorScreen
import ui.editor.KeyboardAction
import ui.settings.SettingsScreenRoot
import ui.settings.SettingsViewModel
import ui.theme.ColorTheme

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun NavigationRoot(
    ddcContent: LoadingState<String>?,
    titleFlow: MutableStateFlow<String>,
    keyboardActions: SharedFlow<KeyboardAction>?,
    lifecycleEvents: SharedFlow<LifecycleEvent>?,
    ddcSharing: DdcSharing?,
) {
    val backStack = rememberNavBackStack(
        SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclassesOfSealed<Route>()
                }
            }
        },
        Route.Editor
    )
    // we init it here cuz otherwise settings screen would play an animation of
    // going from defaults to current values each time you open it
//    val settingsViewModel = viewModel { SettingsViewModel() }
    NavDisplay(
        backStack = backStack,
        onBack = {
            backStack.removeLastOrNull()
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Route.Editor> {
                EditorScreen(
                    ddcContent = ddcContent,
                    keyboardActions = keyboardActions,
                    lifecycleEvents = lifecycleEvents,
                    ddcSharing = ddcSharing,
                    openSettings = {
                        backStack.add(Route.Settings)
                    },
                )
            }
            entry<Route.Settings> {
                SettingsScreenRoot(
                    close = {
                        backStack.removeLastOrNull()
                    },
//                    viewModel = settingsViewModel,
                )
            }
        }
    )
}