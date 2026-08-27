package ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import domain.settings.Settings
import domain.updatesStateFlow
import getPlatform
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.utils.ExperimentalKStoreApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalKStoreApi::class)
class SettingsViewModel : ViewModel() {

    val settingsStore: KStore<Settings> =
        getPlatform().settingsStore
    val settingsFlow: StateFlow<Settings> = settingsStore
        .updatesStateFlow(Settings())

    inline fun updateSetting(
        crossinline update: (Settings) -> Settings
    ) {
        viewModelScope.launch {
            settingsStore.update {
                update(it ?: Settings())
            }
        }
    }

    fun resetSettings() {
        val defaultSettings = Settings()
        updateSetting {
            it.copy(
                // NOTE: don't forget to manually add all adjustable settings
                colorTheme = defaultSettings.colorTheme,
                enablePeriodicAutosave = defaultSettings.enablePeriodicAutosave,
                autosavePeriodInSeconds = defaultSettings.autosavePeriodInSeconds,
                inversionOfControl = defaultSettings.inversionOfControl,
                enableTangentSnapping = defaultSettings.enableTangentSnapping,
                enableAngleSnapping = defaultSettings.enableAngleSnapping,
            )
        }
    }
}
