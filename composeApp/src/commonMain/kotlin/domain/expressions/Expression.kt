package domain.expressions

import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import domain.Ix
import ui.edit_cluster.ExtrapolationParameters
import ui.edit_cluster.InterpolationParameters
import ui.edit_cluster.LoxodromicMotionParameters

// circles: [CircleOrLine?]
// points: [Point?]
// circleExpressions: [Ix] -> Expression
// pointExpressions: [Ix] -> Expression

// associated with ToolMode and signature
sealed interface Function {
    enum class OneToOne : Function {
        CIRCLE_BY_CENTER_AND_RADIUS,
        CIRCLE_BY_3_POINTS,
        LINE_BY_2_POINTS,
        CIRCLE_INVERSION,
        INCIDENCE, // from point-circle snapping, saved as obj + perp line thru the point
    }
    enum class OneToMany : Function {
        CIRCLE_INTERPOLATION,
        CIRCLE_EXTRAPOLATION,
        LOXODROMIC_MOTION,
        INTERSECTION,
    }
}

// numeric values used, from the dialog or somewhere else
interface Parameters {
    data object None : Parameters
}

sealed interface Arg {
    sealed interface Indexed : Arg {
        data class Point(val index: Ix) : Indexed
        data class CircleOrLine(val index: Ix) : Indexed
    }
}

data class Expression(
    val function: Function,
    val parameters: Parameters,
    // Arg can also be computed as an expression, making up Forest-like data structure
    val args: List<Arg.Indexed>,
)

sealed interface Expr {
    val expression: Expression

    data class Just(override val expression: Expression) : Expr
    data class OneOf(
        override val expression: Expression,
        val outputIndex: Ix
    ) : Expr

    // MAYBE: use polymorphism instead for stronger type enforcement
    // indexed args -> VM.circles&points -> VM.downscale -> eval -> VM.upscale
    fun eval(circles: List<CircleOrLine>, points: List<Point>): List<GCircle> {
        val fn = expression.function
        val args = expression.args.map { when (it) {
            is Arg.Indexed.CircleOrLine -> circles[it.index]
            is Arg.Indexed.Point -> points[it.index]
        } }
        when (this) {
            is Just -> {
                require(fn is Function.OneToOne)
                val result: GCircle? = when (fn) {
                    Function.OneToOne.CIRCLE_BY_CENTER_AND_RADIUS ->
                        computeCircleByCenterAndRadius(args[0] as Point, args[1] as Point)
                    Function.OneToOne.CIRCLE_BY_3_POINTS -> computeCircleBy3Points(args[0], args[1], args[2])
                    Function.OneToOne.LINE_BY_2_POINTS -> computeLineBy2Points(args[0], args[1])
                    Function.OneToOne.CIRCLE_INVERSION -> computeCircleInversion(args[0], args[1])
                    Function.OneToOne.INCIDENCE -> computeIncidence(args[0] as CircleOrLine, TODO())
                }
                return listOfNotNull(result)
            }
            is OneOf -> {
                require(fn is Function.OneToMany)
                return when (fn) {
                    Function.OneToMany.INTERSECTION ->
                        computeIntersection(args[0] as CircleOrLine, args[1] as CircleOrLine)
                    Function.OneToMany.CIRCLE_INTERPOLATION ->
                        computeCircleInterpolation(
                            expression.parameters as InterpolationParameters,
                            args[0] as CircleOrLine,
                            args[1] as CircleOrLine,
                        )
                    Function.OneToMany.CIRCLE_EXTRAPOLATION ->
                        computeCircleExtrapolation(
                            expression.parameters as ExtrapolationParameters,
                            args[0] as CircleOrLine,
                            args[1] as CircleOrLine,
                        )
                    Function.OneToMany.LOXODROMIC_MOTION ->
                        computeLoxodromicMotion(
                            expression.parameters as LoxodromicMotionParameters,
                            args[0] as Point,
                            args[1] as Point,
                            args[2]
                        )
                }
            }
        }
    }
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