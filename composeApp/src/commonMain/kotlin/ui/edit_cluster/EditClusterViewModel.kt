package ui.edit_cluster

import androidx.compose.animation.core.snap
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import data.Cluster
import data.OffsetSerializer
import data.OldCluster
import data.PartialArgList
import data.compressPartToEssentials
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GeneralizedCircle
import data.geometry.Line
import data.geometry.Point
import data.io.Ddc
import data.io.OldDdc
import data.io.cluster2svg
import data.io.parseDdc
import data.io.parseOldDdc
import domain.angleDeg
import getPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import ui.theme.DodeclustersColors
import ui.tools.EditClusterCategory
import ui.tools.EditClusterTool
import kotlin.math.abs
import kotlin.math.pow

/** circle index in vm.circles or cluster.circles */
typealias Ix = Int

// NOTE: waiting for decompose 3.0-stable for a real VM impl
// MAYBE: use UiState functional pattern instead this mess
// this class is obviously too big
@Stable
class EditClusterViewModel(
    /** NOT a viewModelScope, just a rememberCS from the screen composable */
    private val coroutineScope: CoroutineScope,
    cluster: Cluster = Cluster.SAMPLE
) {
    var mode: Mode by mutableStateOf(SelectionMode.Drag)
        private set
    // XYPoint uses absolute positioning
    var partialArgList: PartialArgList? by mutableStateOf(null)
        private set
    val circles = mutableStateListOf(*cluster.circles.toTypedArray())
    var _points by mutableStateOf(emptyList<Point>())
        private set
    val parts = mutableStateListOf(*cluster.parts.toTypedArray())
    /** indices of selected circles */
    val selection = mutableStateListOf<Ix>() // MAYBE: when circles are hidden select parts instead

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
    var showPromptToSetActiveSelectionAsToolArg by mutableStateOf(false)
        private set

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
    var invertChessboard by mutableStateOf(false)
        private set

    val circleSelectionIsActive by derivedStateOf {
        showCircles && selection.isNotEmpty() && mode.isSelectingCircles()
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

    private val _circleAnimations = MutableSharedFlow<CircleAnimation>()
    val circleAnimations = _circleAnimations.asSharedFlow()

    var showColorPickerDialog by mutableStateOf(false)
    var showCircleInterpolationDialog by mutableStateOf(false)
    var showCircleExtrapolationDialog by mutableStateOf(false)

    var canvasSize by mutableStateOf(IntSize.Zero) // used when saving best-center
    private val selectionControlsPositions by derivedStateOf {
        SelectionControlsPositions(canvasSize)
    }
    // MAYBE: keep track of the center instead
    var translation by mutableStateOf(Offset.Zero) // pre-scale offset
//    val scale = mutableStateOf(1f)

    /** min tap/grab distance to select an object */
    private var tapDistance = TAP_DISTANCE

    fun setEpsilon(density: Density) {
        with (density) {
            tapDistance = TAP_DISTANCE.dp.toPx()
        }
    }

    // navigation
    fun saveAndGoBack() {}
    fun cancelAndGoBack() {}

    fun saveAsYaml(name: String = Ddc.DEFAULT_NAME): String {
        val cluster = Cluster(
            circles.toList(), parts.toList()
        )
        var ddc = Ddc(cluster).copy(name = name)
        computeAbsoluteCenter()?.let { center ->
            ddc = ddc.copy(bestCenterX = center.x, bestCenterY = center.y)
        }
        return ddc.encode()
    }

    fun exportAsSvg(name: String = Ddc.DEFAULT_NAME): String {
        val cluster = Cluster(
            circles.toList(), parts.toList()
        )
        val start = absolute(Offset.Zero)
        return cluster2svg(
            cluster,
            backgroundColor = null,
            start.x, start.y,
            canvasSize.width.toFloat(), canvasSize.height.toFloat()
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

    fun saveAsJson(): String {
        val cluster = Cluster(
            circles.toList(), parts.toList(), filled = true
        )
        return Json.encodeToString(Cluster.serializer(), cluster)
    }

    fun loadFromJson(json: String) {
        try {
            val permissiveJson = Json {
                isLenient = true
                ignoreUnknownKeys = true // enables backward compatibility to a certain level
            }
            val cluster = permissiveJson.decodeFromString(OldCluster.serializer(), json)
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
        displayChessboardPattern = false
        translation = Offset.Zero
        selection.clear()
        parts.clear()
        circles.clear()
        circles.addAll(cluster.circles)
        parts.addAll(cluster.parts)
        // reset history on load
        history.clear()
        undoIsEnabled = history.undoIsEnabled
        redoIsEnabled = history.redoIsEnabled
    }

    fun undo() {
        history.undo()
        undoIsEnabled = history.undoIsEnabled
        redoIsEnabled = history.redoIsEnabled
    }

    fun redo() {
        history.redo()
        undoIsEnabled = history.undoIsEnabled
        redoIsEnabled = history.redoIsEnabled
    }

    private fun loadUiState(state: UiState) {
        submode = SubMode.None
        selection.clear()
        parts.clear()
        circles.clear()
        circles.addAll(state.circles)
        parts.addAll(state.parts)
        selection.clear() // switch can populate it
        selection.addAll(state.selection)
    }

    /** Use BEFORE modifying the state by the [command]!
     * let s_i := history[[i]], c_i := commands[[i]]
     * s0 (aka original) -> c0 -> s1 -> c1 -> s2 ... */
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
        if (command != Command.ROTATE)
            submode.let {
                if (it is SubMode.Rotate)
                    submode = SubMode.None
            }
    }

    fun createNewCircles(
        newCircles: List<CircleOrLine>,
    ) {
        val validNewCircles = newCircles.filter { newCircle ->
            newCircle is Circle && newCircle.radius > 0.0 || newCircle is Line
        }
        if (validNewCircles.isNotEmpty()) {
            recordCommand(Command.CREATE, unique = true)
            showCircles = true
            val prevSize = circles.size
            circles.addAll(validNewCircles)
            selection.clear()
            selection.addAll(prevSize until circles.size)
            coroutineScope.launch {
                _circleAnimations.emit(
                    CircleAnimation.Entrance(validNewCircles)
                )
            }
        }
    }

    fun duplicateCircles() {
        if (mode.isSelectingCircles()) {
            recordCommand(Command.DUPLICATE, selection)
            val copiedCircles = selection.map { circles[it] } // preserves selection order
            val oldSize = circles.size
            circles.addAll(copiedCircles)
            val newIndices = oldSize until oldSize + copiedCircles.size
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

    private fun copyParts(
        oldIndices: List<Ix>,
        newIndices: List<Ix>
    ) {
        require(oldIndices.size == newIndices.size)
        val old2new = oldIndices.zip(newIndices).toMap()
        val newParts = parts.filter {
            oldIndices.containsAll(it.insides) && oldIndices.containsAll(it.outsides)
        }.map { part ->
            Cluster.Part(
                insides = part.insides.map { old2new[it]!! }.toSet(),
                outsides = part.outsides.map { old2new[it]!! }.toSet(),
                fillColor = part.fillColor
            )
        }
        parts.addAll(newParts)
    }

    fun deleteCircles() {
        fun reindexingMap(originalIndices: IntRange, deletedIndices: Set<Ix>): Map<Ix, Ix> {
            val re = mutableMapOf<Ix, Ix>()
            var shift = 0
            for (ix in originalIndices) {
                if (ix in deletedIndices)
                    shift += 1
                else
                    re[ix] = ix - shift
            }
            return re
        }
        if (mode.isSelectingCircles()) {
            recordCommand(Command.DELETE, unique = true)
            val whatsGone = selection.toSet()
            val deletedCircles = whatsGone.map { circles[it] }
            val whatsLeft = circles.filterIndexed { ix, _ -> ix !in whatsGone }
            val oldParts = parts.toList()
            val reindexing = reindexingMap(circles.indices, whatsGone)
            selection.clear()
            parts.clear()
            circles.clear()
            circles.addAll(whatsLeft)
            parts.addAll(
                oldParts
                    // to avoid stray chessboard selections
                    .filterNot { (ins, _, _) -> ins.isNotEmpty() && ins.minus(whatsGone).isEmpty() }
                    .map { (ins, outs, fillColor) ->
                        Cluster.Part(
                            insides = ins.minus(whatsGone).map { reindexing[it]!! }.toSet(),
                            outsides = outs.minus(whatsGone).map { reindexing[it]!! }.toSet(),
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

    fun switchToMode(newMode: Mode) {
        // NOTE: these altering shortcuts are unused for now so that they don't confuse category-expand buttons
        if (selection.size > 1 && newMode == SelectionMode.Drag)
            selection.clear()
        if (newMode is ToolMode) {
            if (selection.size > 1 &&
                newMode.signature.argTypes.first() == PartialArgList.ArgType.SelectedCircles
            ) {
                showPromptToSetActiveSelectionAsToolArg = true
            } else {
                selection.clear()
            }
            showCircles = true
            partialArgList = PartialArgList(newMode.signature)
        } else {
            partialArgList = null
        }
        mode = newMode
        submode = SubMode.None
    }

    fun absolute(visiblePosition: Offset): Offset =
        visiblePosition - translation

    fun visible(position: Offset): Offset =
        position + translation

    fun isCloseEnoughToSelect(absolutePosition: Offset, visiblePosition: Offset, lowAccuracy: Boolean = false): Boolean {
        val position = absolute(visiblePosition)
        return (absolutePosition - position).getDistance() <= tapDistance * (if (lowAccuracy) LOW_ACCURACY_FACTOR else 1f)
    }

    fun selectPoint(targets: List<Offset>, visiblePosition: Offset): Ix? {
        val position = absolute(visiblePosition)
        return targets.mapIndexed { ix, offset -> ix to (offset - position).getDistance() }
            .filter { (_, distance) -> distance <= tapDistance }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (ix, _) -> ix }
    }

    fun selectCircle(targets: List<CircleOrLine>, visiblePosition: Offset): Ix? {
        val position = absolute(visiblePosition)
        return targets.mapIndexed { ix, circle ->
            ix to circle.distanceFrom(position)
        }
            .filter { (_, distance) -> distance <= tapDistance }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (ix, _) -> ix }
            ?.also { println("select circle #$it") }
    }

    fun reselectCircleAt(visiblePosition: Offset) {
        selectCircle(circles, visiblePosition)?.let { ix ->
            selection.clear()
            selection.add(ix)
        } ?: selection.clear()
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
            .filter { ix -> circles[ix].hasInside(position) }
        val outs = delimiters
            .filter { ix -> circles[ix].hasOutside(position) }
        // NOTE: these do not take into account more complex "intersection is always inside x" type relationships
        val excessiveIns = ins.filter { inJ -> // NOTE: tbh idt these can occur naturally
            val circle = circles[inJ]
            ins.any { otherIn ->
                otherIn != inJ && circles[otherIn] isInside circle // we only leave the smallest 'in'
            } || outs.any { otherOut ->
                circle isInside circles[otherOut] // if an 'in' isInside an 'out' it is empty
            }
        }
        val excessiveOuts = outs.filter { outJ ->
            val circle = circles[outJ]
            outs.any { otherOut ->
                otherOut != outJ && circle isInside circles[otherOut] // we only leave the biggest 'out'
            } || ins.any { otherIn ->
                circle isOutside circles[otherIn] // if an 'out' isOutside an 'in' it is empty
            }
        }
        val sievedIns = ins.minus(excessiveIns.toSet())
        val sievedOuts = outs.minus(excessiveOuts.toSet())
        val (essentialInsIxs, essentialOutsIxs) =
            compressPartToEssentials(sievedIns.map { circles[it] }, sievedOuts.map { circles[it] })
        val essentialIns = essentialInsIxs.map { sievedIns[it] }
        val essentialOuts = essentialOutsIxs.map { sievedOuts[it] }
        val part0 = Cluster.Part(ins.toSet(), outs.toSet(), regionColor)
        val part = Cluster.Part(
//            insides = sievedIns.toSet(),
//            outsides = sievedOuts.toSet(),
            insides = essentialIns.toSet(),
            outsides = essentialOuts.toSet(),
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
        if (!selection.containsAll(circles.indices.toSet())) {
            selection.clear()
            selection.addAll(circles.indices)
        } else {
            selection.clear()
        }
    }

    fun toggleShowCircles() {
        showCircles = !showCircles
        if (!showCircles && mode is ToolMode)
            switchToMode(SelectionMode.Drag)
    }

    fun toggleRestrictRegionsToSelection() {
        restrictRegionsToSelection = !restrictRegionsToSelection
    }

    fun applyChessboardPatter() {
        if (!displayChessboardPattern) {
            displayChessboardPattern = true
            invertChessboard = false
        } else if (!invertChessboard) {
            invertChessboard = true
        } else {
            displayChessboardPattern = false
            invertChessboard = false
        }
    }

    fun selectRegionColor(color: Color) {
        showColorPickerDialog = false
        regionColor = color
        selectTool(EditClusterTool.Region)
//        showPanel = true
    }
    
    // TODO: in the future replace with select-all->delete in invisible-circles part manipulation mode
    fun deleteAllParts() {
        recordCommand(Command.DELETE, unique = true)
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
                tool.signature.argTypes.first() == PartialArgList.ArgType.SelectedCircles &&
                selection.isNotEmpty()
            )
        }
        partialArgList = partialArgList!!.addArg(
            PartialArgList.Arg.SelectedCircles(selection.toList()),
            confirmThisArg = true
        )
        cancelSelectionAsToolArgPrompt()
        if (partialArgList!!.isFull)
            completeToolMode()
    }

    fun insertCenteredCross() {
        recordCommand(Command.CREATE, unique = true)
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
        circles.add(horizontalLine)
        circles.add(verticalLine)
        switchToMode(SelectionMode.Multiselect)
        selection.clear()
        selection.addAll(listOf(circles.size - 2, circles.size - 1))
        coroutineScope.launch { // NOTE: this animation looks bad for lines
            _circleAnimations.emit(
                CircleAnimation.Entrance(listOf(horizontalLine, verticalLine))
            )
        }
    }

    fun scaleSelection(zoom: Float) {
        if (circleSelectionIsActive)
            when (selection.size) {
                0 -> Unit
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
                            absolute(Offset(canvasSize.width/2f, canvasSize.height/2f))
                        else rect.center
                    for (ix in selection) {
                        circles[ix] = circles[ix].scale(center, zoom)
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
                            args = partialArgList!!.args.dropLast(1)
                        )
                    }
                else -> {}
            }
        }
    }

    fun onUp(visiblePosition: Offset?) {
        cancelSelectionAsToolArgPrompt()
        when (mode) {
            is ToolMode -> {
                // we only confirm args in onUp, they are created in onDown etc.
                val args = partialArgList!! // ToolMode implies non-null partialArgList
                val newArg = when (args.currentArg) {
                    is PartialArgList.Arg.XYPoint -> visiblePosition?.let {
                        PartialArgList.Arg.XYPoint.fromOffset(absolute(it))
                    }
                    is PartialArgList.Arg.CircleIndex -> null
                    is PartialArgList.Arg.SelectedCircles -> null
                    is PartialArgList.Arg.GeneralizedCircle -> visiblePosition?.let {
                        if (args.currentArg.gCircle is Point)
                            PartialArgList.Arg.GeneralizedCircle(Point.fromOffset(absolute(it)))
                        else null
                    }
                    null -> null // in case prev onDown failed to select anything
                }
                partialArgList = if (newArg == null)
                    args.copy(lastArgIsConfirmed = true)
                else
                    args.updateCurrentArg(newArg, confirmThisArg = true)
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
                val previouslySelected = selection.firstOrNull()
                reselectCircleAt(visiblePosition)
                if (previouslySelected != null && selection.isEmpty()) // this line requires deselecting first to navigate around canvas
                    selection.add(previouslySelected)
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
                    when (partialArgList!!.nextArgType) {
                        PartialArgList.ArgType.XYPoint -> {
                            if (FAST_CENTERED_CIRCLE && mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS && partialArgList!!.currentArg == null) {
                                val newArg = PartialArgList.Arg.XYPoint.fromOffset(absolute(visiblePosition))
                                partialArgList = partialArgList!!
                                    .addArg(newArg, confirmThisArg = true)
                                    .addArg(newArg, confirmThisArg = true)
                            } else {
                                val newArg = PartialArgList.Arg.XYPoint.fromOffset(absolute(visiblePosition))
                                partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = false)
                            }
                        }
                        PartialArgList.ArgType.CircleIndex -> {
                            selectCircle(circles, visiblePosition)?.let { circleIndex ->
                                val newArg = PartialArgList.Arg.CircleIndex(circleIndex)
                                if (partialArgList!!.currentArg != newArg)
                                    partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = true)
                            }
                        }
                        PartialArgList.ArgType.SelectedCircles -> {
                            selectCircle(circles, visiblePosition)?.let { circleIndex ->
                                val newArg = PartialArgList.Arg.SelectedCircles(listOf(circleIndex))
                                if (partialArgList!!.currentArg != newArg)
                                    partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = true)
                            }
                        }
                        PartialArgList.ArgType.GeneralizedCircle -> {
                            val circleIndex = selectCircle(circles, visiblePosition)
                            if (circleIndex != null) {
                                val newArg = PartialArgList.Arg.GeneralizedCircle(circles[circleIndex])
                                if (partialArgList!!.currentArg != newArg)
                                    partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = false)
                            } else {
                                val newArg = PartialArgList.Arg.GeneralizedCircle(
                                    Point.fromOffset(absolute(visiblePosition))
                                )
                                partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = false)
                            }
                        }
                        else -> {}
                    }
                }
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
                            }
                        }
                        is SubMode.ScaleViaSlider -> {
                            val newPercentage = selectionControlsPositions.addPanToPercentage(sm.sliderPercentage, pan)
                            if (sm.sliderPercentage != newPercentage) {
                                recordCommand(Command.SCALE, targets = listOf(h.ix))
                                val circle = circles[h.ix]
                                val scaleFactor = sliderPercentageDeltaToZoom(newPercentage - sm.sliderPercentage)
                                circles[h.ix] = circle.scale(sm.center, scaleFactor)
                                submode = sm.copy(sliderPercentage = newPercentage)
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
                            circles[h.ix] = circles[h.ix].rotate(center, angle1.toFloat())
                            submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
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
                                    circles[ix] = circles[ix].scale(center, scaleFactor)
                                }
                            }
                        }
                        is SubMode.ScaleViaSlider -> {
                            val newPercentage = selectionControlsPositions.addPanToPercentage(sm.sliderPercentage, pan)
                            if (sm.sliderPercentage != newPercentage) {
                                recordCommand(Command.SCALE, targets = selection)
                                val scaleFactor = sliderPercentageDeltaToZoom(newPercentage - sm.sliderPercentage)
                                for (ix in selection) {
                                    circles[ix] = circles[ix].scale(sm.center, scaleFactor)
                                }
                                submode = sm.copy(sliderPercentage = newPercentage)
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
                                circles[ix] = circles[ix].rotate(sm.center, angle1.toFloat())
                            }
                            submode = sm.copy(angle = newAngle, snappedAngle = snappedAngle)
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
                        (qualifiedPart.insides - newQualifiedPart.insides) union (newQualifiedPart.insides - qualifiedPart.insides) union
                        (qualifiedPart.outsides - newQualifiedPart.outsides) union (newQualifiedPart.outsides - qualifiedPart.outsides)
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
            }
        } else if (mode == SelectionMode.Multiselect && selection.isNotEmpty() && showCircles) {
            if (selection.size == 1) { // move + scale radius
                recordCommand(Command.MOVE, targets = selection)
                val ix = selection.single()
                when (val circle = circles[ix]) {
                    is Circle ->
                        circles[ix] = circle.translate(pan).scale(circle.center, zoom)
                    is Line ->
                        circles[ix] = circle.translate(pan)
                }
            } else if (selection.size > 1) { // scale radius & position
                recordCommand(Command.MOVE, targets = selection)
                for (ix in selection) {
                    circles[ix] = circles[ix].rotate(c, rotationAngle).scale(c, zoom).translate(pan)
                }
            }
        } else {
            if (mode is ToolMode &&
                partialArgList!!.currentArgType == PartialArgList.ArgType.XYPoint
            ) {
                val newArg = PartialArgList.Arg.XYPoint.fromOffset(c)
                partialArgList = partialArgList!!.updateCurrentArg(newArg, confirmThisArg = false)
            } else if (
                mode is ToolMode &&
                partialArgList!!.currentArgType == PartialArgList.ArgType.GeneralizedCircle
            ) {
                val newArg = PartialArgList.Arg.GeneralizedCircle(Point.fromOffset(c))
                partialArgList = partialArgList!!.updateCurrentArg(newArg, confirmThisArg = false)
            } else {
//                recordCommand(Command.CHANGE_POV)
                translation = translation + pan // navigate canvas
            }
        }
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
                switchToCategory(EditClusterCategory.Multiselect) // BUG: weird inconsistent behavior
                toggleSelectAll()
            }
            KeyboardAction.DELETE -> deleteCircles()
            KeyboardAction.PASTE -> duplicateCircles()
            KeyboardAction.ZOOM_IN -> scaleSelection(ZOOM_INCREMENT)
            KeyboardAction.ZOOM_OUT -> scaleSelection(1/ZOOM_INCREMENT)
            KeyboardAction.UNDO -> undo()
            KeyboardAction.REDO -> redo()
            KeyboardAction.CANCEL -> when (mode) { // reset mode
                is ToolMode -> partialArgList = PartialArgList(partialArgList!!.signature)
                is SelectionMode -> selection.clear()
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
        }
    }

    private fun completeCircleByCenterAndRadius() {
        val argList = partialArgList!!
        val (center, radiusPoint) = argList.args.map {
            (it as PartialArgList.Arg.XYPoint).toOffset()
        }
        val newCircle = Circle(
            center,
            radius = (radiusPoint - center).getDistance()
        )
        createNewCircles(listOf(newCircle))
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleBy3Points() {
        val argList = partialArgList!!
        val gCircles = argList.args.map {
            (it as PartialArgList.Arg.GeneralizedCircle).gCircle
        }
        val result = GeneralizedCircle.perp3(
            GeneralizedCircle.fromGCircle(gCircles[0]),
            GeneralizedCircle.fromGCircle(gCircles[1]),
            GeneralizedCircle.fromGCircle(gCircles[2]),
        )?.toGCircle() as? CircleOrLine
        if (result != null) {
            createNewCircles(listOf(result))
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeLineBy2Points() {
        val argList = partialArgList!!
        val gCircles = argList.args.map {
            (it as PartialArgList.Arg.GeneralizedCircle).gCircle
        }
        val result = GeneralizedCircle.perp3(
            GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY),
            GeneralizedCircle.fromGCircle(gCircles[0]),
            GeneralizedCircle.fromGCircle(gCircles[1]),
        )?.toGCircle() as? Line
        if (result != null) {
            createNewCircles(listOf(result))
        }
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleInversion() {
        val argList = partialArgList!!
        val targetCirclesIxs = (argList.args[0] as PartialArgList.Arg.SelectedCircles).indices
        val invertingCircleIndex = (argList.args[1] as PartialArgList.Arg.CircleIndex).index
        val invertingCircle = circles[invertingCircleIndex]
        val newCircles = targetCirclesIxs.map { targetIx ->
            val targetCircle = circles[targetIx]
            val newCircle = Circle.invert(invertingCircle, targetCircle) as CircleOrLine
            newCircle
        }
        createNewCircles(newCircles)
        copyParts(targetCirclesIxs, ((circles.size - newCircles.size) until circles.size).toList())
        partialArgList = PartialArgList(argList.signature)
    }

    fun completeCircleInterpolation(nInterjacents: Int, inBetween: Boolean = true) {
        showCircleInterpolationDialog = false
        val argList = partialArgList!!
        val startCircleIx = (argList.args[0] as PartialArgList.Arg.CircleIndex).index
        val start = GeneralizedCircle.fromGCircle(circles[startCircleIx])
        val endCircleIx = (argList.args[1] as PartialArgList.Arg.CircleIndex).index
        val end = GeneralizedCircle.fromGCircle(circles[endCircleIx])
        val n = nInterjacents + 1
        val newCircles = (1 until n).map { i ->
            val interjacent = start.bisector(end, nOfSections = n, index = i, inBetween = inBetween)
            interjacent.toGCircle() as CircleOrLine
        }
        createNewCircles(newCircles)
        partialArgList = PartialArgList(argList.signature)
        defaultInterpolationParameters = DefaultInterpolationParameters(nInterjacents, inBetween)
    }

    fun resetCircleInterpolation() {
        showCircleInterpolationDialog = false
        partialArgList = PartialArgList(EditClusterTool.CircleInterpolation.signature)
    }

    fun completeCircleExtrapolation(
        nLeft: Int, // start = Left
        nRight: Int, // end = Right
    ) {
        showCircleExtrapolationDialog = false
        val argList = partialArgList!!
        val startCircleIx = (argList.args[0] as PartialArgList.Arg.CircleIndex).index
        val start = GeneralizedCircle.fromGCircle(circles[startCircleIx])
        val endCircleIx = (argList.args[1] as PartialArgList.Arg.CircleIndex).index
        val end = GeneralizedCircle.fromGCircle(circles[endCircleIx])
        val newGeneralizedCircles = mutableListOf<GeneralizedCircle>()
        var a = start
        var b = end
        var c: GeneralizedCircle
        repeat(nLeft) { // <-c-a-b
            c = a.applyTo(b)
            newGeneralizedCircles.add(c)
            b = a
            a = c
        }
        a = start
        b = end
        repeat(nRight) { // a-b-c->
            c = b.applyTo(a)
            newGeneralizedCircles.add(c)
            a = b
            b = c
        }
        createNewCircles(
            newGeneralizedCircles
                .map { it.toGCircle() as CircleOrLine }
        )
        partialArgList = PartialArgList(argList.signature)
        defaultExtrapolationParameters = DefaultExtrapolationParameters(nLeft, nRight)
    }

    fun resetCircleExtrapolation() {
        showCircleExtrapolationDialog = false
        partialArgList = PartialArgList(EditClusterTool.CircleExtrapolation.signature)
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
            EditClusterTool.Palette -> showColorPickerDialog = true
            EditClusterTool.Delete -> deleteCircles()
            EditClusterTool.Duplicate -> duplicateCircles()
            EditClusterTool.InsertCenteredCross -> insertCenteredCross()
            is EditClusterTool.AppliedColor -> selectRegionColor(tool.color)
            is EditClusterTool.MultiArg -> switchToMode(ToolMode.correspondingTo(tool))
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
            EditClusterTool.FillChessboardPattern -> !invertChessboard
            EditClusterTool.RestrictRegionToSelection -> restrictRegionsToSelection
            EditClusterTool.ShowCircles -> showCircles
            EditClusterTool.ToggleFilledOrOutline -> !showWireframes
            EditClusterTool.Palette -> showColorPickerDialog
            is EditClusterTool.MultiArg -> mode == ToolMode.correspondingTo(tool)
            else -> true
        }

    @Serializable
    @Immutable
    data class UiState(
        val circles: List<CircleOrLine>,
        val parts: List<Cluster.Part>,
        val selection: List<Ix>, // circle indices
        @Serializable(OffsetSerializer::class)
        val translation: Offset,
    ) { // MAYBE: save translations & scaling and/or also keep such movements in history
        companion object {
            val DEFAULT = UiState(
                circles = listOf(
                    Circle(200.0, 200.0, 100.0),
                    Circle(250.0, 200.0, 100.0),
                    Circle(200.0, 250.0, 100.0),
                    Circle(250.0, 250.0, 100.0),
                ),
                parts = listOf(Cluster.Part(setOf(0), setOf(1,2,3))),
                selection = listOf(0),
                // NOTE: hardcoded default is bad, much better would be to specify the center but oh well
                translation = Offset(225f, 225f) + Offset(400f, 0f)
            )

            fun restore(coroutineScope: CoroutineScope, uiState: UiState): EditClusterViewModel =
                EditClusterViewModel(
                    coroutineScope,
                    Cluster(uiState.circles.toList(), uiState.parts.toList(), filled = true)
                ).apply {
                    if (uiState.selection.size > 1)
                        mode = SelectionMode.Multiselect
                    selection.addAll(uiState.selection)
                    translation = uiState.translation
                }

            fun save(viewModel: EditClusterViewModel): UiState =
                with (viewModel) {
                    UiState(
                        circles.toList(), parts.toList(), selection.toList(),
                        viewModel.translation
                    )
                }
        }
    }

    class Saver(
        private val coroutineScope: CoroutineScope
    ) : androidx.compose.runtime.saveable.Saver<EditClusterViewModel, String> {
        override fun SaverScope.save(value: EditClusterViewModel): String =
            Json.encodeToString(UiState.serializer(), UiState.save(value))
        override fun restore(value: String): EditClusterViewModel {
            return UiState.restore(
                coroutineScope,
                Json.decodeFromString(UiState.serializer(), value)
            )
        }
    }

    companion object {
        /** min tap/grab distance to select an object in dp */
        const val TAP_DISTANCE = 10f
        const val LOW_ACCURACY_FACTOR = 1.5f
        const val ZOOM_INCREMENT = 1.05f // == +5%
        const val MAX_SLIDER_ZOOM = 3.0f // == +200%
        const val HISTORY_SIZE = 100
        const val FAST_CENTERED_CIRCLE = true
        const val ENABLE_ANGLE_SNAPPING = true

        fun sliderPercentageDeltaToZoom(percentageDelta: Float): Float =
            MAX_SLIDER_ZOOM.pow(2*percentageDelta)
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

/** used for grouping UiState changes into batches for history keeping */
enum class Command {
    MOVE,
    CHANGE_RADIUS, SCALE,
    ROTATE,
    DUPLICATE, DELETE,
    CREATE,
    FILL_REGION,
    /** records canvas translations and scaling */
//    CHANGE_POV,
    ;

    /** Used to distinguish/conflate [Command]s depending on their targets */
    sealed interface Tag {
        data class Targets(val targets: List<Ix> = emptyList()) : Tag
        class Unique : Tag {
            override fun equals(other: Any?): Boolean =
                false
        }
    }
}