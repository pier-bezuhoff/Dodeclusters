package domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import core.geometry.GCircle
import domain.Ix
import domain.OffsetSerializer
import domain.cluster.LogicalRegion
import domain.expressions.ExpressionForest
import domain.model.Change.Transformation
import domain.settings.ChessboardPattern
import kotlinx.serialization.Serializable

/** User-produced changes to the Ddc state, aka Commands */
sealed interface Change {
    /**
     * Translation;Scaling;Rotation order.
     * @param[rotationAngle] CCW, in degrees
     */
    @Serializable
    data class Transformation(
        @Serializable(OffsetSerializer::class)
        val translation: Offset,
        @Serializable(OffsetSerializer::class)
        val focus: Offset,
        val zoom: Float,
        val rotationAngle: Float,
    )
}

// ':=' style
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
    // VM. { objectModel.transform; record new positions at indices here }
    data class Transform(val changes: Map<Ix, GCircle?>) : Diff

    companion object {
        fun revert(
            diff: Diff,
            objectModel: ObjectModel,
            expressionForest: ExpressionForest,
        ): Diff {
            // undo route:
            // ('=:' diff; now) -> (past; ':=' diff)
            // redo route:
            // (past; ':=' diff) -> ('=:' diff; now)
            return when (diff) {
                is Diff.Transform -> TODO()
                is LocalChange.Color ->
                    LocalChange.Color(diff.index, objectModel.objectColors[diff.index])
                is LocalChange.CreateObject ->
                    LocalChange.DeleteObject(diff.index)
                is LocalChange.CreateRegion ->
                    LocalChange.DeleteRegion()
                is LocalChange.DeleteObject -> TODO()
                is LocalChange.DeleteRegion -> TODO()
                is LocalChange.Expression ->
                    LocalChange.Expression(diff.index, expressionForest.expressions[diff.index])
                is LocalChange.Label ->
                    LocalChange.Label(diff.index,)
                is GlobalChange.ChangeBackgroundColor -> TODO()
                is GlobalChange.ChangeCenter -> TODO()
                is GlobalChange.ChangeChessboardColor -> TODO()
                is GlobalChange.ChangeChessboardPattern -> TODO()
                is GlobalChange.ChangeCurrentRegionColor -> TODO()
                is GlobalChange.ChangePhantoms -> TODO()
                is GlobalChange.ChangeSelection -> TODO()
            }
        }

    }
}