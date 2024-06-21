package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

@Composable
fun DodeclustersTheme(
    content: @Composable () -> Unit
) {
    val scheme = when (LocalColorTheme.current) {
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

val LocalColorTheme = compositionLocalOf { ColorTheme.DEFAULT }