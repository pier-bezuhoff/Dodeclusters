package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import domain.ArcPathArc
import domain.ArcPathPoint
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.CircleOrLineOrImaginaryCircle
import core.geometry.CircleOrLineOrPoint
import core.geometry.GCircle
import core.geometry.GeneralizedCircle
import core.geometry.ImaginaryCircle
import core.geometry.Line
import domain.PartialArcPath
import core.geometry.Point
import core.geometry.Rotor
import core.geometry.calculateStereographicRotationBiEngine
import core.geometry.fromCorners
import core.geometry.generateSphereGrid
import core.geometry.scaled00
import core.geometry.selectWithRectangle
import core.geometry.translationDelta
import domain.Arg
import domain.ArgType
import domain.settings.BlendModeType
import domain.settings.ChessboardPattern
import domain.ColorAsCss
import domain.model.Command
import domain.model.History
import domain.settings.InversionOfControl
import domain.Ix
import domain.model.ObjectModel
import domain.PartialArgList
import domain.PointSnapResult
import domain.settings.Settings
import domain.angleDeg
import domain.cluster.ClosedArcPath
import domain.cluster.Constellation
import domain.cluster.LogicalRegion
import domain.cluster.OpenArcPath
import domain.cluster.SignedDirectedArcIndex
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
import domain.expressions.RotationParameters
import domain.expressions.SagittaRatioParameters
import domain.expressions.computeCircleBy3Points
import domain.expressions.computeCircleByCenterAndRadius
import domain.expressions.computeCircleByPencilAndPoint
import domain.expressions.computeIntersection
import domain.expressions.computeLineBy2Points
import domain.expressions.computeSagittaRatio
import domain.expressions.copyWithNewParameters
import domain.expressions.reIndex
import domain.filterIndices
import domain.hug
import domain.io.DdcV1
import domain.io.DdcV2
import domain.io.DdcV4
import domain.io.constellation2svg
import domain.io.tryParseDdc
import domain.reindexingMap
import domain.snapAngle
import domain.snapCircleToCircles
import domain.snapPointToCircles
import domain.snapPointToPoints
import domain.sortedByFrequency
import domain.toArgPoint
import domain.transpose
import domain.updated
import domain.withoutElementAt
import domain.withoutElementsAt
import getPlatform
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ui.editor.dialogs.ColorPickerParameters
import ui.editor.dialogs.DefaultBiInversionParameters
import ui.editor.dialogs.DefaultExtrapolationParameters
import ui.editor.dialogs.DefaultInterpolationParameters
import ui.editor.dialogs.DefaultLoxodromicMotionParameters
import ui.editor.dialogs.DefaultRotationParameters
import ui.editor.dialogs.DialogType
import ui.theme.DodeclustersColors
import ui.tools.Category
import ui.tools.Tool
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

// MAYBE: use UiState functional pattern + StateFlow's instead of this mess
// this class is obviously too big
// TODO: decouple navigation & tools/categories
// MAYBE: timed autosave (cron-like), e.g. every 10min
@Suppress("MemberVisibilityCanBePrivate")
class EditorViewModel : ViewModel() {
    val objectModel = ObjectModel()
    val objects: List<GCircle?> = objectModel.objects
    /** Filled regions delimited by some objects from [objects] */
    var regions: List<LogicalRegion> by mutableStateOf(listOf())
    var expressions: ExpressionForest = ExpressionForest( // stub
        initialExpressions = emptyMap(),
        objects = objectModel.downscaledObjects,
    )
        private set
    // alt name: ghost[ed] objects
    val phantoms: Set<Ix> = objectModel.phantomObjectIndices
    // not in objectModel cuz i want it to be state-backed for nicer caching
    var objectLabels: Map<Ix, String> by mutableStateOf(mapOf())

//    var _debugObjects: List<GCircle> by mutableStateOf(emptyList())

