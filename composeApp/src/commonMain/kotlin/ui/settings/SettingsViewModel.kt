package ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import domain.settings.Settings
import getPlatform
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.extensions.cached
import io.github.xxfast.kstore.utils.ExperimentalKStoreApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalKStoreApi::class)
class SettingsViewModel : ViewModel() {

    private fun <T> Flow<T>.stateInWhileSubscribed(initialValue: T): StateFlow<T> =
        stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue
        )

    val settingsStore: KStore<Settings> = getPlatform().settingsStore
    val settingsFlow: StateFlow<Settings> = settingsStore.updates
        .map { it ?: Settings() }
        .stateInWhileSubscribed(settingsStore.cached ?: Settings())

    inline fun setSetting(crossinline update: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update {
                update(it ?: Settings())
            }
        }
    }

    fun resetSettings() {
        val defaultSettings = Settings()
        setSetting {
            it.copy(
                colorTheme = defaultSettings.colorTheme,
                inversionOfControl = defaultSettings.inversionOfControl,
                enableTangentSnapping = defaultSettings.enableTangentSnapping,
                enableAngleSnapping = defaultSettings.enableAngleSnapping,
            )
        }
    }
}