package ui.editor

import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
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
import core.geometry.CircleOrLineOrConcreteArcPath
import core.geometry.CircleOrLineOrImaginaryCircle
import core.geometry.CircleOrLineOrPoint
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteArcPath
import core.geometry.ImaginaryCircle
import core.geometry.Line
import core.geometry.Point
import core.geometry.RectangleCollider
import core.geometry.conformal.GeneralizedCircle
import core.geometry.conformal.Rotor
import core.geometry.conformal.calculateStereographicRotationBiEngine
import core.geometry.conformal.generateSphereGrid
import core.geometry.fromCorners
import core.geometry.liesInside
import core.geometry.scaled00
import core.geometry.translationDelta
import domain.ColorAsCss
import domain.Ix
import domain.PointSnapResult
import domain.ProgressState
import domain.Snapping
import domain.angleDeg
import domain.cluster.Constellation
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
import domain.expressions.areCompatibleTransforms
import domain.expressions.changeTarget
import domain.expressions.computeConcentricCircle
import domain.expressions.computeIntersection
import domain.expressions.computeSagittaRatio
import domain.expressions.copy
import domain.expressions.copyWithNewParameters
import domain.expressions.moveArcMidpoint
import domain.expressions.reIndex
import domain.filterIndices
import domain.hug
import domain.indicesSortedBy
import domain.io.DdcFormat
import domain.io.DdcV1
import domain.io.DdcV2
import domain.io.DdcV5
import domain.io.SaveConfig
import domain.io.SaveRequest
import domain.io.SaveResult
import domain.io.saveStateAsSvg
import domain.model.Arg
import domain.model.ChangeHistory
import domain.model.ChessboardPattern
import domain.model.CompressedRegionConstraints
import domain.model.ConformalObjectModel
import domain.model.ContinuousChange
import domain.model.LogicalRegion
import domain.model.PartialArcPath
import domain.model.PartialArgList
import domain.model.RegionConstraints
import domain.model.SaveState
import domain.model.Selection
import domain.model.Styling
import domain.mostCommonOf
import domain.never
import domain.settings.BlendModeType
import domain.settings.InversionOfControl
import domain.settings.Settings
import domain.sortedByFrequency
import domain.transpose
import domain.updated
import domain.withoutElementsAt
import domain.xor
import getPlatform
import io.github.xxfast.kstore.extensions.cached
import io.github.xxfast.kstore.utils.ExperimentalKStoreApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import ui.theme.CustomColors
import ui.theme.DodeclustersColors
import ui.tools.Category
import ui.tools.Tool
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// this class is obviously too big, maybe separate into CanvasViewModel and UiViewModel
// MAYBE: timed autosave (cron-like), e.g. every 10min
@Suppress("NOTHING_TO_INLINE")
class EditorViewModel : ViewModel() {
    val objectModel: ConformalObjectModel = ConformalObjectModel()
    val objects: List<GCircleOrConcreteArcPath?> = objectModel.displayObjects
    inline val expressions: ConformalExpressions get() =
        objectModel.expressions
    val styling: Map<Ix, Styling> = objectModel.styling
    val phantoms: Set<Ix> get() =
        objectModel.phantoms
    // MAYBE: encapsulate regions into ObjectModel
    /** Filled regions delimited by some objects from [objects] */
    var regions: List<LogicalRegion> by mutableStateOf(listOf())
        private set
    var _debugObjects: List<GCircle> by mutableStateOf(emptyList())

    var canvasState: CanvasState by mutableStateOf(CanvasState())
    // we dont include translation into canvasState since it usually changes continuously
    var translation: Offset by mutableStateOf(Offset.Zero)
        private set

    private val selectionState: MutableState<Selection> = mutableStateOf(Selection())
    /** indices of selected circles/lines/points & arc-paths */
    var selection: Selection by selectionState
    // maybe make it derivedStateOf w/ invalidation dep
    /** Distinct selected [GCircle]? indices +
     * indices of all vertices/midpoints of selected arc-paths */
    val selectedIndices: List<Ix> get() =
        selection.gCircles.plus(
            selection.arcPaths.flatMap {
                objectModel.getArcPath(it)?.dependencies ?: emptySet()
            }
        ).distinct()

    private val modeState: MutableState<Mode> = mutableStateOf(SelectionMode.Drag)
    private val submodeState: MutableState<Submode?> = mutableStateOf(null)
    /** Major editing mode */
    var mode: Mode by modeState
        private set
    /** Minor editing mode, bound to [mode]; can hold transient data */
    var submode: Submode? by submodeState // freq changes
    // we do this because submode can change continuously while its type only discretely
    /** Use for decisions that don't depend on concrete [submode] parameters,
     * only on its class. Changes discretely, slower than [submode], which can save
     * recompositions */
    val submodeType: Submode.Type? by derivedStateOf { submode?.type }
    val submodeSelectionChoicesInput: Submode.SelectionChoicesInput? by derivedStateOf {
        submode as? Submode.SelectionChoicesInput
    }
    val exprAdjustmentType: Submode.ExprAdjustment.Type? by derivedStateOf {
        when (val sm = submode) {
            is Submode.ExprAdjustment<*> -> when (sm.parameters) {
                is InterpolationParameters -> Submode.ExprAdjustment.Type.INTERPOLATION
                is RotationParameters -> Submode.ExprAdjustment.Type.ROTATION
                is BiInversionParameters -> Submode.ExprAdjustment.Type.BI_INVERSION
                is LoxodromicMotionParameters -> Submode.ExprAdjustment.Type.LOXODROMIC_MOTION
                else -> null
            }
            else -> null
        }
    }

    // NOTE: Arg.XYPoint & co use absolute positioning
    private val partialArgListState: MutableState<PartialArgList?> = mutableStateOf(null)
    /** Partly filled [Tool] arg-list during [ToolMode] */
    var partialArgList: PartialArgList? by partialArgListState
    /** Under-construction arc-path during [ToolMode.ARC_PATH] */
    var partialArcPath: PartialArcPath? by mutableStateOf(null)
        private set

    private var loadedSettings: Settings = Settings()

    /** currently selected color */
    var regionColor: Color by mutableStateOf(DodeclustersColors.deepAmethyst)
        private set

    var regionManipulationStrategy: RegionManipulationStrategy by mutableStateOf(
        RegionManipulationStrategy.REPLACE
    )
        private set
    /** applies to [SelectionMode.Region]:
     * only use circles present in the [selection].gCircles to determine which regions to fill */
    var restrictRegionsToSelection: Boolean by mutableStateOf(false)
        private set

    var uiState: UiState by mutableStateOf(UiState())
    /** encapsulates all category- and tool-related info */
    val toolbarState: ToolbarState get() =
        uiState.toolbarState

