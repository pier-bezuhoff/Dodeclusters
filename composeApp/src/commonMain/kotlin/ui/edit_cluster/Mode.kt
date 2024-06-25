package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.PartialArgList
import kotlinx.serialization.Serializable

@Immutable
sealed interface Mode {
    fun isSelectingCircles(): Boolean =
        this is SelectionMode.Drag || this is SelectionMode.Multiselect
}

sealed interface SelectionMode : Mode {
    /** Select & drag singular circles */
    data object Drag : SelectionMode
    data object Multiselect : SelectionMode
    /** Select regions to create new [Cluster.Part]s */
    data object Region : SelectionMode
}

@Serializable
/** sub-modes of [SelectionMode.Multiselect] related to the method by which circles are selected */
enum class MultiselectMethod {
    BY_CLICK,
    /** Imagine a line/trail drawn by moving down-ed cursor,
     * if the line passes through a circle, the circle is added to the selection */
    BY_FLOW
}

@Serializable
/** sub-modes of [SelectionMode.Multiselect] related to how new selection is combined */
enum class MultiselectLogic {
    SYMMETRIC_DIFFIRENCE, ADD, SUBTRACT
}

// MultiArg Tool -> this enum mapping
@Serializable
enum class ToolMode(val signature: PartialArgList.Signature) { // : Mode
    CIRCLE_BY_CENTER_AND_RADIUS(PartialArgList.SIGNATURE_2_POINTS),
    CIRCLE_BY_3_POINTS(PartialArgList.SIGNATURE_3_POINTS),
}

// equivalent to Multiselect but on confirmation (selection has to be non-empty) we go back
// to [parentMode] and add what we selected as an arg
data class TemporarySelectionMode(
    val parentMode: ToolMode
)

// TODO: migrate to ToolMode & PartialArgList instead
@Immutable
sealed class CreationMode(open val phase: Int, val nPhases: Int): Mode {
    sealed class CircleByCenterAndRadius(
        phase: Int,
    ) : CreationMode(phase, nPhases = 2) {
        // visible positions are used
        data class Center(val center: Offset? = null) : CircleByCenterAndRadius(phase = 1)
        data class Radius(val center: Offset, val radiusPoint: Offset? = null) : CircleByCenterAndRadius(phase = 2)
        companion object {
            val START_STATE by lazy { Center() } // we defer to avoid recursive dependency
        }
    }
    data class CircleBy3Points(
        override val phase: Int = 1,
        val points: List<Offset> = emptyList()
    ) : CreationMode(phase, nPhases = 3) {
        companion object {
            val START_STATE by lazy { CircleBy3Points() }
        }
    }
//    data class LineBy2Points(override val phase: Int) : CreationMode(phase, nPhases = 2)
}

// i just do not know, it feels there should be a better way
// we need haskell type families
val CreationMode.startState: CreationMode
    get() = when (this) {
        is CreationMode.CircleByCenterAndRadius -> CreationMode.CircleByCenterAndRadius.START_STATE
        is CreationMode.CircleBy3Points -> CreationMode.CircleBy3Points.START_STATE
    }

