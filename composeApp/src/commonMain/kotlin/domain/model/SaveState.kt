package domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import core.geometry.GCircle
import domain.ColorAsCss
import domain.Ix
import domain.SerializableOffset
import domain.cluster.LogicalRegion
import domain.expressions.Expression
import domain.settings.ChessboardPattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.exp

/** EditorViewModel's save-state for history.
 * [objects].indices must span all of [objectColors].keys and [objectLabels].keys.
 * And [objects].indices == [expressions].keys */
@Serializable
@SerialName("SaveState")
data class SaveState(
    val objects: List<GCircle?>,
    val objectColors: Map<Ix, ColorAsCss>,
    val objectLabels: Map<Ix, String>,
    val expressions: Map<Ix, Expression?>,
    val regions: List<LogicalRegion>,
    val backgroundColor: ColorAsCss?,
    val chessboardPattern: ChessboardPattern,
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
            data class Objects(val indices: Set<Ix>) : Location
            @Serializable
            data class ObjectColors(val indices: Set<Ix>) : Location
            @Serializable
            data class ObjectLabels(val indices: Set<Ix>) : Location
            @Serializable
            data class Expressions(val indices: Set<Ix>) : Location
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

        @Serializable
        data class Locations(
            val objectIndices: Set<Ix> = emptySet(),
            val objectColorIndices: Set<Ix> = emptySet(),
            val objectLabelIndices: Set<Ix> = emptySet(),
            val expressionIndices: Set<Ix> = emptySet(),
            val regions: Boolean = false,
            val backgroundColor: Boolean = false,
            val chessboardPattern: Boolean = false,
            val chessboardColor: Boolean = false,
            val phantoms: Boolean = false,
            val selection: Boolean = false,
            val center: Boolean = false,
            val regionColor: Boolean = false,
        ) {
            inline val objectsLocation: Location.Objects? get() =
                if (objectIndices.isEmpty()) null else Location.Objects(objectIndices)
            inline val objectColorsLocation: Location.ObjectColors? get() =
                if (objectColorIndices.isEmpty()) null else Location.ObjectColors(objectColorIndices)
            inline val objectLabelsLocation: Location.ObjectLabels? get() =
                if (objectLabelIndices.isEmpty()) null else Location.ObjectLabels(objectLabelIndices)
            inline val expressionsLocation: Location.Expressions? get() =
                if (expressionIndices.isEmpty()) null else Location.Expressions(expressionIndices)
            inline val regionsLocation: Location.Regions? get() =
                if (regions) Location.Regions else null
            inline val backgroundColorLocation: Location.BackgroundColor? get() =
                if (backgroundColor) Location.BackgroundColor else null
            inline val chessboardPatternLocation: Location.ChessboardPattern? get() =
                if (chessboardPattern) Location.ChessboardPattern else null
            inline val chessboardColorLocation: Location.ChessboardColor? get() =
                if (chessboardColor) Location.ChessboardColor else null
            inline val phantomsLocation: Location.Phantoms? get() =
                if (phantoms) Location.Phantoms else null
            inline val selectionLocation: Location.Selection? get() =
                if (selection) Location.Selection else null
            inline val centerLocation: Location.Center? get() =
                if (center) Location.Center else null
            inline val regionColorLocation: Location.RegionColor? get() =
                if (regionColor) Location.RegionColor else null

            val changed: List<Location> = listOfNotNull(
                objectsLocation, objectColorsLocation, objectLabelsLocation,
                expressionsLocation,
                regionsLocation,
                backgroundColorLocation,
                chessboardPatternLocation, chessboardColorLocation,
                phantomsLocation,
                selectionLocation,
                centerLocation,
                regionColorLocation,
            )

            fun accumulate(locations: Locations): Locations =
                Locations(
                    objectIndices = objectIndices.union(locations.objectIndices),
                    objectColorIndices = objectColorIndices.union(locations.objectColorIndices),
                    objectLabelIndices = objectLabelIndices.union(locations.objectLabelIndices),
                    expressionIndices = expressionIndices.union(locations.expressionIndices),
                    regions = regions || locations.regions,
                    backgroundColor = backgroundColor || locations.backgroundColor,
                    chessboardPattern = chessboardPattern || locations.chessboardPattern,
                    chessboardColor = chessboardColor || locations.chessboardColor,
                    phantoms = phantoms || locations.phantoms,
                    selection = selection || locations.selection,
                    center = center || locations.center,
                    regionColor = regionColor || locations.regionColor,
                )

            companion object {
                val EMPTY = Locations(objectIndices = emptySet())
            }
        }

        /** '+=' update-style incremental change */
        sealed interface Update : Change
        /** ':=' reassignment-style change */
        sealed interface Replacement : Change
        @Serializable
        @SerialName("Objects")
        data class Objects(val objects: Map<Ix, GCircle?>) : Update
        @Serializable
        @SerialName("ObjectColors")
        data class ObjectColors(val colors: Map<Ix, ColorAsCss?>) : Update
        @Serializable
        @SerialName("ObjectLabels")
        data class ObjectLabels(val labels: Map<Ix, String?>) : Update
        @Serializable
        @SerialName("Expressions")
        data class Expressions(val expressions: Map<Ix, Expression?>) : Update
        @Serializable
        @SerialName("Regions")
        data class Regions(val regions: List<LogicalRegion>) : Replacement
        @Serializable
        @SerialName("BackgroundColor")
        data class BackgroundColor(val color: ColorAsCss?) : Replacement
        @Serializable
        @SerialName("ChessboardPattern")
        data class ChessboardPattern(val pattern: domain.settings.ChessboardPattern) : Replacement
        @Serializable
        @SerialName("ChessboardColor")
        data class ChessboardColor(val color: ColorAsCss?) : Replacement
        @Serializable
        @SerialName("Phantoms")
        data class Phantoms(val phantoms: Set<Ix>) : Replacement
        @Serializable
        @SerialName("Selection")
        data class Selection(val selection: List<Ix>) : Replacement
        @Serializable
        @SerialName("Center")
        data class Center(val center: SerializableOffset) : Replacement
        @Serializable
        @SerialName("RegionColor")
        data class RegionColor(val color: ColorAsCss?) : Replacement
    }

    @Serializable
    data class Changes(
        val objects: Change.Objects? = null,
        val objectColors: Change.ObjectColors? = null,
        val objectLabels: Change.ObjectLabels? = null,
        val expressions: Change.Expressions? = null,
        val regions: Change.Regions? = null,
        val backgroundColor: Change.BackgroundColor? = null,
        val chessboardPattern: Change.ChessboardPattern? = null,
        val chessboardColor: Change.ChessboardColor? = null,
        val phantoms: Change.Phantoms? = null,
        val selection: Change.Selection? = null,
        val center: Change.Center? = null,
        val regionColor: Change.RegionColor? = null,
    ) {
        val changes: List<Change> = listOfNotNull(
            objects, objectColors, objectLabels, expressions, regions, backgroundColor, chessboardPattern, chessboardColor, phantoms, selection, center, regionColor,
        )

        fun fuseLater(changes: Changes): Changes =
            Changes(
                objects = combineNullables(objects, changes.objects) { a, b ->
                    Change.Objects(a.objects + b.objects)
                },
                objectColors = combineNullables(objectColors, changes.objectColors) { a, b ->
                    Change.ObjectColors(a.colors + b.colors)
                },
                objectLabels = combineNullables(objectLabels, changes.objectLabels) { a, b ->
                    Change.ObjectLabels(a.labels + b.labels)
                },
                expressions = combineNullables(expressions, changes.expressions) { a, b ->
                    Change.Expressions(a.expressions + b.expressions)
                },
                regions = changes.regions ?: regions,
                backgroundColor = changes.backgroundColor ?: backgroundColor,
                chessboardPattern = changes.chessboardPattern ?: chessboardPattern,
                chessboardColor = changes.chessboardColor ?: chessboardColor,
                phantoms = changes.phantoms ?: phantoms,
                selection = changes.selection ?: selection,
                center = changes.center ?: center,
                regionColor = changes.regionColor ?: regionColor,
            )

        companion object {
            val EMPTY = Changes()
        }
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

    fun revert(locations: Change.Locations): Changes =
        Changes(
            objects = locations.objectsLocation?.let { changeLocation ->
                Change.Objects(changeLocation.indices.associateWith { ix -> objects.getOrNull(ix) })
            },
            objectColors = locations.objectColorsLocation?.let { changeLocation ->
                Change.ObjectColors(changeLocation.indices.associateWith { ix -> objectColors[ix] })
            },
            objectLabels = locations.objectLabelsLocation?.let { changeLocation ->
                Change.ObjectLabels(changeLocation.indices.associateWith { ix -> objectLabels[ix] })
            },
            expressions = locations.expressionsLocation?.let { changeLocation ->
                Change.Expressions(changeLocation.indices.associateWith { ix -> expressions[ix] })
            },
            regions =
                if (locations.regions)
                    Change.Regions(regions)
                else null
            ,
            backgroundColor =
                if (locations.backgroundColor)
                    Change.BackgroundColor(backgroundColor)
                else null
            ,
            chessboardPattern =
                if (locations.chessboardPattern)
                    Change.ChessboardPattern(chessboardPattern)
                else null
            ,
            chessboardColor =
                if (locations.chessboardColor)
                    Change.ChessboardColor(chessboardColor)
                else null
            ,
            phantoms =
                if (locations.phantoms)
                    Change.Phantoms(phantoms)
                else null
            ,
            selection =
                if (locations.selection)
                    Change.Selection(selection)
                else null
            ,
            center =
                if (locations.center)
                    Change.Center(center)
                else null
            ,
            regionColor =
                if (locations.regionColor)
                    Change.RegionColor(regionColor)
                else null
            ,
        )

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

    /**
     * @param[changes] are applied in order
     */
    fun applyChanges(changes: List<Change>): SaveState {
        val objects: MutableList<GCircle?> = this.objects.toMutableList()
        val objectColors: MutableMap<Ix, ColorAsCss> = this.objectColors.toMutableMap()
        val objectLabels: MutableMap<Ix, String> = this.objectLabels.toMutableMap()
        val expressions: MutableMap<Ix, Expression?> = this.expressions.toMutableMap()
        var regions: List<LogicalRegion> = this.regions
        var backgroundColor: Color? = this.backgroundColor
        var chessboardPattern: ChessboardPattern = this.chessboardPattern
        var chessboardColor: Color? = this.chessboardColor
        var phantoms: Set<Ix> = this.phantoms
        var selection: List<Ix> = this.selection
        var center: Offset = this.center
        var regionColor: Color? = this.regionColor
        for (change in changes) {
            when (change) {
                is Change.Objects -> {
                    for ((ix, obj) in change.objects) {
                        val size = objects.size
                        if (ix >= size) {
                            repeat(size - ix) {
                                objects.add(null)
                            }
                            objects.add(obj)
                        }
                        objects[ix] = obj
                    }
                }
                is Change.ObjectColors -> {
                    for ((ix, color) in change.colors) {
                        if (color == null) {
                            objectColors.remove(ix)
                        } else {
                            objectColors.put(ix, color)
                        }
                    }
                }
                is Change.ObjectLabels -> {
                    for ((ix, label) in change.labels) {
                        if (label == null) {
                            objectLabels.remove(ix)
                        } else {
                            objectLabels.put(ix, label)
                        }
                    }
                }
                is Change.Expressions -> {
                    expressions.putAll(change.expressions)
                }
                is Change.Regions -> regions = change.regions
                is Change.BackgroundColor -> backgroundColor = change.color
                is Change.ChessboardPattern -> chessboardPattern = change.pattern
                is Change.ChessboardColor -> chessboardColor = change.color
                is Change.Phantoms -> phantoms = change.phantoms
                is Change.Selection -> selection = change.selection
                is Change.Center -> center = change.center
                is Change.RegionColor -> regionColor = change.color
            }
        }
        return SaveState(
            objects = objects,
            objectColors = objectColors,
            objectLabels = objectLabels,
            expressions = expressions,
            regions = regions,
            backgroundColor = backgroundColor,
            chessboardPattern = chessboardPattern,
            chessboardColor = chessboardColor,
            phantoms = phantoms,
            selection = selection,
            center = center,
            regionColor = regionColor
        )
    }
}

// not liftA2 actually
/** [combinator]`(a, b)` or [a] or [b] */
private inline fun <reified T: Any> combineNullables(
    a: T?, b: T?,
    crossinline combinator: (T, T) -> T,
): T? =
    if (a == null)
        b
    else if (b == null)
        a
    else
        combinator(a, b)
