package domain

import androidx.compose.ui.graphics.Color
import core.geometry.GCircle
import domain.Change.Transformation
import domain.cluster.LogicalRegion

/** User-produced changes to the Ddc state */
sealed interface Change {
    sealed interface Transformation {
        data class Translation(
            val dx: Double, val dy: Double
        ) : Transformation
        /** @param[angle] CCW, in degrees */
        data class Rotation(
            val pivotX: Double, val pivotY: Double,
            val angle: Float,
        ) : Transformation
        data class Scaling(
            val focusX: Double, val focusY: Double,
            val zoom: Double,
        ) : Transformation
    }
}

sealed interface LocalChange : Change {
    data class CreateObject(
        val index: Ix,
        val obj: GCircle?,
        val expression: Expression?,
        val color: Color?,
        val label: String?,
    ) : LocalChange, Diff
    data class DeleteObject(val index: Ix) : LocalChange, Diff

    data class Transform(val indices: List<Ix>, val transformation: Transformation) : LocalChange

    data class Expression(val index: Ix, val expression: domain.expressions.Expression?) : LocalChange, Diff
    data class Color(val index: Ix, val color: androidx.compose.ui.graphics.Color?) : LocalChange, Diff
    data class Label(val index: Ix, val label: String?) : LocalChange, Diff

    data class CreateRegion(val region: LogicalRegion) : LocalChange, Diff
    data class DeleteRegion(val index: Ix) : LocalChange, Diff
}

sealed interface GlobalChange : Change, Diff {
    data class ChangeBackgroundColor(val color: Color?) : GlobalChange
    data class ChangeCurrentRegionColor(val color: Color?) : GlobalChange
    data class ChangeChessboardColor(val color: Color?) : GlobalChange

    data class ChangeChessboardPattern(val pattern: ChessboardPattern) : GlobalChange

    data class ChangeCenter(val x: Float, val y: Float) : GlobalChange

    data class ChangeSelection(val selection: List<Ix>) : GlobalChange
    data class ChangePhantoms(val phantoms: Set<Ix>) : GlobalChange
}

/**
 * Difference between Ddc states, for compact [History].
 * Read each [Diff] as a potential action to happen in the future (for redo history).
 * For undo history each should be reverted using current state, so it would point to the past
 * (eg
 * `DeleteObject(index)` -> undo diff `CreateObject(index, <capture parameters
 * from the current state>`;
 * [LocalChange.Color]`(<new color>)` -> undo diff [LocalChange.Color]`(<old color>)`).
 */
sealed interface Diff {
    data class Transform(val changes: List<Pair<Ix, GCircle?>>) : Diff
}