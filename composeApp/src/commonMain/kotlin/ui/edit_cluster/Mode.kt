package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.PartialArgList
import kotlinx.serialization.Serializable
import ui.tools.EditClusterTool
import ui.tools.Tool

@Immutable
sealed interface Mode {
    fun isSelectingCircles(): Boolean =
        this == SelectionMode.Drag || this == SelectionMode.Multiselect
}

@Serializable
enum class SelectionMode : Mode {
    /** Select & drag singular circles */
    Drag,
    Multiselect,
    /** Select regions to create new [Cluster.Part]s */
    Region,
}

@Serializable
/** sub-modes of [SelectionMode.Multiselect] related to the method by which circles are selected */
enum class MultiselectMethod {
    BY_CLICK,
    /** Imagine a line/trail drawn by moving down-ed cursor,
     * if the line passes through a circle, the circle is added to the selection */
    BY_FLOW,
    RECTANGULAR
}

@Serializable
/** sub-modes of [SelectionMode.Multiselect] related to how new selection is combined */
enum class MultiselectLogic {
    SYMMETRIC_DIFFIRENCE, ADD, SUBTRACT, REPLACE
}

enum class ToolMode(val tool: EditClusterTool.MultiArg) : Mode {
    CIRCLE_BY_CENTER_AND_RADIUS(EditClusterTool.ConstructCircleByCenterAndRadius),
    CIRCLE_BY_3_POINTS(EditClusterTool.ConstructCircleBy3Points),
//    CIRCLE_INVERSION(EditClusterTool.CircleInversion),
    ;

    val signature: PartialArgList.Signature = tool.signature

    companion object {
        fun <T> fromTool(tool: T): ToolMode where T: EditClusterTool, T: Tool.MultiArg
            = ToolMode.entries.first { it.tool == tool }
    }
}

// equivalent to Multiselect but on confirmation (selection has to be non-empty) we go back
// to [parentMode] and add what we selected as an arg
@Serializable
data class TemporarySelectionMode(
    val parentMode: ToolMode
)
