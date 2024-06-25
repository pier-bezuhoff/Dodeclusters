package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.PartialArgList
import kotlinx.serialization.Serializable

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

// MultiArg Tool -> this enum mapping
@Serializable
enum class ToolMode(val signature: PartialArgList.Signature) : Mode {
    CIRCLE_BY_CENTER_AND_RADIUS(PartialArgList.SIGNATURE_2_POINTS),
    CIRCLE_BY_3_POINTS(PartialArgList.SIGNATURE_3_POINTS),
}

// equivalent to Multiselect but on confirmation (selection has to be non-empty) we go back
// to [parentMode] and add what we selected as an arg
@Serializable
data class TemporarySelectionMode(
    val parentMode: ToolMode
)
