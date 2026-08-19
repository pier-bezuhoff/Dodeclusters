package ui.editor

import androidx.compose.runtime.Immutable

@Immutable
enum class ContextActions {
    NO,
    GENERIC_SELECTION,
    POINT,
    ARC_PATH,
    PARTIAL_ARC_PATH,
    REGION_FILL,
    INTERPOLATION,
    ROTATION,
    BI_INVERSION,
    LOXODROMIC_MOTION,
}

@Immutable
data class HudState(
    val contextActions: ContextActions,
    val showInfinitePointInput: Boolean,
    val noPhantomsSelected: Boolean,
    val showMovePointToInfinity: Boolean,
    val labelInputIsActive: Boolean,
    val lineThicknessInputIsActive: Boolean,
)
