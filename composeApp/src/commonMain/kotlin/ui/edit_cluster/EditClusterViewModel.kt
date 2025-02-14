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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import data.geometry.ArcPathCircle
import data.geometry.ArcPathPoint
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.ImaginaryCircle
import data.geometry.Line
import data.geometry.PartialArcPath
import data.geometry.Point
import data.geometry.fromCorners
import data.geometry.selectWithRectangle
import domain.Arg
import domain.ArgType
import domain.BlendModeType
import domain.ChessboardPattern
import domain.ColorAsCss
import domain.Command
import domain.History
import domain.InversionOfControl
import domain.Ix
import domain.PartialArgList
import domain.PointSnapResult
import domain.Settings
import domain.angleDeg
import domain.cluster.ClusterV1
import domain.cluster.Constellation
import domain.cluster.LogicalRegion
import domain.compressConstraints
import domain.entails
import domain.expressions.BiInversionParameters
import domain.expressions.Expr
import domain.expressions.Expression
import domain.expressions.ExpressionForest
import domain.expressions.ExtrapolationParameters
import domain.expressions.IncidenceParameters
import domain.expressions.InterpolationParameters
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.ObjectConstruct
import domain.expressions.Parameters
import domain.expressions.computeCircleBy3Points
import domain.expressions.computeCircleByCenterAndRadius
import domain.expressions.computeCircleByPencilAndPoint
import domain.expressions.computeIntersection
import domain.expressions.computeLineBy2Points
import domain.expressions.reIndex
import domain.filterIndices
import domain.io.DdcV1
import domain.io.DdcV2
import domain.io.DdcV4
import domain.io.constellation2svg
import domain.io.parseDdcV1
import domain.io.parseDdcV2
import domain.io.parseDdcV3
import domain.io.parseDdcV4
import domain.never
import domain.reindexingMap
import domain.snapAngle
import domain.snapPointToCircles
import domain.snapPointToPoints
import domain.sortedByFrequency
import domain.toArgPoint
import domain.transpose
import domain.tryCatch2
import getPlatform
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ui.edit_cluster.dialogs.DefaultBiInversionParameters
import ui.edit_cluster.dialogs.DefaultExtrapolationParameters
import ui.edit_cluster.dialogs.DefaultInterpolationParameters
import ui.edit_cluster.dialogs.DefaultLoxodromicMotionParameters
import ui.theme.DodeclustersColors
import ui.tools.EditClusterCategory
import ui.tools.EditClusterTool
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

// MAYBE: use UiState functional pattern + StateFlow's instead of this mess
// this class is obviously too big
// TODO: decouple navigation & tools/categories
@Suppress("MemberVisibilityCanBePrivate")
class EditClusterViewModel : ViewModel() {
    /** All existing [GCircle]s; `null`s correspond either to unrealized outputs of
     * [Expr.OneToMany], or to forever deleted objects (they have `null` [expressions]),
     * or (rarely) to mismatching type casts */
    val objects: SnapshotStateList<GCircle?> = mutableStateListOf()
    /** Filled regions delimited by some objects from [objects] */
    val regions: SnapshotStateList<LogicalRegion> = mutableStateListOf()
    var expressions: ExpressionForest = ExpressionForest( // stub
        initialExpressions = emptyMap(),
        get = { null },
        set = { _, _ -> }
    )
        private set

    var mode: Mode by mutableStateOf(SelectionMode.Drag)
        private set
    var submode: SubMode by mutableStateOf(SubMode.None)
        private set
    // NOTE: Arg.XYPoint & co use absolute positioning
    var partialArgList: PartialArgList? by mutableStateOf(null)
        private set

    // MAYBE: when circles are hidden select regions instead
    /** indices of selected circles/lines/points */
    var selection: List<Ix> by mutableStateOf(emptyList())
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
    var regionColor: Color by mutableStateOf(DodeclustersColors.deepAmethyst)
        private set

    /** `[0; 1]` transparency of non-chessboard [regions] */
    var regionsOpacity: Float by mutableStateOf(1.0f)
        private set
    var regionsBlendModeType: BlendModeType by mutableStateOf(BlendModeType.SRC_OVER)
        private set
    /** custom colors for circle/line borders or points */
    val objectColors: SnapshotStateMap<Ix, Color> = mutableStateMapOf()
    var backgroundColor: Color? by mutableStateOf(null)
    var showCircles: Boolean by mutableStateOf(true)
        private set
    // alt name: ghost[ed] objects
    var phantoms: Set<Ix> by mutableStateOf(emptySet())
        private set
    var showPhantomObjects: Boolean by mutableStateOf(false)
        private set
    /** which style to use when drawing regions: true = stroke, false = fill */
    var showWireframes: Boolean by mutableStateOf(false)
        private set
    var showDirectionArrows: Boolean by mutableStateOf(DEFAULT_SHOW_DIRECTION_ARROWS_ON_SELECTED_CIRCLES)
        private set
    var regionManipulationStrategy: RegionManipulationStrategy by mutableStateOf(RegionManipulationStrategy.REPLACE)
        private set
    /** applies to [SelectionMode.Region]:
     * only use circles present in the [selection] to determine which regions to fill */
    var restrictRegionsToSelection: Boolean by mutableStateOf(false)
        private set
    var chessboardPattern: ChessboardPattern by mutableStateOf(ChessboardPattern.NONE)
        private set
    var chessboardColor: Color by mutableStateOf(regionColor)
        private set

    // these 2 are NG
    val circleSelectionIsActive: Boolean by derivedStateOf {
        showCircles && selection.any { objects[it] is CircleOrLine } && mode.isSelectingCircles()
    }
    val pointSelectionIsActive: Boolean by derivedStateOf {
        showCircles && selection.any { objects[it] is Point } && mode.isSelectingCircles()
    }
    val handleConfig: HandleConfig? by derivedStateOf { // depends on selectionMode & selection
        when (mode) {
            SelectionMode.Drag ->
                if (selection.isEmpty()) null
                else HandleConfig.SingleCircle(selection.single())
            SelectionMode.Multiselect -> when {
                selection.isEmpty() -> null
                selection.size == 1 -> HandleConfig.SingleCircle(selection.single())
                selection.size > 1 -> HandleConfig.SeveralCircles(selection)
                else -> null // never
            }
            SelectionMode.Region -> null
            else -> null
        }
    }
    /** when changing [expressions], flip this to forcibly recalculate [selectionIsLocked] */
    private var selectionIsLockedTrigger: Boolean by mutableStateOf(false)
    // MAYBE: show quick prompt/popup instead of button
    val selectionIsLocked: Boolean by derivedStateOf {
        selectionIsLockedTrigger // haxxz
        selection.all { objects[it] == null || !isFree(it) }
    }

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
    var defaultBiInversionParameters = DefaultBiInversionParameters()
        private set
    var defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters()
        private set

    private val _animations: MutableSharedFlow<ObjectAnimation> = MutableSharedFlow()
    // i'll be real this stupid practice is annoying and ugly & a hack
    val animations: SharedFlow<ObjectAnimation> = _animations.asSharedFlow()

    val snackbarMessages: MutableSharedFlow<Pair<SnackbarMessage, String>> =
        MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var openedDialog: DialogType? by mutableStateOf(null)
        private set

    var partialArcPath: PartialArcPath? by mutableStateOf(null)
        private set
//    var arcPaths: List<ArcPath> by mutableStateOf(emptyList())
//        private set
//    val pathCache: MutableMap<Ix, Path?> = mutableMapOf()

    var canvasSize: IntSize by mutableStateOf(IntSize.Zero) // used when saving best-center
        private set
    private val selectionControlsPositions: SelectionControlsPositions by derivedStateOf {
        SelectionControlsPositions(canvasSize)
    }
    var translation: Offset by mutableStateOf(Offset.Zero)
        private set

    /** min tap/grab distance to select an object */
    private var tapRadius = getPlatform().tapRadius

    private var dragDownedWithNothing = true
    private var movementAfterDown = false

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

    fun saveAsYaml(name: String = DdcV4.DEFAULT_NAME): String {
        // Q: there seemingly was an issue with saving circleColors on Android
        return YamlEncoding.encodeToString(DdcV4.from(toConstellation()).copy(
            name = name,
            bestCenterX = computeAbsoluteCenter()?.x,
            bestCenterY = computeAbsoluteCenter()?.y,
            chessboardPattern = chessboardPattern != ChessboardPattern.NONE,
            chessboardPatternStartsColored = chessboardPattern == ChessboardPattern.STARTS_COLORED,
            chessboardColor = chessboardColor
        ))
    }

    fun exportAsSvg(name: String = DdcV4.DEFAULT_NAME): String {
        val start = absolute(Offset.Zero)
        return constellation2svg(
            constellation = toConstellation(),
            width = canvasSize.width.toFloat(),
            height = canvasSize.height.toFloat(),
            startX = start.x, startY = start.y,
            encodeCirclesAndPoints = showCircles,
            chessboardPattern = chessboardPattern,
            chessboardCellColor = chessboardColor,
            name = name,
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
                val ddc = parseDdcV4(yaml)
                val constellation = ddc.toConstellation()
                loadNewConstellation(constellation)
                centerizeTo(ddc.bestCenterX, ddc.bestCenterY)
                chessboardPattern =
                    if (!ddc.chessboardPattern) ChessboardPattern.NONE
                    else if (ddc.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                    else ChessboardPattern.STARTS_TRANSPARENT
                ddc.chessboardColor?.let {
                    chessboardColor = it
                }
            },
            { e ->
                println("Failed to parse DdcV4->yaml, falling back to DdcV3->yaml")
                e.printStackTrace()
                loadDdcV3FromYaml(yaml)
            }
        )
    }

