package domain.expressions

import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Line
import data.geometry.Point
import domain.Ix
import kotlin.math.abs

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
private const val FREE_TIER: Tier = 0
/** stub for yet-to-be-computed tier */
private const val UNCALCULATED_TIER: Tier = -1
/** stub tier for forever-deleted object index */
private const val ABANDONED_TIER: Tier = -2

// MAYBE: cache carrier->incident points lookup (since it's called on every VM.transform)
/**
 * Class for managing expressions (AST controller)
 * @param[objects] reference to shared, downscaled mutable mirror-list of VM.objects
 */
class ExpressionForest(
    initialExpressions: Map<Ix, Expression?>, // pls include all possible indices
    private val objects: MutableList<GCircle?>,
) {
    // for the VM.objects list nulls correspond to unrealized outputs of multi-functions
    // here nulls correspond to free objects
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
                expr.eval(objects).firstOrNull()
            is Expression.OneOf -> {
                val results = multiExpressionCache.getOrPut(expr) {
                    expr.eval(objects)
                }
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
    fun addSoloExpr(expr: Expr.OneToOne): GCircle? {
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
        val result = expr.eval(objects)
        return result.firstOrNull()
            .also { println("$ix -> $expr -> $result") }
    }

    /** don't forget to upscale the result afterwards! */
    fun addMultiExpression(expression: Expression.OneOf): GCircle? {
        val expr = expression.expr
        val result = expr.eval(objects)[expression.outputIndex]
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
    fun addMultiExpr(expr: Expr.OneToMany): ExprResult {
        val periodicRotation =
            expr is Expr.LoxodromicMotion && expr.parameters.dilation == 0.0 && abs(expr.parameters.angle) == 360f
        val result = expr.eval(objects)
        val ix0 = calculateNextIndex()
        val tier = computeTier(ix0, expr)
        val resultSize =
            if (periodicRotation) result.size - 1 // small hack to avoid duplicating original object
            else result.size
        repeat(resultSize) { outputIndex ->
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

    fun findExpr(expr: Expr?): List<Ix> {
        return expressions.entries
            .filter { (_, e) -> e?.expr == expr }
            .map { it.key }
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
        val result = newExpr.eval(objects)
        return result.firstOrNull()
//            .also {
//                println("change $ix -> $newExpr -> $result")
//            }
    }

    /**
     * Delete [ixs] nodes and all of their children from the [ExpressionForest] by
     * setting [expressions]`[...] = null` and clearing [children], [ix2tier], [tier2ixs]
     * @return all the deleted nodes indices
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

    /**
     * Recursively re-evaluates expressions given that [changedIxs]/parents have changed
     * and updates [objects]
     *
     * NOTE: don't forget to sync `VM.objects` with [objects] at returned indices
     * @return all affected/child indices that were altered by [update] (excluding [changedIxs])
     */
    fun update(
        changedIxs: List<Ix>,
        // deltas (translation, rotation, scaling) to apply to immediate carried objects
    ): List<Ix> {
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
            // children always have non-null expressions
            objects[ix] = expressions[ix]?.eval(cache)
        }
        return toBeUpdated
    }

    /**
     * Re-evaluates all expressions and write them to [objects]
     *
     * NOTE: don't forget to sync all `VM.objects` with [objects]
     */
    fun reEval() {
        val cache = mutableMapOf<Expr.OneToMany, ExprResult>()
        val deps = tier2ixs.drop(1) // no need to calc for tier 0
        for (ixs in deps) {
            for (ix in ixs) {
                expressions[ix]?.let { expression ->
                    objects[ix] = expression.eval(cache)
                }
            }
        }
    }

    fun sortedByTier(ixs: List<Ix>): List<Ix> =
        ixs.sortedBy { ix2tier[it] }

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
                    is Expression.Just -> {
                        addSoloExpr(
                            newExpr as Expr.OneToOne
                        )
                    }
                    is Expression.OneOf -> {
                        addMultiExpression(
                            Expression.OneOf(newExpr as Expr.OneToMany, e.outputIndex)
                        )
                    }
                }
            } else {
                addFree()
            }
        }
    }

    /**
     * Change [Parameters] of all outputs of a given [Expr.OneToMany] to the one in [newExpr].
     * Assumption: old expr and [newExpr] are of the same type.
     * @param[targetIndices] indices of all [Expression.OneOf] of the given expression
     * @param[reservedIndices] all indices that were ever used to hold results of the expr. They
     * must start with [targetIndices], and then potentially contain `null`-ed indices.
     * @return updated ([targetIndices], [reservedIndices], updated objects at new target indices (to be set))
     * */
    fun adjustMultiExpr(
        newExpr: Expr.OneToMany,
        targetIndices: List<Ix>,
        reservedIndices: List<Ix>,
    ): Triple<List<Ix>, List<Ix>, List<GCircle?>> {
        if (targetIndices.isEmpty()) // idk why it can happen but i had witnessed it
            return Triple(targetIndices, reservedIndices, emptyList())
        val i0 = targetIndices.first()
        val oldExpr = expressions[i0]!!.expr
        require(oldExpr.args == newExpr.args && targetIndices.all { expressions[it]?.expr == oldExpr }) {
            "adjustMultiExpr($targetIndices, $reservedIndices, $newExpr)"
        }
        val tier = ix2tier[i0]!!
        var newMaxRange = reservedIndices
        val result = newExpr.eval(objects)
        val sizeIncrease = result.size - targetIndices.size
        val newTargetIndices: List<Ix>
        if (sizeIncrease > 0) {
            val sizeOverflow = result.size - reservedIndices.size
            if (sizeOverflow > 0) {
                newMaxRange = newMaxRange + (expressions.size until expressions.size + sizeOverflow)
            }
            val addedIndices = newMaxRange.take(result.size).drop(targetIndices.size)
            for (parentIx in newExpr.args)
                children[parentIx] = (children[parentIx] ?: emptySet()) + addedIndices
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
            expressions[ix] = Expression.OneOf(newExpr, outputIndex = i)
        }
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

    // MAYBE: transform into tailrec
    fun getAllChildren(parentIx: Ix): Set<Ix> {
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

    // MAYBE: cache
    fun getIncidentPoints(parentIx: Ix): List<Ix> =
        (children[parentIx] ?: emptySet())
            .filter { expressions[it]?.expr is Expr.Incidence }

    @Suppress("NOTHING_TO_INLINE")
    inline fun getIncidentPointsTo(parentIx: Ix, destination: MutableCollection<in Ix>) {
        (children[parentIx] ?: emptySet())
            .filterTo(destination) { expressions[it]?.expr is Expr.Incidence }
    }

    // still unsure about potentially better ways of doing it
    // especially for incident-p on dependent objects of those transformed
    fun adjustIncidentPointExpressions(incidentPointIndices: Collection<Ix> = expressions.keys) {
        for (ix in incidentPointIndices) {
            val expr = expressions[ix]?.expr
            val o = objects[ix]
            if (expr is Expr.Incidence && o is Point) {
                val parent = objects[expr.carrier]
                if (parent is CircleOrLine) {
                    expressions[ix] = Expression.Just(expr.copy(
                        parameters = IncidenceParameters(order = parent.point2order(o))
                    ))
                }
            }
        }
    }

    // FIX: NG, maybe bc of translation, idk
    /**
     * Adjust parameters of all points incident to lines,
     * scaling them by [zoom]. Should be used after uniformly scaling
     * all objects (e.g. when [get] scales source objects) to correctly zoom in/out
     * points on lines.
     * @return indices of changed expressions. You may want to [update]`()` them
     */
    fun scaleLineIncidenceExpressions(zoom: Double): List<Ix> {
        val changedIxs = mutableListOf<Ix>()
        for ((ix, e) in expressions) {
            val expr = e?.expr
            if (expr is Expr.Incidence && objects[expr.carrier] is Line) {
                expressions[ix] = Expression.Just(
                    expr.copy(IncidenceParameters(
                        order = zoom * expr.parameters.order
                    ))
                )
                changedIxs.add(ix)
            }
        }
        return changedIxs
    }

    fun getImmediateParents(childIx: Ix): List<Ix> =
        expressions[childIx]?.expr?.args.orEmpty()

    tailrec fun getAllParents(childs: List<Ix>, _result: Set<Ix> = emptySet()): Set<Ix> {
        val immediateParents = childs.flatMap { getImmediateParents(it) }
        if (immediateParents.isEmpty())
            return _result
        return getAllParents(
            immediateParents.minus(childs.toSet()),
            _result + immediateParents.toSet()
        )
    }

    fun findExistingIntersectionIndices(circleIndex1: Ix, circleIndex2: Ix): List<Ix> {
        // find points that are incident to both
        return expressions.keys.filter { pointIndex ->
            pointIndex != circleIndex1 && pointIndex != circleIndex2 &&
            testDependentIncidence(pointIndex, circleIndex1) &&
            testDependentIncidence(pointIndex, circleIndex2)
        }
    }

    fun testDependentIncidence(pointIndex: Ix, carrierIndex: Ix): Boolean {
        val pointExpr = expressions[pointIndex]?.expr
        val directIncidence =
            pointExpr is Expr.Incidence && pointExpr.carrier == carrierIndex ||
            pointExpr is Expr.Intersection && (pointExpr.circle1 == carrierIndex || pointExpr.circle2 == carrierIndex)
        val indirectIncidence = when (val carrierExpr = expressions[carrierIndex]?.expr) {
            is Expr.CircleByCenterAndRadius -> carrierExpr.radiusPoint == pointIndex
            is Expr.CircleBy3Points -> carrierExpr.object1 == pointIndex || carrierExpr.object2 == pointIndex || carrierExpr.object3 == pointIndex
            is Expr.CircleBy2PointsAndSagittaRatio -> carrierExpr.chordStartPoint == pointIndex || carrierExpr.chordEndPoint == pointIndex
            is Expr.CircleByPencilAndPoint ->
                carrierExpr.perpendicularObject == pointIndex || pointExpr is Expr.Intersection && (
                    pointExpr.circle1 == carrierExpr.pencilObject1 && pointExpr.circle2 == carrierExpr.pencilObject2 ||
                    pointExpr.circle1 == carrierExpr.pencilObject2 && pointExpr.circle2 == carrierExpr.pencilObject1
                )
            is Expr.LineBy2Points -> carrierExpr.object1 == pointIndex || carrierExpr.object2 == pointIndex
            // recursive transform check
            is Expr.TransformLike -> if (pointExpr is Expr.TransformLike) {
                val sameOutputIndex = (expressions[carrierIndex] as? Expression.OneOf)?.outputIndex == (expressions[pointIndex] as? Expression.OneOf)?.outputIndex
                val sameExpr = when (pointExpr) {
                    is Expr.CircleInversion -> pointExpr.copy(target = carrierIndex) == carrierExpr
                    is Expr.Rotation -> pointExpr.copy(target = carrierIndex) == carrierExpr
                    is Expr.BiInversion -> pointExpr.copy(target = carrierIndex) == carrierExpr
                    is Expr.LoxodromicMotion -> pointExpr.copy(target = carrierIndex) == carrierExpr
                }
                sameOutputIndex && sameExpr && testDependentIncidence(pointExpr.target, carrierExpr.target)
            } else false
            // NOTE: there could exist even more indirect, 2+ step incidence cases tbh
            else -> false
        }
        return directIncidence || indirectIncidence
    }
}
