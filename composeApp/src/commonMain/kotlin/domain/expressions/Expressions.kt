package domain.expressions

import domain.Ix

/**
 * tier = 0: free object,
 *
 * tier = 1: depends only on free objects,
 *
 * ...
 *
 * tier = n+1: max dependency tier is n
 */
typealias Tier = Int
/** object with no dependencies */
internal const val FREE_TIER: Tier = 0
/** stub for yet-to-be-computed tier */
internal const val UNCALCULATED_TIER: Tier = -1
/** stub tier for forever-deleted object index */
internal const val ABANDONED_TIER: Tier = -2

// kotlin disallows E1 : E & F where E is generic
// hence this type mess
// see https://discuss.kotlinlang.org/t/current-intersection-type-options-in-kotlin/20903
/**
 * Class for managing expressions (~ AST controller)
 * @param[initialExpressions] pls include all possible [objects].indices as keys
 * @param[objects] reference to shared, *downscaled* mutable mirror-list of VM.objects
 * @param[EXPR] [Expr] subtype (eg [Expr.Conformal])
 * @param[EXPR_ONE_TO_ONE] [EXPR_ONE_TO_ONE] : [Expr.OneToOne], [EXPR_ONE_TO_ONE] : [EXPR] (eg [Expr.Conformal.OneToOne])
 * @param[EXPR_ONE_TO_MANY] [EXPR_ONE_TO_MANY] : [Expr.OneToMany], [EXPR_ONE_TO_MANY] : [EXPR] (eg [Expr.Conformal.OneToMany])
 * @param[R] object type & expression result type (eg [core.geometry.GCircle])
 */
