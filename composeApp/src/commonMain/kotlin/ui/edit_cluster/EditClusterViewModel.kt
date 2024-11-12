package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
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
import domain.Indices
import domain.Ix
import domain.PartialArgList
import domain.PointSnapResult
import domain.angleDeg
import domain.cluster.Cluster
import domain.cluster.ClusterPart
import domain.cluster.ClusterV1
import domain.cluster.Constellation
import domain.compressPart
import domain.expressions.CircleConstruct
import domain.expressions.Expr
import domain.expressions.Expression
import domain.expressions.ExpressionForest
import domain.expressions.ExtrapolationParameters
import domain.expressions.IncidenceParameters
import domain.expressions.Indexed
import domain.expressions.InterpolationParameters
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.PointConstruct
import domain.expressions.computeCircleBy3Points
import domain.expressions.computeCircleByCenterAndRadius
import domain.expressions.computeCircleByPencilAndPoint
import domain.expressions.computeLineBy2Points
import domain.expressions.reIndexExpression
import domain.io.DdcV1
import domain.io.DdcV2
import domain.io.DdcV3
import domain.io.cluster2svg
import domain.io.cluster2svgCheckPattern
import domain.io.parseDdcV1
import domain.io.parseDdcV2
import domain.io.parseDdcV3
import domain.reindexingMap
import domain.snapAngle
import domain.snapPointToCircles
import domain.snapPointToPoints
import domain.sortedByFrequency
import domain.tryCatch2
import getPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ui.theme.DodeclustersColors
import ui.tools.EditClusterCategory
import ui.tools.EditClusterTool
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

// MAYBE: use UiState functional pattern instead of this mess
// this class is obviously too big
// TODO: decouple navigation & tools/categories
class EditClusterViewModel : ViewModel() {
    val points: SnapshotStateList<Point?> = mutableStateListOf()
    val circles: SnapshotStateList<CircleOrLine?> = mutableStateListOf()
    val parts: SnapshotStateList<ClusterPart> = mutableStateListOf()
    private var expressions: ExpressionForest = ExpressionForest( // stub
        initialExpressions = emptyMap(),
        get = { null },
        set = { _, _ -> }
    )

    var mode: Mode by mutableStateOf(SelectionMode.Drag)
        private set
    // XYPoint uses absolute positioning
    var partialArgList: PartialArgList? by mutableStateOf(null)
        private set

    // MAYBE: when circles are hidden select parts instead
    /** indices of selected [circles] */
    var circleSelection: List<Ix> by mutableStateOf(emptyList())
        private set
    /** indices of selected [points] */
    var pointSelection: List<Ix> by mutableStateOf(emptyList())
        private set

    /** encapsulates all category- and tool-related info */
    var toolbarState: ToolbarState by mutableStateOf(ToolbarState())
        private set
    var showPanel: Boolean by mutableStateOf(toolbarState.panelNeedsToBeShown)
        private set
    var showPromptToSetActiveSelectionAsToolArg: Boolean by mutableStateOf(false) // to be updated manually
        private set
    var showUI: Boolean by mutableStateOf(true)
        private set

    /** currently selected color */
    var regionColor: Color by mutableStateOf(DodeclustersColors.primaryDark) // DodeclustersColors.purple)
        private set
    /** custom colors for circle borders */
    val circleColors: SnapshotStateMap<Ix, Color> = mutableStateMapOf()
    var showCircles: Boolean by mutableStateOf(true)
        private set
    /** which style to use when drawing parts: true = stroke, false = fill */
    var showWireframes: Boolean by mutableStateOf(false)
        private set
    /** applies to [SelectionMode.Region]:
     * only use circles present in the [circleSelection] to determine which parts to fill */
    var restrictRegionsToSelection: Boolean by mutableStateOf(false)
        private set
    var displayChessboardPattern: Boolean by mutableStateOf(false)
        private set
    /** true = background starts colored */
    var chessboardPatternStartsColored: Boolean by mutableStateOf(true)
        private set

    val circleSelectionIsActive: Boolean by derivedStateOf {
        showCircles && circleSelection.isNotEmpty() && mode.isSelectingCircles()
    }
    val pointSelectionIsActive: Boolean by derivedStateOf {
        showCircles && pointSelection.isNotEmpty() && mode.isSelectingCircles()
    }
    val handleConfig: HandleConfig? by derivedStateOf { // depends on selectionMode & selection
        when (mode) {
            SelectionMode.Drag ->
                if (circleSelection.isEmpty()) null
                else HandleConfig.SingleCircle(circleSelection.single())
            SelectionMode.Multiselect -> when {
                circleSelection.isEmpty() -> null
                circleSelection.size == 1 -> HandleConfig.SingleCircle(circleSelection.single())
                circleSelection.size > 1 -> HandleConfig.SeveralCircles(circleSelection)
                else -> null // never
            }
            SelectionMode.Region -> null
            else -> null
        }
    }
    var submode: SubMode by mutableStateOf(SubMode.None)
        private set

    // NOTE: history doesn't survive background app kill
    private val history: History<State> = History(
        saveState = { saveState() },
        loadState = { state -> loadState(state) }
    )
    var undoIsEnabled: Boolean by mutableStateOf(false) // = history is not empty
        private set
    var redoIsEnabled: Boolean by mutableStateOf(false) // = redoHistory is not empty
        private set

    var defaultInterpolationParameters = DefaultInterpolationParameters()
        private set
    var defaultExtrapolationParameters = DefaultExtrapolationParameters()
        private set
    var defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters()
        private set

    private val _circleAnimations: MutableSharedFlow<CircleAnimation> = MutableSharedFlow()
    val circleAnimations: SharedFlow<CircleAnimation> = _circleAnimations.asSharedFlow()

    var openedDialog: DialogType? by mutableStateOf(null)
        private set

    var arcPathUnderConstruction: ArcPath? by mutableStateOf(null)
        private set

    var canvasSize: IntSize by mutableStateOf(IntSize.Zero) // used when saving best-center
        private set
    private val selectionControlsPositions: SelectionControlsPositions by derivedStateOf {
        SelectionControlsPositions(canvasSize)
    }
    var translation: Offset by mutableStateOf(Offset.Zero)
        private set

    /** min tap/grab distance to select an object */
    private var tapRadius = getPlatform().tapRadius

    init {
        launchRestore()
    }

    fun setEpsilon(density: Density) {
        with (density) {
            tapRadius = getPlatform().tapRadius.dp.toPx()
        }
    }

    fun changeCanvasSize(newCanvasSize: IntSize) {
        val prevCenter = Offset(canvasSize.width/2f, canvasSize.height/2f)
        val newCenter = Offset(newCanvasSize.width/2f, newCanvasSize.height/2f)
        translation += (newCenter - prevCenter)
        canvasSize = newCanvasSize
    }

    fun saveAsYaml(name: String = DdcV2.DEFAULT_NAME): String {
        // Q: there seemingly was an issue with saving circleColors on Android
        return Yaml(
            configuration = YamlConfiguration(
                encodeDefaults = false,
                strictMode = false,
                polymorphismStyle = PolymorphismStyle.Property,
            )
        ).encodeToString(DdcV3.from(toConstellation()).copy(
            name = name,
            bestCenterX = computeAbsoluteCenter()?.x,
            bestCenterY = computeAbsoluteCenter()?.y,
            chessboardPattern = displayChessboardPattern,
            chessboardPatternStartsColored = chessboardPatternStartsColored,
        ))
    }

