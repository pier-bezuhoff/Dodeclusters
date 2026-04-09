package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
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
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.CircleOrLineOrImaginaryCircle
import core.geometry.CircleOrLineOrPoint
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteAcPath
import core.geometry.ImaginaryCircle
import core.geometry.Line
import core.geometry.Point
import core.geometry.RectangleCollider
import core.geometry.conformal.GeneralizedCircle
import core.geometry.conformal.Rotor
import core.geometry.conformal.calculateStereographicRotationBiEngine
import core.geometry.conformal.generateSphereGrid
import core.geometry.fromCorners
import core.geometry.scaled00
import core.geometry.translationDelta
import domain.ColorAsCss
import domain.Ix
import domain.PointSnapResult
import domain.ProgressState
import domain.Snapping
import domain.angleDeg
import domain.cluster.Constellation
import domain.compressConstraints
import domain.entails
import domain.expressions.ArcPath
import domain.expressions.ArcPathArcMidpointParameters
import domain.expressions.ArcPathIncidenceParameters
import domain.expressions.BiInversionParameters
import domain.expressions.ConformalExpressions
import domain.expressions.Expr
import domain.expressions.ExprOutput
import domain.expressions.ExtrapolationParameters
import domain.expressions.IncidenceParameters
import domain.expressions.InterpolationParameters
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.Parameters
import domain.expressions.RotationParameters
import domain.expressions.computeArcPathIncidenceOrder
import domain.expressions.computeCircleBy3Points
import domain.expressions.computeCircleByCenterAndRadius
import domain.expressions.computeCircleByPencilAndPoint
import domain.expressions.computeIntersection
import domain.expressions.computeLineBy2Points
import domain.expressions.computeSagittaRatio
import domain.expressions.copy
import domain.expressions.copyWithNewParameters
import domain.expressions.moveArcMidpoint
import domain.expressions.reIndex
import domain.filterIndices
import domain.hug
import domain.indicesSortedBy
import domain.io.DdcV1
import domain.io.DdcV2
import domain.io.DdcV5
import domain.io.SaveRequest
import domain.io.SaveResult
import domain.io.saveStateAsSvg
import domain.io.tryParseDdc
import domain.model.Arg
import domain.model.ArgType
import domain.model.ChangeHistory
import domain.model.ChessboardPattern
import domain.model.ConformalObjectModel
import domain.model.ContinuousChange
import domain.model.LogicalRegion
import domain.model.PartialArcPath
import domain.model.PartialArgList
import domain.model.SaveState
import domain.model.Selection
import domain.mostCommonOf
import domain.never
import domain.settings.BlendModeType
import domain.settings.InversionOfControl
import domain.settings.Settings
import domain.sortedByFrequency
import domain.transpose
import domain.withoutElementsAt
import domain.xor
import getPlatform
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ui.editor.EditorViewModel.Companion.INVERSION_OF_CONTROL
import ui.editor.dialogs.ColorPickerParameters
import ui.editor.dialogs.DefaultBiInversionParameters
import ui.editor.dialogs.DefaultExtrapolationParameters
import ui.editor.dialogs.DefaultInterpolationParameters
import ui.editor.dialogs.DefaultLoxodromicMotionParameters
import ui.editor.dialogs.DefaultRotationParameters
import ui.editor.dialogs.DialogType
import ui.theme.DodeclustersColors
import ui.theme.ExtendedColorScheme
import ui.tools.Category
import ui.tools.Tool
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// this class is obviously too big
// MAYBE: timed autosave (cron-like), e.g. every 10min
class EditorViewModel : ViewModel() {
    val objectModel: ConformalObjectModel = ConformalObjectModel()
    val objects: List<GCircleOrConcreteAcPath?> = objectModel.displayObjects
    inline val expressions: ConformalExpressions get() =
        objectModel.expressions
    val borderColors: Map<Ix, Color> = objectModel.borderColors
    val fillColors: Map<Ix, Color> = objectModel.fillColors
    // not in objectModel cuz i want it to be state-backed for nicer caching
    var labels: Map<Ix, String> by mutableStateOf(mapOf())
    // alt name: ghost[ed] objects
    val phantoms: Set<Ix> = objectModel.phantomObjectIndices
    // MAYBE: encapsulate regions into ObjectModel
    /** Filled regions delimited by some objects from [objects] */
    var regions: List<LogicalRegion> by mutableStateOf(listOf())

    var backgroundColor: Color? by mutableStateOf(null)
    var chessboardColor: Color by mutableStateOf(DodeclustersColors.deepAmethyst)
        private set
    var chessboardPattern: ChessboardPattern by mutableStateOf(ChessboardPattern.NONE)
        private set
//    var _debugObjects: List<GCircle> by mutableStateOf(emptyList())

    // MAYBE: when circles are hidden select regions instead
    /** indices of selected circles/lines/points & arc-paths */
    var selection: Selection by mutableStateOf(Selection())
        private set
    /** Distinct selected [GCircle]? indices +
     * indices of all vertices/midpoints of selected arc-paths */
    inline val selectedIndices: List<Ix> get() =
        selection.gCircles.plus(
            selection.arcPaths.flatMap {
                objectModel.getArcPath(it)?.dependencies ?: emptySet()
            }
        ).distinct()
    inline val objectSelection: List<Ix> get() =
        selection.gCircles

    private val modeState: MutableState<Mode> = mutableStateOf(SelectionMode.Drag)
    /** Major editing mode */
    var mode: Mode by modeState
        private set
    private val submodeState: MutableState<SubMode?> = mutableStateOf(null)
    /** Minor editing mode, bound to [mode]; can hold transient data */
    var submode: SubMode? by submodeState
        private set
    // NOTE: Arg.XYPoint & co use absolute positioning
    /** Partly filled [Tool] arg-list during [ToolMode] */
    var partialArgList: PartialArgList? by mutableStateOf(null)
        private set
    /** Under-construction arc-path during [ToolMode.ARC_PATH] */
    var partialArcPath: PartialArcPath? by mutableStateOf(null)
        private set

    // ahh.. to be set during startCircleOrPointInterpolationParameterAdjustment()
    var interpolateCircles: Boolean by mutableStateOf(true)
        private set
    var circlesAreCoDirected: Boolean by mutableStateOf(true)
        private set

    var translation: Offset by mutableStateOf(Offset.Zero)
        private set
    var canvasSize: IntSize by mutableStateOf(IntSize.Zero) // used when saving best-center
        private set

    /** currently selected color */
    var regionColor: Color by mutableStateOf(DodeclustersColors.deepAmethyst)
        private set
    /** `[0; 1]` transparency of non-chessboard [regions] */
    var regionsOpacity: Float by mutableStateOf(1.0f)
        private set
    var regionsBlendModeType: BlendModeType by mutableStateOf(BlendModeType.SRC_OVER)
        private set
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
     * only use circles present in the [objectSelection] to determine which regions to fill */
    var restrictRegionsToSelection: Boolean by mutableStateOf(false)
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

    var openedDialog: DialogType? by mutableStateOf(null)
        private set
    private var queuedAction: Action? by mutableStateOf(null)

    // these are NG
    inline val showGenericSelectionContextActions: Boolean get() =
        mode.isSelectingCircles() && showCircles &&
            (selection.gCircles.any { objects[it] is CircleOrLineOrImaginaryCircle } ||
                selection.arcPaths.isNotEmpty() &&
                selection.gCircles.any { objects[it] is Point }
            )
    inline val showPointContextActions: Boolean get() =
        showCircles && mode.isSelectingCircles() && selection.gCircles.any { objects[it] is Point }
    inline val showArcPathContextActions: Boolean get() =
        mode.isSelectingCircles() && selection.arcPaths.isNotEmpty()

    val handleConfig: HandleConfig? get() =
        if (mode.isSelectingCircles())
            when {
                selection.gCircles.size == 1 && selection.arcPaths.isEmpty() ->
                    HandleConfig.SINGLE_CIRCLE
                selectedIndices.size > 1 ->
                    HandleConfig.SEVERAL_OBJECTS
                else -> null
            }
        else null
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
    // MAYBE: show quick prompt/popup instead of a button
    val selectionIsLocked: Boolean get() {
        hug(objectModel.propertyInvalidations)
        // NOTE: isFree depends on expressions, so propertyInvalidations is not enough
        return selection.gCircles.all { objects[it] == null || !isFree(it) }
    }

    val undoIsEnabled: MutableState<Boolean> = mutableStateOf(false)
    val redoIsEnabled: MutableState<Boolean> = mutableStateOf(false)
    private var history: ChangeHistory =
        ChangeHistory( // stub
            initialState = SaveState.SAMPLE,
            undoIsEnabled = undoIsEnabled,
            redoIsEnabled = redoIsEnabled,
        )

    var colorPickerParameters by mutableStateOf(
        ColorPickerParameters(Color.Unspecified, emptyList())
    )
        private set
    var defaultInterpolationParameters by mutableStateOf(DefaultInterpolationParameters())
        private set
    var defaultExtrapolationParameters by mutableStateOf(DefaultExtrapolationParameters())
        private set
    var defaultRotationParameters by mutableStateOf(DefaultRotationParameters())
        private set
    var defaultBiInversionParameters by mutableStateOf(DefaultBiInversionParameters())
        private set
    var defaultLoxodromicMotionParameters by mutableStateOf(DefaultLoxodromicMotionParameters())
        private set

