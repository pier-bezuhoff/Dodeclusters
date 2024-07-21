package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

val DEFAULT_COLOR_THEME = ColorTheme.DARK

@Composable
fun DodeclustersTheme(
    colorTheme: ColorTheme = DEFAULT_COLOR_THEME,
    content: @Composable () -> Unit
) {
    val scheme = when (colorTheme) {
        ColorTheme.LIGHT -> dodeclustersLightScheme
        ColorTheme.DARK -> dodeclustersDarkScheme
        ColorTheme.AUTO ->
            if (isSystemInDarkTheme()) dodeclustersDarkScheme
            else dodeclustersLightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}

enum class ColorTheme {
    LIGHT, DARK,
    /** Automatically detect current system color theme */
    AUTO
}