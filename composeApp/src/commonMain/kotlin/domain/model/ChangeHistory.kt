package domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** [SaveState.Change] group for a singular undo/redo step.
 * It is assumed that all grouped changes are 'perpendicular' */
typealias RedoGroup = List<SaveState.Change>

/**
 * `initialState <1:1< <1:2< <2:1< <3:1< NOW >4:1> >4:2> >4:3> >5:1>`
 *
 * Use [State] for serialization.
 */
class ChangeHistory(
    private val initialState: SaveState,
    pastHistory: List<RedoGroup>,
    futureHistory: List<RedoGroup>,
) {
    /** Serializable DTO for [domain.model.ChangeHistory],
     * direct serialization is ugly bc @Transient init is handled weirdly */
    @Serializable
    @SerialName("HistoryState")
    data class State(
        @Serializable
        val initialState: SaveState,
        @Serializable
        val past: List<RedoGroup>,
        @Serializable
        val future: List<RedoGroup>,
    ) {
        fun load(): ChangeHistory =
            ChangeHistory(
                initialState = initialState,
                pastHistory = past,
                futureHistory = future,
            )

        companion object {
            val JSON_FORMAT = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        }
    }

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

    // subsequent continuous (same-target) actions don't change the history
    // only append them after conclusion
    /**
     * Usage:
     * 1. save current [state] as [SaveState]
     * 2. apply changes
     * 3. record changes using previously saved [state]
     * @param[locations] locations of actions we are to perform on the [state]
     */
    fun record(locations: Set<SaveState.Change.Location>, state: SaveState) {
        val undoStep = locations.map { state.revert(it) }
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
            .map { state.revert(it) }
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
            .map { state.revert(it) }
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


        private const val HISTORY_SIZE = 1_000
    }
}