    /** Open file requests that generally originate from the keyboard events and are used in
     * platform-dependent buttons */
    val openFileRequests: MutableSharedFlow<Unit> =
        MutableSharedFlow(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    /** Save file requests (quick save/overwrite or save as) that generally originate from
     * the keyboard events and are used in platform-dependent buttons */
    val saveFileRequests: MutableSharedFlow<SaveRequest> =
        MutableSharedFlow(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    // TODO: persist this for UX
    var lastSaveMetadata: SaveResult? by mutableStateOf(null)
        private set

    val animations: MutableSharedFlow<ObjectAnimation> = MutableSharedFlow()

    val snackbarMessages: MutableSharedFlow<Pair<SnackbarMessage, Array<out Any>>> =
        MutableSharedFlow(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    val restoration: MutableStateFlow<ProgressState> =
        MutableStateFlow(ProgressState.NOT_STARTED)
    private val cachingInProgress: MutableStateFlow<Boolean> =
        MutableStateFlow(false)

    /** min tap/grab distance to select an object */
    private var tapRadius = getPlatform().tapRadius
    private val lowAccuracyTapRadius get() = tapRadius*LOW_ACCURACY_FACTOR
    private val tapRadius2 get() = tapRadius*tapRadius
    private val lowAccuracyTapRadius2 get() = tapRadius*tapRadius*LOW_ACCURACY_FACTOR*LOW_ACCURACY_FACTOR

    private var movementAfterDown = false

    init {
        viewModelScope.launch {
            restoreFromDisk()
        }
    }

    /** sets [tapRadius] based on [density] */
    fun setEpsilon(density: Density) {
        with (density) {
            tapRadius = getPlatform().tapRadius.dp.toPx()
        }
    }

    fun clearSelection() { // better for autocomplete
        selection = Selection()
    }

    fun changeCanvasSize(newCanvasSize: IntSize) {
        val prevCenter = Offset(canvasSize.width/2f, canvasSize.height/2f)
        val newCenter = Offset(newCanvasSize.width/2f, newCanvasSize.height/2f)
        translation += (newCenter - prevCenter)
        canvasSize = newCanvasSize
        objectModel.pathCache.invalidateAll()
        objectModel.invalidatePositions()
    }

    // TODO: save history (checkbox)
    fun saveAsYaml(name: String = DdcV5.DEFAULT_NAME): String {
        val yamlString = YamlEncoding.encodeToString(
            DdcV5.fromSaveState(saveState())
                .copy(name = name)
        )
        return yamlString
    }

    fun exportAsSvg(
        name: String = DdcV5.DEFAULT_NAME,
        extendedColorScheme: ExtendedColorScheme = DodeclustersColors.extendedDarkScheme,
    ): String {
        val svgString = saveStateAsSvg(
            saveState = saveState(),
            width = canvasSize.width.toFloat(),
            height = canvasSize.height.toFloat(),
            encodeCirclesAndPoints = showCircles,
            name = name,
            extendedColorScheme = extendedColorScheme,
        )
        return svgString
    }

    private fun computeAbsoluteCenter(): Offset? =
        if (canvasSize == IntSize.Zero) {
            null
        } else {
            val visibleCenter = Offset(canvasSize.width/2f, canvasSize.height/2f)
            absolute(visibleCenter)
        }

    private fun updateLastSaveMetadata(
        filename: String?,
    ) {
        lastSaveMetadata =
            if (filename == null) {
                SaveResult.Cancelled(directory = lastSaveMetadata?.directory)
            } else {
                SaveResult.Success(filename = filename, directory = lastSaveMetadata?.directory)
            }
    }

    // i dont want to make it suspend tbh
    fun loadDdc(content: String, filename: String? = null) {
        tryParseDdc(
            content = content,
            onDdc5 = { ddc5 ->
                val state = ddc5.toSaveState()
                loadState(state)
                updateLastSaveMetadata(filename)
            },
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
                updateLastSaveMetadata(filename)
            },
            onDdc3 = { ddc3 ->
                val constellation = ddc3.toConstellation().toConstellation()
                loadNewConstellation(constellation)
                centerizeTo(ddc3.bestCenterX, ddc3.bestCenterY)
                chessboardPattern =
                    if (!ddc3.chessboardPattern) ChessboardPattern.NONE
                    else if (ddc3.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                    else ChessboardPattern.STARTS_TRANSPARENT
                updateLastSaveMetadata(filename)
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
                updateLastSaveMetadata(filename)
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
                updateLastSaveMetadata(filename)
            },
            onClusterV1 = { cluster1 ->
                loadNewConstellation(
                    cluster1.toCluster().toConstellation()
                )
                updateLastSaveMetadata(filename)
            },
            onFail = {
                queueSnackbarMessage(SnackbarMessage.FAILED_OPEN, filename ?: "")
            },
        )
        resetHistory()
    }

    fun centerizeTo(centerX: Float?, centerY: Float?) {
        translation = -Offset(
            centerX?.let { it - canvasSize.width/2f } ?: 0f,
            centerY?.let { it - canvasSize.height/2f } ?: 0f
        )
    }

    fun openNewBlank() {
//        val presentState = saveState()
//        val expr = ArcPath.Closed(vertices = listOf(1,2), arcs = listOf(ArcPath.Arc.LineSegment, ArcPath.Arc.LineSegment))
//        val exprOutput = ExprOutput.Just(expr)
//        val exprs: Map<Int, ExprOutput<ArcPath.Closed>> = mapOf(0 to exprOutput) //, 1 to null)
//        println("prepared state")
        // BUG: on wasm SaveState encoding breaks (while state.expressions enc doesn't)
        // issue: https://github.com/Kotlin/kotlinx.serialization/issues/3177
//        val str = Choices.JSON_FORMAT.encodeToString(
//            value =
//                Choices(mapOf(0 to Choice.One(A)))
//        )
//        getPlatform().saveState(s)
//        println("encoded: $str")
//        return
        closeDialog()
        loadState(
            SaveState(
                objects = emptyList(),
                expressions = emptyMap(),
                backgroundColor = backgroundColor,
            )
        )
        resetHistory()
        lastSaveMetadata = SaveResult.Cancelled(directory = lastSaveMetadata?.directory)
    }

    fun showDebugInfo() {
        val selectedObjectsString = selection.gCircles.joinToString { ix ->
            "$ix: " + objects[ix].toString()
        }
        val selectedExpressionsString = selection.gCircles.joinToString { ix ->
            "$ix: " + expressions[ix].toString()
        }
        val selectedConcreteArcPathsString = selection.arcPaths.joinToString { ix ->
            "$ix: " + objects[ix].toString()
        }
        val selectedArcPathsString = selection.arcPaths.joinToString { ix ->
            "$ix: " + expressions[ix].toString()
        }
        println("mode = $mode, submode = $submode")
        println("partialArgList = $partialArgList")
        println("selection = $selection")
        println("selected objects = $selectedObjectsString")
        println("selected objects downscaled = " + objectSelection.joinToString { ix ->
            "$ix: " + objectModel.downscaledObjects[ix].toString()
        })
        println("selected objects expressions = $selectedExpressionsString")
        println("selected concrete arc-paths = $selectedConcreteArcPathsString")
        println("selected arc-paths = $selectedArcPathsString")
        println(
            "regions bounded by some of selected objects = " + regions.filter {
                it.insides.any { ix -> ix in objectSelection } ||
                it.outsides.any { ix -> ix in objectSelection }
            }.joinToString { it.toString() }
        )
        println("partialArcPath = $partialArcPath")
        println("invalidation #${objectModel.invalidations}\t " +
                "propertyInvalidation #${objectModel.propertyInvalidations}"
        )
        if (selection.isNotEmpty()) {
            val s1 = if (selectedObjectsString.isEmpty()) "" else "$selectedObjectsString;\n"
            val s2 = if (selectedExpressionsString.isEmpty()) "" else "$selectedExpressionsString;\n"
            val s3 = if (selectedArcPathsString.isEmpty()) "" else "$selectedArcPathsString;\n"
            queueSnackbarMessage(SnackbarMessage.PLACEHOLDER,
                "$s1$s2$s3"
            )
        }
    }

    fun loadNewConstellation(constellation: Constellation) {
        val updatedConstellation = constellation.updated()
        resetTransients()
        chessboardPattern = ChessboardPattern.NONE
        translation = Offset.Zero
        loadConstellation(updatedConstellation)
        println("loaded new constellation")
        if (!mode.isSelectingCircles()) {
            selectTool(Tool.Drag)
        }
    }

    private fun loadConstellation(constellation: Constellation) {
        labels = emptyMap()
        regions = emptyList() // important, since draws are async (otherwise can crash)
        clearSelection()
        objectModel.loadConstellation(constellation)
        val objectIndices = objects.indices.toSet()
        regions = constellation.parts
            .filter { part -> // region validation
                part.insides.all { it in objectIndices } &&
                part.outsides.all { it in objectIndices }
            }
        backgroundColor = constellation.backgroundColor
        labels = constellation.objectLabels
        objectModel.invalidate()
    }

    private fun resetHistory() {
        history = ChangeHistory(
            initialState = saveState(),
            undoIsEnabled = undoIsEnabled,
            redoIsEnabled = redoIsEnabled,
        )
    }

    /** saves present state, diffs it with [history]`.lastRecordedState` and
     * records changes to [history]`.past`, enabling [undo]s */
    private fun recordHistory() {
        history.recordDiff(saveState())
    }

    fun undo() {
        if (!undoIsEnabled.value)
            return
        val m = mode
        if (m is ToolMode && partialArgList?.args?.isNotEmpty() == true) {
            // MAYBE: just pop the last arg
            partialArgList = PartialArgList(m.signature, m.nonEqualityConditions)
            if (submode is SubMode.ExprAdjustment<*>) {
                cancelExprAdjustment()
            }
        } else {
            when (submode) {
                is SubMode.RotateStereographicSphere -> switchToCategory(Category.Drag)
                else -> switchToMode(mode) // clears up stuff
            }
            clearSelection()
            val presentState = saveState()
            val newState = history.undo(presentState)
            loadState(newState)
            resetTransients()
        }
        objectModel.invalidate()
    }

    fun redo() {
        if (!redoIsEnabled.value)
            return
        switchToMode(mode)
        val presentState = saveState()
        val newState = history.redo(presentState)
        loadState(newState)
        resetTransients()
        objectModel.invalidate()
    }

    private fun loadState(state: SaveState) {
        resetTransients()
        labels = emptyMap()
        regions = emptyList() // important, since draws are async (otherwise can crash)
        clearSelection()
        objectModel.loadState(state)
        regions = state.regions
        backgroundColor = state.backgroundColor
        labels = state.labels
        val validSelection = state.selection.copy(
            // just in case
            gCircles = state.selection.gCircles.filter { it in objects.indices },
            arcPaths = state.selection.arcPaths.filter { it in objects.indices }
        )
        val switchToMultiselect = objectSelection.size <= 1 && validSelection.gCircles.size > 1
        selection = validSelection
        if (state.center.isSpecified)
            centerizeTo(state.center.x, state.center.y)
        else
            translation = Offset.Zero
        chessboardPattern = state.chessboardPattern
        regionColor = state.regionColor ?: regionColor
        chessboardColor = state.chessboardColor ?: regionColor
        objectModel.invalidate()
        if (switchToMultiselect) {
            switchToMode(SelectionMode.Multiselect)
        }
    }

    private fun resetTransients() {
        showPromptToSetActiveSelectionAsToolArg = false
        submode = null
    }

    /** Append (upscaled) [newGCircle] to [objects], set it as [selection], invalidate,
     * accumulate history and queue circle entrance animation
     * @return its index
     */
    fun createNewGCircle(newGCircle: GCircle?): Ix {
        return createNewGCircles(listOf(newGCircle)).last
    }

    /** Append (upscaled) [newGCircles] to [objects],
     * set them as [selection], invalidate, accumulate history and
     * queue circle entrance animation
     * @return their index range
     */
    fun createNewGCircles(
        newGCircles: List<GCircle?>,
    ): IntRange {
        val oldSize = objects.size
        val validNewGCircles = newGCircles.filterNotNull()
        objectModel.addDisplayObjects(newGCircles)
        val newIndices = oldSize until objects.size
        if (validNewGCircles.isNotEmpty()) {
            showCircles = true
            selection = Selection(gCircles = newIndices.filter { objects[it] is GCircle })
            viewModelScope.launch {
                animations.emit(
                    CircleAnimation.Entrance(validNewGCircles.filterIsInstance<CircleOrLine>())
                )
            }
        } else { // all nulls
            clearSelection()
        }
        objectModel.invalidate()
        return newIndices
    }

    /** add new free [point] to [objects], invalidate and accumulate history
     * @return its new index
     */
    fun createNewFreePoint(point: Point): Ix {
        val newIx = expressions.addFree()
        objectModel.addDisplayObject(point)
        objectModel.invalidate()
        require(newIx == objects.lastIndex) { "Incorrect index retrieved from expression.addFree() during createNewFreePoint()" }
        return newIx
    }

    /**
     * Copy [regions] from source indices onto trajectories specified
     * by [source2trajectory].
     * @param[source2trajectory] `[(original index ~ style source, [trajectory of indices of objects])]`,
     * note that original indices CAN repeat (tho its regions will be copied only once even for the repeats).
     * @return indices of copied regions within [regions], flattened trajectory of regions
     */
    private fun copySourceRegionsOntoTrajectories(
        source2trajectory: List<Pair<Ix, List<Ix>>>,
    ): List<Int> {
        val newRegionIndices = source2trajectory
            .map { (sourceIndex, trajectory) ->
                trajectory.map { outputIndex ->
                    sourceIndex to outputIndex
                } // Column<Row<(OG Ix, new Ix)?>>
            }.transpose()
            .flatMap { trajectoryStageSlice ->
                // Column<(OG Ix, new Ix)>
                val nonNullSlice = trajectoryStageSlice.filterNotNull()
                // for each stage in the trajectory we try to copy regions
                if (nonNullSlice.isNotEmpty()) {
                    copyRegions(
                        oldIndices = nonNullSlice.map { it.first },
                        newIndices = nonNullSlice.map { it.second },
                        flipInAndOut = false,
                    )
                } else emptyList()
            }
        return newRegionIndices
    }

    fun duplicateSelection() {
        if (mode.isSelectingCircles()) {
            val gCirclesToCopy = selection.gCircles
            val arcPathsToCopy = expressions.sortedByTier(selection.arcPaths)
            val deps = arcPathsToCopy.flatMap {
                objectModel.getArcPath(it)?.dependencies ?: emptySet()
            }.toSet()
            // pre-sorting is mandatory for expression copying to work properly
            val allGCirclesToCopy = expressions.sortedByTier(
                (gCirclesToCopy.toSet() + deps).sorted()
            )
            val allObjectsToCopy = expressions.sortedByTier(
                (allGCirclesToCopy + arcPathsToCopy).sorted()
            )
            if (allGCirclesToCopy.isNotEmpty()) { // empty GCircles => empty arc-paths
                val oldSize = objects.size
                for (ix in allObjectsToCopy) {
                    val newIndex = objectModel.addDownscaledObject(
                        objectModel.downscaledObjects[ix]
                    )
                    copyBorderColor(ix, newIndex)
                    copyFillColor(ix, newIndex)
                    // we don't copy labels
                }
                expressions.copyExpressionsWithDependencies(allObjectsToCopy)
                val newIndices = (oldSize until objects.size).toList()
                copyRegions(allObjectsToCopy, newIndices, flipInAndOut = false)
                // TODO: arc-path duplication animation
                val newGCircleIndices = newIndices.filter { objects[it] is GCircle }
                val newArcPathIndices = newIndices.filter { objects[it] is ConcreteArcPath }
                selection = if (mode == SelectionMode.Drag) {
                    if (gCirclesToCopy.isNotEmpty())
                        Selection(gCircles = newGCircleIndices.take(1))
                    else if (newArcPathIndices.isNotEmpty())
                        Selection(arcPaths = newArcPathIndices.take(1))
                    else
                        Selection(gCircles = newIndices.take(1))
                } else {
                    Selection(
                        gCircles = newGCircleIndices,
                        arcPaths = newArcPathIndices,
                    )
                }
                viewModelScope.launch {
                    animations.emit(CircleAnimation.ReEntrance(
                        allObjectsToCopy
                            .map { objects[it] }
                            .filterIsInstance<CircleOrLine>()
                            .map { it.upscale() }
                    ))
                }
                objectModel.invalidate()
                recordHistory()
            }
        }
    }

    private fun copyBorderColor(
        sourceIndex: Ix,
        destinationIndex: Ix,
    ) {
        objectModel.borderColors[sourceIndex]?.let { color ->
            objectModel.borderColors[destinationIndex] = color
        }
    }

    private fun copyFillColor(
        sourceIndex: Ix,
        destinationIndex: Ix,
    ) {
        objectModel.fillColors[sourceIndex]?.let { color ->
            objectModel.fillColors[destinationIndex] = color
        }
    }

    private fun copyStyle(
        sourceIndex: Ix,
        destinationIndex: Ix,
    ) {
        copyBorderColor(sourceIndex, destinationIndex)
        copyFillColor(sourceIndex, destinationIndex)
        if (sourceIndex in objectModel.phantomObjectIndices)
            objectModel.phantomObjectIndices.add(destinationIndex)
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
        val startIndex = regions.size
        regions += newRegions
        return startIndex until regions.size
    }

    fun deleteSelection() {
        val gCirclesToDelete = selection.gCircles
        val arcPathsToDelete = selection.arcPaths
        if ((showCircles && gCirclesToDelete.isNotEmpty() || arcPathsToDelete.isNotEmpty()) &&
            (mode.isSelectingCircles() || mode == ToolMode.ARC_PATH) // allow instant arc-path deletion
        ) {
            deleteObjectsWithDependenciesColorsAndRegions(selection.indices)
            recordHistory()
        }
    }

    private inline fun deleteObjectsWithDependenciesColorsAndRegions(
        indicesToDelete: List<Ix>,
        crossinline circleAnimationInit: (List<CircleOrLine>) -> CircleAnimation? = { deletedCircles ->
            CircleAnimation.Exit(deletedCircles)
        },
    ) {
        clearSelection()
        val indicesToDeleteSet = indicesToDelete.toSet()
        val arcPathsToDelete = indicesToDelete.filter { objects[it] is ConcreteArcPath }
        val arcPathPointsToDelete = mutableListOf<Ix>()
        for (ix in arcPathsToDelete) {
            // this misses free points of second-order dependent arc-paths
            // that only come up after expressions.deleteNodes
            val arcPath = objectModel.getArcPath(ix)
            if (arcPath != null) {
                arcPathPointsToDelete += arcPath.dependencies.filter { ix ->
                    val childs = expressions.children[ix]
                    isFree(ix) &&
                        (childs == null || indicesToDeleteSet.containsAll(childs))
                }
            }
        }
        val toDelete = indicesToDeleteSet + arcPathPointsToDelete
        val (deletedIndices, changedIndices) = expressions.deleteNodes(toDelete.toList())
        val deletedCircleIndices = deletedIndices.filter { objects[it] is CircleOrLine }
        if (deletedCircleIndices.isNotEmpty()) {
            deleteRegionsBoundBy(deletedCircleIndices)
            val deletedCirclesOrLines = deletedCircleIndices
                .mapNotNull { objects[it] as? CircleOrLine }
            circleAnimationInit(deletedCirclesOrLines)?.let { circleAnimation ->
                viewModelScope.launch {
                    animations.emit(circleAnimation)
                }
            }
        }
        objectModel.removeObjectsAt(deletedIndices)
        // NOTE: changedIndices are ArcPaths with null-ed vertices
        //  it may be sensible to remove those non-existent vertices altoghether now
        // cuz we might've deleted some arc-path vertices
        objectModel.forceUpdate(changedIndices)
        objectModel.invalidate()
    }

    private fun deleteRegionsBoundBy(indices: List<Ix>) {
        val circleOrLineIndices = indices
            .filter { objects[it] is CircleOrLine }
            .toSet()
        if (circleOrLineIndices.isNotEmpty()) {
            val everyBound = circleOrLineIndices.containsAll(
                objects.filterIndices { it is CircleOrLine }
            )
            val oldRegions = regions.toList()
            regions = emptyList()
            if (everyBound) {
                if (chessboardPattern == ChessboardPattern.STARTS_COLORED) {
                    chessboardPattern = ChessboardPattern.STARTS_TRANSPARENT
                }
            } else { // not everything
                regions = oldRegions
                    // to avoid stray chessboard selections
                    .filterNot { (ins, _, _) ->
                        ins.isNotEmpty() && ins.minus(circleOrLineIndices).isEmpty()
                    }
                    .map { (ins, outs, fillColor) ->
                        LogicalRegion(
                            insides = ins.minus(circleOrLineIndices),
                            outsides = outs.minus(circleOrLineIndices),
                            fillColor = fillColor
                        )
                    }
                    .filter { (ins, outs) -> ins.isNotEmpty() || outs.isNotEmpty() }
            }
        }
    }

    fun getArg(arg: Arg): GCircle? =
        when (arg) {
            is Arg.Index -> objects[arg.index] as? GCircle
            is Arg.PointXY -> arg.toPoint()
            is Arg.Indices -> null
            is Arg.InfinitePoint -> Point.CONFORMAL_INFINITY
        }

    fun switchToMode(newMode: Mode) {
        // NOTE: these altering shortcuts are unused for now so that they don't confuse category-expand buttons
        if (objectSelection.size > 1 && newMode == SelectionMode.Drag) {
            clearSelection()
        }
        showPromptToSetActiveSelectionAsToolArg = false
        if (newMode is ToolMode) {
            if (newMode.signature.argTypes.first() == ArgType.INDICES &&
                // we don't prompt to accept a singular GCircle
                (selection.arcPaths.isNotEmpty() || selection.gCircles.size > 1)
            ) {
                showPromptToSetActiveSelectionAsToolArg = true
            } else {
                // keep selection for a bit in case we now switch to another mode that
                // accepts selection as the first arg
            }
            if (newMode == ToolMode.ARC_PATH) {
                clearSelection()
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
        val d2 = (absolutePosition - position).getDistanceSquared()
        return if (lowAccuracy)
            d2 <= lowAccuracyTapRadius2
        else
            d2 <= tapRadius2
    }

    fun getPointsAround(
        targets: List<GCircleOrConcreteAcPath?>,
        visiblePosition: Offset,
        priorityTargets: Set<Int> = emptySet(),
    ): List<Int> {
        val position = absolute(visiblePosition)
        val absolutePoint = Point.fromOffset(position)
        return targets.indicesSortedBy(
            measurer = { o ->
                val point = o as? Point
                    ?: return@indicesSortedBy Double.POSITIVE_INFINITY
                point.distanceFrom(absolutePoint)
            },
            condition = { ix, distance ->
                distance <= tapRadius && (showPhantomObjects || ix !in phantoms)
            },
            sortingPriority = { ix, distance ->
                val priority =
                    if (ix in priorityTargets) 100
                    else 1
                distance / priority
            }
        )
    }

    /** [getPointsAround] around [visiblePosition] while prioritizing free points */
    fun getPreferablyFreePointsAround(visiblePosition: Offset): List<Ix> {
        // TODO: use expressions.pointIndices
        val closePoints = getPointsAround(objects, visiblePosition,
            priorityTargets = expressions.freeObjectsIndices
        )
        return closePoints
    }

    /**
     * @param[targets] to select from, [ImaginaryCircle]s are converted to real [Circle]s
     */
    private fun getCirclesAround(
        targets: List<GCircleOrConcreteAcPath?>,
        visiblePosition: Offset,
        priorityTargets: Set<Int> = emptySet(),
    ): List<Int> {
        val position = absolute(visiblePosition)
        return targets.indicesSortedBy(
            measurer = { o ->
                val circle = when (o) {
                    is Circle -> o
                    is Line -> o
                    is ImaginaryCircle -> o.toRealCircle()
                    else -> null
                }
                circle?.distanceFrom(position) ?: Double.POSITIVE_INFINITY
            },
            condition = { ix, distance ->
                distance <= tapRadius && (showPhantomObjects || ix !in phantoms)
            },
            sortingPriority = { ix, distance ->
                val priority =
                    if (ix in priorityTargets) 100
                    else 1
                distance / priority
            }
        )
    }

    /** [getCirclesAround] around [visiblePosition] while prioritizing free circles */
    fun getPreferablyFreeCirclesAround(visiblePosition: Offset): List<Ix> {
        val closeCircles = getCirclesAround(objects, visiblePosition,
            priorityTargets = expressions.freeObjectsIndices
        )
        return closeCircles
    }

    fun getArcPathsAround(visiblePosition: Offset): List<Ix> {
        val position = Point.fromOffset(absolute(visiblePosition))
        return objects.indicesSortedBy(
            measurer = {
                (it as? ConcreteArcPath)?.distanceFrom(position) ?: Double.POSITIVE_INFINITY
            },
            condition = { _, distance -> distance <= tapRadius },
        )
    }

    private fun findSiblingsAndParents(ix: Ix): List<Ix> {
        val expr = exprOf(ix) ?: return emptyList()
        val parents = expr.args
        val siblings = expressions.findExpr(expr)
        return siblings + parents
    }

    // NOTE: region boundaries get messed up when we alter a big structure like spiral
    /** @return (compressed region, verbose region involving all circles) surrounding clicked position */
    private fun getRegionAround(
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
        val (compressedRegion, uncompressedRegion) = getRegionAround(visiblePosition, boundingCircles)
        regions = RegionManipulationStrategy.updateRegionsAfterReselection(
            compressedRegion = compressedRegion,
            uncompressedRegion = uncompressedRegion,
            allRegions = regions,
            regionManipulationStrategy = regionManipulationStrategy,
            shouldUpdateSelection = shouldUpdateSelection,
            setSelection = { selection = Selection(gCircles = it) },
        )
    }

    /** @return [Rect] using absolute positions */
    fun calculateSelectionRect(): Rect? {
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (ix in selection.indices) {
            when (val o = objects[ix]) {
                is Circle -> {
                    left = min(left, (o.x - o.radius).toFloat())
                    right = max(right, (o.x + o.radius).toFloat())
                    top = min(top, (o.y - o.radius).toFloat())
                    bottom = max(bottom, (o.y + o.radius).toFloat())
                }
                is Point -> {
                    left = min(left, o.x.toFloat())
                    right = max(right, o.x.toFloat())
                    top = min(top, o.y.toFloat())
                    bottom = max(bottom, o.y.toFloat())
                }
                is Line -> return null
                is ConcreteArcPath -> {
                    val (left1, top1, right1, bottom1) = o.toRect()
                    left = min(left, left1)
                    right = max(right, right1)
                    top = min(top, top1)
                    bottom = max(bottom, bottom1)
                }
                else -> {}
            }
        }
        if (left.isInfinite() || right.isInfinite() || top.isInfinite() || bottom.isInfinite())
            return null
        return Rect(left, top, right, bottom)
    }

    inline fun exprOf(index: Ix): Expr.Conformal? =
        expressions[index]?.expr as? Expr.Conformal

    inline fun isFree(index: Ix): Boolean =
        expressions[index] == null

    inline fun isConstrained(index: Ix): Boolean {
        val expr = exprOf(index)
        return expr is Expr.Incidence || expr is Expr.ArcPathIncidence
    }

    // MAYBE: wrap into state that depends only on
    //  [regions, objectColors, chessboardPattern, chessboardColor] for caching
    //  tho from tests this function behaves the same way
    // MAYBE: also add backgroundColor (tho it is MT.surface by default and thus 0-contrast)
    fun getColorsByMostUsed(): List<Color> {
        hug(objectModel.propertyInvalidations)
        val regionBorderColors = regions.mapNotNull { it.borderColor }
        val regionFillColors = regions.map { it.fillColor }
        val borderColors = objectModel.borderColors.values
        val fillColors = objectModel.fillColors.values
        val chessboardColors =
            if (chessboardPattern == ChessboardPattern.NONE)
                emptyList()
            else
                listOf(chessboardColor)
        return (regionBorderColors + regionFillColors + borderColors + fillColors + chessboardColors)
            .sortedByFrequency()
    }

    /**
     * Try to snap [absolutePosition] to some existing object or their intersection.
     * Snap priority: points > circles
     */
    fun snapped(
        absolutePosition: Offset,
        excludePoints: Boolean = false,
        excludedCircles: Set<Ix> = emptySet(),
        excludedArcPaths: Set<Int> = emptySet(),
    ): PointSnapResult {
        val snapDistance = tapRadius.toDouble()
        val point = Point.fromOffset(absolutePosition)
        val toPoints = !excludePoints && mode != ToolMode.POINT
        if (toPoints) {
            // TODO: use oM.e.pointIndices etc
            val snap = Snapping.snapPointToPoints(point, objects,
                snapDistance = snapDistance,
                excludedIndices = if (showPhantomObjects) emptySet() else phantoms
            )
            if (snap is PointSnapResult.Eq)
                return snap
        }
        val toCircles = showCircles // no snapping to invisibles
        if (toCircles) {
            val snap = Snapping.snapPointToCircles(point, objects,
                snapDistance = snapDistance,
                excludedIndices =
                    if (showPhantomObjects) excludedCircles
                    else excludedCircles.union(phantoms)
            )
            if (!snap.isFree)
                return snap
        }
        val snap = Snapping.snapPointToArcPaths(point, objects,
            snapDistance = snapDistance,
            excludedIndices = excludedArcPaths,
        )
        if (snap is PointSnapResult.ArcPathIncidence)
            return snap
        return PointSnapResult.Free(point)
    }

    /** Adds a new point(s) with expression defined by [snapResult] when non-free
     * @return the same [snapResult] if [snapResult] is [PointSnapResult.Free], otherwise
     * [PointSnapResult.Eq] that points to the newly added point */
    private fun realizePointSnap(
        snapResult: PointSnapResult,
        recordHistory: Boolean = true,
    ): PointSnapResult.PointToPoint {
        return when (snapResult) {
            is PointSnapResult.Free -> snapResult
            is PointSnapResult.Eq -> snapResult
            is PointSnapResult.Incidence -> {
                val circle = objectModel.downscaledObjects[snapResult.circleIndex] as CircleOrLine
                // NOTE: we have to downscale to measure order for lines properly
                val order = circle.point2order(snapResult.result.downscale())
                val expr = Expr.Incidence(
                    IncidenceParameters(order),
                    snapResult.circleIndex
                )
                val newPoint = (expressions.addSoloExpr(expr) as Point).upscale()
                val ix = createNewGCircle(newPoint)
                if (recordHistory)
                    recordHistory()
                PointSnapResult.Eq(newPoint, ix)
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
                    val intersectionOutputIndex = computeIntersection(
                        objects[ix1] as CircleOrLine,
                        objects[ix2] as CircleOrLine
                    ).withIndex().minBy { (_, p) ->
                        p?.let { point.distanceFrom(p) } ?: Double.POSITIVE_INFINITY
                    }.index
                    if (closestIndex != null) { // far intersection already exists
                        val p = expressions.addMultiExpression(
                            ExprOutput.OneOf(expr, intersectionOutputIndex)
                        ) as Point
                        val ix = createNewGCircle(p.upscale())
                        if (recordHistory)
                            recordHistory()
                        PointSnapResult.Eq(snapResult.result, ix)
                    } else {
                        val points = expressions.addMultiExpr(expr)
                            .map { (it as? Point)?.upscale() }
                        createNewGCircles(points)
                        val ix = oldSize + intersectionOutputIndex
                        if (recordHistory)
                            recordHistory()
                        PointSnapResult.Eq(snapResult.result, ix)
                    }
                }
            }
            is PointSnapResult.ArcPathIncidence -> {
                val concreteArcPath = objectModel.downscaledObjects[snapResult.arcPathIndex]
                    as? ConcreteArcPath ?: return snapResult.toFree()
                val (arcIndex, order) = computeArcPathIncidenceOrder(
                    concreteArcPath,
                    snapResult.result.downscale(),
                )
                if (arcIndex != snapResult.arcIndex)
                    return snapResult.toFree()
                val expr = Expr.ArcPathIncidence(
                    parameters = ArcPathIncidenceParameters(
                        arcIndex = snapResult.arcIndex,
                        arcPercentage = order,
                    ),
                    arcPath = snapResult.arcPathIndex,
                )
                val result = expressions.addSoloExpr(expr) as? Point
                val incidentPoint = result?.upscale()
                val ix = createNewGCircle(incidentPoint)
                if (recordHistory)
                    history.recordDiff(saveState())
                if (incidentPoint == null)
                    snapResult.toFree()
                else
                    PointSnapResult.Eq(
                        result = incidentPoint,
                        pointIndex = ix,
                    )
            }
            else -> snapResult.toFree()
        }
    }

    fun activateRectangularSelect() {
        switchToMode(SelectionMode.Multiselect)
        clearSelection()
        submode = SubMode.RectangularSelect()
    }

    fun activateFlowSelect() {
        switchToMode(SelectionMode.Multiselect)
        clearSelection()
        submode = SubMode.FlowSelect()
    }

    fun activateFlowFill() {
        switchToMode(SelectionMode.Region)
        submode = SubMode.FlowFill()
    }

    fun forceSelectAll() {
        if (!mode.isSelectingCircles() || !showCircles) { // more intuitive behavior
            // forces to select all instead of toggling
            clearSelection()
        }
        switchToCategory(Category.Multiselect)
        toggleSelectAll()
    }

    fun toggleSelectAll() {
        switchToMode(SelectionMode.Multiselect)
        showCircles = true
        val allCLPIndices = expressions.gCircleIndices.filter { objects[it] is CircleOrLineOrPoint }
        val allArcPathIndices = expressions.arcPathIndices.filter { objects[it] is ConcreteArcPath }
        val everythingIsSelected = selection.gCircles.containsAll(allCLPIndices)
        selection =
            if (everythingIsSelected)
                Selection()
            else
                Selection(
                    // maybe select imaginary too
                    gCircles = allCLPIndices,
                    arcPaths = allArcPathIndices,
                )
    }

    fun toggleShowCircles() {
        showCircles = !showCircles
        if (!showCircles && mode is ToolMode)
            switchToMode(SelectionMode.Drag)
        clearSelection()
    }

    fun togglePhantomObjects() {
        showPhantomObjects = !showPhantomObjects
        if (phantoms.isEmpty()) {
            queueSnackbarMessage(SnackbarMessage.PHANTOM_OBJECT_EXPLANATION)
        }
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
        chessboardPattern = when (chessboardPattern) {
            ChessboardPattern.NONE -> ChessboardPattern.STARTS_COLORED
            ChessboardPattern.STARTS_COLORED -> ChessboardPattern.STARTS_TRANSPARENT
            ChessboardPattern.STARTS_TRANSPARENT -> ChessboardPattern.NONE
        }
        if (chessboardPattern != ChessboardPattern.NONE) {
            chessboardColor = regionColor
        }
        recordHistory()
    }

    fun requestOpenFile() {
        openFileRequests.tryEmit(Unit)
    }

    fun requestSaveFileAs() {
        // we have to open SaveOptionsDialog first so that
        // SaveFileButton starts listening to SaveRequests
        openedDialog = DialogType.SAVE_OPTIONS
        viewModelScope.launch {
            // have to delay a bit for the dialog to open
            delay(200.milliseconds)
            // ts ugly tbh
            saveFileRequests.emit(SaveRequest.SAVE_AS)
        }
    }

    fun newBlank() {
        openedDialog = DialogType.SAVE_PROMPT
        queuedAction = Action.NEW_BLANK
    }

    fun concludeRegionColorPicker(colorPickerParameters: ColorPickerParameters) {
        openedDialog = null
        regionColor = colorPickerParameters.currentColor
        this.colorPickerParameters = colorPickerParameters
        switchToCategory(Category.Region)
    }

    fun concludeBorderColorPicker(colorPickerParameters: ColorPickerParameters) {
        val color = colorPickerParameters.currentColor
        for (ix in selection.indices) {
            objectModel.borderColors[ix] = color
        }
        openedDialog = null
        this.colorPickerParameters = colorPickerParameters
        objectModel.invalidate()
        recordHistory()
    }

    fun concludeFillColorPicker(colorPickerParameters: ColorPickerParameters) {
        val color = colorPickerParameters.currentColor
        for (ix in selection.arcPaths) {
            objectModel.fillColors[ix] = color
        }
        openedDialog = null
        this.colorPickerParameters = colorPickerParameters
        objectModel.invalidate()
        recordHistory()
    }

    fun concludeBackgroundColorPicker(colorPickerParameters: ColorPickerParameters) {
        backgroundColor = colorPickerParameters.currentColor
        openedDialog = null
        this.colorPickerParameters = colorPickerParameters
        recordHistory()
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
        if (category != null) {
            selectCategory(category, togglePanel = true)
        }
        toolbarState = toolbarState.copy(activeTool = tool)
    }

    fun getMostCommonBorderColorInSelection(): Color? {
        hug(objectModel.propertyInvalidations)
        return selection.indices
            .map { objectModel.borderColors[it] }
            .mostCommonOf { it }
    }

    fun getMostCommonFillColorInSelection(): Color? {
        hug(objectModel.propertyInvalidations)
        return selection.arcPaths
            .map { objectModel.fillColors[it] }
            .mostCommonOf { it }
    }

    // MAYBE: replace with select-all->delete in invisible-circles region manipulation mode
    fun deleteAllRegions() {
        chessboardPattern = ChessboardPattern.NONE
        regions = emptyList()
        recordHistory()
    }

    fun setRegionsManipulationStrategy(newStrategy: RegionManipulationStrategy) {
        regionManipulationStrategy = newStrategy
    }

    fun cancelSelectionAsToolArgPrompt() {
        if (showPromptToSetActiveSelectionAsToolArg) {
            showPromptToSetActiveSelectionAsToolArg = false
            clearSelection()
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
            Arg.Indices(selection.indices.filter { objects[it] != null }),
            confirmThisArg = true
        )
        cancelSelectionAsToolArgPrompt()
        if (partialArgList!!.isFull) {
            completeToolMode()
        }
    }

    // MAYBE: axis-aligned cross centered at a point
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
        expressions.addFree()
        expressions.addFree()
        createNewGCircles(listOf(horizontalLine, verticalLine))
        switchToMode(SelectionMode.Multiselect)
        val indices = listOf(objects.size - 2, objects.size - 1)
        selection = Selection(gCircles = indices)
        recordHistory()
    }

    fun scaleSelection(zoom: Float) {
        if (mode == ToolMode.ARC_PATH && partialArcPath != null) {
//            arcPathUnderConstruction = arcPathUnderConstruction?.scale(zoom)
        } else {
            // weird history shenanigans... cuz we want to pin-record on the first zoom
            // action in a sequence
            val firstZoom = history.newContinuousChange(ContinuousChange.ZOOM)
            if (mode.isSelectingCircles() &&
                (showCircles && selection.gCircles.isNotEmpty() || selection.arcPaths.isNotEmpty())
            ) {
                val rect = calculateSelectionRect()
                val focus =
                    if (rect == null || rect.minDimension >= 5_000)
                        computeAbsoluteCenter() ?: Offset.Zero
                    else rect.center
                transformWhatWeCan(selectedIndices,
                    focus = focus, zoom = zoom,
                    continuousChange = ContinuousChange.ZOOM,
                )
            } else { // zoom everything
                val targets = objects.indices.toList()
                val center = computeAbsoluteCenter() ?: Offset.Zero
                val changedIndices = objectModel.transform(targets, focus = center, zoom = zoom)
                // zooming ignores concrete-arc-paths
                expressions.reEval() // overboard but w/e
                objectModel.syncDisplayObjects(objects.indices)
                history.accumulateChangedLocations(
                    objectIndices = changedIndices,
                    // zoom affects point-line incidence
                    expressionIndices = changedIndices,
                    continuousChange = ContinuousChange.ZOOM
                )
            }
            if (firstZoom) {
                recordHistory()
            }
            // NOTE: with this continuous change flipping setup, the first zoom triggers proper
            //  recording, but subsequent ones only accumulate pointless locations that
            //  in turn would be prepended as a duplicating change to the later recording
        }
    }

    private fun detachEverySelectedObject() {
        for (ix in objectSelection) {
            expressions.changeToFree(ix)
        }
        objectModel.invalidate()
        recordHistory()
    }

    fun setLabel(label: String?) {
        val labels = labels.toMutableMap()
        if (label == null) {
            labels -= objectSelection.toSet()
        } else {
            for (ix in objectSelection) {
                labels[ix] = label
            }
        }
        this@EditorViewModel.labels = labels.toMap()
        openedDialog = null
        recordHistory()
    }

    private fun markSelectedObjectsAsPhantoms() {
        objectModel.phantomObjectIndices.addAll(objectSelection)
        // being able to instantly undo is prob better ux
        // showPhantomObjects = false // i think this behavior is confuzzling
        objectModel.invalidate()
    }

    private fun unmarkSelectedObjectsAsPhantoms() {
        objectModel.phantomObjectIndices.removeAll(objectSelection.toSet())
        objectModel.invalidate()
    }

    private fun swapOrientationsInSelection() {
        val targets = selection.gCircles.filter { ix ->
            objects[ix] is CircleOrLine && isFree(ix)
        }
        if (targets.isEmpty()) {
            if (selection.gCircles.size == 1)
                queueSnackbarMessage(SnackbarMessage.LOCKED_OBJECT_NOTICE)
            else if (selection.gCircles.size > 1)
                queueSnackbarMessage(SnackbarMessage.LOCKED_OBJECTS_NOTICE)
        } else {
            objectModel.setDisplayObjectsWithConsequences(
                targets.associateWith { ix ->
                    val obj0 = objects[ix] as CircleOrLine
                    obj0.reversed()
                }
            )
            recordHistory()
        }
    }

    inline val showAdjustExprButton: Boolean get() {
        val sel = objectSelection
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
        val firstSelected = selection.gCircles.firstOrNull() ?: return
        val expr = exprOf(firstSelected)
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
            partialArgList = when (expr) {
                is Expr.CircleInterpolation ->
                    PartialArgList(
                        tool.signature,
                        tool.nonEqualityConditions,
                        args = listOf(
                            Arg.IndexOf(expr.startCircle, objects[expr.startCircle] as GCircle),
                            Arg.IndexOf(expr.endCircle, objects[expr.endCircle] as GCircle)
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
                            Arg.IndexOf(expr.engine1, objects[expr.engine1] as GCircle),
                            Arg.IndexOf(expr.engine2, objects[expr.engine2] as GCircle),
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
            val adjustables = listOf(AdjustableExpr(
                expr, firstSelected, outputIndices, outputIndices
            ))
            val allAdjustables: List<AdjustableExpr<Expr>> =
                if (expr is Expr.LoxodromicMotion && expr.otherHalfStart != null) {
                    exprOf(expr.otherHalfStart)?.let { otherExpr ->
                        val otherOutputIndices = expressions.findExpr(otherExpr)
                        val otherAdjustables = listOf(AdjustableExpr(
                            otherExpr, expr.otherHalfStart, otherOutputIndices, otherOutputIndices
                        ))
                        defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(
                            expr.parameters,
                            bidirectional = true
                        )
                        adjustables + otherAdjustables
                    } ?: adjustables
                } else adjustables
            submode = SubMode.ExprAdjustment(allAdjustables)
            clearSelection() // clear selection to hide selection HUD
        }
    }

    // might be useful for duplication with dependencies
    /** For each object in [objectSelection], add to selection its siblings and parents */
    private fun expandSelectionToFamily() {
        if (mode.isSelectingCircles()) {
            val familyMembers = objectSelection.flatMap { ix ->
                listOf(ix) + findSiblingsAndParents(ix)
            }.distinct()
            if (familyMembers.size > 1 && mode == SelectionMode.Drag) {
                switchToMode(SelectionMode.Multiselect)
            }
            selection = Selection(gCircles = familyMembers)
        }
    }

    /** sibling = has the same [Expr] (possibly `null`) */
    private fun expandSelectionToAllSiblings() {
        if (mode.isSelectingCircles()) {
            val siblings = objectSelection
                .map { exprOf(it) }
                .distinct()
                .flatMap { expr ->
                    expressions.findExpr(expr)
                }
            if (mode == SelectionMode.Drag && siblings.size > 1) {
                switchToMode(SelectionMode.Multiselect)
            }
            selection = Selection(gCircles = siblings)
        }
    }

    fun onDown(position: Offset) {
        if (history.newContinuousChange(null)) {
            recordHistory()
        }
        movementAfterDown = false
        if (submode is SubMode.SelectionChoices)
            submode = null
        if (showCircles) {
            when (handleConfig) {
                HandleConfig.SINGLE_CIRCLE -> {
                    val circle = objects[selection.gCircles.single()]
                    if (circle is Circle) {
                        val radiusHandlePosition = circle.center + Offset(circle.radius.toFloat(), 0f)
                        when {
                            isCloseEnoughToSelect(radiusHandlePosition, position, lowAccuracy = true) ->
                                submode = SubMode.Scale(circle.center)
                        }
                    }
                }
                HandleConfig.SEVERAL_OBJECTS -> {
                    calculateSelectionRect()?.let { rect ->
                        val scaleHandlePosition = rect.topRight
                        val rotateHandlePosition = rect.bottomRight
                        when {
                            isCloseEnoughToSelect(scaleHandlePosition, position, lowAccuracy = true) ->
                                submode = SubMode.Scale(rect.center)
                            isCloseEnoughToSelect(rotateHandlePosition, position, lowAccuracy = true) -> {
                                submode = SubMode.Rotate(rect.center)
                            }
                        }
                    }
                }
                else -> {}
            }
            if (submode == null) { // try grabbing arc midpoint
                for (ix in selection.arcPaths) {
                    val concreteArcPath = objects[ix] as? ConcreteArcPath ?: continue
                    for (arcIndex in concreteArcPath.arcs.indices) {
                        concreteArcPath.arcs[arcIndex].freeMidpoint?.let { midpoint ->
                            if (isCloseEnoughToSelect(midpoint.toOffset(), position, lowAccuracy = true)) {
                                submode = SubMode.GrabbedArcMidpoint(ix, arcIndex)
                            }
                        }
                    }
                }
            }
            when (mode) {
                SelectionMode.Drag -> if (submode == null) { // select point > circle > arcpath
                    val selectedPointIndex = getPreferablyFreePointsAround(position).firstOrNull()
                    selection = if (selectedPointIndex != null) {
                        Selection(gCircles = listOf(selectedPointIndex))
                    } else {
                        val selectedCircleIndex = getPreferablyFreeCirclesAround(position).firstOrNull()
                        if (selectedCircleIndex != null) {
                            Selection(gCircles = listOf(selectedCircleIndex))
                        } else {
                            val selectedArcPathIndex = getArcPathsAround(position).firstOrNull()
                            if (selectedArcPathIndex != null) {
                                Selection(arcPaths = listOf(selectedArcPathIndex))
                            } else {
                                // we keep the previous selection in case we want to drag it
                                // but it can still be discarded in :onTap
                                selection
                            }
                        }
                    }
                }
                SelectionMode.Multiselect -> when (submode) {
                    is SubMode.RectangularSelect -> { // TODO: rect select for arc-paths
                        val (corner1, corner2) = submode as SubMode.RectangularSelect
                        submode = if (corner1 == null) {
                            SubMode.RectangularSelect(absolute(position))
                        } else if (corner2 == null) {
                            SubMode.RectangularSelect(corner1, absolute(position))
                        } else {
                            SubMode.RectangularSelect(absolute(position))
                        }
                    }
                    is SubMode.FlowSelect -> { // doesn't work with arc paths
                        val (_, qualifiedRegion) = getRegionAround(position)
                        submode = SubMode.FlowSelect(qualifiedRegion)
                    }
                    else -> {}
                }
                SelectionMode.Region -> when (submode) {
                    is SubMode.FlowFill -> {
                        val (_, qualifiedRegion) = getRegionAround(position)
                        submode = SubMode.FlowFill(qualifiedRegion)
                        val selectedCircles = objectSelection.filter { objects[it] is CircleOrLine }
                        if (restrictRegionsToSelection && selectedCircles.isNotEmpty()) {
                            reselectRegionAt(position, selectedCircles)
                        } else {
                            reselectRegionAt(position)
                        }
                    }
                    else -> {}
                }
                ToolMode.ARC_PATH -> {
                    partialArcPath = partialArcPath?.unFocus()
                    if (submode == null) { // we might have grabbed an arc midpoint
                        clearSelection()
                        downArcPathPoint(position)
                    }
                }
                is ToolMode -> if (partialArgList?.isFull != true)
                    downToolArg(position)
                else -> {}
            }
        }
        // should work independent of circle visibility
        when (val sm = submode) {
            is SubMode.RotateStereographicSphere ->
                submode = sm.copy(
                    grabbedTarget = absolute(position),
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
                (inInterpolationMode entails (argList.currentArg?.type !is Arg.Type.Point))
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
                    getCirclesAround(selectableObjects, visiblePosition).firstOrNull()?.let { ix ->
                        val newArg = Arg.IndexOf(ix, objects[ix] as GCircle)
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
                    val newArg = realizePointSnap(snap).toArgPoint()
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
                val selectedPointIndex = getPointsAround(objects, visiblePosition).firstOrNull()
                if (selectedPointIndex == null) {
                    val selectedCircleIndex = getCirclesAround(objects, visiblePosition).firstOrNull()
                    if (selectedCircleIndex == null) {
                        val selectedArcPathIndex = getArcPathsAround(visiblePosition).firstOrNull()
                        if (selectedArcPathIndex != null) {
                            val newArg = Arg.Indices(listOf(selectedArcPathIndex))
                            if (argList.validateNewArg(newArg)) {
                                partialArgList = argList.addArg(newArg, confirmThisArg = true)
                            }
                            found = true
                        }
                    } else {
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
                Arg.InfinitePoint in nextArgType.possibleTypes
            } == true
        )
        val infinityIndex = objectModel.getInfinityIndex() ?: createNewFreePoint(Point.CONFORMAL_INFINITY)
        val newArg =
            if (Arg.Indices in argList.nextArgType.possibleTypes)
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
        selection.gCircles.singleOrNull()?.let { ix ->
            val expr = exprOf(ix)
            if (expr == null) {
                objectModel.setDisplayObjectWithConsequences(ix, Point.CONFORMAL_INFINITY)
            } else if (expr is Expr.Incidence && objects[expr.carrier] is Line) {
                objectModel.changeExpr(
                    ix,
                    expr.copy(parameters =
                        expr.parameters.copy(order = Line.ORDER_OF_CONFORMAL_INFINITY)
                    )
                )
            }
            recordHistory()
        }
    }

    private fun downArcPathPoint(visiblePosition: Offset) {
        val snap = snapped(absolute(visiblePosition))
        val arcPath = partialArcPath
        partialArcPath = if (arcPath == null) {
            PartialArcPath(
                vertices = listOf(PartialArcPath.Vertex(snap)),
                focus = PartialArcPath.Focus.Vertex(0),
            )
        } else {
            val vertexIndex = arcPath.vertices.indexOfFirst { vertex ->
                isCloseEnoughToSelect(vertex.point.toOffset(), visiblePosition)
            }
            if (vertexIndex != -1) {
                arcPath.copy(focus = PartialArcPath.Focus.Vertex(vertexIndex))
            } else {
                val arcIndex = arcPath.arcs.indexOfFirst { arc ->
                    isCloseEnoughToSelect(arc.middlePoint.toOffset(), visiblePosition)
                }
                if (arcIndex != -1) {
                    arcPath.copy(focus = PartialArcPath.Focus.MidPoint(arcIndex))
                } else {
                    arcPath.addNewVertexAndGrabIt(PartialArcPath.Vertex(snap))
                }
            }
        }
    }

    private fun tapDuringDrag(visiblePosition: Offset) {
        // when multiple close candidates, show choice list
        if (SHOW_SELECTION_CHOICES) {
            val selectablePoints = getPreferablyFreePointsAround(visiblePosition)
            val selectableCircles = getPreferablyFreeCirclesAround(visiblePosition)
            val selectableArcPaths = getArcPathsAround(visiblePosition)
            when {
                selectablePoints.isNotEmpty() ->
                    selection = Selection(gCircles = selectablePoints.take(1))
                selectableCircles.isNotEmpty() -> {
                    selection = Selection(gCircles = selectableCircles.take(1))
                    highlightSelectionParents()
                }
                selectableArcPaths.isNotEmpty() ->
                    selection = Selection(arcPaths = selectableArcPaths.take(1))
                else ->
                    clearSelection()
            }
            // selecting a point over circles/paths is unambiguous
            val selectionIsAmbiguous = selectablePoints.size != 1 &&
                selectablePoints.size + selectableCircles.size + selectableArcPaths.size > 1
            if (selectionIsAmbiguous) {
                submode = SubMode.SelectionChoices(
                    (selectablePoints + selectableCircles).mapNotNull { ix ->
                        val obj = (objects[ix] as? GCircle) ?: return@mapNotNull null
                        val color = objectModel.borderColors[ix]
                        SubMode.SelectionChoices.Choice(
                            index = ix, objectOrArcPath = obj,
                            borderColor = color, fillColor = color,
                        )
                    } + selectableArcPaths.map { ix ->
                        val borderColor = objectModel.borderColors[ix]
                        val fillColor = objectModel.fillColors[ix]
                        SubMode.SelectionChoices.Choice(
                            index = ix, objectOrArcPath = null,
                            borderColor = borderColor, fillColor = fillColor,
                        )
                    }
                )
            }
        } else { // no selection choices
            val selectedPointIndex = getPreferablyFreePointsAround(visiblePosition).firstOrNull()
            if (selectedPointIndex != null) {
                selection = Selection(gCircles = listOf(selectedPointIndex))
            } else {
                val selectedCircleIndex = getPreferablyFreeCirclesAround(visiblePosition).firstOrNull()
                if (selectedCircleIndex != null) {
                    selection = Selection(gCircles = listOf(selectedCircleIndex))
                    highlightSelectionParents()
                } else {
                    val selectedArcPathIndex = getArcPathsAround(visiblePosition).firstOrNull()
                    if (selectedArcPathIndex != null)
                        selection = Selection(arcPaths = listOf(selectedArcPathIndex))
                    else
                        clearSelection()

                }
            }
        }
    }

    private fun tapDuringMultiselect(visiblePosition: Offset) {
        val selectedPointIndex = getPreferablyFreePointsAround(visiblePosition).firstOrNull()
        if (selectedPointIndex != null) {
            if (selectedPointIndex in selection.gCircles) {
                selection = selection.copy(gCircles = selection.gCircles - selectedPointIndex)
            } else {
                selection = selection.copy(gCircles = selection.gCircles + selectedPointIndex)
                highlightSelectionParents()
            }
        } else {
            val selectedCircleIndex = getPreferablyFreeCirclesAround(visiblePosition).firstOrNull()
            if (selectedCircleIndex != null) {
                if (selectedCircleIndex in selection.gCircles) {
                    selection = selection.copy(gCircles = selection.gCircles - selectedCircleIndex)
                } else {
                    selection = selection.copy(gCircles = selection.gCircles + selectedCircleIndex)
                    highlightSelectionParents()
                }
            } else {
                val selectedArcPathIndex = getArcPathsAround(visiblePosition).firstOrNull()
                if (selectedArcPathIndex != null) {
                    selection = selection.copy(
                        arcPaths = selection.arcPaths.xor(selectedArcPathIndex)
                    )
                } else { // try to select bounding circles of the selected region
                    val (region, region0) = getRegionAround(visiblePosition)
                    // TODO: also try selecting insides of closed arc-paths
                    if (region0.insides.isEmpty()) { // if we clicked outside of everything, toggle select all
                        toggleSelectAll()
                        if (!showPhantomObjects) {
                            selection = selection.copy(
                                gCircles = selection.gCircles.filter { it !in phantoms },
                            )
                        }
                    } else {
                        val selectedCircles = objectSelection.filter { objects[it] is CircleOrLine }
                        val largestInnerRegion = regions
                            .filter { region isObviouslyInside it || region0 isObviouslyInside it }
                            .maxByOrNull { it.insides.size + it.outsides.size }
                        if (largestInnerRegion == null) { // select bound of a non-existent region
                            println("bounds of $region")
                            val bounds: Set<Ix> = region.insides + region.outsides
                            if (bounds != selectedCircles.toSet()) {
                                selection = Selection(gCircles = bounds.toList())
                                highlightSelectionParents()
                            } else {
                                clearSelection()
                            }
                        } else {
                            println("existing bound of $largestInnerRegion")
                            val bounds: Set<Ix> = largestInnerRegion.insides + largestInnerRegion.outsides
                            if (bounds != selectedCircles.toSet()) {
                                selection = Selection(gCircles = bounds.toList())
                                highlightSelectionParents()
                            } else {
                                clearSelection()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun tapDuringRegions(visiblePosition: Offset) {
        val selectedCircles = objectSelection.filter { objects[it] is CircleOrLine }
        if (restrictRegionsToSelection && selectedCircles.isNotEmpty()) {
            reselectRegionAt(visiblePosition, selectedCircles)
        } else {
            reselectRegionAt(visiblePosition)
        }
        recordHistory()
    }

    /**
     * Pointer input callback sequences:
     * Down -> Up -> Tap OR
     * Down -> Up -> Down! -> Tap -> Up (double tap)
     * @param[position] _visible_ position of the tap
     */
    fun onTap(position: Offset, pointerCount: Int) {
        // 2-finger tap for undo (works only on Android afaik)
        if (TWO_FINGER_TAP_FOR_UNDO && pointerCount == 2) {
            if (undoIsEnabled.value)
                undo()
        } else if (showCircles) { // select circle(s)/region
            when (mode) {
                SelectionMode.Drag ->
                    tapDuringDrag(visiblePosition = position)
                SelectionMode.Multiselect ->
                    tapDuringMultiselect(visiblePosition = position)
                SelectionMode.Region -> {
                    when (submode) {
                        is SubMode.FlowFill -> {} // see :onDown
                        else ->
                            tapDuringRegions(visiblePosition = position)
                    }
                }
                ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> {}
                ToolMode.ARC_PATH -> {
                    val pArcPath = partialArcPath
                    if (pArcPath != null && !pArcPath.isClosed && pArcPath.vertices.size >= 2 &&
                        isCloseEnoughToSelect(
                            pArcPath.vertices.first().point.toOffset(),
                            position
                        )
                    ) {
                        partialArcPath = pArcPath.connectLastToFirst()
                    }
                }
                else -> {}
            }
        }
    }

    fun selectFromChoices(indexAmongChoices: Int?) {
        (submode as? SubMode.SelectionChoices)?.let { sm ->
            if (indexAmongChoices != null && indexAmongChoices != 0) { // index=0 is already selected
                val newChoice = sm.choices[indexAmongChoices]
                selection = when (newChoice.objectOrArcPath) {
                    null ->
                        Selection(arcPaths = listOf(newChoice.index))
                    else ->
                        Selection(gCircles = listOf(newChoice.index))
                }
            }
        }
        submode = null
    }

    private fun scaleSingleCircle(ix: Ix, absoluteCentroid: Offset, zoom: Float, sm: SubMode.Scale) {
        val circle = objects[ix] as? CircleOrLine
        if (circle is Circle) {
            val center = sm.center
            val r = (absoluteCentroid - center).getDistance()
            transformWhatWeCan(listOf(ix), focus = center, zoom = (r/circle.radius).toFloat())
        } else if (circle is Line) {
            val center = circle.project(absoluteCentroid)
            transformWhatWeCan(listOf(ix), focus = center, zoom = zoom)
        }
    }

    private fun rotateSingleCircle(ix: Ix, absoluteCentroid: Offset, pan: Offset, sm: SubMode.Rotate) {
        val center = sm.center
        val centerToCurrent = absoluteCentroid - center
        val centerToPreviousHandle = centerToCurrent - pan
        val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
        val newAngle = sm.angle + angle
        val snappedAngle =
            if (ENABLE_ANGLE_SNAPPING) Snapping.snapAngle(newAngle)
            else newAngle
        val angle1 = (snappedAngle - sm.snappedAngle).toFloat()
        transformWhatWeCan(listOf(ix), focus = center, rotationAngle = angle1)
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
    }

    private fun scaleSeveralCircles(targets: List<Ix>, pan: Offset) {
        calculateSelectionRect()?.let { rect ->
            val scaleHandlePosition = rect.topRight
            val center = rect.center
            val centerToHandle = scaleHandlePosition - center
            val centerToCurrent = centerToHandle + pan
            val scaleFactor = centerToCurrent.getDistance()/centerToHandle.getDistance()
            transformWhatWeCan(targets, focus = center, zoom = scaleFactor)
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
        transformWhatWeCan(selectedIndices, focus = sm.center, zoom = scaleFactor)
        submode = sm.copy(sliderPercentage = newSliderPercentage)
    }

    fun finishScalingViaSlider() {
        submode = null
        history.newContinuousChange(null)
        recordHistory()
    }

    fun startHandleRotation(center: Offset) {
        submode = SubMode.Rotate(computeAbsoluteCenter() ?: Offset.Zero)
    }

    fun finishHandleRotation() {
        submode = null
        recordHistory()
    }

    fun rotateViaHandle(newRotationAngle: Float) {
        when (val sm = submode) {
            is SubMode.Rotate -> {
                val newAngle = newRotationAngle.toDouble()
                val snappedAngle =
                    if (ENABLE_ANGLE_SNAPPING) Snapping.snapAngle(newAngle)
                    else newAngle
                val dAngle = (snappedAngle - sm.snappedAngle).toFloat()
                transformWhatWeCan(selectedIndices, focus = sm.center, rotationAngle = dAngle)
                submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
            }
            else -> {}
        }
    }

    private fun rotateSeveralCircles(targets: List<Ix>, absoluteCentroid: Offset, pan: Offset, sm: SubMode.Rotate) {
        val center = sm.center
        val centerToCurrent = absoluteCentroid - center
        val centerToPreviousHandle = centerToCurrent - pan
        val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
        val newAngle = sm.angle + angle
        val snappedAngle =
            if (ENABLE_ANGLE_SNAPPING) Snapping.snapAngle(newAngle)
            else newAngle
        val angle1 = (snappedAngle - sm.snappedAngle).toFloat()
        transformWhatWeCan(targets, focus = sm.center, rotationAngle = angle1)
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
    }

    // dragging circle: move + scale radius & rotate [line]
    private fun dragCircle(
        absoluteCentroid: Offset,
        translation: Offset, zoom: Float, rotationAngle: Float
    ) {
        val selectedIndex = objectSelection.single()
        if (ENABLE_TANGENT_SNAPPING) {
            // TODO: snap to arc-path arcs
            val circle = objects[selectedIndex] as CircleOrLine
            val result0 = circle.transformed(translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
                as CircleOrLine
            val snapDistance = tapRadius.toDouble()/TAP_RADIUS_TO_TANGENTIAL_SNAP_DISTANCE_FACTOR
            val excludedIndices =
                setOf(selectedIndex) +
                (if (showPhantomObjects) emptySet() else phantoms) +
                expressions.getAllChildren(selectedIndex) +
                expressions.getAllParents(listOf(selectedIndex))
            val center = computeAbsoluteCenter()
            val absoluteVisibilityRect =
                if (center != null && canvasSize != IntSize.Zero)
                    Rect(
                        center.x - canvasSize.width/2f, center.y - canvasSize.height/2f,
                        center.x + canvasSize.width/2f, center.y + canvasSize.height/2f,
                    )
                else null
            val snap = Snapping.snapCircleToCircles(result0, objects,
                snapDistance = snapDistance,
                visibleRect = absoluteVisibilityRect,
                excludedIndices = excludedIndices,
            )
            val delta = result0 translationDelta snap.result
            transformWhatWeCan(listOf(selectedIndex), translation = translation + delta, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
        } else {
            transformWhatWeCan(listOf(selectedIndex), translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
        }
    }

    private fun dragPoint(
        absoluteCentroid: Offset,
        translation: Offset
    ) {
        val ix = selection.gCircles.first()
        val point = objects[ix] as Point
        if (point.isInfinite)
            return
        when (val expr = exprOf(ix)) {
            is Expr.Incidence -> {
                slidePointAcrossCarrier(
                    pointIndex = ix,
                    carrierIndex = expr.carrier,
                    absolutePointerPosition = absoluteCentroid
                )
            }
            is Expr.ArcPathIncidence -> {
                slidePointAcrossArcPath(
                    pointIndex = ix,
                    arcPathIndex = expr.arcPath,
                    absolutePointerPosition = absoluteCentroid,
                )
            }
            else -> {
                val childCircles = expressions.getAllChildren(ix)
                    .filter { objects[it] is CircleOrLine }
                    .toSet()
                // when we are dragging intersection of 2 free circles with IoC1 we don't want it to snap to them
                val parents = expressions.getAllParents(listOf(ix))
                val childArcPaths = expressions.children[ix]
                    ?.filter { objects[it] is ConcreteArcPath }
                    ?.toSet() ?: emptySet()
                // NOTE: snap-exclusion calculation when dragging a point seems excessive tbh
                val newPoint = snapped(
                    absoluteCentroid,
                    excludePoints = true,
                    excludedCircles = childCircles + parents,
                    excludedArcPaths = childArcPaths,
                ).result
                val actualTranslation = newPoint.toOffset() - point.toOffset()
                transformWhatWeCan(listOf(ix), translation = actualTranslation)
            }
        }
    }

    // special case that is not handled by transformWhatWeCan()
    // MAYBE: instead transform then snap/project onto carrier and transform by snap-delta again
    private fun slidePointAcrossCarrier(
        pointIndex: Ix,
        carrierIndex: Ix,
        absolutePointerPosition: Offset,
    ) {
        val carrier = objectModel.downscaledObjects[carrierIndex] as? CircleOrLine ?: return
        val point = Point.fromOffset(absolutePointerPosition).downscale()
        val newPoint = carrier.project(point)
        val order = carrier.point2order(newPoint)
        val newExpr = Expr.Incidence(IncidenceParameters(order), carrierIndex)
        val changedIndices = objectModel.changeExpr(pointIndex, newExpr).toSet()
        history.accumulateChangedLocations(
            objectIndices = changedIndices,
            expressionIndices = setOf(pointIndex),
        )
    }

    private fun slidePointAcrossArcPath(
        pointIndex: Ix,
        arcPathIndex: Ix,
        absolutePointerPosition: Offset,
    ) {
        val carrier = objectModel.downscaledObjects[arcPathIndex] as? ConcreteArcPath ?: return
        val point = Point.fromOffset(absolutePointerPosition).downscale()
        val (arcIndex, newPoint, arcPercentage) = carrier.project(point)
        val newExpr = Expr.ArcPathIncidence(
            ArcPathIncidenceParameters(arcIndex, arcPercentage),
            arcPathIndex,
        )
        val changedIndices = objectModel.changeExpr(pointIndex, newExpr).toSet()
        history.accumulateChangedLocations(
            objectIndices = changedIndices,
            expressionIndices = setOf(pointIndex),
        )
    }

    private fun dragArcPaths(
        absoluteCentroid: Offset,
        translation: Offset, zoom: Float, rotationAngle: Float
    ) {
        val targets = selection.arcPaths
            .flatMap { objectModel.getArcPath(it)?.dependencies ?: emptySet() }
            .filter { objects[it] is Point }
            .distinct()
        transformWhatWeCan(targets, translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
    }

    private fun dragSelection(
        absoluteCentroid: Offset,
        translation: Offset,
        zoom: Float,
        rotationAngle: Float,
    ) {
        val targets = selection.gCircles.filter {
            val obj = objects[it]
            obj is CircleOrLine || obj is Point
        }.plus(
            selection.arcPaths
                .flatMap { objectModel.getArcPath(it)?.dependencies ?: emptySet() }
                .filter { objects[it] is Point }
        ).distinct()
        transformWhatWeCan(targets, translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
    }

    private fun dragGrabbedArcMidpoint(
        absoluteCentroid: Offset,
        sm: SubMode.GrabbedArcMidpoint,
    ) {
        val arcPath = objectModel.getArcPath(sm.arcPathIndex) ?: return
        val changedIndices = objectModel.modifyArcPath(
            sm.arcPathIndex,
            arcPath.moveArcMidpoint(
               objects,  sm.arcIndex, Point.fromOffset(absoluteCentroid)
            )
        ).toSet()
        objectModel.invalidatePositions()
        history.accumulateChangedLocations(
            objectIndices = changedIndices,
            expressionIndices = setOf(sm.arcPathIndex),
        )
    }

    // NOTE: polar lines and line-by-2 transform weirdly:
    //  it becomes circle during st-rot, but afterwards when
    //  its carrier is moved it becomes line again
    private fun stereographicallyRotateEverything(
        absolutePointerPosition: Offset,
        sm: SubMode.RotateStereographicSphere,
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
            // inlined computeBiInversion for efficiency
            val engine1 = biEngine.first.downscale()
            val engine2 = biEngine.second.downscale()
            val e1 = GeneralizedCircle.fromGCircle(engine1)
            val e2 = GeneralizedCircle.fromGCircle(engine2)
            val bivector0 = Rotor.fromPencil(e1, e2)
            val bivector = bivector0 * 0.5
            val rotor = bivector.exp() // alternatively bivector0.exp() * log(progress)
            for (ix in objectModel.downscaledObjects.indices) {
                val o = objectModel.downscaledObjects[ix] as? GCircle
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
            objectModel.invalidatePositions()
        }
    }

    /**
     * Wrapper around [ConformalObjectModel.transform] that adjusts [targets] based on [INVERSION_OF_CONTROL].
     *
     * [ConformalObjectModel.transform] applies [translation];scaling;rotation
     * to [targets] (that are all assumed free).
     *
     * Scaling and rotation are w.r.t. fixed [focus] by the factor of
     * [zoom] and by [rotationAngle] degrees. If [focus] is [Offset.Unspecified] for
     * each circle choose its center, for each point -- itself, for each line -- screen center
     * projected onto it
     */
    private fun transformWhatWeCan(
        targets: List<Ix>,
        translation: Offset = Offset.Zero,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
        continuousChange: ContinuousChange? = null,
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
            val changedIndices =
                objectModel.transform(actualTargets, translation, focus, zoom, rotationAngle)
            history.accumulateChangedLocations(
                objectIndices = changedIndices,
                // zoom can change point-line incidence
                expressionIndices = changedIndices,
                continuousChange = continuousChange,
            )
        }
    }

    private fun updateRectangleSelect(absolutePosition: Offset, sm: SubMode.RectangularSelect) {
        val corner1 = sm.corner1
        val rect = Rect.fromCorners(corner1 ?: absolutePosition, absolutePosition)
        val selectables = objects.mapIndexed { ix, o ->
            if (o is GCircle && (showPhantomObjects || ix !in phantoms)) o
            else null
        }
        selection = Selection( // TODO: select arc-paths too
            gCircles = RectangleCollider.selectWithRectangle(selectables, rect)
        )
        submode = SubMode.RectangularSelect(corner1, absolutePosition)
    }

    private fun updateFlowSelect(visiblePosition: Offset, sm: SubMode.FlowSelect) {
        val qualifiedRegion = sm.lastQualifiedRegion
        val (_, newQualifiedRegion) = getRegionAround(visiblePosition)
        if (qualifiedRegion == null) {
            submode = SubMode.FlowSelect(newQualifiedRegion)
        } else {
            val diff =
                (qualifiedRegion.insides - newQualifiedRegion.insides) union
                (newQualifiedRegion.insides - qualifiedRegion.insides) union
                (qualifiedRegion.outsides - newQualifiedRegion.outsides) union
                (newQualifiedRegion.outsides - qualifiedRegion.outsides)
            selection = Selection(gCircles =
                selection.gCircles +
                    diff.filter { it !in objectSelection && (showPhantomObjects || it !in phantoms) }
            )
        }
    }

    private fun updateFlowFill(visiblePosition: Offset, selectedCircles: List<Ix>, sm: SubMode.FlowFill) {
        val qualifiedRegion = sm.lastQualifiedRegion
        val (_, newQualifiedRegion) = getRegionAround(visiblePosition)
        if (qualifiedRegion == null) {
            submode = SubMode.FlowFill(newQualifiedRegion)
        } else if (qualifiedRegion != newQualifiedRegion) {
            submode = SubMode.FlowFill(newQualifiedRegion)
            if (restrictRegionsToSelection && selectedCircles.isNotEmpty()) {
                reselectRegionAt(visiblePosition, selectedCircles)
            } else {
                reselectRegionAt(visiblePosition)
            }
        }
    }

    private fun updatePartialArcPathFocus(absolutePosition: Offset) {
        val snap = snapped(absolutePosition)
        partialArcPath = partialArcPath?.moveFocus(snap, snapDistance = tapRadius.toDouble())
    }

    /** @return whether a tool arg is actually updated */
    private fun tryUpdatingToolArg(absolutePosition: Offset): Boolean {
        val snap = snapped(absolutePosition)
        val absolutePoint = snap.result
        val argList = partialArgList
        val currentArg = argList?.currentArg
        val currentArgType = argList?.currentArgType
        if (mode is ToolMode &&
            currentArgType?.possibleTypes?.any { it is Arg.Type.Point } == true &&
            ((mode == ToolMode.CIRCLE_OR_POINT_INTERPOLATION) entails (currentArg?.type is Arg.Type.Point))
        ) {
            val newArg = when (snap) {
                is PointSnapResult.Eq -> Arg.PointIndex(snap.pointIndex)
                else -> Arg.PointXY(absolutePoint)
            }
            if (argList.validateUpdatedArg(newArg)) {
                partialArgList = argList
                    .updateCurrentArg(newArg, confirmThisArg = false)
                    .copy(lastSnap = snap)
            }
            return true
        }
        return false
    }

    private fun moveAroundCanvas(translation: Offset, zoom: Float, rotationAngle: Float) {
        if (zoom != 1.0f || rotationAngle != 0.0f) {
            val targets = objects.indices.toList()
            val center = computeAbsoluteCenter() ?: Offset.Zero
            val changedIndices =
                objectModel.transform(
                    targets,
                    focus = center,
                    zoom = zoom,
                    rotationAngle = rotationAngle,
                )
            history.accumulateChangedLocations(
                objectIndices = changedIndices,
                // zoom can change point-line incidence
                expressionIndices = changedIndices,
                center = true,
            )
        }
        this.translation += translation // navigate canvas
        objectModel.pathCache.invalidateAll() // sadly have to do this cuz we use visibleRect in path construction
    }

    // MAYBE: handle key arrows as panning
    fun onPanZoomRotate(centroid: Offset, pan: Offset, zoom: Float, rotationAngle: Float) {
        movementAfterDown = true
        /** absolute cursor/pointer position/centroid */
        val absoluteCentroid = absolute(centroid)
        val selectedCircles = objectSelection.filter { objects[it] is CircleOrLineOrImaginaryCircle }
        val selectedPoints = objectSelection.filter { objects[it] is Point }
        when (val sm = submode) {
            is SubMode.Scale -> when (handleConfig) {
                HandleConfig.SINGLE_CIRCLE ->
                    scaleSingleCircle(ix = selection.gCircles.single(), absoluteCentroid = absoluteCentroid, zoom = zoom, sm = sm)
                HandleConfig.SEVERAL_OBJECTS ->
                    scaleSeveralCircles(targets = selectedIndices, pan = pan)
                null -> {}
            }
            is SubMode.Rotate -> when (handleConfig) {
                HandleConfig.SINGLE_CIRCLE ->
                    rotateSingleCircle(ix = selection.gCircles.single(), pan = pan, absoluteCentroid = absoluteCentroid, sm = sm)
                HandleConfig.SEVERAL_OBJECTS ->
                    rotateSeveralCircles(targets = selectedIndices, absoluteCentroid = absoluteCentroid, pan = pan, sm = sm)
                null -> {}
            }
            is SubMode.GrabbedArcMidpoint ->
                dragGrabbedArcMidpoint(absoluteCentroid = absoluteCentroid, sm = sm)
            is SubMode.RectangularSelect ->
                updateRectangleSelect(absolutePosition = absoluteCentroid, sm = sm)
            is SubMode.FlowSelect ->
                updateFlowSelect(visiblePosition = centroid, sm = sm)
            is SubMode.FlowFill ->
                updateFlowFill(visiblePosition = centroid, selectedCircles = selectedCircles, sm = sm)
            is SubMode.RotateStereographicSphere ->
                stereographicallyRotateEverything(absolutePointerPosition = absoluteCentroid, sm = sm)
            null -> when (mode) {
                SelectionMode.Drag if selectedCircles.isNotEmpty() && showCircles ->
                    dragCircle(absoluteCentroid = absoluteCentroid, translation = pan, zoom = zoom, rotationAngle = rotationAngle)
                SelectionMode.Drag if selectedPoints.isNotEmpty() && showCircles ->
                    dragPoint(absoluteCentroid = absoluteCentroid, translation = pan)
                SelectionMode.Drag if selection.arcPaths.isNotEmpty() ->
                    dragArcPaths(absoluteCentroid = absoluteCentroid, translation = pan, zoom = zoom, rotationAngle = rotationAngle)
                SelectionMode.Multiselect if (
                    selectedCircles.isNotEmpty() && showCircles || selectedPoints.isNotEmpty() || selection.arcPaths.isNotEmpty()
                ) ->
                    dragSelection(absoluteCentroid = absoluteCentroid, translation = pan, zoom = zoom, rotationAngle = rotationAngle)
                ToolMode.ARC_PATH ->
                    updatePartialArcPathFocus(absoluteCentroid)
                else -> {
                    val toolArgIsUpdated = tryUpdatingToolArg(absoluteCentroid)
                    if (!toolArgIsUpdated) {
                        moveAroundCanvas(translation = pan, zoom = zoom, rotationAngle = rotationAngle)
                    }
                }
            }
            else -> {}
        }
    }

    private fun upPartialArcPath(visiblePosition: Offset?) {
        var pArcPath = partialArcPath?.realignGrabbedMidpoint()
        val focus = pArcPath?.focus
        // attempt fusing focused vertex to the next or previous
        if (pArcPath != null && visiblePosition != null && focus is PartialArcPath.Focus.Vertex) {
            val absolutePosition = absolute(visiblePosition)
            val closeVertices = pArcPath.vertices.indices.minus(focus.vertexIndex)
                .filter { i ->
                    absolutePosition.minus(pArcPath.vertices[i].point.toOffset())
                        .getDistanceSquared() <= tapRadius2
                }.toSet()
            val nextVertexIndex = (focus.vertexIndex + 1).mod(pArcPath.vertices.size)
            val previousVertexIndex = (focus.vertexIndex - 1).mod(pArcPath.vertices.size)
            when {
                nextVertexIndex in closeVertices -> {
                    pArcPath = pArcPath.fuseSubsequentVertices(focus.vertexIndex)
                }
                previousVertexIndex in closeVertices -> {
                    pArcPath = pArcPath.fuseSubsequentVertices(previousVertexIndex)
                }
                else -> {
                    // we can also snap 2 non-neighboring vertices, but it's prob not a good idea
                }
            }
        }
        partialArcPath = pArcPath
    }

    private fun upToolMode(visiblePosition: Offset?) {
        if (submode == null) {
            var argList = partialArgList
            // we only confirm args in 0nUp, they are created in 0nDown etc.
            val newArg = when (argList?.currentArg) {
                is Arg.Point -> visiblePosition?.let {
                    val args = argList.args
                    val snap = snapped(absolute(visiblePosition))
                    // we cant realize it here since for fast circles the first point already has been
                    // realized in 0nDown and we don't know yet if we moved far enough from it to
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
                            realizePointSnap(snap).toArgPoint()
                        }
                    } else {
                        realizePointSnap(snap).toArgPoint()
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
                argList.updateCurrentArg(newArg, confirmThisArg = true)
            if (partialArgList?.isFull == true) {
                completeToolMode()
            }
        }
    }

    private fun upRectangularSelect(visiblePosition: Offset?) {
        val (corner1, corner2) = submode as SubMode.RectangularSelect
        if (visiblePosition != null && corner1 != null && corner2 != null) {
            val newCorner2 = absolute(visiblePosition)
            val rect = Rect.fromCorners(corner1, newCorner2)
            val selectables = objects.mapIndexed { ix, o ->
                if (showPhantomObjects || ix !in phantoms) o else null
            }
            val rectSelection = RectangleCollider.selectWithRectangle(selectables, rect)
                .also {
                    println("rectangle selection -> $it")
                }
            selection = Selection(gCircles = rectSelection)
            submode = SubMode.RectangularSelect(corner1, corner2)
        }
    }

    /** @param[position] `null` if cancelled/OOB */
    fun onUp(position: Offset?) {
        cancelSelectionAsToolArgPrompt()
        // history is recorded at the end of :onUp
        when (mode) {
            SelectionMode.Drag -> {
                // MAYBE: try to re-attach free points / new pinning/sticky mode
                if (movementAfterDown && submode == null && selection.gCircles.none { isFree(it) })
                    highlightSelectionParents()
            }
            SelectionMode.Multiselect -> {
                when (submode) {
                    is SubMode.RectangularSelect ->
                        upRectangularSelect(visiblePosition = position)
                    is SubMode.FlowSelect -> { // haxx
                        println("flow-select -> $objectSelection")
                        toolbarState = toolbarState.copy(activeTool = Tool.Multiselect)
                    }
                    null -> if (movementAfterDown && selection.gCircles.none { isFree(it) })
                        highlightSelectionParents()
                    else -> {}
                }
            }
            ViewMode.StereographicRotation -> {
                // MAYBE: normalize line-only-output expressions (e.g. polar line)
                // fixes incident points for line->circle and circle->line transitions
                expressions.adjustIncidentPointExpressions()
                history.accumulateChangedLocations(
                    expressionIndices = objects.indices.toSet(),
                )
            }
            ToolMode.ARC_PATH ->
                upPartialArcPath(visiblePosition = position)
            is ToolMode ->
                upToolMode(visiblePosition = position)
            else -> {}
        }
        when (submode) { // history recordings
            is SubMode.FlowFill,
            is SubMode.RotateStereographicSphere,
            is SubMode.Rotate,
            is SubMode.Scale,
            is SubMode.ScaleViaSlider,
            is SubMode.GrabbedArcMidpoint ->
                recordHistory()
            null -> when (mode) {
                SelectionMode.Drag, SelectionMode.Multiselect -> {
                    if (selection.isNotEmpty() && movementAfterDown) {
                        recordHistory()
                    }
                }
                else -> {}
            }
            else -> {}
        }
        when (submode) { // submode cleanup/reset
            is SubMode.Rotate,
            is SubMode.Scale,
            is SubMode.ScaleViaSlider,
            is SubMode.FlowSelect,
            is SubMode.GrabbedArcMidpoint ->
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

    fun queueSnackbarMessage(snackbarMessage: SnackbarMessage, vararg formatArgs: Any) {
        snackbarMessages.tryEmit(snackbarMessage to formatArgs)
//        viewModelScope.launch {
//            snackbarMessages.emit(snackbarMessage)
//        }
    }

    /** Signals locked state to the user with animation & snackbar message */
    private fun highlightSelectionParents() {
        val allParents = selection.indices.flatMap { ix ->
            if (isConstrained(ix)) emptyList() // exclude semi-free incident points
            else expressions.getImmediateParents(ix)
                .minus(selection.indices.toSet())
        }
            .distinct()
            // TODO: highlight parent arc-paths
            // for traj arc-path: custom parent arc-path highlight
            .mapNotNull { objects[it] as? GCircle }
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
                KeyboardAction.SELECT_ALL -> forceSelectAll()
                KeyboardAction.DELETE -> deleteSelection()
                KeyboardAction.PASTE -> duplicateSelection()
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
                KeyboardAction.OPEN -> requestOpenFile()
                KeyboardAction.SAVE -> requestSaveFileAs()
                KeyboardAction.CONFIRM -> confirmCurrentAction()
                KeyboardAction.NEW_DOCUMENT -> newBlank()
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

    fun cancelOngoingActions() {
        when (mode) { // reset mode
            is ToolMode -> {
                // double escape to go to Drag
                if (submode == null && partialArgList?.args?.isNotEmpty() != true && partialArcPath == null) {
                    switchToCategory(Category.Drag)
                } else {
                    if (submode is SubMode.ExprAdjustment<*>) {
                        cancelExprAdjustment()
                        recordHistory()
                    }
                    partialArgList = partialArgList?.copyEmpty()
                    partialArcPath = null
                    submode = null
                }
            }
            ViewMode.StereographicRotation -> {
                // maybe undo the rotation
                switchToCategory(Category.Drag)
            }
            is SelectionMode -> {
                when (val sm = submode) {
                    is SubMode.ExprAdjustment<*> -> {
                        val indices = sm.adjustables.flatMap { it.reservedIndices }.toSet()
                        recordHistory()
                        undo() // contrived way to go to the before-adj savepoint
                    }
                    is SubMode.RectangularSelect,
                    is SubMode.SelectionChoices ->
                        submode = null
                    else -> {
                        when (mode) {
                            SelectionMode.Multiselect -> {
                                if (objectSelection.isNotEmpty()) {
                                    submode = null
                                    clearSelection()
                                } else {
                                    switchToCategory(Category.Drag)
                                }
                            }
                            SelectionMode.Region -> {
                                switchToCategory(Category.Drag)
                            }
                            else -> {
                                submode = null
                                clearSelection()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun realizeArcPathMidpoints(arcPathIndex: Ix): ArcPath {
        val arcPath = objectModel.getArcPath(arcPathIndex)!!
        val arcs = arcPath.arcs.mapIndexed { arcIndex, arc ->
            when (arc) {
                is ArcPath.Arc.By3Points -> arc
                is ArcPath.Arc.By2Points -> {
                    val midpointExpr = Expr.ArcPathArcMidpoint(
                        ArcPathArcMidpointParameters(arcIndex),
                        arcPathIndex,
                    )
                    val existingMidpointIndex = expressions.findExpr(midpointExpr).firstOrNull()
                    val midpointIndex = if (existingMidpointIndex == null) {
                        val midpoint = expressions.addSoloExpr(midpointExpr) as? Point
                        val ix = objectModel.addDownscaledObject(midpoint)
                        objectModel.phantomObjectIndices.add(ix)
                        ix
                    } else existingMidpointIndex
                    ArcPath.Arc.By3Points(middlePointIndex = midpointIndex)
                }
            }
        }
        return when (arcPath) {
            is ArcPath.Closed -> arcPath.copy(arcs = arcs)
            is ArcPath.Open -> arcPath.copy(arcs = arcs)
        }
    }

    private inline fun copyArcPath(
        sourceArcPathIndex: Ix,
        crossinline mkExpr: (pointIndex: Ix) -> Expr.Conformal.OneToOne,
    ) {
        require(objectModel.getArcPath(sourceArcPathIndex) is ArcPath)
        val sourceArcPath = realizeArcPathMidpoints(sourceArcPathIndex)
        val copiedVertices = sourceArcPath.vertices.map { vertexIndex ->
            val expr = mkExpr(vertexIndex)
            val result = expressions.addSoloExpr(expr) as? Point
            val newIndex = objectModel.addDownscaledObject(result)
            copyStyle(vertexIndex, newIndex)
            newIndex
        }
        val copiedArcs = sourceArcPath.arcs.map { arc ->
            when (arc) {
                is ArcPath.Arc.By3Points -> {
                    val sourceIndex = arc.middlePointIndex
                    val expr = mkExpr(sourceIndex)
                    val result = expressions.addSoloExpr(expr) as? Point
                    val newIndex = objectModel.addDownscaledObject(result)
                    copyStyle(sourceIndex, newIndex)
                    ArcPath.Arc.By3Points(middlePointIndex = newIndex)
                }
                is ArcPath.Arc.By2Points ->
                    never("arc-path $sourceArcPath should have no 2-point arcs after realizeArcPathMidpoints")
            }
        }
        val concreteArcPath = expressions.addSoloExpr(
            when (sourceArcPath) {
                is ArcPath.Closed -> ArcPath.Closed(vertices = copiedVertices, arcs = copiedArcs)
                is ArcPath.Open -> ArcPath.Open(vertices = copiedVertices, arcs = copiedArcs)
            }
        )
        val copiedArcPathIndex = objectModel.addDownscaledObject(concreteArcPath)
        copyStyle(sourceArcPathIndex, copiedArcPathIndex)
    }

    /**
     * @return (adjustable trajectory of copied arc-paths, point adjustables)
     */
    private inline fun <reified EXPR : Expr.Conformal.OneToMany> copyArcPathToMany(
        sourceArcPathIndex: Ix,
        crossinline mkExpr: (pointIndex: Ix) -> EXPR,
    ): Pair<AdjustableExpr<ArcPath>, List<AdjustableExpr<EXPR>>> {
        require(objectModel.getArcPath(sourceArcPathIndex) is ArcPath)
        val sourceArcPath = realizeArcPathMidpoints(sourceArcPathIndex)
        val adjustables = mutableListOf<AdjustableExpr<EXPR>>()
        /** trajectory stage index -> arc-path vertices on this stage */
        val trajectoryOfVertices = sourceArcPath.vertices.map { vertexIndex ->
            // vertexIndex -> trajectory of vertices
            val expr = mkExpr(vertexIndex)
            val result = expressions.addMultiExpr(expr)
            val newIndices = objectModel.addDownscaledObjects(result).toList()
            for (newIndex in newIndices) {
                copyStyle(vertexIndex, newIndex)
            }
            adjustables.add(AdjustableExpr(expr,
                vertexIndex,
                newIndices, newIndices
            ))
            newIndices
        }.transpose()
        /** trajectory stage index -> arc-path arcs on this stage */
        val trajectoryOfArcs = sourceArcPath.arcs.map { arc ->
            // arcIndex -> trajectory of arcs
            when (arc) {
                is ArcPath.Arc.By3Points -> {
                    val sourceIndex = arc.middlePointIndex
                    val expr = mkExpr(sourceIndex)
                    val result = expressions.addMultiExpr(expr)
                    val newIndices = objectModel.addDownscaledObjects(result).toList()
                    for (newIndex in newIndices) {
                        copyStyle(sourceIndex, newIndex)
                    }
                    adjustables.add(AdjustableExpr(expr,
                        sourceIndex,
                        newIndices, newIndices
                    ))
                    newIndices.map { newIndex ->
                        ArcPath.Arc.By3Points(middlePointIndex = newIndex)
                    }
                }
                is ArcPath.Arc.By2Points ->
                    never("arc-path $sourceArcPath should have no 2-point arcs after realizeArcPathMidpoints")
            }
        }.transpose()
        // trajectory of arc-paths
        val copiedArcPathIndices = trajectoryOfVertices.zip(trajectoryOfArcs) { nullableVertices, nullableArcs ->
            val vertices = nullableVertices.map { it as Ix }
            val arcs = nullableArcs.map { it as ArcPath.Arc }
            val concreteArcPath = expressions.addSoloExpr(
                sourceArcPath.copy(vertices = vertices, arcs = arcs)
            )
            val copiedArcPathIndex = objectModel.addDownscaledObject(concreteArcPath)
            copyStyle(sourceArcPathIndex, copiedArcPathIndex)
            copiedArcPathIndex
        }
        val arcPathAdjustable = AdjustableExpr(
            sourceArcPath.copy( // blueprint arc-path
                vertices = sourceArcPath.vertices.indices.toList(),
                arcs = List(sourceArcPath.arcs.size) { arcIndex ->
                    ArcPath.Arc.By3Points(sourceArcPath.vertices.size + arcIndex)
                }
            ),
            sourceArcPathIndex,
            copiedArcPathIndices, copiedArcPathIndices,
        )
        return Pair(arcPathAdjustable, adjustables)
    }

    private fun completeCircleByCenterAndRadius() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.Point }
        if (!ALWAYS_CREATE_ADDITIONAL_POINTS && args.all { it is Arg.PointXY }) {
            val newCircle = computeCircleByCenterAndRadius(
                center = (args[0] as Arg.PointXY).toPoint().downscale(),
                radiusPoint = (args[1] as Arg.PointXY).toPoint().downscale(),
            )?.upscale()
            createNewGCircle(newCircle)
            expressions.addFree()
        } else {
            val realized = args.map { arg ->
                when (arg) {
                    is Arg.PointIndex -> arg.index
                    is Arg.FixedPoint -> createNewFreePoint(arg.toPoint())
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
        recordHistory()
    }

    private fun completeCircleBy3Points() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.CLIP }
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
                    is Arg.FixedPoint -> createNewFreePoint(it.toPoint())
                }
            }
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleBy3Points(
                    object1 = realized[0],
                    object2 = realized[1],
                    object3 = realized[2],
                ),
            ) as? GCircle
            createNewGCircle(newGCircle?.upscale())
            if (newGCircle is ImaginaryCircle) {
                queueSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
            }
        }
        partialArgList = argList.copyEmpty()
        recordHistory()
    }

    private fun completeCircleByPencilAndPoint() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.CLIP }
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
                    is Arg.FixedPoint -> createNewFreePoint(it.toPoint())
                }
            }
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleByPencilAndPoint(
                    pencilObject1 = realized[0],
                    pencilObject2 = realized[1],
                    perpendicularObject = realized[2],
                ),
            ) as? GCircle
            createNewGCircle(newGCircle?.upscale())
            if (newGCircle is ImaginaryCircle) {
                queueSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
            }
        }
        partialArgList = argList.copyEmpty()
        recordHistory()
    }

    private fun completeLineBy2Points() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.CLIP }
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
                    is Arg.FixedPoint -> createNewFreePoint(it.toPoint())
                }
            }
            val infinityIndex = objectModel.getInfinityIndex() ?: createNewFreePoint(Point.CONFORMAL_INFINITY)
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleBy3Points(
                    object1 = realized[0],
                    object2 = realized[1],
                    object3 = infinityIndex,
                ),
            ) as? GCircle
            createNewGCircle(newGCircle?.upscale())
        }
        partialArgList = argList.copyEmpty()
        recordHistory()
    }

    private fun completePolarityByCircleAndLineOrPoint() {
        val argList = partialArgList ?: return
        val circleArg = argList.args[0] as Arg.CircleIndex
        val lineOrPointArg = argList.args[1] as Arg.LP
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
                    is Arg.FixedPoint -> createNewFreePoint(lineOrPointArg.toPoint())
                }
                Expr.PolarLineByCircleAndPoint(
                    circle = circleArg.index,
                    point = realizedPointIndex,
                )
            }
        }
        val newGCircle = expressions.addSoloExpr(newExpr) as? GCircle
        createNewGCircle(newGCircle?.upscale())
        partialArgList = argList.copyEmpty()
        recordHistory()
    }

    private fun completeCircleInversion() {
        val argList = partialArgList ?: return
        val sources = expressions.sortedByTier(
            (argList.args[0] as Arg.Indices).indices
        )
        val gCircleSources = sources.filter { objects[it] is GCircle }
        val arcPathSources = sources.filter { objects[it] is ConcreteArcPath }
        val invertingCircleIndex = (argList.args[1] as Arg.CLI).index
        val oldSize = objects.size
        val circlesOrLines = mutableListOf<CircleOrLine>()
        for (sourceIndex in gCircleSources) {
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleInversion(sourceIndex, invertingCircleIndex),
            ) as? GCircle
            val newIndex = objectModel.addDownscaledObject(newGCircle)
            copyStyle(sourceIndex, newIndex)
            if (newGCircle is CircleOrLine) {
                circlesOrLines.add(newGCircle.upscale())
            }
        }
        val newIndices1 = oldSize until objects.size
        for (ix in arcPathSources) {
            copyArcPath(ix) { pointIndex ->
                Expr.CircleInversion(pointIndex, invertingCircleIndex)
            }
        }
        val newIndices = oldSize until objects.size
        copyRegions(
            gCircleSources, newIndices1.toList(),
            flipInAndOut = true
        )
        selection = Selection(
            gCircles = newIndices.filter { objects[it] is GCircle },
            arcPaths = newIndices.filter { objects[it] is ConcreteArcPath },
        )
        partialArgList = argList.copyEmpty()
        viewModelScope.launch {
            animations.emit(CircleAnimation.Entrance(circlesOrLines))
        }
        objectModel.invalidate()
        recordHistory()
    }

    private fun startCircleOrPointInterpolationParameterAdjustment() {
        val argList = partialArgList ?: return
        val (startArg, endArg) = argList.args.map { it as Arg.CLIP }
        if (startArg is Arg.CLI && endArg is Arg.CLI) {
            interpolateCircles = true
            val scalarProduct =
                GeneralizedCircle.fromGCircle(objects[startArg.index] as CircleOrLineOrImaginaryCircle) scalarProduct
                GeneralizedCircle.fromGCircle(objects[endArg.index] as CircleOrLineOrImaginaryCircle)
            circlesAreCoDirected = scalarProduct >= 0.0
            val expr = Expr.CircleInterpolation(
                defaultInterpolationParameters.params.let {
                    it.copy(
                        complementary = if (circlesAreCoDirected) !it.inBetween else it.inBetween
                    )
                },
                startArg.index, endArg.index
            )
            val oldSize = objects.size
            val newGCircles = expressions.addMultiExpr(expr)
            val newCircles = newGCircles.map { (it as? GCircle)?.upscale() }
            objectModel.addDisplayObjects(newCircles)
            val outputRange = (oldSize until objects.size).toList()
            submode = SubMode.ExprAdjustment(listOf(
                // here sourceIndex is less meaningful
                AdjustableExpr(expr,
                    sourceIndex = startArg.index,
                    outputRange, outputRange
                )
            ))
            if (newGCircles.any { it is ImaginaryCircle }) {
                queueSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
            }
        } else if (startArg is Arg.Point && endArg is Arg.Point) {
            val (startPointIndex, endPointIndex) = listOf(startArg, endArg).map { pointArg ->
                when (pointArg) {
                    is Arg.PointIndex -> pointArg.index
                    is Arg.FixedPoint -> createNewFreePoint(pointArg.toPoint())
                }
            }
            val expr = Expr.PointInterpolation(defaultInterpolationParameters.params,
                startPointIndex, endPointIndex
            )
            interpolateCircles = false
            val oldSize = objects.size
            val newGCircles = expressions.addMultiExpr(expr)
            val newPoints = newGCircles.map { (it as? Point)?.upscale() }
            objectModel.addDisplayObjects(newPoints)
            val outputRange = (oldSize until objects.size).toList()
            submode = SubMode.ExprAdjustment(listOf(
                AdjustableExpr(expr,
                    sourceIndex = startPointIndex,
                    outputRange, outputRange
                )
            ))
        }
        objectModel.invalidate()
    }

    fun completeCircleExtrapolation(
        params: ExtrapolationParameters,
    ) {
        openedDialog = null
        val argList = partialArgList ?: return
        val startCircleIx = (argList.args[0] as Arg.CLI).index
        val endCircleIx = (argList.args[1] as Arg.CLI).index
        val newGCircles = expressions.addMultiExpr(
            Expr.CircleExtrapolation(params, startCircleIx, endCircleIx),
        ).map { (it as? GCircle)?.upscale() }
        createNewGCircles(newGCircles)
        partialArgList = argList.copyEmpty()
        defaultExtrapolationParameters = DefaultExtrapolationParameters(params)
        objectModel.invalidate()
        recordHistory()
    }

    fun resetCircleExtrapolation() {
        openedDialog = null
        partialArgList = PartialArgList(
            Tool.CircleExtrapolation.signature,
            Tool.CircleExtrapolation.nonEqualityConditions
        )
    }

    private inline fun startExprParameterAdjustment(
        inputIndices: List<Ix>,
        crossinline mkExpr: (gCircleIndex: Ix) -> Expr.Conformal.OneToMany,
    ) {
        val gCircleSources = inputIndices.filter { objects[it] is GCircle }
        val arcPathSources = inputIndices.filter { objects[it] is ConcreteArcPath }
        val adjustables = mutableListOf<AdjustableExpr<Expr.Conformal>>()
        val source2trajectory = gCircleSources.map { sourceIndex ->
            // row/trajectory - column/simul-slice order
            val expr = mkExpr(sourceIndex)
            val result = expressions
                .addMultiExpr(expr)
                .map { (it as? GCircle)?.upscale() } // multi expression creates a whole trajectory at a time
            val outputIndices = objectModel.addDisplayObjects(result).toList()
            for (outputIndex in outputIndices) {
                copyStyle(sourceIndex, outputIndex)
            }
            adjustables.add(AdjustableExpr(expr,
                sourceIndex,
                outputIndices, outputIndices
            ))
            sourceIndex to outputIndices
        }
        val arcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
        for (sourceArcPathIndex in arcPathSources) {
            val (arcPathAdjustable, arcPathPointsAdjustables) =
                copyArcPathToMany(sourceArcPathIndex, mkExpr)
            val startIndex = adjustables.size
            adjustables.addAll(arcPathPointsAdjustables)
            val blueprintArcPath = arcPathAdjustable.expr
            arcPathAdjustables.add(
                arcPathAdjustable.copy(
                    expr = blueprintArcPath.reIndex { startIndex + it }
                )
            )
        }
        val copiedRegions = copySourceRegionsOntoTrajectories(source2trajectory)
        submode = SubMode.ExprAdjustment(
            adjustables = adjustables,
            arcPathAdjustables = arcPathAdjustables,
            regions = copiedRegions,
        )
        objectModel.invalidate()
    }

    /** When in [SubMode.ExprAdjustment], changes [submode]'s [Expr]s' parameters to
     * [parameters] and updates corresponding [objects] */
    fun adjustExprParameters(parameters: Parameters) {
        val sm = submode
        if (sm is SubMode.ExprAdjustment<*> && parameters != sm.parameters) {
            submode = when (sm.parameters) {
                is InterpolationParameters -> { // single adjustable expr case
                    val (expr, sourceIndex, outputIndices, reservedIndices) = sm.adjustables[0]
                    val newExpr = expr.copyWithNewParameters(parameters) as Expr.Conformal.OneToMany
                    val (newIndices, newReservedIndices, newObjects) = expressions.adjustMultiExpr(
                        newExpr = newExpr,
                        occupiedIndices = outputIndices,
                        reservedIndices = reservedIndices,
                    )
                    for (ix in newReservedIndices) { // we have to cleanup abandoned but reserved indices
                        if (ix < objects.size) {
                            objectModel.removeObjectAt(ix)
                        } else { // padding
                            objectModel.addDownscaledObject(null)
                        }
                    }
                    newIndices.zip(newObjects) { ix, o ->
                        objectModel.setDownscaledObject(ix, o)
                        copyStyle(sourceIndex, ix)
                    }
                    SubMode.ExprAdjustment(listOf(
                        AdjustableExpr(newExpr, sourceIndex, newIndices, newReservedIndices)
                    ))
                }
                // multiple adjustable exprs
                is RotationParameters,
                is BiInversionParameters,
                is LoxodromicMotionParameters -> {
                    for (arcPathAdjustable in sm.arcPathAdjustables)
                        objectModel.removeObjectsAt(arcPathAdjustable.outputIndices)
                    regions = regions.withoutElementsAt(sm.regions.toSet())
                    val newAdjustables = mutableListOf<AdjustableExpr<Expr.Conformal>>()
                    val source2trajectory = mutableListOf<Pair<Ix, List<Ix>>>()
                    for ((expr, sourceIndex, outputIndices, reservedIndices) in sm.adjustables) {
                        val newExpr = expr.copyWithNewParameters(parameters) as Expr.Conformal.OneToMany
                        val (newIndices, newReservedIndices, newObjects) = expressions.adjustMultiExpr(
                            newExpr = newExpr,
                            occupiedIndices = outputIndices,
                            reservedIndices = reservedIndices,
                        )
                        // NOTE: reserved indices will be generally non-contiguous
                        // we have to cleanup abandoned indices
                        val abandonedIndices = outputIndices.toSet() - newIndices.toSet()
                        for (ix in abandonedIndices) {
                            objectModel.removeObjectAt(ix)
                        }
                        for (ix in newReservedIndices) {
                            if (ix >= objects.size) { // pad with nulls
                                objectModel.addDownscaledObject(null)
                            }
                        }
                        for (i in newIndices.indices) {
                            val ix = newIndices[i]
                            objectModel.setDownscaledObject(ix, newObjects[i])
                            copyStyle(sourceIndex, ix)
                        }
                        newAdjustables.add(AdjustableExpr(newExpr,
                            sourceIndex,
                            newIndices, newReservedIndices
                        ))
                        source2trajectory.add(
                            sourceIndex to newIndices
                        )
                    }
                    val newTrajectorySize = newAdjustables.first().size
                    // NOTE: children of the source arc-path are handled properly still, they become
                    //  dependent on source children, not on children of the trajectory arc-paths
                    val newArcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
                    for ((arcPathBlueprint, sourceArcPathIndex, outputIndices, reservedIndices) in sm.arcPathAdjustables) {
                        val newArcPaths = List(newTrajectorySize) { trajectoryStage ->
                            arcPathBlueprint.reIndex { adjustableIndex ->
                                newAdjustables[adjustableIndex].outputIndices[trajectoryStage]
                            }
                        }
                        val (newIndices, newReservedIndices, newObjects) = expressions.adjustArcPathBlueprint(
                            newArcPaths,
                            outputIndices, reservedIndices
                        )
                        val abandonedIndices = outputIndices.toSet() - newIndices.toSet()
                        for (ix in abandonedIndices) {
                            objectModel.removeObjectAt(ix)
                        }
                        for (ix in newReservedIndices) {
                            if (ix >= objects.size) { // pad with nulls
                                objectModel.addDownscaledObject(null)
                            }
                        }
                        newIndices.zip(newObjects) { ix, concreteArcPath ->
                            objectModel.setDownscaledObject(ix, concreteArcPath)
                            copyStyle(sourceArcPathIndex, ix)
                        }
                        newArcPathAdjustables.add(AdjustableExpr(arcPathBlueprint,
                            sourceArcPathIndex,
                            newIndices, newReservedIndices
                        ))
                    }
                    val affectedRegions: List<Int> =
                        if (sm.parameters is LoxodromicMotionParameters &&
                            defaultLoxodromicMotionParameters.bidirectional &&
                            source2trajectory.size >= 2
                        ) {
                            // NOTE: assumption: bidirectional spiral adjustables must be laid out as {t^i}; {t^-i}
                            // s2t structure is
                            // t1^+1 .. t1^+n; t2^+1 .. t2^+n; ... tm^+1 .. tm^+n;
                            // t1^-1 .. t1^-n; t2^-1 .. t2^-n; ... tm^-1 .. tm^-n;
                            val size = source2trajectory.size.div(2)
                            //  we have to do this to copy regions properly both forward and backward
                            val foldedSource2trajectory = source2trajectory
                                .take(size)
                                .mapIndexed { i, (sourceIndex, forwardTrajectory) ->
                                    val backwardTrajectory = source2trajectory[size + i].second
                                    sourceIndex to (backwardTrajectory + forwardTrajectory)
                                }
                            copySourceRegionsOntoTrajectories(foldedSource2trajectory)
                        } else {
                            copySourceRegionsOntoTrajectories(source2trajectory)
                        }
                    SubMode.ExprAdjustment(
                        adjustables = newAdjustables,
                        arcPathAdjustables = newArcPathAdjustables,
                        regions = affectedRegions
                    )
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

    // completes tool modes with adjustable parameters
    fun confirmAdjustedParameters() {
        partialArgList = if (mode is ToolMode) {
            partialArgList?.copyEmpty()
        } else { // when adjusting in drag/multiselect
            null
        }
        when (val sm = submode) {
            is SubMode.ExprAdjustment<*> -> {
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
        recordHistory()
    }

    fun cancelExprAdjustment() {
        when (val sm = submode) {
            is SubMode.ExprAdjustment<*> -> {
                val outputs = sm.adjustables.flatMap { it.outputIndices }
                deleteObjectsWithDependenciesColorsAndRegions(
                    outputs,
                    circleAnimationInit = { null },
                )
            }
            else -> {}
        }
        submode = null
    }

    // NOTE: witnessed abnormal index skipping whe rotating lines, observing closely...
    fun startRotationParameterAdjustment() {
        val argList = partialArgList ?: return
        val objArg = argList.args[0] as Arg.Indices
        val pointArg = argList.args[1] as Arg.Point
        val pivotPointIndex = when (pointArg) {
            is Arg.PointIndex -> pointArg.index
            is Arg.FixedPoint -> createNewFreePoint(pointArg.toPoint())
        }
        val params = defaultRotationParameters.params
        startExprParameterAdjustment(objArg.indices) { ix ->
            Expr.Rotation(params, pivotPointIndex, ix)
        }
    }

    fun startBiInversionParameterAdjustment() {
        val argList = partialArgList ?: return
        val args = argList.args
        val objArg = args[0] as Arg.Indices
        val engine1 = (args[1] as Arg.CLI).index
        val engine2 = (args[2] as Arg.CLI).index
        val engine1GC = GeneralizedCircle.fromGCircle(objects[engine1] as CircleOrLineOrImaginaryCircle)
        val engine2GC0 = GeneralizedCircle.fromGCircle(objects[engine2] as CircleOrLineOrImaginaryCircle)
        val reverseSecondEngine = engine1GC scalarProduct engine2GC0 < 0 // anti-parallel
        defaultBiInversionParameters = defaultBiInversionParameters.copy(
            reverseSecondEngine = reverseSecondEngine
        )
        val params = defaultBiInversionParameters.params
        startExprParameterAdjustment(objArg.indices) { ix ->
            Expr.BiInversion(params, engine1, engine2, ix)
        }
    }

    fun startLoxodromicMotionParameterAdjustment() {
        setupLoxodromicSpiral(bidirectional = defaultLoxodromicMotionParameters.bidirectional)
    }

    private fun setupLoxodromicSpiral(bidirectional: Boolean) {
        val argList = partialArgList ?: return
        val args = argList.args
        val objArg = args[0] as Arg.Indices
        val divergenceArg = args[1] as Arg.Point
        val convergenceArg = args[2] as Arg.Point
        val (divergencePointIndex, convergencePointIndex) = listOf(divergenceArg, convergenceArg)
            .map { when (it) {
                is Arg.PointIndex -> it.index
                is Arg.FixedPoint -> createNewFreePoint(it.toPoint())
            } }
        partialArgList = argList.copy(
            // we dont want to spam create the same Point.XY each time
            args = listOf(
                objArg, Arg.PointIndex(divergencePointIndex), Arg.PointIndex(convergencePointIndex)
            ),
        )
        val params = defaultLoxodromicMotionParameters.params
        if (bidirectional) { // 2 interdependent spiral halves
            val gCircleSources = objArg.indices.filter { objects[it] is GCircle }
            val adjustables = mutableListOf<AdjustableExpr<Expr.Conformal>>()
            val spiralSize = params.nTotalSteps
            val firstHalfStart = objects.size
            val secondHalfStart = firstHalfStart + gCircleSources.size * spiralSize
            val source2trajectory1 = gCircleSources.mapIndexed { i, sourceIndex ->
                val expr = Expr.LoxodromicMotion(
                    parameters = params,
                    divergencePoint = divergencePointIndex,
                    convergencePoint = convergencePointIndex,
                    target = sourceIndex,
                    // NOTE: complementary half indices rely on contiguous layout of trajectories
                    otherHalfStart = secondHalfStart + i * spiralSize,
                )
                val result = expressions
                    .addMultiExpr(expr)
                    .map { (it as? GCircle)?.upscale() } // multi expression creates a whole trajectory at a time
                // result.size == spiralSize
                val outputIndices = objectModel.addDisplayObjects(result).toList()
                for (outputIndex in outputIndices) {
                    copyBorderColor(sourceIndex, outputIndex)
                }
                adjustables.add(AdjustableExpr(expr,
                    sourceIndex,
                    outputIndices, outputIndices
                ))
                sourceIndex to outputIndices
            }
            // reversing convergence-divergence for 2nd trajectory
            val source2trajectory2 = gCircleSources.mapIndexed { i, sourceIndex ->
                val expr = Expr.LoxodromicMotion(
                    parameters = params,
                    divergencePoint = convergencePointIndex,
                    convergencePoint = divergencePointIndex,
                    target = sourceIndex,
                    otherHalfStart = firstHalfStart + i * spiralSize,
                )
                val result = expressions
                    .addMultiExpr(expr)
                    .map { (it as? GCircle)?.upscale() }
                val outputIndices = objectModel.addDisplayObjects(result).toList()
                for (outputIndex in outputIndices) {
                    copyBorderColor(sourceIndex, outputIndex)
                }
                adjustables.add(AdjustableExpr(expr,
                    sourceIndex,
                    outputIndices, outputIndices
                ))
                sourceIndex to outputIndices
            }
            val copiedRegions = copySourceRegionsOntoTrajectories(
                source2trajectory1,
            ) + copySourceRegionsOntoTrajectories(
                source2trajectory2,
            )
            // TODO: copy arc-paths
            // 2 * nTargets adjustables
            submode = SubMode.ExprAdjustment(adjustables, regions = copiedRegions)
            objectModel.invalidate()
        } else { // half-spiral
            startExprParameterAdjustment(objArg.indices) { ix ->
                Expr.LoxodromicMotion(params,
                    divergencePointIndex, convergencePointIndex, ix
                )
            }
        }
    }

    fun updateLoxodromicBidirectionality(bidirectional: Boolean) {
        val sm = submode
        if (sm is SubMode.ExprAdjustment<*>) {
            when (sm.parameters) {
                is LoxodromicMotionParameters -> {
                    defaultLoxodromicMotionParameters = defaultLoxodromicMotionParameters.copy(
                        bidirectional = bidirectional,
                    )
                    regions = regions.withoutElementsAt(sm.regions.toSet())
                    deleteObjectsWithDependenciesColorsAndRegions(
                        indicesToDelete = sm.adjustables.flatMap { it.outputIndices },
                        circleAnimationInit = { null },
                    )
                    setupLoxodromicSpiral(bidirectional)
                }
                else -> {}
            }
        }
    }

    fun completeArcPath() {
        val pArcPath = partialArcPath ?: return
//        println(pArcPath)
        val vertexIndices: List<Ix> = pArcPath.vertices.map { vertex ->
            when (val p2p = realizePointSnap(vertex.snap, recordHistory = false)) {
                is PointSnapResult.Eq -> p2p.pointIndex
                is PointSnapResult.Free -> createNewFreePoint(p2p.result)
            }
        }
        val arcs = pArcPath.arcs.mapIndexed { arcIndex, arc ->
            when (val p2p = realizePointSnap(arc.midpointSnap, recordHistory = false)) {
                is PointSnapResult.Free -> {
                    ArcPath.Arc.By2Points(sagittaRatio =
                        if (arc.circle == null)
                            0.0 // straight line
                        else
                            computeSagittaRatio(
                                circle = arc.circle,
                                chordStart = pArcPath.arcIndex2startVertex(arcIndex).point,
                                chordEnd = pArcPath.arcIndex2endVertex(arcIndex).point,
                            )
                    )
                }
                is PointSnapResult.Eq -> {
                    ArcPath.Arc.By3Points(middlePointIndex = p2p.pointIndex)
                }
            }
        }
        val concreteArcPath = expressions.addSoloExpr(
            if (pArcPath.isClosed)
                ArcPath.Closed(vertices = vertexIndices, arcs = arcs)
            else
                ArcPath.Open(vertices = vertexIndices, arcs = arcs)
        )
        val ix = objectModel.addDownscaledObject(concreteArcPath)
        selection = Selection(arcPaths = listOf(ix))
        objectModel.invalidate()
        recordHistory()
        partialArcPath = null
    }

    private fun completePoint() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.Point }
        val arg0 = args[0]
        if (arg0 is Arg.PointXY) {
            val newPoint = arg0.toPoint()
            val ix = createNewFreePoint(newPoint)
            selection = Selection(gCircles = listOf(ix))
            recordHistory()
        } // it could have already done it with realized PSR.Eq, which results in Arg.Point.Index
        partialArgList = argList.copyEmpty()
    }

    fun confirmDialogSelectedParameters(
        parameters: Parameters
    ) {
        openedDialog = null
        adjustExprParameters(parameters)
        confirmAdjustedParameters()
    }

    fun confirmCurrentAction() {
        when (mode) {
            ToolMode.ARC_PATH ->
                completeArcPath()
            else -> when (submode) {
                is SubMode.ExprAdjustment<*> ->
                    confirmAdjustedParameters()
                // Enter for some reason simulates button click on the focused button...
                // which breaks this
                is SubMode.RectangularSelect ->
                    submode = null
                else -> {}
            }
        }
    }

    fun onSaveFinished(saveResult: SaveResult) {
        openedDialog = null
        when (saveResult) {
            is SaveResult.Success -> {
                lastSaveMetadata = saveResult
                queueSnackbarMessage(
                    SnackbarMessage.SUCCESSFUL_SAVE,
                    saveResult.filename,
                )
                when (queuedAction) {
                    Action.NEW_BLANK ->
                        openNewBlank()
                    else -> {}
                }
            }
            is SaveResult.Failure -> {
                val errorMessage =
                    if (saveResult.error == null) ""
                    else "; error: \"${saveResult.error}\""

                queueSnackbarMessage(
                    SnackbarMessage.FAILED_SAVE,
                    saveResult.filename ?: "-",
                    errorMessage
                )
            }
            is SaveResult.Cancelled -> {}
        }
        queuedAction = null
    }

    fun closeDialog() {
        openedDialog = null
        queuedAction = null
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
            if (sm is SubMode.ExprAdjustment<*>) {
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
            Tool.Palette -> openedDialog = DialogType.REGION_FILL_COLOR_PICKER
            Tool.Expand -> scaleSelection(HUD_ZOOM_INCREMENT)
            Tool.Shrink -> scaleSelection(1/HUD_ZOOM_INCREMENT)
            Tool.Detach -> detachEverySelectedObject()
            Tool.SwapDirection -> swapOrientationsInSelection()
            Tool.MarkAsPhantoms ->
                if (toolPredicate(tool))
                    markSelectedObjectsAsPhantoms()
                else unmarkSelectedObjectsAsPhantoms()
            Tool.Duplicate -> duplicateSelection()
            Tool.BorderColor, Tool.PointColor -> openedDialog = DialogType.BORDER_COLOR_PICKER
            Tool.FillColor -> openedDialog = DialogType.FILL_COLOR_PICKER
            Tool.SetLabel -> openedDialog = DialogType.LABEL_INPUT
            Tool.Delete -> deleteSelection()
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
        if (submode is SubMode.SelectionChoices)
            submode = null
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
                // idt we realistically ever need to track all invalidations.
                // you'd have to move free objects in such a way that all others would
                // become imaginary/null
//                hug(objectModel.invalidations)
                hug(objectModel.propertyInvalidations)
                objectSelection.containsAll(objects.filterIndices { it is CircleOrLineOrPoint })
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
                hug(objectModel.propertyInvalidations)
                objectSelection.none { it in phantoms }
            }
            Tool.InfinitePoint -> { // whether to prompt infinite-point input
                partialArgList?.let { argList ->
                    argList.nextArgType?.let { nextArgType ->
                        val acceptsInfinitePoint = Arg.InfinitePoint in nextArgType.possibleTypes
                        val acceptsIndices = Arg.Indices in nextArgType.possibleTypes
                        acceptsInfinitePoint &&
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
                // NOTE: without changing selection, the only way to change the predicate is
                //  after applying move-to-infinity or on detachment.
                hug(objectModel.invalidations)
                objectSelection.singleOrNull()?.let { ix ->
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

    private fun saveState(): SaveState {
        val center = computeAbsoluteCenter() ?: Offset.Zero
        // NOTE: it's important to copy mutable collections
        val size = min(objects.size, expressions.expressions.size)
        return SaveState(
            objects = objectModel.displayObjects.take(size).toList(),
            expressions = expressions.expressions.filterKeys { it < size }.toMap(),
            borderColors = objectModel.borderColors.filterKeys { it < size }.toMap(),
            fillColors = objectModel.fillColors.filterKeys { it < size }.toMap(),
            labels = labels.filterKeys { it < size }.toMap(),
            regions = regions.mapNotNull { region ->
                val insides = region.insides.filter { it < size }.toSet()
                val outsides = region.outsides.filter { it < size }.toSet()
                if (insides.isEmpty() && outsides.isEmpty())
                    null
                else
                    region.copy(
                        insides = insides,
                        outsides = outsides,
                    )
            },
            backgroundColor = backgroundColor,
            chessboardPattern = chessboardPattern,
            chessboardColor = chessboardColor,
            phantoms = objectModel.phantomObjectIndices.filter { it < size }.toSet(),
            selection = selection.copy(
                gCircles = selection.gCircles.filter { it < size },
                arcPaths = selection.arcPaths.filter { it < size },
            ),
            center = center,
            regionColor = regionColor,
        )
    }

    suspend fun restoreFromDisk() {
        if (restoration.value == ProgressState.NOT_STARTED) {
            restoration.update { ProgressState.IN_PROGRESS }
            val platform = getPlatform()
            val restoreLastSave = RESTORE_LAST_SAVE_ON_LOAD
            val restoreSettings = true
            if (restoreLastSave) {
                val saveState = runCatching {
                    // NOTE: can crash when the underlying format changes
                    platform.autosaveStore.get()
                }
                    .onFailure { it.printStackTrace() }
                    .getOrNull()
                if (saveState != null) {
                    restoreFromState(saveState)
                } else {
                    val vmState = runCatching {
                        platform.lastStateStore.get()
                    }
                        .onFailure { it.printStackTrace() }
                        .getOrNull()
                    if (vmState == null) {
                        // i'd like to replace it with SaveState.SAMPLE
                        // but the format disallows not-yet-calculated objects
                        restoreFromVMState(State.SAMPLE)
                    } else {
                        restoreFromVMState(vmState)
                    }
                }
            } else {
                restoreFromVMState(State.SAMPLE)
            }
            if (restoreSettings) {
                runCatching {
                    platform.settingsStore.get()
                }
                    .onFailure { it.printStackTrace() }
                    .getOrNull()?.let { settings ->
                        loadSettings(settings)
                    }
            }
            if (restoreLastSave) {
                runCatching {
                    platform.historyStore.get()
                }
                    .onFailure { it.printStackTrace() }
                    .getOrNull()?.let { historyState ->
                        history = historyState.load(undoIsEnabled, redoIsEnabled)
                    }
            }
            restoration.update { ProgressState.COMPLETED }
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
        switchToCategory(toolbarState.activeCategory)
    }

    private fun restoreFromState(state: SaveState) {
        if (!mode.isSelectingCircles()) {
            switchToMode(SelectionMode.Drag)
        }
        loadState(state)
        resetHistory()
    }

    // NOTE: migrated to SaveState, this is left for compatibility with previous auto-saves
    private fun restoreFromVMState(state: State) {
        loadNewConstellation(state.constellation)
        centerizeTo(state.centerX, state.centerY)
        val switchToMultiselect = state.selection.size > 1 && objectSelection.size <= 1
        selection = Selection(gCircles = state.selection)
        state.regionColor?.let {
            regionColor = it
        }
        chessboardPattern = state.chessboardPattern
        state.chessboardColor?.let {
            chessboardColor = it
        }
        resetHistory()
        if (switchToMultiselect) {
            switchToMode(SelectionMode.Multiselect)
        }
    }

    /** caches latest [SaveState] using platform-specific local storage */
    fun cacheState() {
        if (!cachingInProgress.value) {
            cachingInProgress.update { true }
            println("caching VM state...")
            val platform = getPlatform()
            val presentState = saveState()
//            println("caching state ${presentState.expressions}")
            platform.saveState(presentState)
            platform.saveSettings(getCurrentSettings())
            platform.saveHistory(history.save())
            cachingInProgress.update { false }
            println("cached.")
        }
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

    // NOTE: i never seen this proc on Android or Wasm tbh, only on Desktop
    //  so i had to create Flow<LifecycleEvent> to manually trigger caching
    override fun onCleared() {
        println("VM.onCleared")
        cacheState()
        super.onCleared()
    }

    // TODO: migrate to SaveState eventually
    /**
     * Save-able state of [EditorViewModel], used for autosave.
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
        const val ENABLE_ANGLE_SNAPPING = false //true
        const val ENABLE_TANGENT_SNAPPING = true
        /** try aligning PartialArcPath vertices horizontally or
         * vertically to each other */
        const val ENABLE_ALIGNMENT_SNAPPING = true
        const val RESTORE_LAST_SAVE_ON_LOAD = true
        const val TWO_FINGER_TAP_FOR_UNDO = true // Android-only
        const val DEFAULT_SHOW_DIRECTION_ARROWS_ON_SELECTED_CIRCLES = false
        const val SHOW_IMAGINARY_CIRCLES = true
        /** When several objects are close enough to the tap position,
         * show the list of them to choose from */
        const val SHOW_SELECTION_CHOICES = true
        /** Allow moving non-free object IF all of it's lvl 1 parents=dependencies are free by
         * moving all of its parent with it */ // ggbra-like
        val INVERSION_OF_CONTROL = InversionOfControl.LEVEL_1
        /** When constructing an object depending on not-yet-existing points,
         * always create them. In contrast to replacing its expression with a static, free circle */
        const val ALWAYS_CREATE_ADDITIONAL_POINTS = false
        // NOTE: changing this factor breaks all line-incident points (scale-dependence)
        /** [Double] arithmetic is best in range that is closer to 0 */
        const val UPSCALING_FACTOR = ConformalObjectModel.UPSCALING_FACTOR
        const val DOWNSCALING_FACTOR = ConformalObjectModel.DOWNSCALING_FACTOR

        fun sliderPercentageDeltaToZoom(percentageDelta: Float): Float =
            MAX_SLIDER_ZOOM.pow(2*percentageDelta)
    }
}
