package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import core.geometry.CircleOrLine
import core.geometry.GCircle
import core.geometry.Point
import domain.Ix
import domain.expressions.ArcPath
import domain.expressions.Expr
import domain.model.PartialArgList
import domain.model.RegionConstraints

@Immutable
/** Additional mode accompanying [Mode] and
 * carrying [Submode]-specific relevant data, also
 * they have specific behavior for VM.onPanZoom */
sealed interface Submode {
    sealed interface OnlyActiveWhenPressed : Submode
    sealed interface InputPopup : Submode

    // center uses absolute positioning
    /** Scale via top-right selection rect handle */
    data class Scale(val center: Offset) : Submode, OnlyActiveWhenPressed
    data class ScaleViaSlider(
        val center: Offset,
        val sliderPercentage: Float = 0.5f
    ) : Submode
    data class Rotate(
        val center: Offset,
        val angle: Double = 0.0,
        val snappedAngle: Double = 0.0
    ) : Submode

    data class RectangularSelect(
        val corner1: Offset? = null,
        val corner2: Offset? = null,
    ) : Submode
    data class FlowSelect(
        val lastConstraints: RegionConstraints? = null,
    ) : Submode
    data class FlowFill(
        val lastConstraints: RegionConstraints? = null,
    ) : Submode

    data class SelectionChoices(
        val choices: List<Choice>,
    ) : Submode {
        /** @property[objectOrArcPath] null means arc-path */
        @Immutable
        data class Choice(
            val index: Ix,
            val objectOrArcPath: GCircle?,
            val borderColor: Color?,
            val fillColor: Color?,
        )
    }

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
    ) : Submode {
        companion object {
            /** in degrees */
            const val GRID_ANGLE_STEP = 15
            // NOTE: equator index depends on angle step
            /** [grid]`[it]` is the equator */
            const val EQUATOR_GRID_INDEX = 5
        }
    }

    // generic type E doesn't actually enforce anything...
    /** Sub-mode accompanying [ToolMode] (or sometimes [SelectionMode.Drag] or [SelectionMode.Multiselect]),
     * that allows live adjustment of expression parameters.
     * Can affect many [adjustables] = [Expr]s of same type, by varying parameters of each of them.
     * All expressions must be of the same type.
     * @property[adjustables] non-empty list of [Expr]s together with occupied and
     * reserved output indices for each
     * @property[arcPathAdjustables] `adjustable.expr` here is a blueprint source arc-path,
     * with vertex/midpoint indices within [adjustables] point trajectories
     * @property[regions] indices of regions resulted from the
     * [adjustables] expressions, this number is generally divisible by the n-steps parameter
     * @property[parameters] should be shared by all [adjustables]
     */
    data class ExprAdjustment<EXPR : Expr>(
        val adjustables: List<AdjustableExpr<EXPR>>, // non-empty
        val arcPathAdjustables: List<AdjustableExpr<ArcPath>> = emptyList(),
        val regions: List<Int> = emptyList(),
    ) : Submode {
        val parameters = (adjustables[0].expr as? Expr.HasParameters)?.parameters

        init {
            require(
                adjustables.isNotEmpty() /* && adjustables[0].expr.let { expr0 ->
                    adjustables.all { expr0::class == it.expr::class }
                } */
            ) { "Invalid adjustables $adjustables" }
        }
    }

    data class GrabbedArcMidpoint(
        val arcPathIndex: Ix,
        val arcIndex: Int,
    ) : Submode

    data object LabelInput : InputPopup
    data object LineThicknessInput : InputPopup

    /** [Submode] of a [ToolMode] after it emitted a result and we may temporarily
     * want to drag it/change it properties */
    data class ToolResultPostprocessing(
        val resultIndices: List<Ix>,
    ) : Submode

    /** Temporary leave tool mode to choose a selection argument and then go back */
    data class ToolSelectionInput(
        val toolMode: ToolMode,
        val partialArgList: PartialArgList,
    )

}

/** Adjustable [expr] with indices that are occupied by its outputs.
 * @param[sourceIndex] index from which the style is copied onto the trajectory,
 * the original transformation target
 * @param[occupiedIndices] indices containing outputs of the multi [expr] we are adjusting
 * @param[reservedIndices] all indices reserved for the [expr],
 * including [occupiedIndices] and additional `null`ed indices that
 * were previously allocated for this multi [expr]
 */
@Immutable
data class AdjustableExpr<out EXPR : Expr>(
    val expr: EXPR,
    val sourceIndex: Ix,
    val occupiedIndices: List<Ix>,
    val reservedIndices: List<Ix>,
) {
    val size: Int get() = occupiedIndices.size
}