    fun exportAsSvg(name: String = DdcV2.DEFAULT_NAME): String {
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

    // TODO: make it suspend
    fun loadFromYaml(yaml: String) {
        tryCatch2<SerializationException, IllegalArgumentException>(
            {
                val ddc = parseDdcV3(yaml)
                val constellation = ddc.toConstellation()
                loadNewConstellation(constellation)
                centerizeTo(ddc.bestCenterX, ddc.bestCenterY)
                displayChessboardPattern = ddc.chessboardPattern
                chessboardPatternStartsColored = ddc.chessboardPatternStartsColored
            },
            { e ->
                e.printStackTrace()
                println("Failed to parse DdcV3->yaml, falling back to DdcV2->yaml")
                loadDdcV2FromYaml(yaml)
            }
        )
    }

    private fun loadDdcV2FromYaml(yaml: String) {
        tryCatch2<SerializationException, IllegalArgumentException>(
            {
                val ddc = parseDdcV2(yaml)
                val cluster = ddc.content
                    .filterIsInstance<DdcV2.Token.Cluster>()
                    .first()
                    .toCluster()
                loadNewConstellation(cluster.toConstellation())
                centerizeTo(ddc.bestCenterX, ddc.bestCenterY)
                displayChessboardPattern = ddc.chessboardPattern
                chessboardPatternStartsColored = ddc.chessboardPatternStartsColored
            },
            { e1 ->
                e1.printStackTrace()
                println("Failed to parse DdcV2->yaml, falling back to DdcV1->yaml")
                loadDdcV1FromYaml(yaml)
            }
        )
    }

    private fun loadDdcV1FromYaml(yaml: String) {
        tryCatch2<SerializationException, IllegalArgumentException>(
            {
                val ddc = parseDdcV1(yaml)
                val cluster = ddc.content
                    .filterIsInstance<DdcV1.Token.Cluster>()
                    .first()
                    .toCluster()
                loadNewConstellation(cluster.toConstellation())
                centerizeTo(ddc.bestCenterX, ddc.bestCenterY)
            },
            { e2 ->
                e2.printStackTrace()
                println("Failed to parse DdcV1->yaml, falling back to ClusterV1->json")
                loadClusterV1FromJson(yaml) // NOTE: for backwards compat
            }
        )
    }

    private fun loadClusterV1FromJson(json: String) {
        val permissiveJson = Json {
            isLenient = true
            ignoreUnknownKeys = true // enables backward compatibility to a certain level
        }
        tryCatch2<SerializationException, IllegalArgumentException>(
            {
                val cluster1: ClusterV1 = permissiveJson.decodeFromString(ClusterV1.serializer(), json)
                loadNewConstellation(cluster1.toCluster().toConstellation())
            },
            { e ->
                e.printStackTrace()
                println("Failed to parse ClusterV1->json")
            }
        )
    }

    fun centerizeTo(centerX: Float?, centerY: Float?) {
        translation = -Offset(
            centerX?.let { it - canvasSize.width/2f } ?: 0f,
            centerY?.let { it - canvasSize.height/2f } ?: 0f
        )
    }

    // MAYBE: make a suspend function + add load spinner
    fun loadNewConstellation(constellation: Constellation) {
        showPromptToSetActiveSelectionAsToolArg = false
        displayChessboardPattern = false
        chessboardPatternStartsColored = true
        translation = Offset.Zero
        loadConstellation(constellation)
        // reset history on load
        history.clear()
        resetTransients()
        println("loaded new constellation")
    }

    private fun loadConstellation(constellation: Constellation) {
        circleSelection = emptyList()
        pointSelection = emptyList()
        parts.clear()
        circleColors.clear()
        points.clear()
        circles.clear()
        points.addAll(
            constellation.points.map {
                when (it) {
                    is PointConstruct.Concrete -> it.point
                    else -> null
                }
            }
        )
        circles.addAll(
            constellation.circles.map {
                when (it) {
                    is CircleConstruct.Concrete -> it.circleOrLine
                    else -> null
                }
            }
        )
        expressions = ExpressionForest(
            initialExpressions = constellation.toExpressionMap(),
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
        expressions.reEval() // calculates all dependent objects
        // FIX: ix2tier calc is off, expr.args are all empty!
        parts.addAll(constellation.parts)
        circleColors.putAll(constellation.circleColors)
    }

    fun toConstellation(): Constellation {
        val deletedPoints = points.indices.filter { i ->
            points[i] == null && expressions.expressions[Indexed.Point(i)] == null
        }.toSet()
        val deletedCircles = circles.indices.filter { i ->
            circles[i] == null && expressions.expressions[Indexed.Circle(i)] == null
        }.toSet()
        val pointReindexing = reindexingMap(
            originalIndices = points.indices,
            deletedIndices = deletedPoints
        )
        val circleReindexing = reindexingMap(
            originalIndices = circles.indices,
            deletedIndices = deletedCircles
        )
        return Constellation(
            points = points.indices.mapNotNull { i ->
                val ix = Indexed.Point(i)
                val e = expressions.expressions[ix]
                if (e == null) {
                    val point = points[i]
                    if (point == null) {
                        null
                    } else {
                        PointConstruct.Concrete(point)
                    }
                } else {
                    PointConstruct.Dynamic(reIndexExpression(
                        expression = e,
                        pointReIndexer = { pointReindexing[it]!! },
                        circleReIndexer = { circleReindexing[it]!! }
                    ))
                }
            },
            circles = circles.indices.mapNotNull { i ->
                val ix = Indexed.Circle(i)
                val e = expressions.expressions[ix]
                if (e == null) {
                    val circle = circles[i]
                    if (circle == null) {
                        null
                    } else {
                        CircleConstruct.Concrete(circle)
                    }
                } else {
                    CircleConstruct.Dynamic(reIndexExpression(
                        expression = e,
                        pointReIndexer = { pointReindexing[it]!! },
                        circleReIndexer = { circleReindexing[it]!! }
                    ))
                }
            },
            parts = parts.map { part ->
                part.copy(
                    insides = part.insides.map { circleReindexing[it]!! }.toSet(),
                    outsides = part.outsides.map { circleReindexing[it]!! }.toSet(),
                )
            },
            circleColors = circleColors.toMap()
        )
    }

    fun undo() {
        val m = mode
        if (m is ToolMode && partialArgList?.args?.isNotEmpty() == true) {
            partialArgList = PartialArgList(m.signature)
        } else {
            val currentSelection = circleSelection.toList()
            switchToMode(mode) // clears up stuff
            circleSelection = emptyList()
            pointSelection = emptyList()
            history.undo()
            circleSelection = currentSelection.filter { it < circles.size }
            resetTransients()
        }
    }

    fun redo() {
        val currentSelection = circleSelection.toList()
        switchToMode(mode)
        history.redo()
        pointSelection = emptyList()
        circleSelection = currentSelection.filter { it < circles.size }
        resetTransients()
    }

    private fun loadState(state: State) {
        submode = SubMode.None
        loadConstellation(state.constellation)
        circleSelection = state.circleSelection
        centerizeTo(state.centerX, state.centerY)
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

    /** Use BEFORE modifying the state by the [Command.CREATE]! */
    private fun recordCreateCommand() {
        recordCommand(Command.CREATE, unique = true)
    }

    fun createNewCircle(
        newCircle: CircleOrLine?,
    ) =
        createNewCircles(listOf(newCircle))

    /** Append [newCircles] to [circles] and queue circle entrance animation
     * */
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
            showCircles = true
            val prevSize = circles.size
            circles.addAll(normalizedCircles)
            circleSelection = (prevSize until circles.size).filter { circles[it] != null }
            viewModelScope.launch {
                _circleAnimations.emit(
                    CircleAnimation.Entrance(validNewCircles)
                )
            }
        } else {
            circles.addAll(normalizedCircles)
            circleSelection = emptyList()
        }
    }

    fun createNewFreePoint(
        point: Point,
        triggerRecording: Boolean = true
    ): Indexed.Point {
        if (triggerRecording)
            recordCreateCommand()
        points.add(point)
        expressions.addFreePoint()
        return Indexed.Point(points.size - 1)
    }

    fun duplicateCircles() {
        if (mode.isSelectingCircles()) {
            recordCommand(Command.DUPLICATE, circleSelection)
            val copiedCircles = circleSelection.mapNotNull { circles[it] } // preserves selection order
            val oldSize = circles.size
            circles.addAll(copiedCircles)
            val newIndices = oldSize until oldSize + copiedCircles.size
            for (ix in newIndices)
                expressions.addFreeCircle()
            copyParts(circleSelection, newIndices.toList())
            circleSelection = newIndices.toList()
            viewModelScope.launch {
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
        flipInAndOut: Boolean = false,
    ) {
        require(oldIndices.size == newIndices.size)
        val old2new = oldIndices.zip(newIndices).toMap()
        val newParts = parts.filter {
            oldIndices.containsAll(it.insides) && oldIndices.containsAll(it.outsides)
        }.map { part ->
            val newInsides: Set<Ix>
            val newOutsides: Set<Ix>
            if (flipInAndOut) {
                newInsides = part.outsides.map { old2new[it]!! }.toSet()
                newOutsides = part.insides.map { old2new[it]!! }.toSet()
            } else {
                newInsides = part.insides.map { old2new[it]!! }.toSet()
                newOutsides = part.outsides.map { old2new[it]!! }.toSet()
            }
            ClusterPart(
                insides = newInsides,
                outsides = newOutsides,
                fillColor = part.fillColor
            )
        }
        parts.addAll(newParts)
    }

    fun deleteCircles() {
        val thereAreSelectedCirclesToDelete = circleSelectionIsActive
        if (pointSelection.isNotEmpty() || thereAreSelectedCirclesToDelete)
            recordCommand(Command.DELETE, unique = true)
        val toBeDeleted = expressions.deleteNodes(
            pointSelection.map { Indexed.Point(it) } +
                if (thereAreSelectedCirclesToDelete) circleSelection.map { Indexed.Circle(it) }
                else emptyList()
        )
        val pointsToBeDelete = toBeDeleted.filterIsInstance<Indexed.Point>()
        val circlesToBeDelete = toBeDeleted.filterIsInstance<Indexed.Circle>()
        for (ix in pointsToBeDelete)
            points[ix.index] = null
        pointSelection = emptyList()
        if (circlesToBeDelete.isNotEmpty()) {
            circlesToBeDelete.forEach {
                circleColors.remove(it.index)
            }
            val whatsGone = circlesToBeDelete.map { it.index }
                .toSet()
            // FIX: there's been an incident of select-all > delete > out-of-bounds 6/6 here
            val deletedCircles = whatsGone.mapNotNull { circles[it] }
            val oldParts = parts.toList()
            circleSelection = emptyList()
            parts.clear()
            for (ix in whatsGone)
                circles[ix] = null
            parts.addAll(
                oldParts
                    // to avoid stray chessboard selections
                    .filterNot { (ins, _, _) -> ins.isNotEmpty() && ins.minus(whatsGone).isEmpty() }
                    .map { (ins, outs, fillColor) ->
                        ClusterPart(
                            insides = ins.minus(whatsGone),
                            outsides = outs.minus(whatsGone),
                            fillColor = fillColor
                        )
                    }
                    .filter { (ins, outs) -> ins.isNotEmpty() || outs.isNotEmpty() }
            )
            viewModelScope.launch {
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
        if (circleSelection.size > 1 && newMode == SelectionMode.Drag)
            circleSelection = emptyList()
        pointSelection = emptyList()
        showPromptToSetActiveSelectionAsToolArg = false
        if (newMode is ToolMode) {
            if (circleSelection.size > 1 &&
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
            pointSelection = listOf()
            return false
        } else {
            pointSelection = listOf(nearPointIndex)
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
            ?.also { println("select circle #$it: ${circles[it]} <- expr: ${expressions.expressions[Indexed.Circle(it)]}") }
    }

    fun reselectCircleAt(visiblePosition: Offset): Boolean {
        val nearCircleIndex = selectCircle(circles, visiblePosition,
            priorityTargets = circles.indices
                .filter { expressions.expressions[Indexed.Circle(it)] == null }
                .toSet()
        )
        circleSelection = emptyList()
        if (nearCircleIndex == null) {
            return false
        } else {
            circleSelection += nearCircleIndex
            return true
        }
    }

    fun reselectCirclesAt(visiblePosition: Offset): Ix? =
        selectCircle(circles, visiblePosition)?.also { ix ->
            if (ix in circleSelection)
                circleSelection -= ix
            else
                circleSelection += ix
        }

    // NOTE: part boundaries get messed up when we alter a big structure like spiral
    /** -> (compressed part, verbose part involving all circles) surrounding clicked position */
    private fun selectPartAt(
        visiblePosition: Offset,
        boundingCircles: List<Ix>? = null
    ): Pair<ClusterPart, ClusterPart> {
        val position = absolute(visiblePosition)
        val delimiters = boundingCircles ?: circles.indices
        val ins = delimiters // NOTE: doesn't include circles that the point lies on
            .filter { ix -> circles[ix]?.hasInside(position) ?: false }
        val outs = delimiters
            .filter { ix -> circles[ix]?.hasOutside(position) ?: false }
        val (essentialIns, essentialOuts) = compressPart(circles, ins, outs)
        val part0 = ClusterPart(ins.toSet(), outs.toSet(), regionColor)
        val part = ClusterPart(
//            insides = ins.toSet(),
//            outsides = outs.toSet(),
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
                circleSelection = (part.insides + part.outsides).toList()
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
                        circleSelection = (part.insides + part.outsides).toList()
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
        val selectedCircles = circleSelection
            .map { circles[it] }
            .filterIsInstance<Circle>()
        if (selectedCircles.isEmpty() || selectedCircles.size < circleSelection.size)
            return null
        val left = selectedCircles.minOf { (it.x - it.radius).toFloat() }
        val right = selectedCircles.maxOf { (it.x + it.radius).toFloat() }
        val top = selectedCircles.minOf { (it.y - it.radius) }.toFloat()
        val bottom = selectedCircles.maxOf { (it.y + it.radius) }.toFloat()
        return Rect(left, top, right, bottom)
    }

    fun isFreeCircle(circleIndex: Ix): Boolean =
        expressions.expressions[Indexed.Circle(circleIndex)] == null
    fun isFreePoint(pointIndex: Ix): Boolean =
        expressions.expressions[Indexed.Point(pointIndex)] == null

    // MAYBE: wrap into state that depends only on [parts] for caching
    fun getColorsByMostUsed(): List<Color> =
        parts
            .flatMap { part ->
                part.borderColor?.let { listOf(part.fillColor, it) }
                    ?: listOf(part.fillColor)
            }
            .sortedByFrequency()

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

    /** Adds a new point[[s]] with expression defined by [snapResult] when non-free
     * @return the same [snapResult] if [snapResult] is [PointSnapResult.Free], otherwise
     * [PointSnapResult.Eq] that points to the newly added point */
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
                val newPoint = expressions.addSoloPointExpression(expr)
                points.add(newPoint?.upscale())
                PointSnapResult.Eq(points.last()!!, points.size - 1)
            }
            is PointSnapResult.Intersection -> {
                val point = snapResult.result
                val (ix1, ix2) = listOf(snapResult.circle1Index, snapResult.circle2index).sorted()
                val expr = Expr.Intersection(
                    Indexed.Circle(ix1),
                    Indexed.Circle(ix2)
                )
                // TODO: lookup if it already exists
                val sameExpr = expressions.expressions.filter { (_, e) -> e?.expr == expr }
                val output1Ix = sameExpr.entries
                    .firstOrNull { (_, e) -> e is Expression.OneOf && e.outputIndex == 0 }
                    ?.key as? Indexed.Point
                val output2Ix = sameExpr.entries
                    .firstOrNull { (_, e) -> e is Expression.OneOf && e.outputIndex == 1 }
                    ?.key as? Indexed.Point
                if (output1Ix != null && output2Ix != null) {
                    // good
                    // find closest and return
                } else if (output1Ix == null && output2Ix != null) {
                    // add 1st
                } else if (output1Ix != null && output2Ix == null) {
                    // add 2st
                } else { // both are absent
                    // add both
                }
                // check if both outputIndices are present
                // if not, add the other
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
        circleSelection = emptyList()
        submode = SubMode.FlowSelect()
    }

    fun activateFlowFill() {
        switchToMode(SelectionMode.Region)
        submode = SubMode.FlowFill()
    }

    fun toggleSelectAll() {
        switchToMode(SelectionMode.Multiselect)
        showCircles = true
        val notEverythingIsSelected = !circleSelection.containsAll(circles.indices.toSet())
        if (notEverythingIsSelected) {
            circleSelection = circles.indices.toList()
            pointSelection = points.indices.toList()
        } else {
            circleSelection = emptyList()
            pointSelection = emptyList()
        }
    }

    fun toggleShowCircles() {
        showCircles = !showCircles
        if (!showCircles && mode is ToolMode)
            switchToMode(SelectionMode.Drag)
    }

    fun hidePanel() {
        showPanel = false
    }

    fun hideUIFor30s() {
        if (showUI) {
            showUI = false
            viewModelScope.launch {
                // MAYBE: also trigger fullscreen for desktop
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

    fun setNewRegionColor(color: Color) {
        openedDialog = null
        regionColor = color
        selectTool(EditClusterTool.Region)
//        showPanel = true
    }

    fun dismissRegionColorPicker() {
        openedDialog = null
        val tool = mode.tool
        val category = toolbarState.categories.firstOrNull { tool in it.tools }
        if (category != null)
            selectCategory(category, togglePanel = true)
        toolbarState = toolbarState.copy(activeTool = tool)
    }

    fun getMostCommonCircleColorInSelection(): Color? =
        circleSelection
            .mapNotNull { circleColors[it] }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { (_, k) -> k }
            ?.key

    fun setNewCircleColor(color: Color) {
        for (ix in circleSelection) {
            circleColors[ix] = color
        }
        openedDialog = null
    }

    fun dismissCircleColorPicker() {
        openedDialog = null
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
            circleSelection = emptyList()
        }
    }

    fun setActiveSelectionAsToolArg() {
        toolbarState.activeTool.let { tool ->
            require(
                tool is EditClusterTool.MultiArg &&
                tool.signature.argTypes.first() == ArgType.CircleAndPointIndices &&
                circleSelection.isNotEmpty()
            )
        }
        partialArgList = partialArgList!!.addArg(
            Arg.CircleAndPointIndices(
                circleIndices = circleSelection.toList(),
                pointIndices = pointSelection,
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
        recordCreateCommand()
        createNewCircles(listOf(horizontalLine, verticalLine))
        expressions.addFreeCircle()
        expressions.addFreeCircle()
        switchToMode(SelectionMode.Multiselect)
        circleSelection = listOf(circles.size - 2, circles.size - 1)
    }

    fun scaleSelection(zoom: Float) {
        val freeCircles =
            if (LOCK_DEPENDENT_OBJECT) circleSelection.filter { isFreeCircle(it) }
            else circleSelection
        if (circleSelectionIsActive && freeCircles.isNotEmpty()) {
            when (freeCircles.size) {
                1 -> {
                    recordCommand(Command.CHANGE_RADIUS, targets = freeCircles)
                    val ix = freeCircles.single()
                    when (val circle = circles[ix]) {
                        is Circle -> {
                            circles[ix] = circle.copy(radius = zoom * circle.radius)
                            adjustIncidentPoints(
                                parentIx = ix,
                                centroid = circle.center,
                                zoom = zoom
                            )
                        }
                        else -> {}
                    }
                }

                else -> {
                    recordCommand(Command.SCALE, targets = freeCircles)
                    val rect = getSelectionRect()
                    val center =
                        if (rect == null || rect.minDimension >= 5_000)
                            absolute(canvasSize.center.toOffset())
                        else rect.center
                    for (ix in freeCircles) {
                        circles[ix] = circles[ix]?.scale(center, zoom)
                        adjustIncidentPoints(
                            parentIx = ix,
                            centroid = center,
                            zoom = zoom
                        )
                    }
                }
            }
            expressions.update(freeCircles.map { Indexed.Circle(it) })
        } else if (mode == ToolMode.ARC_PATH && arcPathUnderConstruction != null) {
//            arcPathUnderConstruction = arcPathUnderConstruction?.scale(zoom)
        } else { // NOTE: scaling everything instead of canvas can produce more artifacts
            val allCircleIndices = circles.indices
            val targets = allCircleIndices + points.indices.map { -it - 1 }
            recordCommand(Command.SCALE, targets = targets)
            val center = absolute(canvasSize.center.toOffset())
            for (ix in allCircleIndices) {
                circles[ix] = circles[ix]?.scale(center, zoom)
                adjustIncidentPoints(
                    parentIx = ix,
                    centroid = center,
                    zoom = zoom
                )
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
                else -> {}
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
                    circleSelection = emptyList()
                } else {
                    val previouslySelectedCircle = circleSelection.firstOrNull()
                    reselectCircleAt(visiblePosition)
                    if (previouslySelectedCircle != null && circleSelection.isEmpty()) {
                        circleSelection += previouslySelectedCircle
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
                    if (restrictRegionsToSelection && circleSelection.isNotEmpty()) {
                        val restriction = circleSelection.toList()
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
                            selectPoint(points, visiblePosition)?.let { pointIndex ->
                                val newArg = Arg.CircleAndPointIndices(
                                    circleIndices = emptyList(),
                                    pointIndices = listOf(pointIndex)
                                )
                                if (partialArgList!!.currentArg != newArg)
                                    partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = true)
                            } ?:
                            selectCircle(circles, visiblePosition)?.let { circleIndex ->
                                val newArg = Arg.CircleAndPointIndices(
                                    circleIndices = listOf(circleIndex),
                                    pointIndices = emptyList()
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
    // onDown -> onTap -> onUp
    fun onTap(position: Offset, pointerCount: Int) {
        // 2-finger tap for undo (works only on Android afaik)
        if (TWO_FINGER_TAP_FOR_UNDO && pointerCount == 2) {
            if (undoIsEnabled)
                undo()
        } else if (showCircles) { // select circle(s)/region
            when (mode) {
                SelectionMode.Drag -> {
                    val pointReselected = reselectPointAt(position)
                    if (pointReselected)
                        circleSelection = emptyList()
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
                                    if (bounds != circleSelection.toSet()) {
                                        circleSelection = bounds.toList()
                                    } else {
                                        circleSelection = emptyList()
                                    }
                                } ?: run { // select bound of a non-existent part
                                    println("bounds of $part")
                                    val bounds: Set<Ix> = part.insides + part.outsides
                                    if (bounds != circleSelection.toSet()) {
                                        circleSelection = bounds.toList()
                                    } else {
                                        circleSelection = emptyList()
                                    }
                            }
                        }
                    }
                }
                SelectionMode.Region -> {
                    if (submode !is SubMode.FlowFill) {
                        if (restrictRegionsToSelection && circleSelection.isNotEmpty()) {
                            val restriction = circleSelection.toList()
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

    private fun scaleSingleCircle(c: Offset, zoom: Float, h: HandleConfig.SingleCircle, sm: SubMode.Scale) {
        val ix = Indexed.Circle(h.ix)
        val circle = circles[h.ix]
        val free = !LOCK_DEPENDENT_OBJECT || isFreeCircle(h.ix)
        if (free) {
            recordCommand(Command.CHANGE_RADIUS, targets = listOf(h.ix))
            if (circle is Circle) {
                val center = sm.center
                val r = (c - center).getDistance()
                circles[h.ix] = circle.copy(radius = r.toDouble())
                // no need to adjust children when scaling Circle
                expressions.update(listOf(ix))
            } else if (circle is Line) {
                val center = circle.project(c)
                adjustIncidentPoints(
                    parentIx = h.ix,
                    centroid = center,
                    zoom = zoom
                )
                expressions.update(listOf(ix))
            }
        }
    }

    private fun scaleViaSliderSingleCircle(pan: Offset, h: HandleConfig.SingleCircle, sm: SubMode.ScaleViaSlider) {
        val newPercentage = selectionControlsPositions.addPanToPercentage(sm.sliderPercentage, pan)
        if (sm.sliderPercentage != newPercentage) {
            recordCommand(Command.SCALE, targets = listOf(h.ix))
            val circle = circles[h.ix]
            val scaleFactor = sliderPercentageDeltaToZoom(newPercentage - sm.sliderPercentage)
            circles[h.ix] = circle?.scale(sm.center, scaleFactor)
            val ix = Indexed.Circle(h.ix)
            adjustIncidentPoints(
                parentIx = h.ix,
                centroid = sm.center,
                zoom = scaleFactor
            )
            submode = sm.copy(sliderPercentage = newPercentage)
            expressions.update(listOf(ix))
        }
    }

    private fun rotateSingleCircle(pan: Offset, c: Offset, h: HandleConfig.SingleCircle, sm: SubMode.Rotate) {
        val free = !LOCK_DEPENDENT_OBJECT || isFreeCircle(h.ix)
        if (free) {
            recordCommand(Command.ROTATE, targets = listOf(h.ix))
            val center = sm.center
            val centerToCurrent = c - center
            val centerToPreviousHandle = centerToCurrent - pan
            val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
            val newAngle = sm.angle + angle
            val snappedAngle =
                if (ENABLE_ANGLE_SNAPPING) snapAngle(newAngle)
                else newAngle
            val angle1 = (snappedAngle - sm.snappedAngle).toFloat()
            circles[h.ix] = circles[h.ix]?.rotate(center, angle1)
            val ix = Indexed.Circle(h.ix)
            adjustIncidentPoints(
                parentIx = h.ix,
                centroid = center,
                rotationAngle = angle1
            )
            submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
            expressions.update(listOf(ix))
        }
    }

    private fun scaleSeveralCircles(pan: Offset, freeCircles: Indices, freePoints: Indices, freeTargets: Indices) {
        getSelectionRect()?.let { rect ->
            recordCommand(Command.SCALE, targets = freeTargets)
            val scaleHandlePosition = rect.topRight
            val center = rect.center
            val centerToHandle = scaleHandlePosition - center
            val centerToCurrent = centerToHandle + pan
            val scaleFactor = centerToCurrent.getDistance()/centerToHandle.getDistance()
            for (ix in freeCircles) {
                circles[ix] = circles[ix]?.scale(center, scaleFactor)
                adjustIncidentPoints(
                    parentIx = ix,
                    centroid = center,
                    zoom = scaleFactor
                )
            }
            for (ix in freePoints) {
                points[ix] = points[ix]?.scale(center, scaleFactor)
            }
            expressions.update(
                freeCircles.map { Indexed.Circle(it) } +
                        freePoints.map { Indexed.Point(it) }
            )
        }
    }

    private fun scaleViaSliderSeveralCircles(pan: Offset, sm: SubMode.ScaleViaSlider, freeCircles: Indices, freePoints: Indices, freeTargets: Indices) {
        val newPercentage = selectionControlsPositions.addPanToPercentage(sm.sliderPercentage, pan)
        if (sm.sliderPercentage != newPercentage) {
            recordCommand(Command.SCALE, targets = freeTargets)
            val scaleFactor = sliderPercentageDeltaToZoom(newPercentage - sm.sliderPercentage)
            for (ix in freeCircles) {
                circles[ix] = circles[ix]?.scale(sm.center, scaleFactor)
                adjustIncidentPoints(
                    parentIx = ix,
                    centroid = sm.center,
                    zoom = scaleFactor
                )
            }
            for (ix in freePoints) {
                points[ix] = points[ix]?.scale(sm.center, scaleFactor)
            }
            submode = sm.copy(sliderPercentage = newPercentage)
            expressions.update(
                freeCircles.map { Indexed.Circle(it) } +
                        freePoints.map { Indexed.Point(it) }
            )
        }
    }

    private fun rotateSeveralCircles(pan: Offset, c: Offset, sm: SubMode.Rotate, freeCircles: Indices, freePoints: Indices, freeTargets: Indices) {
        recordCommand(Command.ROTATE, targets = freeTargets)
        val center = sm.center
        val centerToCurrent = c - center
        val centerToPreviousHandle = centerToCurrent - pan
        val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
        val newAngle = sm.angle + angle
        val snappedAngle =
            if (ENABLE_ANGLE_SNAPPING) snapAngle(newAngle)
            else newAngle
        val angle1 = (snappedAngle - sm.snappedAngle).toFloat()
        for (ix in freeCircles) {
            circles[ix] = circles[ix]?.rotate(sm.center, angle1)
            adjustIncidentPoints(
                parentIx = ix,
                centroid = sm.center,
                rotationAngle = angle1
            )
        }
        for (ix in freePoints) {
            points[ix] = points[ix]?.rotate(sm.center, angle1)
        }
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
        expressions.update(
            freeCircles.map { Indexed.Circle(it) } +
                    freePoints.map { Indexed.Point(it) }
        )
    }

    // dragging circle: move + scale radius
    private fun dragCircle(pan: Offset, c: Offset, zoom: Float, rotationAngle: Float) {
        val ix = circleSelection.single()
        val free = !LOCK_DEPENDENT_OBJECT || isFreeCircle(ix)
        if (free) {
            recordCommand(Command.MOVE, targets = listOf(ix))
            when (val circle = circles[ix]) {
                is Circle -> {
                    circles[ix] = circle
                        .translate(pan)
                        .scale(circle.center, zoom)
                        .rotate(circle.center, rotationAngle)
                    adjustIncidentPoints(
                        parentIx = ix,
                        translation = pan,
                        centroid = circle.center,
                        zoom = zoom,
                        rotationAngle = rotationAngle
                    )
                }
                is Line -> {
                    circles[ix] = circle
                        .translate(pan)
                        .rotate(c, rotationAngle)
                    adjustIncidentPoints(
                        parentIx = ix,
                        translation = pan,
                        centroid = c,
                        rotationAngle = rotationAngle
                    )
                }
                null -> {}
            }
            expressions.update(listOf(Indexed.Circle(ix)))
        }
    }

    private fun dragPoint(c: Offset) {
        // dragging point
        val ix = pointSelection.first()
        val free = !LOCK_DEPENDENT_OBJECT || isFreePoint(ix)
        if (free) {
            recordCommand(Command.MOVE, targets = listOf(-ix-1)) // have to distinguish from circle indices ig
            val excludedSnapTargets = expressions.children
                .getOrElse(Indexed.Point(ix)) { emptySet() }
                .filterIsInstance<Indexed.Circle>()
                .map { it.index }
                .toSet()
            points[ix] = snapped(c, excludePoints = true, excludedCircles = excludedSnapTargets).result
            expressions.changeToFree(Indexed.Point(ix))
            expressions.update(listOf(Indexed.Point(ix)))
        }
    }

    private fun dragCirclesOrPoints(pan: Offset, c: Offset, zoom: Float, rotationAngle: Float) {
        val freeCircles =
            if (LOCK_DEPENDENT_OBJECT)
                circleSelection.filter { isFreeCircle(it) }
            else circleSelection
        val freePoints =
            if (LOCK_DEPENDENT_OBJECT)
                pointSelection.filter { isFreePoint(it) }
            else pointSelection
        val freeTargets = freeCircles + freePoints.map { -it - 1 }
        if (freeCircles.size == 1 && freePoints.isEmpty()) { // move + scale radius
            val ix = freeCircles.single()
            recordCommand(Command.MOVE, targets = freeCircles)
            when (val circle = circles[ix]) {
                is Circle -> {
                    circles[ix] = circle.translate(pan).scale(circle.center, zoom)
                    adjustIncidentPoints(
                        parentIx = ix,
                        translation = pan,
                        centroid = circle.center,
                        zoom = zoom
                    )
                }
                is Line -> {
                    circles[ix] = circle.translate(pan)
                    adjustIncidentPoints(
                        parentIx = ix,
                        translation = pan
                    )
                }
                null -> {}
            }
        } else if (freeCircles.size > 1) { // scale radius & position
            recordCommand(Command.MOVE, targets = freeTargets)
            for (ix in freeCircles) {
                circles[ix] = circles[ix]
                    ?.translate(pan)
                    ?.scale(c, zoom)
                    ?.rotate(c, rotationAngle)
                adjustIncidentPoints(
                    parentIx = ix,
                    translation = pan,
                    centroid = c,
                    zoom = zoom,
                    rotationAngle = rotationAngle
                )
            }
            for (ix in freePoints) {
                points[ix] = points[ix]
                    ?.translate(pan)
                    ?.scale(c, zoom)
                    ?.rotate(c, rotationAngle)
            }
        }
        expressions.update(
            freeCircles.map { Indexed.Circle(it) } +
                    freePoints.map { Indexed.Point(it) }
        )
    }

    // MAYBE: handle key arrows as panning
    fun onPanZoomRotate(pan: Offset, centroid: Offset, zoom: Float, rotationAngle: Float) {
        val c = absolute(centroid)
        if (submode !is SubMode.None) {
            // drag handle
            when (val h = handleConfig) {
                is HandleConfig.SingleCircle -> {
                    when (val sm = submode) {
                        is SubMode.Scale -> scaleSingleCircle(c = c, zoom = zoom, h = h, sm = sm)
                        is SubMode.ScaleViaSlider -> scaleViaSliderSingleCircle(pan = pan, h = h, sm = sm)
                        is SubMode.Rotate -> rotateSingleCircle(pan = pan, c = c, h = h, sm = sm)
                        else -> {}
                    }
                }
                is HandleConfig.SeveralCircles -> {
                    val freeCircles =
                        if (LOCK_DEPENDENT_OBJECT)
                            circleSelection.filter { isFreeCircle(it) }
                        else circleSelection
                    val freePoints =
                        if (LOCK_DEPENDENT_OBJECT)
                            pointSelection.filter { isFreePoint(it) }
                        else pointSelection
                    val freeTargets = freeCircles + freePoints.map { -it - 1 }
                    if (freeTargets.isNotEmpty())
                        when (val sm = submode) {
                            is SubMode.Scale ->
                                scaleSeveralCircles(pan = pan, freeCircles = freeCircles, freePoints = freePoints, freeTargets = freeTargets)
                            is SubMode.ScaleViaSlider ->
                                scaleViaSliderSeveralCircles(pan = pan, sm = sm, freeCircles = freeCircles, freePoints = freePoints, freeTargets = freeTargets)
                            is SubMode.Rotate ->
                                rotateSeveralCircles(pan = pan, c = c, sm = sm, freeCircles = freeCircles, freePoints = freePoints, freeTargets = freeTargets)
                            else -> {}
                        }
                }
                else -> {}
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
                    circleSelection += diff.filter { it !in circleSelection }
                }
            } else if (mode == SelectionMode.Region && submode is SubMode.FlowFill) {
                val qualifiedPart = (submode as SubMode.FlowFill).lastQualifiedPart
                val (_, newQualifiedPart) = selectPartAt(centroid)
                if (qualifiedPart == null) {
                    submode = SubMode.FlowFill(newQualifiedPart)
                } else if (qualifiedPart != newQualifiedPart) {
                    submode = SubMode.FlowFill(newQualifiedPart)
                    if (restrictRegionsToSelection && circleSelection.isNotEmpty()) {
                        val restriction = circleSelection.toList()
                        reselectRegionAt(centroid, restriction)
                    } else {
                        reselectRegionAt(centroid)
                    }
                }
            }
        } else if (mode == SelectionMode.Drag && circleSelection.isNotEmpty() && showCircles) {
            dragCircle(pan = pan, c = c, zoom = zoom, rotationAngle = rotationAngle)
        } else if (mode == SelectionMode.Drag && pointSelection.isNotEmpty() && showCircles) {
            dragPoint(c = c)
        } else if (
            mode == SelectionMode.Multiselect &&
            (circleSelection.isNotEmpty() && showCircles || pointSelection.isNotEmpty())
        ) {
            dragCirclesOrPoints(pan = pan, c = c, zoom = zoom, rotationAngle = rotationAngle)
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
            SelectionMode.Drag -> {
                if (circleSelection.isEmpty() && pointSelection.isNotEmpty() && visiblePosition != null) {
                    // MAYBE: try to re-attach free points
//                    val ix = selectedPoints.first()
//                    val excludedSnapTargets = expressions.children
//                        .getOrElse(Indexed.Point(ix)) { emptySet() }
//                        .filterIsInstance<Indexed.Circle>()
//                        .map { it.index }
//                        .toSet()
//                    val result = snapped(
//                        absolute(visiblePosition),
//                        excludePoints = true,
//                        excludedCircles = excludedSnapTargets
//                    )
                    // should we: set to null and replace with a new point
                    // try to find existing one? smth else?
//                    realizePointCircleSnap(result)
//                    println("attach $result")
                }
            }
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
        if (mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect) // haxx
            toolbarState = toolbarState.copy(activeTool = EditClusterTool.Multiselect)
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

    /** @param[rotationAngle] in degrees */
    fun adjustIncidentPoints(
        parentIx: Ix,
        translation: Offset = Offset.Zero,
        centroid: Offset = Offset.Zero,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
    ) {
        val ix = Indexed.Circle(parentIx)
        val ix2point = expressions.getIncidentPoints(ix)
            .associateWith { child ->
                val newPoint = points[child.index]
                    ?.translate(translation)
                    ?.scale(centroid, zoom)
                    ?.rotate(centroid, rotationAngle)
                newPoint?.downscale()
            }
        expressions.adjustIncidentPointExpressions(ix2point)
    }

    private fun selectCategory(category: EditClusterCategory, togglePanel: Boolean = false) {
        val wasSelected = toolbarState.activeCategory == category
        val panelWasShown = showPanel
        toolbarState = toolbarState.copy(activeCategory = category)
        showPanel = toolbarState.panelNeedsToBeShown
        if (togglePanel && wasSelected && toolbarState.panelNeedsToBeShown) {
            showPanel = !panelWasShown
        }
    }

    fun selectTool(tool: EditClusterTool, togglePanel: Boolean = false) {
        val category: EditClusterCategory
        if (tool is EditClusterTool.AppliedColor) {
            category = EditClusterCategory.Colors
        } else {
            category = toolbarState.getCategory(tool)
            toolbarState = toolbarState.updateDefault(category, tool)
        }
        toolbarState = toolbarState.copy(activeTool = tool)
        selectCategory(category, togglePanel = togglePanel)
        toolAction(tool)
    }

    fun switchToCategory(category: EditClusterCategory, togglePanel: Boolean = false) {
        val defaultTool = toolbarState.getDefaultTool(category)
        if (defaultTool == null) {
            selectCategory(category, togglePanel = togglePanel)
        } else {
            selectTool(defaultTool, togglePanel = togglePanel)
        }
    }

    fun processKeyboardAction(action: KeyboardAction) {
//        println("processing $action")
        when (action) {
            KeyboardAction.SELECT_ALL -> {
                if (!mode.isSelectingCircles() || !showCircles) // more intuitive behavior
                    circleSelection = emptyList() // forces to select all instead of toggling
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
                    circleSelection = emptyList()
                    pointSelection = emptyList()
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
        // TODO: realize args when needed
        when (toolMode) {
            ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> completeCircleByCenterAndRadius()
            ToolMode.CIRCLE_BY_3_POINTS -> completeCircleBy3Points()
            ToolMode.CIRCLE_BY_PENCIL_AND_POINT -> completeCircleByPencilAndPoint()
            ToolMode.LINE_BY_2_POINTS -> completeLineBy2Points()
            ToolMode.POINT -> completePoint()
            ToolMode.ARC_PATH -> throw IllegalStateException("Use separate function to route completion")
            ToolMode.CIRCLE_INVERSION -> completeCircleInversion()
            ToolMode.CIRCLE_INTERPOLATION -> openedDialog = DialogType.CIRCLE_INTERPOLATION
            ToolMode.CIRCLE_EXTRAPOLATION -> openedDialog = DialogType.CIRCLE_EXTRAPOLATION
            ToolMode.LOXODROMIC_MOTION -> openedDialog = DialogType.LOXODROMIC_MOTION
        }
    }

    private fun completeCircleByCenterAndRadius() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.Point }
        recordCreateCommand()
        if (args.all { it is Arg.Point.XY }) {
            val newCircle = computeCircleByCenterAndRadius(
                center = (args[0] as Arg.Point.XY).toPoint().downscale(),
                radiusPoint = (args[1] as Arg.Point.XY).toPoint().downscale(),
            )?.upscale()
            createNewCircle(newCircle)
            expressions.addFreeCircle()
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.Point.Index -> Indexed.Point(it.index)
                    is Arg.Point.XY -> createNewFreePoint(it.toPoint())
                }
            }
            val newCircle = expressions.addSoloCircleExpression(
                Expr.CircleByCenterAndRadius(
                    center = realized[0],
                    radiusPoint = realized[1]
                ),
            )
            createNewCircle(newCircle?.upscale())
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleBy3Points() {
        val argList = partialArgList!!
        val args = argList.args.map {
            it as Arg.CircleOrPoint
        }
        recordCreateCommand()
        if (args.all { it is Arg.CircleOrPoint.Point.XY }) {
            val (p1, p2, p3) = args.map {
                (it as Arg.CircleOrPoint.Point.XY).toPoint().downscale()
            }
            val newCircle = computeCircleBy3Points(p1, p2, p3)
            expressions.addFreeCircle()
            createNewCircle(newCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.CircleOrPoint.CircleIndex -> Indexed.Circle(it.index)
                    is Arg.CircleOrPoint.Point.Index -> Indexed.Point(it.index)
                    is Arg.CircleOrPoint.Point.XY -> createNewFreePoint(it.toPoint(), triggerRecording = false)
                }
            }.sortedBy { it.index }
            val newCircle = expressions.addSoloCircleExpression(
                Expr.CircleBy3Points(
                    object1 = realized[0],
                    object2 = realized[1],
                    object3 = realized[2],
                ),
            )
            createNewCircle(newCircle?.upscale())
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleByPencilAndPoint() {
        val argList = partialArgList!!
        val args = argList.args.map {
            it as Arg.CircleOrPoint
        }
        recordCreateCommand()
        if (args.all { it is Arg.CircleOrPoint.Point.XY }) {
            val (p1, p2, p3) = args.map {
                (it as Arg.CircleOrPoint.Point.XY).toPoint().downscale()
            }
            val newCircle = computeCircleByPencilAndPoint(p1, p2, p3)
            createNewCircle(newCircle?.upscale())
            expressions.addFreeCircle()
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.CircleOrPoint.CircleIndex -> Indexed.Circle(it.index)
                    is Arg.CircleOrPoint.Point.Index -> Indexed.Point(it.index)
                    is Arg.CircleOrPoint.Point.XY -> createNewFreePoint(it.toPoint(), triggerRecording = false)
                }
            }
            val newCircle = expressions.addSoloCircleExpression(
                Expr.CircleByPencilAndPoint(
                    pencilObject1 = realized[0],
                    pencilObject2 = realized[1],
                    perpendicularObject = realized[2],
                ),
            )
            createNewCircle(newCircle?.upscale())
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeLineBy2Points() {
        val argList = partialArgList!!
        val args = argList.args.map {
            it as Arg.CircleOrPoint
        }
        recordCreateCommand()
        if (args.all { it is Arg.CircleOrPoint.Point.XY }) {
            val (p1, p2) = args.map {
                (it as Arg.CircleOrPoint.Point.XY).toPoint().downscale()
            }
            val newCircle = computeLineBy2Points(p1, p2)
            createNewCircle(newCircle?.upscale())
            expressions.addFreeCircle()
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.CircleOrPoint.CircleIndex -> Indexed.Circle(it.index)
                    is Arg.CircleOrPoint.Point.Index -> Indexed.Point(it.index)
                    is Arg.CircleOrPoint.Point.XY -> createNewFreePoint(it.toPoint(), triggerRecording = false)
                }
            }.sortedBy { it.index }
            val newCircle = expressions.addSoloCircleExpression(
                Expr.LineBy2Points(
                    object1 = realized[0],
                    object2 = realized[1],
                ),
            )
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
            val newCircle = expressions.addSoloCircleExpression(
                Expr.CircleInversion(
                    Indexed.Circle(circleIx),
                    Indexed.Circle(invertingCircleIndex),
                ),
            )
            newCircle?.upscale()
//            when (val newCircle = result.toDirectedCircleOrLine()) {
//                is DirectedCircle -> newCircle.toCircle() to !newCircle.inside
//                is Line -> newCircle to false
//                else -> null
//            }
        }
        recordCreateCommand()
        val newPoints = targetPointIxs.mapNotNull { pointIx ->
            val newPoint = expressions.addSoloPointExpression(
                Expr.CircleInversion(
                    Indexed.Point(pointIx),
                    Indexed.Circle(invertingCircleIndex),
                ),
            )
            newPoint?.upscale()
        }
        createNewCircles(newCircles)
        copyParts(
            targetCircleIxs,
            ((circles.size - newCircles.size) until circles.size).toList(),
            flipInAndOut = true
        )
        points.addAll(newPoints)
        partialArgList = PartialArgList(argList.signature)
    }

    fun completeCircleInterpolation(params: InterpolationParameters) {
        openedDialog = null
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.CircleIndex }
        val startCircleIx = args[0].index
        val endCircleIx = args[1].index
        recordCreateCommand()
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
        openedDialog = null
        partialArgList = PartialArgList(EditClusterTool.CircleInterpolation.signature)
    }

    fun completeCircleExtrapolation(
        params: ExtrapolationParameters,
    ) {
        openedDialog = null
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.CircleIndex }
        val startCircleIx = args[0].index
        val endCircleIx = args[1].index
        recordCreateCommand()
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
        openedDialog = null
        partialArgList = PartialArgList(EditClusterTool.CircleExtrapolation.signature)
    }

    // TODO: inf point input
    fun completeLoxodromicMotion(
        params: LoxodromicMotionParameters,
    ) {
        openedDialog = null
        recordCreateCommand()
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
        val n = params.nSteps + 1
        repeat(n) { i ->
            val newIndices = targetCircleIndices.indices.map { j ->
                size0 + i + j*n
            }
            copyParts(targetCircleIndices, newIndices)
        }
        partialArgList = PartialArgList(argList.signature)
        defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(params)
    }

    fun resetLoxodromicMotion() {
        openedDialog = null
        partialArgList = PartialArgList(EditClusterTool.LoxodromicMotion.signature)
    }

    fun completeArcPath() {
        require(arcPathUnderConstruction != null)
        // only add circles
        // since `part`itioning in-arcpath region is rather involved
        arcPathUnderConstruction?.let { arcPath ->
            recordCreateCommand()
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
            EditClusterTool.Palette -> openedDialog = DialogType.REGION_COLOR_PICKER
            EditClusterTool.Expand -> scaleSelection(HUD_ZOOM_INCREMENT)
            EditClusterTool.Shrink -> scaleSelection(1/HUD_ZOOM_INCREMENT)
            EditClusterTool.Duplicate -> duplicateCircles()
            EditClusterTool.PickCircleColor -> openedDialog = DialogType.CIRCLE_COLOR_PICKER
            EditClusterTool.Delete -> deleteCircles()
            EditClusterTool.InsertCenteredCross -> insertCenteredCross()
            is EditClusterTool.AppliedColor -> setNewRegionColor(tool.color)
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
            EditClusterTool.ToggleSelectAll -> circleSelection.containsAll(circles.indices.toSet())
            EditClusterTool.Region -> mode == SelectionMode.Region && submode !is SubMode.FlowFill
            EditClusterTool.FlowFill -> mode == SelectionMode.Region && submode is SubMode.FlowFill
            EditClusterTool.FillChessboardPattern -> chessboardPatternStartsColored
            EditClusterTool.RestrictRegionToSelection -> restrictRegionsToSelection
            EditClusterTool.ShowCircles -> showCircles
            EditClusterTool.ToggleFilledOrOutline -> !showWireframes
//            EditClusterTool.Palette -> openedDialog == DialogType.REGION_COLOR_PICKER
            is EditClusterTool.MultiArg -> mode == ToolMode.correspondingTo(tool)
            else -> true
        }


//    fun GCircle.downscale(): GCircle = scale(0.0, 0.0, DOWNSCALING_FACTOR)
//    fun GCircle.upscale(): GCircle = scale(0.0, 0.0, UPSCALING_FACTOR)
    fun CircleOrLine.downscale(): CircleOrLine = scale(0.0, 0.0, DOWNSCALING_FACTOR)
    fun CircleOrLine.upscale(): CircleOrLine =
        scale(0.0, 0.0, UPSCALING_FACTOR)
    fun Point.downscale(): Point = scale(0.0, 0.0, DOWNSCALING_FACTOR)
    fun Point.upscale(): Point = scale(0.0, 0.0, UPSCALING_FACTOR)

    fun saveState(): State {
        val center = computeAbsoluteCenter() ?: Offset.Zero
        return State(
            constellation = toConstellation(),
            circleSelection = circleSelection.toList(),
            centerX = center.x,
            centerY = center.y,
        )
    }

    private fun launchRestore() {
        viewModelScope.launch {
            if (!RESTORE_LAST_SAVE_ON_LOAD) {
                loadNewConstellation(Constellation.SAMPLE)
                centerizeTo(0f, 0f)
            } else {
                val state = getPlatform().lastStateStore.get()
                if (state != null) {
                    restoreFromState(state)
                } else {
                    loadNewConstellation(Constellation.SAMPLE)
                    centerizeTo(0f, 0f)
                }
            }
        }
    }

    private fun restoreFromState(state: State) {
        loadNewConstellation(state.constellation)
        if (state.circleSelection.size > 1) {
            mode = SelectionMode.Multiselect
        }
        circleSelection = state.circleSelection
        centerizeTo(state.centerX, state.centerY)
    }

    fun cacheState() {
        println("caching VM state...")
        val state = saveState()
        getPlatform().saveLastState(state)
    }

    // NOTE: i never seen this proc on Android or Wasm tbh
    //  so i had to create Flow<LifecycleEvent> to manually trigger caching
    override fun onCleared() {
        cacheState()
        super.onCleared()
    }

    // NOTE: there seem to be some problems with bg kill recovery on android
    /** Be careful to pass *only* strictly immutable args by __copying__ */
    @Serializable
    @Immutable
    data class State(
        val constellation: Constellation,
        val circleSelection: List<Ix>, // circle indices
        // NOTE: saving VM.translation instead has some issues
        val centerX: Float,
        val centerY: Float,
    ) {
        companion object {
            val SAMPLE = State(
                Constellation(
                    points = emptyList(),
                    circles = listOf(
                        Circle(200.0, 200.0, 100.0),
                        Circle(250.0, 200.0, 100.0),
                        Circle(200.0, 250.0, 100.0),
                        Circle(250.0, 250.0, 100.0),
                    ).map { CircleConstruct.Concrete(it) },
                    parts = listOf(ClusterPart(
                        insides = setOf(0),
                        outsides = setOf(1, 2, 3)
                    )),
                ),
                circleSelection = listOf(0),
                centerX = 225f,
                centerY = 225f,
            )
        }
    }

    companion object {
        // reference: https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            addInitializer(EditClusterViewModel::class) {
                EditClusterViewModel()
            }
        }

        const val LOW_ACCURACY_FACTOR = 1.5f
        const val HUD_ZOOM_INCREMENT = 1.1f // == +10%
        const val KEYBOARD_ZOOM_INCREMENT = 1.05f // == +5%
        const val MAX_SLIDER_ZOOM = 3.0f // == +200%
        const val FAST_CENTERED_CIRCLE = true
        const val ENABLE_ANGLE_SNAPPING = true
        const val LOCK_DEPENDENT_OBJECT = true
        const val RESTORE_LAST_SAVE_ON_LOAD = true
        const val TWO_FINGER_TAP_FOR_UNDO = true
        /** [Double] arithmetic is best in range that is closer to 0 */
        const val UPSCALING_FACTOR = 200.0
        const val DOWNSCALING_FACTOR = 1/UPSCALING_FACTOR

        fun sliderPercentageDeltaToZoom(percentageDelta: Float): Float =
            MAX_SLIDER_ZOOM.pow(2*percentageDelta)
    }
}
