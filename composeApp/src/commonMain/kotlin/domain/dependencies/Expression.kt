package domain.dependencies

import domain.Ix
import ui.tools.EditClusterTool

// associated with ToolMode and signature
enum class Function {
    CIRCLE_BY_CENTER_AND_RADIUS,
    CIRCLE_BY_3_POINTS,
    LINE_BY_2_POINTS,
    CIRCLE_INVERSION,
    // indexed output
    CIRCLE_INTERPOLATION,
    CIRCLE_EXTRAPOLATION,
    LOXODROMIC_MOTION,
    INTERSECTION,
    INCIDENCE, // from point-circle snapping
}

sealed interface Parameters // numeric values used, from the dialog or somewhere else

sealed interface Arg {
    sealed interface Indexed : Arg {
        data class Point(val index: Ix) : Indexed
        data class CircleOrLine(val index: Ix) : Indexed
    }
}

data class Expression(
    val function: Function,
    val parameters: Parameters,
    val args: List<Arg>, // Arg can also be computed as an expression, making up Forest-like data structure
) {
}

fun propagateChange(
    changedFreeNodes: List<Ix>,
) {
    // changed := changedFreeNodes
    // for node in changed.dependents
    //   changed += node
    // notChanged = all - changed
    // known = changedFreeNodes + notChanged
    // toBeUpdated = changed - changedFreeNodes
    // length2group = toBeUpdated.groupBy { min arg-path length to known }
    // for i in length.keys.sorted
    //   group = length2group[i]
    //   for node in group
    //     updatedNode = recompute node given known
    //     known.add(updatedNode)
    // given that there is no circular dep cycles it should succeed properly
}