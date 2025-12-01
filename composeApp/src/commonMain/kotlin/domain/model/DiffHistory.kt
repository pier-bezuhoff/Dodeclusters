package domain.model

import kotlinx.serialization.Serializable

/** [Diff] group for a singular undo/redo step */
typealias RedoGroup = List<Diff>

/**
 * `initialState <1:1< <1:2< <2:1< <3:1< NOW >4:1> >4:2> >4:3> >5:1>`
 *
 * Use [State] for serialization.
 */
class DiffHistory(
    private val initialState: SaveState,
    pastHistory: List<RedoGroup>,
    futureHistory: List<RedoGroup>,
) {
    private val past: ArrayDeque<RedoGroup> = ArrayDeque(HISTORY_SIZE)
    private val future: ArrayDeque<RedoGroup> = ArrayDeque(HISTORY_SIZE)

    private var continuousChange: Boolean = false

    val undoIsPossible: Boolean get() =
        past.isNotEmpty()
    val redoIsPossible: Boolean get() =
        past.isNotEmpty()

    init {
        past.addAll(pastHistory)
        future.addAll(futureHistory)
    }

    // subsequent continuous (same-target) actions don't change history
    // only append them after conclusion
    /**
     * @param[group] actions we are about to perform on the current [state]
     */
    fun record(group: RedoGroup, state: SaveState) {
        val undoStep = group
            .reversed()
            .map { Diff.revert(it, state) }
        if (past.size == HISTORY_SIZE) {
            past.removeFirst()
        }
        past.addLast(undoStep)
        future.clear()
    }

    fun undo(state: SaveState): RedoGroup {
        require(undoIsPossible)
        val undoStep = past.removeLast()
        val redoStep = undoStep
            .reversed()
            .map { Diff.revert(it, state) }
        if (future.size == HISTORY_SIZE) {
            future.removeLast()
        }
        future.addFirst(redoStep)
        return undoStep
    }

    fun redo(state: SaveState): RedoGroup {
        require(redoIsPossible)
        val redoStep = future.removeFirst()
        val undoStep = redoStep
            .reversed()
            .map { Diff.revert(it, state) }
        if (past.size == HISTORY_SIZE) {
            past.removeFirst()
        }
        past.addLast(undoStep)
        return redoStep
    }

    fun save(): State =
        State(
            initialState = initialState,
            past = past.toList(),
            future = future.toList(),
        )

    companion object {
        @Serializable
        data class State(
            @Serializable
            val initialState: SaveState,
            @Serializable
            val past: List<RedoGroup>,
            @Serializable
            val future: List<RedoGroup>,
        )

        fun <S> load(state: State): DiffHistory =
            DiffHistory(
                initialState = state.initialState,
                pastHistory = state.past,
                futureHistory = state.future,
            )

        private const val HISTORY_SIZE = 1_000
    }
}