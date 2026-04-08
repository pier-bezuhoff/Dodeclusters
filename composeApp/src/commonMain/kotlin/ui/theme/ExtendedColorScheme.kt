package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Custom colors in addition to standard m3
 * @param[selectionColor] blueish for selected circles/arc-paths
 * @param[creationColor] green for new objects
 * @param[copyingColor] blue for copying/duplicating
 * @param[deletionColor] red for deleting
 * @param[highlightColor] blue for highlighting parents and stuff
 * @param[imaginaryCircleColor] pink-red for imaginary cirles
 */
@Immutable
data class ExtendedColorScheme(
    val accentColor: Color,
    val highAccentColor: Color,
    val selectionColor: Color,
    val creationColor: Color,
    val copyingColor: Color,
    val deletionColor: Color,
    val highlightColor: Color,
    val imaginaryCircleColor: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
//    DodeclustersColors.extendedDarkScheme
    ExtendedColorScheme(
        accentColor = Color.Unspecified,
        highAccentColor = Color.Unspecified,
        selectionColor = Color.Unspecified,
        creationColor = Color.Unspecified,
        copyingColor = Color.Unspecified,
        deletionColor = Color.Unspecified,
        highlightColor = Color.Unspecified,
        imaginaryCircleColor = Color.Unspecified,
    )
}

val MaterialTheme.extendedColorScheme: ExtendedColorScheme
    @Composable
    @ReadOnlyComposable
    get() =
//    DodeclustersColors.extendedDarkScheme
        LocalExtendedColors.current
