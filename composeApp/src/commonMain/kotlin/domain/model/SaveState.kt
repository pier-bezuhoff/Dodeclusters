package domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import core.geometry.Circle
import core.geometry.GCircleOrConcreteAcPath
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
 * [objects].indices must span all of [borderColors].keys and [labels].keys.
 * And [objects].indices == [expressions].keys */
@Immutable
@Serializable
@SerialName("SaveState")
data class SaveState(
    val objects: List<GCircleOrConcreteAcPath?>,
    val expressions: Map<Ix, ConformalExprOutput?>,
    val borderColors: Map<Ix, ColorAsCss> = emptyMap(),
    val fillColors: Map<Ix, ColorAsCss> = emptyMap(),
    val labels: Map<Ix, String> = emptyMap(),
    val regions: List<LogicalRegion> = emptyList(),
    val backgroundColor: ColorAsCss?,
    val chessboardPattern: ChessboardPattern,
    val chessboardColor: ColorAsCss?,
    val phantoms: Set<Ix> = emptySet(),
    @SerialName("selections") // TMP: for backwards compat, diff name cuz the type changed
    val selection: Selection = Selection(),
    val center: SerializableOffset,
    val regionColor: ColorAsCss? = null,
) {
    // since we generate save-state every undo-able action, i think it's prudent to
    // omit any validation

    @Immutable
    @Serializable
    sealed interface Change {
        @Immutable
        @Serializable
        sealed interface Location {
            @Serializable
            data class Objects(val indices: Set<Ix>) : Location
            @Serializable
            data class Expressions(val indices: Set<Ix>) : Location
            @Serializable
            data class BorderColors(val indices: Set<Ix>) : Location
            @Serializable
            data class FillColors(val indices: Set<Ix>) : Location
            @Serializable
            data class Labels(val indices: Set<Ix>) : Location
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

        @Immutable
        @Serializable
        data class Locations(
            val objectIndices: Set<Ix> = emptySet(),
            val expressionIndices: Set<Ix> = emptySet(),
            val borderColorIndices: Set<Ix> = emptySet(),
            val fillColorIndices: Set<Ix> = emptySet(),
            val labelIndices: Set<Ix> = emptySet(),
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
            inline val expressionsLocation: Location.Expressions? get() =
                if (expressionIndices.isEmpty()) null else Location.Expressions(expressionIndices)
            inline val borderColorsLocation: Location.BorderColors? get() =
                if (borderColorIndices.isEmpty()) null else Location.BorderColors(borderColorIndices)
            inline val fillColorsLocation: Location.FillColors? get() =
                if (fillColorIndices.isEmpty()) null else Location.FillColors(fillColorIndices)
            inline val labelsLocation: Location.Labels? get() =
                if (labelIndices.isEmpty()) null else Location.Labels(labelIndices)
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
                    expressionIndices = expressionIndices.union(locations.expressionIndices),
                    borderColorIndices = borderColorIndices.union(locations.borderColorIndices),
                    fillColorIndices = fillColorIndices.union(locations.fillColorIndices),
                    labelIndices = labelIndices.union(locations.labelIndices),
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
        data class Objects(val objects: Map<Ix, GCircleOrConcreteAcPath?>) : Update
        @Serializable
        @SerialName("Expressions")
        data class Expressions(val expressions: Map<Ix, ConformalExprOutput?>) : Update
        @Serializable
        @SerialName("BorderColors")
        data class BorderColors(val colors: Map<Ix, ColorAsCss?>) : Update
        @Serializable
        @SerialName("FillColors")
        data class FillColors(val colors: Map<Ix, ColorAsCss?>) : Update
        @Serializable
        @SerialName("Labels")
        data class Labels(val labels: Map<Ix, String?>) : Update
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

    @Immutable
    @Serializable
    data class Changes(
        val objects: Change.Objects? = null,
        val expressions: Change.Expressions? = null,
        val borderColors: Change.BorderColors? = null,
        val fillColors: Change.FillColors? = null,
        val labels: Change.Labels? = null,
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
            objects, expressions, borderColors, fillColors, labels, regions, backgroundColor, chessboardPattern, chessboardColor, phantoms, selection, center, regionColor,
        )
        val locations: Change.Locations get() = Change.Locations(
            objectIndices = objects?.objects?.keys ?: emptySet(),
            expressionIndices = expressions?.expressions?.keys ?: emptySet(),
            borderColorIndices = borderColors?.colors?.keys ?: emptySet(),
            fillColorIndices = fillColors?.colors?.keys ?: emptySet(),
            labelIndices = labels?.labels?.keys ?: emptySet(),
            regions = regions != null,
            backgroundColor = backgroundColor != null,
            chessboardPattern = chessboardPattern != null,
            chessboardColor = chessboardColor != null,
            phantoms = phantoms != null,
            selection = selection != null,
            center = center != null,
            regionColor = regionColor != null,
        )

        /** fuse `this` earlier changes, with later [changes] */
        fun fuseLater(changes: Changes): Changes =
            Changes(
                objects = combineNullables(objects, changes.objects) { a, b ->
                    Change.Objects(a.objects + b.objects)
                },
                expressions = combineNullables(expressions, changes.expressions) { a, b ->
                    Change.Expressions(a.expressions + b.expressions)
                },
                borderColors = combineNullables(borderColors, changes.borderColors) { a, b ->
                    Change.BorderColors(a.colors + b.colors)
                },
                fillColors = combineNullables(fillColors, changes.fillColors) { a, b ->
                    Change.FillColors(a.colors + b.colors)
                },
                labels = combineNullables(labels, changes.labels) { a, b ->
                    Change.Labels(a.labels + b.labels)
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
            expressions = locations.expressionsLocation?.let { changeLocation ->
                Change.Expressions(changeLocation.indices.associateWith { ix -> expressions[ix] })
            },
            borderColors = locations.borderColorsLocation?.let { changeLocation ->
                Change.BorderColors(changeLocation.indices.associateWith { ix -> this@SaveState.borderColors[ix] })
            },
            fillColors = locations.fillColorsLocation?.let { changeLocation ->
                Change.FillColors(changeLocation.indices.associateWith { ix -> this@SaveState.fillColors[ix] })
            },
            labels = locations.labelsLocation?.let { changeLocation ->
                Change.Labels(changeLocation.indices.associateWith { ix -> this@SaveState.labels[ix] })
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
            is Change.Expressions ->
                Change.Expressions(change.expressions.mapValues { (ix, _) -> expressions[ix] })
            is Change.BorderColors ->
                Change.BorderColors(change.colors.mapValues { (ix, _) -> this@SaveState.borderColors[ix] })
            is Change.FillColors ->
                Change.FillColors(change.colors.mapValues { (ix, _) -> this@SaveState.fillColors[ix] })
            is Change.Labels ->
                Change.Labels(change.labels.mapValues { (ix, _) -> this@SaveState.labels[ix] })
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
        val objects: MutableList<GCircleOrConcreteAcPath?> = this.objects.toMutableList()
        val expressions: MutableMap<Ix, ConformalExprOutput?> = this.expressions.toMutableMap()
        val borderColors: MutableMap<Ix, ColorAsCss> = this.borderColors.toMutableMap()
        val fillColors: MutableMap<Ix, ColorAsCss> = this.fillColors.toMutableMap()
        val labels: MutableMap<Ix, String> = this.labels.toMutableMap()
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
                is Change.Expressions -> {
                    expressions.putAll(change.expressions)
                }
                is Change.BorderColors -> {
                    for ((ix, color) in change.colors) {
                        if (color == null) {
                            borderColors.remove(ix)
                        } else {
                            borderColors[ix] = color
                        }
                    }
                }
                is Change.FillColors -> {
                    for ((ix, color) in change.colors) {
                        if (color == null) {
                            fillColors.remove(ix)
                        } else {
                            fillColors[ix] = color
                        }
                    }
                }
                is Change.Labels -> {
                    for ((ix, label) in change.labels) {
                        if (label == null) {
                            labels.remove(ix)
                        } else {
                            labels[ix] = label
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
            expressions = expressions,
            borderColors = borderColors,
            fillColors = fillColors,
            labels = labels,
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
            borderColors =
                if (this@SaveState.borderColors == earlierState.borderColors) null
                else {
                    val indices: Set<Ix> = this@SaveState.borderColors.keys + earlierState.borderColors.keys
                    val changedIndices = indices.filter { this@SaveState.borderColors[it] != earlierState.borderColors[it] }
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.BorderColors(changedIndices.associateWith { this@SaveState.borderColors[it] })
                },
            fillColors =
                if (this@SaveState.fillColors == earlierState.fillColors) null
                else {
                    val indices: Set<Ix> = this@SaveState.fillColors.keys + earlierState.fillColors.keys
                    val changedIndices = indices.filter { this@SaveState.fillColors[it] != earlierState.fillColors[it] }
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.FillColors(changedIndices.associateWith { this@SaveState.fillColors[it] })
                },
            labels =
                if (this@SaveState.labels == earlierState.labels) null
                else {
                    val indices: Set<Ix> = this@SaveState.labels.keys + earlierState.labels.keys
                    val changedIndices = indices.filter { this@SaveState.labels[it] != earlierState.labels[it] }
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.Labels(changedIndices.associateWith { this@SaveState.labels[it] })
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
            expressions = expressions
                .mapNotNull { (ix, expression) ->
                    reindexing[ix]?.let { ix to expression }
                }.toMap(),
            borderColors = this@SaveState.borderColors
                .mapNotNull { (ix, color) ->
                    reindexing[ix]?.let { ix to color }
                }.toMap(),
            fillColors = this@SaveState.fillColors
                .mapNotNull { (ix, color) ->
                    reindexing[ix]?.let { ix to color }
                }.toMap(),
            labels = this@SaveState.labels
                .mapNotNull { (ix, label) ->
                    reindexing[ix]?.let { ix to label }
                }.toMap(),
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
                gCircles = selection.gCircles.mapNotNull { reindexing[it] },
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
                expressions = expressions,
                borderColors = emptyMap(),
                fillColors = emptyMap(),
                labels = emptyMap(),
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