    // boolean flags with somewhat frequently changing deps are wrapped in derivedStateOf
    val showGenericSelectionContextActions: Boolean by derivedStateOf {
        mode.isSelectingObjects() && canvasState.showCircles &&
        (selection.gCircles.any { objects[it] is CircleOrLineOrImaginaryCircle } ||
            selection.arcPaths.isNotEmpty() &&
            selection.gCircles.any { objects[it] is Point }
        )
    }
    val showPointContextActions: Boolean by derivedStateOf {
        canvasState.showCircles && mode.isSelectingObjects() && selection.gCircles.any { objects[it] is Point }
    }
    val showArcPathContextActions: Boolean by derivedStateOf {
        mode.isSelectingObjects() && selection.arcPaths.isNotEmpty()
    }
    val showPartialArcPathContextActions: Boolean by derivedStateOf {
        mode == ToolMode.ARC_PATH &&
        partialArcPath?.arcs?.size?.let { it >= 1 } == true
    }
    val showAdjustExprButton: Boolean by derivedStateOf {
        hug(objectModel.invalidations)
        exprAdjustmentManager.getAdjustableExprs().isNotEmpty()
//        areAdjustableExprIndices(selection.gCircles)
    }
    val showInfinitePoint: Boolean by derivedStateOf {
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

    val selectionIsLocked: Boolean by derivedStateOf {
        hug(objectModel.invalidations)
        selection.gCircles.toSet()
            .plus(selection.arcPaths.flatMap {
                objectModel.getArcPath(it)?.dependencies ?: emptySet()
            })
            .all { objects[it] == null || !isFree(it) }
    }

    val handleConfig: HandleConfig? by derivedStateOf {
        if (mode.isSelectingObjects())
            when {
                selection.gCircles.size == 1 && selection.arcPaths.isEmpty() ->
                    HandleConfig.SINGLE_CIRCLE
                selectedIndices.size > 1 ->
                    HandleConfig.SEVERAL_OBJECTS
                else -> null
            }
        else null
    }

    inline val scaleSliderPercentage: Float get() =
        submode.let { sm ->
            if (sm is Submode.ScaleViaSlider)
                sm.sliderPercentage
            else 0.5f
        }
    inline val rotationHandleAngle: Float get() =
        submode.let { sm ->
            if (sm is Submode.Rotate)
                sm.angle.toFloat()
            else 0f
        }

    val undoIsEnabled: MutableState<Boolean> = mutableStateOf(false)
    val redoIsEnabled: MutableState<Boolean> = mutableStateOf(false)
    private var history: ChangeHistory = ChangeHistory( // stub
        initialState = SaveState.SAMPLE,
        undoIsEnabled = undoIsEnabled,
        redoIsEnabled = redoIsEnabled,
    )

    // ahh.. to be set during exprAdjustmentManager.startCircleOrPointInterpolationParameterAdjustment()
    var interpolateCircles: Boolean by mutableStateOf(true)
    var circlesAreCoDirected: Boolean by mutableStateOf(true)

    var colorPickerParameters by mutableStateOf(
        ColorPickerParameters(Color.Unspecified, emptyList())
    )
        private set
    var defaultInterpolationParameters by mutableStateOf(DefaultInterpolationParameters())
    var defaultExtrapolationParameters by mutableStateOf(DefaultExtrapolationParameters())
    var defaultRotationParameters by mutableStateOf(DefaultRotationParameters())
    var defaultBiInversionParameters by mutableStateOf(DefaultBiInversionParameters())
    var defaultLoxodromicMotionParameters by mutableStateOf(DefaultLoxodromicMotionParameters())

    private val exprAdjustmentManager = ExprAdjustmentManager(
        objectModel = objectModel,
        submodeState = submodeState,
        partialArgListState = partialArgListState,
    )

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
    var saveConfig: SaveConfig by mutableStateOf(SaveConfig())
        private set

    val animations: MutableSharedFlow<ObjectAnimation> = MutableSharedFlow()

    val snackbarMessages: MutableSharedFlow<Pair<SnackbarMessage, Array<out Any>>> =
        MutableSharedFlow(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    val drawerOpenCloseRequests: MutableSharedFlow<DrawerValue> = MutableSharedFlow()

    val restoration: MutableStateFlow<ProgressState> =
        MutableStateFlow(ProgressState.NOT_STARTED)
    private val cachingInProgress: MutableStateFlow<Boolean> =
        MutableStateFlow(false)

    val toolManager: ToolManager = ToolManager(
        objectModel = objectModel,
        modeState = modeState,
        submodeState = submodeState,
        selectionState = selectionState,
    )

    /** presently used to resolve save-before-new-blank situation by queueing [Action.NEW_BLANK] */
    private var queuedAction: Action? by mutableStateOf(null)

    private var movementAfterDown = false

    /** min tap/grab distance to select an object */
    private var tapRadius =
        getPlatform().tapRadius
//    private val lowAccuracyTapRadius get() = tapRadius*LOW_ACCURACY_FACTOR
    private inline val tapRadius2 get() =
        tapRadius*tapRadius
    private inline val lowAccuracyTapRadius2 get() =
        tapRadius*tapRadius*LOW_ACCURACY_FACTOR*LOW_ACCURACY_FACTOR

    init {
//        println("VM.init")
        viewModelScope.launch {
            restoreFromDisk()
            if (AUTOSAVE_EVERY_5_MINUTES) {
                autosaveEvery5Minutes()
            }
        }
    }

    /** sets [tapRadius] based on [density] */
    fun setEpsilon(density: Density) {
        with (density) {
            tapRadius = getPlatform().tapRadius.dp.toPx()
        }
    }

    inline fun updateCanvasState(crossinline transform: (CanvasState) -> CanvasState) {
        canvasState = transform(canvasState)
    }

    inline fun updateUiState(crossinline transform: (UiState) -> UiState) {
        uiState = transform(uiState)
    }

    fun clearSelection() { // better for autocomplete
        selection = Selection()
    }

    fun onCanvasSizeChange(newCanvasSize: IntSize) {
        val prevCenter = canvasState.canvasCenter
        val newCenter = Offset(newCanvasSize.width/2f, newCanvasSize.height/2f)
        translation += (newCenter - prevCenter)
        updateCanvasState { it.copy(canvasSize = newCanvasSize) }
        objectModel.pathCache.invalidateAll()
        objectModel.invalidatePositions()
    }

    // TODO: save with history (checkbox)
    fun saveAsYaml(name: String = DdcV5.DEFAULT_NAME): String {
        val yamlString = YamlEncoding.encodeToString(
            DdcV5.fromSaveState(saveState())
                .copy(name = name)
        )
        return yamlString
    }

    fun exportAsSvg(
        name: String = DdcV5.DEFAULT_NAME,
        customColors: CustomColors,
    ): String {
        val svgString = saveStateAsSvg(
            saveState = saveState(),
            width = canvasState.canvasSize.width.toFloat(),
            height = canvasState.canvasSize.height.toFloat(),
            encodeCirclesAndPoints = canvasState.showCircles,
            name = name,
            customColors = customColors,
        )
        return svgString
    }

    private fun computeAbsoluteCenter(): Offset? =
        if (canvasState.canvasSize == IntSize.Zero) {
            null
        } else {
            absolute(canvasState.canvasCenter)
        }

    private fun updateSaveConfig(
        filename: String?,
    ) {
        saveConfig = saveConfig.copy(
            name = filename?.substringBeforeLast('.') ?: saveConfig.name
        )
    }

    // i dont want to make it suspend tbh
    fun loadDdc(content: String, filename: String? = null) {
        DdcFormat.tryParseDdc(
            content = content,
            onDdc5 = { ddc5 ->
                val state = ddc5.toSaveState()
                loadState(state)
                updateSaveConfig(filename)
            },
            onDdc4 = { ddc4 ->
                val constellation = ddc4.toConstellation()
                loadNewConstellation(constellation)
                centerizeTo(ddc4.bestCenterX, ddc4.bestCenterY)
                updateCanvasState { it.copy(
                    chessboardColor = ddc4.chessboardColor ?: it.chessboardColor,
                    chessboardPattern =
                        if (!ddc4.chessboardPattern) ChessboardPattern.NONE
                        else if (ddc4.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                        else ChessboardPattern.STARTS_TRANSPARENT
                ) }
                updateSaveConfig(filename)
            },
            onDdc3 = { ddc3 ->
                val constellation = ddc3.toConstellation().toConstellation()
                loadNewConstellation(constellation)
                centerizeTo(ddc3.bestCenterX, ddc3.bestCenterY)
                updateCanvasState { it.copy(
                    chessboardPattern =
                        if (!ddc3.chessboardPattern) ChessboardPattern.NONE
                        else if (ddc3.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                        else ChessboardPattern.STARTS_TRANSPARENT
                ) }
                updateSaveConfig(filename)
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
                updateCanvasState { it.copy(
                    chessboardPattern =
                        if (!ddc2.chessboardPattern) ChessboardPattern.NONE
                        else if (ddc2.chessboardPatternStartsColored) ChessboardPattern.STARTS_COLORED
                        else ChessboardPattern.STARTS_TRANSPARENT
                ) }
                updateSaveConfig(filename)
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
                updateSaveConfig(filename)
            },
            onClusterV1 = { cluster1 ->
                loadNewConstellation(
                    cluster1.toCluster().toConstellation()
                )
                updateSaveConfig(filename)
            },
            onFail = {
                showSnackbarMessage(SnackbarMessage.FAILED_OPEN, filename ?: "")
            },
        )
        resetHistory()
    }

    fun centerizeTo(centerX: Float?, centerY: Float?) {
        translation = -Offset(
            centerX?.let { it - canvasState.canvasHalfWidth } ?: 0f,
            centerY?.let { it - canvasState.canvasHalfHeight } ?: 0f,
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
                backgroundColor = canvasState.backgroundColor,
            )
        )
        resetHistory()
        saveConfig = saveConfig.copy(
            name = null,
            uri = null,
        )
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
        println("circles/lines @ ${objectModel.circleOrLineIndices}")
        println("points @ ${objectModel.pointIndices}")
        println("arc-paths @ ${objectModel.arcPathIndices}")
        println("partialArgList = $partialArgList")
        println("selection = $selection")
        println("selected objects = $selectedObjectsString")
        println("selected objects downscaled = " + selection.gCircles.joinToString { ix ->
            "$ix: " + objectModel.downscaledObjects[ix].toString()
        })
        println("selected objects expressions = $selectedExpressionsString")
        println("selected concrete arc-paths = $selectedConcreteArcPathsString")
        println("selected arc-paths = $selectedArcPathsString")
        println(
            "regions bounded by some of selected objects = " + regions.filter {
                it.insides.any { ix -> ix in selection.indices } ||
                it.outsides.any { ix -> ix in selection.indices }
            }.joinToString { it.toString() }
        )
        println("partialArcPath = $partialArcPath")
        println("invalidation #${objectModel.positionInvalidations}\t " +
                "propertyInvalidation #${objectModel.invalidations}"
        )
//        val circle = objects[expressions.circleIndices.first()] as Circle
//        val line = objects[expressions.lineIndices.first()] as Line
//        val line2 = objects[expressions.lineIndices.last()] as Line
//        val point = objects[expressions.pointIndices.first()] as Point
//        val point2 = objects[expressions.pointIndices.toList()[1]] as Point
//        val point3 = objects[expressions.pointIndices.last()] as Point
//        val arcPath = objects[expressions.arcPathIndices.first()] as ConcreteArcPath
//        val arcPath2 = objects[expressions.arcPathIndices.last()] as ConcreteArcPath
//        _debugObjects = arcPath.calculateIntersectionPoints(circle)
        if (selection.isNotEmpty()) {
            val message = buildString {
                if (selectedObjectsString.isNotEmpty())
                    appendLine("$selectedObjectsString;")
                if (selectedExpressionsString.isNotEmpty())
                    appendLine("$selectedExpressionsString;")
                if (selectedArcPathsString.isNotEmpty())
                    appendLine("$selectedArcPathsString;")
//                clear() // tmp
//                append(line2.getRegionLocation(line))
            }
            showSnackbarMessage(SnackbarMessage.PLACEHOLDER, message)
        }
    }

    fun loadNewConstellation(constellation: Constellation) {
        val updatedConstellation = constellation.updated()
        resetTransients()
        updateCanvasState { it.copy(
            chessboardPattern = ChessboardPattern.NONE
        ) }
        translation = Offset.Zero
        loadConstellation(updatedConstellation)
        println("loaded new constellation")
        if (!mode.isSelectingObjects()) {
            selectTool(Tool.Drag)
        }
    }

    private fun loadConstellation(constellation: Constellation) {
        regions = emptyList() // important, since draws are async (otherwise can crash)
        clearSelection()
        objectModel.loadConstellation(constellation)
        val objectIndices = objects.indices.toSet()
        regions = constellation.parts.filter { part -> // region validation
            part.insides.all { it in objectIndices } &&
            part.outsides.all { it in objectIndices }
        }
        updateCanvasState { it.copy(backgroundColor = constellation.backgroundColor) }
        objectModel.invalidate()
    }

    private fun resetHistory() {
        history = ChangeHistory(
            initialState = saveState(),
            undoIsEnabled = undoIsEnabled,
            redoIsEnabled = redoIsEnabled,
        )
    }

    /** Saves present state, diffs it with [history]`.lastRecordedState` and
     * records changes to [history]`.past`, enabling [undo]s */
    fun recordHistory() {
        history.recordDiff(saveState())
    }

    /** Forgets unrecorded & unaccumulated changes until now */
    fun forgetUnrecordedChanges() {
        history.pushState(saveState())
    }

    private fun resetTransients() {
        submode = null
        partialArcPath = null
    }

    private fun loadState(state: SaveState) {
        resetTransients()
        regions = emptyList() // important, since draws are async (otherwise can crash)
        clearSelection()
        objectModel.loadState(state)
        regions = state.regions
        updateCanvasState { it.copy(backgroundColor = state.backgroundColor) }
        val validSelection = state.selection.copy( // just in case
            gCircles = state.selection.gCircles.filter { it in objects.indices },
            arcPaths = state.selection.arcPaths.filter { it in objects.indices },
        )
        val switchToMultiselect = selection.gCircles.size <= 1 && validSelection.gCircles.size > 1
        selection = validSelection
        if (state.center.isSpecified)
            centerizeTo(state.center.x, state.center.y)
        else
            translation = Offset.Zero
        regionColor = state.regionColor ?: regionColor
        updateCanvasState { it.copy(
            chessboardColor = state.chessboardColor ?: regionColor,
            chessboardPattern = state.chessboardPattern,
        ) }
        objectModel.invalidate()
        if (switchToMultiselect) {
            switchToMode(SelectionMode.Multiselect)
        }
    }

    fun undo() {
        if (!undoIsEnabled.value)
            return
        when (val mode = mode) {
            is ToolMode if (partialArgList?.args?.isNotEmpty() == true) -> {
                // MAYBE: just pop the last arg
                partialArgList = PartialArgList(mode.signature, mode.nonEqualityConditions)
                if (submode is Submode.ExprAdjustment<*>) {
                    cancelExprAdjustment()
                }
            }
            else -> {
                when (submode) {
                    is Submode.RotateStereographicSphere -> switchToCategory(Category.Drag)
                    else -> switchToMode(mode) // clears up stuff
                }
                clearSelection()
                val presentState = saveState()
                val newState = history.undo(presentState)
                loadState(newState)
                resetTransients()
            }
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
        val newIndices = objectModel.addDisplayObjects(newGCircles)
        if (newGCircles.isNotEmpty()) {
            updateCanvasState { it.copy(
                showCircles = true
            ) }
            selection = Selection(gCircles = newIndices.filter { objects[it] is GCircle })
            val ix2o = newIndices.mapNotNull { ix ->
                objects[ix]?.let { ix to it }
            }.toMap()
            viewModelScope.launch {
                animations.emit(
                    AppearanceAnimation.Entrance(ix2o)
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
    fun copySourceRegionsOntoTrajectories(
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
        if (mode.isSelectingObjects()) {
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
                val ix2o = allObjectsToCopy.mapNotNull { ix ->
                    objects[ix]?.let { ix to it }
                }.toMap()
                viewModelScope.launch {
                    animations.emit(AppearanceAnimation.ReEntrance(ix2o))
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
        objectModel.styling[sourceIndex]?.borderColor?.let { color ->
            objectModel.updateStyle(destinationIndex) {
                it.copy(borderColor = color)
            }
        }
    }

    private fun copyFillColor(
        sourceIndex: Ix,
        destinationIndex: Ix,
    ) {
        objectModel.styling[sourceIndex]?.fillColor?.let { color ->
            objectModel.updateStyle(destinationIndex) {
                it.copy(fillColor = color)
            }
        }
    }

    /** copies [Styling] with empty label */
    fun copyStyle(
        sourceIndex: Ix,
        destinationIndex: Ix,
    ) {
        objectModel.styling[sourceIndex]?.let { style ->
            objectModel.updateStyle(destinationIndex) {
                style.copy(label = null)
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
        val startIndex = regions.size
        regions += newRegions
        return startIndex until regions.size
    }

    fun deleteSelection() {
        val gCirclesToDelete = selection.gCircles
        val arcPathsToDelete = selection.arcPaths
        if ((canvasState.showCircles && gCirclesToDelete.isNotEmpty() || arcPathsToDelete.isNotEmpty()) &&
            (mode.isSelectingObjects() || mode == ToolMode.ARC_PATH) // allow instant arc-path deletion
        ) {
            deleteObjectsWithDependenciesColorsAndRegions(selection.indices)
            recordHistory()
        }
    }

    private inline fun deleteObjectsWithDependenciesColorsAndRegions(
        indicesToDelete: List<Ix>,
        crossinline animationInit: (Map<Ix, GCircleOrConcreteArcPath>) -> AppearanceAnimation? =
            { deletedCircles ->
                AppearanceAnimation.Exit(deletedCircles)
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
        val visibleDeleted = deletedIndices.filter { ix ->
            objects[ix] is GCircleOrConcreteArcPath && (canvasState.showPhantomObjects || ix !in phantoms)
        }
        if (visibleDeleted.isNotEmpty()) {
            deleteRegionsBoundBy(visibleDeleted.toSet())
            val ix2o = visibleDeleted.associateWith { objects[it] as GCircleOrConcreteArcPath }
            animationInit(ix2o)?.let { circleAnimation ->
                viewModelScope.launch {
                    animations.emit(circleAnimation)
                }
            }
        }
        objectModel.removeObjectsAt(deletedIndices)
        // NOTE: changedIndices are ArcPaths with null-ed vertices
        //  it may be sensible to remove those non-existent vertices altogether now
        // cuz we might've deleted some arc-path vertices
        objectModel.forceUpdate(changedIndices)
        objectModel.invalidate()
    }

    private fun deleteRegionsBoundBy(indices: Set<Ix>) {
        val everyBound = indices.containsAll(
            objects.filterIndices { it is CircleOrLine || it is ConcreteArcPath }
        )
        val oldRegions = regions.toList()
        if (everyBound) {
            regions = emptyList()
            updateCanvasState { it.copy(
                chessboardPattern =
                    if (it.chessboardPattern == ChessboardPattern.STARTS_COLORED)
                        ChessboardPattern.STARTS_TRANSPARENT
                    else it.chessboardPattern
            ) }
        } else { // not everything
            regions = oldRegions
                // to avoid stray chessboard selections
                .filterNot { (ins, _, _) ->
                    ins.isNotEmpty() && ins.minus(indices).isEmpty()
                }
                .map { (ins, outs, fillColor) ->
                    LogicalRegion(
                        insides = ins.minus(indices),
                        outsides = outs.minus(indices),
                        fillColor = fillColor
                    )
                }
                .filter { (ins, outs) -> ins.isNotEmpty() || outs.isNotEmpty() }
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
        if (mode.isSelectingObjects() && newMode.isSelectingObjects() && newMode != mode) {
            clearSelection()
        }
        if (newMode is ToolMode) {
            val firstArgType = newMode.signature.argTypes.firstOrNull()
            if (firstArgType?.let { Arg.Indices in it.possibleTypes } == true &&
                // we don't prompt to accept a singular GCircle
                (selection.arcPaths.isNotEmpty() || selection.gCircles.size > 1)
            ) {
                showSnackbarMessage(SnackbarMessage.ACT_ON_SELECTION_PROMPT)
            } else {
                // keep selection for a bit in case we now switch to another mode that
                // accepts selection as the first arg
            }
            when (newMode) {
                ToolMode.ARC_PATH -> {
                    clearSelection()
                    partialArgList = null
                }
                else -> {
                    updateCanvasState { it.copy(
                        showCircles = true
                    ) }
                    partialArgList = PartialArgList(newMode.signature, newMode.nonEqualityConditions)
                }
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

    fun isCloseEnoughToSelect(
        position1: Offset,
        position2: Offset,
        lowAccuracy: Boolean = false,
    ): Boolean {
        val d2 = (position2 - position1).getDistanceSquared()
        return if (lowAccuracy)
            d2 <= lowAccuracyTapRadius2
        else
            d2 <= tapRadius2
    }

    fun getPointsAround(
        absolutePosition: Offset,
        potentialIndices: Iterable<Ix> = objectModel.pointIndices,
        priorityTargets: Set<Ix> = emptySet(),
    ): List<Int> {
        return objects.indicesSortedBy(
            indices = potentialIndices,
            measurer = { o ->
                val point = o as? Point
                    ?: return@indicesSortedBy Double.POSITIVE_INFINITY
                point.distanceFrom(absolutePosition)
            },
            condition = { ix, distance ->
                distance <= tapRadius && (canvasState.showPhantomObjects || ix !in phantoms)
            },
            sortingPriority = { ix, distance ->
                val priority =
                    if (ix in priorityTargets) 100
                    else 1
                distance / priority
            }
        )
    }

    /** [getPointsAround] around [absolutePosition] while prioritizing free points */
    fun getPreferablyFreePointsAround(absolutePosition: Offset): List<Ix> {
        val closePoints = getPointsAround(
            absolutePosition = absolutePosition,
            priorityTargets = expressions.freeObjectsIndices
        )
        return closePoints
    }

    /**
     * @param[potentialIndices] to select from [objects],
     * [ImaginaryCircle]s are converted to real [Circle]s
     */
    private fun getCirclesAround(
        absolutePosition: Offset,
        potentialIndices: Iterable<Ix> = objectModel.circleOrLineIndices,
        priorityTargets: Set<Int> = emptySet(),
    ): List<Int> {
        return objects.indicesSortedBy(
            indices = potentialIndices,
            measurer = { o ->
                val circle = when (o) {
                    is Circle -> o
                    is Line -> o
                    is ImaginaryCircle -> o.toRealCircle()
                    else -> null
                }
                circle?.distanceFrom(absolutePosition) ?: Double.POSITIVE_INFINITY
            },
            condition = { ix, distance ->
                distance <= tapRadius && (canvasState.showPhantomObjects || ix !in phantoms)
            },
            sortingPriority = { ix, distance ->
                val priority =
                    if (ix in priorityTargets) 100
                    else 1
                distance / priority
            }
        )
    }

    /** [getCirclesAround] around [absolutePosition] while prioritizing free circles */
    fun getPreferablyFreeCirclesAround(absolutePosition: Offset): List<Ix> {
        val closeCircles = getCirclesAround(
            absolutePosition = absolutePosition,
            priorityTargets = expressions.freeObjectsIndices
        )
        return closeCircles
    }

    fun getArcPathsAround(
        absolutePosition: Offset,
        potentialIndices: Iterable<Ix> = objectModel.arcPathIndices,
    ): List<Ix> {
        return objects.indicesSortedBy(
            indices = potentialIndices,
            measurer = {
                (it as? ConcreteArcPath)?.distanceFrom(absolutePosition)
                    ?: Double.POSITIVE_INFINITY
            },
            condition = { _, distance -> distance <= tapRadius },
        )
    }

    fun getClosedArcPathsSurrounding(
        absolutePosition: Offset,
        potentialIndices: Iterable<Ix> = objectModel.arcPathIndices,
        hasToBeFilled: Boolean = false,
    ): List<Ix> {
        val notFilledIsOk = !hasToBeFilled
        return potentialIndices.filter { ix ->
            val concreteArcPath = objects[ix] as? ConcreteArcPath
            concreteArcPath != null &&
                concreteArcPath.isClosed &&
                (notFilledIsOk || styling[ix]?.fillColor != null) &&
                absolutePosition liesInside concreteArcPath
        }
    }

    fun getClosedArcPathSurrounding(
        absolutePosition: Offset,
        potentialIndices: Iterable<Ix> = objectModel.arcPathIndices,
        hasToBeFilled: Boolean = false,
    ): Ix? {
        return getClosedArcPathsSurrounding(absolutePosition, potentialIndices, hasToBeFilled)
            .maxWithOrNull(Comparator { ix1: Ix, ix2: Ix ->
                val filled1 = styling[ix1]?.fillColor != null
                val filled2 = styling[ix2]?.fillColor != null
                if (filled1) {
                    if (filled2) {
                        ix1.compareTo(ix2) // last index wins when both filled
                    } else {
                        +1
                    }
                } else {
                    if (filled2) {
                        -1
                    } else { // smallest area wins when both unfilled
                        val area1 = (objects[ix1] as? ConcreteArcPath)
                            ?.calculateVertexArea()
                            ?: Double.POSITIVE_INFINITY
                        val area2 = (objects[ix2] as? ConcreteArcPath)
                            ?.calculateVertexArea()
                            ?: Double.POSITIVE_INFINITY
                        -area1.compareTo(area2)
                    }
                }
            })
    }

    /** @return qualified/uncompressed in- and out- constraints */
    private fun getUncompressedRegionSurrounding(
        absolutePosition: Offset,
        bounds: List<Ix>? = null
    ): RegionConstraints {
        val delimiters = bounds ?:
        objectModel.circleOrLineIndices.filter { ix ->
            objects[ix] is CircleOrLine && ix !in phantoms
        }.plus(
            objectModel.arcPathIndices.filter { ix ->
                val o = objects[ix]
                o is ConcreteArcPath && o.isClosed && ix !in phantoms
            }
        )
        // NOTE: doesn't include circles that the point lies on
        val insides = delimiters.filter { ix ->
            val o = objects[ix] as? CircleOrLineOrConcreteArcPath
            o?.hasInside(absolutePosition) == true
        }
        val outsides = delimiters.filter { ix ->
            val o = objects[ix] as? CircleOrLineOrConcreteArcPath
            o?.hasOutside(absolutePosition) == true
        }
        return RegionConstraints(insides, outsides)
    }

    // NOTE: region boundaries get messed up when we alter a big structure like spiral
    /** @return (compressed region constraints, verbose/uncompressed/fully qualified constraints
     * involving all circles) surrounding clicked position
     */
    private fun getRegionSurrounding(
        absolutePosition: Offset,
        bounds: List<Ix>? = null
    ): Pair<CompressedRegionConstraints, RegionConstraints> {
        val fullConstraints = getUncompressedRegionSurrounding(absolutePosition, bounds)
        val compressedConstraints = fullConstraints.compressConstraints(objects)
//        println("compressed $fullConstraints -> $compressedConstraints")
        return Pair(compressedConstraints, fullConstraints)
    }

    /** @return `null` if no arc-path were altered, [Unit] otherwise */
    private fun refillClosedArcPathAt(
        absolutePosition: Offset,
        bounds: List<Ix>? = null,
    ): Unit? {
        val ix = if (bounds.isNullOrEmpty())
            getClosedArcPathSurrounding(absolutePosition)
        else
            getClosedArcPathSurrounding(absolutePosition, bounds)
        if (ix != null) {
            objectModel.updateStyle(ix) {
                it.copy(fillColor =
                    if (it.fillColor != regionColor) regionColor else null
                )
            }
            return Unit
        }
        return null
    }

    private fun refillRegionAt(
        absolutePosition: Offset,
        bounds: List<Ix>? = null,
    ) {
        val (constraints, fullConstraints) = getRegionSurrounding(absolutePosition, bounds)
        regions = RegionManipulationStrategy.updateRegionsAfterReselection(
            constraints = constraints,
            fullConstraints = fullConstraints,
            allRegions = regions,
            regionManipulationStrategy = regionManipulationStrategy,
            color = regionColor,
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
//                    (objectModel.pathCache[ix] ?: o.toPath()).getBounds()
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
        hug(objectModel.invalidations)
        val allColors = mutableListOf<Color>()
        for (region in regions) {
            if (region.borderColor != null)
                allColors.add(region.borderColor)
            allColors.add(region.fillColor)
        }
        for (style in styling.values) {
            if (style.borderColor != null)
                allColors.add(style.borderColor)
            if (style.fillColor != null)
                allColors.add(style.fillColor)
        }
        if (canvasState.chessboardPattern != ChessboardPattern.NONE)
            allColors.add(canvasState.chessboardColor)
        return allColors.sortedByFrequency()
    }

    /**
     * Try to snap [absolutePosition] to some existing object or their intersection.
     * Snap priority: points > circles > arc-paths
     */
    private fun snapped(
        absolutePosition: Offset,
        includePoints: Boolean = true,
        excludedIndices: Set<Ix> = emptySet(),
    ): PointSnapResult {
        val snapDistance = tapRadius.toDouble()
        val point = Point.fromOffset(absolutePosition)
        val excluded =
            if (canvasState.showPhantomObjects) excludedIndices
            else excludedIndices.union(phantoms)
        var snap: PointSnapResult
        if (includePoints) {
            snap = Snapping.snapPointToPoints(point, objects,
                snapTargets = objectModel.pointIndices.minus(excluded),
                snapDistance = snapDistance,
            )
            if (snap is PointSnapResult.Eq)
                return snap
        }
        if (canvasState.showCircles) {
            snap = Snapping.snapPointToCirclesOrLines(point, objects,
                snapTargets = objectModel.circleOrLineIndices.minus(excluded),
                snapDistance = snapDistance,
            )
            if (!snap.isFree)
                return snap
        }
        snap = Snapping.snapPointToArcPaths(point, objects,
            snapTargets = objectModel.arcPathIndices.minus(excluded),
            snapDistance = snapDistance,
        )
        if (snap is PointSnapResult.ArcPathIncidence)
            return snap
        return PointSnapResult.Free(point)
    }

    private fun snappedWithoutChildrenOrParents(
        absolutePosition: Offset,
        index: Ix,
    ): PointSnapResult {
        val allChildren: Set<Ix> = expressions.getAllChildren(index)
        val allParents = expressions.getAllParents(listOf(index))
        return snapped(absolutePosition,
            includePoints = Settings.ENABLE_POINT_TO_POINT_SNAPPING,
            excludedIndices = allChildren + allParents + setOf(index),
        )
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
                val ix1 = snapResult.circle1Index
                val ix2 = snapResult.circle2index
                val expr = Expr.Intersection(ix1, ix2)
                val possibleExistingIntersections =
                    expressions.findExistingIntersectionIndices(ix1, ix2)
                        .filter { objects[it] is Point }
                val closestIndex = possibleExistingIntersections.minByOrNull {
                    val p = objects[it] as Point
                    p.distanceFrom(point)
                }
                val intersectionSnapDistance = INTERSECTION_SNAP_FACTOR * tapRadius
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
                val (_, arcIndex, order) = concreteArcPath.project(
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
                    recordHistory()
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
        submode = Submode.RectangularSelect()
    }

    fun activateFlowSelect() {
        switchToMode(SelectionMode.Multiselect)
        clearSelection()
        submode = Submode.FlowSelect()
    }

    fun activateFlowFill() {
        switchToMode(SelectionMode.Region)
        submode = Submode.FlowFill()
    }

    fun forceSelectAll() {
        if (!mode.isSelectingObjects() || !canvasState.showCircles) { // more intuitive behavior
            // forces to select all instead of toggling
            clearSelection()
        }
        switchToCategory(Category.Multiselect)
        toggleSelectAll()
    }

    fun toggleSelectAll() {
        switchToMode(SelectionMode.Multiselect)
        updateCanvasState { it.copy(
            showCircles = true
        ) }
        val allCLPIndices = expressions.gCircleIndices.filter {
            objects[it] is CircleOrLineOrPoint
        }
        val allArcPathIndices = expressions.arcPathIndices.filter {
            objects[it] is ConcreteArcPath
        }
        val everythingIsSelected = selection.gCircles.containsAll(
            allCLPIndices - phantoms
        )
        selection =
            if (everythingIsSelected)
                Selection()
            else
                Selection(
                    // maybe select imaginary too
                    gCircles = allCLPIndices.filter { canvasState.showPhantomObjects || it !in phantoms },
                    arcPaths = allArcPathIndices.filter { canvasState.showPhantomObjects || it !in phantoms },
                )
    }

    fun toggleShowCircles() {
        updateCanvasState { it.copy(
            showCircles = !it.showCircles
        ) }
        if (!canvasState.showCircles && mode is ToolMode)
            switchToMode(SelectionMode.Drag)
        clearSelection()
    }

    fun togglePhantomObjects() {
        updateCanvasState { it.copy(
            showPhantomObjects = !it.showPhantomObjects
        ) }
        if (phantoms.isEmpty()) {
            showSnackbarMessage(SnackbarMessage.PHANTOM_OBJECT_EXPLANATION)
        }
    }

    fun toggleStereographicRotationMode() {
        if (mode == ViewMode.StereographicRotation) {
            submode = null
            switchToCategory(Category.Drag)
        } else {
            clearSelection()
            switchToMode(ViewMode.StereographicRotation)
            val sphereProjection = Circle(
                computeAbsoluteCenter() ?: Offset.Zero,
                // sphere radius == equator radius
                min(canvasState.canvasSize.width/2.0, canvasState.canvasSize.height/2.0)
            )
            submode = Submode.RotateStereographicSphere(
                sphereRadius = sphereProjection.radius,
                grabbedTarget = sphereProjection.center,
                south = sphereProjection.centerPoint,
                grid = generateSphereGrid(
                    sphereProjection,
                    angleStep = Submode.RotateStereographicSphere.GRID_ANGLE_STEP
                ),
            )
        }
    }

    fun hidePanel() {
        updateUiState { it.copy(
            showPanel = false
        ) }
    }

    fun hideUIFor30s() {
        if (uiState.showUI) {
            updateUiState { it.copy(
                showPanel = false
            ) }
            viewModelScope.launch {
                // MAYBE: also trigger fullscreen for desktop
                delay(30.seconds)
                updateUiState { it.copy(
                    showPanel = true
                ) }
            }
        }
    }

    fun toggleRestrictRegionsToSelection() {
        restrictRegionsToSelection = !restrictRegionsToSelection
    }

    fun cycleChessboardPattern() {
        updateCanvasState { it.copy(
            chessboardColor =
                if (it.chessboardPattern != ChessboardPattern.STARTS_TRANSPARENT)
                    regionColor // when new pattern is not none
                else it.chessboardColor,
            chessboardPattern = when (it.chessboardPattern) {
                ChessboardPattern.NONE -> ChessboardPattern.STARTS_COLORED
                ChessboardPattern.STARTS_COLORED -> ChessboardPattern.STARTS_TRANSPARENT
                ChessboardPattern.STARTS_TRANSPARENT -> ChessboardPattern.NONE
            },
        ) }
        recordHistory()
    }

    fun requestOpenFile() {
        openFileRequests.tryEmit(Unit)
    }

    fun requestSaveFileAs() {
        // we have to open SaveOptionsDialog first so that
        // SaveFileButton starts listening to SaveRequests
        updateUiState { it.copy(
            openedDialog = DialogType.SAVE_OPTIONS
        ) }
        viewModelScope.launch {
            // have to delay a bit for the dialog to open
            delay(200.milliseconds)
            // ts ugly tbh
            saveFileRequests.emit(SaveRequest.SAVE_AS)
        }
    }

    fun newBlank() {
        updateUiState { it.copy(
            openedDialog = DialogType.SAVE_PROMPT
        ) }
        queuedAction = Action.NEW_BLANK
    }

    fun concludeRegionColorPicker(colorPickerParameters: ColorPickerParameters) {
        updateUiState { it.copy(
            openedDialog = null
        ) }
        regionColor = colorPickerParameters.currentColor
        this.colorPickerParameters = colorPickerParameters
        switchToCategory(Category.Region)
    }

    fun concludeBorderColorPicker(colorPickerParameters: ColorPickerParameters) {
        val color = colorPickerParameters.currentColor
        for (ix in selection.indices) {
            objectModel.updateStyle(ix) {
                it.copy(borderColor = color)
            }
        }
        updateUiState { it.copy(
            openedDialog = null
        ) }
        this.colorPickerParameters = colorPickerParameters
        objectModel.invalidate()
        recordHistory()
    }

    fun concludeFillColorPicker(colorPickerParameters: ColorPickerParameters) {
        val color = colorPickerParameters.currentColor
        for (ix in selection.arcPaths) {
            objectModel.updateStyle(ix) {
                it.copy(
                    borderColor = if (it.borderColor == it.fillColor) color else it.borderColor,
                    fillColor = color,
                )
            }
        }
        updateUiState { it.copy(
            openedDialog = null
        ) }
        this.colorPickerParameters = colorPickerParameters
        objectModel.invalidate()
        recordHistory()
    }

    fun concludeBackgroundColorPicker(colorPickerParameters: ColorPickerParameters) {
        updateCanvasState { it.copy(
            backgroundColor = colorPickerParameters.currentColor
        ) }
        updateUiState { it.copy(
            openedDialog = null
        ) }
        this.colorPickerParameters = colorPickerParameters
        recordHistory()
    }

    fun setNewRegionColorToSelectedColorSplash(color: Color) {
        updateUiState { it.copy(
            openedDialog = null
        ) }
        regionColor = color
        switchToCategory(Category.Region)
    }

    fun dismissRegionColorPicker() {
        val tool = mode.tool
        val category = toolbarState.categories.firstOrNull { tool in it.tools }
        if (category != null) {
            selectCategory(category, togglePanel = true)
        }
        updateUiState { it.copy(
            toolbarState = it.toolbarState.copy(activeTool = tool),
            openedDialog = null
        ) }
    }

    fun getMostCommonBorderColorInSelection(): Color? {
        hug(objectModel.invalidations)
        return selection.indices
            .map { styling[it]?.borderColor }
            .mostCommonOf { it }
    }

    fun getMostCommonFillColorInSelection(): Color? {
        hug(objectModel.invalidations)
        return selection.arcPaths
            .map { styling[it]?.fillColor }
            .mostCommonOf { it }
    }

    // MAYBE: replace with select-all->delete in invisible-circles region manipulation mode
    fun deleteAllRegions() {
        updateCanvasState { it.copy(
            chessboardPattern = ChessboardPattern.NONE
        ) }
        regions = emptyList()
        recordHistory()
    }

    fun setRegionsManipulationStrategy(newStrategy: RegionManipulationStrategy) {
        regionManipulationStrategy = newStrategy
    }

    // MAYBE: axis-aligned cross centered at a point
    fun insertCenteredCross() {
        val (midX, midY) = canvasState.canvasSize.toSize()/2f
        val horizontalLine = Line.by2Points(
            absolute(Offset(0f, midY)),
            absolute(Offset(2*midX, midY)),
        )
        val verticalLine = Line.by2Points(
            absolute(Offset(midX, 0f)),
            absolute(Offset(midX, 2*midY)),
        )
        updateCanvasState { it.copy(
            showCircles = true
        ) }
        expressions.addFree()
        expressions.addFree()
        createNewGCircles(listOf(horizontalLine, verticalLine))
        switchToMode(SelectionMode.Multiselect) // idk it's weird
        val indices = listOf(objects.size - 2, objects.size - 1)
        selection = Selection(gCircles = indices)
        recordHistory()
    }

    fun scaleSelection(zoom: Float) {
        if (mode == ToolMode.ARC_PATH && partialArcPath != null) {
            // scale pArcPath? not sure
        } else {
            // weird history shenanigans... cuz we want to pin-record on the first zoom
            // action in a sequence
            val firstZoom = history.newContinuousChange(ContinuousChange.ZOOM)
            if (mode.isSelectingObjects() &&
                (canvasState.showCircles && selection.gCircles.isNotEmpty() || selection.arcPaths.isNotEmpty())
            ) {
                val rect = calculateSelectionRect()
                val focus =
                    if (rect == null || rect.minDimension >= 5_000)
                        computeAbsoluteCenter() ?: Offset.Zero
                    else rect.center
                transformWhatWeCan(selectedIndices,
                    focus = focus, zoom = zoom,
                    continuousChange = ContinuousChange.ZOOM
                )
            } else { // zoom everything
                val targets = objects.indices.toList()
                val center = computeAbsoluteCenter() ?: Offset.Zero
                val changedIndices = objectModel.transform(targets, focus = center, zoom = zoom)
                // zooming ignores concrete-arc-paths
                expressions.reEval() // overboard but w/e
                objectModel.syncDisplayObjects(objects.indices)
                objectModel.invalidatePositions()
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
        val indicesToFree = selection.gCircles
            .filter { objects[it] is CircleOrLineOrPoint? } // ignore imaginary circles
            .toSet()
            .plus(selection.arcPaths.flatMap { ix ->
                objectModel.getArcPath(ix)?.dependencies ?: emptySet()
            })
        for (ix in indicesToFree) {
            expressions.changeToFree(ix)
        }
        objectModel.invalidate()
        recordHistory()
    }

    fun dismissInputSubmode(recordHistory: Boolean = true) {
        if (submode is Submode.InputPopup) {
            when (submode) {
                is Submode.LabelInput -> {
                    for (ix in selection.gCircles)
                        objectModel.updateStyle(ix) {
                            it.copy(label =
                                if (it.label?.content.isNullOrBlank() == true) null
                                else it.label
                            )
                        }
                }
                else -> {}
            }
            submode = null
            objectModel.invalidate()
            if (recordHistory)
                recordHistory()
        }
    }

    fun setLabel(label: String?) {
        for (ix in selection.gCircles) {
            // idt we need to forget label shift when removing the label
            objectModel.updateStyle(ix) {
                it.copy(label =
                    if (label == null)
                        null
                    else
                        Styling.Label(label)
                )
            }
        }
        objectModel.invalidate()
    }

    fun setLineThickness(thickness: Float?) {
        for (ix in selection.indices) {
            objectModel.updateStyle(ix) { it.copy(lineThickness = thickness) }
        }
        // NOTE: slider can continuously invalidate, which isn't ideal for recompositions
        objectModel.invalidate()
    }

    private fun markSelectedObjectsAsPhantoms() {
        for (ix in selection.gCircles) {
            objectModel.updateStyle(ix) { it.copy(isPhantom = true) }
        }
        selection = selection.copy(gCircles = emptyList())
        // showPhantomObjects = false // i think this behavior is confuzzling
        objectModel.invalidate()
        recordHistory()
    }

    private fun unmarkSelectedObjectsAsPhantoms() {
        for (ix in selection.gCircles) {
            objectModel.updateStyle(ix) { it.copy(isPhantom = false) }
        }
        objectModel.invalidate()
        recordHistory()
    }

    private fun swapOrientationsInSelection() {
        val targets = selection.gCircles.filter { ix ->
            objects[ix] is CircleOrLine && isFree(ix)
        }
        if (targets.isEmpty()) {
            if (selection.gCircles.size == 1)
                showSnackbarMessage(SnackbarMessage.LOCKED_OBJECT_NOTICE)
            else if (selection.gCircles.size > 1)
                showSnackbarMessage(SnackbarMessage.LOCKED_OBJECTS_NOTICE)
        } else {
            objectModel.setDisplayObjectsWithConsequences(
                targets.associateWith { ix ->
                    val obj0 = objects[ix] as CircleOrLine
                    obj0.reversed()
                }
            )
            objectModel.invalidate()
            recordHistory()
        }
    }

    private fun findSiblingsAndParents(ix: Ix): List<Ix> {
        val expr = exprOf(ix) ?: return emptyList()
        val parents = expr.args
        val siblings = expressions.findExpr(expr)
        return siblings + parents
    }

    // might be useful for duplication with dependencies
    /** For each object in [selection].gCircles, add to selection its siblings and parents */
    private fun expandSelectionToFamily() {
        if (mode.isSelectingObjects()) {
            val familyMembers = selection.gCircles.flatMap { ix ->
                listOf(ix) + findSiblingsAndParents(ix)
            }.distinct()
            if (familyMembers.size > 1 && mode == SelectionMode.Drag) {
                switchToMode(SelectionMode.Multiselect)
            }
            selection = Selection(gCircles = familyMembers)
        }
    }

    private fun downSingleCircle(absolutePosition: Offset) {
        val circle = objects[selection.gCircles.single()]
        if (circle is Circle) {
            val radiusHandlePosition = circle.center + Offset(circle.radius.toFloat(), 0f)
            when {
                isCloseEnoughToSelect(radiusHandlePosition, absolutePosition, lowAccuracy = true) ->
                    submode = Submode.Scale(circle.center)
            }
        }
    }

    private fun downSeveralObjects(absolutePosition: Offset) {
        calculateSelectionRect()?.let { rect ->
            val scaleHandlePosition = rect.topRight
            val rotateHandlePosition = rect.bottomRight
            when {
                isCloseEnoughToSelect(scaleHandlePosition, absolutePosition, lowAccuracy = true) ->
                    submode = Submode.Scale(rect.center)
                isCloseEnoughToSelect(rotateHandlePosition, absolutePosition, lowAccuracy = true) -> {
                    submode = Submode.Rotate(rect.center)
                }
            }
        }
    }

    private fun tryGrabbingArcMidpoint(absolutePosition: Offset) {
        for (ix in selection.arcPaths) {
            val concreteArcPath = objects[ix] as? ConcreteArcPath ?: continue
            for (arcIndex in concreteArcPath.arcs.indices) {
                concreteArcPath.arcs[arcIndex].freeMidpoint?.let { midpoint ->
                    if (isCloseEnoughToSelect(midpoint.toOffset(), absolutePosition, lowAccuracy = true)) {
                        submode = Submode.GrabbedArcMidpoint(ix, arcIndex)
                    }
                }
            }
        }
    }

    private fun downDuringDrag(absolutePosition: Offset) {
        val selectedPointIndex = getPreferablyFreePointsAround(absolutePosition).firstOrNull()
        // select point > circle > arcpath
        selection = if (selectedPointIndex != null) {
            Selection(gCircles = listOf(selectedPointIndex))
        } else {
            val selectedCircleIndex = getPreferablyFreeCirclesAround(absolutePosition).firstOrNull()
            if (selectedCircleIndex != null) {
                Selection(gCircles = listOf(selectedCircleIndex))
            } else {
                val selectedArcPathIndex = getArcPathsAround(absolutePosition).firstOrNull()
                    ?: getClosedArcPathSurrounding(absolutePosition, hasToBeFilled = true)
                if (selectedArcPathIndex != null) {
                    Selection(arcPaths = listOf(selectedArcPathIndex))
                } else {
                    // we keep the previous selection in case we want to drag it
                    // but it can still be discarded in :0nTap
                    selection
                }
            }
        }
    }

    private fun downDuringRectangularSelect(absolutePosition: Offset) {
        val (corner1, corner2) = submode as Submode.RectangularSelect
        submode = if (corner1 == null) {
            Submode.RectangularSelect(absolutePosition)
        } else if (corner2 == null) {
            Submode.RectangularSelect(corner1, absolutePosition)
        } else {
            Submode.RectangularSelect(absolutePosition)
        }
    }

    private fun downDuringFlowSelect(absolutePosition: Offset) {
        val fullConstraints = getUncompressedRegionSurrounding(absolutePosition)
        submode = Submode.FlowSelect(lastConstraints = fullConstraints)
    }

    private fun downDuringFlowFill(absolutePosition: Offset) {
        val fullConstraints = getUncompressedRegionSurrounding(absolutePosition)
        submode = Submode.FlowFill(lastConstraints = fullConstraints)
        val selectedBounds = selection.indices.filter {
            val o = objects[it]
            o is CircleOrLine || o is ConcreteArcPath && o.isClosed
        }
        if (restrictRegionsToSelection && selectedBounds.isNotEmpty()) {
            refillRegionAt(absolutePosition, selectedBounds)
        } else {
            refillRegionAt(absolutePosition)
        }
    }

    private fun downArcPathPoint(absolutePosition: Offset) {
        val snap = snapped(absolutePosition)
        val arcPath = partialArcPath
        partialArcPath = if (arcPath == null) {
            PartialArcPath(
                vertices = listOf(PartialArcPath.Vertex(snap)),
                focus = PartialArcPath.Focus.Vertex(0),
            )
        } else {
            val vertexIndex = arcPath.vertices.indexOfFirst { vertex ->
                isCloseEnoughToSelect(vertex.point.toOffset(), absolutePosition)
            }
            if (vertexIndex != -1) {
                arcPath.copy(focus = PartialArcPath.Focus.Vertex(vertexIndex))
            } else {
                val arcIndex = arcPath.arcs.indexOfFirst { arc ->
                    isCloseEnoughToSelect(arc.middlePoint.toOffset(), absolutePosition)
                }
                if (arcIndex != -1) {
                    arcPath.copy(focus = PartialArcPath.Focus.MidPoint(arcIndex))
                } else {
                    arcPath.addNewVertexAndGrabIt(PartialArcPath.Vertex(snap))
                }
            }
        }
    }

    private fun downToolArg(absolutePosition: Offset) {
        val argList = partialArgList
        val nextType = argList?.nextArgType
        if (nextType != null) {
            val inInterpolationMode = mode == ToolMode.CIRCLE_OR_POINT_INTERPOLATION
            val inFastCenteredCircle =
                Settings.FAST_CENTERED_CIRCLE && mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS
            /** flags whether we already selected/found an object and there's no
             * more need to proceed further */
            var found = false
            var pointSnap: PointSnapResult? = null
            // try selecting an existing (indexed) point
            if (nextType.acceptsPointIndex) {
                pointSnap = snapped(absolutePosition, includePoints = mode != ToolMode.POINT)
                when (pointSnap) {
                    is PointSnapResult.Eq -> {
                        val newArg = Arg.PointIndex(pointSnap.pointIndex)
                        if (inFastCenteredCircle && argList.currentArg == null) {
                            partialArgList = argList
                                .addArg(newArg, confirmThisArg = true)
                                .addArg(Arg.PointXY(pointSnap.result), confirmThisArg = false)
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
            if (!found && nextType.acceptsCLI &&
                (inInterpolationMode entails (argList.currentArg?.type !is Arg.Type.Point))
            ) {
                getCirclesAround(absolutePosition).firstOrNull()?.let { ix ->
                    val newArg = Arg.IndexOf(ix, objects[ix] as GCircle)
                    // test non-equality conditions
                    if (argList.validateNewArg(newArg)) {
                        if (inFastCenteredCircle && argList.currentArg == null) {
                            pointSnap = snapped(absolutePosition, excludedIndices = setOf(ix))
                            partialArgList = argList
                                .addArg(newArg, confirmThisArg = true)
                                .addArg(Arg.PointXY(pointSnap.result), confirmThisArg = false)
                                .copy(lastSnap = pointSnap)
                        } else {
                            val confirm = !inInterpolationMode
                            partialArgList = argList.addArg(newArg, confirmThisArg = confirm)
                        }
                    }
                    found = true
                }
            }
            // try selecting a new point
            if (!found && nextType.acceptsPointXY) {
                val snap = pointSnap
                    ?: snapped(absolutePosition, includePoints = mode != ToolMode.POINT)
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
            if (!found && nextType.acceptsIndices) {
                val selectedPointIndex = getPointsAround(absolutePosition).firstOrNull()
                if (selectedPointIndex == null) {
                    val selectedCircleIndex = getCirclesAround(absolutePosition).firstOrNull()
                    if (selectedCircleIndex == null) {
                        val selectedArcPathIndex = getArcPathsAround(absolutePosition).firstOrNull()
                        // we don't select in-filled arc-paths here i think
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

    fun onDown(position: Offset) {
        if (history.newContinuousChange(null)) {
            recordHistory()
        }
        movementAfterDown = false
        val absolutePosition = absolute(position)
        if (canvasState.showCircles) { // TODO: allow arc-path selection when no circles shown
            when (handleConfig) {
                HandleConfig.SINGLE_CIRCLE ->
                    downSingleCircle(absolutePosition = absolutePosition)
                HandleConfig.SEVERAL_OBJECTS ->
                    downSeveralObjects(absolutePosition = absolutePosition)
                else -> {}
            }
            if (submode == null) {
                tryGrabbingArcMidpoint(absolutePosition = absolutePosition)
            }
            when (mode) {
                SelectionMode.Drag if (submode == null) ->
                    downDuringDrag(absolutePosition = absolutePosition)
                SelectionMode.Multiselect -> when (submode) {
                    is Submode.RectangularSelect ->
                        downDuringRectangularSelect(absolutePosition = absolutePosition)
                    is Submode.FlowSelect ->
                        downDuringFlowSelect(absolutePosition = absolutePosition)
                    else -> {}
                }
                SelectionMode.Region -> when (submode) {
                    is Submode.FlowFill ->
                        downDuringFlowFill(absolutePosition = absolutePosition)
                    else -> {}
                }
                ToolMode.ARC_PATH -> {
                    partialArcPath = partialArcPath?.unFocus()
                    if (submode == null) { // we might have grabbed an arc midpoint
                        clearSelection()
                        downArcPathPoint(absolutePosition = absolutePosition)
                    }
                }
                is ToolMode if (partialArgList?.isFull != true) ->
                    downToolArg(absolutePosition = absolutePosition)
                else -> {}
            }
        }
        // should work independent of circle visibility
        when (val sm = submode) {
            is Submode.RotateStereographicSphere ->
                submode = sm.copy(
                    grabbedTarget = absolutePosition,
                )
            else -> {}
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
        val infinityIndex = objectModel.getInfinityIndex()
            ?: createNewFreePoint(Point.CONFORMAL_INFINITY)
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
            objectModel.invalidate()
            recordHistory()
        }
    }

    private fun tapDuringDrag(absolutePosition: Offset) {
        // when multiple close candidates, show choice list
        // MAYBE: pass actionLabelTextStyle as an arg
        if (Settings.SHOW_SELECTION_CHOICES) {
            val selectablePoints = getPreferablyFreePointsAround(absolutePosition)
            var selectableCircles: List<Ix> = emptyList()
            var selectableArcPaths: List<Ix> = emptyList()
            if (selectablePoints.isNotEmpty()) {
                selection = Selection(gCircles = selectablePoints.take(1))
                highlightSelectionParents()
            } else {
                selectableCircles = getPreferablyFreeCirclesAround(absolutePosition)
                selectableArcPaths = getArcPathsAround(absolutePosition)
                if (selectableCircles.isNotEmpty()) {
                    selection = Selection(gCircles = selectableCircles.take(1))
                    highlightSelectionParents()
                } else {
                    if (selectableArcPaths.isEmpty())
                        selectableArcPaths = listOfNotNull(
                            getClosedArcPathSurrounding(absolutePosition, hasToBeFilled = true)
                        )
                    if (selectableArcPaths.isNotEmpty()) {
                        selection = Selection(arcPaths = selectableArcPaths.take(1))
                        highlightSelectionParents()
                    } else {
                        clearSelection()
                    }
                }
            }
            // selecting a point over circles/paths is unambiguous
            val selectionIsAmbiguous = selectablePoints.size != 1 &&
                selectablePoints.size + selectableCircles.size + selectableArcPaths.size > 1
            if (selectionIsAmbiguous) {
                submode = Submode.SelectionChoicesInput(
                    (selectablePoints + selectableCircles).mapNotNull { ix ->
                        val obj = (objects[ix] as? GCircle) ?: return@mapNotNull null
                        val color = styling[ix]?.borderColor
                        Submode.SelectionChoicesInput.Choice(
                            index = ix, objectOrArcPath = obj,
                            borderColor = color, fillColor = color,
                        )
                    } + selectableArcPaths.map { ix ->
                        val borderColor = styling[ix]?.borderColor
                        val fillColor = styling[ix]?.fillColor
                        Submode.SelectionChoicesInput.Choice(
                            index = ix, objectOrArcPath = null,
                            borderColor = borderColor, fillColor = fillColor,
                        )
                    }
                )
            }
        } else { // no selection choices
            val selectedPointIndex = getPreferablyFreePointsAround(absolutePosition).firstOrNull()
            if (selectedPointIndex != null) {
                selection = Selection(gCircles = listOf(selectedPointIndex))
                highlightSelectionParents()
            } else {
                val selectedCircleIndex = getPreferablyFreeCirclesAround(absolutePosition).firstOrNull()
                if (selectedCircleIndex != null) {
                    selection = Selection(gCircles = listOf(selectedCircleIndex))
                    highlightSelectionParents()
                } else {
                    val selectedArcPathIndex = getArcPathsAround(absolutePosition).firstOrNull()
                        ?: getClosedArcPathSurrounding(absolutePosition, hasToBeFilled = true)
                    if (selectedArcPathIndex != null) {
                        selection = Selection(arcPaths = listOf(selectedArcPathIndex))
                        highlightSelectionParents()
                    } else {
                        clearSelection()
                    }
                }
            }
        }
    }

    private fun tapDuringMultiselect(absolutePosition: Offset) {
        val selectedPointIndex = getPreferablyFreePointsAround(absolutePosition).firstOrNull()
        if (selectedPointIndex != null) {
            if (selectedPointIndex in selection.gCircles) {
                selection = selection.copy(gCircles = selection.gCircles - selectedPointIndex)
            } else {
                selection = selection.copy(gCircles = selection.gCircles + selectedPointIndex)
                highlightSelectionParents()
            }
        } else {
            val selectedCircleIndex = getPreferablyFreeCirclesAround(absolutePosition).firstOrNull()
            if (selectedCircleIndex != null) {
                if (selectedCircleIndex in selection.gCircles) {
                    selection = selection.copy(gCircles = selection.gCircles - selectedCircleIndex)
                } else {
                    selection = selection.copy(gCircles = selection.gCircles + selectedCircleIndex)
                    highlightSelectionParents()
                }
            } else {
                val selectedArcPathIndex = getArcPathsAround(absolutePosition).firstOrNull()
                    ?: getClosedArcPathSurrounding(absolutePosition, hasToBeFilled = true)
                if (selectedArcPathIndex != null) {
                    if (selectedArcPathIndex in selection.arcPaths) {
                        selection = selection.copy(
                            arcPaths = selection.arcPaths - selectedArcPathIndex
                        )
                    } else {
                        selection = selection.copy(
                            arcPaths = selection.arcPaths + selectedArcPathIndex
                        )
                        highlightSelectionParents()
                    }
                } else { // try to select bounding circles of the selected region
                    val (constraints, fullConstraints) = getRegionSurrounding(absolutePosition)
                    if (fullConstraints.insides.isEmpty()) {
                        // if we clicked outside of everything, toggle select all
                        toggleSelectAll()
                    } else {
                        val selectedBounds = selection.indices.filter { ix ->
                            val o = objects[ix]
                            o is CircleOrLine || o is ConcreteArcPath && o.isClosed
                        }
                        val largestInnerRegion = regions
                            .filter { r ->
                                constraints isTriviallyInside r || fullConstraints isTriviallyInside r
                            }.maxByOrNull { it.insides.size + it.outsides.size }
                        val bounds: Set<Ix> =
                            if (largestInnerRegion == null) // select bounds of a non-existent region
                                constraints.insides.toSet() + constraints.outsides
                            else
                                largestInnerRegion.insides + largestInnerRegion.outsides
                        if (bounds != selectedBounds.toSet()) {
                            selection = Selection(
                                gCircles = bounds.filter { objects[it] is GCircle },
                                arcPaths = bounds.filter { objects[it] is ConcreteArcPath },
                            )
                            highlightSelectionParents()
                        } else {
                            clearSelection()
                        }
                    }
                }
            }
        }
    }

    private fun tapDuringRegions(absolutePosition: Offset) {
        if (restrictRegionsToSelection && selection.isNotEmpty()) {
            val selectedBounds = selection.indices.filter { ix ->
                val o = objects[ix]
                o is CircleOrLine || o is ConcreteArcPath && o.isClosed
            }
//            refillClosedArcPathAt(absolutePosition, selectedClosedArcPaths)
            refillRegionAt(absolutePosition, selectedBounds)
        } else {
//            refillClosedArcPathAt(absolutePosition)
            refillRegionAt(absolutePosition)
        }
        objectModel.invalidate()
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
        } else if (canvasState.showCircles) { // select circle(s)/region
            val absolutePosition = absolute(position)
            when (mode) {
                SelectionMode.Drag ->
                    tapDuringDrag(absolutePosition = absolutePosition)
                SelectionMode.Multiselect ->
                    tapDuringMultiselect(absolutePosition = absolutePosition)
                SelectionMode.Region -> {
                    when (submode) {
                        is Submode.FlowFill -> {} // see :0nDown
                        else ->
                            tapDuringRegions(absolutePosition = absolutePosition)
                    }
                }
                ToolMode.ARC_PATH -> {
                    val pArcPath = partialArcPath
                    if (pArcPath != null && !pArcPath.isClosed && pArcPath.vertices.size >= 2 &&
                        isCloseEnoughToSelect(
                            pArcPath.vertices.first().point.toOffset(),
                            absolutePosition,
                        )
                    ) {
                        partialArcPath = pArcPath.connectLastToFirst()
//                        showSnackbarMessage(SnackbarMessage.COMPLETE_ARC_PATH_PROMPT)
                    }
                }
                else -> {}
            }
        }
    }

    fun selectFromChoices(indexAmongChoices: Int?) {
        (submode as? Submode.SelectionChoicesInput)?.let { sm ->
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

    private fun scaleSingleCircle(ix: Ix, absoluteCentroid: Offset, zoom: Float, sm: Submode.Scale) {
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

    private fun rotateSingleCircle(ix: Ix, absoluteCentroid: Offset, pan: Offset, sm: Submode.Rotate) {
        val center = sm.center
        val centerToCurrent = absoluteCentroid - center
        val centerToPreviousHandle = centerToCurrent - pan
        val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
        val newAngle = sm.angle + angle
        val snappedAngle =
            if (loadedSettings.enableAngleSnapping)
                Snapping.snapAngle(newAngle)
            else newAngle
        val angle1 = (snappedAngle - sm.snappedAngle).toFloat()
        transformWhatWeCan(listOf(ix), focus = center, rotationAngle = angle1)
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
    }

    private fun rotateSeveralCircles(targets: List<Ix>, absoluteCentroid: Offset, pan: Offset, sm: Submode.Rotate) {
        val center = sm.center
        val centerToCurrent = absoluteCentroid - center
        val centerToPreviousHandle = centerToCurrent - pan
        val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
        val newAngle = sm.angle + angle
        val snappedAngle =
            if (loadedSettings.enableAngleSnapping)
                Snapping.snapAngle(newAngle)
            else newAngle
        val angle1 = (snappedAngle - sm.snappedAngle).toFloat()
        transformWhatWeCan(targets, focus = sm.center, rotationAngle = angle1)
        submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
    }

    fun scaleViaSlider(newSliderPercentage: Float) {
        val sm = when (val sm0 = submode) {
            is Submode.ScaleViaSlider -> sm0
            else -> {
                val center =
                    calculateSelectionRect()?.center ?:
                    absolute(canvasState.canvasCenter)
                Submode.ScaleViaSlider(center)
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
        submode = Submode.Rotate(computeAbsoluteCenter() ?: Offset.Zero)
    }

    fun rotateViaHandle(newRotationAngle: Float) {
        when (val sm = submode) {
            is Submode.Rotate -> {
                val newAngle = newRotationAngle.toDouble()
                val snappedAngle =
                    if (loadedSettings.enableAngleSnapping)
                        Snapping.snapAngle(newAngle)
                    else newAngle
                val dAngle = (snappedAngle - sm.snappedAngle).toFloat()
                transformWhatWeCan(selectedIndices, focus = sm.center, rotationAngle = dAngle)
                submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
            }
            else -> {}
        }
    }

    fun finishHandleRotation() {
        submode = null
        recordHistory()
    }

    // dragging circle: move + scale radius & rotate [line]
    private fun dragCircle(
        absoluteCentroid: Offset,
        translation: Offset, zoom: Float, rotationAngle: Float
    ) {
        val selectedIndex = selection.gCircles.single()
        val circle = objects[selectedIndex] as? CircleOrLine
        if (loadedSettings.enableTangentSnapping && circle != null) {
            // TODO: snap to arc-path arcs
            val result0 = circle.transformed(translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
                as CircleOrLine
            val snapDistance = tapRadius.toDouble()/TAP_RADIUS_TO_TANGENTIAL_SNAP_DISTANCE_FACTOR
            val excludedIndices =
                setOf(selectedIndex) +
                (if (canvasState.showPhantomObjects) emptySet() else phantoms) +
                expressions.getAllChildren(selectedIndex) +
                expressions.getAllParents(listOf(selectedIndex))
            val center = computeAbsoluteCenter()
            val canvasState = canvasState
            val absoluteVisibilityRect =
                if (center != null && canvasState.canvasSize != IntSize.Zero) {
                    val halfWidth = canvasState.canvasHalfWidth
                    val halfHeight = canvasState.canvasHalfHeight
                    Rect(
                        center.x - halfWidth, center.y - halfHeight,
                        center.x + halfWidth, center.y + halfHeight,
                    )
                } else null
            val snap = Snapping.snapCircleToCirclesLinesOrPoints(result0, objects,
                snapTargets = objectModel.gCircleIndices.minus(
                    if (canvasState.showPhantomObjects)
                        excludedIndices
                    else
                        excludedIndices.union(phantoms)
                ),
                snapDistance = snapDistance,
                visibleRect = absoluteVisibilityRect,
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
                val newPoint = snappedWithoutChildrenOrParents(absoluteCentroid,
                    index = ix,
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
        val projectedPoint = carrier.project(point)
        val upscaledProjectedPoint = projectedPoint.upscale()
        // when we are dragging intersection of 2 free circles with IoC1 we don't want it to snap to them
        val snap = snappedWithoutChildrenOrParents(upscaledProjectedPoint.toOffset(),
            index = pointIndex,
        )
        val newPoint = when (snap) {
            is PointSnapResult.Intersection
                if (snap.circle1Index == carrierIndex || snap.circle2index == carrierIndex) ->
                    snap.result.downscale()
            else -> projectedPoint
        }
        val order = carrier.point2order(newPoint)
        val newExpr = Expr.Incidence(IncidenceParameters(order), carrierIndex)
        objectModel.changeExpr(pointIndex, newExpr).toSet()
        objectModel.invalidatePositions()
    }

    private fun slidePointAcrossArcPath(
        pointIndex: Ix,
        arcPathIndex: Ix,
        absolutePointerPosition: Offset,
    ) {
        val carrier = objectModel.downscaledObjects[arcPathIndex] as? ConcreteArcPath ?: return
        val point = Point.fromOffset(absolutePointerPosition).downscale()
        val (_, arcIndex, arcPercentage) = carrier.project(point)
        // no snapping
        val newExpr = Expr.ArcPathIncidence(
            ArcPathIncidenceParameters(arcIndex, arcPercentage),
            arcPathIndex,
        )
        objectModel.changeExpr(pointIndex, newExpr).toSet()
        objectModel.invalidatePositions()
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
            objects[it] is CircleOrLineOrPoint
        }.plus(
            selection.arcPaths
                .flatMap { objectModel.getArcPath(it)?.dependencies ?: emptySet() }
                .filter { objects[it] is Point }
        ).distinct()
        transformWhatWeCan(targets, translation = translation, focus = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
    }

    private fun dragGrabbedArcMidpoint(
        absoluteCentroid: Offset,
        sm: Submode.GrabbedArcMidpoint,
    ) {
        val arcPath = objectModel.getArcPath(sm.arcPathIndex) ?: return
        val newMidpoint = snapped(absoluteCentroid,
            excludedIndices = expressions.getAllChildren(sm.arcPathIndex) + setOf(sm.arcPathIndex)
        ).result
        val changedIndices = objectModel.modifyArcPath(
            sm.arcPathIndex,
            arcPath.moveArcMidpoint(objects,  sm.arcIndex, newMidpoint)
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
        sm: Submode.RotateStereographicSphere,
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
                when (val o = objectModel.downscaledObjects[ix]) {
                    is GCircle -> {
                        val newObject = rotor.applyTo(GeneralizedCircle.fromGCircle(o))
                            .asGCircle(o)
                        objectModel.setDownscaledObject(ix, newObject)
                    }
                    else -> {}
                }
            }
            expressions.adjustIncidentPointExpressions()
            expressions.reEval()
            objectModel.syncDisplayObjects(objects.indices)
            val newSouth = (rotor.applyTo(GeneralizedCircle.fromGCircle(
                sm.south.downscale()
            )).asGCircle(sm.south) as? Point)
                ?.upscale()
            val newGrid = sm.grid.mapNotNull { o ->
                (rotor.applyTo(GeneralizedCircle.fromGCircle(
                    o.downscale()
                )).asGCircle(o) as? CircleOrLine)
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
     * Wrapper around [ConformalObjectModel.transform] that adjusts [targets] based on [InversionOfControl].
     *
     * [ConformalObjectModel.transform] applies [translation];scaling;rotation
     * to [targets] (that are all assumed free).
     *
     * Scaling and rotation are w.r.t. fixed [focus] by the factor of
     * [zoom] and by [rotationAngle] degrees. If [focus] is [Offset.Unspecified] for
     * each circle choose its center, for each point -- itself, for each line -- screen center
     * projected onto it.
     * Includes [ConformalObjectModel.invalidatePositions] call.
     */
    private fun transformWhatWeCan(
        targets: List<Ix>,
        translation: Offset = Offset.Zero,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
        continuousChange: ContinuousChange? = null,
    ) {
        val actualTargets = mutableSetOf<Ix>()
        when (loadedSettings.inversionOfControl) {
            InversionOfControl.NONE ->
                targets.filterTo(actualTargets) { isFree(it) }
            InversionOfControl.LEVEL_1 -> {
                targets.flatMapTo(actualTargets) { targetIx ->
                    if (isFree(targetIx)) {
                        listOf(targetIx)
                    } else {
                        val parents = expressions.getImmediateParents(targetIx)
                        if (parents.all { isFree(it) })
                            parents
                        else emptyList()
                    }
                }
            }
            InversionOfControl.LEVEL_INFINITY -> {
                actualTargets.addAll(targets)
                actualTargets.addAll(expressions.getAllParents(targets))
            }
        }
        if (actualTargets.isEmpty()) {
            if (targets.size == 1) // not sure this is the right place for snackbar messages
                showSnackbarMessage(SnackbarMessage.LOCKED_OBJECT_NOTICE)
            else
                showSnackbarMessage(SnackbarMessage.LOCKED_OBJECTS_NOTICE)
        } else {
            val changedIndices = objectModel.transform(actualTargets.toList(), translation, focus, zoom, rotationAngle)
            objectModel.invalidatePositions()
            history.accumulateChangedLocations(
                objectIndices = changedIndices,
                // zoom can change point-line incidence
                expressionIndices = changedIndices,
                continuousChange = continuousChange,
            )
        }
    }

    private fun updateRectangleSelect(absolutePosition: Offset, sm: Submode.RectangularSelect) {
        val corner1 = sm.corner1
        val rect = Rect.fromCorners(corner1 ?: absolutePosition, absolutePosition)
        val selectables = objects.mapIndexed { ix, o ->
            if (o is GCircleOrConcreteArcPath && (canvasState.showPhantomObjects || ix !in phantoms)) o
            else null
        }
        val rectSelection = RectangleCollider.selectWithRectangle(selectables, rect)
        selection = Selection(
            gCircles = rectSelection.filter { objects[it] is GCircle },
            arcPaths = rectSelection.filter { objects[it] is ConcreteArcPath },
        )
        submode = Submode.RectangularSelect(corner1, absolutePosition)
    }

    private fun updateFlowSelect(absolutePosition: Offset, sm: Submode.FlowSelect) {
        val fullConstraints = getUncompressedRegionSurrounding(absolutePosition)
        if (sm.lastConstraints == null) {
            submode = Submode.FlowSelect(lastConstraints = fullConstraints)
        } else {
            val diff =
                (fullConstraints.insides.toSet() xor sm.lastConstraints.insides.toSet()) union
                (fullConstraints.outsides.toSet() xor sm.lastConstraints.outsides.toSet())
            val additional = diff.filter {
                it !in selection.indices && (canvasState.showPhantomObjects || it !in phantoms)
            }
            selection = Selection(
                gCircles = selection.gCircles + additional.filter { objects[it] is GCircle },
                arcPaths = selection.arcPaths + additional.filter { objects[it] is ConcreteArcPath },
            )
        }
    }

    private fun updateFlowFill(
        absolutePosition: Offset,
        selectedCircles: List<Ix>,
        sm: Submode.FlowFill
    ) {
        val fullConstraints = getUncompressedRegionSurrounding(absolutePosition)
        if (sm.lastConstraints == null) {
            submode = Submode.FlowFill(lastConstraints = fullConstraints)
        } else {
            var submode: Submode.FlowFill = sm
            if (sm.lastConstraints != fullConstraints) {
                submode = sm.copy(lastConstraints = fullConstraints)
                if (restrictRegionsToSelection && selectedCircles.isNotEmpty()) {
                    refillRegionAt(absolutePosition, selectedCircles)
                } else {
                    refillRegionAt(absolutePosition)
                }
            }
            this.submode = submode
        }
    }

    private fun updatePartialArcPathFocus(absolutePosition: Offset) {
        val snap = snapped(absolutePosition)
        partialArcPath = partialArcPath?.moveFocus(snap, snapDistance = tapRadius.toDouble())
    }

    /** @return whether a tool arg is actually updated */
    private fun tryUpdatingToolArg(absolutePosition: Offset): Boolean {
        val snap = snapped(absolutePosition, includePoints = mode != ToolMode.POINT)
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

    private fun moveAroundCanvas(
        translation: Offset,
        absoluteCentroid: Offset,
        zoom: Float,
        rotationAngle: Float,
    ) {
        if (zoom != 1.0f || rotationAngle != 0.0f) {
            val targets = objects.indices.toList()
//            val center = computeAbsoluteCenter() ?: Offset.Zero
            val changedIndices = objectModel.transform(
                targets = targets,
                focus = absoluteCentroid,
                zoom = zoom,
                rotationAngle = rotationAngle,
            )
            this.translation += translation // navigate canvas
            objectModel.forceUpdate(objectModel.arcPathIndices)
            // force-update arc-paths or recalc concrete arc-paths in objectModel.transform
            objectModel.pathCache.invalidateAll() // sadly have to do this cuz we use visibleRect in path construction
            objectModel.invalidatePositions()
            history.accumulateChangedLocations(
                objectIndices = changedIndices,
                // zoom can change point-line incidence
                expressionIndices = changedIndices,
                center = true,
            )
        } else { // only translation
            this.translation += translation // navigate canvas
            objectModel.pathCache.invalidateAll() // sadly have to do this cuz we use visibleRect in path construction
            objectModel.invalidatePositions()
        }
    }

    // MAYBE: handle key arrows as panning
    fun onPanZoomRotate(centroid: Offset, pan: Offset, zoom: Float, rotationAngle: Float) {
        movementAfterDown = true
        /** absolute cursor/pointer position/centroid */
        val absoluteCentroid = absolute(centroid)
        val selectedCircles = selection.gCircles.filter { objects[it] is CircleOrLine }
        val selectedPoints = selection.gCircles.filter { objects[it] is Point }
        when (val sm = submode) {
            is Submode.Scale -> when (handleConfig) {
                HandleConfig.SINGLE_CIRCLE ->
                    scaleSingleCircle(ix = selection.gCircles.single(), absoluteCentroid = absoluteCentroid, zoom = zoom, sm = sm)
                HandleConfig.SEVERAL_OBJECTS ->
                    scaleSeveralCircles(targets = selectedIndices, pan = pan)
                null -> {}
            }
            is Submode.Rotate -> when (handleConfig) {
                HandleConfig.SINGLE_CIRCLE ->
                    rotateSingleCircle(ix = selection.gCircles.single(), pan = pan, absoluteCentroid = absoluteCentroid, sm = sm)
                HandleConfig.SEVERAL_OBJECTS ->
                    rotateSeveralCircles(targets = selectedIndices, absoluteCentroid = absoluteCentroid, pan = pan, sm = sm)
                null -> {}
            }
            is Submode.GrabbedArcMidpoint ->
                dragGrabbedArcMidpoint(absoluteCentroid = absoluteCentroid, sm = sm)
            is Submode.RectangularSelect ->
                updateRectangleSelect(absolutePosition = absoluteCentroid, sm = sm)
            is Submode.FlowSelect ->
                updateFlowSelect(absolutePosition = absoluteCentroid, sm = sm)
            is Submode.FlowFill ->
                updateFlowFill(absolutePosition = absoluteCentroid, selectedCircles = selectedCircles, sm = sm)
            is Submode.RotateStereographicSphere ->
                stereographicallyRotateEverything(absolutePointerPosition = absoluteCentroid, sm = sm)
            null -> when (mode) {
                SelectionMode.Drag if selectedCircles.isNotEmpty() && canvasState.showCircles ->
                    dragCircle(absoluteCentroid = absoluteCentroid, translation = pan, zoom = zoom, rotationAngle = rotationAngle)
                SelectionMode.Drag if selectedPoints.isNotEmpty() && canvasState.showCircles ->
                    dragPoint(absoluteCentroid = absoluteCentroid, translation = pan)
                SelectionMode.Drag if selection.arcPaths.isNotEmpty() ->
                    dragArcPaths(absoluteCentroid = absoluteCentroid, translation = pan, zoom = zoom, rotationAngle = rotationAngle)
                SelectionMode.Multiselect if (
                    selectedCircles.isNotEmpty() && canvasState.showCircles || selectedPoints.isNotEmpty() || selection.arcPaths.isNotEmpty()
                ) ->
                    dragSelection(absoluteCentroid = absoluteCentroid, translation = pan, zoom = zoom, rotationAngle = rotationAngle)
                ToolMode.ARC_PATH ->
                    updatePartialArcPathFocus(absolutePosition = absoluteCentroid)
                else -> {
                    val toolArgIsUpdated = tryUpdatingToolArg(absolutePosition = absoluteCentroid)
                    if (!toolArgIsUpdated) {
                        moveAroundCanvas(translation = pan, absoluteCentroid = absoluteCentroid, zoom = zoom, rotationAngle = rotationAngle)
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
            val closeVertices = pArcPath.vertices.indices
                .minus(focus.vertexIndex)
                .filter { i ->
                    absolutePosition.minus(pArcPath.vertices[i].point.toOffset())
                        .getDistanceSquared() <= tapRadius2
                }.toSet()
            val nextVertexIndex = (focus.vertexIndex + 1).mod(pArcPath.vertices.size)
            val previousVertexIndex = (focus.vertexIndex - 1).mod(pArcPath.vertices.size)
            when {
                nextVertexIndex in closeVertices -> {
                    pArcPath = pArcPath.fuseSubsequentVertices(focus.vertexIndex)
//                    if (partialArcPath?.isClosed == false && pArcPath.isClosed)
//                        showSnackbarMessage(SnackbarMessage.COMPLETE_ARC_PATH_PROMPT)
                }
                previousVertexIndex in closeVertices -> {
                    pArcPath = pArcPath.fuseSubsequentVertices(previousVertexIndex)
//                    if (partialArcPath?.isClosed == false && pArcPath.isClosed)
//                        showSnackbarMessage(SnackbarMessage.COMPLETE_ARC_PATH_PROMPT)
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
                    val snap = snapped(absolute(visiblePosition),
                        includePoints = mode != ToolMode.POINT,
                    )
                    // we cant realize it here since for fast circles the first point already has been
                    // realized in 0nDown and we don't know yet if we moved far enough from it to
                    // create the second point
                    if (mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS &&
                        Settings.FAST_CENTERED_CIRCLE &&
                        args.size == 2
                    ) {
                        val centerPoint: CircleOrLineOrPoint =
                            when (val centerArg = args[0]) {
                                is Arg.Index -> objects[centerArg.index] as CircleOrLineOrPoint
                                is Arg.FixedPoint -> centerArg.toPoint()
                                else -> never(centerArg)
                            }
                        val secondPointIsTooClose = centerPoint.distanceFrom(snap.result) < 1e-3
                        if (secondPointIsTooClose) { // haxxz
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
        val (corner1, corner2) = submode as Submode.RectangularSelect
        if (visiblePosition != null && corner1 != null && corner2 != null) {
            val newCorner2 = absolute(visiblePosition)
            val rect = Rect.fromCorners(corner1, newCorner2)
            val selectables = objects.mapIndexed { ix, o ->
                if (canvasState.showPhantomObjects || ix !in phantoms) o else null
            }
            val rectSelection = RectangleCollider.selectWithRectangle(selectables, rect)
                .also {
                    println("rectangle selection -> $it")
                }
            selection = Selection(
                gCircles = rectSelection.filter { objects[it] is GCircle },
                arcPaths = rectSelection.filter { objects[it] is ConcreteArcPath },
            )
            submode = Submode.RectangularSelect(corner1, corner2)
        }
    }

    /** @param[position] `null` if cancelled/OOB */
    fun onUp(position: Offset?) {
        // history is recorded at the end of :0nUp
        when (mode) {
            SelectionMode.Drag -> {
                when (submode) {
                    null -> if (movementAfterDown && canvasState.showCircles) {
                        if (selection.gCircles.none { isFree(it) }) {
                            highlightSelectionParents()
                        } else if (selection.gCircles.size == 1 && objects[selection.gCircles.first()] is Point) {
                            // this point is implied to be free by the first if
                            val pointIndex = selection.gCircles.first()
                            val point = objects[pointIndex] as Point
                            // MAYBE: sticky mode instead, which would allow to snap to points
                            when (snappedWithoutChildrenOrParents(
                                point.toOffset(),
                                pointIndex,
                            )) {
                                is PointSnapResult.ArcPathIncidence,
                                is PointSnapResult.Incidence,
                                is PointSnapResult.Intersection -> {
                                    showSnackbarMessage(SnackbarMessage.ATTACH_POINT_PROMPT)
                                }
                                else -> {}
                            }
                        }
                    }
                    else -> {}
                }
            }
            SelectionMode.Multiselect -> {
                when (submode) {
                    is Submode.RectangularSelect ->
                        upRectangularSelect(visiblePosition = position)
                    is Submode.FlowSelect -> { // haxx
                        println("flow-select -> ${selection.gCircles}")
                        updateUiState { it.copy(
                            toolbarState = it.toolbarState.copy(activeTool = Tool.Multiselect)
                        ) }
                    }
                    null -> if (movementAfterDown && canvasState.showCircles) {
                        if (selection.gCircles.none { isFree(it) })
                            highlightSelectionParents()
                    }
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
            is Submode.FlowFill,
            is Submode.RotateStereographicSphere,
            is Submode.Rotate,
            is Submode.Scale,
            is Submode.ScaleViaSlider,
            is Submode.GrabbedArcMidpoint ->
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
            is Submode.Rotate,
            is Submode.Scale,
            is Submode.ScaleViaSlider,
            is Submode.FlowSelect,
            is Submode.GrabbedArcMidpoint ->
                submode = null
            is Submode.RectangularSelect ->
                submode = Submode.RectangularSelect()
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

    fun showSnackbarMessage(snackbarMessage: SnackbarMessage, vararg formatArgs: Any) {
        snackbarMessages.tryEmit(snackbarMessage to formatArgs)
    }

    fun onSnackbarAction(snackbarMessage: SnackbarMessage) {
        when (snackbarMessage) {
            SnackbarMessage.LOCKED_OBJECTS_NOTICE, SnackbarMessage.LOCKED_OBJECT_NOTICE ->
                toolAction(Tool.Detach)
            SnackbarMessage.ACT_ON_SELECTION_PROMPT ->
                setActiveSelectionAsToolArg()
            SnackbarMessage.COMPLETE_ARC_PATH_PROMPT ->
                toolAction(Tool.CompleteArcPath)
            SnackbarMessage.ATTACH_POINT_PROMPT ->
                attachSelectedPoint()
            else -> {}
        }
    }

    fun setActiveSelectionAsToolArg() {
        val argList = partialArgList
        val validState = toolbarState.activeTool.let { tool ->
            tool is Tool.MultiArg &&
            Arg.Indices in tool.signature.argTypes.first().possibleTypes &&
            selection.isNotEmpty() &&
            argList != null
        }
        if (!validState) { // in case snackbar prompt outlives validity
            println("Illegal state in setActiveSelectionAsToolArg(): tool = ${toolbarState.activeTool}, selection == $selection")
            return
        }
        partialArgList = argList?.addArg(
            Arg.Indices(selection.indices),
            confirmThisArg = true
        )
        if (partialArgList?.isFull == true) {
            completeToolMode()
        }
    }

    private fun attachSelectedPoint() {
        if (selection.gCircles.size != 1)
            return
        val pointIndex = selection.gCircles.first()
        val point = objects[pointIndex] as? Point ?: return
        when (val snap = snappedWithoutChildrenOrParents(point.toOffset(), pointIndex)) {
            // idk how to deal with Eq snap, like fuse points into one?
            is PointSnapResult.Intersection -> {
                objectModel.changeToIntersection(pointIndex,
                    carrier1Index = snap.circle1Index,
                    carrier2Index = snap.circle2index,
                )
                objectModel.invalidate()
                recordHistory()
            }
            is PointSnapResult.Incidence -> {
                objectModel.changeToIncidence(pointIndex, snap.circleIndex)
                objectModel.invalidate()
                recordHistory()
            }
            is PointSnapResult.ArcPathIncidence -> {
                objectModel.changeToArcPathIncidence(pointIndex, snap.arcPathIndex)
                objectModel.invalidate()
                recordHistory()
            }
            else -> {}
        }
    }

    // TODO: instead perma-highlight parents with 2 colors
    /** Signals locked state to the user with animation & snackbar message */
    private fun highlightSelectionParents() {
        val sel = selection.indices.toSet()
        val parents = sel.flatMap { ix ->
            if (isConstrained(ix)) emptyList() // exclude semi-free incident points
            else expressions.getImmediateParents(ix)
                .minus(sel)
        }
            .filter { ix ->
                (canvasState.showPhantomObjects || ix !in phantoms) &&
                objects[ix] is GCircleOrConcreteArcPath
            }.toMutableSet()
        if (parents.isNotEmpty()) {
            val protoArcPaths = selection.arcPaths.mapNotNull { ix ->
                val arcPath = objectModel.getArcPath(ix) ?: return@mapNotNull null
                if (arcPath.dependencies.any { ix ->
                    exprOf(ix) !is Expr.TransformLike
                })
                    return@mapNotNull null
                val protoVertices = arcPath.vertices.mapNotNull { vertexIndex ->
                    (exprOf(vertexIndex) as? Expr.TransformLike)?.target
                }
                protoVertices.mapNotNull { expressions.children[it] }
                    // common children of all proto vertices
                    .reduceOrNull { a, b -> a.intersect(b) }
                    // first arc-path common child
                    ?.firstOrNull { ix ->
                        exprOf(ix) is ArcPath && ix !in sel
                    }
                    ?.also { // if there is a proto arc-path, highlight all parents of vertices
                        parents += arcPath.vertices.flatMap {
                            expressions.getImmediateParents(it)
                                .filter { ix ->
                                    ix !in sel && (canvasState.showPhantomObjects || ix !in phantoms)
                                }
                        }
                    }
            }
            val ix2o = (parents + protoArcPaths)
                .mapNotNull { ix ->
                    objects[ix]?.let { ix to it }
                }.toMap()
            viewModelScope.launch {
                animations.emit(HighlightAnimation(ix2o))
            }
        }
    }

    fun switchToCategory(category: Category, togglePanel: Boolean = false) {
        val defaultTool = toolbarState.getDefaultTool(category)
        if (defaultTool == null) {
            selectCategory(category, togglePanel = togglePanel)
        } else {
            selectTool(defaultTool, togglePanel = togglePanel)
        }
    }

    private fun selectCategory(category: Category, togglePanel: Boolean = false) {
        updateUiState {
            val wasSelected = it.toolbarState.activeCategory == category
            val panelWasShown = it.showPanel
            val toolbarStateWithUpdatedCategory = it.toolbarState.copy(activeCategory = category)
            val panelNeedsToBeShown = toolbarStateWithUpdatedCategory.panelNeedsToBeShown
            it.copy(
                toolbarState = toolbarStateWithUpdatedCategory,
                showPanel =
                    if (togglePanel && wasSelected && panelNeedsToBeShown)
                        !panelWasShown
                    else panelNeedsToBeShown
            )
        }
    }

    fun selectTool(tool: Tool, togglePanel: Boolean = false) {
        // automatically complete unfinished arc-path, since losing it is annoying
        if (mode == ToolMode.ARC_PATH && partialArcPath != null) {
            completeArcPath()
        }
        val category: Category
        if (tool is Tool.AppliedColor) {
            category = Category.Colors
            updateUiState { it.copy(
                toolbarState = it.toolbarState.copy(activeTool = tool)
            ) }
        } else {
            category = toolbarState.getCategory(tool)
            updateUiState { it.copy(
                toolbarState = it.toolbarState
                    .updateDefault(category, tool)
                    .copy(activeTool = tool)
            ) }
        }
        selectCategory(category, togglePanel = togglePanel)
        toolAction(tool)
    }

    fun processKeyboardAction(action: KeyboardAction) {
//        println("processing $action")
        if (submode is Submode.InputPopup) {
            when (action) {
                KeyboardAction.CANCEL ->
                    submode = null
                else -> {}
            }
        } else if (uiState.openedDialog == null) {
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
            ToolMode.CIRCLE_INVERSION ->
                completeCircleInversion()
            ToolMode.CIRCLE_OR_POINT_INTERPOLATION ->
                exprAdjustmentManager.startCircleOrPointInterpolationParameterAdjustment()
            ToolMode.ROTATION ->
                exprAdjustmentManager.startRotationParameterAdjustment()
            ToolMode.BI_INVERSION ->
                exprAdjustmentManager.startBiInversionParameterAdjustment()
            ToolMode.LOXODROMIC_MOTION ->
                exprAdjustmentManager.startLoxodromicMotionParameterAdjustment()
            ToolMode.CIRCLE_EXTRAPOLATION -> updateUiState { it.copy(
                openedDialog = DialogType.CIRCLE_EXTRAPOLATION
            ) }
            // create
            ToolMode.CIRCLE_BY_CENTER_AND_RADIUS ->
                completeCircleByCenterAndRadius()
            ToolMode.CIRCLE_BY_3_POINTS ->
                completeCircleBy3Points()
            ToolMode.LINE_BY_2_POINTS ->
                completeLineBy2Points()
            ToolMode.POINT ->
                completePoint()
            ToolMode.CIRCLE_BY_PENCIL_AND_POINT ->
                completeCircleByPencilAndPoint()
            ToolMode.POLARITY_BY_CIRCLE_AND_LINE_OR_POINT ->
                completePolarityByCircleAndLineOrPoint()
            ToolMode.ARC_PATH ->
                throw IllegalStateException("Use separate function to route completion")
        }
    }

    fun cancelOngoingActions() {
        when (mode) { // reset mode
            is ToolMode -> {
                // double escape to go to Drag
                if (submode == null && partialArgList?.args?.isNotEmpty() != true && partialArcPath == null) {
                    switchToCategory(Category.Drag)
                } else {
                    if (submode is Submode.ExprAdjustment<*>) {
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
                when (submode) {
                    is Submode.ExprAdjustment<*> -> {
                        recordHistory()
                        undo() // contrived way to go to the before-adj savepoint
                    }
                    is Submode.RectangularSelect -> {
                        clearSelection()
                        submode = Submode.RectangularSelect()
                    }
                    else -> {
                        when (mode) {
                            SelectionMode.Multiselect -> {
                                if (selection.isNotEmpty()) {
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

    fun realizeArcPathMidpoints(arcPathIndex: Ix): ArcPath {
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
                        objectModel.updateStyle(ix) { it.copy(isPhantom = true) }
                        ix
                    } else existingMidpointIndex
                    ArcPath.Arc.By3Points(middlePointIndex = midpointIndex)
                }
            }
        }
        return arcPath.copy(arcs = arcs)
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
        val centerArg = argList.args[0]
        val pointArg = argList.args[1]
        require(centerArg is Arg.CircleIndex || centerArg is Arg.LineIndex || centerArg is Arg.PointIndex || centerArg is Arg.PointXY)
        require(pointArg is Arg.CircleIndex || pointArg is Arg.PointIndex || pointArg is Arg.PointXY)
        if (!Settings.ALWAYS_CREATE_ADDITIONAL_POINTS && centerArg is Arg.PointXY && pointArg is Arg.PointXY) {
            val newCircle = computeConcentricCircle(
                samePencilObject = centerArg.toPoint().downscale(),
                point = pointArg.toPoint().downscale(),
            )?.upscale()
            createNewGCircle(newCircle)
            expressions.addFree()
        } else {
            val realizedCenterArg = when (centerArg) {
                is Arg.Index -> centerArg.index
                is Arg.PointXY -> createNewFreePoint(centerArg.toPoint())
                else -> never(centerArg)
            }
            val realizedPointArg = when (pointArg) {
                is Arg.Index -> pointArg.index
                is Arg.PointXY -> createNewFreePoint(pointArg.toPoint())
                else -> never(pointArg)
            }
            val newCircle = expressions.addSoloExpr(
                Expr.CircleByCenterAndRadius(
                    center = realizedCenterArg,
                    radiusPoint = realizedPointArg,
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
        // i think circle by 3 implies we want to move these points later
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
            showSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
        }
        partialArgList = argList.copyEmpty()
        recordHistory()
    }

    private fun completeCircleByPencilAndPoint() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.CLIP }
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
            showSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
        }
        partialArgList = argList.copyEmpty()
        recordHistory()
    }

    private fun completeLineBy2Points() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.CLIP }
        val realized = args.map {
            when (it) {
                is Arg.Index -> it.index
                is Arg.FixedPoint -> createNewFreePoint(it.toPoint())
            }
        }
        val infinityIndex = objectModel.getInfinityIndex()
            ?: createNewFreePoint(Point.CONFORMAL_INFINITY)
        val newGCircle = expressions.addSoloExpr(
            Expr.CircleBy3Points(
                object1 = realized[0],
                object2 = realized[1],
                object3 = infinityIndex,
            ),
        ) as? GCircle
        createNewGCircle(newGCircle?.upscale())
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
        for (sourceIndex in gCircleSources) {
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleInversion(sourceIndex, invertingCircleIndex),
            ) as? GCircle
            val newIndex = objectModel.addDownscaledObject(newGCircle)
            copyStyle(sourceIndex, newIndex)
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
        val ix2o = newIndices.mapNotNull { ix ->
            objects[ix]?.let { ix to it }
        }.toMap()
        viewModelScope.launch {
            animations.emit(AppearanceAnimation.Entrance(ix2o))
        }
        objectModel.invalidate()
        recordHistory()
    }

    fun completeCircleExtrapolation(
        params: ExtrapolationParameters,
    ) {
        updateUiState { it.copy(
            openedDialog = null
        ) }
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
        updateUiState { it.copy(
            openedDialog = null
        ) }
        partialArgList = PartialArgList(
            Tool.CircleExtrapolation.signature,
            Tool.CircleExtrapolation.nonEqualityConditions
        )
    }

    private fun adjustInterpolationParameters(
        sm: Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
        parameters: InterpolationParameters,
    ): Submode.ExprAdjustment<Expr.Conformal.OneToMany> {
        val (expr, sourceIndex, occupiedIndices, reservedIndices) = sm.adjustables[0]
        val newExpr = expr.copyWithNewParameters(parameters)
        val (newIndices, newReservedIndices, newObjects, deleted, changed) = expressions.adjustMultiExpr(
            newExpr = newExpr,
            occupiedIndices = occupiedIndices,
            reservedIndices = reservedIndices,
        )
        objectModel.removeObjectsAt(deleted)
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
        objectModel.update(newIndices.toSet())
        objectModel.forceUpdate(changed)
        return Submode.ExprAdjustment(listOf(
            AdjustableExpr(newExpr, sourceIndex, newIndices, newReservedIndices)
        ))
    }

    private fun adjustTransformationParameters(
        sm: Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
        parameters: Parameters,
    ): Submode.ExprAdjustment<Expr.Conformal.OneToMany> {
        regions = regions.withoutElementsAt(sm.regions.toSet())
        for (arcPathAdjustable in sm.arcPathAdjustables) {
            objectModel.removeObjectsAt(arcPathAdjustable.occupiedIndices)
        }
        val newAdjustables = mutableListOf<AdjustableExpr<Expr.Conformal.OneToMany>>()
        /** object trajectories used to transfer regions */
        val source2trajectory1 = mutableListOf<Pair<Ix, List<Ix>>>()
        for ((expr, sourceIndex, occupiedIndices, reservedIndices) in sm.adjustables) {
            val newExpr = expr.copyWithNewParameters(parameters)
            val (newIndices, newReservedIndices, newObjects, deleted, changed) = expressions.adjustMultiExpr(
                newExpr = newExpr,
                occupiedIndices = occupiedIndices,
                reservedIndices = reservedIndices,
            )
            // NOTE: reserved indices will be generally non-contiguous
            // we have to cleanup abandoned indices
            val abandonedIndices = occupiedIndices.toSet() - newIndices.toSet()
            objectModel.removeObjectsAt(abandonedIndices + deleted)
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
            source2trajectory1.add(sourceIndex to newIndices)
            objectModel.update(newIndices.toSet())
            objectModel.forceUpdate(changed)
        }
        val newTrajectorySize = newAdjustables.first().size
        /** arc-path trajectories used to transfer regions */
        val source2trajectory2 = mutableListOf<Pair<Ix, List<Ix>>>()
        // NOTE: children of the source arc-path are handled properly still, they become
        //  dependent on source children, not on children of the trajectory arc-paths
        val newArcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
        for ((arcPathBlueprint, sourceArcPathIndex, occupiedIndices, reservedIndices) in sm.arcPathAdjustables) {
            val newArcPaths = List(newTrajectorySize) { trajectoryStage ->
                arcPathBlueprint.reIndex { adjustableIndex ->
                    newAdjustables[adjustableIndex].occupiedIndices[trajectoryStage]
                }
            }
            val (newIndices, newReservedIndices, newObjects, deleted, changed) =
                expressions.adjustArcPathBlueprint(newArcPaths,
                    occupiedIndices, reservedIndices
                )
            val abandonedIndices = occupiedIndices.toSet() - newIndices.toSet()
            objectModel.removeObjectsAt(abandonedIndices + deleted)
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
            source2trajectory2.add(sourceArcPathIndex to newIndices)
            objectModel.update(newIndices.toSet())
            objectModel.forceUpdate(changed)
        }
        val source2trajectory: List<Pair<Ix, List<Ix>>> = if (
            parameters is LoxodromicMotionParameters &&
            defaultLoxodromicMotionParameters.bidirectional &&
            source2trajectory1.size.mod(2) == 0 &&
            source2trajectory2.size.mod(2) == 0
        ) {
            // NOTE: assumption: bidirectional spiral adjustables must be laid out as {t^i}; {t^-i}
            // s2t structure is
            // t1^+1 .. t1^+n; t2^+1 .. t2^+n; ... tm^+1 .. tm^+n;
            // t1^-1 .. t1^-n; t2^-1 .. t2^-n; ... tm^-1 .. tm^-n;
            // or alternatively,
            // adjustables = [[forward trajectories], [backward trajectories]]
            val halfSize1 = source2trajectory1.size.div(2)
            val halfSize2 = source2trajectory2.size.div(2)
            //  we have to do this to copy regions properly both forward and backward
            val forwardSource2trajectory =
                source2trajectory1.take(halfSize1) + source2trajectory2.take(halfSize2)
            val backwardSource2trajectory =
                source2trajectory1.drop(halfSize1) + source2trajectory2.drop(halfSize2)
            val source2fullTrajectory = forwardSource2trajectory.zip(
                backwardSource2trajectory
            ) { (sourceIndex1, forwardTrajectory), (sourceIndex2, backwardTrajectory) ->
                require(sourceIndex1 == sourceIndex2)
                // the order of indices within full trajectory doesn't matter,
                // only that it is consistent across all of them
                sourceIndex1 to (backwardTrajectory + forwardTrajectory)
            }
            source2fullTrajectory
        } else {
            source2trajectory1 + source2trajectory2
        }
        val affectedRegions: List<Int> = copySourceRegionsOntoTrajectories(source2trajectory)
        return Submode.ExprAdjustment(
            adjustables = newAdjustables,
            arcPathAdjustables = newArcPathAdjustables,
            regions = affectedRegions,
        )
    }

    /** When in [Submode.ExprAdjustment], changes [submode]'s [Expr]s' parameters to
     * [parameters] and updates corresponding [objects] */
    @Suppress("UNCHECKED_CAST")
    fun adjustExprParameters(parameters: Parameters) {
        val sm = submode
        if (sm is Submode.ExprAdjustment<*> && parameters != sm.parameters) {
            submode = when (parameters) {
                is InterpolationParameters -> // single adjustable expr case
                    adjustInterpolationParameters(
                        sm as Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
                        parameters
                    )
                // multiple adjustable exprs
                is RotationParameters,
                is BiInversionParameters,
                is LoxodromicMotionParameters ->
                    adjustTransformationParameters(
                        sm as Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
                        parameters
                    )
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
            // NOTE: continuous invalidations from slider, not ideal for recompositions
//            objectModel.invalidate()
            objectModel.invalidatePositions()
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
            is Submode.ExprAdjustment<*> -> {
                when (val parameters = sm.parameters) {
                    is InterpolationParameters ->
                        defaultInterpolationParameters = DefaultInterpolationParameters(parameters)
                    is RotationParameters ->
                        defaultRotationParameters = DefaultRotationParameters(parameters)
                    is BiInversionParameters ->
                        defaultBiInversionParameters = DefaultBiInversionParameters(parameters)
                    is LoxodromicMotionParameters -> {
                        defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(
                            parameters,
                            bidirectional = defaultLoxodromicMotionParameters.bidirectional
                        )
                    }
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
            is Submode.ExprAdjustment<*> -> {
                val outputs =
                    sm.adjustables.flatMap { it.occupiedIndices } +
                    sm.arcPathAdjustables.flatMap { it.occupiedIndices }
                deleteObjectsWithDependenciesColorsAndRegions(
                    outputs,
                    animationInit = { null },
                )
            }
            else -> {}
        }
        submode = null
    }

    fun updateLoxodromicBidirectionality(bidirectional: Boolean) {
        val sm = submode
        if (sm is Submode.ExprAdjustment<*>) {
            when (sm.parameters) {
                is LoxodromicMotionParameters -> {
                    defaultLoxodromicMotionParameters = defaultLoxodromicMotionParameters.copy(
                        bidirectional = bidirectional,
                    )
                    regions = regions.withoutElementsAt(sm.regions.toSet())
                    deleteObjectsWithDependenciesColorsAndRegions(
                        indicesToDelete =
                            sm.adjustables.flatMap { it.occupiedIndices } +
                            sm.arcPathAdjustables.flatMap { it.occupiedIndices }
                        ,
                        animationInit = { null },
                    )
                    // NOTE: this leaves a LOT of unused nulls
                    exprAdjustmentManager.setupLoxodromicSpiral(bidirectional)
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
        // TODO: init SubMode.ToolResultPostprocessing
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
        updateUiState { it.copy(
            openedDialog = null
        ) }
        adjustExprParameters(parameters)
        confirmAdjustedParameters()
    }

    fun confirmCurrentAction() {
        when (mode) {
            ToolMode.ARC_PATH ->
                completeArcPath()
            else -> when (submode) {
                is Submode.ExprAdjustment<*> ->
                    confirmAdjustedParameters()
                // Enter for some reason simulates button click on the focused button...
                // which breaks this
                is Submode.RectangularSelect ->
                    submode = null
                else -> {}
            }
        }
    }

    fun onSaveFinished(saveResult: SaveResult) {
        updateUiState { it.copy(
            openedDialog = null
        ) }
        when (saveResult) {
            is SaveResult.Success -> {
                saveConfig = saveResult.asSaveConfig()
                showSnackbarMessage(
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
                showSnackbarMessage(
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
        updateUiState { it.copy(
            openedDialog = null
        ) }
        queuedAction = null
    }

    fun setBlendSettings(newRegionsOpacity: Float, newRegionsBlendModeType: BlendModeType) {
        updateCanvasState { it.copy(
            regionsOpacity = newRegionsOpacity,
            regionsBlendModeType = newRegionsBlendModeType,
        ) }
        updateUiState { it.copy(
            openedDialog = null
        ) }
    }

    // context: pArgList is full and we are in submode
    fun openDetailsDialog() {
        updateUiState { it.copy(
            openedDialog = when (mode) {
                ToolMode.CIRCLE_OR_POINT_INTERPOLATION -> DialogType.CIRCLE_OR_POINT_INTERPOLATION
                ToolMode.CIRCLE_EXTRAPOLATION -> DialogType.CIRCLE_EXTRAPOLATION
                ToolMode.BI_INVERSION -> DialogType.BI_INVERSION
                ToolMode.LOXODROMIC_MOTION -> DialogType.LOXODROMIC_MOTION
                else -> when (val sm = submode) {
                    is Submode.ExprAdjustment<*> ->
                        when (sm.parameters) {
                            is InterpolationParameters -> DialogType.CIRCLE_OR_POINT_INTERPOLATION
                            is RotationParameters -> DialogType.ROTATION
                            is BiInversionParameters -> DialogType.BI_INVERSION
                            is LoxodromicMotionParameters -> DialogType.LOXODROMIC_MOTION
                            else -> null
                        }
                    else -> null
                }
            }
        ) }
    }

    fun toolAction(tool: Tool) {
//        println("toolAction($tool)")
        when (tool) {
            Tool.Undo -> undo()
            Tool.Redo -> redo()
            Tool.SaveCluster -> updateUiState { it.copy(
                openedDialog = DialogType.SAVE_OPTIONS
            ) }
            Tool.Drag -> switchToMode(SelectionMode.Drag)
            Tool.Multiselect -> {
                if (mode == SelectionMode.Multiselect)
                    clearSelection()
                switchToMode(SelectionMode.Multiselect)
            }
            Tool.RectangularSelect -> activateRectangularSelect()
            Tool.FlowSelect -> activateFlowSelect()
            Tool.ToggleSelectAll -> toggleSelectAll()
            Tool.Region -> switchToMode(SelectionMode.Region)
            Tool.FlowFill -> activateFlowFill()
            Tool.FillChessboardPattern -> cycleChessboardPattern()
            Tool.RestrictRegionToSelection -> toggleRestrictRegionsToSelection()
            Tool.DeleteAllParts -> deleteAllRegions()
            Tool.BlendSettings -> updateUiState { it.copy(
                openedDialog = DialogType.BLEND_SETTINGS
            ) }
            Tool.ToggleObjects -> toggleShowCircles()
            Tool.TogglePhantoms -> togglePhantomObjects()
            Tool.HideUI -> hideUIFor30s()
            Tool.ToggleDirectionArrows -> updateCanvasState { it.copy(
                showDirectionArrows = !it.showDirectionArrows
            ) }
            // TODO: 2 options: solid color or external image
            Tool.AddBackgroundImage -> updateUiState { it.copy(
                openedDialog = DialogType.BACKGROUND_COLOR_PICKER
            ) }
            Tool.StereographicRotation -> toggleStereographicRotationMode()
            Tool.InsertCenteredCross -> insertCenteredCross()
            Tool.CompleteArcPath -> completeArcPath()
            Tool.Palette -> updateUiState { it.copy(
                openedDialog = DialogType.REGION_FILL_COLOR_PICKER
            ) }
            Tool.Expand -> scaleSelection(HUD_ZOOM_INCREMENT)
            Tool.Shrink -> scaleSelection(1/HUD_ZOOM_INCREMENT)
            Tool.Detach -> detachEverySelectedObject()
            Tool.SwapDirection -> swapOrientationsInSelection()
            Tool.MarkAsPhantoms ->
                if (toolPredicate(tool))
                    markSelectedObjectsAsPhantoms()
                else unmarkSelectedObjectsAsPhantoms()
            Tool.Duplicate -> duplicateSelection()
            Tool.BorderColor, Tool.PointColor -> updateUiState { it.copy(
                openedDialog = DialogType.BORDER_COLOR_PICKER
            ) }
            Tool.FillColor -> updateUiState { it.copy(
                openedDialog = DialogType.FILL_COLOR_PICKER
            ) }
            Tool.SetLineThickness -> submode = Submode.LineThicknessInput
            Tool.SetLabel -> submode = Submode.LabelInput
            Tool.Delete -> deleteSelection()
            is Tool.AppliedColor -> setNewRegionColorToSelectedColorSplash(tool.color)
            is Tool.MultiArg -> switchToMode(ToolMode.correspondingTo(tool))
            is Tool.CustomAction -> {} // custom, platform-dependent handlers for open/save
            Tool.DetailedAdjustment -> openDetailsDialog()
            Tool.AdjustExpr -> exprAdjustmentManager.startExprAdjustmentOfSelection()
            Tool.InBetween -> {} // unused, potentially updateParams(...)
            Tool.ReverseDirection -> {}
            Tool.BidirectionalSpiral -> {}
            Tool.InfinitePoint -> addInfinitePointArg()
            Tool.MovePointToInfinity -> movePointToInfinity()
        }
    }

    /** Is [tool] enabled? */
    fun toolPredicate(tool: Tool): Boolean {
        hug(objectModel.invalidations)
        return when (tool) {
            Tool.Drag ->
                mode == SelectionMode.Drag
            Tool.Multiselect ->
                mode == SelectionMode.Multiselect &&
                submodeType != Submode.Type.FLOW_SELECT &&
                submodeType != Submode.Type.RECTANGULAR_SELECT
            Tool.RectangularSelect ->
                mode == SelectionMode.Multiselect &&
                submodeType == Submode.Type.RECTANGULAR_SELECT
            Tool.FlowSelect ->
                mode == SelectionMode.Multiselect &&
                submodeType == Submode.Type.FLOW_SELECT
            Tool.ToggleSelectAll ->
                selection.gCircles.containsAll(
                    objects.filterIndices { it is CircleOrLineOrPoint }
                )
            Tool.Region ->
                mode == SelectionMode.Region &&
                submodeType != Submode.Type.FLOW_FILL
            Tool.FlowFill ->
                mode == SelectionMode.Region &&
                submodeType == Submode.Type.FLOW_FILL
            Tool.FillChessboardPattern ->
                canvasState.chessboardPattern != ChessboardPattern.NONE
            Tool.RestrictRegionToSelection ->
                restrictRegionsToSelection
            Tool.StereographicRotation ->
                mode == ViewMode.StereographicRotation
            Tool.ToggleObjects ->
                canvasState.showCircles
            Tool.TogglePhantoms ->
                canvasState.showPhantomObjects
            Tool.ToggleDirectionArrows ->
                canvasState.showDirectionArrows
            Tool.MarkAsPhantoms ->
                selection.gCircles.none { it in phantoms }
            Tool.InfinitePoint -> // whether to prompt infinite-point input
                showInfinitePoint
            Tool.MovePointToInfinity -> {
                // without changing selection, the only way to change the predicate is
                //  after applying move-to-infinity or on detachment.
                selection.gCircles.singleOrNull()?.let { ix ->
                    val o = objects[ix]
                    val expr = exprOf(ix)
                    o is Point && o != Point.CONFORMAL_INFINITY &&
                    (expr == null || expr is Expr.Incidence && objects[expr.carrier] is Line)
                } == true
            }
            Tool.SetLabel ->
                submodeType == Submode.Type.LABEL_INPUT
            Tool.SetLineThickness ->
                submodeType == Submode.Type.LINE_THICKNESS_INPUT
            is Tool.MultiArg ->
                mode == ToolMode.correspondingTo(tool)
            else -> true
        }
    }

    /** alternative enabled, mainly for 3-state buttons */
    fun toolAlternativePredicate(tool: Tool): Boolean =
        when (tool) {
            Tool.FillChessboardPattern ->
                canvasState.chessboardPattern == ChessboardPattern.STARTS_TRANSPARENT
            else -> false
        }

    // NOTE: downscaling each arg for eval is an extreme performance bottleneck (4 - 15 times)
    fun GCircle.downscale(): GCircle = scaled00(DOWNSCALING_FACTOR)
    fun GCircle.upscale(): GCircle = scaled00(UPSCALING_FACTOR)
    fun CircleOrLine.downscale(): CircleOrLine = scaled00(DOWNSCALING_FACTOR)
    fun CircleOrLine.upscale(): CircleOrLine = scaled00(UPSCALING_FACTOR)
    fun Point.downscale(): Point = scaled00(DOWNSCALING_FACTOR)
    fun Point.upscale(): Point = scaled00(UPSCALING_FACTOR)

    private fun saveState(): SaveState {
        val center = computeAbsoluteCenter() ?: Offset.Zero
        // NOTE: it's important to copy mutable collections
        val size = min(objects.size, expressions.expressions.size)
        return SaveState(
            objects = objectModel.displayObjects.take(size).toList(),
            expressions = expressions.expressions.filterKeys { it < size }.toMap(),
            styling = objectModel.styling.filterKeys { it < size },
            regions = regions.mapNotNull { region ->
                val insides = region.insides.filter { it < size }.toSet()
                val outsides = region.outsides.filter { it < size }.toSet()
                if (insides.isEmpty() && outsides.isEmpty())
                    null
                else
                    region.copy(insides = insides, outsides = outsides)
            },
            backgroundColor = canvasState.backgroundColor,
            chessboardPattern = canvasState.chessboardPattern,
            chessboardColor = canvasState.chessboardColor,
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
            if (Settings.RESTORE_LAST_STATE_ON_LOAD) {
                // NOTE: can crash when the underlying format changes
                val saveState = runCatching { platform.autosaveStore.get() }
                    .onFailure {
                        println("VM.restoreFromDisk: failed to retrieve autosave")
                        it.printStackTrace()
                    }
                    .getOrNull()
                if (saveState != null) {
                    restoreFromState(saveState)
                } else {
                    println("fallback to last VM.state")
                    val vmState = runCatching { platform.lastStateStore.get() }
                        .onFailure {
                            println("VM.restoreFromDisk: failed to retrieve last state")
                            it.printStackTrace()
                        }
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
            runCatching { platform.settingsStore.get() }
                .onFailure {
                    println("VM.restoreFromDisk: failed to retrieve settings")
                    it.printStackTrace()
                }
                .getOrNull()?.also { settings ->
                    loadSettings(settings)
                }
            if (Settings.RESTORE_LAST_STATE_ON_LOAD) {
                runCatching { platform.historyStore.get() }
                    .onFailure {
                        println("VM.restoreFromDisk: failed to retrieve history")
                        it.printStackTrace()
                    }
                    .getOrNull()?.also { historyState ->
                        history = historyState.load(undoIsEnabled, redoIsEnabled)
                    }
            }
            viewModelScope.launch {
                // settings can be updated externally from the SettingsScreen
                platform.settingsStore.updates.collect { settings ->
                    if (settings != null) {
                        loadSettings(settings)
                    }
                }
            }
            restoration.update { ProgressState.COMPLETED }
        }
    }

    fun loadSettings(settings: Settings) {
        loadedSettings = settings
        updateCanvasState { it.copy(
            showDirectionArrows = settings.showDirectionArrows,
            regionsOpacity = settings.regionsOpacity,
            regionsBlendModeType = settings.regionsBlendModeType,
        ) }
        colorPickerParameters = colorPickerParameters.copy(savedColors = settings.savedColors)
        defaultInterpolationParameters = settings.defaultInterpolationParameters
        defaultRotationParameters = settings.defaultRotationParameters
        defaultBiInversionParameters = settings.defaultBiInversionParameters
        defaultLoxodromicMotionParameters = settings.defaultLoxodromicMotionParameters
        saveConfig = saveConfig.copy(directory = settings.saveDirectory)
        updateUiState { it.copy(
            toolbarState = it.toolbarState.copy(categoryDefaultIndices = settings.categoryDefaultIndices)
        ) }
        switchToCategory(toolbarState.activeCategory)
    }

    private fun restoreFromState(state: SaveState) {
        if (!mode.isSelectingObjects()) {
            switchToMode(SelectionMode.Drag)
        }
        loadState(state)
        resetHistory()
    }

    // NOTE: migrated to SaveState, this is left for compatibility with previous auto-saves
    private fun restoreFromVMState(state: State) {
        loadNewConstellation(state.constellation)
        centerizeTo(state.centerX, state.centerY)
        val switchToMultiselect = state.selection.size > 1 && selection.gCircles.size <= 1
        selection = Selection(gCircles = state.selection)
        state.regionColor?.let {
            regionColor = it
        }
        updateCanvasState { it.copy(
            chessboardColor = state.chessboardColor ?: it.chessboardColor,
            chessboardPattern = state.chessboardPattern,
        ) }
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
            val presentHistoryState = history.save()
            val currentSettings = getCurrentSettings()
//            println("caching state ${presentState.expressions}")
            platform.saveState(presentState)
            platform.saveSettings(currentSettings)
            platform.saveHistory(presentHistoryState)
            cachingInProgress.update { false }
            println("cached.")
        }
    }

    private suspend fun autosaveEvery5Minutes() {
        withContext(Dispatchers.Default) {
            while (true) {
                delay(5.minutes)
                cacheState()
            }
        }
    }

    @OptIn(ExperimentalKStoreApi::class)
    private fun getCurrentSettings(): Settings {
        // we dont want to call suspend store.get here
        val settings = getPlatform().settingsStore.cached ?: loadedSettings
        return settings.copy(
            regionsOpacity = canvasState.regionsOpacity,
            regionsBlendModeType = canvasState.regionsBlendModeType,
            savedColors = colorPickerParameters.savedColors,
            defaultInterpolationParameters = defaultInterpolationParameters,
            defaultRotationParameters = defaultRotationParameters,
            defaultBiInversionParameters = defaultBiInversionParameters,
            defaultLoxodromicMotionParameters = defaultLoxodromicMotionParameters,
            categoryDefaultIndices = toolbarState.categoryDefaultIndices,
            saveDirectory = saveConfig.directory,
            showDirectionArrows = canvasState.showDirectionArrows,
        )
    }

    // TODO: fully migrate to SaveState eventually
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
        const val INTERSECTION_SNAP_FACTOR = 1.5
        const val TAP_RADIUS_TO_TANGENTIAL_SNAP_DISTANCE_FACTOR = 7.0
        const val AUTOSAVE_EVERY_5_MINUTES = true
        // NOTE: changing this factor breaks all line-incident points (scale-dependence)
        /** [Double] arithmetic is best in range that is closer to 0 */
        const val UPSCALING_FACTOR = ConformalObjectModel.UPSCALING_FACTOR
        const val DOWNSCALING_FACTOR = ConformalObjectModel.DOWNSCALING_FACTOR

        const val TWO_FINGER_TAP_FOR_UNDO = true // Android-only
        /** When several objects are close enough to the tap position,
         * show the list of them to choose from */

        fun sliderPercentageDeltaToZoom(percentageDelta: Float): Float =
            MAX_SLIDER_ZOOM.pow(2*percentageDelta)
    }
}
