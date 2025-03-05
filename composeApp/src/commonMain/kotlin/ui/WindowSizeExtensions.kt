package ui

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

val WindowSizeClass.isLandscape: Boolean get() =
    widthSizeClass == WindowWidthSizeClass.Expanded && heightSizeClass <= WindowHeightSizeClass.Expanded ||
    widthSizeClass == WindowWidthSizeClass.Medium && heightSizeClass < WindowHeightSizeClass.Medium
// (Medium, Medium) is the size in portrait tablet browser

/** Both dimensions are expanded */
val WindowSizeClass.isExpanded: Boolean get() =
    widthSizeClass == WindowWidthSizeClass.Expanded &&
    heightSizeClass == WindowHeightSizeClass.Expanded

/** Either of dimensions is compact */
val WindowSizeClass.isCompact: Boolean get() =
    widthSizeClass == WindowWidthSizeClass.Compact ||
    heightSizeClass == WindowHeightSizeClass.Compact

operator fun WindowSizeClass.component1(): WindowWidthSizeClass =
    widthSizeClass

operator fun WindowSizeClass.component2(): WindowHeightSizeClass =
    heightSizeClass
