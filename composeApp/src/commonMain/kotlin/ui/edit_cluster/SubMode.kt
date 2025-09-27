package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import core.geometry.CircleOrLine
import core.geometry.Point
import domain.Ix
import domain.cluster.LogicalRegion
import domain.expressions.Expr
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Immutable
@Serializable
/** Additional mode accompanying [Mode] and
 * carrying [SubMode]-specific relevant data, also
 * they have specific behavior for VM.[onPanZoom] */
sealed interface SubMode {
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

    /**
     * Rotate domain-sphere of stereographic projection
     * (the plane-cuts-thru-sphere-center type). All coordinates are _absolute_
     * @param[grabbedTarget] the point offset with grabbed to drag around
     * @param[south] (absolute) original screen center (projection of sphere's south)
     * @param[grid] regularly spaced lines of
     * constant latitude and longitude, after the rotation is applied to them
     */
    data class RotateStereographicSphere(
        val sphereRadius: Double,
        val grabbedTarget: Offset,
        val south: Point,
        val grid: List<CircleOrLine>,
    ) : SubMode {
        companion object {
            /** in degrees */
            const val GRID_ANGLE_STEP = 15
            // NOTE: equator index depends on angle step
            /** [grid]`[it]` is the equator */
            const val EQUATOR_GRID_INDEX = 5
        }
    }

    // generic type E doesn't actually enforce anything...
    /** Sub-mode accompanying [ToolMode], that allows live adjustment of expression parameters.
     * Can affect many [adjustables] = [Expr]s of same type, by varying parameters of each of them.
     * All expressions must be of the same type.
     * @property[adjustables] non-empty list of [Expr]s together with occupied and
     * reserved output indices for each
     * @property[regions] indices of regions resulted from the [adjustables] expressions, this
     * number is generally divisible by the n-steps parameter
     * @property[parameters] should be the same for all [adjustables]
     */
    data class ExprAdjustment(
        val adjustables: List<AdjustableExpr>, // non-empty
        val regions: List<Ix> = emptyList(),
    ) : SubMode { // allow it in Drag mode

        @Transient
        val parameters = (adjustables[0].expr as? Expr.HasParameters)?.parameters

        init {
            require(
                adjustables.isNotEmpty() &&
                adjustables[0].expr.let { expr0 ->
                    adjustables.all { expr0::class == it.expr::class }
                }
            ) { "Invalid adjustables $adjustables" }
        }
    }
}

/** Adjustable [expr] with indices that are occupied by its outputs.
 * @property[outputIndices] indices containing outputs of the multi [expr] we are adjusting
 * @property[reservedIndices] all indices reserved for the [expr],
 * including [outputIndices] and additional `null`ed indices that
 * were previously allocated for this multi [expr]
 */
@Immutable
data class AdjustableExpr(
    val expr: Expr,
    val outputIndices: List<Ix>,
    val reservedIndices: List<Ix>,
)