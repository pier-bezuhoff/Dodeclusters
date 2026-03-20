package domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import core.geometry.Circle
import core.geometry.GCircle
import core.geometry.Point
import domain.ColorAsCss
import domain.Ix
import domain.SerializableOffset
import domain.expressions.ConformalExprOutput
import domain.expressions.Expr
import domain.expressions.ExprOutput
import domain.expressions.LoxodromicMotionParameters
import domain.reindexingMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** EditorViewModel's save-state for history.
 * [objects].indices must span all of [objectColors].keys and [objectLabels].keys.
 * And [objects].indices == [expressions].keys */
@Serializable
@SerialName("SaveState")
data class SaveState(
    val objects: List<GCircle?>,
    val objectColors: Map<Ix, ColorAsCss> = emptyMap(),
    val objectLabels: Map<Ix, String> = emptyMap(),
    val expressions: Map<Ix, ConformalExprOutput?>,
    val arcPaths: List<ArcPath?> = emptyList(),
    val regions: List<LogicalRegion> = emptyList(),
    val backgroundColor: ColorAsCss?,
    val chessboardPattern: ChessboardPattern,
    val chessboardColor: ColorAsCss?,
    val phantoms: Set<Ix> = emptySet(),
    @SerialName("selections") // TMP: for backwards compat
    val selection: Selection = Selection(),
    val center: SerializableOffset,
    val regionColor: ColorAsCss? = null,
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
            data class ArcPaths(val indices: Set<Int>) : Location
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
            val arcPaths: Set<Int> = emptySet(),
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
            inline val arcPathsLocation: Location.ArcPaths? get() =
                if (arcPaths.isEmpty()) null else Location.ArcPaths(arcPaths)
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

            fun accumulate(locations: Locations): Locations =
                Locations(
                    objectIndices = objectIndices.union(locations.objectIndices),
                    objectColorIndices = objectColorIndices.union(locations.objectColorIndices),
                    objectLabelIndices = objectLabelIndices.union(locations.objectLabelIndices),
                    expressionIndices = expressionIndices.union(locations.expressionIndices),
                    arcPaths = arcPaths.union(locations.arcPaths),
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
        data class Expressions(val expressions: Map<Ix, ConformalExprOutput?>) : Update
        // arcPaths cannot be a Map<Int, _> since same-index changes are possible
        /** @property[arcPaths] should be in the execution/increasing index order */
        @Serializable
        @SerialName("ArcPaths")
        data class ArcPaths(val arcPaths: Map<Int, ArcPath?>) : Update
        @Serializable
        @SerialName("Regions")
        data class Regions(val regions: List<LogicalRegion>) : Replacement
        @Serializable
        @SerialName("BackgroundColor")
        data class BackgroundColor(val color: ColorAsCss?) : Replacement
        @Serializable
        @SerialName("ChessboardPattern")
        data class ChessboardPattern(val pattern: domain.model.ChessboardPattern) : Replacement
        @Serializable
        @SerialName("ChessboardColor")
        data class ChessboardColor(val color: ColorAsCss?) : Replacement
        @Serializable
        @SerialName("Phantoms")
        data class Phantoms(val phantoms: Set<Ix>) : Replacement
        @Serializable
        @SerialName("Selection")
        data class Selection(val selection: domain.model.Selection) : Replacement
        // MAYBE: global zoom
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
        val arcPaths: Change.ArcPaths? = null,
        val regions: Change.Regions? = null,
        val backgroundColor: Change.BackgroundColor? = null,
        val chessboardPattern: Change.ChessboardPattern? = null,
        val chessboardColor: Change.ChessboardColor? = null,
        val phantoms: Change.Phantoms? = null,
        val selection: Change.Selection? = null,
        val center: Change.Center? = null,
        val regionColor: Change.RegionColor? = null,
    ) {
        val changes: List<Change> get() = listOfNotNull(
            objects, objectColors, objectLabels, expressions, arcPaths, regions, backgroundColor, chessboardPattern, chessboardColor, phantoms, selection, center, regionColor,
        )
        val locations: Change.Locations get() = Change.Locations(
            objectIndices = objects?.objects?.keys ?: emptySet(),
            objectColorIndices = objectColors?.colors?.keys ?: emptySet(),
            objectLabelIndices = objectLabels?.labels?.keys ?: emptySet(),
            expressionIndices = expressions?.expressions?.keys ?: emptySet(),
            arcPaths = arcPaths?.arcPaths?.keys ?: emptySet(),
            regions = regions != null,
            backgroundColor = backgroundColor != null,
            chessboardPattern = chessboardPattern != null,
            chessboardColor = chessboardColor != null,
            phantoms = phantoms != null,
            selection = selection != null,
            center = center != null,
            regionColor = regionColor != null,
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
                arcPaths = combineNullables(arcPaths, changes.arcPaths) { a, b ->
                    Change.ArcPaths(a.arcPaths + b.arcPaths)
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
            arcPaths = locations.arcPathsLocation?.let { changeLocation ->
                Change.ArcPaths(changeLocation.indices.associateWith { i -> arcPaths.getOrNull(i) })
            },
            regions =
                if (locations.regions) Change.Regions(regions) else null,
            backgroundColor =
                if (locations.backgroundColor) Change.BackgroundColor(backgroundColor) else null,
            chessboardPattern =
                if (locations.chessboardPattern) Change.ChessboardPattern(chessboardPattern) else null,
            chessboardColor =
                if (locations.chessboardColor) Change.ChessboardColor(chessboardColor) else null,
            phantoms =
                if (locations.phantoms) Change.Phantoms(phantoms) else null,
            selection =
                if (locations.selection) Change.Selection(selection) else null,
            center =
                if (locations.center) Change.Center(center) else null,
            regionColor =
                if (locations.regionColor) Change.RegionColor(regionColor) else null,
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
            is Change.ArcPaths ->
                Change.ArcPaths(change.arcPaths.mapValues { (i, _) -> arcPaths.getOrNull(i) })
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
        val expressions: MutableMap<Ix, ConformalExprOutput?> = this.expressions.toMutableMap()
        val arcPaths: MutableList<ArcPath?> = this.arcPaths.toMutableList()
        var regions: List<LogicalRegion> = this.regions
        var backgroundColor: Color? = this.backgroundColor
        var chessboardPattern: ChessboardPattern = this.chessboardPattern
        var chessboardColor: Color? = this.chessboardColor
        var phantoms: Set<Ix> = this.phantoms
        var selection: Selection = this.selection
        var center: Offset = this.center
        var regionColor: Color? = this.regionColor
        for (change in changes) {
            when (change) {
                is Change.Objects -> {
                    for (ix in change.objects.keys.sorted()) {
                        val obj = change.objects[ix]
                        val overshoot = ix - objects.size
                        if (overshoot >= 0) {
                            repeat(overshoot) {
                                objects.add(null)
                            }
                            objects.add(obj)
                        } else {
                            objects[ix] = obj
                        }
                    }
                }
                is Change.ObjectColors -> {
                    for ((ix, color) in change.colors) {
                        if (color == null) {
                            objectColors.remove(ix)
                        } else {
                            objectColors[ix] = color
                        }
                    }
                }
                is Change.ObjectLabels -> {
                    for ((ix, label) in change.labels) {
                        if (label == null) {
                            objectLabels.remove(ix)
                        } else {
                            objectLabels[ix] = label
                        }
                    }
                }
                is Change.Expressions -> {
                    expressions.putAll(change.expressions)
                }
                is Change.ArcPaths -> {
                    for (i in change.arcPaths.keys.sorted()) {
                        val arcPath = change.arcPaths[i]
                        val overshoot = i - arcPaths.size
                        if (overshoot >= 0) {
                            repeat(overshoot) {
                                arcPaths.add(null)
                            }
                            arcPaths.add(arcPath)
                        } else {
                            arcPaths[i] = arcPath
                        }
                    }
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
            arcPaths = arcPaths,
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

    fun diff(earlierState: SaveState): Changes =
        Changes(
            objects =
                if (objects == earlierState.objects) null
                else {
                    // realistically the earlier state must have smaller or equal size
                    val size0 = earlierState.objects.size
                    val size = objects.size
                    require(size0 <= size) { "objects.size of an earlier state must not be greater, $earlierState vs $this" }
                    val changedIndices = (0 until size0)
                        .filter { objects[it] != earlierState.objects[it] }
                        .plus(size0 until size)
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.Objects(changedIndices.associateWith { objects[it] })
                }
            ,
            objectColors =
                if (objectColors == earlierState.objectColors) null
                else {
                    val indices: Set<Ix> = objectColors.keys + earlierState.objectColors.keys
                    val changedIndices = indices.filter { objectColors[it] != earlierState.objectColors[it] }
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.ObjectColors(changedIndices.associateWith { objectColors[it] })
                },
            objectLabels =
                if (objectLabels == earlierState.objectLabels) null
                else {
                    val indices: Set<Ix> = objectLabels.keys + earlierState.objectLabels.keys
                    val changedIndices = indices.filter { objectLabels[it] != earlierState.objectLabels[it] }
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.ObjectLabels(changedIndices.associateWith { objectLabels[it] })
                },
            expressions =
                if (expressions == earlierState.expressions) null
                else {
                    val size0 = earlierState.expressions.size
                    val size = expressions.size
                    require(size0 <= size) { "expressions.size of an earlier state must NOT be greater, $earlierState vs $this" }
                    val changedIndices = (0 until size0)
                        .filter { expressions[it] != earlierState.expressions[it] }
                        .plus(size0 until size)
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.Expressions(changedIndices.associateWith { expressions[it] })
                },
            arcPaths =
                if (arcPaths == earlierState.arcPaths) null
                else {
                    val size0 = earlierState.arcPaths.size
                    val size = arcPaths.size
                    require(size0 <= size) { "arcPaths.size of an earlier state must not be greater, $earlierState vs $this" }
                    val changedIndices = (0 until size0)
                        .filter { arcPaths[it] != earlierState.arcPaths[it] }
                        .plus(size0 until size)
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.ArcPaths(changedIndices.associateWith { arcPaths[it] })
                },
            regions =
                if (regions == earlierState.regions) null
                else Change.Regions(regions),
            backgroundColor =
                if (backgroundColor == earlierState.backgroundColor) null
                else Change.BackgroundColor(backgroundColor),
            chessboardPattern =
                if (chessboardPattern == earlierState.chessboardPattern) null
                else Change.ChessboardPattern(chessboardPattern),
            chessboardColor =
                if (chessboardColor == earlierState.chessboardColor) null
                else Change.ChessboardColor(chessboardColor),
            phantoms =
                if (phantoms == earlierState.phantoms) null
                else Change.Phantoms(phantoms),
            selection =
                if (selection == earlierState.selection) null
                else Change.Selection(selection),
            center =
                if (center == earlierState.center) null
                else Change.Center(center),
            regionColor =
                if (regionColor == earlierState.regionColor) null
                else Change.RegionColor(regionColor),
        )

    /**
     * Shift indices to fill-in empty positions of previously deleted objects.
     * Note that freeing indices would break compatibility with [ChangeHistory]
     */
    fun compressFreeIndices(): SaveState {
        val indices = objects.indices
        val deleted = indices.filter { ix ->
            objects[ix] == null && expressions[ix] == null
        }.toSet()
        val reindexing = reindexingMap(
            originalIndices = indices,
            deletedIndices = deleted,
        )
        return copy(
            objects = objects.filterIndexed { ix, _ -> ix !in deleted },
            objectColors = objectColors
                .mapNotNull { (ix, color) ->
                    reindexing[ix]?.let { ix to color }
                }.toMap(),
            objectLabels = objectLabels
                .mapNotNull { (ix, label) ->
                    reindexing[ix]?.let { ix to label }
                }.toMap(),
            expressions = expressions
                .mapNotNull { (ix, expression) ->
                    reindexing[ix]?.let { ix to expression }
                }.toMap(),
            arcPaths = arcPaths.mapNotNull { arcPath ->
                when (arcPath) {
                    // Q: what if a vertex/arc-middle is cleared?
                    is ArcPath.Closed -> arcPath.copy(
                        vertices = arcPath.vertices.map {
                            reindexing[it] ?: return@mapNotNull null
                        },
                        arcs = arcPath.arcs.map { arc ->
                            when (arc) {
                                is Arc.By2Points -> arc
                                is Arc.By3Points -> arc.copy(middlePointIndex =
                                    reindexing[arc.middlePointIndex] ?: return@mapNotNull null
                                )
                            }
                        }
                    )
                    is ArcPath.Open -> arcPath.copy(
                        vertices = arcPath.vertices.map {
                            reindexing[it] ?: return@mapNotNull null
                        },
                        arcs = arcPath.arcs.map { arc ->
                            when (arc) {
                                is Arc.By2Points -> arc
                                is Arc.By3Points -> arc.copy(middlePointIndex =
                                    reindexing[arc.middlePointIndex] ?: return@mapNotNull null
                                )
                            }
                        }
                    )
                    null -> null
                }
            },
            regions = regions
                .mapNotNull { region ->
                    val insides = region.insides.mapNotNull { reindexing[it] }.toSet()
                    val outsides = region.outsides.mapNotNull { reindexing[it] }.toSet()
                    if (insides.isEmpty() && outsides.isEmpty())
                        null
                    else
                        region.copy(insides = insides, outsides = outsides)
                }
            ,
            phantoms = phantoms.mapNotNull { reindexing[it] }.toSet(),
            selection = selection.copy(
                objects = selection.objects.mapNotNull { reindexing[it] },
                // we assume null-vertices arc-paths are deleted upon deleting the last vertex
                arcPaths = selection.arcPaths,
            ),
        )
    }

    companion object {
        val JSON_FORMAT = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            // support Infinity for Points
            allowSpecialFloatingPointValues = true
        }
        /** nice symmetric spiral */
        val SAMPLE = run {
            val circle = Circle(0.0, 0.0, 100.0)
            val p1 = Point(-282.0, 0.0)
            val p2 = Point(+282.0, 0.0)
            val trajectoryLength = 10
            val expr1 = Expr.LoxodromicMotion(
                parameters = LoxodromicMotionParameters(-200f, 2.0, 9),
                divergencePoint = 1, convergencePoint = 2,
                target = 0,
                otherHalfStart = trajectoryLength + 3
            )
            val expr2 = Expr.LoxodromicMotion(
                parameters = LoxodromicMotionParameters(-200f, 2.0, 9),
                divergencePoint = 2, convergencePoint = 1,
                target = 0,
                otherHalfStart = 3
            )
            val objects = listOf(circle, p1, p2) + (0 until 2*trajectoryLength).map { null }
            // TODO: we need to calc objects here ig
            val expressions = mutableMapOf<Ix, ConformalExprOutput?>(
                0 to null, 1 to null, 2 to null,
            )
            for (i in 0 until trajectoryLength) {
                expressions[3 + i] = ExprOutput.OneOf(expr1, i)
                expressions[3 + trajectoryLength + i] = ExprOutput.OneOf(expr2, i)
            }
            SaveState(
                objects = objects,
                objectColors = emptyMap(),
                objectLabels = emptyMap(),
                expressions = expressions,
                arcPaths = emptyList(),
                regions = emptyList(),
                backgroundColor = null,
                chessboardPattern = ChessboardPattern.STARTS_TRANSPARENT,
                chessboardColor = Color(56, 136, 116),
                phantoms = emptySet(),
                selection = Selection(),
                center = Offset.Zero,
                regionColor = null,
            )
        }
    }
}

// not liftA2 actually
/** [combinator]`(a, b)` otherwise [a] or [b] */
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
