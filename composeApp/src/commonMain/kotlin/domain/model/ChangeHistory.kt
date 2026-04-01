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
 * Usage: [pinState] -> [accumulateChangedLocations] -> [recordAccumulatedChanges]
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
    @Immutable
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

    /** Locations, changed after most recent [pinState] */
    private var accumulatedChangedLocations: SaveState.Change.Locations =
        SaveState.Change.Locations.EMPTY
    /**
     * `[latest pin -> last unrecorded pin]` changes.
     *
     * Locations from `pin1->locations->pin2` are reverted via `pin1` to `[pin2->pin1]` changes.
     * [accumulatedChangedLocations] get converted here if they were NOT recorded, but
     * rather pin-pin squished.
     *
     * [accumulatedUnrecordedChanges] > [pinnedState] > [accumulatedChangedLocations] > now
     */
    private var accumulatedUnrecordedChanges: SaveState.Changes = SaveState.Changes.EMPTY
    /** primarily used for continuous actions without clearly defined start or end */
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

    /** Save the present [state] as [pinnedState] to be used in later [record]s */
    fun pinState(state: SaveState) {
        // pin1->accumulate1->pin2->accumulate2->pin3
        // after pin2:
        //   accumulatedChanges := pin1.revert(accumulateChangedLocations[pin1->pin2] =
        //   = [pin2->pin1]
        // after pin3: [pin3->pin2].fuseLater[pin2->pin1] = [pin3->pin1]
        accumulatedUnrecordedChanges = pinnedState.revert(accumulatedChangedLocations)
            .fuseLater(accumulatedUnrecordedChanges) // later <-> earlier cuz we reverse for undo
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        pinnedState = state
    }

    /**
     * @param[newIndices] shorthand for [objectIndices] + [expressionIndices]
     * @param[changedIndices] shorthand for [objectIndices] + [expressionIndices]
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

    /**
     * [record] locations created thru [accumulateChangedLocations] with the state from [pinState]
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
        if (locations == SaveState.Change.Locations.EMPTY) {
//            println("record: skipped recording: no changed locations")
//            println("accumulated skipped changes: " + accumulatedChanges.changes.joinToString())
            // accumulatedChanges remain as-is
            return
        }
        // last recording > accumulatedChanged > pinnedState > laterChanges
        val laterChanges = state.revert(locations)
        val undoChanges = laterChanges
            .fuseLater(accumulatedUnrecordedChanges) // later <-> earlier cuz we reverse for undo
        val undoStep = undoChanges.changes.reversed()
//        println("record: undoStep = " + undoStep.joinToString(", "))
        if (!areChangesWorthRecording(undoChanges) || undoStep.isEmpty()) {
            accumulatedUnrecordedChanges = undoChanges
            accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
//            println("record: skipped recording: no changes worth recording")
//            println("accumulated skipped changes: " + accumulatedChanges.changes.joinToString())
            return
        }
        if (past.size == HISTORY_SIZE) {
            past.removeFirst()
        }
        past.addLast(undoStep)
        future.clear()
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        accumulatedUnrecordedChanges = SaveState.Changes.EMPTY
        // we dont reset continuousChange here
        refreshUndoRedoStates()
//        println(
//            "past = " + past.joinToString(";\n", prefix = "[\n", postfix = "\n]") { group ->
//                group.joinToString(", ")
//            }
//        )
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
     * @param[state] the present state
     * @return the previous state, the one before applying the most recent [RedoGroup]
     */
    fun undo(state: SaveState): SaveState {
        require(undoIsPossible)
        val undoStep = past.removeLast()
        val unrecordedChangesRedo = accumulatedUnrecordedChanges
            .fuseLater(state.revert(accumulatedChangedLocations))
        val newState: SaveState
        val redoStep: RedoGroup
        if (past.isEmpty()) {
            redoStep = state.diff(initialState).changes
            // since we diff with the present state, no need to count unrecordedChanges
            newState = initialState
        } else {
            redoStep = undoStep
                .reversed()
                .map { state.revert(it) }
                .plus(unrecordedChangesRedo.changes)
            newState = state.applyChanges(undoStep)
        }
        if (future.size == HISTORY_SIZE) {
            future.removeLast()
        }
        future.addFirst(redoStep)
        pinnedState = newState
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        accumulatedUnrecordedChanges = SaveState.Changes.EMPTY
        continuousChange = null
        refreshUndoRedoStates()
//        println("undo: undoStep = $undoStep")
        return newState
    }

    /** @return the 'next' state in the undo/redo dequeue, the one we undone from
     * @param[state] the present state */
    fun redo(state: SaveState): SaveState {
        require(redoIsPossible)
        val unrecordedChangesUndo = pinnedState.revert(accumulatedChangedLocations)
            .fuseLater(accumulatedUnrecordedChanges)
        // we don't update undoStep with unrecorded changes
        // so that undo->redo-> can be repeated indefinitely with no surprises
        val redoStep = future.removeFirst()
        val undoStep = redoStep
            .reversed()
            .map { state.revert(it) }
        if (past.size == HISTORY_SIZE) {
            past.removeFirst()
        }
        past.addLast(undoStep)
        val newState = state.applyChanges(unrecordedChangesUndo.changes + redoStep)
        pinnedState = newState
        accumulatedChangedLocations = SaveState.Change.Locations.EMPTY
        accumulatedUnrecordedChanges = SaveState.Changes.EMPTY
        continuousChange = null
        refreshUndoRedoStates()
//        println("redo: redoStep = $redoStep")
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

/** The type of continuous change */
enum class ContinuousChange {
    DRAG,
    ZOOM,
    SCALE_SLIDER,
}