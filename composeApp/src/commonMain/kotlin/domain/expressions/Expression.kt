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

sealed class Expr(
    val function: Function,
    open val parameters: Parameters,
    // Arg can also be computed as an expression, making up Forest-like data structure
    val args: List<Arg.Indexed>,
) {
    sealed class OneToOne(
        function: Function.OneToOne,
        parameters: Parameters,
        args: List<Arg.Indexed>,
    ) : Expr(function, parameters, args)
    sealed class OneToMany(
        function: Function.OneToMany,
        parameters: Parameters,
        args: List<Arg.Indexed>,
    ) : Expr(function, parameters, args)

    data class Incidence(
        val point: Arg.Indexed.Point,
        val carrier: Arg.Indexed.CircleOrLine,
    ) : OneToOne(Function.OneToOne.INCIDENCE, Parameters.None, listOf(point, carrier))
    data class CircleByCenterAndRadius(
        val center: Arg.Indexed.Point,
        val radiusPoint: Arg.Indexed.Point
    ) : OneToOne(Function.OneToOne.CIRCLE_BY_CENTER_AND_RADIUS, Parameters.None, listOf(center, radiusPoint))
    data class CircleBy3Points(
        val point1: Arg.Indexed,
        val point2: Arg.Indexed,
        val point3: Arg.Indexed,
    ) : OneToOne(Function.OneToOne.CIRCLE_BY_3_POINTS, Parameters.None, listOf(point1, point2, point3))
    data class LineBy2Points(
        val point1: Arg.Indexed,
        val point2: Arg.Indexed,
    ) : OneToOne(Function.OneToOne.LINE_BY_2_POINTS, Parameters.None, listOf(point1, point2))
    data class CircleInversion(
        val target: Arg.Indexed,
        val engine: Arg.Indexed.CircleOrLine,
    ) : OneToOne(Function.OneToOne.CIRCLE_INVERSION, Parameters.None, listOf(target, engine))

    data class Intersection(
        val circle1: Arg.Indexed.CircleOrLine,
        val circle2: Arg.Indexed.CircleOrLine,
    ) : OneToMany(Function.OneToMany.INTERSECTION, Parameters.None, listOf(circle1, circle2))
    data class CircleInterpolation(
        override val parameters: InterpolationParameters,
        val startCircle: Arg.Indexed.CircleOrLine,
        val endCircle: Arg.Indexed.CircleOrLine,
    ) : OneToMany(Function.OneToMany.CIRCLE_INTERPOLATION, parameters, listOf(startCircle, endCircle))
    data class CircleExtrapolation(
        override val parameters: ExtrapolationParameters,
        val startCircle: Arg.Indexed.CircleOrLine,
        val endCircle: Arg.Indexed.CircleOrLine,
    ) : OneToMany(Function.OneToMany.CIRCLE_EXTRAPOLATION, parameters, listOf(startCircle, endCircle))
    data class LoxodromicMotion(
        override val parameters: LoxodromicMotionParameters,
        val divergencePoint: Arg.Indexed.Point,
        val convergencePoint: Arg.Indexed.Point,
        val target: Arg.Indexed,
    ) : OneToMany(Function.OneToMany.LOXODROMIC_MOTION, parameters, listOf(divergencePoint, convergencePoint, target))

    // indexed args -> VM.circles&points -> VM.downscale -> eval -> VM.upscale
    fun eval(
        p: (Arg.Indexed.Point) -> Point,
        c: (Arg.Indexed.CircleOrLine) -> CircleOrLine
    ): List<GCircle> {
        fun g(arg: Arg.Indexed): GCircle =
            when (arg) {
                is Arg.Indexed.Point -> p(arg)
                is Arg.Indexed.CircleOrLine -> c(arg)
            }
        // idt it's worth to polymorphism eval
        return when (this) {
            is OneToOne -> {
                val result = when (this) {
                    is Incidence -> computeIncidence(p(point), c(carrier))
                    is CircleByCenterAndRadius -> computeCircleByCenterAndRadius(p(center), p(radiusPoint))
                    is CircleBy3Points -> computeCircleBy3Points(g(point1), g(point2), g(point3))
                    is LineBy2Points -> computeLineBy2Points(g(point1), g(point2))
                    is CircleInversion -> computeCircleInversion(g(target), g(engine))
                }
                listOfNotNull(result)
            }
            is Intersection -> computeIntersection(c(circle1), c(circle2))
            is CircleInterpolation -> computeCircleInterpolation(parameters, c(startCircle), c(endCircle))
            is CircleExtrapolation -> computeCircleExtrapolation(parameters, c(startCircle), c(endCircle))
            is LoxodromicMotion -> computeLoxodromicMotion(parameters, p(divergencePoint), p(convergencePoint), g(target))
        }
    }
}

sealed interface Expression {
    val expr: Expr

    data class Just(override val expr: Expr.OneToOne) : Expression
    data class OneOf(
        override val expr: Expr.OneToMany,
        val outputIndex: Ix
    ) : Expression
}

// prototype of VM
private interface Circles {
    // im thinking of nullable in case moving parts changes number of outputs which would mess up indexing
    val circles: List<CircleOrLine?> // null's correspond to unrealized outputs of multi-functions
    val circleExpressions: List<Expr?> // null's correspond to free objects
    val points: List<Point?>
    val pointExpressions: List<Expr?>

    fun p(arg: Arg.Indexed.Point): Point
    fun c(arg: Arg.Indexed.CircleOrLine): CircleOrLine

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