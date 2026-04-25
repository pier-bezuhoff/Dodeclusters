package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density

@Immutable
data class AdaptiveSizing(
    val windowSizeClass: WindowSizeClass,
) {
    // (Medium, Medium) is the size in portrait tablet browser
    val isLandscape =
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
        windowSizeClass.heightSizeClass <= WindowHeightSizeClass.Expanded ||
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium &&
        windowSizeClass.heightSizeClass < WindowHeightSizeClass.Medium
    /** Either of dimensions is compact */
    val isCompact =
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact ||
        windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    val isCompactVertically =
        windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    /** Both dimensions are medium */
    val isMedium =
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium &&
        windowSizeClass.heightSizeClass == WindowHeightSizeClass.Medium
    /** Both dimensions are expanded */
    val isExpanded =
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
        windowSizeClass.heightSizeClass == WindowHeightSizeClass.Expanded
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
val LocalAdaptiveSizing = staticCompositionLocalOf {
    AdaptiveSizing(
        WindowSizeClass.calculateFromSize(
            Size(1f, 1f),
            Density(1f),
        )
    )
}

val MaterialTheme.adaptiveSizing: AdaptiveSizing
    @Composable
    @ReadOnlyComposable
    get() =
        LocalAdaptiveSizing.current
