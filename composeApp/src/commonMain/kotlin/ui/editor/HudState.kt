package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
    val selectionIsLocked: Boolean,
    val someArcPathsAreClosed: Boolean,
    val showAdjustExprButton: Boolean,
    val showOrientationToggle: Boolean,
    val showMovePointToInfinity: Boolean,
    val labelInputIsActive: Boolean,
    val lineThicknessInputIsActive: Boolean,
    val mostCommonBorderColorOfSelection: Color?,
    val mostCommonFillColorOfSelection: Color?,
    val mostCommonLineThicknessOfSelection: Float?,
)
