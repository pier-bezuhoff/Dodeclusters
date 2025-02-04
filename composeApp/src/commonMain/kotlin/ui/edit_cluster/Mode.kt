package ui.edit_cluster

import androidx.compose.runtime.Immutable
import domain.Signature
import kotlinx.serialization.Serializable
import ui.tools.EditClusterTool
import kotlin.jvm.Transient

@Immutable
sealed interface Mode {
    val tool: EditClusterTool

    fun isSelectingCircles(): Boolean =
        this == SelectionMode.Drag || this == SelectionMode.Multiselect
}

@Serializable
@Immutable
enum class SelectionMode(
    @Transient
    override val tool: EditClusterTool
) : Mode {
    /** Select & drag singular circles */
    Drag(EditClusterTool.Drag),
    /** Select multiple circles */
    Multiselect(EditClusterTool.Multiselect),
    /** Select regions to create new [LogicalRegion]s */
    Region(EditClusterTool.Region),
}

// MAYBE: associate function(constants, variables) -> circles
//  with each tool and update the result when any variable changes
//  + potentially save these functional dependencies to ddc
@Serializable
@Immutable
enum class ToolMode(
    @Transient
    override val tool: EditClusterTool.MultiArg
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

