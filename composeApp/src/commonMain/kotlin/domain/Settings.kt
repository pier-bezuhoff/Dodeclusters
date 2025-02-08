package domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Settings(
//    val fastCenteredCircles: Boolean = true,
//    val enableAngleSnapping: Boolean = true,
//    val restoreLastSaveOnLoad: Boolean = true,
//    val showDirectionArrowsOnSelectedCircles: Boolean = false,
//    val showImaginaryCircles: Boolean = true,
//    val inversionOfControl: InversionOfControl = InversionOfControl.LEVEL_1,
//    val alwaysCreateAdditionalPoints: Boolean = false,
    val regionsTransparency: Float = 1f,
    val regionsBlendModeType: BlendModeType = BlendModeType.SRC_OVER,
    // default tools for categories
)