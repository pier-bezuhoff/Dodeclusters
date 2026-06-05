package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Immutable
data class CustomColors(
    val scaleIconColor: Color,
    val scaleIndicatorColor: Color,
    val rotateIconColor: Color,
    val rotationIndicatorColor: Color,
    val rotationHandleBackgroundColor: Color,
    val rotationHandleColor: Color,
    val defaultCircleColor: Color,
    val defaultFreeCircleColor: Color,
    val defaultPointColor: Color,
    val defaultSelectionColor: Color,
    val imaginaryCircleColor: Color,
    val selectionMarkingsColor: Color,
    val stereographicGridColor: Color,
    val defaultArcPathColor: Color,
    val arcMiddlePointColor: Color,
    val creationColor: Color,
    val copyingColor: Color,
    val deletionColor: Color,
    val highlightColor: Color,
    val defaultFreePointColor: Color,
    val selectedArgColor: Color,
) {
    companion object {
        const val thiccSelectedCircleAlpha = 0.9f
        const val thiccSelectedPathAlpha = 0.5f
    }
}

val ExtendedColorScheme.defaultCircleColor: Color get() = accentColor.copy(alpha = 0.6f)
val ExtendedColorScheme.defaultPointColor: Color get() = accentColor.copy(alpha = 0.7f)
val ExtendedColorScheme.defaultArcPathColor: Color get() = highAccentColor
val ExtendedColorScheme.defaultFreeCircleColor: Color get() = highAccentColor
val ExtendedColorScheme.defaultFreePointColor: Color get() = highAccentColor

val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = CustomColors(
        scaleIconColor = MaterialTheme.colorScheme.secondary,
        scaleIndicatorColor = MaterialTheme.extendedColorScheme.highlightColor,
        rotateIconColor = MaterialTheme.colorScheme.secondary,
        rotationIndicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 1.5f),
        rotationHandleBackgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        rotationHandleColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        // MAYBE: black/dark grey for light scheme,
        defaultCircleColor = MaterialTheme.extendedColorScheme.defaultCircleColor,
        defaultFreeCircleColor = MaterialTheme.extendedColorScheme.defaultFreeCircleColor,
        defaultPointColor = MaterialTheme.extendedColorScheme.defaultPointColor,
        defaultSelectionColor = MaterialTheme.extendedColorScheme.selectionColor,
        imaginaryCircleColor = MaterialTheme.extendedColorScheme.imaginaryCircleColor,
        selectionMarkingsColor = MaterialTheme.colorScheme.outline, // center-radius line / bounding rect of selection
        stereographicGridColor = MaterialTheme.colorScheme.secondary,
        defaultArcPathColor = MaterialTheme.extendedColorScheme.defaultArcPathColor,
        arcMiddlePointColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
        creationColor = MaterialTheme.extendedColorScheme.creationColor,
        copyingColor = MaterialTheme.extendedColorScheme.copyingColor,
        deletionColor = MaterialTheme.extendedColorScheme.deletionColor,
        highlightColor = MaterialTheme.extendedColorScheme.highlightColor,
        defaultFreePointColor = MaterialTheme.extendedColorScheme.defaultFreePointColor,
        selectedArgColor = MaterialTheme.extendedColorScheme.creationColor,
    )
