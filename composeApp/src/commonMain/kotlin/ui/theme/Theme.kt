package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun DodeclustersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
//    val colors = if (false) dodeclustersDarkScheme else dodeclustersLightScheme
    val scheme = if (true) darkScheme else lightScheme
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}