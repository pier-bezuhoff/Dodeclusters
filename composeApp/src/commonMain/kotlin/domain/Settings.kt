package domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ui.edit_cluster.dialogs.DefaultBiInversionParameters
import ui.edit_cluster.dialogs.DefaultInterpolationParameters
import ui.edit_cluster.dialogs.DefaultLoxodromicMotionParameters
import ui.edit_cluster.dialogs.DefaultRotationParameters

/**
 * [savedColors] user-defined & saved in the color picker as part of [ColorPickerParameters]
 */
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
    // upscaling factor
    val regionsOpacity: Float = 1f,
    val regionsBlendModeType: BlendModeType = BlendModeType.SRC_OVER,
    // default tools for categories
    val savedColors: List<ColorAsCss> = emptyList(),
    val defaultInterpolationParameters: DefaultInterpolationParameters = DefaultInterpolationParameters(),
    val defaultRotationParameters: DefaultRotationParameters = DefaultRotationParameters(),
    val defaultBiInversionParameters: DefaultBiInversionParameters = DefaultBiInversionParameters(),
    val defaultLoxodromicMotionParameters: DefaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(),
) {
    companion object {
        val JSON_FORMAT = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}