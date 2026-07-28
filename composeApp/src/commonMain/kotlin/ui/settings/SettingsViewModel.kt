package ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import domain.LoadingState
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
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME

class SettingsViewModel : ViewModel() {

    private val settingsStore: KStore<Settings> = getPlatform().settingsStore
    val settingsFlow: Flow<Settings?> = settingsStore.updates
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalKStoreApi::class)
    val colorThemesDataFlow: StateFlow<List<ColorThemeData>> = settingsFlow
        .map { settings ->
            val colorTheme = settings?.colorTheme ?: DEFAULT_COLOR_THEME
            val colorThemesData = DEFAULT_COLOR_THEMES_DATA.map {
                it.copy(enabled = colorTheme == it.colorTheme)
            }
            colorThemesData
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            (settingsStore.cached?.colorTheme ?: DEFAULT_COLOR_THEME).let { colorTheme ->
                DEFAULT_COLOR_THEMES_DATA.map { it.copy(enabled = colorTheme == it.colorTheme) }
            }
        )

    private inline fun setSetting(crossinline update: (Settings) -> Settings) {
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
            )
        }
    }

    fun setColorTheme(colorTheme: ColorTheme) {
        setSetting {
            it.copy(colorTheme = colorTheme)
        }
        println("set color theme to $colorTheme")
    }

    companion object {
        val DEFAULT_COLOR_THEMES_DATA = listOf(
            ColorThemeData(DEFAULT_COLOR_THEME == ColorTheme.LIGHT, "Light", ColorTheme.LIGHT),
            ColorThemeData(DEFAULT_COLOR_THEME == ColorTheme.DARK, "Dark", ColorTheme.DARK),
            ColorThemeData(DEFAULT_COLOR_THEME == ColorTheme.AUTO, "Auto", ColorTheme.AUTO),
        )
    }
}