    var mode: Mode by mutableStateOf(SelectionMode.Drag)
        private set
    var submode: SubMode? by mutableStateOf(null)
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
//    val objectColors: SnapshotStateMap<Ix, Color> = mutableStateMapOf()
    var backgroundColor: Color? by mutableStateOf(null)
    var showCircles: Boolean by mutableStateOf(true)
        private set
    var showPhantomObjects: Boolean by mutableStateOf(false)
        private set
    /** which style to use when drawing regions: true = stroke, false = fill */
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
    inline val circleSelectionIsActive: Boolean get() =
        showCircles && selection.any { objects[it] is CircleOrLineOrImaginaryCircle } && mode.isSelectingCircles()
    inline val pointSelectionIsActive: Boolean get() =
        showCircles && selection.any { objects[it] is Point } && mode.isSelectingCircles()
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
    inline val scaleSliderPercentage: Float get() =
        submode.let { sm ->
            if (sm is SubMode.ScaleViaSlider)
                sm.sliderPercentage
            else 0.5f
        }
    inline val rotationHandleAngle: Float get() =
        submode.let { sm ->
            if (sm is SubMode.Rotate)
                sm.angle.toFloat()
            else 0f
        }
    /** when changing [expressions], flip this to forcibly recalculate [selectionIsLocked] */
    private var selectionIsLockedTrigger: Boolean by mutableStateOf(false)
    // MAYBE: show quick prompt/popup instead of button
    val selectionIsLocked: Boolean get() {
        hug(selectionIsLockedTrigger, objectModel.invalidations)
        return selection.all { objects[it] == null || !isFree(it) }
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

    // ahh.. to be set during startCircleOrPointInterpolationParameterAdjustment()
    var interpolateCircles: Boolean by mutableStateOf(true)
    var circlesAreCoDirected: Boolean by mutableStateOf(true)

    var colorPickerParameters = ColorPickerParameters(Color.Unspecified, emptyList())
    // so.. why aren't these States?
    var defaultInterpolationParameters = DefaultInterpolationParameters()
        private set
    var defaultExtrapolationParameters = DefaultExtrapolationParameters()
        private set
    var defaultRotationParameters = DefaultRotationParameters()
        private set
    var defaultBiInversionParameters = DefaultBiInversionParameters()
        private set
    var defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters()
        private set

    val openFileRequests: MutableSharedFlow<Unit> =
        MutableSharedFlow(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    val animations: MutableSharedFlow<ObjectAnimation> = MutableSharedFlow()

    val snackbarMessages: MutableSharedFlow<Pair<SnackbarMessage, String>> =
        MutableSharedFlow(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    var openedDialog: DialogType? by mutableStateOf(null)
        private set

    var partialArcPath: PartialArcPath? by mutableStateOf(null)
        private set
//    var arcPaths: List<ArcPath> by mutableStateOf(emptyList())
//        private set

    var canvasSize: IntSize by mutableStateOf(IntSize.Zero) // used when saving best-center
        private set
    var translation: Offset by mutableStateOf(Offset.Zero)
        private set

    /** min tap/grab distance to select an object */
    private var tapRadius = getPlatform().tapRadius

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
        objectModel.pathCache.invalidateAll()
        objectModel.invalidate()
    }

    fun saveAsYaml(name: String = DdcV4.DEFAULT_NAME): String {
        // Q: there seemingly was an issue with saving circleColors on Android
        return YamlEncoding.encodeToString(DdcV4.from(toConstellation()).copy(
            name = name,
            bestCenterX = computeAbsoluteCenter()?.x,
            bestCenterY = computeAbsoluteCenter()?.y,
            chessboardPattern = chessboardPattern != ChessboardPattern.NONE,
            chessboardPatternStartsColored = chessboardPattern == ChessboardPattern.STARTS_COLORED,
            chessboardColor = chessboardColor,
        ))
    }

    fun exportAsSvg(name: String = DdcV4.DEFAULT_NAME): String {
        val constellation = toConstellation()
        return constellation2svg(
            constellation =
                if (showPhantomObjects)
                    constellation.copy(phantoms = emptyList())
                else constellation
            ,
            objects = objects.toList()
                .map { o ->
                    o?.translated(translation) // back to top-left = (0,0) system
                }
            ,
            freeObjectIndices = objects.indices.filter { isFree(it) }.toSet(),
            width = canvasSize.width.toFloat(),
            height = canvasSize.height.toFloat(),
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
    fun loadDdc(content: String) {
        tryParseDdc(
            content = content,
            onDdc4 = { ddc4 ->
                val constellation = ddc4.toConstellation()
                loadNewConstellation(constellation)
                centerizeTo(ddc4.bestCenterX, ddc4.bestCenterY)
                chessboardPattern =
                    if (!ddc4.chessboardPattern) ChessboardPattern.NONE
                    else if (ddc4.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                    else ChessboardPattern.STARTS_TRANSPARENT
                ddc4.chessboardColor?.let {
                    chessboardColor = it
                }
            },
            onDdc3 = { ddc3 ->
                val constellation = ddc3.toConstellation().toConstellation()
                loadNewConstellation(constellation)
                centerizeTo(ddc3.bestCenterX, ddc3.bestCenterY)
                chessboardPattern =
                    if (!ddc3.chessboardPattern) ChessboardPattern.NONE
                    else if (ddc3.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                    else ChessboardPattern.STARTS_TRANSPARENT
            },
            onDdc2 = { ddc2 ->
                val cluster = ddc2.content
                    .filterIsInstance<DdcV2.Token.Cluster>()
                    .first()
                    .toCluster()
                loadNewConstellation(
                    cluster.toConstellation()
                )
                centerizeTo(ddc2.bestCenterX, ddc2.bestCenterY)
                chessboardPattern =
                    if (!ddc2.chessboardPattern) ChessboardPattern.NONE
                    else if (ddc2.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                    else ChessboardPattern.STARTS_TRANSPARENT
            },
            onDdc1 = { ddc1 ->
                val cluster = ddc1.content
                    .filterIsInstance<DdcV1.Token.Cluster>()
                    .first()
                    .toCluster()
                loadNewConstellation(
                    cluster.toConstellation()
                )
                centerizeTo(ddc1.bestCenterX, ddc1.bestCenterY)
            },
            onClusterV1 = { cluster1 ->
                loadNewConstellation(
                    cluster1.toCluster().toConstellation()
                )
            },
            onFail = {
                queueSnackbarMessage(SnackbarMessage.FAILED_OPEN)
            },
        )
    }

    fun centerizeTo(centerX: Float?, centerY: Float?) {
        translation = -Offset(
            centerX?.let { it - canvasSize.width/2f } ?: 0f,
            centerY?.let { it - canvasSize.height/2f } ?: 0f
        )
    }

    // TODO: prompt save
    fun openNewBlankConstellation() {
        loadNewConstellation(Constellation(
            objects = emptyList(),
            parts = emptyList(),
            backgroundColor = backgroundColor,
        ))
    }

    fun showDebugInfo() {
        val selectedObjectsString = selection.joinToString { ix ->
            "$ix: " + objects[ix].toString()
        }
        val selectedExpressionsString = selection.joinToString { ix ->
            "$ix: " + expressions.expressions[ix].toString()
        }
        println("mode = $mode, submode = $submode")
        println("partialArgList = $partialArgList")
        println("selection = $selection")
        println("selected objects = $selectedObjectsString")
        println("selected objects downscaled = " + selection.joinToString { ix ->
            "$ix: " + objectModel.downscaledObjects[ix].toString()
        })
        println("selected objects expressions = $selectedExpressionsString")
        println(
            "regions bounded by some of selected objects = " + regions.filter {
                it.insides.any { ix -> ix in selection } ||
                it.outsides.any { ix -> ix in selection }
            }.joinToString { it.toString() }
        )
        println("partialArcPath = $partialArcPath")
        println("invalidation #${objectModel.invalidations}")
        queueSnackbarMessage(SnackbarMessage.STUB, " | $selectedObjectsString;\n$selectedExpressionsString")
    }

    // MAYBE: make a suspend function + add load spinner
    fun loadNewConstellation(constellation: Constellation) {
        val updatedConstellation = constellation.updated()
        showPromptToSetActiveSelectionAsToolArg = false
        chessboardPattern = ChessboardPattern.NONE
        translation = Offset.Zero
        loadConstellation(updatedConstellation)
        // reset history on load
        history.clear()
        resetTransients()
        println("loaded new constellation")
        if (!mode.isSelectingCircles()) {
            selectTool(Tool.Drag)
        }
    }

    private fun loadConstellation(constellation: Constellation) {
        objectLabels = emptyMap()
        regions = emptyList() // important, since draws are async (otherwise can crash)
        selection = emptyList()
        objectModel.clearObjects()
        for (objectConstruct in constellation.objects) {
            val o = when (objectConstruct) {
                is ObjectConstruct.ConcreteCircle -> objectConstruct.circle
                is ObjectConstruct.ConcreteLine -> objectConstruct.line
                is ObjectConstruct.ConcretePoint -> objectConstruct.point
                is ObjectConstruct.Dynamic -> null // to-be-computed during reEval()
            }
            objectModel.addObject(o)
        }
        expressions = ExpressionForest(
            initialExpressions = constellation.toExpressionMap(),
            objects = objectModel.downscaledObjects,
        )
        expressions.reEval() // calculates all dependent objects
        objectModel.syncObjects()
//        expressions.update(
//            expressions.scaleLineIncidenceExpressions(DOWNSCALING_FACTOR)
//        )
        val objectIndices = objects.indices.toSet()
        regions = constellation.parts
            .filter { part -> // region validation
                part.insides.all { it in objectIndices } &&
                part.outsides.all { it in objectIndices }
            }
        for ((ix, color) in constellation.objectColors) {
            if (ix in objectIndices) {
                objectModel.objectColors[ix] = color
            }
        }
        backgroundColor = constellation.backgroundColor
        for (phantomIndex in constellation.phantoms)
            if (phantomIndex in objectIndices) {
                objectModel.phantomObjectIndices.add(phantomIndex)
            }
        objectLabels = constellation.objectLabels
        objectModel.invalidate()
    }

    fun toConstellation(): Constellation {
        // pruning nulls
        val deleted = objects.indices.filter { ix ->
            objects[ix] == null && isFree(ix)
        }.toSet()
        // reindexing because of pruning
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
            objectColors = objectModel.objectColors.mapNotNull { (ix, color) ->
                reindexing[ix]?.let { it to color }
            }.toMap(),
            objectLabels = objectLabels.mapNotNull { (ix, label) ->
                reindexing[ix]?.let { it to label }
            }.toMap(),
            backgroundColor = backgroundColor,
            // NOTE: we keep track of phantoms EVEN when they are shown
            phantoms = objectModel.phantomObjectIndices.mapNotNull { reindexing[it] },
        )
    }

    fun undo() {
        val m = mode
        if (m is ToolMode && partialArgList?.args?.isNotEmpty() == true) {
            // MAYBE: just pop the last arg
            partialArgList = PartialArgList(m.signature, m.nonEqualityConditions)
            if (submode is SubMode.ExprAdjustment) {
                // NOTE: this leaves one useless history entry
                cancelExprAdjustment()
            }
        } else {
            val currentSelection = selection.toList()
            when (submode) {
                is SubMode.RotateStereographicSphere -> switchToCategory(Category.Drag)
                else -> switchToMode(mode) // clears up stuff
            }
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
        submode = null
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
        submode = null
        undoIsEnabled = history.undoIsEnabled
        redoIsEnabled = history.redoIsEnabled
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
        if (command != Command.ROTATE) { // erm
            if (submode is SubMode.Rotate) {
                submode = null
            }
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
        val validNewGCircles = newGCircles.filterNotNull()
        if (validNewGCircles.isNotEmpty()) {
            showCircles = true
            val prevSize = objects.size
            objectModel.addObjects(newGCircles)
            selection = (prevSize until objects.size).filter { objects[it] != null }
            viewModelScope.launch {
                animations.emit(
                    CircleAnimation.Entrance(validNewGCircles.filterIsInstance<CircleOrLine>())
                )
            }
        } else { // all nulls
            objectModel.addObjects(newGCircles)
            selection = emptyList()
        }
        objectModel.invalidate()
    }

    fun createNewFreePoint(
        point: Point,
        triggerRecording: Boolean = true
    ): Ix {
        if (triggerRecording)
            recordCreateCommand()
        objectModel.addObject(point)
        val newIx = expressions.addFree()
        objectModel.invalidate()
        require(newIx == objects.size - 1) { "Incorrect index retrieved from expression.addFree() during createNewFreePoint()" }
        return newIx
    }

    /** Add objects from [sourceIndex2NewTrajectory] to [objects], while
     * copying regions (for [CircleOrLine]s) and [objectColors] from original
     * indices specified in [sourceIndex2NewTrajectory].
     * We assume that appropriate expressions were/will be created and
     * that those expressions follow the order of [sourceIndex2NewTrajectory]`.flatten()`, but
     * the objects themselves are yet to be added to [objects]. In addition, set
     * new objects that are circles/lines/points as [selection].
     * @param[sourceIndex2NewTrajectory] `[(original index ~ style source, [new trajectory of objects])]`,
     * note that original indices CAN repeat (tho its regions will be copied only once even for the repeats).
     * @param[circleAnimationInit] given list of circles/lines queue [CircleAnimation]
     * constructed by this block. Use `{ null }` if no animation is required.
     * @param[flipRegionsInAndOut] set to `true` for odd number of inversions (non-continuous)
     */
    private inline fun copyRegionsAndStylesOntoNewTrajectories(
        sourceIndex2NewTrajectory: List<Pair<Ix, List<GCircle?>>>,
        flipRegionsInAndOut: Boolean = false,
        crossinline circleAnimationInit: (List<CircleOrLine>) -> CircleAnimation? = { null },
    ) {
        val oldSize = objects.size
        val newObjects = sourceIndex2NewTrajectory.flatMap { it.second }
        objectModel.addObjects(newObjects) // row-column order
        objectModel.copySourceColorsOntoTrajectories(sourceIndex2NewTrajectory, oldSize)
        copySourceRegionsOntoTrajectories(sourceIndex2NewTrajectory, oldSize, flipRegionsInAndOut)
        selection = (oldSize until objects.size).filter { ix ->
            objects[ix] is CircleOrLine || objects[ix] is Point
        }
        objectModel.invalidate()
        circleAnimationInit(newObjects.filterIsInstance<CircleOrLine>())?.let { circleAnimation ->
            viewModelScope.launch {
                animations.emit(circleAnimation)
            }
        }
    }

    /**
     * Copy [regions] from source indices onto trajectories specified
     * by [sourceIndex2NewTrajectory]. Trajectory objects are assumed to be laid out in
     * row-column order of [sourceIndex2NewTrajectory]`.flatten` starting from [startIndex]
     * @param[sourceIndex2NewTrajectory] `[(original index ~ style source, [new trajectory of objects])]`,
     * note that original indices CAN repeat (tho its regions will be copied only once even for the repeats).
     * @param[flipRegionsInAndOut] set to `true` for odd number of inversions (non-continuous)
     * @return indices of copied regions within [regions]
     */
    private fun copySourceRegionsOntoTrajectories(
        sourceIndex2NewTrajectory: List<Pair<Ix, List<GCircle?>>>,
        startIndex: Ix,
        flipRegionsInAndOut: Boolean = false,
    ): List<Int> {
        val newRegionIndices = mutableListOf<Int>()
        var outputIndex = startIndex - 1 // -1 offsets pre-increment
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
            if (nonNullSlice.isNotEmpty()) {
                newRegionIndices.addAll(
                    copyRegions(
                        oldIndices = nonNullSlice.map { it.first },
                        newIndices = nonNullSlice.map { it.second },
                        flipInAndOut = flipRegionsInAndOut
                    )
                )
            }
        }
        return newRegionIndices
    }

    /**
     * Copy [regions] from source indices onto trajectories specified
     * by [sourceIndex2TrajectoryOfIndices].
     * @param[sourceIndex2TrajectoryOfIndices] `[(original index ~ style source, [trajectory of indices of objects])]`,
     * note that original indices CAN repeat (tho its regions will be copied only once even for the repeats).
     * @param[flipRegionsInAndOut] set to `true` for odd number of inversions (non-continuous)
     * @return indices of copied regions within [regions]
     */
    private fun copySourceRegionsOntoTrajectories(
        sourceIndex2TrajectoryOfIndices: List<Pair<Ix, List<Ix>>>,
        flipRegionsInAndOut: Boolean = false,
    ): List<Int> {
        val newRegionIndices = mutableListOf<Int>()
        sourceIndex2TrajectoryOfIndices.map { (sourceIndex, trajectory) ->
            trajectory.map { outputIndex ->
                sourceIndex to outputIndex
            } // Column<Row<(OG Ix, new Ix)?>>
        }.transpose().forEach { trajectoryStageSlice ->
            // Column<(OG Ix, new Ix)>
            val nonNullSlice = trajectoryStageSlice.filterNotNull()
            // for each stage in the trajectory we try to copy regions
            if (nonNullSlice.isNotEmpty()) {
                newRegionIndices.addAll(
                    copyRegions(
                        oldIndices = nonNullSlice.map { it.first },
                        newIndices = nonNullSlice.map { it.second },
                        flipInAndOut = flipRegionsInAndOut
                    )
                )
            }
        }
        return newRegionIndices
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
     * @param[flipRegionsInAndOut] set to `true` for odd number of inversions (non-continuous)
     */
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
        objectModel.addObjects(sourceIndex2NewObject.map { it.second })
        for ((i, sourceIndex) in sourceIndices.withIndex()) {
            objectModel.objectColors[sourceIndex]?.let { color ->
                val correspondingIx = oldSize + i
                objectModel.objectColors[correspondingIx] = color
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
                animations.emit(circleAnimation)
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
                objectModel.invalidate()
            }
        }
    }

    /**
     * Copy [LogicalRegion]s defined by [oldIndices] onto [newIndices].
     * [oldIndices].size must be [newIndices].size
     * @param[flipInAndOut] when `true` new region's insides use old region's outsides and
     * and vice versa
     * @return indices of the new regions within [regions]
     */
    private fun copyRegions(
        oldIndices: List<Ix>,
        newIndices: List<Ix>,
        flipInAndOut: Boolean = false,
    ): IntRange {
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
        val oldSize = regions.size
        regions += newRegions
        return oldSize until regions.size
    }

    fun deleteSelectedPointsAndCircles() {
        if (showCircles && selection.isNotEmpty() && mode.isSelectingCircles()) {
            deleteObjectsWithDependenciesColorsAndRegions(selection)
            selection = emptyList()
        }
    }

    private inline fun deleteObjectsWithDependenciesColorsAndRegions(
        indicesToDelete: List<Ix>,
        triggerRecording: Boolean = true,
        crossinline circleAnimationInit: (List<CircleOrLine>) -> CircleAnimation? = { deletedCircles ->
            CircleAnimation.Exit(deletedCircles)
        },
    ) {
        if (triggerRecording) {
            recordCommand(Command.DELETE, unique = true)
        }
        val toBeDeleted = expressions.deleteNodes(indicesToDelete)
        val deletedCircleIndices = toBeDeleted
            .filter { objects[it] is CircleOrLine }
            .toSet()
        if (deletedCircleIndices.isNotEmpty()) {
            val everythingIsDeleted = deletedCircleIndices.containsAll(
                objects.filterIndices { it is CircleOrLine }
            )
            val oldRegions = regions.toList()
            regions = emptyList()
            if (everythingIsDeleted) {
                if (chessboardPattern == ChessboardPattern.STARTS_COLORED) {
                    chessboardPattern = ChessboardPattern.STARTS_TRANSPARENT
                }
            } else {
                regions = oldRegions
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
            }
            val deletedCircles = deletedCircleIndices.mapNotNull { objects[it] as? CircleOrLine }
            circleAnimationInit(deletedCircles)?.let { circleAnimation ->
                viewModelScope.launch {
                    animations.emit(circleAnimation)
                }
            }
        }
        objectModel.removeObjectsAt(toBeDeleted.toList())
        objectModel.invalidate()
    }

    fun getArg(arg: Arg): GCircle? =
        when (arg) {
            is Arg.Index -> objects[arg.index]
            is Arg.PointXY -> arg.toPoint()
            is Arg.Indices -> null
            is Arg.InfinitePoint -> Point.CONFORMAL_INFINITY
        }

    fun switchToMode(newMode: Mode) {
        // NOTE: these altering shortcuts are unused for now so that they don't confuse category-expand buttons
        if (selection.size > 1 && newMode == SelectionMode.Drag)
            selection = emptyList()
        showPromptToSetActiveSelectionAsToolArg = false
        if (newMode is ToolMode) {
            if (selection.size > 1 &&
                newMode.signature.argTypes.first() == ArgType.INDICES
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
                partialArgList = PartialArgList(newMode.signature, newMode.nonEqualityConditions)
            }
        } else {
            partialArgList = null
        }
        mode = newMode
        submode = null
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
                .filter { isFree(it) }
                .toSet()
        )
        return nearPointIndex
    }

    /**
     * @param[targets] to select [ImaginaryCircle] convert it to real [Circle]
     */
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
        val circles = objects.map { o ->
            when (o) {
                is Circle -> o
                is Line -> o
                // imaginary circles selected as if they were real
                is ImaginaryCircle -> Circle(o.x, o.y, o.radius)
                else -> null
            }
        }
        val nearCircleIndex = selectCircle(circles, visiblePosition,
            priorityTargets = circles.indices
                .filter { isFree(it) }
                .toSet()
        )
        return nearCircleIndex
    }

    /** [selectCircle] & add/remove it from selection if it's new/already in */
    fun xorSelectCircleAt(visiblePosition: Offset): Ix? {
        val circles = objects.map { o ->
            when (o) {
                is Circle -> o
                is Line -> o
                is ImaginaryCircle -> Circle(o.x, o.y, o.radius)
                else -> null
            }
        }
        return selectCircle(circles, visiblePosition)?.also { ix ->
            if (ix in selection)
                selection -= ix
            else
                selection += ix
        }
    }

    private fun findSiblingsAndParents(ix: Ix): List<Ix> {
        val expr = exprOf(ix) ?: return emptyList()
        val parents = expr.args
        val siblings = expressions.findExpr(expr)
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
                    regions += region
                    if (shouldUpdateSelection) {
                        selection = (region.insides + region.outsides).toList()
                    }
                    println("added $region")
                } else if (outerRegions.size == 1) {
                    val i = outerRegionsIndices.single()
                    val outer = outerRegions.single()
                    if (region.fillColor == outer.fillColor) {
                        recordCommand(Command.FILL_REGION, unique = true)
                        regions = regions.withoutElementAt(i)
                        println("removed singular same-color outer $outer")
                    } else { // we are trying to change the color im guessing
                        recordCommand(Command.FILL_REGION, target = i)
                        regions = regions.updated(i, outer.copy(fillColor = region.fillColor))
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
                        regions -= sameRegions
                        println("removed all same-bounds same-color $sameBoundsSameColorRegionsIndices ~ $region")
                    } else { // we are trying to change the color im guessing
                        val i = sameBoundsRegionsIndices.last()
                        if (sameBoundsRegionsIndices.size == 1)
                            recordCommand(Command.FILL_REGION, target = i)
                        else // cleanup can shift region index
                            recordCommand(Command.FILL_REGION, unique = true)
                        val _regions = regions.toMutableList()
                        _regions[i] = region
                        sameBoundsRegions
                            .dropLast(1)
                            .forEach {
                                _regions.remove(it) // cleanup
                            }
                        regions = _regions
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
                        regions = regions.filter { it !in outerRegionsOfTheSameColor }
                        println("removed same color regions [${outerRegionsOfTheSameColor.joinToString(prefix = "\n", separator = ";\n")}]")
                    } else { // there are several outer regions, but none of the color of region.fillColor
                        recordCommand(Command.FILL_REGION, target = regions.size)
                        regions += region
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
                    regions += region
                    if (shouldUpdateSelection) {
                        selection = (region.insides + region.outsides).toList()
                    }
                    println("added $region")
                } else if (sameBoundsRegions.last().fillColor == region.fillColor) {
                    // im gonna cleanup same bounds until only 1 is left
                    // cleanup & skip
                    val _regions = regions.toMutableList()
                    sameBoundsRegions
                        .dropLast(1)
                        .forEach {
                            _regions.remove(it) // cannot use removeAll cuz it could remove the last one too
                        }
                    regions = _regions
                } else { // same bounds, different color
                    // replace & cleanup
                    val i = sameBoundsRegionsIndices.last()
                    if (sameBoundsRegionsIndices.size == 1)
                        recordCommand(Command.FILL_REGION, target = i)
                    else
                        recordCommand(Command.FILL_REGION, unique = true)
                    val _regions = regions.toMutableList()
                    _regions[i] = region
                    if (shouldUpdateSelection) {
                        selection = (region.insides + region.outsides).toList()
                    }
                    sameBoundsRegions
                        .dropLast(1)
                        .forEach {
                            _regions.remove(it) // cannot use removeAll cuz it could remove the last one too
                        }
                    regions = _regions
                    println("recolored $i (same bounds ~ $region)")
                }
            }
            RegionManipulationStrategy.ERASE -> {
                if (sameBoundsRegions.isNotEmpty()) {
                    recordCommand(Command.FILL_REGION, unique = true)
                    regions = regions.filter { it !in sameBoundsRegions }
                    println("removed [${sameBoundsRegionsIndices.joinToString(prefix = "\n", separator = ";\n")}] (same bounds ~ $region)")
                } else if (outerRegions.isNotEmpty()) {
                    // maybe find minimal and erase it OR remove last outer
                    // tho it would stop working like eraser then
                    recordCommand(Command.FILL_REGION, unique = true)
                    regions = regions.filter { it !in outerRegions }
                    println("removed outer [${outerRegions.joinToString(prefix = "\n", separator = ";\n")}]")
                } // when clicking on nowhere nothing happens
            }
        }
    }

    /** @return [Rect] using absolute positions */
    fun getSelectionRect(): Rect? {
        val selectedCircles = selection.mapNotNull { objects[it] as? Circle }
        if (selectedCircles.isEmpty() || selection.any { objects[it] is Line })
            return null
        val left = selectedCircles.minOf { (it.x - it.radius).toFloat() }
        val right = selectedCircles.maxOf { (it.x + it.radius).toFloat() }
        val top = selectedCircles.minOf { (it.y - it.radius) }.toFloat()
        val bottom = selectedCircles.maxOf { (it.y + it.radius) }.toFloat()
        return Rect(left, top, right, bottom)
    }

    fun exprOf(index: Ix): Expr? =
        expressions.expressions[index]?.expr

    fun isFree(index: Ix): Boolean =
        expressions.expressions[index] == null

    fun isConstrained(index: Ix): Boolean =
        expressions.expressions[index]?.expr is Expr.Incidence

    // MAYBE: wrap into state that depends only on [regions] for caching
    // MAYBE: also add backgroundColor (tho it is MT.surface by default and thus 0-contrast)
    fun getColorsByMostUsed(): List<Color> =
        regions
            .flatMap { region ->
                region.borderColor?.let { listOf(region.fillColor, it) }
                    ?: listOf(region.fillColor)
            }
            .plus(objectModel.objectColors.values)
            .plus(
                if (chessboardPattern == ChessboardPattern.NONE)
                    emptyList()
                else
                    listOf(chessboardColor)
            )
            .sortedByFrequency()

    /**
     * Try to snap [absolutePosition] to some existing object or their intersection.
     * Snap priority: points > circles
     */
    fun snapped(
        absolutePosition: Offset,
        excludePoints: Boolean = false,
        excludedCircles: Set<Ix> = emptySet(),
    ): PointSnapResult {
        val snapDistance = tapRadius.toDouble()
        val point = Point.fromOffset(absolutePosition)
        val point2pointSnapping = !excludePoints && mode != ToolMode.POINT
        if (point2pointSnapping) {
            val snappablePoints = objects.mapIndexed { ix, o ->
                if (!showPhantomObjects && ix in phantoms)
                    null
                else
                    o as? Point
            }
            val p2pResult = snapPointToPoints(point, snappablePoints, snapDistance)
            if (p2pResult is PointSnapResult.Eq)
                return p2pResult
        }
        val point2circleSnapping = showCircles
        if (!point2circleSnapping) // no snapping to invisibles
            return PointSnapResult.Free(point)
        val snappableCircles = objects.mapIndexed { ix, c ->
            if (ix in excludedCircles || !showPhantomObjects && ix in phantoms) null
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
                objectModel.addObjects(listOf(newPoint))
                objectModel.invalidate()
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
                        objectModel.addDownscaledObject(p)
                        objectModel.invalidate()
                        PointSnapResult.Eq(snapResult.result, oldSize)
                    } else {
                        val ps = expressions.addMultiExpr(expr)
                            .map { it as? Point }
                        objectModel.addDownscaledObjects(ps)
                        objectModel.invalidate()
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
        val allCLPIndices = objects.filterIndices { it is CircleOrLineOrPoint }
        val everythingIsSelected = selection.containsAll(allCLPIndices)
        selection = if (everythingIsSelected) {
            emptyList()
        } else { // maybe select Imaginary's and nulls too
            allCLPIndices
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

    fun toggleStereographicRotationMode() {
        if (mode == ViewMode.StereographicRotation) {
            submode = null
            switchToCategory(Category.Drag)
        } else {
            switchToMode(ViewMode.StereographicRotation)
            val sphereProjection = Circle(
                computeAbsoluteCenter() ?: Offset.Zero,
                // sphere radius == equator radius
                min(canvasSize.width/2.0, canvasSize.height/2.0)
            )
            submode = SubMode.RotateStereographicSphere(
                sphereRadius = sphereProjection.radius,
                grabbedTarget = sphereProjection.center,
                south = sphereProjection.centerPoint,
                grid = generateSphereGrid(
                    sphereProjection,
                    angleStep = SubMode.RotateStereographicSphere.GRID_ANGLE_STEP
                ),
            )
        }
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

    fun concludeRegionColorPicker(colorPickerParameters: ColorPickerParameters) {
        openedDialog = null
        regionColor = colorPickerParameters.currentColor
        this.colorPickerParameters = colorPickerParameters
        switchToCategory(Category.Region)
    }

    fun concludeCircleColorPicker(colorPickerParameters: ColorPickerParameters) {
        recordCommand(Command.CHANGE_COLOR)
        for (ix in selection) {
            objectModel.objectColors[ix] = colorPickerParameters.currentColor
        }
        openedDialog = null
        this.colorPickerParameters = colorPickerParameters
        objectModel.invalidate()
    }

    fun concludeBackgroundColorPicker(colorPickerParameters: ColorPickerParameters) {
        recordCommand(Command.CHANGE_COLOR)
        backgroundColor = colorPickerParameters.currentColor
        openedDialog = null
        this.colorPickerParameters = colorPickerParameters
    }

    fun setNewRegionColorToSelectedColorSplash(color: Color) {
        openedDialog = null
        regionColor = color
        switchToCategory(Category.Region)
    }

    fun dismissRegionColorPicker() {
        openedDialog = null
        val tool = mode.tool
        val category = toolbarState.categories.firstOrNull { tool in it.tools }
        if (category != null)
            selectCategory(category, togglePanel = true)
        toolbarState = toolbarState.copy(activeTool = tool)
    }

    fun getMostCommonCircleColorInSelection(): Color? {
        hug(objectModel.invalidations)
        return selection
            .mapNotNull { objectModel.objectColors[it] }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { (_, k) -> k }
            ?.key
    }

    fun dismissCircleColorPicker() {
        openedDialog = null
    }

    fun dismissBackgroundColorPicker() {
        openedDialog = null
    }

    // MAYBE: replace with select-all->delete in invisible-circles region manipulation mode
    fun deleteAllRegions() {
        recordCommand(Command.DELETE, unique = true)
        chessboardPattern = ChessboardPattern.NONE
        regions = emptyList()
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
                tool is Tool.MultiArg &&
                tool.signature.argTypes.first() == ArgType.INDICES &&
                selection.isNotEmpty()
            ) { "Illegal state in setActiveSelectionAsToolArg(): tool = $tool, selection == $selection" }
        }
        partialArgList = partialArgList!!.addArg(
            Arg.Indices(selection.filter { objects[it] != null }),
            confirmThisArg = true
        )
        cancelSelectionAsToolArgPrompt()
        if (partialArgList!!.isFull) {
            completeToolMode()
        }
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
                if (rect == null || rect.minDimension >= 5_000)
                    computeAbsoluteCenter() ?: Offset.Zero
                else rect.center
            transformWhatWeCan(Command.SCALE, selection, focus = focus, zoom = zoom)
        } else if (mode == ToolMode.ARC_PATH && partialArcPath != null) {
//            arcPathUnderConstruction = arcPathUnderConstruction?.scale(zoom)
        } else {
            val targets = objects.indices.toList()
            val center = computeAbsoluteCenter() ?: Offset.Zero
            recordCommand(Command.SCALE, selection)
            objectModel.transform(expressions, targets, focus = center, zoom = zoom)
        }
    }

    private fun detachEverySelectedObject() {
        recordCommand(Command.CHANGE_EXPRESSION)
        for (ix in selection) {
            expressions.changeToFree(ix)
        }
        selectionIsLockedTrigger = !selectionIsLockedTrigger
    }

    fun setLabel(label: String?) {
        val labels = objectLabels.toMutableMap()
        if (label == null) {
            labels -= selection.toSet()
        } else {
            for (ix in selection) {
                labels[ix] = label
            }
        }
        objectLabels = labels.toMap()
        openedDialog = null
    }

    private fun markSelectedObjectsAsPhantoms() {
        objectModel.phantomObjectIndices.addAll(selection)
        // selection = emptyList() // being able to instantly undo is prob better ux
        // showPhantomObjects = false // i think this behavior is confuzzling
        objectModel.invalidate()
    }

    private fun unmarkSelectedObjectsAsPhantoms() {
        objectModel.phantomObjectIndices.removeAll(selection.toSet())
        objectModel.invalidate()
    }

    private fun swapDirectionsOfSelectedCircles() {
        val targets = selection.filter { ix ->
            objects[ix] is CircleOrLine && isFree(ix)
        }
        if (targets.isEmpty()) {
            if (targets.size == 1)
                queueSnackbarMessage(SnackbarMessage.LOCKED_OBJECT_NOTICE)
            else if (targets.size > 1)
                queueSnackbarMessage(SnackbarMessage.LOCKED_OBJECTS_NOTICE)
        } else {
            recordCommand(Command.ROTATE, targets = targets) // hijacking rotation
            objectModel.setObjectsWithConsequences(
                expressions,
                targets.associateWith { ix ->
                    val obj0 = objects[ix] as CircleOrLine
                    obj0.reversed()
                }
            )
        }
    }

    inline val showAdjustExprButton: Boolean get() {
        val sel = selection
        return sel.isNotEmpty() && (exprOf(sel[0])?.let { expr0 ->
            (expr0 is Expr.CircleInterpolation ||
                expr0 is Expr.PointInterpolation ||
                expr0 is Expr.Rotation ||
                expr0 is Expr.BiInversion ||
                expr0 is Expr.LoxodromicMotion) &&
            sel.all { exprOf(it) == expr0 }
        } == true)
    }

    fun adjustExpr() {
        val expr = exprOf(selection[0])
        if (expr !is Expr.HasParameters) {
            return
        }
        val outputIndices = expressions.findExpr(expr)
        val tool = when (expr) {
            is Expr.CircleInterpolation -> Tool.CircleOrPointInterpolation
            is Expr.PointInterpolation -> Tool.CircleOrPointInterpolation
            is Expr.Rotation -> Tool.Rotation
            is Expr.BiInversion -> Tool.BiInversion
            is Expr.LoxodromicMotion -> Tool.LoxodromicMotion
            else -> null
        }
        when (val params = expr.parameters) {
            is InterpolationParameters ->
                defaultInterpolationParameters = DefaultInterpolationParameters(params)
            is RotationParameters ->
                defaultRotationParameters = DefaultRotationParameters(params)
            is BiInversionParameters ->
                defaultBiInversionParameters = DefaultBiInversionParameters(params)
            is LoxodromicMotionParameters ->
                // bidirectionality might be overridden further down
                defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(params, bidirectional = false)
            else -> {}
        }
        if (tool != null) {
            recordCreateCommand() // create savepoint to go back to on cancel
            partialArgList = when (expr) {
                is Expr.CircleInterpolation ->
                    PartialArgList(
                        tool.signature,
                        tool.nonEqualityConditions,
                        args = listOf(
                            Arg.IndexOf(expr.startCircle, objects[expr.startCircle]!!),
                            Arg.IndexOf(expr.endCircle, objects[expr.endCircle]!!)
                        )
                    )
                is Expr.PointInterpolation ->
                    PartialArgList(
                        tool.signature,
                        tool.nonEqualityConditions,
                        args = listOf(
                            Arg.PointIndex(expr.startPoint),
                            Arg.PointIndex(expr.endPoint)
                        )
                    )
                is Expr.Rotation ->
                    PartialArgList(
                        tool.signature,
                        tool.nonEqualityConditions,
                        args = listOf(
                            Arg.Indices(listOf(expr.target)),
                            Arg.PointIndex(expr.pivot),
                        )
                    )
                is Expr.BiInversion ->
                    PartialArgList(
                        tool.signature,
                        tool.nonEqualityConditions,
                        args = listOf(
                            Arg.Indices(listOf(expr.target)),
                            Arg.IndexOf(expr.engine1, objects[expr.engine1]!!),
                            Arg.IndexOf(expr.engine2, objects[expr.engine2]!!),
                        )
                    )
                is Expr.LoxodromicMotion ->
                    PartialArgList(
                        tool.signature,
                        tool.nonEqualityConditions,
                        args = listOf(
                            Arg.Indices(listOf(expr.target)),
                            Arg.PointIndex(expr.divergencePoint),
                            Arg.PointIndex(expr.convergencePoint),
                        )
                    )
                else -> null
            }
            val adjustables = listOf(AdjustableExpr(expr, outputIndices, outputIndices))
            val allAdjustables: List<AdjustableExpr> =
                if (
                    expr is Expr.LoxodromicMotion &&
                    expr.otherHalfStart != null
                ) {
                    exprOf(expr.otherHalfStart)?.let { otherExpr ->
                        val otherOutputIndices = expressions.findExpr(otherExpr)
                        val otherAdjustables = listOf(AdjustableExpr(otherExpr, otherOutputIndices, otherOutputIndices))
                        defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(
                            expr.parameters,
                            bidirectional = true
                        )
                        adjustables + otherAdjustables
                    } ?: adjustables
                } else adjustables
            submode = SubMode.ExprAdjustment(allAdjustables)
            selection = emptyList() // clear selection to hide selection HUD
        }
    }

    // might be useful for duplication with dependencies
    /** For each object in [selection], add to selection its siblings and parents */
    private fun expandSelectionToFamily() {
        if (mode.isSelectingCircles()) {
            val familyMembers = selection.flatMap { ix ->
                listOf(ix) + findSiblingsAndParents(ix)
            }.distinct()
            if (familyMembers.size > 1 && mode == SelectionMode.Drag) {
                switchToMode(SelectionMode.Multiselect)
            }
            selection = familyMembers
        }
    }

    /** sibling = has the same [Expr] (possibly `null`) */
    private fun expandSelectionToAllSiblings() {
        if (mode.isSelectingCircles()) {
            val siblings = selection
                .map { exprOf(it) }
                .distinct()
                .flatMap { expr ->
                    expressions.findExpr(expr)
                }
            if (mode == SelectionMode.Drag && siblings.size > 1) {
                switchToMode(SelectionMode.Multiselect)
            }
            selection = siblings
        }
    }

    fun onDown(visiblePosition: Offset) {
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
            // NOTE: this enables drag-only behavior, you lose your selection when grabbing new circle
            if (mode == SelectionMode.Drag && submode == null) {
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
                } else if (mode is ToolMode && partialArgList?.isFull != true) {
                    when (mode) {
                        ToolMode.ARC_PATH -> downArcPathPoint(visiblePosition)
                        else -> downToolArg(visiblePosition)
                    }
                }
            }
        }
        // should work independent of circle visibility
        when (val sm = submode) {
            is SubMode.RotateStereographicSphere ->
                submode = sm.copy(
                    grabbedTarget = absolute(visiblePosition),
                )
            else -> {}
        }
    }

    private fun downToolArg(visiblePosition: Offset) {
        val argList = partialArgList
        val nextType = argList?.nextArgType
        if (nextType != null) {
            val inInterpolationMode = mode == ToolMode.CIRCLE_OR_POINT_INTERPOLATION
            val inFastCenteredCircle =
                FAST_CENTERED_CIRCLE && mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS
            /** flags whether we already selected/found an object and there's no
             * more need to proceed further */
            var found = false
            var pointSnap: PointSnapResult? = null
            // try selecting an existing (indexed) point
            if (Arg.PointIndex in nextType.possibleTypes) {
                pointSnap = snapped(absolute(visiblePosition))
                when (pointSnap) {
                    is PointSnapResult.Eq -> {
                        val newArg = Arg.PointIndex(pointSnap.pointIndex)
                        if (inFastCenteredCircle && argList.currentArg == null) {
                            val newArg2 = Arg.PointXY(pointSnap.result)
                            partialArgList = argList
                                .addArg(newArg, confirmThisArg = true)
                                .addArg(newArg2, confirmThisArg = false)
                                .copy(lastSnap = pointSnap)
                            found = true
                        } else {
                            val sameArgsForInterpolation =
                                inInterpolationMode entails
                                    (argList.args.isEmpty() || argList.currentArg is Arg.Point)
                            if (argList.validateNewArg(newArg) && sameArgsForInterpolation) {
                                partialArgList = argList
                                    .addArg(newArg, confirmThisArg = false)
                                    .copy(lastSnap = pointSnap)
                            }
                            found = true
                        }
                    }
                    else -> {}
                }
            }
            // try selecting an existing (indexed) object
            if (!found &&
                (inInterpolationMode entails
                    (argList.currentArg?.type !is Arg.Type.Point))
            ) {
                val acceptsCircle = Arg.CircleIndex in nextType.possibleTypes
                val acceptsLine = Arg.LineIndex in nextType.possibleTypes
                val acceptsImaginaryCircle = Arg.ImaginaryCircleIndex in nextType.possibleTypes
                val acceptsCLI = acceptsCircle || acceptsLine || acceptsImaginaryCircle
                if (acceptsCLI) {
                    val selectableObjects = objects.map { o ->
                        when (o) {
                            is Circle -> if (acceptsCircle) o else null
                            is Line -> if (acceptsLine) o else null
                            is ImaginaryCircle ->
                                if (acceptsImaginaryCircle) o.toRealCircle() else null
                            else -> null
                        }
                    }
                    selectCircle(selectableObjects, visiblePosition)?.let { ix ->
                        val newArg = Arg.IndexOf(ix, objects[ix]!!)
                        // test non-equality conditions
                        if (argList.validateNewArg(newArg)) {
                            val confirm = !inInterpolationMode
                            partialArgList = argList.addArg(newArg, confirmThisArg = confirm)
                        }
                        found = true
                    }
                }
            }
            // try selecting a new point
            if (!found && Arg.PointXY in nextType.possibleTypes) {
                val snap = pointSnap ?: snapped(absolute(visiblePosition))
                if (inFastCenteredCircle && argList.currentArg == null) {
                    // we have to realize the first point here so we don't forget its
                    // snap after panning
                    val newArg = realizePointCircleSnap(snap).toArgPoint()
                    val newArg2 = Arg.PointXY(snap.result)
                    partialArgList = argList
                        .addArg(newArg, confirmThisArg = true)
                        .addArg(newArg2, confirmThisArg = false)
                        .copy(lastSnap = pointSnap)
                    found = true
                } else if (
                // first point-interpolation arg cannot be XY ig
                    inInterpolationMode entails (argList.currentArg is Arg.Point)
                ) {
                    val newArg = Arg.PointXY(snap.result)
                    if (argList.validateNewArg(newArg)) {
                        partialArgList = argList
                            .addArg(newArg, confirmThisArg = false)
                            .copy(lastSnap = snap)
                    }
                    found = true
                }
            }
            // try selecting an existing object (singular as a group)
            if (!found && Arg.Indices in nextType.possibleTypes) {
                val points = objects.map { it as? Point }
                val selectedPointIndex = selectPoint(points, visiblePosition)
                if (selectedPointIndex == null) {
                    val circles = objects.map { o ->
                        when (o) {
                            is Circle -> o
                            is Line -> o
                            is ImaginaryCircle -> o.toRealCircle()
                            else -> null
                        }
                    }
                    val selectedCircleIndex = selectCircle(circles, visiblePosition)
                    if (selectedCircleIndex != null) {
                        val newArg = Arg.Indices(listOf(selectedCircleIndex))
                        if (argList.validateNewArg(newArg)) {
                            partialArgList = argList.addArg(newArg, confirmThisArg = true)
                        }
                        found = true
                    }
                } else {
                    val newArg = Arg.Indices(listOf(selectedPointIndex))
                    if (argList.validateNewArg(newArg)) {
                        partialArgList = argList.addArg(newArg, confirmThisArg = true)
                    }
                    found = true
                }
            }
        }
    }

    private fun addInfinitePointArg() {
        val argList = partialArgList
        require(
            argList != null && !argList.isFull &&
            argList.nextArgType?.let { nextArgType ->
                Arg.InfinitePoint in nextArgType.possibleTypes ||
                Arg.Indices in nextArgType.possibleTypes
            } == true
        )
        val infinityIndex = objectModel.getInfinityIndex() ?:
            createNewFreePoint(Point.CONFORMAL_INFINITY, triggerRecording = false)
        val newArg =
            if (argList.nextArgType.let { Arg.Indices in it.possibleTypes } == true)
                Arg.Indices(listOf(infinityIndex))
            else
                Arg.PointIndex(infinityIndex)
        if (argList.validateNewArg(newArg)) {
            partialArgList = argList.addArg(newArg, confirmThisArg = true)
            if (partialArgList?.isFull == true) {
                completeToolMode()
            }
        }
    }

    private fun movePointToInfinity() {
        selection.singleOrNull()?.let { ix ->
            val expr = exprOf(ix)
            if (expr == null) {
                recordCommand(Command.MOVE, target = ix)
                objectModel.setObjectWithConsequences(expressions, ix, Point.CONFORMAL_INFINITY)
            } else if (expr is Expr.Incidence && objects[expr.carrier] is Line) {
                recordCommand(Command.MOVE, target = ix)
                objectModel.changeExpr(
                    expressions,
                    ix,
                    expr.copy(parameters =
                        expr.parameters.copy(order = Line.ORDER_OF_CONFORMAL_INFINITY)
                    )
                )
            }
        }
    }

    private fun downArcPathPoint(visiblePosition: Offset) {
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
                        arcPath.addNewVertexAndGrabIt(ArcPathPoint.Free(absolutePoint))
                    }
                }
            }
        }
    }

    // pointer input callbacks
    // onDown -> onUp -> onTap OR
    // onDown -> onUp -> onDown! -> onTap -> onUp (i.e. double tap)
    fun onTap(position: Offset, pointerCount: Int) {
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
            transformWhatWeCan(Command.SCALE, listOf(ix), focus = center, zoom = (r/circle.radius).toFloat())
        } else if (circle is Line) {
            val center = circle.project(c)
            transformWhatWeCan(Command.SCALE, listOf(ix), focus = center, zoom = zoom)
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
        transformWhatWeCan(Command.ROTATE, listOf(h.ix), focus = center, rotationAngle = angle1)
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
    }

    private fun scaleSeveralCircles(pan: Offset, targets: List<Ix>) {
        getSelectionRect()?.let { rect ->
            val scaleHandlePosition = rect.topRight
            val center = rect.center
            val centerToHandle = scaleHandlePosition - center
            val centerToCurrent = centerToHandle + pan
            val scaleFactor = centerToCurrent.getDistance()/centerToHandle.getDistance()
            transformWhatWeCan(Command.SCALE, targets, focus = center, zoom = scaleFactor)
        }
    }

    fun scaleViaSlider(newSliderPercentage: Float) {
        val sm = when (val sm0 = submode) {
            is SubMode.ScaleViaSlider -> sm0
            else -> {
                val screenCenter = absolute(Offset(canvasSize.width/2f, canvasSize.height/2f))
                SubMode.ScaleViaSlider(screenCenter)
            }
        }
        val scaleFactor = sliderPercentageDeltaToZoom(newSliderPercentage - sm.sliderPercentage)
        transformWhatWeCan(Command.SCALE, selection, focus = sm.center, zoom = scaleFactor)
        submode = sm.copy(sliderPercentage = newSliderPercentage)
    }

    fun concludeSubmode() {
        submode = null
    }

    fun startHandleRotation() {
        submode = SubMode.Rotate(computeAbsoluteCenter() ?: Offset.Zero)
    }

    fun rotateViaHandle(newRotationAngle: Float) {
        when (val sm = submode) {
            is SubMode.Rotate -> {
                val newAngle = newRotationAngle.toDouble()
                val snappedAngle =
                    if (ENABLE_ANGLE_SNAPPING) snapAngle(newAngle)
                    else newAngle
                val dAngle = (snappedAngle - sm.snappedAngle).toFloat()
                transformWhatWeCan(Command.ROTATE, selection, focus = sm.center, rotationAngle = dAngle)
                submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
            }
            else -> {}
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
        transformWhatWeCan(Command.ROTATE, targets, focus = sm.center, rotationAngle = angle1)
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
    }

    // dragging circle: move + scale radius & rotate [line]
    private fun dragCircle(
        absoluteCentroid: Offset,
        translation: Offset, zoom: Float, rotationAngle: Float
    ) {
        val selectedIndex = selection.single()
        if (ENABLE_TANGENT_SNAPPING) {
            val circle = objects[selectedIndex] as CircleOrLine
            val result0 = circle.transformed(translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
                as CircleOrLine
            val snapDistance = tapRadius.toDouble()/TAP_RADIUS_TO_TANGENTIAL_SNAP_DISTANCE_FACTOR
            val excludedCircles =
                setOf(selectedIndex) +
                expressions.getAllChildren(selectedIndex) +
                expressions.getAllParents(listOf(selectedIndex))
            val snappableCLPs = objects.mapIndexed { ix, c ->
                if (ix in excludedCircles || !showPhantomObjects && ix in phantoms) null
                else c as? CircleOrLineOrPoint
            }
            val center = computeAbsoluteCenter()
            val absoluteVisibilityRect =
                if (center != null && canvasSize != IntSize.Zero)
                    Rect(
                        center.x - canvasSize.width/2f, center.y - canvasSize.height/2f,
                        center.x + canvasSize.width/2f, center.y + canvasSize.height/2f,
                    )
                else null
            val snap = snapCircleToCircles(
                result0, snappableCLPs,
                snapDistance = snapDistance,
                visibleRect = absoluteVisibilityRect,
            )
            val delta = result0 translationDelta snap.result
            transformWhatWeCan(Command.MOVE, listOf(selectedIndex), translation = translation + delta, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
        } else {
            transformWhatWeCan(Command.MOVE, listOf(selectedIndex), translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
        }
    }

    private fun dragPoint(absolutePointerPosition: Offset) {
        val ix = selection.first()
        val expr = exprOf(ix)
        if (expr is Expr.Incidence) {
            slidePointAcrossCarrier(pointIndex = ix, carrierIndex = expr.carrier, absolutePointerPosition = absolutePointerPosition)
        } else {
            val childCircles = expressions.getAllChildren(ix)
                .filter { objects[it] is CircleOrLine }
                .toSet()
            // when we are dragging intersection of 2 free circles with IoC1 we don't want it to snap to them
            val parents = expressions.getAllParents(listOf(ix))
            val newPoint = snapped(absolutePointerPosition,
                excludePoints = true, excludedCircles = childCircles + parents
            ).result
            transformWhatWeCan(
                Command.MOVE,
                listOf(ix),
                translation = newPoint.toOffset() - (objects[ix] as Point).toOffset(),
            )
        }
    }

    // special case that is not handled by transform()
    // MAYBE: instead transform then snap/project onto carrier and transform by snap-delta again
    private fun slidePointAcrossCarrier(
        pointIndex: Ix, carrierIndex: Ix,
        absolutePointerPosition: Offset
    ) {
        recordCommand(Command.MOVE, target = pointIndex)
        val carrier = objectModel.downscaledObjects[carrierIndex] as CircleOrLine
        val pointer = Point.fromOffset(absolutePointerPosition).downscale()
        val newPoint = carrier.project(pointer)
        val order = carrier.point2order(newPoint)
        val newExpr = Expr.Incidence(IncidenceParameters(order), carrierIndex)
        objectModel.changeExpr(expressions, pointIndex, newExpr)
    }

    private fun dragCirclesOrPoints(
        absoluteCentroid: Offset,
        translation: Offset,
        zoom: Float,
        rotationAngle: Float,
    ) {
        val targets = selection.filter {
            val o = objects[it]
            o is CircleOrLine || o is Point
        }
        transformWhatWeCan(Command.MOVE, targets,
            translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle
        )
    }

    // NOTE: polar lines and line-by-2 transform weirdly:
    //  it becomes circle during st-rot, but afterwards when
    //  its carrier is moved it becomes line again
    private fun stereographicallyRotateEverything(
        sm: SubMode.RotateStereographicSphere,
        absolutePointerPosition: Offset,
    ) {
        // MAYBE: wrap in try-catch
        // MAYBE: snap North & South to screen center
        val screenCenter = computeAbsoluteCenter() ?: Offset.Zero
        val biEngine = calculateStereographicRotationBiEngine(
            sphereProjection = Circle(screenCenter, sm.sphereRadius),
            start = Point.fromOffset(sm.grabbedTarget),
            end = Point.fromOffset(absolutePointerPosition),
        )
        if (biEngine != null) {
            recordCommand(Command.MOVE)
            // inlined computeBiInversion for efficiency
            val engine1 = biEngine.first.downscale()
            val engine2 = biEngine.second.downscale()
            val e1 = GeneralizedCircle.fromGCircle(engine1)
            val e2 = GeneralizedCircle.fromGCircle(engine2)
            val bivector0 = Rotor.fromPencil(e1, e2)
            val bivector = bivector0 * 0.5
            val rotor = bivector.exp() // alternatively bivector0.exp() * log(progress)
            for (ix in objectModel.downscaledObjects.indices) {
                val o = objectModel.downscaledObjects[ix]
                if (o != null) {
                    val newObject = rotor.applyTo(GeneralizedCircle.fromGCircle(o)).toGCircleAs(o)
                    objectModel.setDownscaledObject(ix, newObject)
                }
            }
            expressions.adjustIncidentPointExpressions()
            val newSouth = (rotor.applyTo(GeneralizedCircle.fromGCircle(
                sm.south.downscale()
            )).toGCircleAs(sm.south) as? Point)
                ?.upscale()
            val newGrid = sm.grid.mapNotNull { o ->
                (rotor.applyTo(GeneralizedCircle.fromGCircle(
                    o.downscale()
                )).toGCircleAs(o) as? CircleOrLine)
                    ?.upscale()
            }
            submode = sm.copy(
                grabbedTarget = absolutePointerPosition,
                south = newSouth ?: sm.south,
                grid = newGrid,
            )
            objectModel.invalidate()
        }
    }

    /**
     * Wrapper around [ObjectModel.transform] that adjusts [targets] based on [INVERSION_OF_CONTROL].
     *
     * [ObjectModel.transform] applies [translation];scaling;rotation
     * to [targets] (that are all assumed free).
     *
     * Scaling and rotation are w.r.t. fixed [focus] by the factor of
     * [zoom] and by [rotationAngle] degrees. If [focus] is [Offset.Unspecified] for
     * each circle choose its center, for each point -- itself, for each line -- screen center
     * projected onto it
     */
    private fun transformWhatWeCan(
        command: Command,
        targets: List<Ix>,
        translation: Offset = Offset.Zero,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
    ) {
        val actualTargets: List<Ix> =
            when (INVERSION_OF_CONTROL) {
                InversionOfControl.NONE ->
                    targets.filter { isFree(it) }
                InversionOfControl.LEVEL_1 -> {
                    targets.flatMap { targetIx ->
                        if (isFree(targetIx)) {
                            listOf(targetIx)
                        } else {
                            val parents = expressions.getImmediateParents(targetIx)
                            if (parents.all { isFree(it) })
                                parents
                            else emptyList()
                        }
                    }.distinct()
                }
                InversionOfControl.LEVEL_INFINITY -> {
                    (targets.toSet() + expressions.getAllParents(targets)).distinct()
                }
            }
        if (actualTargets.isEmpty()) {
            if (targets.size == 1) // not sure this is the right place for snackbar messages
                queueSnackbarMessage(SnackbarMessage.LOCKED_OBJECT_NOTICE)
            else
                queueSnackbarMessage(SnackbarMessage.LOCKED_OBJECTS_NOTICE)
        } else {
            recordCommand(command, actualTargets)
            objectModel.transform(expressions, actualTargets, translation, focus, zoom, rotationAngle)
        }
    }

    // MAYBE: handle key arrows as panning
    fun onPanZoomRotate(pan: Offset, centroid: Offset, zoom: Float, rotationAngle: Float) {
        movementAfterDown = true
        /** absolute cursor/pointer position */
        val c = absolute(centroid)
        val selectedCircles = selection.filter { objects[it] is CircleOrLineOrImaginaryCircle }
        val selectedPoints = selection.filter { objects[it] is Point }
        val sm = submode
        if (sm != null) {
            // drag handle
            when (val h = handleConfig) {
                is HandleConfig.SingleCircle -> {
                    when (sm) {
                        is SubMode.Scale -> scaleSingleCircle(c = c, zoom = zoom, h = h, sm = sm)
                        is SubMode.Rotate -> rotateSingleCircle(pan = pan, c = c, h = h, sm = sm)
                        else -> {}
                    }
                }
                is HandleConfig.SeveralCircles -> {
                    when (sm) {
                        is SubMode.Scale ->
                            scaleSeveralCircles(pan, selection)
                        is SubMode.Rotate ->
                            rotateSeveralCircles(pan = pan, c = c, sm = sm, targets = selection)
                        else -> {}
                    }
                }
                else -> {}
            }
            if (mode == SelectionMode.Multiselect && sm is SubMode.RectangularSelect) {
                val corner1 = sm.corner1
                val rect = Rect.fromCorners(corner1 ?: c, c)
                val selectables = objects.mapIndexed { ix, o ->
                    if (showPhantomObjects || ix !in phantoms) o else null
                }
                selection = selectWithRectangle(selectables, rect)
                submode = SubMode.RectangularSelect(corner1, c)
            } else if (mode == SelectionMode.Multiselect && sm is SubMode.FlowSelect) {
                val qualifiedRegion = sm.lastQualifiedRegion
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
            } else if (mode == SelectionMode.Region && sm is SubMode.FlowFill) {
                val qualifiedRegion = sm.lastQualifiedRegion
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
            } else if (mode == ViewMode.StereographicRotation && sm is SubMode.RotateStereographicSphere) {
                stereographicallyRotateEverything(sm, c)
            }
        } else if (mode == SelectionMode.Drag && selectedCircles.isNotEmpty() && showCircles) {
            dragCircle(absoluteCentroid = c, translation = pan, zoom = zoom, rotationAngle = rotationAngle)
        } else if (mode == SelectionMode.Drag && selectedPoints.isNotEmpty() && showCircles) {
            dragPoint(absolutePointerPosition = c)
        } else if (
            mode == SelectionMode.Multiselect &&
            (selectedCircles.isNotEmpty() && showCircles || selectedPoints.isNotEmpty())
        ) {
            dragCirclesOrPoints(absoluteCentroid = c, translation = pan, zoom = zoom, rotationAngle = rotationAngle)
        } else {
            val result = snapped(c)
            val absolutePoint = result.result
            val argList = partialArgList
            val currentArg = argList?.currentArg
            val currentArgType = argList?.currentArgType
            if (mode == ToolMode.ARC_PATH) {
                // TODO: if last with n>=3, snap to start
                partialArcPath = partialArcPath?.moveFocused(absolutePoint)
            } else if (
                mode is ToolMode &&
                currentArgType?.possibleTypes?.any { it is Arg.Type.Point } == true &&
                ((mode == ToolMode.CIRCLE_OR_POINT_INTERPOLATION) entails
                    (currentArg?.type is Arg.Type.Point))
            ) {
                val newArg = when (result) {
                    is PointSnapResult.Eq -> Arg.PointIndex(result.pointIndex)
                    else -> Arg.PointXY(absolutePoint)
                }
                if (argList.validateUpdatedArg(newArg)) {
                    partialArgList = argList
                        .updateCurrentArg(newArg, confirmThisArg = false)
                        .copy(lastSnap = result)
                }
            } else {
//                recordCommand(Command.CHANGE_POV)
                if (zoom != 1.0f || rotationAngle != 0.0f) {
                    val targets = objects.indices.toList()
                    val center = computeAbsoluteCenter() ?: Offset.Zero
                    recordCommand(Command.MOVE)
                    objectModel.transform(expressions, targets, focus = center, zoom = zoom, rotationAngle = rotationAngle)
                }
                translation += pan // navigate canvas
                objectModel.pathCache.invalidateAll() // sadly have to do this cuz we use visibleRect in path construction
            }
        }
    }

    fun onUp(visiblePosition: Offset?) {
        cancelSelectionAsToolArgPrompt()
        when (mode) {
            SelectionMode.Drag -> {
                // MAYBE: try to re-attach free points
            }
            ToolMode.ARC_PATH -> {
                partialArcPath = partialArcPath?.unFocus()
            }
            ViewMode.StereographicRotation -> {
                // MAYBE: normalize line-only-output expressions (e.g. polar line)
                // fixes incident points for line->circle and circle->line transitions
                expressions.adjustIncidentPointExpressions()
            }
            is ToolMode -> if (submode == null) {
                var argList = partialArgList
                // we only confirm args in onUp, they are created in onDown etc.
                val newArg = when (argList?.currentArg) {
                    is Arg.Point -> visiblePosition?.let {
                        val args = argList.args
                        val snap = snapped(absolute(visiblePosition))
                        // we cant realize it here since for fast circles the first point already has been
                        // realized in onDown and we don't know yet if we moved far enough from it to
                        // create the second point
                        if (mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS &&
                            FAST_CENTERED_CIRCLE &&
                            args.size == 2
                        ) {
                            val firstPoint: Point =
                                when (val first = args.first() as Arg.Point) {
                                    is Arg.PointIndex -> objects[first.index] as Point
                                    is Arg.FixedPoint -> first.toPoint()
                                }
                            val pointsAreTooClose = firstPoint.distanceFrom(snap.result) < 1e-3
                            if (pointsAreTooClose) { // haxxz
                                argList = argList.copy(
                                    args = args.take(1),
                                    lastArgIsConfirmed = true,
                                    lastSnap = null
                                )
                                null
                            } else {
                                realizePointCircleSnap(snap).toArgPoint()
                            }
                        } else {
                            realizePointCircleSnap(snap).toArgPoint()
                            // realized, but might be invalid (nonEqualityConditions)
                        }
                    }
                    else -> null
                }
                partialArgList = if (
                    newArg == null ||
                    argList?.validateUpdatedArg(newArg) != true
                )
                    argList?.copy(lastArgIsConfirmed = true)
                else
                    argList?.updateCurrentArg(newArg, confirmThisArg = true)
            }
            else -> {}
        }
        if (partialArgList?.isFull == true && submode == null) {
            completeToolMode()
        }
        if ((mode == SelectionMode.Drag || mode == SelectionMode.Multiselect) &&
            movementAfterDown &&
            submode == null &&
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
                    .also {
                        println("rectangle selection -> $it")
                    }
                submode = SubMode.RectangularSelect(corner1, corner2)
            }
        }
        if (mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect) { // haxx
            println("flow-select -> $selection")
            toolbarState = toolbarState.copy(activeTool = Tool.Multiselect)
        }
        when (submode) { // submode cleanup/reset
            is SubMode.Rotate,
            is SubMode.Scale,
            is SubMode.ScaleViaSlider,
            is SubMode.FlowSelect, ->
                submode = null
            is SubMode.RectangularSelect ->
                submode = SubMode.RectangularSelect()
            else -> {}
        }
    }

    fun onVerticalScroll(yDelta: Float) {
        val zoom = getPlatform().scrollToZoom(yDelta)
        scaleSelection(zoom)
    }

    // maybe enable it &
    // make long drag = pan zoom
    fun onLongPress(position: Offset) { // by itself interferes with long-drag
        // select siblings & parents for easy copy
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
                animations.emit(HighlightAnimation(allParents))
            }
        }
    }

    private fun selectCategory(category: Category, togglePanel: Boolean = false) {
        val wasSelected = toolbarState.activeCategory == category
        val panelWasShown = showPanel
        toolbarState = toolbarState.copy(activeCategory = category)
        showPanel = toolbarState.panelNeedsToBeShown
        if (togglePanel && wasSelected && toolbarState.panelNeedsToBeShown) {
            showPanel = !panelWasShown
        }
    }

    fun selectTool(tool: Tool, togglePanel: Boolean = false) {
        val category: Category
        if (tool is Tool.AppliedColor) {
            category = Category.Colors
        } else {
            category = toolbarState.getCategory(tool)
            toolbarState = toolbarState.updateDefault(category, tool)
        }
        toolbarState = toolbarState.copy(activeTool = tool)
        selectCategory(category, togglePanel = togglePanel)
        toolAction(tool)
    }

    fun switchToCategory(category: Category, togglePanel: Boolean = false) {
        val defaultTool = toolbarState.getDefaultTool(category)
        if (defaultTool == null) {
            selectCategory(category, togglePanel = togglePanel)
        } else {
            selectTool(defaultTool, togglePanel = togglePanel)
        }
    }

    fun processKeyboardAction(action: KeyboardAction) {
//        println("processing $action")
        if (openedDialog == null) {
            when (action) {
                KeyboardAction.SELECT_ALL -> {
                    if (!mode.isSelectingCircles() || !showCircles) // more intuitive behavior
                        selection = emptyList() // forces to select all instead of toggling
                    switchToCategory(Category.Multiselect)
                    toggleSelectAll()
                }
                KeyboardAction.DELETE -> deleteSelectedPointsAndCircles()
                KeyboardAction.PASTE -> duplicateSelectedCircles()
                KeyboardAction.ZOOM_IN -> scaleSelection(KEYBOARD_ZOOM_INCREMENT)
                KeyboardAction.ZOOM_OUT -> scaleSelection(1/KEYBOARD_ZOOM_INCREMENT)
                KeyboardAction.UNDO -> undo()
                KeyboardAction.REDO -> redo()
                KeyboardAction.CANCEL -> cancelOngoingActions()
                KeyboardAction.MOVE -> switchToCategory(Category.Drag)
                KeyboardAction.SELECT -> switchToCategory(Category.Multiselect)
                KeyboardAction.REGION -> switchToCategory(Category.Region)
                KeyboardAction.PALETTE -> toolAction(Tool.Palette)
                KeyboardAction.TRANSFORM -> switchToCategory(Category.Transform)
                KeyboardAction.CREATE -> switchToCategory(Category.Create)
                KeyboardAction.OPEN -> openFileRequests.tryEmit(Unit)
                KeyboardAction.SAVE -> toolAction(Tool.SaveCluster)
                KeyboardAction.CONFIRM ->
                    if (submode is SubMode.ExprAdjustment)
                        confirmAdjustedParameters()
                    else if (mode == ToolMode.ARC_PATH)
                        completeArcPath()
                KeyboardAction.NEW_DOCUMENT -> openNewBlankConstellation()
                KeyboardAction.HELP -> { // temporarily hijacked for debugging
                    showDebugInfo()
                }
            }
        }
    }

    private fun completeToolMode() {
        val toolMode = mode
        val argList = partialArgList
        require(argList != null && argList.isFull && argList.isValid && argList.lastArgIsConfirmed) { "Invalid partialArgList in completeToolMode(): $argList" }
        require(toolMode is ToolMode && toolMode.signature == argList.signature) { "Invalid signature in completeToolMode(): $toolMode's ${(toolMode as ToolMode).signature} != ${argList.signature}" }
        when (toolMode) {
            // transform
            ToolMode.CIRCLE_INVERSION -> completeCircleInversion()
            ToolMode.CIRCLE_OR_POINT_INTERPOLATION -> startCircleOrPointInterpolationParameterAdjustment()
            ToolMode.ROTATION -> startRotationParameterAdjustment()
            ToolMode.BI_INVERSION -> startBiInversionParameterAdjustment()
            ToolMode.LOXODROMIC_MOTION -> startLoxodromicMotionParameterAdjustment()
            ToolMode.CIRCLE_EXTRAPOLATION -> openedDialog = DialogType.CIRCLE_EXTRAPOLATION
            // create
            ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> completeCircleByCenterAndRadius()
            ToolMode.CIRCLE_BY_3_POINTS -> completeCircleBy3Points()
            ToolMode.LINE_BY_2_POINTS -> completeLineBy2Points()
            ToolMode.POINT -> completePoint()
            ToolMode.CIRCLE_BY_PENCIL_AND_POINT -> completeCircleByPencilAndPoint()
            ToolMode.POLARITY_BY_CIRCLE_AND_LINE_OR_POINT -> completePolarityByCircleAndLineOrPoint()
            ToolMode.ARC_PATH -> throw IllegalStateException("Use separate function to route completion")
        }
    }

    /** When in [SubMode.ExprAdjustment], changes [submode]'s [Expr]s' parameters to
     * [parameters] and updates corresponding [objects] */
    fun updateParameters(parameters: Parameters) {
        val sm = submode
        if (sm is SubMode.ExprAdjustment && parameters != sm.parameters) {
            submode = when (sm.parameters) {
                is InterpolationParameters -> { // single adjustable expr case
                    val (expr, outputIndices, reservedIndices) = sm.adjustables[0]
                    val newExpr = expr.copyWithNewParameters(parameters) as Expr.OneToMany
                    val (newIndices, newReservedIndices, newObjects) = expressions.adjustMultiExpr(
                        newExpr = newExpr,
                        targetIndices = outputIndices,
                        reservedIndices = reservedIndices,
                    )
                    for (ix in newReservedIndices) { // we have to cleanup abandoned but reserved indices
                        if (ix < objects.size) {
                            objectModel.removeObjectAt(ix)
                        } else {
                            objectModel.addObject(null)
                        }
                    }
                    for (i in newIndices.indices) {
                        val ix = newIndices[i]
                        objectModel.setDownscaledObject(ix, newObjects[i])
                    }
                    SubMode.ExprAdjustment(listOf(
                        AdjustableExpr(newExpr, newIndices, newReservedIndices)
                    ))
                }
                // multiple adjustable exprs
                is RotationParameters,
                is BiInversionParameters,
                is LoxodromicMotionParameters -> {
                    regions = regions.withoutElementsAt(sm.regions)
                    val newAdjustables = mutableListOf<AdjustableExpr>()
                    val source2trajectory = mutableListOf<Pair<Ix, List<Ix>>>()
                    for ((expr, outputIndices, reservedIndices) in sm.adjustables) {
                        val sourceIndex = (expr as Expr.TransformLike).target
                        val newExpr = expr.copyWithNewParameters(parameters) as Expr.OneToMany
                        val (newIndices, newReservedIndices, newObjects) = expressions.adjustMultiExpr(
                            newExpr = newExpr,
                            targetIndices = outputIndices,
                            reservedIndices = reservedIndices,
                        )
                        // NOTE: reserved indices will be generally non-contiguous
                        // we have to cleanup abandoned indices
                        for (ix in (outputIndices - newIndices.toSet())) {
                            objectModel.removeObjectAt(ix)
                        }
                        for (ix in (newReservedIndices - reservedIndices.toSet())) {
                            if (ix >= objects.size) {
                                objectModel.addObject(null) // pad with nulls
                            }
                        }
                        objectModel.objectColors -= outputIndices.toSet()
                        val sourceColor = objectModel.objectColors[sourceIndex]
                        for (i in newIndices.indices) {
                            val ix = newIndices[i]
                            objectModel.setDownscaledObject(ix, newObjects[i])
                            sourceColor?.also {
                                objectModel.objectColors[ix] = it
                            }
                        }
                        newAdjustables.add(
                            AdjustableExpr(newExpr, newIndices, newReservedIndices)
                        )
                        source2trajectory.add(
                            sourceIndex to newIndices
                        )
                    }
                    val regions: List<Int>
                    if (sm.parameters is LoxodromicMotionParameters &&
                        defaultLoxodromicMotionParameters.bidirectional &&
                        source2trajectory.size >= 2
                    ) {
                        // NOTE: assumption: bidirectional spiral adjustables must be laid out as {t^i}; {t^-i}
                        // s2t structure is
                        // t1^+1 .. t1^+n; t2^+1 .. t2^+n; ... tm^+1 .. tm^+n;
                        // t1^-1 .. t1^-n; t2^-1 .. t2^-n; ... tm^-1 .. tm^-n;
                        val size = source2trajectory.size.div(2)
                        // Q: is it an optimization, or a necessary step
                        val foldedSource2trajectory = source2trajectory
                            .take(size)
                            .mapIndexed { i, (sourceIndex, forwardTrajectory) ->
                                val backwardTrajectory = source2trajectory[size + i].second //.reversed()
                                sourceIndex to (backwardTrajectory + forwardTrajectory)
                            }
                        regions = copySourceRegionsOntoTrajectories(
                            foldedSource2trajectory,
                            flipRegionsInAndOut = false
                        )
                    } else {
                        regions = copySourceRegionsOntoTrajectories(
                            source2trajectory,
                            flipRegionsInAndOut = false
                        )
                    }
                    SubMode.ExprAdjustment(newAdjustables, regions)
                }
                else -> sm
            }
            when (parameters) { // upd defaults for dialog, not sure it's sensible
                is InterpolationParameters ->
                    defaultInterpolationParameters = DefaultInterpolationParameters(parameters)
                is RotationParameters ->
                    defaultRotationParameters = DefaultRotationParameters(parameters)
                is BiInversionParameters ->
                    defaultBiInversionParameters = DefaultBiInversionParameters(parameters)
                is LoxodromicMotionParameters ->
                    defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(parameters,
                        bidirectional = defaultLoxodromicMotionParameters.bidirectional
                    )
                else -> {}
            }
            objectModel.invalidate()
        }
    }

    fun confirmAdjustedParameters() {
        partialArgList = if (mode is ToolMode) {
            partialArgList?.copyEmpty()
        } else { // when adjusting in drag/multiselect
            null
        }
        when (val sm = submode) {
            is SubMode.ExprAdjustment -> {
                when (val parameters = sm.parameters) {
                    is InterpolationParameters ->
                        defaultInterpolationParameters = DefaultInterpolationParameters(parameters)
                    is RotationParameters ->
                        defaultRotationParameters = DefaultRotationParameters(parameters)
                    is BiInversionParameters ->
                        defaultBiInversionParameters = DefaultBiInversionParameters(parameters)
                    is LoxodromicMotionParameters ->
                        defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(parameters,
                            bidirectional = defaultLoxodromicMotionParameters.bidirectional
                        )
                    else -> {}
                }
            }
            else -> {}
        }
        submode = null
    }

    fun cancelOngoingActions() {
        when (mode) { // reset mode
            is ToolMode -> {
                // double escape to go to Drag
                if (submode == null && partialArgList?.args?.isNotEmpty() != true && partialArcPath == null) {
                    switchToCategory(Category.Drag)
                } else {
                    if (submode is SubMode.ExprAdjustment) {
                        cancelExprAdjustment()
                    }
                    partialArgList = partialArgList?.copyEmpty()
                    partialArcPath = null
                    submode = null
                }
            }
            ViewMode.StereographicRotation ->
                switchToCategory(Category.Drag)
            is SelectionMode -> {
                if (submode is SubMode.ExprAdjustment) {
                    undo() // contrived way to go to before-adj savepoint
                }
                submode = null
                selection = emptyList()
            }
        }
    }

    fun cancelExprAdjustment() {
        when (val sm = submode) {
            is SubMode.ExprAdjustment -> {
                val outputs = sm.adjustables.flatMap { it.outputIndices }
                deleteObjectsWithDependenciesColorsAndRegions(
                    outputs,
                    circleAnimationInit = { null }
                )
                selection = emptyList()
            }
            else -> {}
        }
        submode = null
    }

    private fun completeCircleByCenterAndRadius() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.Point }
        recordCreateCommand()
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.PointXY }) {
            val newCircle = computeCircleByCenterAndRadius(
                center = (args[0] as Arg.PointXY).toPoint().downscale(),
                radiusPoint = (args[1] as Arg.PointXY).toPoint().downscale(),
            )?.upscale()
            expressions.addFree()
            createNewGCircle(newCircle)
        } else {
            val realized = args.map { arg ->
                when (arg) {
                    is Arg.PointIndex -> arg.index
                    is Arg.FixedPoint -> createNewFreePoint(arg.toPoint(), triggerRecording = false)
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
        partialArgList = argList.copyEmpty()
    }

    private fun completeCircleBy3Points() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.CLIP }
        recordCreateCommand()
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.PointXY }) {
            val (p1, p2, p3) = args.map {
                (it as Arg.PointXY).toPoint().downscale()
            }
            val newCircle = computeCircleBy3Points(p1, p2, p3)
            expressions.addFree()
            createNewGCircle(newCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.Index -> it.index
                    is Arg.FixedPoint -> createNewFreePoint(it.toPoint(), triggerRecording = false)
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
        partialArgList = argList.copyEmpty()
    }

    private fun completeCircleByPencilAndPoint() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.CLIP }
        recordCreateCommand()
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.PointXY }) {
            val (p1, p2, p3) = args.map {
                (it as Arg.PointXY).toPoint().downscale()
            }
            val newCircle = computeCircleByPencilAndPoint(p1, p2, p3)
            expressions.addFree()
            createNewGCircle(newCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.Index -> it.index
                    is Arg.FixedPoint ->
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
        partialArgList = argList.copyEmpty()
    }

    private fun completeLineBy2Points() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.CLIP }
        recordCreateCommand()
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.PointXY }) {
            val (p1, p2) = args.map {
                (it as Arg.PointXY).toPoint().downscale()
            }
            val newGCircle = computeLineBy2Points(p1, p2)
            expressions.addFree()
            createNewGCircle(newGCircle?.upscale())
        } else {
            val realized = args.map {
                when (it) {
                    is Arg.Index -> it.index
                    is Arg.FixedPoint ->
                        createNewFreePoint(it.toPoint(), triggerRecording = false)
                }
            }
            val infinityIndex = objectModel.getInfinityIndex() ?:
                createNewFreePoint(Point.CONFORMAL_INFINITY, triggerRecording = false)
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleBy3Points(
                    object1 = realized[0],
                    object2 = realized[1],
                    object3 = infinityIndex,
                ),
            )
            createNewGCircle(newGCircle?.upscale())
        }
        partialArgList = argList.copyEmpty()
    }

    private fun completePolarityByCircleAndLineOrPoint() {
        val argList = partialArgList!!
        val circleArg = argList.args[0] as Arg.CircleIndex
        val lineOrPointArg = argList.args[1] as Arg.LP
        recordCreateCommand()
        val newExpr = when (lineOrPointArg) {
            is Arg.LineIndex -> {
                Expr.PoleByCircleAndLine(
                    circle = circleArg.index,
                    line = lineOrPointArg.index,
                )
            }
            is Arg.Point -> {
                val realizedPointIndex = when (lineOrPointArg) {
                    is Arg.PointIndex -> lineOrPointArg.index
                    is Arg.FixedPoint ->
                        createNewFreePoint(lineOrPointArg.toPoint(), triggerRecording = false)
                }
                Expr.PolarLineByCircleAndPoint(
                    circle = circleArg.index,
                    point = realizedPointIndex,
                )
            }
        }
        val newGCircle = expressions.addSoloExpr(newExpr)
        createNewGCircle(newGCircle?.upscale())
        partialArgList = argList.copyEmpty()
    }

    private fun completeCircleInversion() {
        val argList = partialArgList!!
        val targetIxs = (argList.args[0] as Arg.Indices).indices
        val invertingCircleIndex = (argList.args[1] as Arg.CLI).index
        recordCreateCommand()
        val newIndexedGCircles = targetIxs.map { ix ->
            ix to expressions.addSoloExpr(
                Expr.CircleInversion(ix, invertingCircleIndex),
            )?.upscale()
        }
        copyRegionsAndStyles(newIndexedGCircles, flipRegionsInAndOut = true) {
            CircleAnimation.Entrance(it)
        }
        partialArgList = argList.copyEmpty()
        objectModel.invalidate()
    }

    private fun startCircleOrPointInterpolationParameterAdjustment() {
        val argList = partialArgList!!
        val (startArg, endArg) = argList.args.map { it as Arg.CLIP }
        if (startArg is Arg.CLI && endArg is Arg.CLI) {
            interpolateCircles = true
            val scalarProduct =
                GeneralizedCircle.fromGCircle(objects[startArg.index]!!) scalarProduct
                GeneralizedCircle.fromGCircle(objects[endArg.index]!!)
            circlesAreCoDirected = scalarProduct >= 0.0
            val expr = Expr.CircleInterpolation(
                defaultInterpolationParameters.params.let {
                    it.copy(
                        complementary = if (circlesAreCoDirected) !it.inBetween else it.inBetween
                    )
                },
                startArg.index, endArg.index
            )
            recordCreateCommand()
            val oldSize = objects.size
            val newGCircles = expressions.addMultiExpr(expr)
            val newCircles = newGCircles.map { it?.upscale() }
            objectModel.addObjects(newCircles)
            val outputRange = (oldSize until objects.size).toList()
            submode = SubMode.ExprAdjustment(listOf(
                AdjustableExpr(expr, outputRange, outputRange)
            ))
            if (newGCircles.any { it is ImaginaryCircle }) {
                queueSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
            }
        } else if (startArg is Arg.Point && endArg is Arg.Point) {
            recordCreateCommand()
            val (startPointIx, endPointIx) = listOf(startArg, endArg).map { pointArg ->
                when (pointArg) {
                    is Arg.PointIndex -> pointArg.index
                    is Arg.FixedPoint ->
                        createNewFreePoint(pointArg.toPoint(), triggerRecording = false)
                }
            }
            val expr = Expr.PointInterpolation(defaultInterpolationParameters.params, startPointIx, endPointIx)
            interpolateCircles = false
            val oldSize = objects.size
            val newGCircles = expressions.addMultiExpr(expr)
            val newPoints = newGCircles.map { it?.upscale() as? Point }
            objectModel.addObjects(newPoints)
            val outputRange = (oldSize until objects.size).toList()
            submode = SubMode.ExprAdjustment(listOf(
                AdjustableExpr(expr, outputRange, outputRange)
            ))
        }
        objectModel.invalidate()
    }

    fun completeCircleExtrapolation(
        params: ExtrapolationParameters,
    ) {
        openedDialog = null
        val argList = partialArgList!!
        val startCircleIx = (argList.args[0] as Arg.CLI).index
        val endCircleIx = (argList.args[1] as Arg.CLI).index
        recordCreateCommand()
        val newGCircles = expressions.addMultiExpr(
            Expr.CircleExtrapolation(params, startCircleIx, endCircleIx),
        ).map { it?.upscale() }
        createNewGCircles(newGCircles)
        partialArgList = argList.copyEmpty()
        defaultExtrapolationParameters = DefaultExtrapolationParameters(params)
    }

    fun resetCircleExtrapolation() {
        openedDialog = null
        partialArgList = PartialArgList(
            Tool.CircleExtrapolation.signature,
            Tool.CircleExtrapolation.nonEqualityConditions
        )
    }

    // Q: witnessed abnormal index skipping whe rotating lines, observing closely...
    fun startRotationParameterAdjustment() {
        val argList = partialArgList!!
        val objArg = argList.args[0] as Arg.Indices
        val pointArg = argList.args[1] as Arg.Point
        recordCreateCommand()
        val pivotPointIndex = when (pointArg) {
            is Arg.PointIndex -> pointArg.index
            is Arg.FixedPoint -> createNewFreePoint(pointArg.toPoint(), triggerRecording = false)
        }
        val targetIndices = objArg.indices
        val adjustables = mutableListOf<AdjustableExpr>()
        val params0 = defaultRotationParameters.params
        val oldSize = objects.size
        var outputIndex = oldSize
        val source2trajectory = targetIndices.map { targetIndex ->
            val expr = Expr.Rotation(params0, pivotPointIndex, targetIndex)
            val result = expressions
                .addMultiExpr(expr)
                .map { it?.upscale() } // multi expression creates a whole trajectory at a time
            val outputRange = (outputIndex until (outputIndex + result.size)).toList()
            adjustables.add(
                AdjustableExpr(expr, outputRange, outputRange)
            )
            outputIndex += result.size
            return@map targetIndex to result
        }
        val newObjects = source2trajectory.flatMap { it.second }
        objectModel.addObjects(newObjects) // row-column order
        objectModel.copySourceColorsOntoTrajectories(source2trajectory, oldSize)
        val regions = copySourceRegionsOntoTrajectories(
            source2trajectory, oldSize,
            flipRegionsInAndOut = false
        )
        submode = SubMode.ExprAdjustment(adjustables, regions)
        objectModel.invalidate()
    }

    fun startBiInversionParameterAdjustment() {
        val argList = partialArgList!!
        val args = argList.args
        val objArg = args[0] as Arg.Indices
        val engine1 = (args[1] as Arg.CLI).index
        val engine2 = (args[2] as Arg.CLI).index
        val engine1GC = GeneralizedCircle.fromGCircle(objects[engine1]!!)
        val engine2GC0 = GeneralizedCircle.fromGCircle(objects[engine2]!!)
        val reverseSecondEngine = engine1GC scalarProduct engine2GC0 < 0 // anti-parallel
        defaultBiInversionParameters = defaultBiInversionParameters.copy(
            reverseSecondEngine = reverseSecondEngine
        )
        val targetIndices = objArg.indices
        recordCreateCommand()
        val adjustables = mutableListOf<AdjustableExpr>()
        val params0 = defaultBiInversionParameters.params
        val oldSize = objects.size
        var outputIndex = oldSize
        val source2trajectory = targetIndices.map { targetIndex ->
            val expr = Expr.BiInversion(params0, engine1, engine2, targetIndex)
            val result = expressions
                .addMultiExpr(expr)
                .map { it?.upscale() } // multi expression creates a whole trajectory at a time
            val outputRange = (outputIndex until (outputIndex + result.size)).toList()
            adjustables.add(
                AdjustableExpr(expr, outputRange, outputRange)
            )
            outputIndex += result.size
            return@map targetIndex to result
        }
        val newObjects = source2trajectory.flatMap { it.second }
        objectModel.addObjects(newObjects) // row-column order
        objectModel.copySourceColorsOntoTrajectories(source2trajectory, oldSize)
        val regions = copySourceRegionsOntoTrajectories(
            source2trajectory, oldSize,
            flipRegionsInAndOut = false
        )
        submode = SubMode.ExprAdjustment(adjustables, regions)
        objectModel.invalidate()
    }

    fun startLoxodromicMotionParameterAdjustment() {
        recordCreateCommand()
        setupLoxodromicSpiral(defaultLoxodromicMotionParameters.bidirectional)
    }

    private fun setupLoxodromicSpiral(bidirectional: Boolean) {
        val argList = partialArgList!!
        val args = argList.args
        val objArg = args[0] as Arg.Indices
        val divergenceArg = args[1] as Arg.Point
        val convergenceArg = args[2] as Arg.Point
        val (divergencePointIndex, convergencePointIndex) = listOf(divergenceArg, convergenceArg)
            .map { when (val arg = it) {
                is Arg.PointIndex -> arg.index
                is Arg.FixedPoint -> createNewFreePoint(arg.toPoint(), triggerRecording = false)
            } }
        partialArgList = argList.copy(
            // we dont want to spam create the same Point.XY each time
            args = listOf(
                objArg, Arg.PointIndex(divergencePointIndex), Arg.PointIndex(convergencePointIndex)
            ),
        )
        val targetIndices = objArg.indices
        val adjustables = mutableListOf<AdjustableExpr>()
        val params0 = defaultLoxodromicMotionParameters.params
        val oldSize = objects.size
        var outputIndex = oldSize
        if (bidirectional) {
            val spiralSize = params0.nTotalSteps
            val firstHalfStart = outputIndex
            val secondHalfStart = firstHalfStart + targetIndices.size * spiralSize
            val source2trajectory1 = targetIndices.mapIndexed { i, targetIndex ->
                val expr = Expr.LoxodromicMotion(
                    parameters = params0,
                    divergencePoint = divergencePointIndex,
                    convergencePoint = convergencePointIndex,
                    target = targetIndex,
                    // NOTE: complementary half indices rely on contiguous layout of trajectories
                    otherHalfStart = secondHalfStart + i * spiralSize,
                )
                val result = expressions
                    .addMultiExpr(expr)
                    .map { it?.upscale() } // multi expression creates a whole trajectory at a time
                // result.size == spiralSize
                val outputRange = (outputIndex until (outputIndex + result.size)).toList()
                adjustables.add(
                    AdjustableExpr(expr, outputRange, outputRange)
                )
                outputIndex += result.size
                return@mapIndexed targetIndex to result
            }
            val interimSize = outputIndex
            // reversing convergence-divergence for 2nd trajectory
            val source2trajectory2 = targetIndices.mapIndexed { i, targetIndex ->
                val expr = Expr.LoxodromicMotion(
                    parameters = params0,
                    divergencePoint = convergencePointIndex,
                    convergencePoint = divergencePointIndex,
                    target = targetIndex,
                    otherHalfStart = firstHalfStart + i * spiralSize,
                )
                val result = expressions
                    .addMultiExpr(expr)
                    .map { it?.upscale() } // multi expression creates a whole trajectory at a time
                val outputRange = (outputIndex until (outputIndex + result.size)).toList()
                adjustables.add(
                    AdjustableExpr(expr, outputRange, outputRange)
                )
                outputIndex += result.size
                return@mapIndexed targetIndex to result
            }
            val source2trajectory = source2trajectory1 + source2trajectory2
            val newObjects = source2trajectory.flatMap { it.second }
            objectModel.addObjects(newObjects) // row-column order
            objectModel.copySourceColorsOntoTrajectories(source2trajectory1, startIndex = oldSize)
            objectModel.copySourceColorsOntoTrajectories(source2trajectory2, startIndex = interimSize)
            val regions = copySourceRegionsOntoTrajectories(
                source2trajectory1,
                startIndex = oldSize,
                flipRegionsInAndOut = false
            ) + copySourceRegionsOntoTrajectories(
                source2trajectory2,
                startIndex = interimSize,
                flipRegionsInAndOut = false
            )
            // 2 * nTargets adjustables
            submode = SubMode.ExprAdjustment(adjustables, regions)
        } else {
            val source2trajectory = targetIndices.map { targetIndex ->
                val expr = Expr.LoxodromicMotion(params0, divergencePointIndex, convergencePointIndex, targetIndex)
                val result = expressions
                    .addMultiExpr(expr)
                    .map { it?.upscale() } // multi expression creates a whole trajectory at a time
                val outputRange = (outputIndex until (outputIndex + result.size)).toList()
                adjustables.add(
                    AdjustableExpr(expr, outputRange, outputRange)
                )
                outputIndex += result.size
                return@map targetIndex to result
            }
            val newObjects = source2trajectory.flatMap { it.second }
            objectModel.addObjects(newObjects) // row-column order
            objectModel.copySourceColorsOntoTrajectories(source2trajectory, startIndex = oldSize)
            val regions = copySourceRegionsOntoTrajectories(
                source2trajectory,
                startIndex = oldSize,
                flipRegionsInAndOut = false
            )
            submode = SubMode.ExprAdjustment(adjustables, regions)
        }
        objectModel.invalidate()
    }

    fun updateLoxodromicBidirectionality(bidirectional: Boolean) {
        val sm = submode
        if (sm is SubMode.ExprAdjustment) {
            when (sm.parameters) {
                is LoxodromicMotionParameters -> {
                    defaultLoxodromicMotionParameters = defaultLoxodromicMotionParameters.copy(
                        bidirectional = bidirectional,
                    )
                    regions = regions.withoutElementsAt(sm.regions)
                    deleteObjectsWithDependenciesColorsAndRegions(
                        indicesToDelete = sm.adjustables.flatMap { it.outputIndices },
                        triggerRecording = false,
                        circleAnimationInit = { null },
                    )
                    setupLoxodromicSpiral(bidirectional)
                }
                else -> {}
            }
        }
    }

    fun completeArcPath() {
        partialArcPath?.let { pArcPath ->
            recordCreateCommand()
            val vertices = listOf(pArcPath.startVertex) + pArcPath.vertices
            val vertexIndices: List<Ix> = vertices.map { vertex ->
                when (vertex) {
                    is ArcPathPoint.Eq -> vertex.index
                    is ArcPathPoint.Free -> {
                        createNewFreePoint(vertex.point, triggerRecording = false)
                    }
                    is ArcPathPoint.Incident -> {
                        val carrier = objects[vertex.carrierIndex] as CircleOrLine
                        val order = carrier.downscale().point2order(vertex.point.downscale())
                        val point = expressions.addSoloExpr(
                            Expr.Incidence(IncidenceParameters(order), vertex.carrierIndex)
                        ) as Point
                        objectModel.addDownscaledObject(point)
                    }
                    is ArcPathPoint.Intersection -> {
                        val expression = Expression.OneOf(
                            Expr.Intersection(vertex.carrier1Index, vertex.carrier2Index),
                            outputIndex = 0
                        )
                        val point = expressions.addMultiExpression(expression) as Point
                        objectModel.addDownscaledObject(point)
                    }
                }
            }
            val auxillaryIndices = mutableListOf<Ix>()
            val signedDirectedArcIndices: List<SignedDirectedArcIndex> =
                pArcPath.arcs.mapIndexed { arcIndex, arc ->
                    when (val arc = pArcPath.arcs[arcIndex]) {
                        is ArcPathArc.Eq -> {
                            if (arc.sameDirection) {
                                1 + arc.index
                            } else {
                                -1 - arc.index
                            }
                        }
                        is ArcPathArc.Free -> {
                            // vertexIndices: vertexNumber -> index within VM.objects
                            val previousVertexIndex: Ix = vertexIndices[arcIndex]
                            val nextVertexIndex: Ix = vertexIndices[(arcIndex + 1) % vertexIndices.size]
                            val circle = arc.circle
                            if (circle == null) {
                                val expr = Expr.LineBy2Points(previousVertexIndex, nextVertexIndex)
                                val line = expressions.addSoloExpr(expr) as Line
                                val ix = objectModel.addDownscaledObject(line)
                                auxillaryIndices.add(ix)
                                1 + ix
                            } else {
                                val sagittaRatio = computeSagittaRatio(
                                    circle,
                                    objects[previousVertexIndex] as Point,
                                    objects[nextVertexIndex] as Point,
                                )
                                val expr = Expr.CircleBy2PointsAndSagittaRatio(
                                    SagittaRatioParameters(sagittaRatio),
                                    previousVertexIndex, nextVertexIndex
                                )
                                val circle1 = expressions.addSoloExpr(expr) as CircleOrLine
                                val ix = objectModel.addDownscaledObject(circle1)
                                auxillaryIndices.add(ix)
                                1 + ix
                            }
                        }
                    }
                }
//            val newCircles: List<CircleOrLine> = pArcPath.arcs
//                .mapIndexed { arcIndex, circle ->
//                    when (circle) {
//                        is ArcPathArc.Eq -> null
//                        is ArcPathArc.Free -> pArcPath.arcIndex2CircleOrLine(arcIndex)
////                        else -> never()
//                    }
//                }.filterNotNull()
//            createNewGCircles(newCircles)
            objectModel.phantomObjectIndices += auxillaryIndices
            val newArcPath = if (pArcPath.isClosed) {
                ClosedArcPath(
                    vertices = vertexIndices,
                    arcs = signedDirectedArcIndices,
                    fillColor = regionColor,
                    borderColor = regionColor,
                )
            } else {
                OpenArcPath(
                    vertices = vertexIndices,
                    arcs = signedDirectedArcIndices,
                    borderColor = regionColor,
                )
            }
            objectModel.invalidate()
        }
        partialArcPath = null
    }

    private fun completePoint() {
        val argList = partialArgList!!
        val args = argList.args.map { it as Arg.Point }
        val arg0 = args[0]
        if (arg0 is Arg.PointXY) {
            val newPoint = arg0.toPoint()
            createNewFreePoint(newPoint)
        } // it could have already done it with realized PSR.Eq, which results in Arg.Point.Index
        partialArgList = argList.copyEmpty()
    }

    fun confirmDialogSelectedParameters(
        parameters: Parameters
    ) {
        openedDialog = null
        updateParameters(parameters)
        confirmAdjustedParameters()
    }

    fun closeDialog() {
        openedDialog = null
    }

    fun setBlendSettings(newRegionsOpacity: Float, newRegionsBlendModeType: BlendModeType) {
        regionsOpacity = newRegionsOpacity
        regionsBlendModeType = newRegionsBlendModeType
        openedDialog = null
    }

    // context: pArgList is full and we are in submode
    fun openDetailsDialog() {
        openedDialog = when (mode) {
            ToolMode.CIRCLE_OR_POINT_INTERPOLATION -> DialogType.CIRCLE_OR_POINT_INTERPOLATION
            ToolMode.CIRCLE_EXTRAPOLATION -> DialogType.CIRCLE_EXTRAPOLATION
            ToolMode.BI_INVERSION -> DialogType.BI_INVERSION
            ToolMode.LOXODROMIC_MOTION -> DialogType.LOXODROMIC_MOTION
            else -> null
        } ?: submode.let { sm ->
            if (sm is SubMode.ExprAdjustment) {
                when (sm.parameters) {
                    is InterpolationParameters -> DialogType.CIRCLE_OR_POINT_INTERPOLATION
                    is RotationParameters -> DialogType.ROTATION
                    is BiInversionParameters -> DialogType.BI_INVERSION
                    is LoxodromicMotionParameters -> DialogType.LOXODROMIC_MOTION
                    else -> null
                }
            } else {
                null
            }
        }
    }

    fun toolAction(tool: Tool) {
//        println("toolAction($tool)")
        when (tool) {
            Tool.Undo -> undo()
            Tool.Redo -> redo()
            Tool.SaveCluster -> openedDialog = DialogType.SAVE_OPTIONS
            Tool.Drag -> switchToMode(SelectionMode.Drag)
            Tool.Multiselect -> switchToMode(SelectionMode.Multiselect)
            Tool.RectangularSelect -> activateRectangularSelect()
            Tool.FlowSelect -> activateFlowSelect()
            Tool.ToggleSelectAll -> toggleSelectAll()
            Tool.Region -> switchToMode(SelectionMode.Region)
            Tool.FlowFill -> activateFlowFill()
            Tool.FillChessboardPattern -> toggleChessboardPattern()
            Tool.RestrictRegionToSelection -> toggleRestrictRegionsToSelection()
            Tool.DeleteAllParts -> deleteAllRegions()
            Tool.BlendSettings -> openedDialog = DialogType.BLEND_SETTINGS
            Tool.ToggleObjects -> toggleShowCircles()
            Tool.TogglePhantoms -> togglePhantomObjects()
            Tool.HideUI -> hideUIFor30s()
            Tool.ToggleDirectionArrows -> showDirectionArrows = !showDirectionArrows
            // TODO: 2 options: solid color or external image
            Tool.AddBackgroundImage -> openedDialog = DialogType.BACKGROUND_COLOR_PICKER
            Tool.StereographicRotation -> toggleStereographicRotationMode()
            Tool.InsertCenteredCross -> insertCenteredCross()
            Tool.CompleteArcPath -> completeArcPath()
            Tool.Palette -> openedDialog = DialogType.REGION_COLOR_PICKER
            Tool.Expand -> scaleSelection(HUD_ZOOM_INCREMENT)
            Tool.Shrink -> scaleSelection(1/HUD_ZOOM_INCREMENT)
            Tool.Detach -> detachEverySelectedObject()
            Tool.SwapDirection -> swapDirectionsOfSelectedCircles()
            Tool.MarkAsPhantoms ->
                if (toolPredicate(tool))
                    markSelectedObjectsAsPhantoms()
                else unmarkSelectedObjectsAsPhantoms()
            Tool.Duplicate -> duplicateSelectedCircles()
            Tool.PickCircleColor -> openedDialog = DialogType.CIRCLE_COLOR_PICKER
            Tool.SetLabel -> openedDialog = DialogType.LABEL_INPUT
            Tool.Delete -> deleteSelectedPointsAndCircles()
            is Tool.AppliedColor -> setNewRegionColorToSelectedColorSplash(tool.color)
            is Tool.MultiArg -> switchToMode(ToolMode.correspondingTo(tool))
            is Tool.CustomAction -> {} // custom, platform-dependent handlers for open/save
            Tool.DetailedAdjustment -> openDetailsDialog()
            Tool.AdjustExpr -> adjustExpr()
            Tool.InBetween -> {} // unused, potentially updateParams(...)
            Tool.ReverseDirection -> {}
            Tool.BidirectionalSpiral -> {}
            Tool.ToggleFilledOrOutline -> TODO("presently unused")
            Tool.InfinitePoint -> addInfinitePointArg()
            Tool.MovePointToInfinity -> movePointToInfinity()
        }
    }

    /** Is [tool] enabled? */
    fun toolPredicate(tool: Tool): Boolean =
        when (tool) { // NOTE: i think this has to return State<Boolean> to work properly
            Tool.Drag ->
                mode == SelectionMode.Drag
            Tool.Multiselect ->
                mode == SelectionMode.Multiselect &&
                submode !is SubMode.FlowSelect && submode !is SubMode.RectangularSelect
            Tool.RectangularSelect ->
                mode == SelectionMode.Multiselect && submode is SubMode.RectangularSelect
            Tool.FlowSelect ->
                mode == SelectionMode.Multiselect && submode is SubMode.FlowSelect
            Tool.ToggleSelectAll -> {
                hug(objectModel.invalidations)
                selection.containsAll(objects.filterIndices { it is CircleOrLineOrPoint })
            }
            Tool.Region ->
                mode == SelectionMode.Region && submode !is SubMode.FlowFill
            Tool.FlowFill ->
                mode == SelectionMode.Region && submode is SubMode.FlowFill
            Tool.FillChessboardPattern ->
                chessboardPattern != ChessboardPattern.NONE
            Tool.RestrictRegionToSelection ->
                restrictRegionsToSelection
            Tool.StereographicRotation ->
                mode == ViewMode.StereographicRotation
            Tool.ToggleObjects ->
                showCircles
            Tool.TogglePhantoms ->
                showPhantomObjects
            Tool.ToggleDirectionArrows ->
                showDirectionArrows
            Tool.MarkAsPhantoms -> {
                hug(objectModel.invalidations)
                selection.none { it in phantoms }
            }
            Tool.InfinitePoint -> {
                partialArgList?.let { argList ->
                    argList.nextArgType?.let { nextArgType ->
                        val acceptsInfinitePoint = Arg.InfinitePoint in nextArgType.possibleTypes
                        val acceptsIndices = Arg.Indices in nextArgType.possibleTypes
                        (acceptsInfinitePoint || acceptsIndices) &&
                                objectModel.getInfinityIndex()?.let { ix ->
                                    val potentialNewArg = if (acceptsIndices)
                                        Arg.Indices(listOf(ix))
                                    else
                                        Arg.PointIndex(ix)
                                    argList.validateNewArg(potentialNewArg)
                                } != false
                    } == true
                } == true
            }
            Tool.MovePointToInfinity -> {
                hug(objectModel.invalidations)
                selection.singleOrNull()?.let { ix ->
                    val o = objects[ix]
                    val expr = exprOf(ix)
                    o is Point && o != Point.CONFORMAL_INFINITY &&
                        (expr == null || expr is Expr.Incidence && objects[expr.carrier] is Line)
                } == true
            }
            is Tool.MultiArg ->
                mode == ToolMode.correspondingTo(tool)
            else -> true
        }

    /** alternative enabled, mainly for 3-state buttons */
    fun toolAlternativePredicate(tool: Tool): Boolean =
        when (tool) {
            Tool.FillChessboardPattern ->
                chessboardPattern == ChessboardPattern.STARTS_TRANSPARENT
            else -> false
        }

    // NOTE: downscaling each arg for eval is an extreme performance bottleneck (4 - 15 times)
    private fun GCircle.downscale(): GCircle = scaled00(DOWNSCALING_FACTOR)
    private fun GCircle.upscale(): GCircle = scaled00(UPSCALING_FACTOR)
    private fun CircleOrLine.downscale(): CircleOrLine = scaled00(DOWNSCALING_FACTOR)
    private fun CircleOrLine.upscale(): CircleOrLine = scaled00(UPSCALING_FACTOR)
    private fun Point.downscale(): Point = scaled00(DOWNSCALING_FACTOR)
    private fun Point.upscale(): Point = scaled00(UPSCALING_FACTOR)

    fun saveState(): State {
        val center = computeAbsoluteCenter() ?: Offset.Zero
        val deleted = objects.indices.filter { ix ->
            objects[ix] == null && isFree(ix)
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
                restoreFromState(State.SAMPLE)
//                loadNewConstellation(Constellation.SAMPLE)
//                centerizeTo(0f, 0f)
            } else {
                val result = runCatching { // NOTE: can fail crash when underlying VM.State format changes
                    platform.lastStateStore.get()
                }
                val state = result.getOrNull()
                if (state == null) {
                    restoreFromState(State.SAMPLE)
//                    loadNewConstellation(Constellation.SAMPLE)
//                    centerizeTo(0f, 0f)
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
        colorPickerParameters = colorPickerParameters.copy(savedColors = settings.savedColors)
        defaultInterpolationParameters = settings.defaultInterpolationParameters
        defaultRotationParameters = settings.defaultRotationParameters
        defaultBiInversionParameters = settings.defaultBiInversionParameters
        defaultLoxodromicMotionParameters = settings.defaultLoxodromicMotionParameters
        toolbarState = toolbarState.copy(categoryDefaultIndices = settings.categoryDefaultIndices)
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
            regionsBlendModeType = regionsBlendModeType,
            savedColors = colorPickerParameters.savedColors,
            defaultInterpolationParameters = defaultInterpolationParameters,
            defaultRotationParameters = defaultRotationParameters,
            defaultBiInversionParameters = defaultBiInversionParameters,
            defaultLoxodromicMotionParameters = defaultLoxodromicMotionParameters,
            categoryDefaultIndices = toolbarState.categoryDefaultIndices,
        )

    // NOTE: i never seen this proc on Android or Wasm tbh
    //  so i had to create Flow<LifecycleEvent> to manually trigger caching
    override fun onCleared() {
        cacheState()
        super.onCleared()
    }

    /**
     * Save-able state of [EditorViewModel], used for [history].
     * Be careful to pass _only_ strictly immutable args by __copying__
     */
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
    ) {
        companion object {
            val JSON_FORMAT = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                // did you know that JSON doesn't support +Infinity, -Infinity or NaN?
                // yep, gotta thank brilliant JS devs for their gift to humanity
                allowSpecialFloatingPointValues = true
            }

            // nice spiral
            val SAMPLE = State(
                constellation = Constellation.SAMPLE,
                selection = listOf(),
                centerX = 0f, centerY = 0f,
                regionColor = null,
                chessboardPattern = ChessboardPattern.STARTS_TRANSPARENT,
                chessboardColor = Color(56, 136, 116),
            )
        }
    }

    companion object {
        // reference: https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            addInitializer(EditorViewModel::class) {
                EditorViewModel()
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
        const val TAP_RADIUS_TO_TANGENTIAL_SNAP_DISTANCE_FACTOR = 7.0
        const val FAST_CENTERED_CIRCLE = true
        const val ENABLE_ANGLE_SNAPPING = true
        const val ENABLE_TANGENT_SNAPPING = false
        const val RESTORE_LAST_SAVE_ON_LOAD = true
        const val TWO_FINGER_TAP_FOR_UNDO = true // Android-only
        const val DEFAULT_SHOW_DIRECTION_ARROWS_ON_SELECTED_CIRCLES = false
        const val SHOW_IMAGINARY_CIRCLES = true
        /** Allow moving non-free object IF all of it's lvl 1 parents=dependencies are free by
         * moving all of its parent with it */ // ggbra-like
        val INVERSION_OF_CONTROL = InversionOfControl.LEVEL_1
        /** When constructing an object depending on not-yet-existing points,
         * always create them. In contrast to replacing its expression with a static, free circle */
        const val ALWAYS_CREATE_ADDITIONAL_POINTS = false
        // NOTE: changing it presently breaks all line-incident points
        /** [Double] arithmetic is best in range that is closer to 0 */
        const val UPSCALING_FACTOR = ObjectModel.UPSCALING_FACTOR
        const val DOWNSCALING_FACTOR = ObjectModel.DOWNSCALING_FACTOR

        fun sliderPercentageDeltaToZoom(percentageDelta: Float): Float =
            MAX_SLIDER_ZOOM.pow(2*percentageDelta)
    }
}
