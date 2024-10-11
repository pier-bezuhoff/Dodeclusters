package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.Cluster
import domain.Signature
import kotlinx.serialization.Serializable
import ui.tools.EditClusterTool
import kotlin.jvm.Transient

@Immutable
sealed interface Mode {
    fun isSelectingCircles(): Boolean =
        this == SelectionMode.Drag || this == SelectionMode.Multiselect
}

@Serializable
enum class SelectionMode : Mode {
    /** Select & drag singular circles */
    Drag,
    /** Select multiple circles */
    Multiselect,
    /** Select regions to create new [Cluster.Part]s */
    Region,
}

// presently unused
/** intersection-modes of [SelectionMode.Multiselect] related to how new selection is combined */
enum class MultiselectLogic {
    ADD, REPLACE, SUBTRACT, SYMMETRIC_DIFFERENCE,
}

// MAYBE: associate function(constants, variables) -> circles
//  with each tool and update the result when any variable changes
//  + potentially save these functional dependencies to ddc
@Serializable
enum class ToolMode(
    @Transient
    val tool: EditClusterTool.MultiArg
) : Mode {
    CIRCLE_INVERSION(EditClusterTool.CircleInversion),
    CIRCLE_INTERPOLATION(EditClusterTool.CircleInterpolation),
    CIRCLE_EXTRAPOLATION(EditClusterTool.CircleExtrapolation),
    LOXODROMIC_MOTION(EditClusterTool.LoxodromicMotion),

    CIRCLE_BY_CENTER_AND_RADIUS(EditClusterTool.ConstructCircleByCenterAndRadius),
    CIRCLE_BY_3_POINTS(EditClusterTool.ConstructCircleBy3Points),
    CIRCLE_BY_PENCIL_AND_POINT(EditClusterTool.ConstructCircleByPencilAndPoint),
    LINE_BY_2_POINTS(EditClusterTool.ConstructLineBy2Points),
    ARC_PATH(EditClusterTool.ConstructArcPath),
    POINT(EditClusterTool.AddPoint),
    ;

    val signature: Signature = tool.signature

    companion object {
        fun correspondingTo(tool: EditClusterTool.MultiArg): ToolMode =
            ToolMode.entries.first { it.tool == tool }
    }
}

@Immutable
/** Additional mode accompanying [Mode] and
 * carrying [SubMode]-specific relevant data, also
 * they have specific behavior for VM.[onPanZoom] */
sealed interface SubMode {
    data object None : SubMode
    // center uses absolute positioning
    /** Scale via top-right selection rect handle */
    data class Scale(val center: Offset) : SubMode
    data class ScaleViaSlider(val center: Offset, val sliderPercentage: Float = 0.5f) : SubMode
    data class Rotate(val center: Offset, val angle: Double = 0.0, val snappedAngle: Double = 0.0) : SubMode
    
    data class FlowSelect(val lastQualifiedPart: Cluster.Part? = null) : SubMode
    data class FlowFill(val lastQualifiedPart: Cluster.Part? = null) : SubMode
}

