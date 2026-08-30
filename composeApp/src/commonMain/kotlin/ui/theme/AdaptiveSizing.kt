package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.window.core.layout.WindowSizeClass

@Immutable
data class AdaptiveSizing(
    val windowSizeClass: WindowSizeClass,
) {
    // (Medium, Medium) is the size in portrait tablet browser
    val isLandscape =
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND <= windowSizeClass.minWidthDp &&
        windowSizeClass.minHeightDp <= WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND ||
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND <= windowSizeClass.minWidthDp &&
        windowSizeClass.minWidthDp < WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND &&
        windowSizeClass.minHeightDp < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    /** Either of dimensions is compact */
    val isCompact =
        windowSizeClass.minWidthDp < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND ||
        windowSizeClass.minHeightDp < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    val isCompactVertically =
        windowSizeClass.minHeightDp < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    /** Both dimensions are medium */
    val isMedium =
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND <= windowSizeClass.minWidthDp &&
        windowSizeClass.minWidthDp < WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND &&
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND <= windowSizeClass.minHeightDp &&
        windowSizeClass.minHeightDp < WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND
    /** Both dimensions are expanded */
    val isExpanded =
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND <= windowSizeClass.minWidthDp &&
        WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND <= windowSizeClass.minHeightDp
    val isExpandedHorizontally =
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND <= windowSizeClass.minWidthDp
}

val LocalAdaptiveSizing = staticCompositionLocalOf {
    AdaptiveSizing(
        WindowSizeClass(1, 1)
    )
}

val MaterialTheme.adaptiveSizing: AdaptiveSizing
    @Composable
    @ReadOnlyComposable
    get() =
        LocalAdaptiveSizing.current