@Suppress("UNCHECKED_CAST")
sealed class Expressions<EXPR : Expr, EXPR_ONE_TO_ONE : Expr.OneToOne, EXPR_ONE_TO_MANY : Expr.OneToMany, R : Any>(
    initialExpressions: Map<Ix, ExprOutput<EXPR>?>, // pls include all possible indices
    protected val objects: MutableList<R?>,
) {
//    typealias ExprResult = List<R?>

    // for the VM.objects list nulls correspond to unrealized outputs of multi-functions
    // here nulls correspond to free objects
    /**
     * object index -> its expression, `null` meaning "free" object
     * `expressions.keys` == `objects.indices`
     */
    val expressions: MutableMap<Ix, ExprOutput<EXPR>?> = initialExpressions.toMutableMap()
    /**
     * parent index -> set of all its children with *direct* dependency, inversion of [Expr.args]
     *
     * NOTE: [parents2gluedIncidentPoints] has to be cleared each time we alter [children]
     */
    val children: MutableMap<Ix, Set<Ix>> = expressions
        .keys
        .associateWith { emptySet<Ix>() }
        .toMutableMap()
    /** Cache for looking up all glued/fully constrained incident point children by
     * a set of parents.
     * Cache should be cleared on [children] changes. */
    protected val parents2gluedIncidentPoints: MutableMap<Set<Ix>, Set<Ix>> = mutableMapOf()
    /** index -> tier */
    protected val ix2tier: MutableMap<Ix, Tier> = initialExpressions
        .keys
        .associateWith { UNCALCULATED_TIER } // stub
        .toMutableMap()
    /** tier -> indices */
    protected val tier2ixs: MutableList<Set<Ix>>

    init {
        require(objects.indices.toSet() == initialExpressions.keys) {
            "initialExpressions keys must coincide with objects.indices: ${initialExpressions.keys} == ${objects.indices.toSet()}, where objects = $objects, initialExpressions = $initialExpressions"
        }
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

    protected abstract fun EXPR.evaluate(
        objects: List<R?>
    ): List<R?>

    /**
     * @param[multiExpressionCache] cache used to store multi-output `List<R?>`
     * of [Expr.OneToMany]
     */
    protected fun ExprOutput<EXPR>.evaluateWithCache(
        multiExpressionCache: MutableMap<EXPR_ONE_TO_MANY, List<R?>>
    ): R? = when (this) {
        is ExprOutput.Just ->
            expr.evaluate(objects).firstOrNull()
        is ExprOutput.OneOf -> {
            val results = multiExpressionCache.getOrPut(expr as EXPR_ONE_TO_MANY) {
                expr.evaluate(objects)
            }
            results.getOrNull(outputIndex)
        }
    }

    protected fun calculateNextIndex(): Ix =
        expressions.keys.maxOfOrNull { it + 1 } ?: 0

    /** append an empty expression and return its index */
    fun addFree(): Ix {
        val ix = calculateNextIndex()
        expressions[ix] = null
        children[ix] = emptySet()
        ix2tier[ix] = FREE_TIER
        // no need to clear glued cache
        if (tier2ixs.isEmpty())
            tier2ixs += setOf(ix)
        else
            tier2ixs[FREE_TIER] = tier2ixs[FREE_TIER] + ix
        return ix
    }

    /** don't forget to upscale the result afterwards! */
    fun addSoloExpr(expr: EXPR_ONE_TO_ONE): R? {
        val ix = calculateNextIndex()
        expressions[ix] = ExprOutput.Just(expr as Expr.OneToOne) as ExprOutput<EXPR>
        expr.args.forEach { parentIx ->
            children[parentIx] = children.getOrElse(parentIx) { emptySet() } + ix
        }
        val tier = computeTier(ix, expr as EXPR)
        ix2tier[ix] = tier
        if (tier < tier2ixs.size) {
            tier2ixs[tier] = tier2ixs[tier] + ix
        } else { // no hopping over tiers, we good
            tier2ixs.add(setOf(ix))
        }
        parents2gluedIncidentPoints.clear()
        val result = (expr as EXPR).evaluate(objects)
        println("$ix -> $expr -> $result")
        return result.firstOrNull()
    }

    /** don't forget to upscale the result afterwards! */
    fun addMultiExpression(exprOutput: ExprOutput.OneOf<EXPR_ONE_TO_MANY>): R? {
        val expr = exprOutput.expr
        val result = (expr as EXPR).evaluate(objects)[exprOutput.outputIndex]
        val ix = calculateNextIndex()
        val tier = computeTier(ix, expr)
        expressions[ix] = exprOutput as ExprOutput<EXPR>
        expr.args.forEach { parentIx ->
            children[parentIx] = children.getOrElse(parentIx) { emptySet() } + ix
        }
        ix2tier[ix] = tier
        if (tier < tier2ixs.size) {
            tier2ixs[tier] = tier2ixs[tier] + ix
        } else { // no hopping over tiers, we good
            tier2ixs.add(setOf(ix))
        }
        parents2gluedIncidentPoints.clear()
        println("$ix -> $exprOutput -> $result")
        return result
    }

    /** don't forget to upscale the result afterwards! */
    fun addMultiExpr(expr: EXPR_ONE_TO_MANY): List<R?> {
        val result0 = (expr as EXPR).evaluate(objects)
        val isPeriodic = isExprPeriodic(expr)
        val result = // small hack to avoid duplicating the original object
            if (isPeriodic && result0.isNotEmpty())
                result0.dropLast(1)
            else result0
        val ix0 = calculateNextIndex()
        val tier = computeTier(ix0, expr)
        val resultSize = result.size
        repeat(resultSize) { outputIndex ->
            val ix = ix0 + outputIndex
            expressions[ix] = ExprOutput.OneOf(expr, outputIndex)
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
        parents2gluedIncidentPoints.clear()
        println("$ix0:${ix0+result.size} -> $expr -> $result")
        return result
    }

    /** Used in [addMultiExpr] to avoid creating the last object in 360-rotation-like
     * operation which would duplicate the original target object */
    protected abstract fun isExprPeriodic(expr: EXPR_ONE_TO_MANY): Boolean

    fun findExpr(expr: EXPR?): List<Ix> {
        return expressions.entries
            .filter { (_, e) -> e?.expr == expr }
            .map { it.key }
    }

    /** The new node still inherits its previous children */
    fun changeToFree(index: Ix) {
        if (expressions[index] != null) {
            expressions[index]?.let { previousExpr ->
                previousExpr.expr.args.forEach { parentIx ->
                    children[parentIx] = (children[parentIx] ?: emptySet()) - index
                }
            }
            expressions[index] = null
            val previousTier = ix2tier[index] ?: UNCALCULATED_TIER
            if (previousTier != UNCALCULATED_TIER) {
                tier2ixs[previousTier] = tier2ixs[previousTier] - index
            }
            tier2ixs[FREE_TIER] = tier2ixs[FREE_TIER] + index
            ix2tier[index] = FREE_TIER
            recomputeChildrenTiers(index)
            parents2gluedIncidentPoints.clear()
        }
    }

    /** The new node still inherits its previous children */
    fun changeExpr(index: Ix, newExpr: EXPR_ONE_TO_ONE): R? {
        expressions[index]?.let { previousExpr ->
            previousExpr.expr.args.forEach { parentIx ->
                children[parentIx] = (children[parentIx] ?: emptySet()) - index
            }
        }
        val previousTier = ix2tier[index]!!
        tier2ixs[previousTier] = tier2ixs[previousTier] - index
        ix2tier[index] = UNCALCULATED_TIER
        expressions[index] = ExprOutput.Just(newExpr) as ExprOutput<EXPR>
        newExpr.args.forEach { parentIx ->
            children[parentIx] = children.getOrElse(parentIx) { emptySet() } + index
        }
        val tier = computeTier(index)
        ix2tier[index] = tier
        if (tier < tier2ixs.size) {
            tier2ixs[tier] = tier2ixs[tier] + index
        } else { // no hopping over tiers, we good
            tier2ixs.add(setOf(index))
        }
        recomputeChildrenTiers(index)
        parents2gluedIncidentPoints.clear()
        val result = (newExpr as EXPR).evaluate(objects)
//        println("change $ix -> $newExpr -> $result")
        return result.firstOrNull()
    }

    /**
     * Delete [indices] nodes and all of their children from the [ConformalExpressions] by
     * setting [expressions]`[...] = null` and clearing [children], [ix2tier], [tier2ixs]
     * @return all the deleted nodes indices
     * */
    fun deleteNodes(indices: List<Ix>): Set<Ix> {
        val deleted = indices.toMutableSet()
        var lvl = indices
        while (lvl.isNotEmpty()) {
            lvl = lvl.flatMap { children[it] ?: emptySet() }
            deleted += lvl
        }
        for (d in deleted) {
            expressions[d] = null // do not delete it since we want to keep indices (keys) in sync with VM.circles
            children.remove(d)
            ix2tier[d] = ABANDONED_TIER
        }
        for ((parentIx, childIxs) in children) {
            children[parentIx] = childIxs - deleted
        }
        for ((tier, tiered) in tier2ixs.withIndex())
            tier2ixs[tier] = tiered - deleted
        parents2gluedIncidentPoints.clear()
        return deleted
    }

    /**
     * Recursively re-evaluates expressions given that [changed]/parents have changed
     * and updates [objects] (except [excluded])
     *
     * NOTE: don't forget to sync `VM.objects` with [objects] at returned indices
     * @return all affected/child indices that were altered by [update] (excluding [changed]),
     * sorted by tier
     */
    fun update(
        changed: Set<Ix>,
        excluded: Set<Ix> = emptySet(),
        // deltas (translation, rotation, scaling) to apply to immediate carried objects
    ): List<Ix> {
        val changedTransitive = mutableSetOf<Ix>()
        var lvl = changed.toList() // MAYBE: make this a set
        while (lvl.isNotEmpty()) {
            lvl = lvl.flatMap { children[it] ?: emptySet() }
            changedTransitive += lvl
        }
        // we assume that changedIxs are up-to-date
        val toBeUpdated = (changedTransitive - changed - excluded)
            .sortedBy { ix2tier[it] }
        val cache: MutableMap<EXPR_ONE_TO_MANY, List<R?>> = mutableMapOf()
        for (ix in toBeUpdated) {
            // children always have non-null expressions
            objects[ix] = expressions[ix]?.evaluateWithCache(cache)
        }
        return toBeUpdated
    }

    /**
     * Re-evaluates all expressions and write them to [objects]
     *
     * NOTE: don't forget to sync all `VM.objects` with [objects]
     */
    fun reEval() {
        val cache = mutableMapOf<EXPR_ONE_TO_MANY, List<R?>>()
        val deps = tier2ixs.drop(1) // no need to calc for tier 0
        for (ixs in deps) {
            for (ix in ixs) {
                expressions[ix]?.let { expression ->
                    objects[ix] = expression.evaluateWithCache(cache)
                }
            }
        }
    }

    fun sortedByTier(indices: List<Ix>): List<Ix> =
        indices.sortedBy { ix2tier[it] }

    /** Copies those expressions whose dependencies are also in [sourceIndices].
     * REQUIRES all used object to had been added already. Additionally requires
     * [sourceIndices] to be SORTED in a way that a parent comes before its
     * child (e.g. via [sortedByTier]) */
    fun copyExpressionsWithDependencies(sourceIndices: List<Ix>) {
        val sources = sourceIndices.toSet()
        val oldSize = expressions.size
        val source2new = sourceIndices.mapIndexed { i, sourceIndex ->
            sourceIndex to (oldSize + i)
        }.toMap()
        for (sourceIndex in sourceIndices) {
            val e = expressions[sourceIndex]
            if (e != null && e.expr.args.all { it in sources }) {
                val newExpr = e.expr.reIndex { oldIx -> source2new[oldIx]!! }
                when (e) {
                    is ExprOutput.Just -> addSoloExpr(newExpr as EXPR_ONE_TO_ONE)
                    is ExprOutput.OneOf -> addMultiExpression(
                        ExprOutput.OneOf(newExpr as EXPR_ONE_TO_MANY, e.outputIndex)
                    )
                }
            } else {
                addFree()
            }
        }
    }

    /**
     * Change [Parameters] of all outputs of a given [Expr.OneToMany] to the one in [newExpr].
     * Assumption: old expr and [newExpr] are of the same type.
     * @param[targetIndices] indices of all [ExprOutput.OneOf] of the given expression
     * @param[reservedIndices] all indices that were ever used to hold results of the expr. They
     * must start with [targetIndices], and then potentially contain `null`-ed indices.
     * @return updated ([targetIndices], [reservedIndices], updated objects at new target indices (to be set))
     * */
    fun adjustMultiExpr(
        newExpr: EXPR_ONE_TO_MANY,
        targetIndices: List<Ix>,
        reservedIndices: List<Ix>,
    ): Triple<List<Ix>, List<Ix>, List<R?>> {
        if (targetIndices.isEmpty()) // idk why it can happen but i had witnessed it
            return Triple(targetIndices, reservedIndices, emptyList())
        val i0 = targetIndices.first()
        val oldExpr = expressions[i0]!!.expr
        require(oldExpr.args == newExpr.args && targetIndices.all { expressions[it]?.expr == oldExpr }) {
            "adjustMultiExpr($targetIndices, $reservedIndices, $newExpr)"
        }
        val tier = ix2tier[i0]!!
        var newMaxRange = reservedIndices
        val result0 = (newExpr as EXPR).evaluate(objects)
        val isPeriodic = isExprPeriodic(newExpr)
        val result = // small hack to avoid duplicating the original object
            if (isPeriodic && result0.isNotEmpty())
                result0.dropLast(1)
            else result0
        val sizeIncrease = result.size - targetIndices.size
        val newTargetIndices: List<Ix>
        if (sizeIncrease > 0) {
            val sizeOverflow = result.size - reservedIndices.size
            if (sizeOverflow > 0) {
                newMaxRange = newMaxRange + (expressions.size until expressions.size + sizeOverflow)
            }
            val addedIndices = newMaxRange.take(result.size).drop(targetIndices.size)
            for (parentIx in newExpr.args) {
                children[parentIx] = (children[parentIx] ?: emptySet()) + addedIndices
            }
            tier2ixs[tier] = tier2ixs[tier] + addedIndices
            for (ix in addedIndices)
                ix2tier[ix] = tier
            newTargetIndices = targetIndices + addedIndices
        } else if (sizeIncrease == 0) {
            newTargetIndices = targetIndices
        } else {
            val excessIndices = targetIndices.drop(result.size)
            deleteNodes(excessIndices)
            newTargetIndices = targetIndices.take(result.size)
        }
        for (i in result.indices) {
            val ix = newTargetIndices[i]
            expressions[ix] = ExprOutput.OneOf(newExpr, outputIndex = i)
        }
        parents2gluedIncidentPoints.clear()
        return Triple(newTargetIndices, newMaxRange, result)
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
     * You must either supply [expr0] or set `expressions[ix]` BEFORE calling this
     */
    private fun computeTier(ix: Ix, expr0: EXPR? = null): Tier {
        val expr: EXPR? = expr0 ?: expressions[ix]?.expr
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

    fun getAllChildren(parentIx: Ix): Set<Ix> {
        val directChildren = children[parentIx] ?: emptySet()
        val stack = ArrayDeque<Ix>(directChildren)
        val visited = mutableSetOf<Ix>()
        while (stack.isNotEmpty()) {
            val ix = stack.removeLast()
            val unexplored = visited.add(ix)
            if (unexplored) {
                children[ix]?.let { nextChildren ->
                    nextChildren.filterTo(stack) { it !in visited }
                }
            }
        }
        return visited
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

    private fun getIncidentChildren(carrierIndex: Ix): Set<Ix>? =
        children[carrierIndex]
            ?.filter { expressions[it]?.expr is Expr.Incidence }
            ?.toSet()

    // MAYBE: for performance, preemptively populate the cache somehow (after clearing)
    fun getGluedIncidentPoints(parents: Set<Ix>): Set<Ix> {
        val cached = parents2gluedIncidentPoints[parents]
        if (cached != null)
            return cached
        val tieredParents: Map<Tier, Set<Ix>> = parents
            .groupBy { ix2tier[it]!! }
            .mapValues { it.value.toSet() }
        val accumulatedParents = mutableSetOf<Ix>()
        var parentsOfThisTier: Set<Ix> = emptySet()
        var glued: Set<Ix> = emptySet()
        val gluedIncidentPoints = mutableSetOf<Ix>()
        for (tier in tier2ixs.indices) {
            parentsOfThisTier = (tieredParents[tier] ?: emptySet()) + glued
            if (parentsOfThisTier.isEmpty())
                continue
            accumulatedParents += parentsOfThisTier
            glued = parentsOfThisTier
                .flatMap { parent -> children[parent] ?: emptySet() }
//                .distinct()
                .filter { child ->
                    expressions[child]?.expr?.args?.all { arg -> arg in accumulatedParents } == true
                }.toSet()
            gluedIncidentPoints += glued.filter { child ->
                expressions[child]?.expr is Expr.Incidence && objects[child] != null
            }
        }
        parents2gluedIncidentPoints[parents] = gluedIncidentPoints
        return gluedIncidentPoints
    }

    /** For each incident point in [incidentPointIndices] project it onto its
     * carrier and set order appropriate to its present position. Should be used
     * after manually transforming the incident points. */
    abstract fun adjustIncidentPointExpressions(
        incidentPointIndices: Collection<Ix> = expressions.keys
    )

    @Suppress("NOTHING_TO_INLINE")
    inline fun getImmediateParents(childIx: Ix): List<Ix> =
        expressions[childIx]?.expr?.args.orEmpty()

    fun getAllParents(childs: List<Ix>): Set<Ix> {
        val directParents = mutableSetOf<Ix>()
        for (child in childs) {
            directParents += getImmediateParents(child)
        }
        val stack = ArrayDeque<Ix>(directParents)
        val visited = mutableSetOf<Ix>()
        while (stack.isNotEmpty()) {
            val ix = stack.removeLast()
            val unexplored = visited.add(ix)
            if (unexplored) {
                // removing filter slows it down, idk why
                getImmediateParents(ix).filterTo(stack) { it !in visited }
            }
        }
        return visited
    }

    fun findExistingIntersectionIndices(intersectorIndex1: Ix, intersectorIndex2: Ix): List<Ix> {
        // find points that are incident to both
        return expressions.keys.filter { pointIndex ->
            pointIndex != intersectorIndex1 && pointIndex != intersectorIndex2 &&
            testDependentIncidence(pointIndex, intersectorIndex1) &&
            testDependentIncidence(pointIndex, intersectorIndex2)
        }
    }

    abstract fun testDependentIncidence(pointIndex: Ix, carrierIndex: Ix): Boolean
}