package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import data.Cluster
import data.OldCluster
import data.compressPart
import data.geometry.ArcPath
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Line
import data.geometry.Point
import domain.Arg
import domain.ArgType
import domain.Command
import domain.History
import domain.Ix
import domain.OffsetSerializer
import domain.PartialArgList
import domain.PointSnapResult
import domain.angleDeg
import domain.expressions.Expr
import domain.expressions.Expression
import domain.expressions.ExpressionForest
import domain.expressions.ExtrapolationParameters
import domain.expressions.IncidenceParameters
import domain.expressions.Indexed
import domain.expressions.InterpolationParameters
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.computeCircleBy3Points
import domain.expressions.computeCircleByCenterAndRadius
import domain.expressions.computeLineBy2Points
import domain.io.Ddc
import domain.io.OldDdc
import domain.io.cluster2svg
import domain.io.cluster2svgCheckPattern
import domain.io.parseDdc
import domain.io.parseOldDdc
import domain.reindexingMap
import domain.snapAngle
import domain.snapPointToCircles
import domain.snapPointToPoints
import getPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import ui.theme.DodeclustersColors
import ui.tools.EditClusterCategory
import ui.tools.EditClusterTool
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

// TODO: migrate to Decompose 3 for a real VM impl
// MAYBE: use UiState functional pattern instead of this mess
// this class is obviously too big
// TODO: decouple navigation & tools/categories
@Stable
class EditClusterViewModel(
    /** NOT a viewModelScope, just a rememberCS from the screen composable */
    private val coroutineScope: CoroutineScope,
    initialCircles: List<CircleOrLine?>,
    initialParts: List<Cluster.Part>,
    initialExpressions: Map<Indexed, Expression?> =
        initialCircles.indices.associate { Indexed.Circle(it) to null }
) {
    var mode: Mode by mutableStateOf(SelectionMode.Drag)
        private set
    // XYPoint uses absolute positioning
    var partialArgList: PartialArgList? by mutableStateOf(null)
        private set
    /** [Cluster.circles] */
    val circles: SnapshotStateList<CircleOrLine?> = initialCircles.toMutableStateList()
    /** helper anchor points for snapping; these are not saved into [Ddc] */
    val points: SnapshotStateList<Point?> = mutableStateListOf()
    /** [Cluster.parts] */
    val parts: SnapshotStateList<Cluster.Part> = initialParts.toMutableStateList()

    var expressions: ExpressionForest = ExpressionForest(
        initialExpressions = initialExpressions,
        get = { when (it) {
            is Indexed.Circle -> circles[it.index]?.downscale()
            is Indexed.Point -> points[it.index]?.downscale()
        } },
        set = { ix, value -> when (ix) {
            is Indexed.Circle ->
                circles[ix.index] = (value as? CircleOrLine)?.upscale()
            is Indexed.Point ->
                points[ix.index] = (value as? Point)?.upscale()
        } }
    )

    /** indices of selected [circles] */
    val selection = mutableStateListOf<Ix>() // MAYBE: when circles are hidden select parts instead
    /** indices of selected [points] */
    var selectedPoints by mutableStateOf(listOf<Ix>())

    val categories: List<EditClusterCategory> = listOf(
        EditClusterCategory.Drag,
        EditClusterCategory.Multiselect,
        EditClusterCategory.Region,
        EditClusterCategory.Visibility,
        EditClusterCategory.Colors,
        EditClusterCategory.Transform,
        EditClusterCategory.Create, // FAB
    )
    // this int list is to be persisted/preserved
    // category index -> tool index among category.tools
    val categoryDefaults: SnapshotStateList<Int> =
        categories.map { it.tools.indexOf(it.default) }.toMutableStateList()
    var activeCategoryIndex: Int by mutableIntStateOf(0)
        private set
    val activeCategory: EditClusterCategory by derivedStateOf { categories[activeCategoryIndex] }
    var activeTool: EditClusterTool by mutableStateOf(activeCategory.default ?: EditClusterTool.Drag)
        private set
    private val panelNeedsToBeShown by derivedStateOf { activeCategory.tools.size > 1 }
    var showPanel by mutableStateOf(panelNeedsToBeShown)
    var showPromptToSetActiveSelectionAsToolArg by mutableStateOf(false) // to be updated manually
        private set
    var showUI by mutableStateOf(true)

    /** currently selected color */
    var regionColor by mutableStateOf(DodeclustersColors.primaryDark) // DodeclustersColors.purple)
        private set
    var showCircles by mutableStateOf(true)
        private set
    /** which style to use when drawing parts: true = stroke, false = fill */
    var showWireframes by mutableStateOf(false)
        private set
    /** applies to [SelectionMode.Region]:
     * only use circles present in the [selection] to determine which parts to fill */
    var restrictRegionsToSelection by mutableStateOf(false)
        private set
    var displayChessboardPattern by mutableStateOf(false)
        private set
    /** true = background starts colored */
    var chessboardPatternStartsColored by mutableStateOf(true)
        private set

    val circleSelectionIsActive by derivedStateOf {
        showCircles && selection.isNotEmpty() && mode.isSelectingCircles()
    }
    val pointSelectionIsActive by derivedStateOf {
        showCircles && selectedPoints.isNotEmpty() && mode.isSelectingCircles()
    }
    val handleConfig by derivedStateOf { // depends on selectionMode & selection
        when (mode) {
            SelectionMode.Drag ->
                if (selection.isEmpty()) null
                else HandleConfig.SingleCircle(selection.single())
            SelectionMode.Multiselect -> when {
                selection.isEmpty() -> null
                selection.size == 1 -> HandleConfig.SingleCircle(selection.single())
                selection.size > 1 -> HandleConfig.SeveralCircles(selection)
                else -> Unit // never
            }
            SelectionMode.Region -> null
            else -> null
        }
    }
    var submode: SubMode by mutableStateOf(SubMode.None)
        private set

    // NOTE: history doesn't survive background app kill
    private val history = History<UiState>(
        saveState = { UiState.save(this) },
        loadState = { state -> loadUiState(state) }
    )
    var undoIsEnabled by mutableStateOf(false) // = history is not empty
    var redoIsEnabled by mutableStateOf(false) // = redoHistory is not empty

    var defaultInterpolationParameters = DefaultInterpolationParameters()
        private set
    var defaultExtrapolationParameters = DefaultExtrapolationParameters()
        private set
    var defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters()
        private set

    private val _circleAnimations = MutableSharedFlow<CircleAnimation>()
    val circleAnimations = _circleAnimations.asSharedFlow()

    var showColorPickerDialog by mutableStateOf(false)
    var showCircleInterpolationDialog by mutableStateOf(false)
    var showCircleExtrapolationDialog by mutableStateOf(false)
    var showLoxodromicMotionDialog by mutableStateOf(false)

    var arcPathUnderConstruction by mutableStateOf<ArcPath?>(null)
        private set

    var canvasSize: IntSize by mutableStateOf(IntSize.Zero) // used when saving best-center
        private set
    private val selectionControlsPositions by derivedStateOf {
        SelectionControlsPositions(canvasSize)
    }
    var translation by mutableStateOf(Offset.Zero) // pre-scale offset
//    val scale = mutableStateOf(1f)

    /** min tap/grab distance to select an object */
    private var tapRadius = getPlatform().tapRadius

    fun setEpsilon(density: Density) {
        with (density) {
            tapRadius = getPlatform().tapRadius.dp.toPx()
        }
    }

    fun changeCanvasSize(newCanvasSize: IntSize) {
        val prevCenter = Offset(canvasSize.width/2f, canvasSize.height/2f)
        val newCenter = Offset(newCanvasSize.width/2f, newCanvasSize.height/2f)
        translation = translation + (newCenter - prevCenter)
        canvasSize = newCanvasSize
    }

    fun saveAsYaml(name: String = Ddc.DEFAULT_NAME): String {
        val nullCircles = circles.indices.filter { circles[it] == null }
        val circleReindexing = reindexingMap(circles.indices, nullCircles.toSet())
        val realCircles = circles.filterNotNull()
        val reindexedParts = parts.map { part ->
            part.copy(
                insides = part.insides.map { circleReindexing[it]!! }.toSet(),
                outsides = part.outsides.map { circleReindexing[it]!! }.toSet(),
            )
        }
        val cluster = Cluster(
            realCircles, reindexedParts
        )
        var ddc = Ddc(cluster).copy(
            name = name,
            chessboardPattern = displayChessboardPattern,
            chessboardPatternStartsColored = chessboardPatternStartsColored,
        )
        computeAbsoluteCenter()?.let { center ->
            ddc = ddc.copy(bestCenterX = center.x, bestCenterY = center.y)
        }
        return ddc.encode()
    }

    fun exportAsSvg(name: String = Ddc.DEFAULT_NAME): String {
        val nullCircles = circles.indices.filter { circles[it] == null }
        val circleReindexing = reindexingMap(circles.indices, nullCircles.toSet())
        val realCircles = circles.filterNotNull()
        val reindexedParts = parts.map { part ->
            part.copy(
                insides = part.insides.map { circleReindexing[it]!! }.toSet(),
                outsides = part.outsides.map { circleReindexing[it]!! }.toSet(),
            )
        }
        val cluster = Cluster(
            realCircles, reindexedParts
        )
        val start = absolute(Offset.Zero)
        return if (displayChessboardPattern)
            cluster2svgCheckPattern(
                cluster = cluster, backgroundColor = regionColor, chessboardPatternStartsColored = chessboardPatternStartsColored,
                startX = start.x, startY = start.y,
                width = canvasSize.width.toFloat(), height = canvasSize.height.toFloat()
            )
        else
            cluster2svg(
                cluster = cluster,
                backgroundColor = null,
                startX = start.x, startY = start.y,
                width = canvasSize.width.toFloat(), height = canvasSize.height.toFloat()
            )
    }

    private fun computeAbsoluteCenter(): Offset? =
        if (canvasSize == IntSize.Zero) {
            null
        } else {
            val visibleCenter = Offset(canvasSize.width/2f, canvasSize.height/2f)
            absolute(visibleCenter)
        }

    fun loadFromYaml(yaml: String) {
        try {
            val ddc = parseDdc(yaml)
            val cluster = ddc.content
                .filterIsInstance<Ddc.Token.Cluster>()
                .first()
                .toCluster()
            loadCluster(cluster)
            moveToDdcCenter(ddc.bestCenterX, ddc.bestCenterY)
            displayChessboardPattern = ddc.chessboardPattern
            chessboardPatternStartsColored = ddc.chessboardPatternStartsColored
        } catch (e: Exception) {
            println("Failed to parse yaml")
            e.printStackTrace()
            try {
                println("Falling back to OldDdc yaml")
                e.printStackTrace()
                val ddc = parseOldDdc(yaml)
                val cluster = ddc.content
                    .filterIsInstance<OldDdc.Token.Cluster>()
                    .first()
                    .toCluster()
                loadCluster(cluster)
                moveToDdcCenter(ddc.bestCenterX, ddc.bestCenterY)
            } catch (e: Exception) {
                println("Falling back to json")
                loadFromJson(yaml) // NOTE: for backwards compat
            }
        }
    }

    fun moveToDdcCenter(bestCenterX: Float?, bestCenterY: Float?) {
        translation = -Offset(
            bestCenterX?.let { it - canvasSize.width/2f } ?: 0f,
            bestCenterY?.let { it - canvasSize.height/2f } ?: 0f
        )
    }

    // backwards compatibility
    fun saveAsJson(): String {
        val cluster = Cluster(
            circles.filterNotNull(), parts.toList(), filled = true
        )
        return Json.encodeToString(Cluster.serializer(), cluster)
    }

    // backwards compatibility
    fun loadFromJson(json: String) {
        try {
            val permissiveJson = Json {
                isLenient = true
                ignoreUnknownKeys = true // enables backward compatibility to a certain level
            }
            val cluster: OldCluster = permissiveJson.decodeFromString(OldCluster.serializer(), json)
            loadCluster(Cluster(
                cluster.circles,
                cluster.parts,
                cluster.filled
            ))
        } catch (e: SerializationException) {
            println("Failed to parse json")
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            println("Failed to parse json")
            e.printStackTrace()
        }
    }

    fun loadCluster(cluster: Cluster) {
        showPromptToSetActiveSelectionAsToolArg = false
        displayChessboardPattern = false
        chessboardPatternStartsColored = true
        translation = Offset.Zero
        selection.clear()
        selectedPoints = emptyList()
        parts.clear()
        circles.clear()
        circles.addAll(cluster.circles)
        parts.addAll(cluster.parts)
        // reset history on load
        history.clear()
        resetTransients()
        expressions = ExpressionForest(
            initialExpressions = cluster.circles.indices.associate { Indexed.Circle(it) to null },
            get = { when (it) {
                is Indexed.Circle -> circles[it.index]?.downscale()
                is Indexed.Point -> points[it.index]?.downscale()
            } },
            set = { ix, value -> when (ix) {
                is Indexed.Circle ->
                    circles[ix.index] = (value as? CircleOrLine)?.upscale()
                is Indexed.Point ->
                    points[ix.index] = (value as? Point)?.upscale()
            } }
        )
    }

    fun undo() {
        val currentSelection = selection.toList()
        switchToMode(mode) // clears up stuff
        selection.clear()
        selectedPoints = emptyList()
        history.undo()
        selection.clear()
        selection.addAll(currentSelection.filter { it < circles.size })
        resetTransients()
    }

    fun redo() {
        // MAYBE: keep prev selection but be careful about extra stuff
        val currentSelection = selection.toList()
        switchToMode(mode)
        history.redo()
        selection.clear()
        selectedPoints = emptyList()
        selection.addAll(currentSelection.filter { it < circles.size })
        resetTransients()
    }

    private fun loadUiState(state: UiState) {
        submode = SubMode.None
        selection.clear()
        selectedPoints = emptyList()
        parts.clear()
        circles.clear()
        circles.addAll(state.circles)
        parts.addAll(state.parts)
        selection.clear() // switch can populate it
        selection.addAll(state.selection)
        points.clear()
        points.addAll(state.points)
        expressions = ExpressionForest(
            initialExpressions = state.expressions,
            get = { when (it) {
                is Indexed.Circle -> circles[it.index]?.downscale()
                is Indexed.Point -> points[it.index]?.downscale()
            } },
            set = { ix, value -> when (ix) {
                is Indexed.Circle ->
                    circles[ix.index] = (value as? CircleOrLine)?.upscale()
                is Indexed.Point ->
                    points[ix.index] = (value as? Point)?.upscale()
            } }
        )
    }

    private fun resetTransients() {
        showPromptToSetActiveSelectionAsToolArg = false
        submode = SubMode.None
        undoIsEnabled = history.undoIsEnabled
        redoIsEnabled = history.redoIsEnabled
    }

    /** Use BEFORE modifying the state by the [command]!
     * let s_i := history[[i]], c_i := commands[[i]]
     * s0 (aka original) -> c0 -> s1 -> c1 -> s2 ...
     *
     * [unique] flag guarantees snapshotting new state for [history]
     * */
    private fun recordCommand(
        command: Command,
        targets: Iterable<Ix>? = null,
        unique: Boolean = false
    ) {
        val tag = if (unique)
            Command.Tag.Unique()
        else
            targets?.let { Command.Tag.Targets(it.toList()) }
        history.recordCommand(command, tag)
        undoIsEnabled = history.undoIsEnabled
        redoIsEnabled = history.redoIsEnabled
        if (command != Command.ROTATE) // erm
            submode.let {
                if (it is SubMode.Rotate)
                    submode = SubMode.None
            }
    }

    fun createNewCircle(newCircle: CircleOrLine?) =
        createNewCircles(listOfNotNull(newCircle))

    /** Append [newCircles] to [circles] and queue circle entrance animation */
    fun createNewCircles(
        newCircles: List<CircleOrLine?>,
    ) {
        val normalizedCircles = newCircles.map {
            if (it is Circle && it.radius <= 0)
                null
            else it
        }
        val validNewCircles = normalizedCircles.filterNotNull()
        if (validNewCircles.isNotEmpty()) {
            recordCommand(Command.CREATE, unique = true)
            showCircles = true
            val prevSize = circles.size
            circles.addAll(normalizedCircles)
            selection.clear()
            selection.addAll((prevSize until circles.size).filter { circles[it] != null })
            coroutineScope.launch {
                _circleAnimations.emit(
                    CircleAnimation.Entrance(validNewCircles)
                )
            }
        } else {
            circles.addAll(normalizedCircles)
            selection.clear()
        }
    }

    fun createNewFreePoint(
        point: Point
    ): Indexed.Point {
        recordCommand(Command.CREATE, unique = true)
        points.add(point)
        expressions.addFree(isPoint = true)
        return Indexed.Point(points.size - 1)
    }

    fun duplicateCircles() {
        if (mode.isSelectingCircles()) {
            recordCommand(Command.DUPLICATE, selection)
            val copiedCircles = selection.mapNotNull { circles[it] } // preserves selection order
            val oldSize = circles.size
            circles.addAll(copiedCircles)
            val newIndices = oldSize until oldSize + copiedCircles.size
            for (ix in newIndices)
                expressions.addFree(isPoint = false)
            copyParts(selection, newIndices.toList())
            selection.clear()
            selection.addAll(newIndices)
            coroutineScope.launch {
                _circleAnimations.emit(
                    CircleAnimation.ReEntrance(copiedCircles)
                )
            }
        }
    }

    /**
     * @param[orientationFlips] for every index from [newIndices] this flag indicates
     * that it is a circle that flipped it's orientation inside <-> outside
     * */
    private fun copyParts(
        oldIndices: List<Ix>,
        newIndices: List<Ix>,
        orientationFlips: List<Boolean> = newIndices.map { false }
    ) {
        // TODO: line -> circle flipping
        require(oldIndices.size == newIndices.size && oldIndices.size == orientationFlips.size)
        val old2new = oldIndices.zip(newIndices).toMap()
        val new2flip = newIndices.zip(orientationFlips).toMap()
        val newParts = parts.filter {
            oldIndices.containsAll(it.insides) && oldIndices.containsAll(it.outsides)
        }.map { part ->
            val newInsides = mutableSetOf<Ix>()
            val newOutsides = mutableSetOf<Ix>()
            for (i in part.insides) {
                val new = old2new[i]!!
                if (new2flip[new]!!)
                    newOutsides.add(new)
                else
                    newInsides.add(new)
            }
            for (i in part.outsides) {
                val new = old2new[i]!!
                if (new2flip[new]!!)
                    newInsides.add(new)
                else
                    newOutsides.add(new)
            }
            Cluster.Part(
                insides = newInsides,
                outsides = newOutsides,
                fillColor = part.fillColor
            )
        }
        parts.addAll(newParts)
    }

    fun deleteCircles() {
        val thereAreSelectedCirclesToDelete = circleSelectionIsActive
        if (selectedPoints.isNotEmpty() || thereAreSelectedCirclesToDelete)
            recordCommand(Command.DELETE, unique = true)
        val toBeDeleted = expressions.deleteNodes(
            selectedPoints.map { Indexed.Point(it) } +
                if (circleSelectionIsActive) selection.map { Indexed.Circle(it) }
                else emptyList()
        )
        val pointsToBeDelete = toBeDeleted.filterIsInstance<Indexed.Point>()
        val circlesToBeDelete = toBeDeleted.filterIsInstance<Indexed.Circle>()
        for (ix in pointsToBeDelete)
            points[ix.index] = null
        selectedPoints = emptyList()
        if (circlesToBeDelete.isNotEmpty()) {
            val whatsGone = circlesToBeDelete.map { it.index }
                .toSet()
            val deletedCircles = whatsGone.mapNotNull { circles[it] }
            val oldParts = parts.toList()
            selection.clear()
            parts.clear()
            for (ix in whatsGone)
                circles[ix] = null
            parts.addAll(
                oldParts
                    // to avoid stray chessboard selections
                    .filterNot { (ins, _, _) -> ins.isNotEmpty() && ins.minus(whatsGone).isEmpty() }
                    .map { (ins, outs, fillColor) ->
                        Cluster.Part(
                            insides = ins.minus(whatsGone),
                            outsides = outs.minus(whatsGone),
                            fillColor = fillColor
                        )
                    }
                    .filter { (ins, outs) -> ins.isNotEmpty() || outs.isNotEmpty() }
            )
            coroutineScope.launch {
                _circleAnimations.emit(
                    CircleAnimation.Exit(deletedCircles)
                )
            }
        }
    }

    fun getArg(arg: Arg.Point): Point? =
        when (arg) {
            is Arg.Point.Index -> points[arg.index]
            is Arg.Point.XY -> arg.toPoint()
        }

    fun getArg(arg: Arg.CircleOrPoint): GCircle? =
        when (arg) {
            is Arg.CircleOrPoint.Point.Index -> points[arg.index]
            is Arg.CircleOrPoint.Point.XY -> arg.toPoint()
            is Arg.CircleOrPoint.CircleIndex -> circles[arg.index]
        }

    fun switchToMode(newMode: Mode) {
        // NOTE: these altering shortcuts are unused for now so that they don't confuse category-expand buttons
        if (selection.size > 1 && newMode == SelectionMode.Drag)
            selection.clear()
        selectedPoints = emptyList()
        showPromptToSetActiveSelectionAsToolArg = false
        if (newMode is ToolMode) {
            if (selection.size > 1 &&
                newMode.signature.argTypes.first() == ArgType.CircleAndPointIndices
            ) {
                showPromptToSetActiveSelectionAsToolArg = true
            } else {
                // keep selection for a bit in case we now switch to another mode that
                // accepts selection as the first arg
            }
            if (newMode == ToolMode.ARC_PATH) {
                partialArgList = null
            } else {
                showCircles = true
                partialArgList = PartialArgList(newMode.signature)
            }
        } else {
            partialArgList = null
        }
        mode = newMode
        submode = SubMode.None
        arcPathUnderConstruction = null
    }

    fun absolute(visiblePosition: Offset): Offset =
        visiblePosition - translation

    fun visible(position: Offset): Offset =
        position + translation

    fun isCloseEnoughToSelect(absolutePosition: Offset, visiblePosition: Offset, lowAccuracy: Boolean = false): Boolean {
        val position = absolute(visiblePosition)
        return (absolutePosition - position).getDistance() <= tapRadius * (if (lowAccuracy) LOW_ACCURACY_FACTOR else 1f)
    }

    fun selectPoint(
        targets: List<Point?>,
        visiblePosition: Offset,
        priorityTargets: Set<Ix> = emptySet(),
    ): Ix? {
        val position = absolute(visiblePosition)
        val absolutePoint = Point.fromOffset(position)
        return targets
            .mapIndexed { ix, point ->
                val distance = point?.distanceFrom(absolutePoint) ?: Double.POSITIVE_INFINITY
                ix to distance
            }.filter { (_, distance) -> distance <= tapRadius }
            .minByOrNull { (ix, distance) ->
                val priority =
                    if (ix in priorityTargets) 100
                    else 1
                distance / priority
            }
            ?.let { (ix, _) -> ix }
            ?.also { println("select point #$it: ${points[it]} <- ${expressions.expressions[Indexed.Point(it)]}") }
    }

    fun reselectPointAt(visiblePosition: Offset): Boolean {
        val nearPointIndex = selectPoint(points, visiblePosition,
            priorityTargets = points.indices
                .filter { expressions.expressions[Indexed.Point(it)] == null }
                .toSet()
        )
        if (nearPointIndex == null) {
            selectedPoints = listOf()
            return false
        } else {
            selectedPoints = listOf(nearPointIndex)
            return true
        }
    }

    fun selectCircle(
        targets: List<CircleOrLine?>,
        visiblePosition: Offset,
        priorityTargets: Set<Ix> = emptySet(),
    ): Ix? {
        val position = absolute(visiblePosition)
        return targets.mapIndexed { ix, circle ->
            val distance = circle?.distanceFrom(position) ?: Double.POSITIVE_INFINITY
            ix to distance
        }
            .filter { (_, distance) -> distance <= tapRadius }
            .minByOrNull { (ix, distance) ->
                val priority =
                    if (ix in priorityTargets) 100
                    else 1
                distance / priority
            }
            ?.let { (ix, _) -> ix }
            ?.also { println("select circle #$it: ${circles[it]} <- ${expressions.expressions[Indexed.Circle(it)]}") }
    }

    fun reselectCircleAt(visiblePosition: Offset): Boolean {
        val nearCircleIndex = selectCircle(circles, visiblePosition,
            priorityTargets = circles.indices
                .filter { expressions.expressions[Indexed.Circle(it)] == null }
                .toSet()
        )
        selection.clear()
        if (nearCircleIndex == null) {
            return false
        } else {
            selection.add(nearCircleIndex)
            return true
        }
    }

    fun reselectCirclesAt(visiblePosition: Offset): Ix? =
        selectCircle(circles, visiblePosition)?.also { ix ->
            if (ix in selection)
                selection.remove(ix)
            else
                selection.add(ix)
        }

    /** -> (compressed part, verbose part involving all circles) surrounding clicked position */
    private fun selectPartAt(
        visiblePosition: Offset,
        boundingCircles: List<Ix>? = null
    ): Pair<Cluster.Part, Cluster.Part> {
        val position = absolute(visiblePosition)
        val delimiters = boundingCircles ?: circles.indices
        val ins = delimiters // NOTE: doesn't include circles that the point lies on
            .filter { ix -> circles[ix]?.hasInside(position) ?: false }
        val outs = delimiters
            .filter { ix -> circles[ix]?.hasOutside(position) ?: false }
        val (essentialIns, essentialOuts) = compressPart(circles, ins, outs)
        val part0 = Cluster.Part(ins.toSet(), outs.toSet(), regionColor)
        val part = Cluster.Part(
//            insides = sievedIns.toSet(),
//            outsides = sievedOuts.toSet(),
            insides = essentialIns,
            outsides = essentialOuts,
            fillColor = regionColor
        )
        return Pair(part, part0)
    }

    fun reselectRegionAt(
        visiblePosition: Offset,
        boundingCircles: List<Ix>? = null,
        setSelectionToRegionBounds: Boolean = false
    ) {
        displayChessboardPattern = false
        val (part, part0) = selectPartAt(visiblePosition, boundingCircles)
        val outerParts = parts.filter { part isObviouslyInside it || part0 isObviouslyInside it  }
        if (outerParts.isEmpty()) {
            recordCommand(Command.FILL_REGION, listOf(parts.size))
            parts.add(part)
            if (setSelectionToRegionBounds && !restrictRegionsToSelection) {
                selection.clear()
                selection.addAll(part.insides + part.outsides)
            }
            println("added $part")
        } else {
            val sameExistingPart = parts.singleOrNull {
                part.insides == it.insides && part.outsides == it.outsides
            }
            recordCommand(Command.FILL_REGION, unique = true)
            if (sameExistingPart != null) {
                parts.remove(sameExistingPart)
                if (part == sameExistingPart) {
                    println("removed $sameExistingPart")
                } else { // we are trying to change color im guessing
                    parts.add(sameExistingPart.copy(fillColor = part.fillColor))
                    if (setSelectionToRegionBounds && !restrictRegionsToSelection) {
                        selection.clear()
                        selection.addAll(part.insides + part.outsides)
                    }
                    println("recolored $sameExistingPart")
                }
            } else {
                parts.removeAll(outerParts)
                if (
                    outerParts.all { it.fillColor == outerParts[0].fillColor } &&
                    outerParts[0].fillColor != part.fillColor
                ) { // we are trying to change color im guessing
                    parts.addAll(outerParts.map { it.copy(fillColor = part.fillColor) })
                    println("recolored parts [${outerParts.joinToString(prefix = "\n", separator = ";\n")}]")
                } else {
                    println("removed parts [${outerParts.joinToString(prefix = "\n", separator = ";\n")}]")
                }
            }
        }
    }

    /** absolute positions */
    fun getSelectionRect(): Rect? {
        val selectedCircles = selection
            .map { circles[it] }
            .filterIsInstance<Circle>()
        if (selectedCircles.isEmpty() || selectedCircles.size < selection.size)
            return null
        val left = selectedCircles.minOf { (it.x - it.radius).toFloat() }
        val right = selectedCircles.maxOf { (it.x + it.radius).toFloat() }
        val top = selectedCircles.minOf { (it.y - it.radius) }.toFloat()
        val bottom = selectedCircles.maxOf { (it.y + it.radius) }.toFloat()
        return Rect(left, top, right, bottom)
    }

    inline fun isFreeCircle(circleIndex: Ix): Boolean =
        expressions.expressions[Indexed.Circle(circleIndex)] == null
    inline fun isFreePoint(pointIndex: Ix): Boolean =
        expressions.expressions[Indexed.Point(pointIndex)] == null

    fun snapped(
        absolutePosition: Offset,
        excludePoints: Boolean = false,
        excludedCircles: Set<Ix> = emptySet(),
    ): PointSnapResult {
        // snap to: points > circles > circle contact
        val snapDistance = tapRadius.toDouble()
        val point = Point.fromOffset(absolutePosition)
        val point2pointSnapping = !excludePoints && mode != ToolMode.POINT
        if (point2pointSnapping) {
            val snappablePoints = points
            val p2pResult = snapPointToPoints(point, snappablePoints, snapDistance)
            if (p2pResult is PointSnapResult.Eq)
                return p2pResult
        }
        val point2circleSnapping = showCircles
        if (!point2circleSnapping) // no snapping to invisibles
            return PointSnapResult.Free(point)
        val snappableCircles = circles.mapIndexed { ix, c ->
            if (ix in excludedCircles) null
            else c
        }
        val p2cResult = snapPointToCircles(point, snappableCircles, snapDistance)
        return p2cResult
    }

    private fun realizePointCircleSnap(snapResult: PointSnapResult): PointSnapResult.PointToPoint =
        when (snapResult) {
            is PointSnapResult.Free -> snapResult
            is PointSnapResult.Eq -> snapResult
            is PointSnapResult.Incidence -> {
                val circle = circles[snapResult.circleIndex]!!
                // NOTE: we have to downscale to measure order for lines properly
                val order = circle.downscale().point2order(snapResult.result.downscale())
                val expr = Expr.Incidence(
                    IncidenceParameters(order),
                    Indexed.Circle(snapResult.circleIndex)
                )
                recordCommand(Command.CREATE, unique = true)
                val newPoint = expressions.addSoloExpression(expr, isPoint = true) as? Point
                points.add(newPoint?.upscale())
                PointSnapResult.Eq(points.last()!!, points.size - 1)
            }
            is PointSnapResult.Intersection -> {
                val point = snapResult.result
                val expr = Expr.Intersection(
                    Indexed.Circle(snapResult.circle1Index),
                    Indexed.Circle(snapResult.circle2index)
                )
                val j = points.size
                recordCommand(Command.CREATE, unique = true)
                val ps = expressions.addMultiExpression(expr, isPoint = true)
                    .map { it as? Point }
                    .map { it?.upscale() }
                points.addAll(ps)
                val (p1, p2) = ps
                // we dont know in which order Circle.intersection will output these points...
                val realIndex = if (
                    (p1?.distanceFrom(point) ?: Double.POSITIVE_INFINITY) <=
                    (p2?.distanceFrom(point) ?: Double.POSITIVE_INFINITY)
                ) j
                else j+1
                PointSnapResult.Eq(snapResult.result, realIndex)
            }
        }

    fun activateFlowSelect() {
        switchToMode(SelectionMode.Multiselect)
        selection.clear()
        submode = SubMode.FlowSelect()
    }

    fun activateFlowFill() {
        switchToMode(SelectionMode.Region)
        submode = SubMode.FlowFill()
    }

    fun toggleSelectAll() {
        switchToMode(SelectionMode.Multiselect)
        showCircles = true
        val notEverythingIsSelected = !selection.containsAll(circles.indices.toSet())
        if (notEverythingIsSelected) {
            selection.clear()
            selection.addAll(circles.indices)
            selectedPoints = points.indices.toList()
        } else {
            selection.clear()
            selectedPoints = emptyList()
        }
    }

    fun toggleShowCircles() {
        showCircles = !showCircles
        if (!showCircles && mode is ToolMode)
            switchToMode(SelectionMode.Drag)
    }

    fun hideUIFor30s() {
        if (showUI) {
            showUI = false
            coroutineScope.launch {
                delay(30.seconds)
                showUI = true
            }
        }
    }

    fun toggleRestrictRegionsToSelection() {
        restrictRegionsToSelection = !restrictRegionsToSelection
    }

    fun applyChessboardPatter() {
        if (!displayChessboardPattern) {
            displayChessboardPattern = true
            chessboardPatternStartsColored = true
        } else if (chessboardPatternStartsColored) {
            chessboardPatternStartsColored = false
        } else {
            displayChessboardPattern = false
            chessboardPatternStartsColored = true
        }
    }

    fun selectRegionColor(color: Color) {
        showColorPickerDialog = false
        regionColor = color
        selectTool(EditClusterTool.Region)
//        showPanel = true
    }
    
    // MAYBE: replace with select-all->delete in invisible-circles part manipulation mode
    fun deleteAllParts() {
        recordCommand(Command.DELETE, unique = true)
        displayChessboardPattern = false
        parts.clear()
    }

    fun cancelSelectionAsToolArgPrompt() {
        if (showPromptToSetActiveSelectionAsToolArg) {
            showPromptToSetActiveSelectionAsToolArg = false
            selection.clear()
        }
    }

    fun setActiveSelectionAsToolArg() {
        activeTool.let { tool ->
            require(
                tool is EditClusterTool.MultiArg &&
                tool.signature.argTypes.first() == ArgType.CircleAndPointIndices &&
                selection.isNotEmpty()
            )
        }
        partialArgList = partialArgList!!.addArg(
            Arg.CircleAndPointIndices(
                circleIndices = selection.toList(),
                pointIndices = selectedPoints,
            ),
            confirmThisArg = true
        )
        cancelSelectionAsToolArgPrompt()
        if (partialArgList!!.isFull)
            completeToolMode()
    }

    fun insertCenteredCross() {
        val (midX, midY) = canvasSize.toSize()/2f
        val horizontalLine = Line.by2Points(
            absolute(Offset(0f, midY)),
            absolute(Offset(2*midX, midY)),
        )
        val verticalLine = Line.by2Points(
            absolute(Offset(midX, 0f)),
            absolute(Offset(midX, 2*midY)),
        )
        showCircles = true
        createNewCircles(listOf(horizontalLine, verticalLine))
        expressions.addFree(isPoint = false)
        expressions.addFree(isPoint = false)
        switchToMode(SelectionMode.Multiselect)
        selection.clear()
        selection.addAll(listOf(circles.size - 2, circles.size - 1))
    }

    fun scaleSelection(zoom: Float) {
        if (circleSelectionIsActive) {
            when (selection.size) {
                1 -> {
                    recordCommand(Command.CHANGE_RADIUS, targets = selection)
                    val ix = selection.single()
                    when (val circle = circles[ix]) {
                        is Circle ->
                            circles[ix] = circle.copy(radius = zoom * circle.radius)
                        else -> {}
                    }
                }

                else -> {
                    recordCommand(Command.SCALE, targets = selection)
                    val rect = getSelectionRect()
                    val center =
                        if (rect == null || rect.minDimension >= 5_000)
                            absolute(canvasSize.center.toOffset())
                        else rect.center
                    for (ix in selection) {
                        circles[ix] = circles[ix]?.scale(center, zoom)
                    }
                }
            }
            expressions.update(selection.map { Indexed.Circle(it) })
        } else if (mode == ToolMode.ARC_PATH && arcPathUnderConstruction != null) {
//            arcPathUnderConstruction = arcPathUnderConstruction?.scale(zoom)
        } else { // NOTE: scaling everything instead of canvas can produce more artifacts
            val allIndices = circles.indices
            recordCommand(Command.SCALE, targets = allIndices)
            val center = absolute(canvasSize.center.toOffset())
            for (ix in allIndices) {
                circles[ix] = circles[ix]?.scale(center, zoom)
            }
            for ((ix, point) in points.withIndex()) {
                points[ix] = point?.scale(center, zoom)
            }
            // no need to recompute expressions here
        }
    }

    fun onDown(visiblePosition: Offset) {
        // reset grabbed thingies
        if (showCircles) {
            when (val h = handleConfig) {
                is HandleConfig.SingleCircle -> {
                    val circle = circles[h.ix]
                    if (circle is Circle) {
                        val radiusHandlePosition = circle.center + Offset(circle.radius.toFloat(), 0f)
                        when {
                            isCloseEnoughToSelect(radiusHandlePosition, visiblePosition, lowAccuracy = true) ->
                                submode = SubMode.Scale(circle.center)
                        }
                    }
                }
                is HandleConfig.SeveralCircles -> {
                    getSelectionRect()?.let { rect ->
                        val scaleHandlePosition = rect.topRight
                        val rotateHandlePosition = rect.bottomRight
                        when {
                            isCloseEnoughToSelect(scaleHandlePosition, visiblePosition, lowAccuracy = true) ->
                                submode = SubMode.Scale(rect.center)
                            isCloseEnoughToSelect(rotateHandlePosition, visiblePosition, lowAccuracy = true) -> {
                                submode = SubMode.Rotate(rect.center)
                            }
                        }
                    }
                }
            }
            if (circleSelectionIsActive && submode is SubMode.None) {
                val screenCenter = absolute(Offset(canvasSize.width/2f, canvasSize.height/2f))
                when {
                    isCloseEnoughToSelect(absolute(selectionControlsPositions.sliderMiddleOffset), visiblePosition, lowAccuracy = true) ->
                        submode = SubMode.ScaleViaSlider(screenCenter)
                    isCloseEnoughToSelect(absolute(selectionControlsPositions.rotationHandleOffset), visiblePosition, lowAccuracy = true) ->
                        submode = SubMode.Rotate(screenCenter)
                }
            }
            // NOTE: this enables drag-only behavior, you lose your selection when grabbing new circle
            if (mode == SelectionMode.Drag && submode is SubMode.None) {
                val reselected = reselectPointAt(visiblePosition)
                if (reselected) {
                    selection.clear()
                } else {
                    val previouslySelectedCircle = selection.firstOrNull()
                    reselectCircleAt(visiblePosition)
                    if (previouslySelectedCircle != null && selection.isEmpty()) {
                        selection.add(previouslySelectedCircle)
                        // we keep previous selection in case we want to drag it
                        // but it can still be discarded in onTap
                    }
                }
            } else {
                if (mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect) {
                    val (_, qualifiedPart) = selectPartAt(visiblePosition)
                    submode = SubMode.FlowSelect(qualifiedPart)
                } else if (mode == SelectionMode.Region && submode is SubMode.FlowFill) {
                    val (_, qualifiedPart) = selectPartAt(visiblePosition)
                    submode = SubMode.FlowFill(qualifiedPart)
                    if (restrictRegionsToSelection && selection.isNotEmpty()) {
                        val restriction = selection.toList()
                        reselectRegionAt(visiblePosition, restriction)
                    } else {
                        reselectRegionAt(visiblePosition)
                    }
                } else if (mode is ToolMode) {
                    when (partialArgList?.nextArgType) {
                        ArgType.Point -> {
                            val result = snapped(absolute(visiblePosition))
                            val newArg = when (result) {
                                is PointSnapResult.Eq -> Arg.Point.Index(result.pointIndex)
                                else -> Arg.Point.XY(result.result)
                            }
                            if (FAST_CENTERED_CIRCLE && mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS && partialArgList!!.currentArg == null) {
                                val newArg2 = Arg.Point.XY(result.result)
                                partialArgList = partialArgList!!
                                    .addArg(newArg, confirmThisArg = true)
                                    .addArg(newArg2, confirmThisArg = true)
                                    .copy(lastSnap = result)
                            } else {
                                partialArgList = partialArgList!!
                                    .addArg(newArg, confirmThisArg = false)
                                    .copy(lastSnap = result)
                            }
                        }
                        ArgType.Circle -> {
                            selectCircle(circles, visiblePosition)?.let { circleIndex ->
                                val newArg = Arg.CircleIndex(circleIndex)
                                val previous = partialArgList?.currentArg
                                if (previous == newArg ||
                                    previous is Arg.CircleIndex && previous.index == circleIndex ||
                                    previous is Arg.CircleAndPointIndices && previous.circleIndices == listOf(circleIndex)
                                ) {
                                    // we ignore identical args (tho there can be a reason not to)
                                } else {
                                    partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = true)
                                }
                            }
                        }
                        ArgType.CircleOrPoint -> {
                            val result = snapped(absolute(visiblePosition))
                            if (result is PointSnapResult.Eq) {
                                val newArg = Arg.CircleOrPoint.Point.Index(result.pointIndex)
                                if (partialArgList!!.currentArg != newArg)
                                    partialArgList = partialArgList!!
                                        .addArg(newArg, confirmThisArg = false)
                                        .copy(lastSnap = result)
                            } else {
                                val circleIndex = selectCircle(circles, visiblePosition)
                                if (circleIndex != null) {
                                    val newArg = Arg.CircleOrPoint.CircleIndex(circleIndex)
                                    if (partialArgList!!.currentArg != newArg)
                                        partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = false)
                                } else {
                                    val newArg = Arg.CircleOrPoint.Point.XY(result.result)
                                    partialArgList = partialArgList!!
                                        .addArg(newArg, confirmThisArg = false)
                                        .copy(lastSnap = result)
                                }
                            }
                        }
                        ArgType.CircleAndPointIndices -> {
                            selectCircle(circles, visiblePosition)?.let { circleIndex ->
                                val newArg = Arg.CircleAndPointIndices(
                                    circleIndices = listOf(circleIndex),
                                    pointIndices = emptyList()
                                )
                                if (partialArgList!!.currentArg != newArg)
                                    partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = true)
                            } ?: selectPoint(points, visiblePosition)?.let { pointIndex ->
                                val newArg = Arg.CircleAndPointIndices(
                                    circleIndices = emptyList(),
                                    pointIndices = listOf(pointIndex)
                                )
                                if (partialArgList!!.currentArg != newArg)
                                    partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = true)
                            }
                        }
                        else -> if (mode == ToolMode.ARC_PATH) {
                            val absolutePoint = snapped(absolute(visiblePosition)).result
                            val arcPath = arcPathUnderConstruction
                            arcPathUnderConstruction = if (arcPath == null) {
                                ArcPath(
                                    startPoint = absolutePoint,
                                    focus = ArcPath.Focus.StartPoint
                                )
                            } else {
                                if (isCloseEnoughToSelect(arcPath.startPoint.toOffset(), visiblePosition)) {
                                    arcPath.copy(focus = ArcPath.Focus.StartPoint)
                                } else {
                                    val pointIx = arcPath.points.indexOfFirst {
                                        isCloseEnoughToSelect(it.toOffset(), visiblePosition)
                                    }
                                    if (pointIx != -1) {
                                        arcPath.copy(focus = ArcPath.Focus.Point(pointIx))
                                    } else {
                                        val midpointIx = arcPath.midpoints.indexOfFirst {
                                            isCloseEnoughToSelect(it.toOffset(), visiblePosition)
                                        }
                                        if (midpointIx != -1) {
                                            arcPath.copy(focus = ArcPath.Focus.MidPoint(midpointIx))
                                        } else {
                                            arcPath.addNewPoint(absolutePoint)
                                                .copy(focus = ArcPath.Focus.Point(arcPath.points.size))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // pointer input callbacks
    // onDown -> onTap
    fun onTap(position: Offset) {
        // select circle(s)/region
        if (showCircles) {
            when (mode) {
                SelectionMode.Drag -> {
                    val pointReselected = reselectPointAt(position)
                    if (pointReselected)
                        selection.clear()
                    else
                        reselectCircleAt(position)
                }
                SelectionMode.Multiselect -> {
                    // (re)-select part
                    val selectedCircleIx = reselectCirclesAt(position)
                    if (selectedCircleIx == null) { // try to select bounding circles of the selected part
                        val (part, part0) = selectPartAt(position)
                        if (part0.insides.isEmpty()) { // if we clicked outside of everything, toggle select all
                            toggleSelectAll()
                        } else {
                            parts
                                .withIndex()
                                .filter { (_, p) -> part isObviouslyInside p || part0 isObviouslyInside p }
                                .maxByOrNull { (_, p) -> p.insides.size + p.outsides.size }
                                ?.let { (i, existingPart) ->
                                    println("existing bound of $existingPart")
                                    val bounds: Set<Ix> = existingPart.insides + existingPart.outsides
                                    if (bounds != selection.toSet()) {
                                        selection.clear()
                                        selection.addAll(bounds)
                                    } else selection.clear()
                                } ?: run { // select bound of a non-existent part
                                    println("bounds of $part")
                                    val bounds: Set<Ix> = part.insides + part.outsides
                                    if (bounds != selection.toSet()) {
                                        selection.clear()
                                        selection.addAll(bounds)
                                    } else selection.clear()
                            }
                        }
                    }
                }
                SelectionMode.Region -> {
                    if (submode !is SubMode.FlowFill) {
                        if (restrictRegionsToSelection && selection.isNotEmpty()) {
                            val restriction = selection.toList()
                            reselectRegionAt(position, restriction)
                        } else {
                            reselectRegionAt(position)
                        }
                    }
                }
                ToolMode.CIRCLE_BY_CENTER_AND_RADIUS ->
                    if (FAST_CENTERED_CIRCLE && partialArgList!!.lastArgIsConfirmed) {
                        partialArgList = partialArgList!!.copy(
                            args = partialArgList!!.args.dropLast(1),
                            lastSnap = null
                        )
                    }
                ToolMode.ARC_PATH -> {
                    // when 3+ points, tap on the start closes the loop
                }
                else -> {}
            }
        }
    }

    // MAYBE: handle key arrows as panning
    fun onPanZoomRotate(pan: Offset, centroid: Offset, zoom: Float, rotationAngle: Float) {
        val c = absolute(centroid)
        if (submode !is SubMode.None) {
            // drag handle
            when (val h = handleConfig) {
                is HandleConfig.SingleCircle -> {
                    when (val sm = submode) {
                        is SubMode.Scale -> {
                            val circle = circles[h.ix]
                            if (circle is Circle) {
                                recordCommand(Command.CHANGE_RADIUS, targets = listOf(h.ix))
                                val center = sm.center
                                val r = (c - center).getDistance()
                                circles[h.ix] = circle.copy(radius = r.toDouble())
                                expressions.update(listOf(Indexed.Circle(h.ix)))
                            }
                        }
                        is SubMode.ScaleViaSlider -> {
                            val newPercentage = selectionControlsPositions.addPanToPercentage(sm.sliderPercentage, pan)
                            if (sm.sliderPercentage != newPercentage) {
                                recordCommand(Command.SCALE, targets = listOf(h.ix))
                                val circle = circles[h.ix]
                                val scaleFactor = sliderPercentageDeltaToZoom(newPercentage - sm.sliderPercentage)
                                circles[h.ix] = circle?.scale(sm.center, scaleFactor)
                                submode = sm.copy(sliderPercentage = newPercentage)
                                expressions.update(listOf(Indexed.Circle(h.ix)))
                            }
                        }
                        is SubMode.Rotate -> {
                            recordCommand(Command.ROTATE, targets = listOf(h.ix))
                            val center = sm.center
                            val centerToCurrent = c - center
                            val centerToPreviousHandle = centerToCurrent - pan
                            val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
                            val newAngle = sm.angle + angle
                            val snappedAngle =
                                if (ENABLE_ANGLE_SNAPPING) snapAngle(newAngle)
                                else newAngle
                            val angle1 = snappedAngle - sm.snappedAngle
                            circles[h.ix] = circles[h.ix]?.rotate(center, angle1.toFloat())
                            submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
                            expressions.update(listOf(Indexed.Circle(h.ix)))
                        }
                        else -> {}
                    }
                }
                is HandleConfig.SeveralCircles -> {
                    when (val sm = submode) {
                        is SubMode.Scale -> {
                            recordCommand(Command.SCALE, targets = selection)
                            getSelectionRect()?.let { rect ->
                                val scaleHandlePosition = rect.topRight
                                val center = rect.center
                                val centerToHandle = scaleHandlePosition - center
                                val centerToCurrent = centerToHandle + pan
                                val scaleFactor = centerToCurrent.getDistance()/centerToHandle.getDistance()
                                for (ix in selection) {
                                    circles[ix] = circles[ix]?.scale(center, scaleFactor)
                                }
                            }
                            expressions.update(selection.map { Indexed.Circle(it) })
                        }
                        is SubMode.ScaleViaSlider -> {
                            val newPercentage = selectionControlsPositions.addPanToPercentage(sm.sliderPercentage, pan)
                            if (sm.sliderPercentage != newPercentage) {
                                recordCommand(Command.SCALE, targets = selection)
                                val scaleFactor = sliderPercentageDeltaToZoom(newPercentage - sm.sliderPercentage)
                                for (ix in selection) {
                                    circles[ix] = circles[ix]?.scale(sm.center, scaleFactor)
                                }
                                submode = sm.copy(sliderPercentage = newPercentage)
                                expressions.update(selection.map { Indexed.Circle(it) })
                            }
                        }
                        is SubMode.Rotate -> {
                            recordCommand(Command.ROTATE, targets = selection)
                            val center = sm.center
                            val centerToCurrent = c - center
                            val centerToPreviousHandle = centerToCurrent - pan
                            val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
                            val newAngle = sm.angle + angle
                            val snappedAngle =
                                if (ENABLE_ANGLE_SNAPPING) snapAngle(newAngle)
                                else newAngle
                            val angle1 = snappedAngle - sm.snappedAngle
                            for (ix in selection) {
                                circles[ix] = circles[ix]?.rotate(sm.center, angle1.toFloat())
                            }
                            submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
                            expressions.update(selection.map { Indexed.Circle(it) })
                        }
                        else -> Unit
                    }
                }
                else -> Unit
            }
            if (mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect) {
                val qualifiedPart = (submode as SubMode.FlowSelect).lastQualifiedPart
                val (_, newQualifiedPart) = selectPartAt(centroid)
                if (qualifiedPart == null) {
                    submode = SubMode.FlowSelect(newQualifiedPart)
                } else {
                    val diff =
                        (qualifiedPart.insides - newQualifiedPart.insides) union
                        (newQualifiedPart.insides - qualifiedPart.insides) union
                        (qualifiedPart.outsides - newQualifiedPart.outsides) union
                        (newQualifiedPart.outsides - qualifiedPart.outsides)
                    selection.addAll(diff.filter { it !in selection })
                }
            } else if (mode == SelectionMode.Region && submode is SubMode.FlowFill) {
                val qualifiedPart = (submode as SubMode.FlowFill).lastQualifiedPart
                val (_, newQualifiedPart) = selectPartAt(centroid)
                if (qualifiedPart == null) {
                    submode = SubMode.FlowFill(newQualifiedPart)
                } else if (qualifiedPart != newQualifiedPart) {
                    submode = SubMode.FlowFill(newQualifiedPart)
                    if (restrictRegionsToSelection && selection.isNotEmpty()) {
                        val restriction = selection.toList()
                        reselectRegionAt(centroid, restriction)
                    } else {
                        reselectRegionAt(centroid)
                    }
                }
            }
        } else if (mode == SelectionMode.Drag && selection.isNotEmpty() && showCircles) {
            // move + scale radius
            recordCommand(Command.MOVE, targets = selection)
            val ix = selection.single()
            when (val circle = circles[ix]) {
                is Circle ->
                    circles[ix] = circle.translate(pan).scale(circle.center, zoom)
                is Line ->
                    circles[ix] = circle.translate(pan)
                null -> {}
            }
            expressions.update(listOf(Indexed.Circle(ix)))
        } else if (mode == SelectionMode.Drag && selectedPoints.isNotEmpty() && showCircles) {
            val ix = selectedPoints.first()
            recordCommand(Command.MOVE, targets = listOf(-ix-1)) // have to distinguish from circle indices ig
            val excludedSnapTargets = expressions.children
                .getOrElse(Indexed.Point(ix)) { emptySet() }
                .filterIsInstance<Indexed.Circle>()
                .map { it.index }
                .toSet()
            points[ix] = snapped(c, excludePoints = true, excludedCircles = excludedSnapTargets).result
            // MAYBE: dragging a point can add deps to it?
            expressions.update(listOf(Indexed.Point(ix)))
        } else if (mode == SelectionMode.Multiselect && selection.isNotEmpty() && showCircles) {
            if (selection.size == 1) { // move + scale radius
                recordCommand(Command.MOVE, targets = selection)
                val ix = selection.single()
                when (val circle = circles[ix]) {
                    is Circle ->
                        circles[ix] = circle.translate(pan).scale(circle.center, zoom)
                    is Line ->
                        circles[ix] = circle.translate(pan)
                    null -> {}
                }
            } else if (selection.size > 1) { // scale radius & position
                recordCommand(Command.MOVE, targets = selection)
                for (ix in selection) {
                    circles[ix] = circles[ix]
                        ?.rotate(c, rotationAngle)
                        ?.scale(c, zoom)
                        ?.translate(pan)
                }
            }
            expressions.update(selection.map { Indexed.Circle(it) })
        } else {
            val result = snapped(c)
            val absolutePoint = result.result
            if (mode == ToolMode.ARC_PATH) {
                // TODO: if last with n>=3, snap to start
                arcPathUnderConstruction = arcPathUnderConstruction?.moveFocused(absolutePoint)
            } else if (
                mode is ToolMode &&
                partialArgList?.currentArgType == ArgType.Point
            ) {
                val newArg = when (result) {
                    is PointSnapResult.Eq -> Arg.Point.Index(result.pointIndex)
                    else -> Arg.Point.XY(absolutePoint)
                }
                partialArgList = partialArgList!!
                    .updateCurrentArg(newArg, confirmThisArg = false)
                    .copy(lastSnap = result)
            } else if (
                mode is ToolMode &&
                partialArgList?.currentArgType == ArgType.CircleOrPoint
            ) {
                val newArg = when (result) {
                    is PointSnapResult.Eq -> Arg.CircleOrPoint.Point.Index(result.pointIndex)
                    else -> Arg.CircleOrPoint.Point.XY(absolutePoint)
                }
                partialArgList = partialArgList!!
                    .updateCurrentArg(newArg, confirmThisArg = false)
                    .copy(lastSnap = result)
            } else {
//                recordCommand(Command.CHANGE_POV)
                if (zoom != 1f) {
                    scaleSelection(zoom)
                }
                translation = translation + pan // navigate canvas
            }
        }
    }

    fun onUp(visiblePosition: Offset?) {
        cancelSelectionAsToolArgPrompt()
        when (mode) {
            ToolMode.ARC_PATH -> {}
            is ToolMode -> {
                // we only confirm args in onUp, they are created in onDown etc.
                val newArg = when (val arg = partialArgList?.currentArg) {
                    is Arg.Point -> visiblePosition?.let {
                        val realized = realizePointCircleSnap(snapped(absolute(visiblePosition)))
                        when (realized) {
                            is PointSnapResult.Free -> Arg.Point.XY(realized.result)
                            is PointSnapResult.Eq -> Arg.Point.Index(realized.pointIndex)
                        }
                    }
                    is Arg.CircleIndex -> null
                    is Arg.CircleAndPointIndices -> null
                    is Arg.CircleOrPoint -> visiblePosition?.let {
                        if (arg is Arg.CircleOrPoint.Point) {
                            val realized = realizePointCircleSnap(snapped(absolute(visiblePosition)))
                            when (realized) {
                                is PointSnapResult.Free -> Arg.CircleOrPoint.Point.XY(realized.result)
                                is PointSnapResult.Eq -> Arg.CircleOrPoint.Point.Index(realized.pointIndex)
                            }
                        } else null
                    }
                    null -> null // in case prev onDown failed to select anything
                }
                partialArgList = if (newArg == null)
                    partialArgList?.copy(lastArgIsConfirmed = true)
                else
                    partialArgList?.updateCurrentArg(newArg, confirmThisArg = true)
            }
            else -> {}
        }
        if (partialArgList?.isFull == true) {
            completeToolMode()
        }
        if (mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect)
            activeTool = EditClusterTool.Multiselect // haxx
        if (submode !is SubMode.FlowFill)
            submode = SubMode.None
    }

    fun onVerticalScroll(yDelta: Float) {
        val zoom = getPlatform().scrollToZoom(yDelta)
        scaleSelection(zoom)
    }

    fun onLongDragStart(position: Offset) {}
    fun onLongDrag(delta: Offset) {}
    fun onLongDragCancel() {}
    fun onLongDragEnd() {}

    private fun selectCategory(category: EditClusterCategory, togglePanel: Boolean = false) {
        val wasSelected = activeCategory == category
        val panelWasShown = showPanel
        val ix = categories.indexOf(category)
        activeCategoryIndex = ix
        showPanel = panelNeedsToBeShown
        if (togglePanel && wasSelected && panelNeedsToBeShown) {
            showPanel = !panelWasShown
        }
    }

    fun selectTool(tool: EditClusterTool, togglePanel: Boolean = false) {
        val category: EditClusterCategory
        if (tool is EditClusterTool.AppliedColor) {
            category = EditClusterCategory.Colors
        } else {
            val cIndex = categories.indexOfFirst { tool in it.tools }
            category = categories[cIndex]
            val i = category.tools.indexOf(tool)
            if (i in category.defaultables)
                categoryDefaults[cIndex] = i
        }
        activeTool = tool
        selectCategory(category, togglePanel = togglePanel)
        toolAction(tool)
    }

    fun switchToCategory(category: EditClusterCategory, togglePanel: Boolean = false) {
        val categoryIndex = categories.indexOf(category)
        val defaultToolIndex = categoryDefaults[categoryIndex]
        if (defaultToolIndex != -1) {
            val defaultTool = category.tools[defaultToolIndex]
            selectTool(defaultTool, togglePanel = togglePanel)
        } else {
            selectCategory(category, togglePanel = togglePanel)
        }
    }

    fun processKeyboardAction(action: KeyboardAction) {
//        println("processing $action")
        when (action) {
            KeyboardAction.SELECT_ALL -> {
                if (!mode.isSelectingCircles() || !showCircles) // more intuitive behavior
                    selection.clear() // forces to select all instead of toggling
                switchToCategory(EditClusterCategory.Multiselect)
                toggleSelectAll()
            }
            KeyboardAction.DELETE -> deleteCircles()
            KeyboardAction.PASTE -> duplicateCircles()
            KeyboardAction.ZOOM_IN -> scaleSelection(KEYBOARD_ZOOM_INCREMENT)
            KeyboardAction.ZOOM_OUT -> scaleSelection(1/KEYBOARD_ZOOM_INCREMENT)
            KeyboardAction.UNDO -> undo()
            KeyboardAction.REDO -> redo()
            KeyboardAction.CANCEL -> when (mode) { // reset mode
                is ToolMode -> {
                    partialArgList = partialArgList?.let { PartialArgList(it.signature) }
                    arcPathUnderConstruction = null
                }
                is SelectionMode -> {
                    selection.clear()
                    selectedPoints = emptyList()
                }
                else -> Unit
            }
        }
    }

    private fun completeToolMode() {
        val toolMode = mode
        val argList = partialArgList
        require(argList != null && argList.isFull && argList.isValid && argList.lastArgIsConfirmed) { "Invalid partialArgList $argList" }
        require(toolMode is ToolMode && toolMode.signature == argList.signature) { "Invalid signature: $toolMode's ${(toolMode as ToolMode).signature} != ${argList.signature}" }
        when (toolMode) {
            ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> completeCircleByCenterAndRadius()
            ToolMode.CIRCLE_BY_3_POINTS -> completeCircleBy3Points()
            ToolMode.LINE_BY_2_POINTS -> completeLineBy2Points()
            ToolMode.CIRCLE_INVERSION -> completeCircleInversion()
            ToolMode.CIRCLE_INTERPOLATION -> showCircleInterpolationDialog = true
            ToolMode.CIRCLE_EXTRAPOLATION -> showCircleExtrapolationDialog = true
            ToolMode.LOXODROMIC_MOTION -> showLoxodromicMotionDialog = true
            ToolMode.ARC_PATH -> throw IllegalStateException("Use separate function to route completion")
            ToolMode.POINT -> completePoint()
        }
    }

    private fun completeCircleByCenterAndRadius() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.Point }
        if (args.all { it is Arg.Point.XY }) {
            val newCircle = computeCircleByCenterAndRadius(
                center = (args[0] as Arg.Point.XY).toPoint().downscale(),
                radiusPoint = (args[1] as Arg.Point.XY).toPoint().downscale(),
            ).upscale()
            createNewCircle(newCircle)
            expressions.addFree(isPoint = false)
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.Point.Index -> Indexed.Point(it.index)
                    is Arg.Point.XY -> createNewFreePoint(it.toPoint())
                }
            }
            val newCircle = expressions.addSoloExpression(
                Expr.CircleByCenterAndRadius(
                    center = realized[0],
                    radiusPoint = realized[1]
                ),
                isPoint = false
            ) as? CircleOrLine
            createNewCircle(newCircle?.upscale())
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleBy3Points() {
        val argList = partialArgList!!
        val args = argList.args.map {
            it as Arg.CircleOrPoint
        }
        if (args.all { it is Arg.CircleOrPoint.Point.XY }) {
            val (p1, p2, p3) = args.map {
                (it as Arg.CircleOrPoint.Point.XY).toPoint().downscale()
            }
            val newCircle = computeCircleBy3Points(p1, p2, p3)
            expressions.addFree(isPoint = false)
            createNewCircle(newCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.CircleOrPoint.CircleIndex -> Indexed.Circle(it.index)
                    is Arg.CircleOrPoint.Point.Index -> Indexed.Point(it.index)
                    is Arg.CircleOrPoint.Point.XY -> createNewFreePoint(it.toPoint())
                }
            }
            val newCircle = expressions.addSoloExpression(
                Expr.CircleBy3Points(
                    point1 = realized[0],
                    point2 = realized[1],
                    point3 = realized[2],
                ),
                isPoint = false
            ) as? CircleOrLine
            createNewCircle(newCircle?.upscale())
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeLineBy2Points() {
        val argList = partialArgList!!
        val args = argList.args.map {
            it as Arg.CircleOrPoint
        }
        if (args.all { it is Arg.CircleOrPoint.Point.XY }) {
            val (p1, p2) = args.map {
                (it as Arg.CircleOrPoint.Point.XY).toPoint().downscale()
            }
            val newCircle = computeLineBy2Points(p1, p2)
            expressions.addFree(isPoint = false)
            createNewCircle(newCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.CircleOrPoint.CircleIndex -> Indexed.Circle(it.index)
                    is Arg.CircleOrPoint.Point.Index -> Indexed.Point(it.index)
                    is Arg.CircleOrPoint.Point.XY -> createNewFreePoint(it.toPoint())
                }
            }
            val newCircle = expressions.addSoloExpression(
                Expr.LineBy2Points(
                    point1 = realized[0],
                    point2 = realized[1],
                ),
                isPoint = false
            ) as? CircleOrLine
            createNewCircle(newCircle?.upscale())
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleInversion() {
        val argList = partialArgList!!
        val objArg = argList.args[0] as Arg.CircleAndPointIndices
        val targetCircleIxs = objArg.circleIndices
        val targetPointIxs = objArg.pointIndices
        val invertingCircleIndex = (argList.args[1] as Arg.CircleIndex).index
        val newCircles = targetCircleIxs.mapNotNull { circleIx ->
            val newCircle = expressions.addSoloExpression(
                Expr.CircleInversion(
                    Indexed.Circle(circleIx),
                    Indexed.Circle(invertingCircleIndex),
                ),
                isPoint = false
            ) as? CircleOrLine
            newCircle?.upscale()
//            when (val newCircle = result.toDirectedCircleOrLine()) {
//                is DirectedCircle -> newCircle.toCircle() to !newCircle.inside
//                is Line -> newCircle to false
//                else -> null
//            }
        }
        val newPoints = targetPointIxs.mapNotNull { pointIx ->
            val newPoint = expressions.addSoloExpression(
                Expr.CircleInversion(
                    Indexed.Point(pointIx),
                    Indexed.Circle(invertingCircleIndex),
                ),
                isPoint = true
            ) as? Point
            newPoint?.upscale()
        }
//        newCircles
//            .mapIndexed { i, (_, flip) -> if (flip) i+circles.size else null }
//            .filterNotNull()
//            .let { ixs ->
//                println("flipped: $ixs")
//            }
        // FIX: doesn't account for inside-out region spilling
        createNewCircles(newCircles)
        copyParts(
            targetCircleIxs,
            ((circles.size - newCircles.size) until circles.size).toList(),
//            newCircles.map { (_, flip) -> flip }
        )
        points.addAll(newPoints)
        partialArgList = PartialArgList(argList.signature)
    }

    fun completeCircleInterpolation(params: InterpolationParameters) {
        showCircleInterpolationDialog = false
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.CircleIndex }
        val startCircleIx = args[0].index
        val endCircleIx = args[1].index
        val newCircles = expressions.addMultiExpression(
            Expr.CircleInterpolation(
                params,
                Indexed.Circle(startCircleIx),
                Indexed.Circle(endCircleIx),
            ),
            isPoint = false
        ).map { if (it is CircleOrLine?) it?.upscale() else null }
        createNewCircles(newCircles)
        partialArgList = PartialArgList(argList.signature)
        defaultInterpolationParameters = DefaultInterpolationParameters(params)
    }

    fun resetCircleInterpolation() {
        showCircleInterpolationDialog = false
        partialArgList = PartialArgList(EditClusterTool.CircleInterpolation.signature)
    }

    fun completeCircleExtrapolation(
        params: ExtrapolationParameters,
    ) {
        showCircleExtrapolationDialog = false
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.CircleIndex }
        val startCircleIx = args[0].index
        val endCircleIx = args[1].index
        val newCircles = expressions.addMultiExpression(
            Expr.CircleExtrapolation(
                params,
                Indexed.Circle(startCircleIx),
                Indexed.Circle(endCircleIx),
            ),
            isPoint = false
        ).map { if (it is CircleOrLine?) it?.upscale() else null }
        createNewCircles(newCircles)
        partialArgList = PartialArgList(argList.signature)
        defaultExtrapolationParameters = DefaultExtrapolationParameters(params)
    }

    fun resetCircleExtrapolation() {
        showCircleExtrapolationDialog = false
        partialArgList = PartialArgList(EditClusterTool.CircleExtrapolation.signature)
    }

    // TODO: inf point input
    fun completeLoxodromicMotion(
        params: LoxodromicMotionParameters,
    ) {
        showLoxodromicMotionDialog = false
        val argList = partialArgList!!
        val args = argList.args
        val objArg = args[0] as Arg.CircleAndPointIndices
        val targetCircleIndices = objArg.circleIndices
        val targetPointsIndices = objArg.pointIndices
        val (divergence, convergence) = args.drop(1).take(2)
            .map { when (val arg = it as Arg.Point) {
                is Arg.Point.Index -> Indexed.Point(arg.index)
                is Arg.Point.XY -> createNewFreePoint(arg.toPoint())
            } }
        val allNewCircles = mutableListOf<CircleOrLine?>()
        val allNewPoints = mutableListOf<Point?>()
        for (circleIndex in targetCircleIndices) {
            val newCircles = expressions.addMultiExpression(
                Expr.LoxodromicMotion(
                    params,
                    divergence, convergence,
                    Indexed.Circle(circleIndex)
                ),
                isPoint = false
            ).map { if (it is CircleOrLine?) it?.upscale() else null }
            allNewCircles.addAll(newCircles)
        }
        for (pointIndex in targetPointsIndices) {
            val newPoints = expressions.addMultiExpression(
                Expr.LoxodromicMotion(
                    params,
                    divergence, convergence,
                    Indexed.Point(pointIndex)
                ),
                isPoint = true
            ).map { if (it is Point?) it?.upscale() else null }
            allNewPoints.addAll(newPoints)
        }
        val size0 = circles.size
        createNewCircles(allNewCircles)
        points.addAll(allNewPoints)
        val k = targetCircleIndices.size
        repeat(params.nSteps + 1) { i ->
            val m = size0 + i * k // first index of this batch
            // FIX: doesn't account for inside-out region spilling
            copyParts(targetCircleIndices, (m until m+k).toList())
        }
        partialArgList = PartialArgList(argList.signature)
        defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(params)
    }

    fun resetLoxodromicMotion() {
        showLoxodromicMotionDialog = false
        partialArgList = PartialArgList(EditClusterTool.LoxodromicMotion.signature)
    }

    fun completeArcPath() {
        require(arcPathUnderConstruction != null)
        // only add circles
        // since `part`itioning in-arcpath region is rather involved
        arcPathUnderConstruction?.let { arcPath ->
            val newCircles: List<CircleOrLine> = arcPath.circles
                .mapIndexed { j, circle ->
                    when (circle) {
                        is Circle -> circle
                        null -> Line.by2Points(
                            arcPath.previousPoint(j),
                            arcPath.points[j]
                        )
                        else -> throw IllegalStateException("Never")
                    }
                }
            createNewCircles(newCircles)
        }
        arcPathUnderConstruction = null
    }

    private fun completePoint() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.Point }
        val arg0 = args[0]
        if (arg0 is Arg.Point.XY) {
            val newPoint = arg0.toPoint()
            createNewFreePoint(newPoint)
        } // it could have already done it with realized PSR.Eq, which results in Arg.Point.Index
        partialArgList = PartialArgList(argList.signature)
    }

    fun toolAction(tool: EditClusterTool) {
//        println("toolAction($tool)")
        when (tool) {
            EditClusterTool.Drag -> switchToMode(SelectionMode.Drag)
            EditClusterTool.Multiselect -> switchToMode(SelectionMode.Multiselect)
            EditClusterTool.FlowSelect -> activateFlowSelect()
            EditClusterTool.ToggleSelectAll -> toggleSelectAll()
            EditClusterTool.Region -> switchToMode(SelectionMode.Region)
            EditClusterTool.FlowFill -> activateFlowFill()
            EditClusterTool.FillChessboardPattern -> applyChessboardPatter()
            EditClusterTool.RestrictRegionToSelection -> toggleRestrictRegionsToSelection()
            EditClusterTool.DeleteAllParts -> deleteAllParts()
            EditClusterTool.ShowCircles -> toggleShowCircles() // MAYBE: apply to selected circles only
            EditClusterTool.ToggleFilledOrOutline -> showWireframes = !showWireframes
            EditClusterTool.HideUI -> hideUIFor30s()
            EditClusterTool.Palette -> showColorPickerDialog = true
            EditClusterTool.Expand -> scaleSelection(HUD_ZOOM_INCREMENT)
            EditClusterTool.Shrink -> scaleSelection(1/HUD_ZOOM_INCREMENT)
            EditClusterTool.Duplicate -> duplicateCircles()
            EditClusterTool.Delete -> deleteCircles()
            EditClusterTool.InsertCenteredCross -> insertCenteredCross()
            is EditClusterTool.AppliedColor -> selectRegionColor(tool.color)
            is EditClusterTool.MultiArg -> switchToMode(ToolMode.correspondingTo(tool))
            EditClusterTool.Undo -> undo()
            EditClusterTool.Redo -> redo()
            is EditClusterTool.CustomAction -> {} // custom handlers
            EditClusterTool.CompleteArcPath -> completeArcPath()
            EditClusterTool.AddBackgroundImage -> TODO("issues..")
        }
    }

    /** Is [tool] enabled? */
    fun toolPredicate(tool: EditClusterTool): Boolean =
        when (tool) { // NOTE: i think this has to return State<Boolean> to work properly
            EditClusterTool.Drag -> mode == SelectionMode.Drag
            EditClusterTool.Multiselect -> mode == SelectionMode.Multiselect && submode !is SubMode.FlowSelect
            EditClusterTool.FlowSelect -> mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect
            EditClusterTool.ToggleSelectAll -> selection.containsAll(circles.indices.toSet())
            EditClusterTool.Region -> mode == SelectionMode.Region && submode !is SubMode.FlowFill
            EditClusterTool.FlowFill -> mode == SelectionMode.Region && submode is SubMode.FlowFill
            EditClusterTool.FillChessboardPattern -> chessboardPatternStartsColored
            EditClusterTool.RestrictRegionToSelection -> restrictRegionsToSelection
            EditClusterTool.ShowCircles -> showCircles
            EditClusterTool.ToggleFilledOrOutline -> !showWireframes
            EditClusterTool.Palette -> showColorPickerDialog
            is EditClusterTool.MultiArg -> mode == ToolMode.correspondingTo(tool)
            else -> true
        }

    /** Be careful to pass *only* strictly immutable args by __copying__ */
    @Serializable
    @Immutable
    data class UiState(
        val circles: List<CircleOrLine?>,
        val points: List<Point?>,
        val parts: List<Cluster.Part>,
        val expressions: Map<Indexed, Expression?>,
        val selection: List<Ix>, // circle indices
        @Serializable(OffsetSerializer::class)
        val translation: Offset,
    ) {
        companion object {
            val SAMPLE = UiState(
                circles = listOf(
                    Circle(200.0, 200.0, 100.0),
                    Circle(250.0, 200.0, 100.0),
                    Circle(200.0, 250.0, 100.0),
                    Circle(250.0, 250.0, 100.0),
                ),
                points = emptyList(),
                parts = listOf(Cluster.Part(setOf(0), setOf(1,2,3))),
                expressions = (0..3).associate { Indexed.Circle(it) to null },
                selection = listOf(0),
                // NOTE: hardcoded default is bad, much better would be to specify the center but oh well
                translation = Offset(225f, 225f) + Offset(400f, 0f)
            )

            fun restore(coroutineScope: CoroutineScope, uiState: UiState): EditClusterViewModel =
                EditClusterViewModel(
                    coroutineScope,
                    uiState.circles,
                    uiState.parts,
                    uiState.expressions,
                ).apply {
                    if (uiState.selection.size > 1)
                        mode = SelectionMode.Multiselect
                    selection.addAll(uiState.selection)
                    translation = uiState.translation
                    points.addAll(uiState.points)
                }

            fun save(viewModel: EditClusterViewModel): UiState =
                with (viewModel) {
                    UiState(
                        circles.toList(), points.toList(), parts.toList(),
                        expressions.expressions.toMap(),
                        selection.toList(),
                        viewModel.translation
                    )
                }
        }
    }

    class Saver(
        private val coroutineScope: CoroutineScope
    ) : androidx.compose.runtime.saveable.Saver<EditClusterViewModel, String> {
        override fun SaverScope.save(value: EditClusterViewModel): String =
            JSON.encodeToString(UiState.serializer(), UiState.save(value))
        override fun restore(value: String): EditClusterViewModel {
            return UiState.restore(
                coroutineScope,
                JSON.decodeFromString(UiState.serializer(), value)
            )
        }
        companion object {
            val JSON = Json {
                allowStructuredMapKeys = true
            }
        }
    }

    companion object {
        const val LOW_ACCURACY_FACTOR = 1.5f
        const val HUD_ZOOM_INCREMENT = 1.1f // == +10%
        const val KEYBOARD_ZOOM_INCREMENT = 1.05f // == +5%
        const val MAX_SLIDER_ZOOM = 3.0f // == +200%
        const val FAST_CENTERED_CIRCLE = true
        const val ENABLE_ANGLE_SNAPPING = true
        /** [Double] arithmetic is best in range that is closer to 0 */
        const val UPSCALING_FACTOR = 200.0
        const val DOWNSCALING_FACTOR = 1/UPSCALING_FACTOR

        fun sliderPercentageDeltaToZoom(percentageDelta: Float): Float =
            MAX_SLIDER_ZOOM.pow(2*percentageDelta)

        fun GCircle.downscale(): GCircle = scale(0.0, 0.0, DOWNSCALING_FACTOR)
        fun GCircle.upscale(): GCircle = scale(0.0, 0.0, UPSCALING_FACTOR)
        fun CircleOrLine.downscale(): CircleOrLine = scale(0.0, 0.0, DOWNSCALING_FACTOR)
        fun CircleOrLine.upscale(): CircleOrLine = scale(0.0, 0.0, UPSCALING_FACTOR)
        fun Point.downscale(): Point = scale(0.0, 0.0, DOWNSCALING_FACTOR)
        fun Point.upscale(): Point = scale(0.0, 0.0, UPSCALING_FACTOR)
    }
}

/** ixs = indices of circles to which the handle is attached */
@Immutable
sealed class HandleConfig(open val ixs: List<Ix>) {
    data class SingleCircle(val ix: Ix): HandleConfig(listOf(ix))
    data class SeveralCircles(override val ixs: List<Ix>): HandleConfig(ixs)
}

/** params for create/copy/delete animations */
@Immutable
sealed interface CircleAnimation {
    val circles: List<CircleOrLine>
    data class Entrance(override val circles: List<CircleOrLine>) : CircleAnimation
    data class ReEntrance(override val circles: List<CircleOrLine>) : CircleAnimation
    data class Exit(override val circles: List<CircleOrLine>) : CircleAnimation
}
