package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.Cluster
import data.PartialArgList
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

/** sub-modes of [SelectionMode.Multiselect] related to how new selection is combined */
enum class MultiselectLogic {
    ADD, REPLACE, SUBTRACT, SYMMETRIC_DIFFERENCE,
}

@Serializable
enum class ToolMode(
    @Transient
    val tool: EditClusterTool.MultiArg
) : Mode {
    CIRCLE_INVERSION(EditClusterTool.CircleInversion),
    CIRCLE_INTERPOLATION(EditClusterTool.CircleInterpolation),
    CIRCLE_EXTRAPOLATION(EditClusterTool.CircleExtrapolation),

    CIRCLE_BY_CENTER_AND_RADIUS(EditClusterTool.ConstructCircleByCenterAndRadius),
    CIRCLE_BY_3_POINTS(EditClusterTool.ConstructCircleBy3Points),
    LINE_BY_2_POINTS(EditClusterTool.ConstructLineBy2Points),
    ;

    val signature: PartialArgList.Signature = tool.signature

    companion object {
        fun correspondingTo(tool: EditClusterTool.MultiArg): ToolMode =
            ToolMode.entries.first { it.tool == tool }
    }
}

@Immutable
/** Additional mode accompanying [Mode] and
 * carrying [SubMode]-specific relevant data, also
 * they have specific behavior for [onPanZoom] */
sealed interface SubMode {
    data object None : SubMode
    // center uses absolute positioning
    /** Scale via top-right selection rect handle */
    data class Scale(val center: Offset) : SubMode
    data class ScaleViaSlider(val center: Offset, val sliderPercentage: Float = 0.5f) : SubMode
    data class Rotate(val center: Offset, val angle: Double = 0.0) : SubMode
    
    data class FlowSelect(val lastQualifiedPart: Cluster.Part? = null) : SubMode
    data class FlowFill(val lastQualifiedPart: Cluster.Part? = null) : SubMode
}

