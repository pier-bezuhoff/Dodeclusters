package domain.expressions

import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import domain.Indices
import domain.Ix
import domain.filterIndices
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
        val index: Ix

        data class CircleOrLine(override val index: Ix) : Indexed
        data class Point(override val index: Ix) : Indexed
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
    initialExpressions: Map<Arg.Indexed, Expression?>,
) {
    // for the circles list null's correspond to unrealized outputs of multi-functions
    // null's correspond to free objects
    val expressions: MutableMap<Arg.Indexed, Expression?> = initialExpressions.toMutableMap()

    /** parent index -> set of all its children with *direct* dependency */
    val children: MutableMap<Arg.Indexed, Set<Arg.Indexed>> = expressions
        .keys
        .associateWith { emptySet<Arg.Indexed>() }
        .toMutableMap()

    /** index -> tier */
    val ix2tier: MutableMap<Arg.Indexed, Int> = initialExpressions
        .keys
        .associateWith { -1 }
        .toMutableMap()
    /** tier -> indices */
    val tier2ixs: MutableList<Set<Arg.Indexed>>

    init {
        computeTiers() // computes ix2tier
        tier2ixs = ix2tier
            .entries
            .groupBy { (_, t) -> t }
            .mapValues { (_, v) -> v.map { it.key } }
            .entries
            .sortedBy { (t, _) -> t }
            .map { (_, ixs) -> ixs.toSet() }
            .toMutableList()
        expressions
            .entries
            .forEach { (ix, expression) ->
                expression?.expr?.let { expr ->
                    expr.args.forEach { childIx ->
                        children[ix] = children[ix]!! + childIx
                    }
                }
            }
    }

    // find indexed args -> downscale them -> eval expr -> upscale result
    abstract fun Expr.eval(): ExprResult
//        = this.eval(::c, ::p).map { upscale(it) }

    fun addSoloExpression(isPoint: Boolean, expr: Expr.OneToOne): ExprResult {
        val ix: Arg.Indexed = if (isPoint) {
            val i = expressions.keys
                .filterIsInstance<Arg.Indexed.Point>()
                .maxOfOrNull { it.index + 1 } ?: 0
            Arg.Indexed.Point(i)
        } else {
            val i = expressions.keys
                .filterIsInstance<Arg.Indexed.CircleOrLine>()
                .maxOfOrNull { it.index + 1 } ?: 0
            Arg.Indexed.CircleOrLine(i)
        }
        expressions[ix] = Expression.Just(expr)
        val tier = computeTier(ix)
        ix2tier[ix] = tier
        if (tier < tier2ixs.size) {
            tier2ixs[tier] = tier2ixs[tier] + ix
        } else { // no hopping over tiers, we good
            tier2ixs.add(setOf(ix))
        }
        return expr.eval()
    }

    fun addMultiExpression(isPoint: Boolean, expr: Expr.OneToMany): ExprResult {
        val result = expr.eval()
         if (isPoint) {
            val i = expressions.keys
                .filterIsInstance<Arg.Indexed.Point>()
                .maxOfOrNull { it.index + 1 } ?: 0
            val ix0 = Arg.Indexed.Point(i)
            val tier = computeTier(ix0)
            repeat(result.size) { outputIndex ->
                val ix = Arg.Indexed.Point(ix0.index + outputIndex)
                expressions[ix] = Expression.OneOf(expr, outputIndex)
                ix2tier[ix] = tier
                if (tier < tier2ixs.size) {
                    tier2ixs[tier] = tier2ixs[tier] + ix
                } else { // no hopping over tiers, we good
                    tier2ixs.add(setOf(ix))
                }
            }
        } else {
            val i = expressions.keys
                .filterIsInstance<Arg.Indexed.CircleOrLine>()
                .maxOfOrNull { it.index + 1 } ?: 0
            val ix0 = Arg.Indexed.CircleOrLine(i)
             val tier = computeTier(ix0)
             repeat(result.size) { outputIndex ->
                 val ix = Arg.Indexed.CircleOrLine(ix0.index + outputIndex)
                 expressions[ix] = Expression.OneOf(expr, outputIndex)
                 ix2tier[ix] = tier
                 if (tier < tier2ixs.size) {
                     tier2ixs[tier] = tier2ixs[tier] + ix
                 } else { // no hopping over tiers, we good
                     tier2ixs.add(setOf(ix))
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
        val deps = tier2ixs.drop(1)
        for (ixs in deps) { // no need to calc for tier 0
            for (ix in ixs) {
                val result = when (val expression = expressions[ix]) {
                    null -> {
                        when (ix) {
                            is Arg.Indexed.CircleOrLine -> circles[ix.index]
                            is Arg.Indexed.Point -> points[ix.index]
                        }
                    }
                    is Expression.Just ->
                        expression.expr.eval().firstOrNull()
                    is Expression.OneOf -> {
                        val results = cache.getOrPut(expression.expr) { expression.expr.eval() }
                        results[expression.outputIndex]
                    }
                }
                when (ix) {
                    is Arg.Indexed.CircleOrLine ->
                        circles[ix.index] = result as? CircleOrLine
                    is Arg.Indexed.Point ->
                        points[ix.index] = result as? Point
                }
            }
        }
    }

    /**
     * tier 0 = free from deps, tier 1 = all deps are tier 0 at max...
     * tier k = all deps are tier (k-1) at max
     * @return (circle index -> expr tier, point index -> expr tier)
     **/
    private fun computeTiers() {
        for (ix in expressions.keys) {
            if (ix2tier[ix] == -1) {
                val tier = computeTier(ix)
                ix2tier[ix] = tier
            }
        }
    }

    // no need for tailrec idt
    private fun computeTier(arg: Arg.Indexed): Int {
        val expression = expressions[arg]
        return if (expression == null) {
            0
        } else {
            val argTiers = mutableSetOf<Int>()
            val args = expression.expr.args
            for (subArg in args) {
                val tier = computeTier(subArg)
                ix2tier[subArg] = tier
                argTiers.add(tier)
            }
            argTiers.maxByOrNull { it + 1 } ?: 0
        }
    }
}