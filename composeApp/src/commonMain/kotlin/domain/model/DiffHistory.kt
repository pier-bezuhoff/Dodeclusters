package domain.model

typealias DiffGroup = List<Diff>

// MAYBE: add very distant past where all same-targets diffs would be fused

class DiffHistory(
    private val originalState: Unit
) {
    private val distantPast = ArrayDeque<DiffGroup>(DISTANT_HISTORY_SIZE)
    private val recentPast = ArrayDeque<DiffGroup>(RECENT_HISTORY_SIZE)
    private val recentFuture = ArrayDeque<DiffGroup>(RECENT_HISTORY_SIZE)
    private val distantFuture = ArrayDeque<DiffGroup>(DISTANT_HISTORY_SIZE)

    private var continuousChange: Boolean = false

    val undoIsPossible: Boolean get() =
        recentPast.isNotEmpty()
    val redoIsPossible: Boolean get() =
        recentFuture.isNotEmpty()

    private fun fuseContinuousChange(diff1: DiffGroup, diff2: DiffGroup): DiffGroup {
        val prev = diff1.singleOrNull()
        val next = diff2.singleOrNull()
        if (prev is Diff.Transform && next is Diff.Transform) {
            val changes = prev.changes.toMutableMap()
            val nextChanges = next.changes
            changes.putAll(nextChanges)
            return listOf(Diff.Transform(changes))
        } else {
            return diff1 + diff2
        }
    }

    private fun fuseContinuousChange(diffs: DiffGroup): DiffGroup {
        if (diffs.size < 2) {
            return diffs
        }
        val allDiffs = mutableListOf<Diff>()
        var i = 0
        while (i < diffs.size - 1) {
            val prev = diffs[i]
            val next = diffs[i + 1]
            when {
                prev is Diff.Transform && next is Diff.Transform -> {
                    val changes = prev.changes.toMutableMap()
                    val nextChanges = next.changes
                    changes.putAll(nextChanges)
                    allDiffs.add(
                        Diff.Transform(changes)
                    )
                    i += 2
                }
                else -> {
                    allDiffs.add(prev)
                    i += 1
                }
            }
        }
        if (i == diffs.size - 1) {
            allDiffs.add(diffs.last())
        }
        return allDiffs
    }

    private fun fuseSameTagSameTargets(diffs1: DiffGroup, diffs2: DiffGroup?): DiffGroup? {
        TODO()
    }

    fun recordDiff(diffs: DiffGroup, continuous: Boolean = false) {
        continuousChange = continuous
        if (diffs.isNotEmpty()) {
            TODO()
        }
    }

    fun undo() {
        require(undoIsPossible)
        val diffs = recentPast.removeLast()
        if (distantPast.isNotEmpty()) {
            recentPast.addFirst(
                distantPast.removeLast()
            )
        }
        // TODO: transform past diff into future diff
        //  using current state
        val futureDiffs = diffs
        if (recentFuture.size == RECENT_HISTORY_SIZE) {
            val lastRecentFuture = recentFuture.removeLast()
            val firstDistantFuture = distantFuture.firstOrNull()
            val fused = fuseSameTagSameTargets(lastRecentFuture, firstDistantFuture)
            if (fused != null) {
                distantFuture.removeFirst()
                distantFuture.addFirst(fused)
            } else {
                if (distantFuture.size == DISTANT_HISTORY_SIZE) {
                    // theoretically this should never happen i think
                    distantFuture.removeLast()
                }
                distantFuture.addFirst(lastRecentFuture)
            }
        }
        recentFuture.addFirst(futureDiffs)
    }

    fun redo() {}

    companion object {
        private const val RECENT_HISTORY_SIZE = 10
        private const val DISTANT_HISTORY_SIZE = 200
    }
}