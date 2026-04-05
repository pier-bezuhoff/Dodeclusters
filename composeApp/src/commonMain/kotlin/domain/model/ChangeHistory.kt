package domain.model

import androidx.compose.runtime.Immutable
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
 * Usage: maybe [accumulateChangedLocations] -> [recordDiff].
 * When a change is not followed by recording, accumulate it for proper undo.
 *
 * Use [State] for serialization.
 */
class ChangeHistory(
    private val initialState: SaveState,
    private var lastRecordedState: SaveState = initialState,
    pastHistory: List<RedoGroup> = emptyList(),
    futureHistory: List<RedoGroup> = emptyList(),
    private val undoIsEnabled: MutableState<Boolean>? = null,
    private val redoIsEnabled: MutableState<Boolean>? = null,
) {
    /** Serializable DTO for [domain.model.ChangeHistory],
     * direct serialization is ugly bc @Transient init is handled weirdly */
    @Immutable
    @Serializable
    @SerialName("HistoryState")
    data class State(
        @Serializable
        @SerialName("initialState")
        val initialState: SaveState,
        @Serializable
        @SerialName("lastRecordedState")
        val lastRecordedState: SaveState,
        @Serializable
        @SerialName("past")
        val past: List<RedoGroup>,
        @Serializable
        @SerialName("future")
        val future: List<RedoGroup>,
    ) {
        fun load(
            undoIsEnabled: MutableState<Boolean>? = null,
            redoIsEnabled: MutableState<Boolean>? = null,
        ): ChangeHistory =
            ChangeHistory(
                initialState = initialState,
                lastRecordedState = lastRecordedState,
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

    /** Locations, changed after most recent [lastRecordedState] */
    private var accumulatedChangedLocations: SaveState.Change.Locations =
        SaveState.Change.Locations.EMPTY
    /** Ongoing continuous change type,
     * primarily used for continuous actions without clearly defined start or end */
    private var continuousChange: ContinuousChange? = null

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

    /**
     * Use it to trigger on-start event for continuous changes.
     * [continuousChange] resets in VM.onDown
     * @return if the continuous change is the first of this type
     * */
    fun newContinuousChange(newContinuousChange: ContinuousChange?): Boolean {
        val isFirstChange = continuousChange != newContinuousChange
        continuousChange = newContinuousChange
        return isFirstChange
    }

    /**
     * Optionally use for not-yet-recorded changes
     * @param[newIndices] shorthand for [objectIndices] + [expressionIndices]
     * @param[allIndices] shorthand for [objectIndices] + [expressionIndices] +
     * [borderColorIndices] + [fillColorIndices] + [labelIndices]
     */
    fun accumulateChangedLocations(
        objectIndices: Set<Ix> = emptySet(),
        expressionIndices: Set<Ix> = emptySet(),
        borderColorIndices: Set<Ix> = emptySet(),
        fillColorIndices: Set<Ix> = emptySet(),
        labelIndices: Set<Ix> = emptySet(),
        regions: Boolean = false,
        backgroundColor: Boolean = false,
        chessboardPattern: Boolean = false,
        chessboardColor: Boolean = false,
        phantoms: Boolean = false,
        selection: Boolean = false,
        center: Boolean = false,
        regionColor: Boolean = false,
        //
        newIndices: Set<Ix> = emptySet(),
        allIndices: Set<Ix> = emptySet(),
        //
        continuousChange: ContinuousChange? = null,
    ) {
        accumulatedChangedLocations = accumulatedChangedLocations.accumulate(
            SaveState.Change.Locations(
                objectIndices = objectIndices + newIndices + allIndices,
                expressionIndices = expressionIndices + newIndices + allIndices,
                borderColorIndices = borderColorIndices + allIndices,
                fillColorIndices = fillColorIndices + allIndices,
                labelIndices = labelIndices + allIndices,
                regions = regions,
                backgroundColor = backgroundColor,
                chessboardPattern = chessboardPattern,
                chessboardColor = chessboardColor,
                phantoms = phantoms,
                selection = selection,
                center = center,
                regionColor = regionColor,
            )
        )
        this.continuousChange = continuousChange
    }

    fun recordDiff(
        newRecordedState: SaveState,
    ) {
        val accumulatedChanges = lastRecordedState.revert(accumulatedChangedLocations)
        val diffChanges = lastRecordedState.revert(
            newRecordedState.diff(lastRecordedState).locations
        )
        val undoChanges = diffChanges.fuseLater(accumulatedChanges) // reversing order for undo
        if (areChangesWorthRecording(undoChanges)) {
            if (past.size == HISTORY_SIZE) {
                past.removeFirst()
            }
            val undoStep = undoChanges.changes
            past.addLast(undoStep)
            future.clear()
            accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
            // we dont reset continuousChange here
            refreshUndoRedoStates()
            lastRecordedState = newRecordedState
        } else {
            accumulatedChangedLocations = diffChanges.locations
            println("recordDiff: skipped recording: no changes worth recording")
            println("accumulated skipped changes: " + diffChanges.changes.joinToString())
        }
    }

    // not just selection, center and regionColor have changed
    private fun areChangesWorthRecording(changes: SaveState.Changes): Boolean =
        changes != SaveState.Changes.EMPTY.copy(
            selection = changes.selection,
            center = changes.center,
            regionColor = changes.regionColor,
        )

    // MAYBE: restore most recent selection after undo
    /**
     * We backtrack to a past state, saving ([presentState] - past state) as `redoStep` to [future]
     * @param[presentState] [lastRecordedState] + alpha (unrecorded [accumulatedChangedLocations])
     * @return the previous state, the one before applying the most recent [RedoGroup]
     */
    fun undo(presentState: SaveState): SaveState {
        require(undoIsPossible)
        val newState: SaveState
        val redoStep: RedoGroup
        if (past.isEmpty()) {
            redoStep = presentState.diff(initialState).changes
            // since we diff with the present state, no need to count unrecorded changes
            newState = initialState
        } else {
            /** [presentState] - [lastRecordedState] */
            val unrecordedChanges = presentState.revert(accumulatedChangedLocations)
            val undoStep = past.removeLast()
            redoStep = undoStep // past state -> presentState
                .reversed()
                .map { presentState.revert(it) } // lastRecordedState - past state
                .plus(unrecordedChanges.changes) // presentState - lastRecordedState
            newState = lastRecordedState.applyChanges(undoStep)
        }
        if (future.size == HISTORY_SIZE) { // shouldn't be possible
            future.removeLast()
        }
        future.addFirst(redoStep)
        lastRecordedState = newState
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        continuousChange = null
        refreshUndoRedoStates()
        return newState
    }

    /**
     * We ignore [presentState] and unrecorded alpha and simply start at
     * [lastRecordedState], then jump to the future state, saving this jump as `undoStep` to [past]
     * @param[presentState] [lastRecordedState] + alpha (unrecorded [accumulatedChangedLocations])
     * @return the 'next'/future state in the undo/redo dequeue, the one we undone from before
     */
    fun redo(presentState: SaveState): SaveState {
        require(redoIsPossible)
        val redoStep = future.removeFirst()
        // we simply discard unrecorded accumulatedChangedLocations
        val undoStep = redoStep
            .reversed()
            .map { lastRecordedState.revert(it) }
        if (past.size == HISTORY_SIZE) {
            past.removeFirst()
        }
        past.addLast(undoStep)
        val newState = lastRecordedState.applyChanges(redoStep)
        lastRecordedState = newState
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        continuousChange = null
        refreshUndoRedoStates()
        return newState
    }

    fun save(): State =
        State(
            initialState = initialState,
            lastRecordedState = this@ChangeHistory.lastRecordedState,
            past = past.toList(),
            future = future.toList(),
        )

    companion object {
        private const val HISTORY_SIZE = 1_000
    }
}

/** The type of continuous change */
enum class ContinuousChange {
    DRAG,
    ZOOM,
    SCALE_SLIDER,
}