package domain.expressions

import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import kotlin.math.exp

class ExpressionForest(
    initialExpressions: Map<Indexed, Expression?>, // pls include all possible indices
    // find indexed args -> downscale them -> eval expr -> upscale result
    private val get: (Indexed) -> GCircle?,
    private val set: (Indexed, GCircle?) -> Unit,
) {
    // for the VM.circles list null's correspond to unrealized outputs of multi-functions
    // here null's correspond to free objects
    val expressions: MutableMap<Indexed, Expression?> = initialExpressions.toMutableMap()

    /** parent index -> set of all its children with *direct* dependency */
    val children: MutableMap<Indexed, Set<Indexed>> = expressions
        .keys
        .associateWith { emptySet<Indexed>() }
        .toMutableMap()

    /** index -> tier */
    private val ix2tier: MutableMap<Indexed, Int> = initialExpressions
        .keys
        .associateWith { -1 } // stub
        .toMutableMap()
    /** tier -> indices */
    private val tier2ixs: MutableList<Set<Indexed>>

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

    private fun calculateNextPointIndex(): Indexed.Point {
        val i = expressions.keys
            .filterIsInstance<Indexed.Point>()
            .maxOfOrNull { it.index + 1 } ?: 0
        return Indexed.Point(i)
    }

    private fun calculateNextCircleIndex(): Indexed.Circle {
        val i = expressions.keys
            .filterIsInstance<Indexed.Circle>()
            .maxOfOrNull { it.index + 1 } ?: 0
        return Indexed.Circle(i)
    }

    fun addFreePoint() {
        val ix: Indexed.Point = calculateNextPointIndex()
        expressions[ix] = null
        children[ix] = emptySet()
        ix2tier[ix] = 0
        if (tier2ixs.isEmpty())
            tier2ixs += setOf(ix)
        else
            tier2ixs[0] = tier2ixs[0] + ix
    }

    fun addFreeCircle() {
        val ix: Indexed.Circle = calculateNextCircleIndex()
        expressions[ix] = null
        children[ix] = emptySet()
        ix2tier[ix] = 0
        if (tier2ixs.isEmpty())
            tier2ixs += setOf(ix)
        else
            tier2ixs[0] = tier2ixs[0] + ix
    }

    /** don't forget to upscale the result afterwards! */
    fun addSoloPointExpression(
        expr: Expr.OneToOne,
    ): Point? {
        val ix: Indexed.Point = calculateNextPointIndex()
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
        return (result.firstOrNull() as? Point)
            .also {
                println("$ix -> $expr -> $result")
            }
    }

    /** don't forget to upscale the result afterwards! */
    fun addSoloCircleExpression(
        expr: Expr.OneToOne,
    ): CircleOrLine? {
        val ix: Indexed.Circle = calculateNextCircleIndex()
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
        return (result.firstOrNull() as? CircleOrLine)
            .also {
                println("$ix -> $expr -> $result")
            }
    }

    /** don't forget to upscale the result afterwards! */
    fun addMultiExpression(
        expr: Expr.OneToMany,
        isPoint: Boolean,
    ): ExprResult {
        val result = expr.eval(get)
         if (isPoint) {
            val ix0: Indexed.Point = calculateNextPointIndex()
            val tier = computeTier(ix0, expr)
            repeat(result.size) { outputIndex ->
                val ix = Indexed.Point(ix0.index + outputIndex)
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
             println("$ix0 -> $expr -> $result")
        } else {
            val ix0: Indexed.Circle = calculateNextCircleIndex()
             val tier = computeTier(ix0, expr)
             repeat(result.size) { outputIndex ->
                 val ix = Indexed.Circle(ix0.index + outputIndex)
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
             println("$ix0 -> $expr -> $result")
        }
        return result
    }

    /** The new node still inherits its previous children */
    fun changeToFree(ix: Indexed) {
        if (expressions[ix] != null) {
            expressions[ix]?.let { previousExpr ->
                previousExpr.expr.args.forEach { parentIx ->
                    children[parentIx] = (children[parentIx] ?: emptySet()) - ix
                }
            }
            expressions[ix] = null
            val previousTier = ix2tier[ix]!!
            tier2ixs[previousTier] = tier2ixs[previousTier] - ix
            tier2ixs[0] = tier2ixs[0] + ix
            ix2tier[ix] = 0
        }
    }

    /** The new node still inherits its previous children */
    fun changeExpression(ix: Indexed, newExpr: Expr.OneToOne): GCircle? {
        expressions[ix]?.let { previousExpr ->
            previousExpr.expr.args.forEach { parentIx ->
                children[parentIx] = (children[parentIx] ?: emptySet()) - ix
            }
        }
        val previousTier = ix2tier[ix]!!
        tier2ixs[previousTier] = tier2ixs[previousTier] - ix
        ix2tier[ix] = -1
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
    fun deleteNodes(ixs: List<Indexed>): Set<Indexed> {
        val deleted = ixs.toMutableSet()
        var lvl = ixs
        while (lvl.isNotEmpty()) {
            lvl = lvl.flatMap { children[it] ?: emptySet() }
            deleted += lvl
        }
        for (d in deleted) {
            expressions[d] = null // do not delete it since we want to keep indices (keys) in sync with VM.circles
            children.remove(d)
            ix2tier[d] = -1
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
        changedIxs: List<Indexed>,
        // deltas (translation, rotation, scaling) to apply to immediate carried objects
    ) {
        val changed = mutableSetOf<Indexed>()
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
            if (ix2tier[ix] == -1) {
                ix2tier[ix] = computeTier(ix)
            }
        }
    }

    /**
     * note: either supply [expr0] or set `expressions[ix]` BEFORE calling this
     * */
    private fun computeTier(ix: Indexed, expr0: Expr? = null): Int {
        val expr: Expr? = expr0 ?: expressions[ix]?.expr
        return if (expr == null) {
            0
        } else {
            val argTiers = mutableSetOf<Int>()
            val args = expr.args
            for (subArg in args) {
                val t = ix2tier[subArg] ?: -1
                val tier: Int
                if (t == -1) {
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

    fun getIncidentPoints(parentIx: Indexed.Circle): List<Indexed.Point> =
        (children[parentIx] ?: emptySet())
            .filterIsInstance<Indexed.Point>()
            .filter { expressions[it]?.expr is Expr.Incidence }

    fun adjustIncidentPointExpressions(ix2point: Map<Indexed.Point, Point?>) {
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

}