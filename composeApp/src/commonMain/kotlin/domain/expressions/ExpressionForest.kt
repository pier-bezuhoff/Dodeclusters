package domain.expressions

import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import domain.Ix

/**
 * tier = 0: free object,
 * tier = 1: depends only on free objects,
 * ...
 * tier = n+1: max dependency tier is n
 */
typealias Tier = Int
/** object with no dependencies */
private const val FREE_TIER: Tier = 0
/** stub for yet-to-be-computed tier */
private const val UNCALCULATED_TIER: Tier = -1
/** stub tier for forever-deleted object index */
private const val ABANDONED_TIER: Tier = -2

/**
 * Class for managing expressions
 * @param[get] [GCircle]s are stored separately by design, so we have to access them somehow
 * @param[set] used to set updated objects when calling [update] or [reEval] */
class ExpressionForest(
    initialExpressions: Map<Ix, Expression?>, // pls include all possible indices
    // find indexed args -> downscale them -> eval expr -> upscale result
    private val get: (Ix) -> GCircle?,
    private val set: (Ix, GCircle?) -> Unit,
) {
    // for the VM.circles list null's correspond to unrealized outputs of multi-functions
    // here null's correspond to free objects
    val expressions: MutableMap<Ix, Expression?> = initialExpressions.toMutableMap()

    /** parent index -> set of all its children with *direct* dependency */
    val children: MutableMap<Ix, Set<Ix>> = expressions
        .keys
        .associateWith { emptySet<Ix>() }
        .toMutableMap()

    /** index -> tier */
    private val ix2tier: MutableMap<Ix, Tier> = initialExpressions
        .keys
        .associateWith { UNCALCULATED_TIER } // stub
        .toMutableMap()
    /** tier -> indices */
    private val tier2ixs: MutableList<Set<Ix>>

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
                    expr.args.forEach { parentIx ->
                        children[parentIx] = children.getOrElse(parentIx) { emptySet() } + ix
                    }
                }
            }
    }

    /**
     * @param[multiExpressionCache] cache used to store multi-output [ExprResult]
     * of [Expr.OneToMany]
     */
    private fun Expression.eval(
        multiExpressionCache: MutableMap<Expr.OneToMany, ExprResult>
    ): GCircle? =
        when (this) {
            is Expression.Just ->
                expr.eval(get).firstOrNull()
            is Expression.OneOf -> {
                val results = multiExpressionCache.getOrPut(expr) { expr.eval(get) }
                results.getOrNull(outputIndex)
            }
        }

    private fun calculateNextIndex(): Ix =
        expressions.keys.maxOfOrNull { it + 1 } ?: 0

    fun addFree(): Ix {
        val ix = calculateNextIndex()
        expressions[ix] = null
        children[ix] = emptySet()
        ix2tier[ix] = FREE_TIER
        if (tier2ixs.isEmpty())
            tier2ixs += setOf(ix)
        else
            tier2ixs[FREE_TIER] = tier2ixs[FREE_TIER] + ix
        return ix
    }

    /** don't forget to upscale the result afterwards! */
    fun addSoloExpression(expr: Expr.OneToOne): GCircle? {
        val ix = calculateNextIndex()
        expressions[ix] = Expression.Just(expr)
        expr.args.forEach { parentIx ->
            children[parentIx] = children.getOrElse(parentIx) { emptySet() } + ix
        }
        val tier = computeTier(ix, expr)
        ix2tier[ix] = tier
        if (tier < tier2ixs.size) {
            tier2ixs[tier] = tier2ixs[tier] + ix
        } else { // no hopping over tiers, we good
            tier2ixs.add(setOf(ix))
        }
        val result = expr.eval(get)
        return result.firstOrNull()
            .also {
                println("$ix -> $expr -> $result")
            }
    }

    /** don't forget to upscale the result afterwards! */
    fun addMultiExpression(expression: Expression.OneOf): GCircle? {
        val expr = expression.expr
        val result = expr.eval(get)[expression.outputIndex]
        val ix = calculateNextIndex()
        val tier = computeTier(ix, expr)
        expressions[ix] = expression
        expr.args.forEach { parentIx ->
            children[parentIx] = children.getOrElse(parentIx) { emptySet() } + ix
        }
        ix2tier[ix] = tier
        if (tier < tier2ixs.size) {
            tier2ixs[tier] = tier2ixs[tier] + ix
        } else { // no hopping over tiers, we good
            tier2ixs.add(setOf(ix))
        }
        println("$ix -> $expression -> $result")
        return result
    }

    /** don't forget to upscale the result afterwards! */
    fun addMultiExpression(expr: Expr.OneToMany): ExprResult {
        val result = expr.eval(get)
        val ix0 = calculateNextIndex()
        val tier = computeTier(ix0, expr)
        repeat(result.size) { outputIndex ->
            val ix = ix0 + outputIndex
            expressions[ix] = Expression.OneOf(expr, outputIndex)
            expr.args.forEach { parentIx ->
                children[parentIx] = children.getOrElse(parentIx) { emptySet() } + ix
            }
            ix2tier[ix] = tier
            if (tier < tier2ixs.size) {
                tier2ixs[tier] = tier2ixs[tier] + ix
            } else { // no hopping over tiers, we good
                tier2ixs.add(setOf(ix))
            }
        }
        println("$ix0:${ix0+result.size} -> $expr -> $result")
        return result
    }

    fun findExpression(expression: Expression): Ix? {
        return expressions.entries
            .firstOrNull { (_, e) -> e == expression }
            ?.key
    }

    /** The new node still inherits its previous children */
    fun changeToFree(ix: Ix) {
        if (expressions[ix] != null) {
            expressions[ix]?.let { previousExpr ->
                previousExpr.expr.args.forEach { parentIx ->
                    children[parentIx] = (children[parentIx] ?: emptySet()) - ix
                }
            }
            expressions[ix] = null
            val previousTier = ix2tier[ix] ?: UNCALCULATED_TIER
            if (previousTier != UNCALCULATED_TIER) {
                tier2ixs[previousTier] = tier2ixs[previousTier] - ix
            }
            tier2ixs[FREE_TIER] = tier2ixs[FREE_TIER] + ix
            ix2tier[ix] = FREE_TIER
            recomputeChildrenTiers(ix)
        }
    }

    /** The new node still inherits its previous children */
    fun changeExpression(ix: Ix, newExpr: Expr.OneToOne): GCircle? {
        expressions[ix]?.let { previousExpr ->
            previousExpr.expr.args.forEach { parentIx ->
                children[parentIx] = (children[parentIx] ?: emptySet()) - ix
            }
        }
        val previousTier = ix2tier[ix]!!
        tier2ixs[previousTier] = tier2ixs[previousTier] - ix
        ix2tier[ix] = UNCALCULATED_TIER
        expressions[ix] = Expression.Just(newExpr)
        newExpr.args.forEach { parentIx ->
            children[parentIx] = children.getOrElse(parentIx) { emptySet() } + ix
        }
        val tier = computeTier(ix)
        ix2tier[ix] = tier
        if (tier < tier2ixs.size) {
            tier2ixs[tier] = tier2ixs[tier] + ix
        } else { // no hopping over tiers, we good
            tier2ixs.add(setOf(ix))
        }
        recomputeChildrenTiers(ix)
        val result = newExpr.eval(get)
        return result.firstOrNull()
            .also {
                println("change $ix -> $newExpr -> $result")
            }
    }

    /**
     * delete [ixs] nodes and all of their children from the [ExpressionForest]
     * @return all the deleted nodes
     * */
    fun deleteNodes(ixs: List<Ix>): Set<Ix> {
        val deleted = ixs.toMutableSet()
        var lvl = ixs
        while (lvl.isNotEmpty()) {
            lvl = lvl.flatMap { children[it] ?: emptySet() }
            deleted += lvl
        }
        for (d in deleted) {
            expressions[d] = null // do not delete it since we want to keep indices (keys) in sync with VM.circles
            children.remove(d)
            ix2tier[d] = ABANDONED_TIER
        }
        for ((parentIx, childIxs) in children)
            children[parentIx] = childIxs - deleted
        for ((tier, tiered) in tier2ixs.withIndex())
            tier2ixs[tier] = tiered - deleted
        return deleted
    }

    /** recursively re-evaluates expressions given that [changedIxs] have changed
     * and updates via [set] */
    fun update(
        changedIxs: List<Ix>,
        // deltas (translation, rotation, scaling) to apply to immediate carried objects
    ) {
        val changed = mutableSetOf<Ix>()
        var lvl = changedIxs
        while (lvl.isNotEmpty()) {
            lvl = lvl.flatMap { children[it] ?: emptySet() }
            changed += lvl
        }
        val toBeUpdated = (changed - changedIxs.toSet()) // we assume that changedIxs are up-to-date
            .sortedBy { ix2tier[it] }
        val cache: MutableMap<Expr.OneToMany, ExprResult> = mutableMapOf()
        for (ix in toBeUpdated) {
            expressions[ix]?.let { expression -> // tbh the expression cannot be null
                set(ix, expression.eval(cache))
            }
        }
        // idk, being so easily detachable feels wrong
//        for (changedIx in changedIxs) {
//            if (expressions[changedIx] != null && changedIx !in changed) {
//                // we have moved a non-free node, but haven't touched its parents
//                changeToFree(changedIx)
//            }
//        }
    }

    /** Re-evaluates all expressions and [set]s updated results */
    fun reEval() {
        val cache = mutableMapOf<Expr.OneToMany, ExprResult>()
        val deps = tier2ixs.drop(1) // no need to calc for tier 0
        for (ixs in deps) {
            for (ix in ixs) {
                expressions[ix]?.let { expression ->
                    set(ix, expression.eval(cache))
                }
            }
        }
    }

    /**
     * tier 0 = free from deps, tier 1 = all deps are tier 0 at max...
     * tier k = all deps are tier (k-1) at max
     *
     * results in filling [ix2tier]
     **/
    private fun computeTiers() {
        for (ix in expressions.keys) {
            if (ix2tier[ix] == UNCALCULATED_TIER) {
                ix2tier[ix] = computeTier(ix)
            }
        }
    }

    /**
     * note: either supply [expr0] or set `expressions[ix]` BEFORE calling this
     * */
    private fun computeTier(ix: Ix, expr0: Expr? = null): Tier {
        val expr: Expr? = expr0 ?: expressions[ix]?.expr
        return if (expr == null) {
            FREE_TIER
        } else {
            val argTiers = mutableSetOf<Tier>()
            val args = expr.args
            for (subArg in args) {
                val t = ix2tier[subArg] ?: UNCALCULATED_TIER
                val tier: Tier
                if (t == UNCALCULATED_TIER) {
                    tier = computeTier(subArg)
                    ix2tier[subArg] = tier
                } else {
                    tier = t
                }
                argTiers.add(tier)
            }
            if (args.isEmpty())
                println("WARNING: $expr has empty args!?")
            1 + (argTiers.maxOrNull() ?: -1)
        }
    }

    private fun getAllChildren(parentIx: Ix): Set<Ix> {
        val childs = children[parentIx] ?: emptySet()
        val allChilds = childs.toMutableSet()
        for (child in childs) {
            allChilds += getAllChildren(child)
        }
        return allChilds
    }

    /**
     * call AFTER changing parent expression & tier
     */
    private fun recomputeChildrenTiers(parentIx: Ix) {
        val childs = getAllChildren(parentIx)
        for (child in childs) {
            val tier = ix2tier[child] ?: UNCALCULATED_TIER
            if (tier != UNCALCULATED_TIER) {
                tier2ixs[tier] = tier2ixs[tier] - child
            }
            ix2tier[child] = UNCALCULATED_TIER
        }
        for (child in childs) {
            val tier = computeTier(child)
            ix2tier[child] = tier
            tier2ixs[tier] = tier2ixs[tier] + child
        }
    }

    fun getIncidentPoints(parentIx: Ix): List<Ix> =
        (children[parentIx] ?: emptySet())
            .filter { expressions[it]?.expr is Expr.Incidence }

    fun adjustIncidentPointExpressions(ix2point: Map<Ix, Point?>) {
        for ((ix, point) in ix2point) {
            if (point != null) {
                val expr = expressions[ix]?.expr as Expr.Incidence
                val parent = get(expr.carrier) as CircleOrLine
                expressions[ix] = Expression.Just(expr.copy(
                    parameters = IncidenceParameters(order = parent.point2order(point))
                ))
            }
        }
    }

    fun immediateParentsOf(childIx: Ix): List<Ix> =
        expressions[childIx]?.expr?.args.orEmpty()
}
