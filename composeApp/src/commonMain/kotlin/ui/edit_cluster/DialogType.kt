package ui.edit_cluster

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
enum class DialogType {
    REGION_COLOR_PICKER,
    CIRCLE_COLOR_PICKER,
    CIRCLE_INTERPOLATION,
    CIRCLE_EXTRAPOLATION,
    LOXODROMIC_MOTION,
}