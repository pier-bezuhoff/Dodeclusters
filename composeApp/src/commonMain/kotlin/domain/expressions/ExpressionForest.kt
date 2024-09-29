package domain.expressions

import data.geometry.GCircle

class ExpressionForest(
    initialExpressions: Map<Arg.Indexed, Expression?>, // pls include all possible indices
    // find indexed args -> downscale them -> eval expr -> upscale result
    private val get: (Arg.Indexed) -> GCircle?,
    private val set: (Arg.Indexed, GCircle?) -> Unit,
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
    private val ix2tier: MutableMap<Arg.Indexed, Int> = initialExpressions
        .keys
        .associateWith { -1 }
        .toMutableMap()
    /** tier -> indices */
    private val tier2ixs: MutableList<Set<Arg.Indexed>>

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
                        children[parentIx] = children[parentIx]!! + ix
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

    fun addSoloExpression(isPoint: Boolean, expr: Expr.OneToOne) {
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
        expr.args.forEach { parentIx ->
            children[parentIx] = children[parentIx]!! + ix
        }
        val tier = computeTier(ix)
        ix2tier[ix] = tier
        if (tier < tier2ixs.size) {
            tier2ixs[tier] = tier2ixs[tier] + ix
        } else { // no hopping over tiers, we good
            tier2ixs.add(setOf(ix))
        }
        val result = expr.eval(get)
        set(ix, result.firstOrNull())
    }

    fun addMultiExpression(isPoint: Boolean, expr: Expr.OneToMany) {
        val result = expr.eval(get)
         if (isPoint) {
            val i = expressions.keys
                .filterIsInstance<Arg.Indexed.Point>()
                .maxOfOrNull { it.index + 1 } ?: 0
            val ix0 = Arg.Indexed.Point(i)
            val tier = computeTier(ix0)
            repeat(result.size) { outputIndex ->
                val ix = Arg.Indexed.Point(ix0.index + outputIndex)
                expressions[ix] = Expression.OneOf(expr, outputIndex)
                expr.args.forEach { parentIx ->
                    children[parentIx] = children[parentIx]!! + ix
                }
                ix2tier[ix] = tier
                if (tier < tier2ixs.size) {
                    tier2ixs[tier] = tier2ixs[tier] + ix
                } else { // no hopping over tiers, we good
                    tier2ixs.add(setOf(ix))
                }
                set(ix, result[outputIndex])
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
                 expr.args.forEach { parentIx ->
                     children[parentIx] = children[parentIx]!! + ix
                 }
                 ix2tier[ix] = tier
                 if (tier < tier2ixs.size) {
                     tier2ixs[tier] = tier2ixs[tier] + ix
                 } else { // no hopping over tiers, we good
                     tier2ixs.add(setOf(ix))
                 }
                 set(ix, result[outputIndex])
             }
        }
    }

    /**
     * delete [ix] node and all of its children
     * @return all deleted nodes
     * */
    fun deleteNode(ix: Arg.Indexed): Set<Arg.Indexed> {
        val deleted = mutableSetOf(ix)
        var lvl = listOf(ix)
        while (lvl.isNotEmpty()) {
            lvl = lvl.flatMap { children[it] ?: emptySet() }
            deleted += lvl
        }
        for (d in deleted) {
            children.remove(d)
            ix2tier[d] = -1
        }
        for ((parentIx, childs) in children)
            children[parentIx] = childs - deleted
        for ((tier, ixs) in tier2ixs.withIndex())
            tier2ixs[tier] = ixs - deleted
        for (d in deleted) {
            set(d, null)
        }
        return deleted
    }

    /** recursively re-evaluates expressions given that [changedIxs] have changed
     * and updates via [set] */
    fun update(
        changedIxs: List<Arg.Indexed>,
        // deltas (translation, rotation, scaling) to apply to immediate carried objects
    ) {
        val changed = changedIxs.toMutableSet()
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