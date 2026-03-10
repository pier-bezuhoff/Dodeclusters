package ui.editor.dialogs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class DialogType {
    REGION_FILL_COLOR_PICKER,
    BORDER_COLOR_PICKER,
    FILL_COLOR_PICKER,
    BACKGROUND_COLOR_PICKER,
    CIRCLE_OR_POINT_INTERPOLATION,
    CIRCLE_EXTRAPOLATION,
    ROTATION,
    BI_INVERSION,
    LOXODROMIC_MOTION,
    SAVE_OPTIONS,
    BLEND_SETTINGS,
    LABEL_INPUT,
}