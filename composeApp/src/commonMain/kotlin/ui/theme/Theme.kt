package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun DodeclustersTheme(
    colorTheme: ColorTheme = ColorTheme.DARK,
    content: @Composable () -> Unit
) {
    val scheme = when (colorTheme) {
        ColorTheme.LIGHT -> dodeclustersLightScheme
        ColorTheme.DARK -> dodeclustersDarkScheme
        ColorTheme.DEFAULT ->
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
    DEFAULT
}