package domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import core.geometry.Circle
import core.geometry.GCircleOrConcreteAcPath
import core.geometry.Point
import domain.ColorAsCss
import domain.ColorCssSerializer
import domain.Ix
import domain.SerializableOffset
import domain.expressions.ArcPath
import domain.expressions.ConformalExprOutput
import domain.expressions.Expr
import domain.expressions.ExprOutput
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.reIndex
import domain.expressions.withoutPointsAt
import domain.reindexingMap
import domain.setOrRemove
import domain.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule

// autosave location on Linux: ~/.local/share/Dodeclusters/autosave.json
/** EditorViewModel's save-state for history.
 * [objects].indices must span all of [styling].keys.
 * And [objects].indices == [expressions].keys */
@OptIn(ExperimentalSerializationApi::class)
@Immutable
@KeepGeneratedSerializer
@Serializable(with = SaveState.CompatSerializer::class)
@SerialName("SaveState")
data class SaveState(
    val objects: List<GCircleOrConcreteAcPath?>,
    val expressions: Map<Ix, ConformalExprOutput?>,
    val styling: Map<Ix, Styling> = emptyMap(),
    val regions: List<LogicalRegion> = emptyList(),
    val backgroundColor: ColorAsCss? = null,
    val chessboardPattern: ChessboardPattern = ChessboardPattern.NONE,
    val chessboardColor: ColorAsCss? = null,
    @SerialName("selections") // TMP: for backwards compat, diff name cuz the type has changed
    val selection: Selection = Selection(),
    val center: SerializableOffset = Offset.Zero,
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
            data class Styling(val indices: Set<Ix>) : Location
            @Serializable
            data object Regions : Location
            @Serializable
            data object BackgroundColor : Location
            @Serializable
            data object ChessboardPattern : Location
            @Serializable
            data object ChessboardColor : Location
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
            val stylingIndices: Set<Ix> = emptySet(),
            val regions: Boolean = false,
            val backgroundColor: Boolean = false,
            val chessboardPattern: Boolean = false,
            val chessboardColor: Boolean = false,
            val selection: Boolean = false,
            val center: Boolean = false,
            val regionColor: Boolean = false,
        ) {
            inline val objectsLocation: Location.Objects? get() =
                if (objectIndices.isEmpty()) null else Location.Objects(objectIndices)
            inline val expressionsLocation: Location.Expressions? get() =
                if (expressionIndices.isEmpty()) null else Location.Expressions(expressionIndices)
            inline val stylingLocation: Location.Styling? get() =
                if (stylingIndices.isEmpty()) null else Location.Styling(stylingIndices)
            inline val regionsLocation: Location.Regions? get() =
                if (regions) Location.Regions else null
            inline val backgroundColorLocation: Location.BackgroundColor? get() =
                if (backgroundColor) Location.BackgroundColor else null
            inline val chessboardPatternLocation: Location.ChessboardPattern? get() =
                if (chessboardPattern) Location.ChessboardPattern else null
            inline val chessboardColorLocation: Location.ChessboardColor? get() =
                if (chessboardColor) Location.ChessboardColor else null
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
                    stylingIndices = stylingIndices.union(locations.stylingIndices),
                    regions = regions || locations.regions,
                    backgroundColor = backgroundColor || locations.backgroundColor,
                    chessboardPattern = chessboardPattern || locations.chessboardPattern,
                    chessboardColor = chessboardColor || locations.chessboardColor,
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
        @SerialName("Styling")
        data class Styling(val styling: Map<Ix, domain.model.Styling?>) : Update
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
        @SerialName("Selection")
        data class Selection(val selection: domain.model.Selection) : Replacement
        // MAYBE: global zoom
        @Serializable
        @SerialName("Center")
        data class Center(val center: SerializableOffset) : Replacement
        @Serializable
        @SerialName("RegionColor")
        data class RegionColor(val color: ColorAsCss?) : Replacement

        object CompatSerializer : JsonTransformingSerializer<Change>(
            serializer() // generatedSerializer()
        ) {
            override fun transformDeserialize(element: JsonElement): JsonElement =
                when (element) {
                    is JsonObject -> when (element["type"]) {
                        JsonPrimitive("BorderColor") if (element["colors"] is JsonObject) -> {
                            val colors = element["colors"] as JsonObject
                            buildJsonObject {
                                put("type", "Styling")
                                put("styling", JsonObject(
                                    colors.mapValues { (_, color) ->
                                        JsonObject(mapOf("borderColor" to color))
                                    }
                                ))
                            }
                        }
                        else -> element
                    }
                    else -> element
                }
        }
    }

    @Immutable
    @Serializable
    data class Changes(
        val objects: Change.Objects? = null,
        val expressions: Change.Expressions? = null,
        val styling: Change.Styling? = null,
        val regions: Change.Regions? = null,
        val backgroundColor: Change.BackgroundColor? = null,
        val chessboardPattern: Change.ChessboardPattern? = null,
        val chessboardColor: Change.ChessboardColor? = null,
        val selection: Change.Selection? = null,
        val center: Change.Center? = null,
        val regionColor: Change.RegionColor? = null,
    ) {
        val changes: List<Change> get() = listOfNotNull(
            objects, expressions, styling, regions, backgroundColor, chessboardPattern, chessboardColor, selection, center, regionColor,
        )
        val locations: Change.Locations get() = Change.Locations(
            objectIndices = objects?.objects?.keys ?: emptySet(),
            expressionIndices = expressions?.expressions?.keys ?: emptySet(),
            stylingIndices = styling?.styling?.keys ?: emptySet(),
            regions = regions != null,
            backgroundColor = backgroundColor != null,
            chessboardPattern = chessboardPattern != null,
            chessboardColor = chessboardColor != null,
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
                styling = combineNullables(styling, changes.styling) { a, b ->
                    Change.Styling(a.styling + b.styling)
                },
                regions = changes.regions ?: regions,
                backgroundColor = changes.backgroundColor ?: backgroundColor,
                chessboardPattern = changes.chessboardPattern ?: chessboardPattern,
                chessboardColor = changes.chessboardColor ?: chessboardColor,
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
            styling = locations.stylingLocation?.let { changeLocation ->
                Change.Styling(changeLocation.indices.associateWith { ix -> styling[ix] })
            },
            regions =
                if (locations.regions) Change.Regions(regions) else null,
            backgroundColor =
                if (locations.backgroundColor) Change.BackgroundColor(backgroundColor) else null,
            chessboardPattern =
                if (locations.chessboardPattern) Change.ChessboardPattern(chessboardPattern) else null,
            chessboardColor =
                if (locations.chessboardColor) Change.ChessboardColor(chessboardColor) else null,
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
            is Change.Styling ->
                Change.Styling(change.styling.mapValues { (ix, _) -> this@SaveState.styling[ix] })
            is Change.Regions ->
                Change.Regions(regions)
            is Change.BackgroundColor ->
                Change.BackgroundColor(backgroundColor)
            is Change.ChessboardPattern ->
                Change.ChessboardPattern(chessboardPattern)
            is Change.ChessboardColor ->
                Change.ChessboardColor(chessboardColor)
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
        val styling: MutableMap<Ix, Styling> = this.styling.toMutableMap()
        var regions: List<LogicalRegion> = this.regions
        var backgroundColor: Color? = this.backgroundColor
        var chessboardPattern: ChessboardPattern = this.chessboardPattern
        var chessboardColor: Color? = this.chessboardColor
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
                is Change.Styling -> {
                    for ((ix, style) in change.styling) {
                        styling.setOrRemove(ix, style)
                    }
                }
                is Change.Regions -> regions = change.regions
                is Change.BackgroundColor -> backgroundColor = change.color
                is Change.ChessboardPattern -> chessboardPattern = change.pattern
                is Change.ChessboardColor -> chessboardColor = change.color
                is Change.Selection -> selection = change.selection
                is Change.Center -> center = change.center
                is Change.RegionColor -> regionColor = change.color
            }
        }
        return SaveState(
            objects = objects,
            expressions = expressions,
            styling = styling,
            regions = regions,
            backgroundColor = backgroundColor,
            chessboardPattern = chessboardPattern,
            chessboardColor = chessboardColor,
            selection = selection,
            center = center,
            regionColor = regionColor
        )
    }

    /** [earlierState] -> `this` state changes */
    fun diff(earlierState: SaveState): Changes =
        Changes(
            objects =
                if (objects == earlierState.objects) null
                else {
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
            styling =
                if (this@SaveState.styling == earlierState.styling) null
                else {
                    val indices: Set<Ix> = this@SaveState.styling.keys + earlierState.styling.keys
                    val changedIndices = indices.filter {
                        this@SaveState.styling[it] != earlierState.styling[it]
                    }
                    if (changedIndices.isEmpty())
                        null
                    else
                        Change.Styling(changedIndices.associateWith { this@SaveState.styling[it] })
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
            objects = objects.filterIndexed { oldIndex, _ -> oldIndex !in deleted },
            expressions = expressions
                .mapNotNull { (oldIndex, expression) ->
                    reindexing[oldIndex]?.let { newIndex ->
                        if (expression?.expr?.args?.any { it in deleted } == true) {
                            when (val arcPath = expression.expr) {
                                is ArcPath -> {
                                    newIndex to arcPath.withoutPointsAt(deleted)
                                        ?.reIndex { reindexing[it]!! }
                                }
                                else -> {
                                    println("W: SaveState.compressFreeIndices deleted some $expression arguments")
                                    newIndex to null
                                }
                            }
                            println("W: SaveState.compressFreeIndices deleted some $expression arguments")
                            newIndex to null
                        } else
                            newIndex to expression?.reIndex { reindexing[it]!! }
                    }
                }.toMap(),
            styling = this@SaveState.styling
                .mapNotNull { (oldIndex, style) ->
                    reindexing[oldIndex]?.let { it to style }
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
            selection = selection.copy(
                gCircles = selection.gCircles.mapNotNull { reindexing[it] },
                arcPaths = selection.arcPaths.mapNotNull { reindexing[it] },
            ),
        )
    }

    object CompatSerializer : JsonTransformingSerializer<SaveState>(
        generatedSerializer()
    ) {
        private val json = Json {
            encodeDefaults = false
        }
        override fun transformDeserialize(element: JsonElement): JsonElement =
            when (element) {
                is JsonObject -> when {
                    "styling" !in element -> {
                        val state = element.toMutableMap()
                        // indices are strings
                        val styling = mutableMapOf<String, Styling>()
                        when (val borderColors = element["borderColors"]) {
                            is JsonObject if (borderColors.all { (_, color) -> color is JsonPrimitive }) -> {
                                borderColors.forEach { (ix, color) ->
                                    styling.update(ix, Styling()) {
                                        it.copy(borderColor = json.decodeFromJsonElement(ColorCssSerializer, color))
                                    }
                                }
                                state.remove("borderColors")
                            }
                            else -> {}
                        }
                        when (val fillColors = element["fillColors"]) {
                            is JsonObject if (fillColors.all { (_, color) -> color is JsonPrimitive }) -> {
                                fillColors.forEach { (ix, color) ->
                                    styling.update(ix, Styling()) {
                                        it.copy(fillColor = json.decodeFromJsonElement(ColorCssSerializer, color))
                                    }
                                }
                                state.remove("fillColors")
                            }
                            else -> {}
                        }
                        when (val labels = element["labels"]) {
                            is JsonObject if (labels.all { (_, label) -> label is JsonPrimitive }) -> {
                                labels.forEach { (ix, label) ->
                                    styling.update(ix, Styling()) {
                                        it.copy(label = Styling.Label(label.jsonPrimitive.content))
                                    }
                                }
                                state.remove("labels")
                            }
                            else -> {}
                        }
                        when (val phantoms = element["phantoms"]) {
                            is JsonArray if (phantoms.all { it is JsonPrimitive }) -> {
                                phantoms.forEach { ix ->
                                    styling.update(ix.jsonPrimitive.content, Styling()) {
                                        it.copy(isPhantom = true)
                                    }
                                }
                                state.remove("phantoms")
                            }
                            else -> {}
                        }
                        if (styling.isEmpty()) {
                            element
                        } else {
                            state["styling"] = JsonObject(styling.mapValues {
                                json.encodeToJsonElement(it)
                            })
                            JsonObject(state)
                        }
                    }
                    else -> element
                }
                else -> element
            }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val SERIALIZERS_MODULE = SerializersModule {
//            polymorphic(GCircleOrConcreteAcPath::class) {
//                subclass(Circle::class)
//                subclass(Line::class)
//                subclass(ImaginaryCircle::class)
//                subclass(Point::class)
//                subclass(ConcreteArcPath::class)
//            }
//            polymorphic(ExprOutput::class) {
//                subclass(ExprOutput.Just.serializer(PolymorphicSerializer(Any::class)))
//                subclass(ExprOutput.OneOf.serializer(PolymorphicSerializer(Any::class)))
//            }
//            polymorphic(Expr.Conformal::class) {
//                subclassesOfSealed<ArcPath>()
//                subclassesOfSealed<Expr.Conformal.OneToOne>()
//                subclassesOfSealed<Expr.Conformal.OneToMany>()
//            }
//            polymorphic(Expr.OneToOne::class) {
//                subclassesOfSealed<Expr.Conformal.OneToOne>()
//                subclassesOfSealed<Expr.Projective.OneToOne>()
//            }
//            polymorphic(Expr.OneToMany::class) {
//                subclassesOfSealed<Expr.Conformal.OneToMany>()
//                subclassesOfSealed<Expr.Projective.OneToMany>()
//            }
        }
        val JSON_FORMAT = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            // support Infinity for Points
            allowSpecialFloatingPointValues = true
            serializersModule = SERIALIZERS_MODULE
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
                styling = emptyMap(),
                regions = emptyList(),
                backgroundColor = null,
                chessboardPattern = ChessboardPattern.STARTS_TRANSPARENT,
                chessboardColor = Color(56, 136, 116),
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
