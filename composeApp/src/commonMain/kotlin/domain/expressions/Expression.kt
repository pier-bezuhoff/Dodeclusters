package domain.expressions

import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import domain.Indices
import domain.Ix
import kotlin.math.exp

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
    // potential optimization: represent point indices as
    // -(i+1) while circle indices are +i
    sealed interface Indexed : Arg {
        data class CircleOrLine(val index: Ix) : Indexed
        data class Point(val index: Ix) : Indexed
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
abstract class ExpessionForest(
    initialCircleExpressions: List<Expression?>,
    initialPointExpressions: List<Expression?>,
) {
    // for the circles list null's correspond to unrealized outputs of multi-functions
    // null's correspond to free objects
    val circleExpressions: MutableList<Expression?> = initialCircleExpressions.toMutableList()
    val pointExpressions: MutableList<Expression?> = initialPointExpressions.toMutableList()

    val children: MutableMap<Arg.Indexed, List<Arg.Indexed>> = mutableMapOf()

    // circle index -> tier
    private val c2tier: MutableList<Int> = MutableList(circleExpressions.size) { -1 }
    private val p2tier: MutableList<Int> = MutableList(pointExpressions.size) { -1 }
    // tier -> circle indices
    private val tier2cs: MutableList<Indices>
    private val tier2ps: MutableList<Indices>

    init {
        computeTiers()
        tier2cs = c2tier.withIndex()
            .groupBy { (_, t) -> t }
            .mapValues { (_, v) -> v.map { (ix, _) -> ix } }
            .entries
            .sortedBy { (t, _) -> t }
            .map { (_, cs) -> cs }
            .toMutableList()
        tier2ps = p2tier.withIndex()
            .groupBy { (_, t) -> t }
            .mapValues { (_, v) -> v.map { (ix, _) -> ix } }
            .entries
            .sortedBy { (t, _) -> t }
            .map { (_, ps) -> ps }
            .toMutableList()
    }

    // find indexed args -> downscale them -> eval expr -> upscale result
    abstract fun Expr.eval(): ExprResult
//        = this.eval(::c, ::p).map { upscale(it) }

    fun addSoloExpression(isPoint: Boolean, expr: Expr.OneToOne): ExprResult {
        if (isPoint) {
            val ix = pointExpressions.size
            pointExpressions.add(Expression.Just(expr))
            val tier = computeTier(Arg.Indexed.Point(ix))
            p2tier.add(tier)
            if (tier < tier2ps.size) {
                tier2ps[tier] = tier2ps[tier] + ix
            } else { // no hopping over tiers, we good
                tier2ps.add(listOf(ix))
            }
        } else {
            val ix = circleExpressions.size
            circleExpressions.add(Expression.Just(expr))
            val tier = computeTier(Arg.Indexed.CircleOrLine(ix))
            c2tier.add(tier)
            if (tier < tier2cs.size) {
                tier2cs[tier] = tier2cs[tier] + ix
            } else { // no hopping over tiers, we good
                tier2cs.add(listOf(ix))
            }
        }
        return expr.eval()
    }

    fun addMultiExpression(isPoint: Boolean, expr: Expr.OneToMany): ExprResult {
        val result = expr.eval()
        if (isPoint) {
            val ix0 = pointExpressions.size
            val tier = computeTier(Arg.Indexed.Point(ix0))
            repeat(result.size) { i ->
                val ix = ix0 + i
                pointExpressions.add(Expression.OneOf(expr, i))
                p2tier.add(tier)
                if (tier < tier2ps.size) {
                    tier2ps[tier] = tier2ps[tier] + ix
                } else { // no hopping over tiers, we good
                    tier2ps.add(listOf(ix))
                }
            }
        } else {
            val ix0 = circleExpressions.size
            val tier = computeTier(Arg.Indexed.CircleOrLine(ix0))
            repeat(result.size) { i ->
                val ix = ix0 + i
                circleExpressions.add(Expression.OneOf(expr, i))
                c2tier.add(tier)
                if (tier < tier2cs.size) {
                    tier2cs[tier] = tier2cs[tier] + ix
                } else { // no hopping over tiers, we good
                    tier2cs.add(listOf(ix))
                }
            }
        }
        return result
    }

    fun deleteExpression(arg: Arg.Indexed) {
        // delete expr and shift all indices
        // (or set it to null)
        // remove it from both tiers
        when (arg) {
            is Arg.Indexed.CircleOrLine -> {
                1
            }
            is Arg.Indexed.Point -> {
                2
            }
        }
        TODO()
    }

    fun propagateChange(
        changedArgs: List<Arg.Indexed>,
        // deltas (translation, rotation, scaling) to apply to immediate carried objects
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
        TODO()
    }

    fun reEval(
        circles: MutableList<CircleOrLine?>,
        points: MutableList<Point?>
    ) {
        val cache = mutableMapOf<Expr.OneToMany, ExprResult>()
        val deps = tier2cs.zip(tier2ps).drop(1)
        for ((cs, ps) in deps) { // no need to calc for tier 0
            for (c in cs) {
                val result = when (val expression = circleExpressions[c]) {
                    null -> circles[c]
                    is Expression.Just ->
                        expression.expr.eval().firstOrNull()
                    is Expression.OneOf -> {
                        val results = cache.getOrPut(expression.expr) { expression.expr.eval() }
                        results[expression.outputIndex]
                    }
                }
                circles[c] = result as? CircleOrLine
            }
            for (p in ps) {
                val result = when (val expression = pointExpressions[p]) {
                    null -> points[p]
                    is Expression.Just ->
                        expression.expr.eval().firstOrNull()
                    is Expression.OneOf -> {
                        val results = cache.getOrPut(expression.expr) { expression.expr.eval() }
                        results[expression.outputIndex]
                    }
                }
                points[p] = result as? Point
            }
        }
    }

    /**
     * tier 0 = free from deps, tier 1 = all deps are tier 0 at max...
     * tier k = all deps are tier (k-1) at max
     * @return (circle index -> expr tier, point index -> expr tier)
     **/
    private fun computeTiers() {
        for (ix in circleExpressions.indices) {
            if (c2tier[ix] == -1) {
                val tier = computeTier(Arg.Indexed.CircleOrLine(ix))
                c2tier[ix] = tier
            }
        }
        for (ix in pointExpressions.indices) {
            if (p2tier[ix] == -1) {
                val tier = computeTier(Arg.Indexed.CircleOrLine(ix))
                p2tier[ix] = tier
            }
        }
    }

    // no need for tailrec idt
    private fun computeTier(arg: Arg.Indexed): Int {
        val expression = when (arg) {
            is Arg.Indexed.CircleOrLine -> circleExpressions[arg.index]
            is Arg.Indexed.Point -> pointExpressions[arg.index]
        }
        return if (expression == null) {
            0
        } else {
            val argTiers = mutableSetOf<Int>()
            val args = expression.expr.args
            for (subArg in args) {
                val tier = computeTier(subArg)
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