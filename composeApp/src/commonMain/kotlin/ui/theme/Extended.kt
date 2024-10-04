package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** custom colors in addition to standard m3 */
@Immutable
data class ExtendedColorScheme(
    val accentColor: Color,
    val highAccentColor: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColorScheme(
        accentColor = Color.Unspecified,
        highAccentColor = Color.Unspecified
    )
}

val MaterialTheme.extendedColorScheme: ExtendedColorScheme
    @Composable
    @ReadOnlyComposable
    get() =
        LocalExtendedColors.current