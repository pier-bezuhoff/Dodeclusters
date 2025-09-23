package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit

@Immutable
data class AdaptiveTypography(
    val actionButtonFontSize: TextUnit,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
)

val LocalAdaptiveTypography = staticCompositionLocalOf {
    AdaptiveTypography(
        actionButtonFontSize = TextUnit.Unspecified,
        title = TextStyle.Default,
        body = TextStyle.Default,
        label = TextStyle.Default,
    )
}

val MaterialTheme.adaptiveTypography: AdaptiveTypography
    @Composable
    @ReadOnlyComposable
    get() =
        LocalAdaptiveTypography.current
