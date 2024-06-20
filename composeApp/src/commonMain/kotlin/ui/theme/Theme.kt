package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun DodeclustersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (false) dodeclustersDarkScheme else dodeclustersLightScheme
    MaterialTheme(
        colors = colors,
        content = content
    )
}