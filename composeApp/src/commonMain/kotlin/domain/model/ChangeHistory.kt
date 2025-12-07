package domain.model

import androidx.compose.runtime.MutableState
import domain.Ix
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
    private var pinnedState: SaveState = initialState,
    pastHistory: List<RedoGroup> = emptyList(),
    futureHistory: List<RedoGroup> = emptyList(),
    private val undoIsEnabled: MutableState<Boolean>? = null,
    private val redoIsEnabled: MutableState<Boolean>? = null,
) {
    /** Serializable DTO for [domain.model.ChangeHistory],
     * direct serialization is ugly bc @Transient init is handled weirdly */
    @Serializable
    @SerialName("HistoryState")
    data class State(
        @Serializable
        val initialState: SaveState,
        @Serializable
        val pinnedState: SaveState,
        @Serializable
        val past: List<RedoGroup>,
        @Serializable
        val future: List<RedoGroup>,
    ) {
        fun load(
            undoIsEnabled: MutableState<Boolean>? = null,
            redoIsEnabled: MutableState<Boolean>? = null,
        ): ChangeHistory =
            ChangeHistory(
                initialState = initialState,
                pinnedState = pinnedState,
                pastHistory = past,
                futureHistory = future,
                undoIsEnabled = undoIsEnabled,
                redoIsEnabled = redoIsEnabled,
            )

        companion object {
            val JSON_FORMAT = Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                // SaveState <- GCircle <- Point <-- can contain infinity
                allowSpecialFloatingPointValues = true
            }
        }
    }

    private val past: ArrayDeque<RedoGroup> = ArrayDeque(HISTORY_SIZE)
    private val future: ArrayDeque<RedoGroup> = ArrayDeque(HISTORY_SIZE)

    private var accumulatedChangedLocations: SaveState.Change.Locations =
        SaveState.Change.Locations.EMPTY
    /** already reverted with previous [pinnedState]; oldest to newest */
    private var accumulatedChanges: SaveState.Changes = SaveState.Changes.EMPTY
    private var continuousChange: ContinuousChangeType? = null

    private val undoIsPossible: Boolean get() =
        past.isNotEmpty()
    private val redoIsPossible: Boolean get() =
        future.isNotEmpty()

    init {
        past.addAll(pastHistory)
        future.addAll(futureHistory)
        refreshUndoRedoStates()
    }

    private fun refreshUndoRedoStates() {
        undoIsEnabled?.value = past.isNotEmpty()
        redoIsEnabled?.value = future.isNotEmpty()
    }

    /** @return if the continuous change is the first of this [type] */
    fun newContinuousChange(type: ContinuousChangeType?): Boolean {
        val isFirstChange = continuousChange != type
        continuousChange = type
        return isFirstChange
    }

    /** Save the present [state] as [pinnedState] to be used in later [record]s */
    fun pinState(state: SaveState) {
        accumulatedChanges = pinnedState.revert(accumulatedChangedLocations)
            .fuseLater(accumulatedChanges) // later <-> earlier cuz we reverse for undo
            .also { println("accumulated pre-pin changes: " + it.changes.joinToString()) }
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        pinnedState = state
        println("pinState $state")
    }

    fun accumulateChangedLocations(
        objectIndices: Set<Ix> = emptySet(),
        objectColorIndices: Set<Ix> = emptySet(),
        objectLabelIndices: Set<Ix> = emptySet(),
        expressionIndices: Set<Ix> = emptySet(),
        regions: Boolean = false,
        backgroundColor: Boolean = false,
        chessboardPattern: Boolean = false,
        chessboardColor: Boolean = false,
        phantoms: Boolean = false,
        selection: Boolean = false,
        center: Boolean = false,
        regionColor: Boolean = false,
    ) {
        accumulatedChangedLocations = accumulatedChangedLocations.accumulate(
            SaveState.Change.Locations(
                objectIndices = objectIndices,
                objectColorIndices = objectColorIndices,
                objectLabelIndices = objectLabelIndices,
                expressionIndices = expressionIndices,
                regions = regions,
                backgroundColor = backgroundColor,
                chessboardPattern = chessboardPattern,
                chessboardColor = chessboardColor,
                phantoms = phantoms,
                selection = selection,
                center = center,
                regionColor = regionColor,
            )
                .also { println("accumulateChanged: new locations = " + it.changed.joinToString(", ")) }
        )
    }

    /**
     * @param[objectsSize] resulting `objects.size` AFTER adding [count] new objects
     */
    fun accumulateNewObjects(objectsSize: Int, count: Int) {
        val startIndex = objectsSize - count
        val indices = (startIndex until objectsSize).toSet()
        accumulateChangedLocations(
            objectIndices = indices,
            expressionIndices = indices,
        )
    }

    /**
     * [record] locations created thru [accumulateChangedLocations] with state from [pinState]
     * Usage:
     * 1. Save the present [SaveState] using [pinState] (to [pinnedState])
     * 2. Apply changes
     * 3. Accumulate the changes by providing their location data to [accumulateChangedLocations]
     * 4. Commit them via [recordAccumulatedChanges]
     */
    fun recordAccumulatedChanges() {
        record(accumulatedChangedLocations, pinnedState)
    }

    // subsequent continuous (same-target) actions don't change the history
    // only append them after conclusion
    /**
     * @param[locations] locations of the actions have been performed on the [state]
     */
    private fun record(locations: SaveState.Change.Locations, state: SaveState = pinnedState) {
        // pinnedState > accumulatedChanged > laterChanges
        val laterChanges = state.revert(locations)
        val undoStep = laterChanges
            .fuseLater(accumulatedChanges) // later <-> earlier cuz we reverse for undo
            .changes
            .reversed()
        println("record: undoStep = " + undoStep.joinToString(", "))
        if (undoStep.isEmpty()) {
            println("W: attempting to record empty changes")
            return
        }
        if (past.size == HISTORY_SIZE) {
            past.removeFirst()
        }
        past.addLast(undoStep)
        future.clear()
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        accumulatedChanges = SaveState.Changes.EMPTY
        refreshUndoRedoStates()
        println(
            "past = " + past.joinToString(";\n", prefix = "[\n", postfix = "\n]") { group ->
                group.joinToString(", ")
            }
        )
    }

    fun undo(state: SaveState): SaveState {
        require(undoIsPossible)
        val undoStep = past.removeLast()
        val newState: SaveState
        val redoStep: RedoGroup
        if (past.isEmpty()) {
            redoStep = state.diff(initialState).changes
            newState = initialState
        } else {
            redoStep = undoStep
                .reversed()
                .map { state.revert(it) }
            newState = state.applyChanges(undoStep)
        }
        if (future.size == HISTORY_SIZE) {
            future.removeLast()
        }
        future.addFirst(redoStep)
        pinnedState = newState
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        accumulatedChanges = SaveState.Changes.EMPTY
        refreshUndoRedoStates()
        println("undo: undoStep = $undoStep")
        return newState
    }

    fun redo(state: SaveState): SaveState {
        require(redoIsPossible)
        val redoStep = future.removeFirst()
        val undoStep = redoStep
            .reversed()
            .map { state.revert(it) }
        if (past.size == HISTORY_SIZE) {
            past.removeFirst()
        }
        past.addLast(undoStep)
        val newState = state.applyChanges(redoStep)
        pinnedState = newState
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        accumulatedChanges = SaveState.Changes.EMPTY
        refreshUndoRedoStates()
        println("redo: redoStep = $redoStep")
        return newState
    }

    fun save(): State =
        State(
            initialState = initialState,
            pinnedState = pinnedState,
            past = past.toList(),
            future = future.toList(),
        )

    companion object {
        private const val HISTORY_SIZE = 1_000
    }
}

enum class ContinuousChangeType {
    ZOOM, SCALE_SLIDER,
}