package domain.model

import core.geometry.GCircle
import domain.ColorAsCss
import domain.Ix
import domain.SerializableOffset
import domain.cluster.LogicalRegion
import domain.expressions.Expression
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** EditorViewModel's save-state for history */
@Serializable
@SerialName("SaveState")
data class SaveState(
    val objects: List<GCircle?>,
    val objectColors: Map<Ix, ColorAsCss?>,
    val objectLabels: Map<Ix, String?>,
    val expressions: Map<Ix, Expression?>,
    val regions: List<LogicalRegion>,
    val backgroundColor: ColorAsCss?,
    val chessboardPattern: domain.settings.ChessboardPattern,
    val chessboardColor: ColorAsCss?,
    val phantoms: Set<Ix>,
    val selection: List<Ix>,
    val center: SerializableOffset,
    val regionColor: ColorAsCss?,
) {
    // since we generate save-state every undo-able action, i think it's prudent to
    // omit any validation

    @Serializable
    sealed interface Change {
        @Serializable
        sealed interface Location {
            @Serializable
            data class Objects(val indices: List<Ix>) : Location
            @Serializable
            data class ObjectColors(val indices: List<Ix>) : Location
            @Serializable
            data class ObjectLabels(val indices: List<Ix>) : Location
            @Serializable
            data class Expressions(val indices: List<Ix>) : Location
            @Serializable
            data object Regions : Location
            @Serializable
            data object BackgroundColor : Location
            @Serializable
            data object ChessboardPattern : Location
            @Serializable
            data object ChessboardColor : Location
            @Serializable
            data object Phantoms : Location
            @Serializable
            data object Selection : Location
            @Serializable
            data object Center : Location
            @Serializable
            data object RegionColor : Location
        }

        /** '+=' update-style incremental change */
        sealed interface Update : Change
        /** ':=' reassignment-style change */
        sealed interface Replacement : Change
        @Serializable
        data class Objects(val objects: Map<Ix, GCircle?>) : Update
        @Serializable
        data class ObjectColors(val colors: Map<Ix, ColorAsCss?>) : Update
        @Serializable
        data class ObjectLabels(val labels: Map<Ix, String?>) : Update
        @Serializable
        data class Expressions(val expressions: Map<Ix, Expression?>) : Update
        @Serializable
        data class Regions(val regions: List<LogicalRegion>) : Replacement
        @Serializable
        data class BackgroundColor(val color: ColorAsCss?) : Replacement
        @Serializable
        data class ChessboardPattern(val pattern: domain.settings.ChessboardPattern) : Replacement
        @Serializable
        data class ChessboardColor(val color: ColorAsCss?) : Replacement
        @Serializable
        data class Phantoms(val phantoms: Set<Ix>) : Replacement
        @Serializable
        data class Selection(val selection: List<Ix>) : Replacement
        @Serializable
        data class Center(val center: SerializableOffset) : Replacement
        @Serializable
        data class RegionColor(val color: ColorAsCss?) : Replacement
    }

    fun revert(changeLocation: Change.Location): Change =
        when (changeLocation) {
            is Change.Location.Objects ->
                Change.Objects(changeLocation.indices.associateWith { ix -> objects.getOrNull(ix) })
            is Change.Location.ObjectColors ->
                Change.ObjectColors(changeLocation.indices.associateWith { ix -> objectColors[ix] })
            is Change.Location.ObjectLabels ->
                Change.ObjectLabels(changeLocation.indices.associateWith { ix -> objectLabels[ix] })
            is Change.Location.Expressions ->
                Change.Expressions(changeLocation.indices.associateWith { ix -> expressions[ix] })
            is Change.Location.Regions ->
                Change.Regions(regions)
            is Change.Location.BackgroundColor ->
                Change.BackgroundColor(backgroundColor)
            is Change.Location.ChessboardPattern ->
                Change.ChessboardPattern(chessboardPattern)
            is Change.Location.ChessboardColor ->
                Change.ChessboardColor(chessboardColor)
            is Change.Location.Phantoms ->
                Change.Phantoms(phantoms)
            is Change.Location.Selection ->
                Change.Selection(selection)
            is Change.Location.Center ->
                Change.Center(center)
            is Change.Location.RegionColor ->
                Change.RegionColor(regionColor)
        }

    fun revert(change: Change): Change =
        when (change) {
            is Change.Objects ->
                Change.Objects(change.objects.mapValues { (ix, _) -> objects.getOrNull(ix) })
            is Change.ObjectColors ->
                Change.ObjectColors(change.colors.mapValues { (ix, _) -> objectColors[ix] })
            is Change.ObjectLabels ->
                Change.ObjectLabels(change.labels.mapValues { (ix, _) -> objectLabels[ix] })
            is Change.Expressions ->
                Change.Expressions(change.expressions.mapValues { (ix, _) -> expressions[ix] })
            is Change.Regions ->
                Change.Regions(regions)
            is Change.BackgroundColor ->
                Change.BackgroundColor(backgroundColor)
            is Change.ChessboardPattern ->
                Change.ChessboardPattern(chessboardPattern)
            is Change.ChessboardColor ->
                Change.ChessboardColor(chessboardColor)
            is Change.Phantoms ->
                Change.Phantoms(phantoms)
            is Change.Selection ->
                Change.Selection(selection)
            is Change.Center ->
                Change.Center(center)
            is Change.RegionColor ->
                Change.RegionColor(regionColor)
        }
}

