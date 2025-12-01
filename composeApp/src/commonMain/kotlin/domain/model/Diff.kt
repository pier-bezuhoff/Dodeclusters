package domain.model

import androidx.compose.ui.graphics.Color
import core.geometry.GCircle
import domain.ColorAsCss
import domain.Ix
import domain.cluster.LogicalRegion
import domain.expressions.Expression
import domain.settings.ChessboardPattern
import kotlinx.serialization.Serializable

// ':=' style
/**
 * Difference between Ddc states, for compact [History].
 * Read each [Diff] as a potential action to happen in the future (for redo history).
 * For undo history each should be reverted using current state, so it would point to the past
 *
 * `<diff1< <diff2< NOW >diff3> >diff4>`
 */
sealed interface Diff {
    @Serializable
    data class FullObject(
        val obj: GCircle?,
        val expression: Expression? = null,
        val color: ColorAsCss? = null,
        val label: String? = null,
    )

    @Serializable
    data class CreateObjects(val fullObjects: Map<Ix, FullObject>) : Diff
    @Serializable
    data class DeleteObjects(val indices: List<Ix>) : Diff
    @Serializable
    data class Expressions(val expressions: Map<Ix, Expression?>) : Diff
    @Serializable
    data class ObjectColors(val colors: Map<Ix, ColorAsCss?>) : Diff
    @Serializable
    data class ObjectLabels(val labels: Map<Ix, String?>) : Diff

    @Serializable
    data class Reposition(val objects: Map<Ix, GCircle?>) : Diff

    @Serializable
    data class Regions(val regions: List<LogicalRegion>) : Diff

    @Serializable
    data class BackgroundColor(val color: ColorAsCss?) : Diff
    @Serializable
    data class CurrentRegionColor(val color: ColorAsCss?) : Diff
    @Serializable
    data class ChessboardColor(val color: ColorAsCss?) : Diff

    @Serializable
    data class ChessboardPattern(val pattern: ChessboardPattern) : Diff

    @Serializable
    data class Center(val x: Float, val y: Float) : Diff

    @Serializable
    data class Selection(val selection: List<Ix>) : Diff
    @Serializable
    data class Phantoms(val phantoms: Set<Ix>) : Diff

    companion object {
        fun revert(
            diff: Diff,
            // present state
            objects: List<GCircle?>,
            objectColors: Map<Ix, Color>,
            objectLabels: Map<Ix, String>,
            expressions: Map<Ix, Expression?>,
            regions: List<LogicalRegion>,
            backgroundColor: Color?,
            chessboardPattern: ChessboardPattern,
            chessboardColor: Color?,
            phantoms: Set<Ix>,
            selection: List<Ix>,
            centerX: Float,
            centerY: Float,
            regionColor: Color?,
        ): Diff {
            // undo route:
            // ('=:' diff; now) -> (past; ':=' diff)
            // redo route:
            // (now; ':=' diff) -> ('=:' diff; future)
            return when (diff) {
                is CreateObjects ->
                    DeleteObjects(diff.fullObjects.keys.toList())
                is DeleteObjects ->
                    CreateObjects(
                        diff.indices.associateWith { ix ->
                            FullObject(
                                obj = objects[ix],
                                expression = expressions[ix],
                                color = objectColors[ix],
                                label = objectLabels[ix],
                            )
                        }
                    )
                is Reposition ->
                    Reposition(diff.objects.mapValues { (ix, _) -> objects[ix] })
                is Expressions ->
                    Expressions(diff.expressions.mapValues { (ix, _) -> expressions[ix] })
                is ObjectColors ->
                    ObjectColors(diff.colors.mapValues { (ix, _) -> objectColors[ix] })
                is ObjectLabels ->
                    ObjectLabels(diff.labels.mapValues { (ix, _) -> objectLabels[ix] })
                is Regions ->
                    Regions(regions)
                is BackgroundColor ->
                    BackgroundColor(backgroundColor)
                is Center ->
                    Center(centerX, centerY)
                is ChessboardColor ->
                    ChessboardColor(chessboardColor)
                is ChessboardPattern ->
                    ChessboardPattern(chessboardPattern)
                is CurrentRegionColor ->
                    CurrentRegionColor(regionColor)
                is Phantoms ->
                    Phantoms(phantoms)
                is Selection ->
                    Selection(selection)
            }
        }
    }
}