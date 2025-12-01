package domain.model

import kotlinx.serialization.Serializable

/** [Diff] group for a singular undo/redo step */
typealias RedoGroup = List<Diff>

class DiffHistory(
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

    fun recordDiff(diff: Diff, continuous: Boolean = false) {
        continuousChange = continuous
        TODO()
    }

    fun undo() {
        require(undoIsPossible)
        val diff = past.removeLast()
        // TODO: transform past diff into future diff
        //  using current state
        val futureDiff = diff
        if (future.size == HISTORY_SIZE) {
            future.removeLast()
        }
        future.addFirst(futureDiff)
    }

    fun redo() {
        require(redoIsPossible)
        val diff = future.removeFirst()
        // TODO: transform future diff into past diff
        //  using current state
        val pastDiff = diff
        if (past.size == HISTORY_SIZE) {
            past.removeFirst()
        }
        past.addLast(pastDiff)
    }

    fun save(): State =
        State(
            past = past.toList(),
            future = future.toList(),
        )

    companion object {
        @Serializable
        data class State(
            @Serializable
            val past: List<RedoGroup>,
            @Serializable
            val future: List<RedoGroup>,
        )

        fun load(state: State): DiffHistory =
            DiffHistory(
                pastHistory = state.past,
                futureHistory = state.future,
            )

        private const val HISTORY_SIZE = 1_000
    }
}