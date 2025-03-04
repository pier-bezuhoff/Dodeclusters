package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

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
    MaterialTheme.shapes.small
    CompositionLocalProvider(LocalExtendedColors provides extendedScheme) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = MaterialTheme.shapes.copy(
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(24.dp), // default is 28dp it appears
            ),
            content = content
        )
    }
}

@Immutable
enum class ColorTheme {
    LIGHT, DARK,
    /** Automatically detect current system color theme */
    AUTO
}

