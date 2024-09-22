package domain.dependencies

import data.geometry.CircleOrLine
import data.geometry.Point
import domain.Ix

// circles: [CircleOrLine?]
// points: [Point?]
// circleExpressions: [Ix] -> Expression
// pointExpressions: [Ix] -> Expression

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
    val args: List<Arg.Indexed>, // Arg can also be computed as an expression, making up Forest-like data structure
    val outputIndex: Ix? = null,
) {
}

private interface Circles {
    val circles: List<CircleOrLine?> // null's correspond to unrealized outputs of multi-functions
    val circleExpressions: List<Expression?> // null's correspond to free objects
    val points: List<Point?>
    val pointExpressions: List<Expression?>

    // recompute when adding/removing circles or points
    fun computeTiers(): Pair<List<Int>, List<Int>> {
        val c2tier = MutableList(circles.size) { -1 }
        val p2tier = MutableList(points.size) { -1 }
        for (ix in circles.indices) {
            if (c2tier[ix] == -1) {
                val tier = computeTier(Arg.Indexed.CircleOrLine(ix), c2tier, p2tier)
                c2tier[ix] = tier
            }
        }
        for (ix in points.indices) {
            if (p2tier[ix] == -1) {
                val tier = computeTier(Arg.Indexed.CircleOrLine(ix), c2tier, p2tier)
                p2tier[ix] = tier
            }
        }
        return Pair(c2tier, p2tier)
    }

    // no need for tailrec idt
    fun computeTier(arg: Arg.Indexed, c2tier: MutableList<Int>, p2tier: MutableList<Int>): Int {
        val expr = when (arg) {
            is Arg.Indexed.CircleOrLine -> circleExpressions[arg.index]
            is Arg.Indexed.Point -> pointExpressions[arg.index]
        }
        return if (expr == null) {
            0
        } else {
            val argTiers = mutableSetOf<Int>()
            for (subArg in expr.args) {
                val tier = computeTier(subArg, c2tier, p2tier)
                when (subArg) {
                    is Arg.Indexed.CircleOrLine ->
                        c2tier[subArg.index] = tier
                    is Arg.Indexed.Point ->
                        p2tier[subArg.index] = tier
                }
                argTiers.add(tier)
            }
            argTiers.maxByOrNull { it + 1 } ?: 0
        }
    }
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