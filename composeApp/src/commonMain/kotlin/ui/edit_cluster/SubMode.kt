package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import domain.Ix
import domain.cluster.LogicalRegion
import domain.expressions.Expr
import kotlinx.serialization.Serializable

@Immutable
@Serializable
/** Additional mode accompanying [Mode] and
 * carrying [SubMode]-specific relevant data, also
 * they have specific behavior for VM.[onPanZoom] */
sealed interface SubMode {
    data object None : SubMode
    // center uses absolute positioning
    /** Scale via top-right selection rect handle */
    data class Scale(val center: Offset) : SubMode
    data class ScaleViaSlider(
        val center: Offset,
        val sliderPercentage: Float = 0.5f
    ) : SubMode
    data class Rotate(
        val center: Offset,
        val angle: Double = 0.0,
        val snappedAngle: Double = 0.0
    ) : SubMode

    data class RectangularSelect(
        val corner1: Offset? = null,
        val corner2: Offset? = null,
    ) : SubMode
    data class FlowSelect(
        val lastQualifiedRegion: LogicalRegion? = null
    ) : SubMode
    data class FlowFill(
        val lastQualifiedRegion: LogicalRegion? = null
    ) : SubMode

    /** Sub-mode accompanying [ToolMode], that allows live adjustment of expression parameters
     * @property[outputIndices] indices containing outputs of the multi expr we are adjusting
     * @property[maxOutputRange] all indices reserved for [expr],
     * contains [outputIndices] and additional `null`ed indices that
     * were previously allocated for this multi expr
     * */
    data class ExprAdjustment(
        val expr: Expr,
        val outputIndices: List<Ix>,
        val maxOutputRange: List<Ix>,
    ) : SubMode // maybe allow it in Drag mode
}