    fun loadDdcV3FromYaml(yaml: String) {
        tryCatch2<SerializationException, IllegalArgumentException>(
            {
                val ddc = parseDdcV3(yaml)
                val constellation = ddc.toConstellation().toConstellation()
                loadNewConstellation(constellation)
                centerizeTo(ddc.bestCenterX, ddc.bestCenterY)
                chessboardPattern =
                    if (!ddc.chessboardPattern) ChessboardPattern.NONE
                    else if (ddc.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                    else ChessboardPattern.STARTS_TRANSPARENT
            },
            { e ->
                println("Failed to parse DdcV3->yaml, falling back to DdcV2->yaml")
                e.printStackTrace()
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
                chessboardPattern =
                    if (!ddc.chessboardPattern) ChessboardPattern.NONE
                    else if (ddc.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                    else ChessboardPattern.STARTS_TRANSPARENT
            },
            { e ->
                println("Failed to parse DdcV2->yaml, falling back to DdcV1->yaml")
                e.printStackTrace()
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
            { e ->
                println("Failed to parse DdcV1->yaml, falling back to ClusterV1->json")
                e.printStackTrace()
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
                println("Failed to parse ClusterV1->json")
                e.printStackTrace()
                queueSnackbarMessage(SnackbarMessage.FAILED_OPEN)
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
        chessboardPattern = ChessboardPattern.NONE
        translation = Offset.Zero
        loadConstellation(constellation)
        // reset history on load
        history.clear()
        resetTransients()
        println("loaded new constellation")
    }

    private fun loadConstellation(constellation: Constellation) {
        selection = emptyList()
        regions.clear()
        objectColors.clear()
        objects.clear()
        objects.addAll(
            constellation.objects.map {
                when (it) {
                    is ObjectConstruct.ConcreteCircle -> it.circle
                    is ObjectConstruct.ConcreteLine -> it.line
                    is ObjectConstruct.ConcretePoint -> it.point
                    is ObjectConstruct.Dynamic -> null // to-be-computed during reEval()
                }
            }
        )
        expressions = ExpressionForest(
            initialExpressions = constellation.toExpressionMap(),
            get = { objects[it]?.downscale() },
            set = { ix, value ->
                objects[ix] = value?.upscale()
            }
        )
        expressions.reEval() // calculates all dependent objects
//        expressions.update(
//            expressions.scaleLineIncidenceExpressions(DOWNSCALING_FACTOR)
//        )
        regions.addAll(constellation.parts)
        objectColors.putAll(constellation.objectColors)
        backgroundColor = constellation.backgroundColor
    }

    fun toConstellation(): Constellation {
        // pruning nulls
        val deleted = objects.indices.filter { ix ->
            objects[ix] == null && expressions.expressions[ix] == null
        }.toSet()
        val reindexing = reindexingMap(
            originalIndices = objects.indices,
            deletedIndices = deleted
        )
//        expressions.scaleLineIncidenceExpressions(UPSCALING_FACTOR)
        val objectConstructs = objects.indices.mapNotNull { ix ->
            val e = expressions.expressions[ix]
            if (e == null) {
                when (val o = objects[ix]) {
                    is Point -> ObjectConstruct.ConcretePoint(o)
                    is Line -> ObjectConstruct.ConcreteLine(o)
                    is Circle -> ObjectConstruct.ConcreteCircle(o)
                    else -> null
                }
            } else {
                ObjectConstruct.Dynamic(
                    // since children are auto-deleted with their parent we can !! safely
                    e.reIndex(reIndexer = { reindexing[it]!! })
                )
            }
        }
//        expressions.scaleLineIncidenceExpressions(DOWNSCALING_FACTOR)
        val logicalRegions = regions.mapNotNull { region ->
            val insides = region.insides.mapNotNull { reindexing[it] }.toSet()
            val outsides = region.outsides.mapNotNull { reindexing[it] }.toSet()
            if (insides.isEmpty() && outsides.isEmpty())
                null
            else
                region.copy(insides = insides, outsides = outsides)
        }
        return Constellation(
            objects = objectConstructs,
            parts = logicalRegions,
            objectColors = objectColors.mapNotNull { (ix, color) ->
                reindexing[ix]?.let { it to color }
            }.toMap(),
            backgroundColor = backgroundColor
        )
    }

    fun undo() {
        val m = mode
        if (m is ToolMode && partialArgList?.args?.isNotEmpty() == true) {
            partialArgList = PartialArgList(m.signature) // MAYBE: just pop the last arg
        } else {
            val currentSelection = selection.toList()
            switchToMode(mode) // clears up stuff
            selection = emptyList()
            history.undo()
            selection = currentSelection.filter { it in objects.indices }
            resetTransients()
        }
    }

    fun redo() {
        val currentSelection = selection.toList()
        switchToMode(mode)
        history.redo()
        selection = currentSelection.filter { it in objects.indices }
        resetTransients()
    }

    private fun loadState(state: State) {
        submode = SubMode.None
        loadConstellation(state.constellation)
        selection = state.selection.filter { it in objects.indices } // just in case
        centerizeTo(state.centerX, state.centerY)
        chessboardPattern = state.chessboardPattern
        state.regionColor?.let {
            regionColor = it
        }
    }

    private fun resetTransients() {
        showPromptToSetActiveSelectionAsToolArg = false
        submode = SubMode.None
        undoIsEnabled = history.undoIsEnabled
        redoIsEnabled = history.redoIsEnabled
    }

    private fun addObject(obj: GCircle?): Ix {
        objects.add(obj)
        return objects.size - 1
    }

    private fun addObjects(objs: List<GCircle?>) {
        objects.addAll(objs)
    }

    private fun removeObjects(ixs: List<Ix>) {
        for (ix in ixs) {
            objects[ix] = null
            objectColors.remove(ix)
        }
    }

    /** Use BEFORE modifying the state by the [command]!
     * Takes snapshot of present state and records it to history.
     * ```
     * s_i := history[i]
     * c_i := commands[i]
     * s0 (aka original) -> c0 -> s1 -> c1 -> s2 ...
     * ```
     *
     * [unique] flag guarantees snapshotting new state for [history]
     * */
    private fun recordCommand(
        command: Command,
        targets: List<Ix>? = null,
        target: Ix? = null,
        unique: Boolean = false,
    ) {
        val tag = if (unique) {
            Command.Tag.Unique()
        } else {
            val allTargets = targets ?: listOfNotNull(target)
            if (allTargets.isEmpty()) {
                null
            } else {
                Command.Tag.Targets(allTargets)
            }
        }
        history.recordCommand(command, tag)
        undoIsEnabled = history.undoIsEnabled
        redoIsEnabled = history.redoIsEnabled
        if (command != Command.ROTATE) // erm
            submode.let {
                if (it is SubMode.Rotate)
                    submode = SubMode.None
            }
    }

    /** Use BEFORE modifying the state by the [Command.CREATE]!
     * Takes snapshot of present state and records it to history.
     * See [recordCommand] for more details. */
    private fun recordCreateCommand() {
        recordCommand(Command.CREATE, unique = true)
    }

    fun createNewGCircle(newGCircle: GCircle?) =
        createNewGCircles(listOf(newGCircle))

    /** Append [newGCircles] to [objects] and queue circle entrance animation */
    fun createNewGCircles(
        newGCircles: List<GCircle?>,
    ) {
        val normalizedGCircles = newGCircles.map {
            if (it is Circle && it.radius <= 0) // Q: idk why are we doing it, does it ever happen?
                null
            else it
        }
        val validNewGCircles = normalizedGCircles.filterNotNull()
        if (validNewGCircles.isNotEmpty()) {
            showCircles = true
            val prevSize = objects.size
            addObjects(normalizedGCircles)
            selection = (prevSize until objects.size).filter { objects[it] != null }
            viewModelScope.launch {
                _animations.emit(
                    CircleAnimation.Entrance(validNewGCircles.filterIsInstance<CircleOrLine>())
                )
            }
        } else { // all nulls
            addObjects(normalizedGCircles)
            selection = emptyList()
        }
    }

    fun createNewFreePoint(
        point: Point,
        triggerRecording: Boolean = true
    ): Ix {
        if (triggerRecording)
            recordCreateCommand()
        addObject(point)
        val newIx = expressions.addFree()
        require(newIx == objects.size - 1) { "Incorrect index retrieved from expression.addFree() during createNewFreePoint()" }
        return newIx
    }

    /** Add objects from [sourceIndex2NewTrajectory] to [objects], while
     * copying regions (for [CircleOrLine]s) and [objectColors] from original
     * indices specified in [sourceIndex2NewTrajectory].
     * We assume that appropriate expressions were/will be created and
     * that those expressions follow the order of [sourceIndex2NewTrajectory]`.flatten()`, but
     * the objects themselves are yet to be added to [objects]. In addition set
     * new objects that are circles/lines/points as [selection].
     * @param[sourceIndex2NewTrajectory] `[(original index ~ style source, [new trajectory of objects])]`,
     * note that original indices CAN repeat (tho its regions will be copied only once even for the repeats).
     * @param[circleAnimationInit] given list of circles/lines queue [CircleAnimation]
     * constructed by this block. Use `{ null }` if no animation is required.
     * */
    private inline fun copyRegionsAndStylesForNewTrajectories(
        sourceIndex2NewTrajectory: List<Pair<Ix, List<GCircle?>>>,
        flipRegionsInAndOut: Boolean = false,
        crossinline circleAnimationInit: (List<CircleOrLine>) -> CircleAnimation? = { null },
    ) {
        val oldSize = objects.size
        val newObjects = sourceIndex2NewTrajectory.flatMap { it.second }
        addObjects(newObjects) // row-column order
        var outputIndex = oldSize - 1
        sourceIndex2NewTrajectory.forEach { (sourceIndex, trajectory) ->
            if (objectColors.containsKey(sourceIndex)) {
                val sourceColor = objectColors[sourceIndex]!!
                trajectory.forEach { _ ->
                    outputIndex += 1
                    objectColors[outputIndex] = sourceColor
                }
            }
        }
        outputIndex = oldSize - 1
        sourceIndex2NewTrajectory.map { (ix, trajectory) ->
            trajectory.map { o ->
                outputIndex += 1
                if (o is CircleOrLine)
                    Pair(ix, outputIndex)
                else
                    null
            } // Column<Row<(OG Ix, new Ix)?>>
        }.transpose().forEach { trajectoryStageSlice ->
            // Column<(OG Ix, new Ix)>
            val nonNullSlice = trajectoryStageSlice.filterNotNull()
            // for each stage in the trajectory we try to copy regions
            if (nonNullSlice.isNotEmpty())
                copyRegions(
                    oldIndices = nonNullSlice.map { it.first },
                    newIndices = nonNullSlice.map { it.second },
                    flipInAndOut = flipRegionsInAndOut
                )
        }
        selection = (oldSize until objects.size).filter { ix ->
            objects[ix] is CircleOrLine || objects[ix] is Point
        }
        circleAnimationInit(newObjects.filterIsInstance<CircleOrLine>())?.let { circleAnimation ->
            viewModelScope.launch {
                _animations.emit(circleAnimation)
            }
        }
    }

    /** Add objects from [sourceIndex2NewObject] to [objects], while
     * copying regions (for [CircleOrLine]s) and [objectColors] from original
     * indices specified in [sourceIndex2NewObject].
     * We assume that appropriate expressions were/will be created separately and
     * that those expressions follow the order of [sourceIndex2NewObject], but
     * the objects themselves are yet to be added to [objects]. In addition set
     * new objects that are circles/lines/points as [selection].
     * @param[sourceIndex2NewObject] `[(original index ~ style source, new object)]`, note that
     * original indices CAN repeat (tho its regions will be copied only once even for the repeats).
     * @param[circleAnimationInit] given list of circles/lines queue [CircleAnimation]
     * constructed by this block. Use `{ null }` if no animation is required.
     * */
    private inline fun copyRegionsAndStyles(
        sourceIndex2NewObject: List<Pair<Ix, GCircle?>>,
        flipRegionsInAndOut: Boolean = false,
        crossinline circleAnimationInit: (List<CircleOrLine>) -> CircleAnimation? = { null },
    ) {
        val sourceIndices = sourceIndex2NewObject.map { it.first }
        @Suppress("UNCHECKED_CAST")
        val ix2circle: List<Pair<Ix, CircleOrLine>> = sourceIndex2NewObject
            .filter { it.second is CircleOrLine } // filterIsInstance doesn't work on Pair<*, *> (Java type erasure)
                as List<Pair<Ix, CircleOrLine>>
        val sourceIndicesOfNewCircles = ix2circle.map { it.first }
        val oldSize = objects.size
        addObjects(sourceIndex2NewObject.map { it.second })
        for ((i, sourceIndex) in sourceIndices.withIndex()) {
            if (objectColors.containsKey(sourceIndex)) {
                val correspondingIx = oldSize + i
                objectColors[correspondingIx] = objectColors[sourceIndex]!!
            }
        }
        val newIndicesOfCircles = sourceIndex2NewObject
            .filterIndices { (_, o) -> o is CircleOrLine }
            .map { oldSize + it }
        copyRegions(
            oldIndices = sourceIndicesOfNewCircles,
            newIndices = newIndicesOfCircles,
            flipInAndOut = flipRegionsInAndOut
        )
        selection = (oldSize until objects.size).filter { ix ->
            objects[ix] is CircleOrLine || objects[ix] is Point
        }
        circleAnimationInit(ix2circle.map { it.second })?.let { circleAnimation ->
            viewModelScope.launch {
                _animations.emit(circleAnimation)
            }
        }
    }

    fun duplicateSelectedCircles() {
        if (mode.isSelectingCircles()) {
            // pre-sorting is mandatory for expression copying to work properly
            val toBeCopied = expressions.sortedByTier(
                selection.filter { objects[it] is CircleOrLine || objects[it] is Point }
            )
            if (toBeCopied.isNotEmpty()) {
                recordCommand(Command.DUPLICATE, toBeCopied)
                copyRegionsAndStyles(toBeCopied.map { it to objects[it] }) { circles ->
                    CircleAnimation.ReEntrance(circles)
                }
                expressions.copyExpressionsWithDependencies(toBeCopied)
            }
        }
    }

    /**
     * Copy [LogicalRegion]s defined by [oldIndices] onto [newIndices].
     * [oldIndices].size must be [newIndices].size
     * @param[flipInAndOut] when `true` new region's insides use old region's outsides and
     * and vice versa
     * */
    private fun copyRegions(
        oldIndices: List<Ix>,
        newIndices: List<Ix>,
        flipInAndOut: Boolean = false,
    ) {
        require(oldIndices.size == newIndices.size) { "Original size doesn't match target size during copyRegions($oldIndices, $newIndices, $flipInAndOut)" }
        val old2new = oldIndices.zip(newIndices).toMap()
        val newRegions = regions.filter {
            oldIndices.containsAll(it.insides) && oldIndices.containsAll(it.outsides)
        }.map { region ->
            val newInsides: Set<Ix>
            val newOutsides: Set<Ix>
            if (flipInAndOut) {
                newInsides = region.outsides.map { old2new[it]!! }.toSet()
                newOutsides = region.insides.map { old2new[it]!! }.toSet()
            } else {
                newInsides = region.insides.map { old2new[it]!! }.toSet()
                newOutsides = region.outsides.map { old2new[it]!! }.toSet()
            }
            LogicalRegion(
                insides = newInsides,
                outsides = newOutsides,
                fillColor = region.fillColor
            )
        }
        regions.addAll(newRegions)
    }

    fun deleteSelectedPointsAndCircles() {
        if (showCircles && selection.isNotEmpty() && mode.isSelectingCircles()) {
            recordCommand(Command.DELETE, unique = true)
            val toBeDeleted = expressions.deleteNodes(selection)
            val deletedCircleIndices = toBeDeleted
                .filter { objects[it] is CircleOrLine }
                .toSet()
            if (deletedCircleIndices.isNotEmpty()) {
                val everythingIsDeleted = deletedCircleIndices.containsAll(objects.filterIndices { it is CircleOrLine })
                val oldRegions = regions.toList()
                regions.clear()
                if (!everythingIsDeleted) {
                    regions.addAll(
                        oldRegions
                            // to avoid stray chessboard selections
                            .filterNot { (ins, _, _) ->
                                ins.isNotEmpty() && ins.minus(deletedCircleIndices).isEmpty()
                            }
                            .map { (ins, outs, fillColor) ->
                                LogicalRegion(
                                    insides = ins.minus(deletedCircleIndices),
                                    outsides = outs.minus(deletedCircleIndices),
                                    fillColor = fillColor
                                )
                            }
                            .filter { (ins, outs) -> ins.isNotEmpty() || outs.isNotEmpty() }
                    )
                } else {
                    if (chessboardPattern == ChessboardPattern.STARTS_COLORED)
                        chessboardPattern = ChessboardPattern.STARTS_TRANSPARENT
                }
                val deletedCircles = deletedCircleIndices.mapNotNull { objects[it] as? CircleOrLine }
                viewModelScope.launch {
                    _animations.emit(
                        CircleAnimation.Exit(deletedCircles)
                    )
                }
            }
            removeObjects(toBeDeleted.toList())
            selection = emptyList()
        }
    }

    fun getArg(arg: Arg.Point): Point? =
        when (arg) {
            is Arg.Point.Index -> objects[arg.index] as? Point
            is Arg.Point.XY -> arg.toPoint()
        }

    fun getArg(arg: Arg.CircleOrPoint): GCircle? =
        when (arg) {
            is Arg.CircleOrPoint.Point.Index -> objects[arg.index]
            is Arg.CircleOrPoint.Point.XY -> arg.toPoint()
            is Arg.CircleOrPoint.CircleIndex -> objects[arg.index]
        }

    fun switchToMode(newMode: Mode) {
        // NOTE: these altering shortcuts are unused for now so that they don't confuse category-expand buttons
        if (selection.size > 1 && newMode == SelectionMode.Drag)
            selection = emptyList()
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
        partialArcPath = null
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
            .filter { (ix, _) -> showPhantomObjects || ix !in phantoms }
            .minByOrNull { (ix, distance) ->
                val priority =
                    if (ix in priorityTargets) 100
                    else 1
                distance / priority
            }
            ?.let { (ix, _) -> ix }
            ?.also { println("select point #$it: ${objects[it]} <- ${expressions.expressions[it]}") }
    }

    /** [selectPoint] around [visiblePosition] while prioritizing free points */
    fun selectPointAt(visiblePosition: Offset): Ix? {
        val points = objects.map { it as? Point }
        val nearPointIndex = selectPoint(points, visiblePosition,
            priorityTargets = points.indices
                .filter { expressions.expressions[it] == null }
                .toSet()
        )
        return nearPointIndex
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
            .filter { (ix, _) -> showPhantomObjects || ix !in phantoms }
            .minByOrNull { (ix, distance) ->
                val priority =
                    if (ix in priorityTargets) 100
                    else 1
                distance / priority
            }
            ?.let { (ix, _) -> ix }
            ?.also {
                // NOTE: this is printed twice when tapping on a circle in
                //  drag mode, since both onDown & onTap trigger it once
                println("select circle #$it: ${objects[it]} <- expr: ${expressions.expressions[it]}")
            }
    }

    /** [selectCircle] around [visiblePosition] while prioritizing free circles */
    fun selectCircleAt(visiblePosition: Offset): Ix? {
        val circles = objects.map { it as? CircleOrLine }
        val nearCircleIndex = selectCircle(circles, visiblePosition,
            priorityTargets = circles.indices
                .filter { expressions.expressions[it] == null }
                .toSet()
        )
        return nearCircleIndex
    }

    /** [selectCircle] & add/remove it from selection if it's new/already in */
    fun xorSelectCircleAt(visiblePosition: Offset): Ix? {
        val circles = objects.map { it as? CircleOrLine }
        return selectCircle(circles, visiblePosition)?.also { ix ->
            if (ix in selection)
                selection -= ix
            else
                selection += ix
        }
    }

    private fun findSiblingsAndParents(ix: Ix): List<Ix> {
        val e = expressions.expressions[ix] ?: return emptyList()
        val parents = e.expr.args
        val siblings = expressions.findExpr(e.expr)
        return siblings + parents
    }

    // NOTE: region boundaries get messed up when we alter a big structure like spiral
    /** @return (compressed region, verbose region involving all circles) surrounding clicked position */
    private fun selectRegionAt(
        visiblePosition: Offset,
        boundingCircles: List<Ix>? = null
    ): Pair<LogicalRegion, LogicalRegion> {
        val position = absolute(visiblePosition)
        val delimiters = boundingCircles ?:
            objects.filterIndices { it is CircleOrLine }
                .filter { showPhantomObjects || it !in phantoms }
        val ins = delimiters // NOTE: doesn't include circles that the point lies on
            .filter { ix -> (objects[ix] as? CircleOrLine)?.hasInside(position) ?: false }
        val outs = delimiters
            .filter { ix -> (objects[ix] as? CircleOrLine)?.hasOutside(position) ?: false }
        val circles = objects.map { it as? CircleOrLine }
        val (essentialIns, essentialOuts) =
            compressConstraints(circles, ins, outs)
        val region0 = LogicalRegion(ins.toSet(), outs.toSet(), regionColor)
        val region = LogicalRegion(
//            insides = ins.toSet(),
//            outsides = outs.toSet(),
            insides = essentialIns,
            outsides = essentialOuts,
            fillColor = regionColor
        )
        return Pair(region, region0)
    }

    fun reselectRegionAt(
        visiblePosition: Offset,
        boundingCircles: List<Ix>? = null,
        setSelectionToRegionBounds: Boolean = false
    ) {
        val shouldUpdateSelection = setSelectionToRegionBounds && !restrictRegionsToSelection
        val (region, region0) = selectRegionAt(visiblePosition, boundingCircles)
        val outerRegionsIndices = regions.filterIndices { region isObviouslyInside it || region0 isObviouslyInside it  }
        val outerRegions = outerRegionsIndices.map { regions[it] }
        val sameBoundsRegionsIndices = outerRegionsIndices.filter {
            regions[it].insides == region.insides && regions[it].outsides == region.outsides
        }
        val sameBoundsRegions = sameBoundsRegionsIndices.map { regions[it] }
        when (regionManipulationStrategy) {
            RegionManipulationStrategy.REPLACE -> {
                if (outerRegions.isEmpty()) {
                    recordCommand(Command.FILL_REGION, target = regions.size)
                    regions.add(region)
                    if (shouldUpdateSelection) {
                        selection = (region.insides + region.outsides).toList()
                    }
                    println("added $region")
                } else if (outerRegions.size == 1) {
                    val i = outerRegionsIndices.single()
                    val outer = outerRegions.single()
                    if (region.fillColor == outer.fillColor) {
                        recordCommand(Command.FILL_REGION, unique = true)
                        regions.removeAt(i)
                        println("removed singular same-color outer $outer")
                    } else { // we are trying to change the color im guessing
                        recordCommand(Command.FILL_REGION, target = i)
                        regions[i] = outer.copy(fillColor = region.fillColor)
                        if (shouldUpdateSelection) {
                            selection = (region.insides + region.outsides).toList()
                        }
                        println("recolored singular $outer")
                    }
                } else if (sameBoundsRegionsIndices.isNotEmpty()) {
                    val sameBoundsSameColorRegionsIndices = sameBoundsRegionsIndices.filter {
                        regions[it].fillColor == region.fillColor
                    }
                    if (sameBoundsSameColorRegionsIndices.isNotEmpty()) {
                        recordCommand(Command.FILL_REGION, unique = true)
                        val sameRegions = sameBoundsSameColorRegionsIndices.map { regions[it] }
                        regions.removeAll(sameRegions)
                        println("removed all same-bounds same-color $sameBoundsSameColorRegionsIndices ~ $region")
                    } else { // we are trying to change the color im guessing
                        val i = sameBoundsRegionsIndices.last()
                        if (sameBoundsRegionsIndices.size == 1)
                            recordCommand(Command.FILL_REGION, target = i)
                        else // cleanup can shift region index
                            recordCommand(Command.FILL_REGION, unique = true)
                        regions[i] = region
                        sameBoundsRegions
                            .dropLast(1)
                            .forEach {
                                regions.remove(it) // cleanup
                            }
                        if (shouldUpdateSelection) {
                            selection = (region.insides + region.outsides).toList()
                        }
                        println("recolored $i (same bounds ~ $region)")
                    }
                } else {
                    // NOTE: click on overlapping region: contested behaviour
                    val outerRegionsOfTheSameColor = outerRegions.filter { it.fillColor == region.fillColor }
                    if (outerRegionsOfTheSameColor.isNotEmpty()) {
                        recordCommand(Command.FILL_REGION, unique = true)
                        // NOTE: this removes regions of the same color that lie under
                        //  others (potentially invisible), which can be counter-intuitive
                        regions.removeAll(outerRegionsOfTheSameColor)
                        println("removed same color regions [${outerRegionsOfTheSameColor.joinToString(prefix = "\n", separator = ";\n")}]")
                    } else { // there are several outer regions, but none of the color of region.fillColor
                        recordCommand(Command.FILL_REGION, target = regions.size)
                        regions.add(region)
                        if (shouldUpdateSelection) {
                            selection = (region.insides + region.outsides).toList()
                        }
                        println("added $region")
                    }
                }
            }
            RegionManipulationStrategy.ADD -> {
                if (sameBoundsRegionsIndices.isEmpty()) {
                    recordCommand(Command.FILL_REGION, target = regions.size)
                    regions.add(region)
                    if (shouldUpdateSelection) {
                        selection = (region.insides + region.outsides).toList()
                    }
                    println("added $region")
                } else if (sameBoundsRegions.last().fillColor == region.fillColor) {
                    // im gonna cleanup same bounds until only 1 is left
                    // cleanup & skip
                    sameBoundsRegions
                        .dropLast(1)
                        .forEach {
                            regions.remove(it) // cannot use removeAll cuz it could remove the last one too
                        }
                } else { // same bounds, different color
                    // replace & cleanup
                    val i = sameBoundsRegionsIndices.last()
                    if (sameBoundsRegionsIndices.size == 1)
                        recordCommand(Command.FILL_REGION, target = i)
                    else
                        recordCommand(Command.FILL_REGION, unique = true)
                    regions[i] = region
                    if (shouldUpdateSelection) {
                        selection = (region.insides + region.outsides).toList()
                    }
                    sameBoundsRegions
                        .dropLast(1)
                        .forEach {
                            regions.remove(it) // cannot use removeAll cuz it could remove the last one too
                        }
                    println("recolored $i (same bounds ~ $region)")
                }
            }
            RegionManipulationStrategy.ERASE -> {
                if (sameBoundsRegions.isNotEmpty()) {
                    recordCommand(Command.FILL_REGION, unique = true)
                    regions.removeAll(sameBoundsRegions)
                    println("removed [${sameBoundsRegionsIndices.joinToString(prefix = "\n", separator = ";\n")}] (same bounds ~ $region)")
                } else if (outerRegions.isNotEmpty()) {
                    // maybe find minimal and erase it OR remove last outer
                    // tho it would stop working like eraser then
                    recordCommand(Command.FILL_REGION, unique = true)
                    regions.removeAll(outerRegions)
                    println("removed outer [${outerRegions.joinToString(prefix = "\n", separator = ";\n")}]")
                } // when clicking on nowhere nothing happens
            }
        }
    }

    /** @return [Rect] using absolute positions */
    @Suppress("NOTHING_TO_INLINE")
    inline fun getSelectionRect(): Rect? {
        val selectedCircles = selection.mapNotNull { objects[it] as? Circle }
        if (selectedCircles.isEmpty() || selection.any { objects[it] is Line })
            return null
        val left = selectedCircles.minOf { (it.x - it.radius).toFloat() }
        val right = selectedCircles.maxOf { (it.x + it.radius).toFloat() }
        val top = selectedCircles.minOf { (it.y - it.radius) }.toFloat()
        val bottom = selectedCircles.maxOf { (it.y + it.radius) }.toFloat()
        return Rect(left, top, right, bottom)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun isFree(circleIndex: Ix): Boolean =
        expressions.expressions[circleIndex] == null

    fun isConstrained(index: Ix): Boolean =
        expressions.expressions[index]?.expr is Expr.Incidence

    // MAYBE: wrap into state that depends only on [regions] for caching
    fun getColorsByMostUsed(): List<Color> =
        regions.flatMap { region ->
            region.borderColor?.let { listOf(region.fillColor, it) }
                ?: listOf(region.fillColor)
        }.plus(chessboardColor)
        .sortedByFrequency()

    /** Try to snap [absolutePosition] to some existing object or their intersection.
     * Snap priority: points > circles
     * */
    fun snapped(
        absolutePosition: Offset,
        excludePoints: Boolean = false,
        excludedCircles: Set<Ix> = emptySet(),
    ): PointSnapResult {
        val snapDistance = tapRadius.toDouble()
        val point = Point.fromOffset(absolutePosition)
        val point2pointSnapping = !excludePoints && mode != ToolMode.POINT
        if (point2pointSnapping) {
            val snappablePoints = objects.map { it as? Point }
            val p2pResult = snapPointToPoints(point, snappablePoints, snapDistance)
            if (p2pResult is PointSnapResult.Eq)
                return p2pResult
        }
        val point2circleSnapping = showCircles
        if (!point2circleSnapping) // no snapping to invisibles
            return PointSnapResult.Free(point)
        val snappableCircles = objects.mapIndexed { ix, c ->
            if (ix in excludedCircles) null
            else c as? CircleOrLine
        }
        val p2cResult = snapPointToCircles(point, snappableCircles, snapDistance)
        return p2cResult
    }

    /** Adds a new point(s) with expression defined by [snapResult] when non-free
     * @return the same [snapResult] if [snapResult] is [PointSnapResult.Free], otherwise
     * [PointSnapResult.Eq] that points to the newly added point */
    private fun realizePointCircleSnap(snapResult: PointSnapResult): PointSnapResult.PointToPoint =
        when (snapResult) {
            is PointSnapResult.Free -> snapResult
            is PointSnapResult.Eq -> snapResult
            is PointSnapResult.Incidence -> {
                val circle = objects[snapResult.circleIndex] as CircleOrLine
                // NOTE: we have to downscale to measure order for lines properly
                val order = circle.downscale().point2order(snapResult.result.downscale())
                val expr = Expr.Incidence(
                    IncidenceParameters(order),
                    snapResult.circleIndex
                )
                recordCreateCommand()
                val newPoint = (expressions.addSoloExpr(expr) as Point).upscale()
                val newIx = objects.size
                addObjects(listOf(newPoint))
                PointSnapResult.Eq(newPoint, newIx)
            }
            is PointSnapResult.Intersection -> {
                val point = snapResult.result
                val (ix1, ix2) = listOf(snapResult.circle1Index, snapResult.circle2index)
                val expr = Expr.Intersection(ix1, ix2)
                val possibleExistingIntersections =
                    expressions.findExistingIntersectionIndices(ix1, ix2)
                        .filter { objects[it] is Point }
                val closestIndex = possibleExistingIntersections.minByOrNull {
                    val p = objects[it] as Point
                    p.distanceFrom(point)
                }
                val intersectionSnapDistance = 1.5 * tapRadius.toDouble() // magic number
                if (closestIndex != null &&
                    point.distanceFrom(objects[closestIndex] as Point) <= intersectionSnapDistance
                ) {
                    PointSnapResult.Eq(objects[closestIndex] as Point, closestIndex)
                } else {
                    // check if both outputIndices are present, if not add the other
                    val oldSize = objects.size
                    recordCommand(Command.CREATE, unique = true)
                    val intersectionOutputIndex = computeIntersection(
                        objects[ix1] as CircleOrLine,
                        objects[ix2] as CircleOrLine
                    ).withIndex().minBy { (_, p) ->
                        p?.let { point.distanceFrom(p) } ?: Double.POSITIVE_INFINITY
                    }.index
                    if (closestIndex != null) { // far intersection already exists
                        val p = expressions.addMultiExpression(Expression.OneOf(expr, intersectionOutputIndex))
                            as Point
                        addObject(p.upscale())
                        PointSnapResult.Eq(snapResult.result, oldSize)
                    } else {
                        val ps = expressions.addMultiExpr(expr)
                            .map { it as? Point }
                            .map { it?.upscale() }
                        addObjects(ps)
                        PointSnapResult.Eq(snapResult.result, oldSize + intersectionOutputIndex)
                    }
                }
            }
        }

    fun activateRectangularSelect() {
        switchToMode(SelectionMode.Multiselect)
        selection = emptyList()
        submode = SubMode.RectangularSelect()
    }

    fun activateFlowSelect() {
        switchToMode(SelectionMode.Multiselect)
        selection = emptyList()
        submode = SubMode.FlowSelect()
    }

    fun activateFlowFill() {
        switchToMode(SelectionMode.Region)
        submode = SubMode.FlowFill()
    }

    fun toggleSelectAll() {
        switchToMode(SelectionMode.Multiselect)
        showCircles = true
        val everythingIsSelected = selection.containsAll(
            objects.filterIndices { it is CircleOrLine }
                .filter { showPhantomObjects || it !in phantoms }
        )
        selection = if (everythingIsSelected) {
            emptyList()
        } else { // maybe select Imaginary's and nulls too
            objects.filterIndices { it is Point || it is CircleOrLine }
        }
    }

    fun toggleShowCircles() {
        showCircles = !showCircles
        if (!showCircles && mode is ToolMode)
            switchToMode(SelectionMode.Drag)
        selection = emptyList()
    }

    fun togglePhantomObjects() {
        showPhantomObjects = !showPhantomObjects
        if (phantoms.isEmpty())
            queueSnackbarMessage(SnackbarMessage.PHANTOM_OBJECT_EXPLANATION)
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

    fun toggleChessboardPattern() {
        recordCommand(Command.FILL_REGION)
        chessboardPattern = when (chessboardPattern) {
            ChessboardPattern.NONE -> ChessboardPattern.STARTS_COLORED
            ChessboardPattern.STARTS_COLORED -> ChessboardPattern.STARTS_TRANSPARENT
            ChessboardPattern.STARTS_TRANSPARENT -> ChessboardPattern.NONE
        }
        if (chessboardPattern != ChessboardPattern.NONE) {
            chessboardColor = regionColor
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
        selection
            .mapNotNull { objectColors[it] }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { (_, k) -> k }
            ?.key

    fun setNewCircleColor(color: Color) {
        recordCommand(Command.CHANGE_COLOR)
        for (ix in selection) {
            objectColors[ix] = color
        }
        openedDialog = null
    }

    fun dismissCircleColorPicker() {
        openedDialog = null
    }

    fun setNewBackgroundColor(color: Color) {
        recordCommand(Command.CHANGE_COLOR)
        backgroundColor = color
        openedDialog = null
    }

    fun dismissBackgroundColorPicker() {
        openedDialog = null
    }

    // MAYBE: replace with select-all->delete in invisible-circles region manipulation mode
    fun deleteAllRegions() {
        recordCommand(Command.DELETE, unique = true)
        chessboardPattern = ChessboardPattern.NONE
        regions.clear()
    }

    fun setRegionsManipulationStrategy(newStrategy: RegionManipulationStrategy) {
        regionManipulationStrategy = newStrategy
    }

    fun cancelSelectionAsToolArgPrompt() {
        if (showPromptToSetActiveSelectionAsToolArg) {
            showPromptToSetActiveSelectionAsToolArg = false
            selection = emptyList()
        }
    }

    fun setActiveSelectionAsToolArg() {
        toolbarState.activeTool.let { tool ->
            require(
                tool is EditClusterTool.MultiArg &&
                tool.signature.argTypes.first() == ArgType.CircleAndPointIndices &&
                selection.isNotEmpty()
            ) { "Illegal state in setActiveSelectionAsToolArg(): tool = $tool, selection == $selection" }
        }
        partialArgList = partialArgList!!.addArg(
            Arg.CircleAndPointIndices(
                circleIndices = selection.filter { objects[it] is CircleOrLine },
                pointIndices = selection.filter { objects[it] is Point },
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
        createNewGCircles(listOf(horizontalLine, verticalLine))
        expressions.addFree()
        expressions.addFree()
        switchToMode(SelectionMode.Multiselect)
        selection = listOf(objects.size - 2, objects.size - 1)
    }

    fun scaleSelection(zoom: Float) {
        if (showCircles && mode.isSelectingCircles() && selection.isNotEmpty()) {
            val rect = getSelectionRect()
            val focus =
                if (selection.size == 1 && isFree(selection.single()))
                    Offset.Unspecified
                else if (rect == null || rect.minDimension >= 5_000)
                    computeAbsoluteCenter() ?: Offset.Zero
                else rect.center
            transformWhatWeCan(selection, focus = focus, zoom = zoom)
        } else if (mode == ToolMode.ARC_PATH && partialArcPath != null) {
//            arcPathUnderConstruction = arcPathUnderConstruction?.scale(zoom)
        } else { // NOTE: scaling everything instead of canvas can produce more artifacts
            val targets = objects.indices.toList()
            val center = computeAbsoluteCenter() ?: Offset.Zero
            // no need to recompute expressions here
            transform(targets, focus = center, zoom = zoom, updateExpressions = {})
        }
    }

    private fun detachEverySelectedObject() {
        recordCommand(Command.CHANGE_EXPRESSION)
        for (ix in selection) {
            expressions.changeToFree(ix)
        }
        selectionIsLockedTrigger = !selectionIsLockedTrigger
    }

    private fun markSelectedObjectsAsPhantoms() {
        phantoms = phantoms + selection
        // selection = emptyList() // being able to instantly undo is prob better ux
        // showPhantomObjects = false // i think this behavior is confuzzling
    }

    private fun unmarkSelectedObjectsAsPhantoms() {
        phantoms = phantoms - selection.toSet()
    }

    private fun swapDirectionsOfSelectedCircles() {
        for (ix in selection) { // no recording (idk why)
            val obj = objects[ix]
            if (obj is CircleOrLine && isFree(ix)) {
                objects[ix] = obj.reversed()
            }
        }
        expressions.update(selection)
    }

    fun onDown(visiblePosition: Offset) {
        dragDownedWithNothing = false
        movementAfterDown = false
        // reset grabbed thingies
        if (showCircles) {
            when (val h = handleConfig) {
                is HandleConfig.SingleCircle -> {
                    val circle = objects[h.ix]
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
            if (selection.any { objects[it] is CircleOrLine } && mode.isSelectingCircles() && submode is SubMode.None) {
                val screenCenter = absolute(Offset(canvasSize.width/2f, canvasSize.height/2f))
                when {
                    isCloseEnoughToSelect(
                        absolutePosition = absolute(selectionControlsPositions.sliderMiddleOffset),
                        visiblePosition = visiblePosition,
                        lowAccuracy = true
                    ) -> {
                        submode = SubMode.ScaleViaSlider(screenCenter)
                    }
                    isCloseEnoughToSelect(
                        absolutePosition = absolute(selectionControlsPositions.rotationHandleOffset),
                        visiblePosition = visiblePosition,
                        lowAccuracy = true
                    ) -> {
                        submode = SubMode.Rotate(screenCenter)
                    }
                }
            }
            // NOTE: this enables drag-only behavior, you lose your selection when grabbing new circle
            if (mode == SelectionMode.Drag && submode is SubMode.None) {
                val selectedPointIndex = selectPointAt(visiblePosition)
                if (selectedPointIndex != null) {
                    selection = listOf(selectedPointIndex)
                } else {
                    val previouslySelectedCircle = selection.firstOrNull()?.let { ix ->
                        if (objects[ix] is CircleOrLine)
                            ix
                        else null
                    }
                    val selectedCircleIndex = selectCircleAt(visiblePosition)
                    selection = if (previouslySelectedCircle != null && selectedCircleIndex == null) {
                        dragDownedWithNothing = true
                        listOf(previouslySelectedCircle)
                        // we keep previous selection in case we want to drag it
                        // but it can still be discarded in onTap
                    } else {
                        listOfNotNull(selectedCircleIndex)
                    }
                }
            } else {
                if (mode == SelectionMode.Multiselect && submode is SubMode.RectangularSelect) {
                    val (corner1, corner2) = submode as SubMode.RectangularSelect
                    submode = if (corner1 == null) {
                        SubMode.RectangularSelect(absolute(visiblePosition))
                    } else if (corner2 == null) {
                        SubMode.RectangularSelect(corner1, absolute(visiblePosition))
                    } else {
                        SubMode.RectangularSelect(absolute(visiblePosition))
                    }
                } else if (mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect) {
                    val (_, qualifiedRegion) = selectRegionAt(visiblePosition)
                    submode = SubMode.FlowSelect(qualifiedRegion)
                } else if (mode == SelectionMode.Region && submode is SubMode.FlowFill) {
                    val (_, qualifiedRegion) = selectRegionAt(visiblePosition)
                    submode = SubMode.FlowFill(qualifiedRegion)
                    val selectedCircles = selection.filter { objects[it] is CircleOrLine }
                    if (restrictRegionsToSelection && selectedCircles.isNotEmpty()) {
                        reselectRegionAt(visiblePosition, selectedCircles)
                    } else {
                        reselectRegionAt(visiblePosition)
                    }
                } else if (mode is ToolMode) {
                    val pArgList = partialArgList
                    when (pArgList?.nextArgType) {
                        ArgType.Point -> {
                            val result = snapped(absolute(visiblePosition))
                            if (FAST_CENTERED_CIRCLE && mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS && pArgList.currentArg == null) {
                                // we have to realize the first point here so we don't forget its
                                // snap after panning
                                val newArg = realizePointCircleSnap(result).toArgPoint()
                                val newArg2 = Arg.Point.XY(result.result)
                                partialArgList = pArgList
                                    .addArg(newArg, confirmThisArg = true)
                                    .addArg(newArg2, confirmThisArg = false)
                                    .copy(lastSnap = result)
                            } else {
                                val newArg = when (result) {
                                    is PointSnapResult.Eq -> Arg.Point.Index(result.pointIndex)
                                    else -> Arg.Point.XY(result.result)
                                }
                                partialArgList = pArgList
                                    .addArg(newArg, confirmThisArg = false)
                                    .copy(lastSnap = result)
                            }
                        }
                        ArgType.Circle -> {
                            val circles = objects.map { it as? CircleOrLine }
                            selectCircle(circles, visiblePosition)?.let { circleIndex ->
                                val newArg = Arg.CircleIndex(circleIndex)
                                val previous = pArgList.currentArg
                                val allowRepeatingArg =
                                    // we allow target == engine1, but NOT engine1 == engine2
                                    mode == ToolMode.BI_INVERSION && pArgList.args.size == 1
                                val repeatingArg =
                                    previous == newArg ||
                                    previous is Arg.CircleIndex && previous.index == circleIndex ||
                                    previous is Arg.CircleAndPointIndices && previous.circleIndices == listOf(circleIndex)
                                // we ignore identical args (tho there can be a reason not to)
                                if (allowRepeatingArg || !repeatingArg) {
                                    partialArgList = pArgList.addArg(newArg, confirmThisArg = true)
                                }
                            }
                        }
                        ArgType.CircleOrPoint -> {
                            val result = snapped(absolute(visiblePosition))
                            if (result is PointSnapResult.Eq) {
                                val newArg = Arg.CircleOrPoint.Point.Index(result.pointIndex)
                                val sameArgsForInterpolation =
                                    (mode == ToolMode.CIRCLE_INTERPOLATION) entails
                                    (pArgList.args.isEmpty() || pArgList.currentArg is Arg.CircleOrPoint.Point)
                                if (pArgList.currentArg != newArg && sameArgsForInterpolation) {
                                    partialArgList = pArgList
                                        .addArg(newArg, confirmThisArg = false)
                                        .copy(lastSnap = result)
                                }
                            } else if (mode == ToolMode.CIRCLE_INTERPOLATION && pArgList.currentArg is Arg.CircleOrPoint.Point) {
                                val newArg = Arg.CircleOrPoint.Point.XY(result.result)
                                partialArgList = pArgList
                                    .addArg(newArg, confirmThisArg = false)
                                    .copy(lastSnap = result)
                            } else {
                                val circles = objects.map { it as? CircleOrLine }
                                val circleIndex = selectCircle(circles, visiblePosition)
                                if (circleIndex != null) {
                                    val newArg = Arg.CircleOrPoint.CircleIndex(circleIndex)
                                    if (pArgList.currentArg != newArg) {
                                        partialArgList = pArgList.addArg(newArg, confirmThisArg = false)
                                    }
                                } else {
                                    val newArg = Arg.CircleOrPoint.Point.XY(result.result)
                                    partialArgList = pArgList
                                        .addArg(newArg, confirmThisArg = false)
                                        .copy(lastSnap = result)
                                }
                            }
                        }
                        ArgType.CircleAndPointIndices -> {
                            val points = objects.map { it as? Point }
                            val selectedPointIndex = selectPoint(points, visiblePosition)
                            if (selectedPointIndex == null) {
                                val circles = objects.map { it as? CircleOrLine }
                                val selectedCircleIndex = selectCircle(circles, visiblePosition)
                                if (selectedCircleIndex != null) {
                                    val newArg = Arg.CircleAndPointIndices(
                                        circleIndices = listOf(selectedCircleIndex),
                                        pointIndices = emptyList()
                                    )
                                    if (pArgList.currentArg != newArg)
                                        partialArgList = pArgList.addArg(newArg, confirmThisArg = true)
                                }
                            } else {
                                val newArg = Arg.CircleAndPointIndices(
                                    circleIndices = emptyList(),
                                    pointIndices = listOf(selectedPointIndex)
                                )
                                if (pArgList.currentArg != newArg)
                                    partialArgList = pArgList.addArg(newArg, confirmThisArg = true)
                            }
                        }
                        else -> if (mode == ToolMode.ARC_PATH) {
                            val absolutePoint = snapped(absolute(visiblePosition)).result
                            val arcPath = partialArcPath
                            partialArcPath = if (arcPath == null) {
                                PartialArcPath(
                                    startVertex = ArcPathPoint.Free(absolutePoint),
                                    focus = PartialArcPath.Focus.StartPoint
                                )
                            } else {
                                if (isCloseEnoughToSelect(arcPath.startVertex.point.toOffset(), visiblePosition)) {
                                    arcPath.copy(focus = PartialArcPath.Focus.StartPoint)
                                } else {
                                    val pointIx = arcPath.vertices.indexOfFirst {
                                        isCloseEnoughToSelect(it.point.toOffset(), visiblePosition)
                                    }
                                    if (pointIx != -1) {
                                        arcPath.copy(focus = PartialArcPath.Focus.Point(pointIx))
                                    } else {
                                        val midpointIx = arcPath.midpoints.indexOfFirst {
                                            isCloseEnoughToSelect(it.toOffset(), visiblePosition)
                                        }
                                        if (midpointIx != -1) {
                                            arcPath.copy(focus = PartialArcPath.Focus.MidPoint(midpointIx))
                                        } else {
                                            arcPath.addNewVertex(ArcPathPoint.Free(absolutePoint))
                                                .copy(focus = PartialArcPath.Focus.Point(arcPath.vertices.size))
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
    // onDown -> onUp -> onTap OR
    // onDown -> onUp -> onDown! -> onTap -> onUp
    fun onTap(position: Offset, pointerCount: Int) {
//        println("onTap(pointerCount = $pointerCount)")
        // 2-finger tap for undo (works only on Android afaik)
        if (TWO_FINGER_TAP_FOR_UNDO && pointerCount == 2) {
            if (undoIsEnabled)
                undo()
        } else if (showCircles) { // select circle(s)/region
            when (mode) {
                SelectionMode.Drag -> {
                    var selectedIndex = selectPointAt(position)
                    selection = listOfNotNull(selectedIndex)
                    if (selectedIndex == null) {
                        selectedIndex = selectCircleAt(position)
                        selection = listOfNotNull(selectedIndex)
                        highlightSelectionParents()
                    }
                }
                SelectionMode.Multiselect -> {
                    // (re)-select region
                    val selectedCircleIndex = xorSelectCircleAt(position)
                    if (selectedCircleIndex == null) { // try to select bounding circles of the selected region
                        val (region, region0) = selectRegionAt(position)
                        if (region0.insides.isEmpty()) { // if we clicked outside of everything, toggle select all
                            toggleSelectAll()
                            if (!showPhantomObjects)
                                selection = selection.filter { it !in phantoms }
                        } else {
                            val selectedCircles = selection.filter { objects[it] is CircleOrLine }
                            regions
                                .withIndex()
                                .filter { (_, p) -> region isObviouslyInside p || region0 isObviouslyInside p }
                                .maxByOrNull { (_, p) -> p.insides.size + p.outsides.size }
                                ?.let { (_, existingRegion) ->
                                    println("existing bound of $existingRegion")
                                    val bounds: Set<Ix> = existingRegion.insides + existingRegion.outsides
                                    if (bounds != selectedCircles.toSet()) {
                                        selection = bounds.toList()
                                        highlightSelectionParents()
                                    } else {
                                        selection = emptyList()
                                    }
                                } ?: run { // select bound of a non-existent region
                                    println("bounds of $region")
                                    val bounds: Set<Ix> = region.insides + region.outsides
                                    if (bounds != selectedCircles.toSet()) {
                                        selection = bounds.toList()
                                        highlightSelectionParents()
                                    } else {
                                        selection = emptyList()
                                    }
                            }
                        }
                    } else {
                        if (selectedCircleIndex in selection)
                            highlightSelectionParents()
                    }
                }
                SelectionMode.Region -> {
                    if (submode !is SubMode.FlowFill) {
                        val selectedCircles = selection.filter { objects[it] is CircleOrLine }
                        if (restrictRegionsToSelection && selectedCircles.isNotEmpty()) {
                            reselectRegionAt(position, selectedCircles)
                        } else {
                            reselectRegionAt(position)
                        }
                    }
                }
                ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> {}
//                    if (FAST_CENTERED_CIRCLE && partialArgList!!.lastArgIsConfirmed) {
//                        partialArgList = partialArgList!!.copy(
//                            args = partialArgList!!.args.dropLast(1),
//                            lastSnap = null
//                        )
//                    }
                ToolMode.ARC_PATH -> {
                    // when 3+ points, tap on the start closes the loop
                }
                else -> {}
            }
        }
    }

    private fun scaleSingleCircle(c: Offset, zoom: Float, h: HandleConfig.SingleCircle, sm: SubMode.Scale) {
        val ix = h.ix
        val circle = objects[h.ix] as? CircleOrLine
        if (circle is Circle) {
            val center = sm.center
            val r = (c - center).getDistance()
            transformWhatWeCan(listOf(ix), focus = center, zoom = (r/circle.radius).toFloat())
        } else if (circle is Line) {
            val center = circle.project(c)
            transformWhatWeCan(listOf(ix), focus = center, zoom = zoom)
        }
    }

    private fun scaleViaSliderSingleCircle(pan: Offset, h: HandleConfig.SingleCircle, sm: SubMode.ScaleViaSlider) {
        val newPercentage = selectionControlsPositions.addPanToPercentage(sm.sliderPercentage, pan)
        if (sm.sliderPercentage != newPercentage) {
            val scaleFactor = sliderPercentageDeltaToZoom(newPercentage - sm.sliderPercentage)
            transformWhatWeCan(listOf(h.ix), focus = sm.center, zoom = scaleFactor)
            submode = sm.copy(sliderPercentage = newPercentage)
        }
    }

    private fun rotateSingleCircle(pan: Offset, c: Offset, h: HandleConfig.SingleCircle, sm: SubMode.Rotate) {
        val center = sm.center
        val centerToCurrent = c - center
        val centerToPreviousHandle = centerToCurrent - pan
        val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
        val newAngle = sm.angle + angle
        val snappedAngle =
            if (ENABLE_ANGLE_SNAPPING) snapAngle(newAngle)
            else newAngle
        val angle1 = (snappedAngle - sm.snappedAngle).toFloat()
        transformWhatWeCan(listOf(h.ix), focus = center, rotationAngle = angle1)
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
    }

    private fun scaleSeveralCircles(pan: Offset, targets: List<Ix>) {
        getSelectionRect()?.let { rect ->
            val scaleHandlePosition = rect.topRight
            val center = rect.center
            val centerToHandle = scaleHandlePosition - center
            val centerToCurrent = centerToHandle + pan
            val scaleFactor = centerToCurrent.getDistance()/centerToHandle.getDistance()
            transformWhatWeCan(targets, focus = center, zoom = scaleFactor)
        }
    }

    private fun scaleViaSliderSeveralCircles(pan: Offset, sm: SubMode.ScaleViaSlider, targets: List<Ix>) {
        val newPercentage = selectionControlsPositions.addPanToPercentage(sm.sliderPercentage, pan)
        if (sm.sliderPercentage != newPercentage) {
            val scaleFactor = sliderPercentageDeltaToZoom(newPercentage - sm.sliderPercentage)
            transformWhatWeCan(targets, focus = sm.center, zoom = scaleFactor)
            submode = sm.copy(sliderPercentage = newPercentage)
        }
    }

    private fun rotateSeveralCircles(pan: Offset, c: Offset, sm: SubMode.Rotate, targets: List<Ix>) {
        val center = sm.center
        val centerToCurrent = c - center
        val centerToPreviousHandle = centerToCurrent - pan
        val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
        val newAngle = sm.angle + angle
        val snappedAngle =
            if (ENABLE_ANGLE_SNAPPING) snapAngle(newAngle)
            else newAngle
        val angle1 = (snappedAngle - sm.snappedAngle).toFloat()
        transformWhatWeCan(targets, focus = sm.center, rotationAngle = angle1)
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
    }

    // dragging circle: move + scale radius
    private fun dragCircle(pan: Offset, zoom: Float, rotationAngle: Float) {
        val ix = selection.single()
        transformWhatWeCan(listOf(ix), translation = pan, zoom = zoom, rotationAngle = rotationAngle)
    }

    private fun dragPoint(c: Offset) {
        val ix = selection.first()
        val expr = expressions.expressions[ix]?.expr
        if (expr is Expr.Incidence) {
            slidePointAcrossCarrier(pointIndex = ix, carrierIndex = expr.carrier, cursorLocation = c)
        } else {
            val childCircles = expressions.getAllChildren(ix)
                .filter { objects[it] is CircleOrLine }
                .toSet()
            // when we are dragging intersection of 2 frees with IoC1 we don't want it to snap to them
            val parents = expressions.getAllParents(listOf(ix))
            val newPoint = snapped(c, excludePoints = true, excludedCircles = childCircles + parents).result
            transformWhatWeCan(
                listOf(ix),
                translation = newPoint.toOffset() - (objects[ix] as Point).toOffset(),
            )
        }
    }

    // special case that is not handled by transform()
    private fun slidePointAcrossCarrier(pointIndex: Ix, carrierIndex: Ix, cursorLocation: Offset) {
        recordCommand(Command.MOVE, target = pointIndex)
        val carrier = objects[carrierIndex] as CircleOrLine
        val newPoint = carrier.project(Point.fromOffset(cursorLocation))
        val order = carrier.downscale().point2order(newPoint.downscale())
        val newExpr = Expr.Incidence(IncidenceParameters(order), carrierIndex)
        objects[pointIndex] = newPoint
        expressions.changeExpression(pointIndex, newExpr)
        expressions.update(listOf(pointIndex))
    }

    private fun dragCirclesOrPoints(pan: Offset, c: Offset, zoom: Float, rotationAngle: Float) {
        val targets = selection.filter {
            val o = objects[it]
            o is CircleOrLine || o is Point
        }
        transformWhatWeCan(targets, translation = pan, focus = c, zoom = zoom, rotationAngle = rotationAngle)
    }

    /** Wrapper around [transform] that adjusts [targets] based on [INVERSION_OF_CONTROL] */
    private inline fun transformWhatWeCan(
        targets: List<Ix>,
        translation: Offset = Offset.Zero,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
        crossinline updateExpressions: (affectedTargets: List<Ix>) -> Unit = {
            expressions.update(it)
        },
    ) {
        val actualTargets: List<Ix> =
            when (INVERSION_OF_CONTROL) {
                InversionOfControl.NONE -> targets.filter { isFree(it) }
                InversionOfControl.LEVEL_1 -> {
                    targets.flatMap { targetIx ->
                        if (isFree(targetIx)) {
                            listOf(targetIx)
                        } else {
                            val parents = expressions.getImmediateParents(targetIx)
                            if (parents.all { isFree(it) }) parents
                            else emptyList()
                        }
                    }.distinct()
                }
                InversionOfControl.LEVEL_INFINITY -> {
                    (targets.toSet() + expressions.getAllParents(targets)).toList()
                }
            }
        if (actualTargets.isEmpty()) {
            if (targets.size == 1) // not sure this is the right place for snackbar messages
                queueSnackbarMessage(SnackbarMessage.LOCKED_OBJECT_NOTICE)
            else
                queueSnackbarMessage(SnackbarMessage.LOCKED_OBJECTS_NOTICE)
        } else {
            transform(actualTargets, translation, focus, zoom, rotationAngle, updateExpressions)
        }
    }

    /** Apply [translation];scaling;rotation to [targets] (that are all assumed free).
     *
     * Scaling and rotation are wrt. fixed [focus] by the factor of
     * [zoom] and by [rotationAngle] degrees. If [focus] is [Offset.Unspecified] for
     * each circle choose its center, for each point -- itself, for each line -- screen center
     * projected onto it
     * */
    private inline fun transform(
        targets: List<Ix>,
        translation: Offset = Offset.Zero,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
        crossinline updateExpressions: (affectedTargets: List<Ix>) -> Unit = {
            expressions.update(it)
        },
    ) {
        if (targets.isEmpty()) {
            return
        }
        val requiresTranslation = translation != Offset.Zero
        val requiresZoom = zoom != 1f
        val requiresRotation = rotationAngle != 0f
        if (requiresTranslation)
            recordCommand(Command.MOVE, targets)
        else if (requiresZoom)
            recordCommand(Command.SCALE, targets) // scale & rotate case doesn't happen usually
        else
            recordCommand(Command.ROTATE, targets)
        if (!requiresZoom && !requiresRotation) {
            for (ix in targets) {
                val o = objects[ix]
                objects[ix] = o?.translated(translation)
                if (o is Line)
                    adjustIncidentPoints(parentIx = ix, translation = translation)
            }
        } else {
            val screenCenter = computeAbsoluteCenter() ?: Offset.Zero
            for (ix in targets) {
                when (val o = objects[ix]) {
                    is Circle -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                        if (requiresRotation) {
                            adjustIncidentPoints(
                                parentIx = ix,
                                centroid = focus,
                                rotationAngle = rotationAngle
                            )
                        }
                    }
                    is Line -> {
                        val f = if (focus == Offset.Unspecified)
                            o.project(screenCenter)
                        else focus
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                        adjustIncidentPoints(
                            parentIx = ix,
                            translation = translation,
                            centroid = f,
                            zoom = zoom,
                            rotationAngle = rotationAngle
                        )
                    }
                    is Point -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                    }
                    is ImaginaryCircle -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                    }
                    null -> {}
                }
            }
        }
//        measureTime {
        updateExpressions(targets)
//        }.also { println("update time: $it") }
    }

    // MAYBE: handle key arrows as panning
    fun onPanZoomRotate(pan: Offset, centroid: Offset, zoom: Float, rotationAngle: Float) {
        movementAfterDown = true
        val c = absolute(centroid)
        val selectedCircles = selection.filter { objects[it] is CircleOrLine }
        val selectedPoints = selection.filter { objects[it] is Point }
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
                    when (val sm = submode) {
                        is SubMode.Scale ->
                            scaleSeveralCircles(pan, selection)
                        is SubMode.ScaleViaSlider ->
                            scaleViaSliderSeveralCircles(pan = pan, sm = sm, targets = selection)
                        is SubMode.Rotate ->
                            rotateSeveralCircles(pan = pan, c = c, sm = sm, targets = selection)
                        else -> {}
                    }
                }
                else -> {}
            }
            if (mode == SelectionMode.Multiselect && submode is SubMode.RectangularSelect) {
                val corner1 = (submode as SubMode.RectangularSelect).corner1
                val rect = Rect.fromCorners(corner1 ?: c, c)
                val selectables = objects.mapIndexed { ix, o ->
                    if (showPhantomObjects || ix !in phantoms) o else null
                }
                selection = selectWithRectangle(selectables, rect)
                submode = SubMode.RectangularSelect(corner1, c)
            } else if (mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect) {
                val qualifiedRegion = (submode as SubMode.FlowSelect).lastQualifiedRegion
                val (_, newQualifiedRegion) = selectRegionAt(centroid)
                if (qualifiedRegion == null) {
                    submode = SubMode.FlowSelect(newQualifiedRegion)
                } else {
                    val diff =
                        (qualifiedRegion.insides - newQualifiedRegion.insides) union
                        (newQualifiedRegion.insides - qualifiedRegion.insides) union
                        (qualifiedRegion.outsides - newQualifiedRegion.outsides) union
                        (newQualifiedRegion.outsides - qualifiedRegion.outsides)
                    selection += diff.filter { it !in selection && (showPhantomObjects || it !in phantoms) }
                }
            } else if (mode == SelectionMode.Region && submode is SubMode.FlowFill) {
                val qualifiedRegion = (submode as SubMode.FlowFill).lastQualifiedRegion
                val (_, newQualifiedRegion) = selectRegionAt(centroid)
                if (qualifiedRegion == null) {
                    submode = SubMode.FlowFill(newQualifiedRegion)
                } else if (qualifiedRegion != newQualifiedRegion) {
                    submode = SubMode.FlowFill(newQualifiedRegion)
                    if (restrictRegionsToSelection && selectedCircles.isNotEmpty()) {
                        reselectRegionAt(centroid, selectedCircles)
                    } else {
                        reselectRegionAt(centroid)
                    }
                }
            }
        } else if (mode == SelectionMode.Drag && selectedCircles.isNotEmpty() && showCircles) {
            dragCircle(pan = pan, zoom = zoom, rotationAngle = rotationAngle)
        } else if (mode == SelectionMode.Drag && selectedPoints.isNotEmpty() && showCircles) {
            dragPoint(c = c)
        } else if (
            mode == SelectionMode.Multiselect &&
            (selectedCircles.isNotEmpty() && showCircles || selectedPoints.isNotEmpty())
        ) {
            dragCirclesOrPoints(pan = pan, c = c, zoom = zoom, rotationAngle = rotationAngle)
        } else {
            val result = snapped(c)
            val absolutePoint = result.result
            if (mode == ToolMode.ARC_PATH) {
                // TODO: if last with n>=3, snap to start
                partialArcPath = partialArcPath?.moveFocused(absolutePoint)
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
                translation += pan // navigate canvas
            }
        }
    }

    fun onUp(visiblePosition: Offset?) {
        cancelSelectionAsToolArgPrompt()
        when (mode) {
            SelectionMode.Drag -> {
                // MAYBE: try to re-attach free points
            }
            ToolMode.ARC_PATH -> {}
            is ToolMode -> {
                val pArgList = partialArgList
                // we only confirm args in onUp, they are created in onDown etc.
                val newArg = when (val arg = pArgList?.currentArg) {
                    is Arg.Point -> visiblePosition?.let {
                        val args = pArgList.args
                        val snap = snapped(absolute(visiblePosition))
                        // we cant realize it here since for fast circles the first point already has been
                        // realized in onDown and we don't know yet if we moved far enough from it to
                        // create the second point
                        if (mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS && FAST_CENTERED_CIRCLE && args.size == 2) {
                            val firstPoint: Point =
                                when (val first = args.first() as Arg.Point) {
                                    is Arg.Point.Index -> objects[first.index] as Point
                                    is Arg.Point.XY -> first.toPoint()
                                }
                            val pointsAreTooClose = firstPoint.distanceFrom(snap.result) < 1e-3
                            if (pointsAreTooClose) { // haxxz
                                partialArgList = pArgList.copy(
                                    args = args.dropLast(1),
                                    lastArgIsConfirmed = true,
                                    lastSnap = null
                                )
                                null
                            } else {
                                realizePointCircleSnap(snap).toArgPoint()
                            }
                        } else {
                            realizePointCircleSnap(snap).toArgPoint()
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
        if ((mode == SelectionMode.Drag || mode == SelectionMode.Multiselect) &&
            movementAfterDown &&
            submode is SubMode.None &&
            selection.none { isFree(it) }
        ) {
            highlightSelectionParents()
        }
        if (mode == SelectionMode.Multiselect && submode is SubMode.RectangularSelect && visiblePosition != null) {
            val (corner1, corner2) = submode as SubMode.RectangularSelect
            if (corner1 != null && corner2 != null) {
                val newCorner2 = absolute(visiblePosition)
                val rect = Rect.fromCorners(corner1, newCorner2)
                val selectables = objects.mapIndexed { ix, o ->
                    if (showPhantomObjects || ix !in phantoms) o else null
                }
                selection = selectWithRectangle(selectables, rect)
                submode = SubMode.RectangularSelect(corner1, corner2)
            }
        }
        if (mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect) // haxx
            toolbarState = toolbarState.copy(activeTool = EditClusterTool.Multiselect)
        if (submode !is SubMode.FlowFill) // we don't want to exit flow-fill
            submode = SubMode.None
    }

    fun onVerticalScroll(yDelta: Float) {
        val zoom = getPlatform().scrollToZoom(yDelta)
        scaleSelection(zoom)
    }

    // maybe enable it &
    // make long drag = pan zoom
    fun onLongPress(position: Offset) { // by itself interferes with long-drag
        // select siblings & parents for easy copy
        if (mode.isSelectingCircles()) {
            selection.let {
                if (it.size == 1) {
                    val ix = it.single()
                    val family = listOf(ix) + findSiblingsAndParents(ix)
                    if (family.size > 1 && mode == SelectionMode.Drag) {
                        switchToMode(SelectionMode.Multiselect)
                        selection = family
                    }
                }
            }
        }
    }

//    fun onLongDragStart(position: Offset) {}
//    fun onLongDrag(delta: Offset) {}
//    fun onLongDragCancel() {}
//    fun onLongDragEnd() {}

    fun queueSnackbarMessage(snackbarMessage: SnackbarMessage, postfix: String = "") {
        snackbarMessages.tryEmit(snackbarMessage to postfix)
//        viewModelScope.launch {
//            snackbarMessages.emit(snackbarMessage)
//        }
    }

    /** Signals locked state to the user with animation & snackbar message */
    private fun highlightSelectionParents() {
        val allParents = selection.flatMap { selectedIndex ->
            if (isConstrained(selectedIndex)) emptyList() // exclude semi-free Expr.Incidence
            else expressions.getImmediateParents(selectedIndex)
                .minus(selection.toSet())
        }.distinct().mapNotNull { objects[it] }
        if (allParents.isNotEmpty()) {
            viewModelScope.launch {
                _animations.emit(HighlightAnimation(allParents))
            }
        }
    }

    /**
     * transform points incident to the circle #[parentIx] via
     * [translation] >>> scaling ([centroid], [zoom]) >>> rotation ([centroid], [rotationAngle])
     * @param[rotationAngle] in degrees
     */
    private fun adjustIncidentPoints(
        parentIx: Ix,
        translation: Offset = Offset.Zero,
        centroid: Offset = Offset.Zero,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
    ) {
        val ix2point = expressions.getIncidentPoints(parentIx)
            .associateWith { child ->
                val newPoint = (objects[child] as? Point)
                    ?.translated(translation)
                    ?.scaled(centroid, zoom)
                    ?.rotated(centroid, rotationAngle)
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
                    selection = emptyList() // forces to select all instead of toggling
                switchToCategory(EditClusterCategory.Multiselect)
                toggleSelectAll()
            }
            KeyboardAction.DELETE -> deleteSelectedPointsAndCircles()
            KeyboardAction.PASTE -> duplicateSelectedCircles()
            KeyboardAction.ZOOM_IN -> scaleSelection(KEYBOARD_ZOOM_INCREMENT)
            KeyboardAction.ZOOM_OUT -> scaleSelection(1/KEYBOARD_ZOOM_INCREMENT)
            KeyboardAction.UNDO -> undo()
            KeyboardAction.REDO -> redo()
            KeyboardAction.CANCEL -> when (mode) { // reset mode
                is ToolMode -> {
                    partialArgList = partialArgList?.let { PartialArgList(it.signature) }
                    partialArcPath = null
                }
                is SelectionMode -> {
                    selection = emptyList()
                }
            }
            KeyboardAction.OPEN -> {} // idk
            KeyboardAction.MOVE -> switchToCategory(EditClusterCategory.Drag)
            KeyboardAction.SELECT -> switchToCategory(EditClusterCategory.Multiselect)
            KeyboardAction.REGION -> switchToCategory(EditClusterCategory.Region)
            KeyboardAction.PALETTE -> toolAction(EditClusterTool.Palette)
            KeyboardAction.TRANSFORM -> switchToCategory(EditClusterCategory.Transform)
            KeyboardAction.CREATE -> switchToCategory(EditClusterCategory.Create)
        }
    }

    private fun completeToolMode() {
        val toolMode = mode
        val argList = partialArgList
        require(argList != null && argList.isFull && argList.isValid && argList.lastArgIsConfirmed) { "Invalid partialArgList in completeToolMode(): $argList" }
        require(toolMode is ToolMode && toolMode.signature == argList.signature) { "Invalid signature in completeToolMode(): $toolMode's ${(toolMode as ToolMode).signature} != ${argList.signature}" }
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
            ToolMode.BI_INVERSION -> openedDialog = DialogType.BI_INVERSION
            ToolMode.LOXODROMIC_MOTION -> openedDialog = DialogType.LOXODROMIC_MOTION
        }
    }

    private fun completeCircleByCenterAndRadius() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.Point }
        recordCreateCommand()
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.Point.XY }) {
            val newCircle = computeCircleByCenterAndRadius(
                center = (args[0] as Arg.Point.XY).toPoint().downscale(),
                radiusPoint = (args[1] as Arg.Point.XY).toPoint().downscale(),
            )?.upscale()
            expressions.addFree()
            createNewGCircle(newCircle)
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.Point.Index -> it.index
                    is Arg.Point.XY -> createNewFreePoint(it.toPoint(), triggerRecording = false)
                }
            }
            val newCircle = expressions.addSoloExpr(
                Expr.CircleByCenterAndRadius(
                    center = realized[0],
                    radiusPoint = realized[1]
                ),
            ) as? CircleOrLine
            createNewGCircle(newCircle?.upscale())
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleBy3Points() {
        val argList = partialArgList!!
        val args = argList.args.map {
            it as Arg.CircleOrPoint
        }
        recordCreateCommand()
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.CircleOrPoint.Point.XY }) {
            val (p1, p2, p3) = args.map {
                (it as Arg.CircleOrPoint.Point.XY).toPoint().downscale()
            }
            val newCircle = computeCircleBy3Points(p1, p2, p3)
            expressions.addFree()
            createNewGCircle(newCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.CircleOrPoint.CircleIndex -> it.index
                    is Arg.CircleOrPoint.Point.Index -> it.index
                    is Arg.CircleOrPoint.Point.XY -> createNewFreePoint(it.toPoint(), triggerRecording = false)
                }
            }
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleBy3Points(
                    object1 = realized[0],
                    object2 = realized[1],
                    object3 = realized[2],
                ),
            )
            createNewGCircle(newGCircle?.upscale())
            if (newGCircle is ImaginaryCircle)
                queueSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleByPencilAndPoint() {
        val argList = partialArgList!!
        val args = argList.args.map {
            it as Arg.CircleOrPoint
        }
        recordCreateCommand()
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.CircleOrPoint.Point.XY }) {
            val (p1, p2, p3) = args.map {
                (it as Arg.CircleOrPoint.Point.XY).toPoint().downscale()
            }
            val newCircle = computeCircleByPencilAndPoint(p1, p2, p3)
            expressions.addFree()
            createNewGCircle(newCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.CircleOrPoint.CircleIndex -> it.index
                    is Arg.CircleOrPoint.Point.Index -> it.index
                    is Arg.CircleOrPoint.Point.XY ->
                        createNewFreePoint(it.toPoint(), triggerRecording = false)
                }
            }
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleByPencilAndPoint(
                    pencilObject1 = realized[0],
                    pencilObject2 = realized[1],
                    perpendicularObject = realized[2],
                ),
            )
            createNewGCircle(newGCircle?.upscale())
            if (newGCircle is ImaginaryCircle)
                queueSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeLineBy2Points() {
        val argList = partialArgList!!
        val args = argList.args.map {
            it as Arg.CircleOrPoint
        }
        recordCreateCommand()
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.CircleOrPoint.Point.XY }) {
            val (p1, p2) = args.map {
                (it as Arg.CircleOrPoint.Point.XY).toPoint().downscale()
            }
            val newGCircle = computeLineBy2Points(p1, p2)
            expressions.addFree()
            createNewGCircle(newGCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.CircleOrPoint.CircleIndex -> it.index
                    is Arg.CircleOrPoint.Point.Index -> it.index
                    is Arg.CircleOrPoint.Point.XY ->
                        createNewFreePoint(it.toPoint(), triggerRecording = false)
                }
            }
            val newGCircle = expressions.addSoloExpr(
                Expr.LineBy2Points(
                    object1 = realized[0],
                    object2 = realized[1],
                ),
            )
            createNewGCircle(newGCircle?.upscale())
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleInversion() {
        val argList = partialArgList!!
        val objArg = argList.args[0] as Arg.CircleAndPointIndices
        val targetCircleIxs = objArg.circleIndices
        val targetPointIxs = objArg.pointIndices
        val invertingCircleIndex = (argList.args[1] as Arg.CircleIndex).index
        recordCreateCommand()
        val newIndexedGCircles = (targetCircleIxs + targetPointIxs).map { ix ->
            ix to expressions.addSoloExpr(
                Expr.CircleInversion(ix, invertingCircleIndex),
            )?.upscale()
        }
        copyRegionsAndStyles(newIndexedGCircles, flipRegionsInAndOut = true) {
            CircleAnimation.Entrance(it)
        }
        partialArgList = PartialArgList(argList.signature)
    }

    fun completeCircleInterpolation(params: InterpolationParameters) {
        openedDialog = null
        val argList = partialArgList!!
        val (arg1, arg2) = argList.args.map { it as Arg.CircleOrPoint }
        if (arg1 is Arg.CircleOrPoint.CircleIndex && arg2 is Arg.CircleOrPoint.CircleIndex) {
            val startCircleIx = arg1.index
            val endCircleIx = arg2.index
            recordCreateCommand()
            val newGCircles = expressions.addMultiExpr(
                Expr.CircleInterpolation(params, startCircleIx, endCircleIx),
            )
            val newCircles = newGCircles.map { it?.upscale() }
            createNewGCircles(newCircles)
            partialArgList = PartialArgList(argList.signature)
            defaultInterpolationParameters = DefaultInterpolationParameters(params)
            if (newGCircles.any { it is ImaginaryCircle })
                queueSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
        } else if (arg1 is Arg.CircleOrPoint.Point && arg2 is Arg.CircleOrPoint.Point) {
            recordCreateCommand()
            val (startPointIx, endPointIx) = listOf(arg1, arg2).map { pointArg ->
                when (pointArg) {
                    is Arg.CircleOrPoint.Point.Index -> pointArg.index
                    is Arg.CircleOrPoint.Point.XY ->
                        createNewFreePoint(pointArg.toPoint(), triggerRecording = false)
                }
            }
            val newGCircles = expressions.addMultiExpr(
                Expr.PointInterpolation(params, startPointIx, endPointIx),
            )
            val newPoints = newGCircles.map { it?.upscale() as? Point }
            createNewGCircles(newPoints)
            partialArgList = PartialArgList(argList.signature)
            defaultInterpolationParameters = DefaultInterpolationParameters(params)
        }
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
        val newGCircles = expressions.addMultiExpr(
            Expr.CircleExtrapolation(params, startCircleIx, endCircleIx),
        ).map { it?.upscale() }
        createNewGCircles(newGCircles)
        partialArgList = PartialArgList(argList.signature)
        defaultExtrapolationParameters = DefaultExtrapolationParameters(params)
    }

    fun resetCircleExtrapolation() {
        openedDialog = null
        partialArgList = PartialArgList(EditClusterTool.CircleExtrapolation.signature)
    }

    fun completeBiInversion(
        params: BiInversionParameters,
    ) {
        openedDialog = null
        val argList = partialArgList!!
        val args = argList.args
        val objArg = args[0] as Arg.CircleAndPointIndices
        val targetCircleIndices = objArg.circleIndices
        val targetPointsIndices = objArg.pointIndices
        recordCreateCommand()
        val (engine1, engine2) = args.drop(1).take(2)
            .map { (it as Arg.CircleIndex).index }
        val targetIndices = targetCircleIndices + targetPointsIndices
        copyRegionsAndStylesForNewTrajectories(
            sourceIndex2NewTrajectory = targetIndices.map { targetIndex ->
                targetIndex to expressions.addMultiExpr(
                    Expr.BiInversion(
                        params,
                        engine1, engine2,
                        targetIndex
                    ),
                ).map { it?.upscale() } // multi expression creates a whole trajectory at a time
            }
        ) { CircleAnimation.Entrance(it) }
        partialArgList = PartialArgList(argList.signature)
        defaultBiInversionParameters = DefaultBiInversionParameters(params)
    }

    fun resetBiInversion() {
        openedDialog = null
        partialArgList = PartialArgList(EditClusterTool.BiInversion.signature)
    }

    // TODO: inf point input
    fun completeLoxodromicMotion(
        params: LoxodromicMotionParameters,
    ) {
        openedDialog = null
        val argList = partialArgList!!
        val args = argList.args
        val objArg = args[0] as Arg.CircleAndPointIndices
        val targetCircleIndices = objArg.circleIndices
        val targetPointsIndices = objArg.pointIndices
        recordCreateCommand()
        val (divergence, convergence) = args.drop(1).take(2)
            .map { when (val arg = it as Arg.Point) {
                is Arg.Point.Index -> arg.index
                is Arg.Point.XY -> createNewFreePoint(arg.toPoint(), triggerRecording = false)
            } }
        val targetIndices = targetCircleIndices + targetPointsIndices
        copyRegionsAndStylesForNewTrajectories(
            sourceIndex2NewTrajectory = targetIndices.map { targetIndex ->
                targetIndex to expressions.addMultiExpr(
                    Expr.LoxodromicMotion(
                        params,
                        divergence, convergence,
                        targetIndex
                    ),
                ).map { it?.upscale() } // multi expression creates a whole trajectory at a time
            }
        ) { CircleAnimation.Entrance(it) }
        partialArgList = PartialArgList(argList.signature)
        defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(params)
    }

    fun resetLoxodromicMotion() {
        openedDialog = null
        partialArgList = PartialArgList(EditClusterTool.LoxodromicMotion.signature)
    }

    fun closeDialog() {
        openedDialog = null
    }

    fun completeArcPath() {
        require(partialArcPath != null) { "Cannot complete non-existent arc path during completeArcPath()" }
        partialArcPath?.let { pArcPath ->
            recordCreateCommand()
//            val vertices =
//                if (pArcPath.isClosed)
//                    listOf(pArcPath.startVertex) + pArcPath.vertices.dropLast(1)
//                else listOf(pArcPath.startVertex) + pArcPath.vertices
//            val vertexIndices: List<Ix> = vertices.map { vertex ->
//                when (vertex) {
//                    is ArcPathPoint.Eq -> vertex.index
//                    is ArcPathPoint.Free -> {
//                        expressions.addFree()
//                        addObject(vertex.point)
//                    }
//                    is ArcPathPoint.Incident -> {
//                        val carrier = objects[vertex.carrierIndex] as CircleOrLine
//                        val order = carrier.downscale().point2order(vertex.point.downscale())
//                        val point = expressions.addSoloExpression(
//                            Expr.Incidence(IncidenceParameters(order), vertex.carrierIndex)
//                        ) as Point
//                        addObject(point)
//                    }
//                    is ArcPathPoint.Intersection -> {
//                        val expression = Expression.OneOf(
//                            Expr.Intersection(vertex.carrier1Index, vertex.carrier2Index),
//                            outputIndex = 0
//                        )
//                        val point = expressions.addMultiExpression(expression) as Point
//                        addObject(point)
//                    }
//                }
//            }
//            val circleIndices = mutableSetOf<Ix>()
//            for (j in pArcPath.circles.indices) {
//                when (val c = pArcPath.circles[j]) {
//                    is ArcPathCircle.Eq -> {
//                        circleIndices.add(c.index)
//                    }
//                    is ArcPathCircle.Free -> {
//                        val previous = vertexIndices[j]
//                        val next = vertexIndices[(j + 1) % vertexIndices.size]
//                        val circle = c.circle
//                        if (circle == null) {
//                            val expr = Expr.LineBy2Points(previous, next)
//                            val line = expressions.addSoloExpression(expr) as Line
//                            TODO("line by 2 points")
//                        } else {
//                            val sagittaRatio = computeSagittaRatio(circle, objects[previous] as Point, objects[next] as Point)
//                            val expr = Expr.CircleBy2PointsAndSagittaRatio(
//                                SagittaRatioParameters(sagittaRatio),
//                                previous, next
//                            )
//                            val circle1 = expressions.addSoloExpression(expr) as CircleOrLine
//                            TODO("sagitta")
//                        }
//                    }
//                }
//            }
            // and create [abstract] arc path
            val newCircles: List<CircleOrLine> = pArcPath.circles
                .mapIndexed { j, circle ->
                    when (circle) {
                        is ArcPathCircle.Eq -> null
                        is ArcPathCircle.Free -> when (val c = circle.circle) {
                            is Circle -> c
                            null -> Line.by2Points(
                                pArcPath.previousVertex(j).point,
                                pArcPath.vertices[j].point
                            )
                            else -> never()
                        }
                        else -> never()
                    }
                }.filterNotNull()
            createNewGCircles(newCircles)
        }
        partialArcPath = null
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

    fun setBlendSettings(newRegionsOpacity: Float, newRegionsBlendModeType: BlendModeType) {
        regionsOpacity = newRegionsOpacity
        regionsBlendModeType = newRegionsBlendModeType
        openedDialog = null
    }

    fun updateParameters(parameters: Parameters) {
        val sm = submode
        if (sm is SubMode.ExprAdjustment)
            when {
                mode == ToolMode.CIRCLE_INTERPOLATION &&
                sm.expr is Expr.CircleInterpolation &&
                parameters is InterpolationParameters -> {
                    val newExpr = sm.expr.copy(parameters = parameters)
                    val (newIndices, newRange) = expressions.adjustMultiExpr(
                        targetIndices = sm.outputIndices,
                        maxRange = sm.maxOutputRange,
                        newExpr = newExpr
                    )
                    submode = sm.copy(expr = newExpr, outputIndices = newIndices, maxOutputRange = newRange)
                }
            }
    }

    fun openDetailsDialog() {
        openedDialog = when (mode) {
            ToolMode.CIRCLE_INTERPOLATION -> DialogType.CIRCLE_INTERPOLATION
            ToolMode.CIRCLE_EXTRAPOLATION -> DialogType.CIRCLE_EXTRAPOLATION
            ToolMode.BI_INVERSION -> DialogType.BI_INVERSION
            ToolMode.LOXODROMIC_MOTION -> DialogType.LOXODROMIC_MOTION
            else -> null
        }
    }

    /** @return Assigned [newInBetween] */
    fun toggleInBetween(
        newInBetween: Boolean? = null,
        newComplementary: Boolean? = null,
    ): Boolean? {
        when (mode) {
            ToolMode.CIRCLE_INTERPOLATION -> {
                when (val s = submode) {
                    is SubMode.ExprAdjustment -> {
                        when (val expr = s.expr) {
                            is Expr.CircleInterpolation -> {
                                val newValue = newInBetween ?: !expr.parameters.inBetween
                                submode = s.copy(expr = expr.copy(
                                    expr.parameters.copy(
                                        inBetween = newValue,
                                        complementary = newComplementary ?: !expr.parameters.complementary
                                    )
                                ))
                                return newValue
                            }
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }
            else -> {}
        }
        return null
    }

    fun toolAction(tool: EditClusterTool) {
//        println("toolAction($tool)")
        when (tool) {
            EditClusterTool.Undo -> undo()
            EditClusterTool.Redo -> redo()
            EditClusterTool.SaveCluster -> openedDialog = DialogType.SAVE_OPTIONS
            EditClusterTool.Drag -> switchToMode(SelectionMode.Drag)
            EditClusterTool.Multiselect -> switchToMode(SelectionMode.Multiselect)
            EditClusterTool.RectangularSelect -> activateRectangularSelect()
            EditClusterTool.FlowSelect -> activateFlowSelect()
            EditClusterTool.ToggleSelectAll -> toggleSelectAll()
            EditClusterTool.Region -> switchToMode(SelectionMode.Region)
            EditClusterTool.FlowFill -> activateFlowFill()
            EditClusterTool.FillChessboardPattern -> toggleChessboardPattern()
            EditClusterTool.RestrictRegionToSelection -> toggleRestrictRegionsToSelection()
            EditClusterTool.DeleteAllParts -> deleteAllRegions()
            EditClusterTool.BlendSettings -> openedDialog = DialogType.BLEND_SETTINGS
            EditClusterTool.ToggleObjects -> toggleShowCircles()
            EditClusterTool.TogglePhantoms -> togglePhantomObjects()
            EditClusterTool.ToggleFilledOrOutline -> showWireframes = !showWireframes
            EditClusterTool.HideUI -> hideUIFor30s()
            EditClusterTool.ToggleDirectionArrows -> showDirectionArrows = !showDirectionArrows
            // TODO: 2 options: solid color or external image
            EditClusterTool.AddBackgroundImage -> openedDialog = DialogType.BACKGROUND_COLOR_PICKER
            EditClusterTool.InsertCenteredCross -> insertCenteredCross()
            EditClusterTool.CompleteArcPath -> completeArcPath()
            EditClusterTool.Palette -> openedDialog = DialogType.REGION_COLOR_PICKER
            EditClusterTool.Expand -> scaleSelection(HUD_ZOOM_INCREMENT)
            EditClusterTool.Shrink -> scaleSelection(1/HUD_ZOOM_INCREMENT)
            EditClusterTool.Detach -> detachEverySelectedObject()
            EditClusterTool.SwapDirection -> swapDirectionsOfSelectedCircles()
            EditClusterTool.MarkAsPhantoms -> if (toolPredicate(tool)) markSelectedObjectsAsPhantoms() else unmarkSelectedObjectsAsPhantoms()
            EditClusterTool.Duplicate -> duplicateSelectedCircles()
            EditClusterTool.PickCircleColor -> openedDialog = DialogType.CIRCLE_COLOR_PICKER
            EditClusterTool.Delete -> deleteSelectedPointsAndCircles()
            is EditClusterTool.AppliedColor -> setNewRegionColor(tool.color)
            is EditClusterTool.MultiArg -> switchToMode(ToolMode.correspondingTo(tool))
            is EditClusterTool.CustomAction -> {} // custom handlers
            EditClusterTool.DetailedAdjustment -> openDetailsDialog()
            EditClusterTool.InBetween -> TODO()
        }
    }

    /** Is [tool] enabled? */
    fun toolPredicate(tool: EditClusterTool): Boolean =
        when (tool) { // NOTE: i think this has to return State<Boolean> to work properly
            EditClusterTool.Drag -> mode == SelectionMode.Drag
            EditClusterTool.Multiselect -> mode == SelectionMode.Multiselect &&
                submode !is SubMode.FlowSelect && submode !is SubMode.RectangularSelect
            EditClusterTool.RectangularSelect -> mode == SelectionMode.Multiselect && submode is SubMode.RectangularSelect
            EditClusterTool.FlowSelect -> mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect
            EditClusterTool.ToggleSelectAll -> selection.containsAll(objects.filterIndices { it is CircleOrLine })
            EditClusterTool.Region -> mode == SelectionMode.Region && submode !is SubMode.FlowFill
            EditClusterTool.FlowFill -> mode == SelectionMode.Region && submode is SubMode.FlowFill
            EditClusterTool.FillChessboardPattern -> chessboardPattern != ChessboardPattern.NONE
            EditClusterTool.RestrictRegionToSelection -> restrictRegionsToSelection
            EditClusterTool.ToggleObjects -> showCircles
            EditClusterTool.TogglePhantoms -> showPhantomObjects
            EditClusterTool.ToggleFilledOrOutline -> !showWireframes
            EditClusterTool.ToggleDirectionArrows -> showDirectionArrows
            is EditClusterTool.MultiArg -> mode == ToolMode.correspondingTo(tool)
            EditClusterTool.MarkAsPhantoms -> selection.none { it in phantoms }
            else -> true
        }

    /** alternative enabled, mainly for 3-state buttons */
    fun toolAlternativePredicate(tool: EditClusterTool): Boolean =
        when (tool) {
            EditClusterTool.FillChessboardPattern ->
                chessboardPattern == ChessboardPattern.STARTS_TRANSPARENT
            else -> false
        }

    // NOTE: downscaling each arg for eval is an extreme performance bottleneck (4 - 15 times)
    private fun GCircle.downscale(): GCircle = scaled(0.0, 0.0, DOWNSCALING_FACTOR)
    private fun GCircle.upscale(): GCircle = scaled(0.0, 0.0, UPSCALING_FACTOR)
    private fun CircleOrLine.downscale(): CircleOrLine = scaled(0.0, 0.0, DOWNSCALING_FACTOR)
    private fun CircleOrLine.upscale(): CircleOrLine = scaled(0.0, 0.0, UPSCALING_FACTOR)
    private fun Point.downscale(): Point = scaled(0.0, 0.0, DOWNSCALING_FACTOR)
    private fun Point.upscale(): Point = scaled(0.0, 0.0, UPSCALING_FACTOR)

    fun saveState(): State {
        val center = computeAbsoluteCenter() ?: Offset.Zero
        val deleted = objects.indices.filter { i ->
            objects[i] == null && expressions.expressions[i] == null
        }.toSet()
        val reindexing = reindexingMap(
            originalIndices = objects.indices,
            deletedIndices = deleted
        )
        val constellation = toConstellation()
        return State(
            constellation = constellation,
            // Q: idk from where, but sometimes it gets null's after select-all
            selection = selection.mapNotNull { reindexing[it] },
            centerX = center.x,
            centerY = center.y,
            regionColor = regionColor,
            chessboardPattern = chessboardPattern,
            chessboardColor = chessboardColor,
        )
    }

    private fun launchRestore() {
        viewModelScope.launch {
            val platform = getPlatform()
            if (!RESTORE_LAST_SAVE_ON_LOAD) {
                loadNewConstellation(Constellation.SAMPLE)
                centerizeTo(0f, 0f)
            } else {
                val result = runCatching { // NOTE: can fail crash when underlying VM.State format changes
                    platform.lastStateStore.get()
                }
                val state = result.getOrNull()
                if (state == null) {
                    loadNewConstellation(Constellation.SAMPLE)
                    centerizeTo(0f, 0f)
                } else {
                    restoreFromState(state)
//                    queueSnackbarMessage(SnackbarMessage.SUCCESSFUL_RESTORE)
                }
            }
            runCatching {
                platform.settingsStore.get()
            }.getOrNull()?.let { settings ->
                loadSettings(settings)
            }
        }
    }

    private fun loadSettings(settings: Settings) {
        regionsOpacity = settings.regionsOpacity
        regionsBlendModeType = settings.regionsBlendModeType
    }

    private fun restoreFromState(state: State) {
        loadNewConstellation(state.constellation)
        if (state.selection.size > 1) {
            mode = SelectionMode.Multiselect
        }
        selection = state.selection
        centerizeTo(state.centerX, state.centerY)
        state.regionColor?.let {
            regionColor = it
        }
        chessboardPattern = state.chessboardPattern
        state.chessboardColor?.let {
            chessboardColor = it
        }
    }

    /** caches latest [State] using platform-specific local storage */
    fun cacheState() {
        println("caching VM state...")
        val state = saveState()
        val platform = getPlatform()
        platform.saveLastState(state)
        platform.saveSettings(getCurrentSettings())
        println("cached.")
    }

    private fun getCurrentSettings(): Settings =
        Settings(
            regionsOpacity = regionsOpacity,
            regionsBlendModeType = regionsBlendModeType
        )

    // NOTE: i never seen this proc on Android or Wasm tbh
    //  so i had to create Flow<LifecycleEvent> to manually trigger caching
    override fun onCleared() {
        cacheState()
        super.onCleared()
    }

    /** Be careful to pass *only* strictly immutable args by __copying__ */
    @Immutable
    @Serializable
    data class State(
        val constellation: Constellation,
        val selection: List<Ix>,
        // NOTE: saving VM.translation instead has issues (on desktop window size rapidly cycles thru 3 sizes)
        val centerX: Float,
        val centerY: Float,
        val regionColor: ColorAsCss? = null,
        val chessboardPattern: ChessboardPattern = ChessboardPattern.NONE,
        val chessboardColor: ColorAsCss? = null,
    )

    companion object {
        // reference: https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            addInitializer(EditClusterViewModel::class) {
                EditClusterViewModel()
            }
        }
        val YamlEncoding = Yaml(
            configuration = YamlConfiguration(
                encodeDefaults = false,
                strictMode = false,
                polymorphismStyle = PolymorphismStyle.Property,
            )
        )

        const val LOW_ACCURACY_FACTOR = 1.5f
        const val HUD_ZOOM_INCREMENT = 1.1f // == +10%
        const val KEYBOARD_ZOOM_INCREMENT = 1.05f // == +5%
        const val MAX_SLIDER_ZOOM = 3.0f // == +200%
        const val FAST_CENTERED_CIRCLE = true
        const val ENABLE_ANGLE_SNAPPING = true
        const val RESTORE_LAST_SAVE_ON_LOAD = true
        const val TWO_FINGER_TAP_FOR_UNDO = true
        const val DEFAULT_SHOW_DIRECTION_ARROWS_ON_SELECTED_CIRCLES = false
        const val SHOW_IMAGINARY_CIRCLES = true
        /** Allow moving non-free object IF all of it's lvl1 parents/dependencies are free by
         * moving all of its parent with it */ // ggbra-like
        val INVERSION_OF_CONTROL = InversionOfControl.LEVEL_1
        /** when constructing object depending on not-yet-existing points,
         * always create them. In contrast to replacing expression with static circle */
        const val ALWAYS_CREATE_ADDITIONAL_POINTS = false
        // NOTE: changing it presently breaks all line-incident points
        /** [Double] arithmetic is best in range that is closer to 0 */
        const val UPSCALING_FACTOR = 500.0 //200.0
        const val DOWNSCALING_FACTOR = 1.0/UPSCALING_FACTOR

        fun sliderPercentageDeltaToZoom(percentageDelta: Float): Float =
            MAX_SLIDER_ZOOM.pow(2*percentageDelta)
    }
}
