package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

val DEFAULT_COLOR_THEME = ColorTheme.DARK

@Composable
fun DodeclustersTheme(
    colorTheme: ColorTheme = DEFAULT_COLOR_THEME,
    content: @Composable () -> Unit
) {
    val isLight = when (colorTheme) {
        ColorTheme.LIGHT -> true
        ColorTheme.DARK -> false
        ColorTheme.AUTO -> !isSystemInDarkTheme()
    }
    val scheme =
        if (isLight) DodeclustersColors.lightScheme
        else DodeclustersColors.darkScheme
    val extendedScheme =
        if (isLight) DodeclustersColors.extendedLightScheme
        else DodeclustersColors.extendedDarkScheme
    CompositionLocalProvider(LocalExtendedColors provides extendedScheme) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}

enum class ColorTheme {
    LIGHT, DARK,
    /** Automatically detect current system color theme */
    AUTO
}

