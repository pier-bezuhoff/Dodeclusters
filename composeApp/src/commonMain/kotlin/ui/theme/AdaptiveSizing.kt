package ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

// potentially use BoxWithConstraints for fine-grained control over
// layout depending on the available size
/**
 * Both width and height are classified each in 3 groups: compact < medium < expanded
 */
@Immutable
data class AdaptiveSizing(
    val windowSizeClass: WindowSizeClass,
) {
    // (Medium, Medium) is the size in portrait tablet browser
    /** Distinguishes portrait and landscape using bounds (not straightforward) */
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

    val hudButtonModifier =
        if (isCompact)
            Modifier
                .padding(4.dp)
                .size(30.dp)
        else
            Modifier
                .padding(8.dp)
                .size(36.dp)
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
