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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import data.geometry.Circle
import data.Cluster
import data.OffsetSerializer
import data.PartialArgList
import data.io.Ddc
import data.io.parseDdc
import domain.angleDeg
import domain.rotateBy
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
import kotlin.math.absoluteValue

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
    var partialArgList: PartialArgList? by mutableStateOf(null)
        private set
    val circles = mutableStateListOf(*cluster.circles.toTypedArray())
    val parts = mutableStateListOf(*cluster.parts.toTypedArray())
    /** indices of selected circles */
    val selection = mutableStateListOf<Ix>() // MAYBE: when circles are hidden select parts instead


    val categories: List<EditClusterCategory> = listOf(
        EditClusterCategory.Drag,
        EditClusterCategory.Multiselect,
        EditClusterCategory.Region,
        EditClusterCategory.Visibility,
        EditClusterCategory.Colors,
//        EditClusterCategory.Attributes, // replace with floating context menu
        EditClusterCategory.Transform,
        EditClusterCategory.Create, // FAB
    )
    // this int list is to be persisted/preserved
    // category index -> tool index among category.tools
    val categoryDefaults: SnapshotStateList<Int> =
        categories.map { it.tools.indexOf(it.default) }.toMutableStateList()
    var activeCategoryIndex: Int by mutableIntStateOf(0)
        private set
    var activeToolIndex: Int by mutableIntStateOf(categoryDefaults[activeCategoryIndex])
        private set
    val activeCategory: EditClusterCategory by derivedStateOf { categories[activeCategoryIndex] }
    private val panelNeedsToBeShown by derivedStateOf { activeCategory.tools.size > 1 }
    var showPanel by mutableStateOf(panelNeedsToBeShown)

    /** currently selected color */
    var regionColor by mutableStateOf(DodeclustersColors.purple)
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

    var undoIsEnabled by mutableStateOf(false) // = history is not empty
    var redoIsEnabled by mutableStateOf(false) // = redoHistory is not empty
    // tagged & grouped gap buffer
    private val commands = ArrayDeque<Command>(HISTORY_SIZE)
    private val redoCommands = ArrayDeque<Command>(HISTORY_SIZE)
    // we group history by commands and record it only when the new command differs from the previous one
    // NOTE: history doesn't survive background app kill
    private val history = ArrayDeque<UiState>(HISTORY_SIZE)
    private val redoHistory = ArrayDeque<UiState>(HISTORY_SIZE)
    // c_i := commands[i], s_i := history[i],
    // s_k+1 := current state (UiState.save(this))
    // c_k+i := redoCommands[i-1], s_k+i := redoHistory[i-2]
    // command c_i modifies previous state s_i into a new state s_i+1
    //   c0  c1  c2 ...  c_k |       | c_k+1     c_k+2     c_k+3
    // s0  s1  s2 ... s_k    | s_k+1 |      s_k+2     s_k+3
    // ^ history (past) ^    |  ^^^current state  \  ^^ redo history (aka future)

    private val _circleAnimations = MutableSharedFlow<CircleAnimation>()
    val circleAnimations = _circleAnimations.asSharedFlow()

    var showColorPickerDialog by mutableStateOf(false)

    var canvasSize by mutableStateOf(IntSize.Zero) // used when saving best-center
    var translation by mutableStateOf(Offset.Zero) // pre-scale offset
//    val scale = mutableStateOf(1f)

    /** min tap/grab distance to select an object */
    private var epsilon = EPSILON

    fun setEpsilon(density: Density) {
        with (density) {
            epsilon = EPSILON.dp.toPx()
        }
    }

    // navigation
    fun saveAndGoBack() {}
    fun cancelAndGoBack() {}

    fun saveAsYaml(): String {
        val cluster = Cluster(
            circles.toList(), parts.toList()
        )
        var ddc = Ddc(cluster)
        computeAbsoluteCenter()?.let { center ->
            ddc = ddc.copy(bestCenterX = center.x, bestCenterY = center.y)
        }
        return ddc.encode()
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
            moveToDdcCenter(ddc)
        } catch (e: Exception) {
            println("Failed to parse yaml")
            e.printStackTrace()
            println("Falling back to json")
            loadFromJson(yaml) // NOTE: for backwards compat
        }
    }

    private fun moveToDdcCenter(ddc: Ddc) {
        translation = -Offset(
            ddc.bestCenterX?.let { it - canvasSize.width/2f } ?: 0f,
            ddc.bestCenterY?.let { it - canvasSize.height/2f } ?: 0f
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
            val cluster = permissiveJson.decodeFromString(Cluster.serializer(), json)
            loadCluster(cluster)
        } catch (e: SerializationException) {
            println("Failed to parse json")
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            println("Failed to parse json")
            e.printStackTrace()
        }
    }

    private fun loadCluster(cluster: Cluster) {
        translation = Offset.Zero
        selection.clear()
        parts.clear()
        circles.clear()
        circles.addAll(cluster.circles)
        parts.addAll(cluster.parts)
        // reset history on load
        undoIsEnabled = false
        redoIsEnabled = false
        redoHistory.clear()
        redoCommands.clear()
        history.clear()
        commands.clear()
    }

    fun undo() {
        if (history.isNotEmpty()) {
            val currentState = UiState.save(this)
            val previousState = history.removeLast()
            val previousCommand = commands.removeLast() // previousState -> previousCommand ^n -> currentState
            loadUiState(previousState)
            redoCommands.addFirst(previousCommand)
            redoHistory.addFirst(currentState)
            redoIsEnabled = true
            undoIsEnabled = history.isNotEmpty()
        }
    }

    fun redo() {
        if (redoHistory.isNotEmpty()) {
            val currentState = UiState.save(this)
            val nextCommand = redoCommands.removeFirst()
            val nextState = redoHistory.removeFirst()
            loadUiState(nextState)
            commands.addLast(nextCommand)
            history.addLast(currentState)
            undoIsEnabled = true
            redoIsEnabled = redoHistory.isNotEmpty()
        }
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
    private fun recordCommand(command: Command) {
        if (commands.lastOrNull() != command) {
            if (history.size == HISTORY_SIZE) {
                history.removeAt(1) // save th original
                commands.removeFirst()
            }
            history.addLast(UiState.save(this)) // saving state before [command]
            commands.addLast(command)
            undoIsEnabled = true
        }
        redoCommands.clear()
        redoHistory.clear()
        redoIsEnabled = false
        if (command != Command.ROTATE)
            submode.let {
                if (it is SubMode.Rotate)
                    submode = it.copy(angle = 0.0)
            }
    }

    fun createNewCircle(
        newCircle: Circle = Circle(absolute(Offset(200f, 200f)), 50.0),
        switchToSelectionMode: Boolean = true
    ) {
        if (newCircle.radius > 0.0) {
            recordCommand(Command.CREATE)
            showCircles = true
            circles.add(newCircle)
            if (switchToSelectionMode && !mode.isSelectingCircles())
                switchToMode(SelectionMode.Drag)
            selection.clear()
            selection.add(circles.size - 1)
            coroutineScope.launch {
                _circleAnimations.emit(
                    CircleAnimation.Entrance(listOf(newCircle))
                )
            }
        }
    }

    fun duplicateCircles() {
        if (mode.isSelectingCircles()) {
            recordCommand(Command.DUPLICATE)
            val copiedCircles = selection.map { circles[it] } // preserves selection order
            val oldSize = circles.size
            val reindexing = selection.mapIndexed { i, ix -> ix to (oldSize + i) }.toMap()
            circles.addAll(copiedCircles)
            val newParts = parts.filter {
                selection.containsAll(it.insides) && selection.containsAll(it.outsides)
            }.map { part ->
                Cluster.Part( // TODO: test part copying further
                    insides = part.insides.map { reindexing[it]!! }.toSet(),
                    outsides = part.outsides.map { reindexing[it]!! }.toSet(),
                    fillColor = part.fillColor
                )
            }
            parts.addAll(newParts)
            selection.clear()
            selection.addAll(oldSize until (oldSize + copiedCircles.size))
            coroutineScope.launch {
                _circleAnimations.emit(
                    CircleAnimation.ReEntrance(copiedCircles)
                )
            }
        }
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
            recordCommand(Command.DELETE)
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
            selection.clear()
            showCircles = true
            partialArgList = PartialArgList(newMode.signature)
        }
        mode = newMode
    }

    fun absolute(visiblePosition: Offset): Offset =
        visiblePosition - translation

    fun visible(position: Offset): Offset =
        position + translation

    fun isCloseEnoughToSelect(absolutePosition: Offset, visiblePosition: Offset): Boolean {
        val position = absolute(visiblePosition)
        return (absolutePosition - position).getDistance() <= epsilon
    }

    fun selectPoint(targets: List<Offset>, visiblePosition: Offset): Ix? {
        val position = absolute(visiblePosition)
        return targets.mapIndexed { ix, offset -> ix to (offset - position).getDistance() }
            .filter { (_, distance) -> distance <= epsilon }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (ix, _) -> ix }
    }

    fun selectCircle(targets: List<Circle>, visiblePosition: Offset): Ix? {
        val position = absolute(visiblePosition)
        return targets.mapIndexed { ix, circle ->
            ix to ((circle.center - position).getDistance() - circle.radius).absoluteValue
        }
            .filter { (_, distance) -> distance <= epsilon }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (ix, _) -> ix }
    }

    fun reselectCircleAt(visiblePosition: Offset) {
        selectCircle(circles, visiblePosition)?.let { ix ->
//            println("select circle #$ix")
            selection.clear()
            selection.add(ix)
        } ?: selection.clear()
    }

    fun reselectCirclesAt(visiblePosition: Offset): Ix? =
        selectCircle(circles, visiblePosition)?.also { ix ->
            println("reselect circle @ $ix")
            if (ix in selection)
                selection.remove(ix)
            else
                selection.add(ix)
        }

    /** -> (compressed part, verbose part involving all circles) surrounding clicked position */
    private fun selectPartAt(visiblePosition: Offset, boundingCircles: List<Ix>? = null): Pair<Cluster.Part, Cluster.Part> {
        val position = absolute(visiblePosition)
        val delimiters = boundingCircles ?: circles.indices
        val ins = delimiters // NOTE: doesn't include circles that the point lies on
            .filter { ix -> circles[ix].hasInside(position) }
            .sortedBy { ix -> circles[ix].radius }
        val outs = delimiters
            .filter { ix -> circles[ix].hasOutside(position) }
            .sortedByDescending { ix -> circles[ix].radius }
        // NOTE: these do not take into account more complex "intersection is always inside x" type relationships
        val excessiveIns = ins.indices.filter { j -> // NOTE: tbh idt these can occur naturally
            val inJ = ins[j]
            (0 until j).any { smallerRJ -> // being inside imposes constraint on radii, so we pre-sort by radius
                circles[ins[smallerRJ]] isInside circles[inJ]
            } || outs.any { ix -> circles[inJ] isInside circles[ix] } // if an 'in' isInside an 'out' it does nothing
        }. map { ins[it] }
        val excessiveOuts = outs.indices.filter { j ->
            val outJ = outs[j]
            (0 until j).any { largerRJ ->
                circles[outJ] isInside circles[outs[largerRJ]]
            } || ins.any { ix -> circles[outJ] isOutside circles[ix] } // if an 'out' isOutside an 'in' it does nothing
        }.map { outs[it] }
        val part0 = Cluster.Part(ins.toSet(), outs.toSet(), regionColor)
        val part = Cluster.Part(
            insides = ins.toSet().minus(excessiveIns.toSet()),
            outsides = outs.toSet().minus(excessiveOuts.toSet()),
            fillColor = regionColor
        )
        return Pair(part, part0)
    }

    fun reselectRegionAt(
        visiblePosition: Offset,
        boundingCircles: List<Ix>? = null,
        setSelectionToRegionBounds: Boolean = false
    ) {
        val (part, part0) = selectPartAt(visiblePosition, boundingCircles)
        val outerParts = parts.filter { part isObviouslyInside it || part0 isObviouslyInside it  }
        if (outerParts.isEmpty()) {
            recordCommand(Command.SELECT_REGION)
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
            if (sameExistingPart != null) {
                recordCommand(Command.SELECT_REGION)
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
                recordCommand(Command.SELECT_REGION)
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
    fun getSelectionRect(): Rect {
        if (selection.isEmpty()) // pls dont use when empty selection
            return Rect(0f, 0f, 0f, 0f)
        val selectedCircles = selection.map { circles[it] }
        val left = selectedCircles.minOf { (it.x - it.radius).toFloat() }
        val right = selectedCircles.maxOf { (it.x + it.radius).toFloat() }
        val top = selectedCircles.minOf { (it.y - it.radius) }.toFloat()
        val bottom = selectedCircles.maxOf { (it.y + it.radius) }.toFloat()
        return Rect(left, top, right, bottom)
    }

    fun checkIfSelectionRectHandlesFitIn(): Boolean {
        val rect = getSelectionRect()
        val threshold = 0.2f // = 20%
        val (w, h) = canvasSize
        return rect.top > threshold * h &&
            rect.bottom < (1 - threshold) * h &&
            rect.right < (1 - threshold) * w
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

    fun selectRegionColor(color: Color) {
        showColorPickerDialog = false
        regionColor = color
        selectTool(EditClusterTool.Region)
//        showPanel = true
    }
    
    // TODO: in the future replace with select-all->delete in invisible-circles part manipulation mode
    fun deleteAllParts() {
        recordCommand(Command.DELETE)
        parts.clear()
    }

    fun insertCenteredCross() {
        recordCommand(Command.CREATE)
        val (midX, midY) = canvasSize.toSize()/2f
        val horizontalLine = Circle.almostALine(
            absolute(Offset(0f, midY)),
            absolute(Offset(2*midX, midY)),
        )
        val verticalLine = Circle.almostALine(
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
                    recordCommand(Command.CHANGE_RADIUS)
                    val ix = selection.single()
                    val circle = circles[ix]
                    circles[ix] = circle.copy(radius = zoom * circle.radius)
                }
                else -> {
                    recordCommand(Command.SCALE)
                    val rect = getSelectionRect()
                    val center = if (rect.minDimension < 5_000) rect.center else Offset.Zero
                    for (ix in selection) {
                        val circle = circles[ix]
                        val newOffset = (circle.center - center) * zoom + center
                        circles[ix] = Circle(newOffset, zoom * circle.radius)
                    }
                }
            }
    }

    // pointer input callbacks
    // onTap triggers after onDown
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
                    if (restrictRegionsToSelection && selection.isNotEmpty()) {
                        val restriction = selection.toList()
                        reselectRegionAt(position, restriction)
                    } else {
                        reselectRegionAt(position)
                    }
                }
                else -> {}
            }
        }
    }

    fun onUp(visiblePosition: Offset?) {
        submode.let {
            if (it is SubMode.Rotate)
                submode = it.copy(angle = 0.0)
        }
        when (mode) {
            is ToolMode -> {
                // we only confirm args in onUp, they are created in onDown etc.
                val args = partialArgList!! // ToolMode implies non-null partialArgList
                val newArg = when (args.currentArg) {
                    is PartialArgList.Arg.XYPoint -> visiblePosition?.let {
                        PartialArgList.Arg.XYPoint.fromOffset(it)
                    }
                    is PartialArgList.Arg.CircleIndex -> null
                    is PartialArgList.Arg.SelectedCircles -> null
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
    }

    fun onDown(visiblePosition: Offset) {
        // reset grabbed thingies
        if (showCircles) {
            submode = when (val h = handleConfig) {
                is HandleConfig.SingleCircle -> {
                    val circle = circles[h.ix]
                    val radiusHandlePosition = circle.center + Offset(circle.radius.toFloat(), 0f)
                    when {
                        isCloseEnoughToSelect(radiusHandlePosition, visiblePosition) ->
                            SubMode.Scale(circle.center)
                        else -> SubMode.None
                    }
                }
                is HandleConfig.SeveralCircles -> {
                    val rect = getSelectionRect()
                    val scaleHandlePosition = rect.topRight
                    val rotateHandlePosition = rect.bottomRight
                    when {
                        isCloseEnoughToSelect(scaleHandlePosition, visiblePosition) ->
                            SubMode.Scale(rect.center)
                        isCloseEnoughToSelect(rotateHandlePosition, visiblePosition) -> {
                            SubMode.Rotate(rect.center)
                        }
                        else -> SubMode.None
                    }
                }
                else -> SubMode.None
            }
            // NOTE: this enables drag-only behavior, you lose your selection when grabbing new circle
            if (mode == SelectionMode.Drag && submode is SubMode.None) {
                val previouslySelected = selection.firstOrNull()
                reselectCircleAt(visiblePosition)
                if (previouslySelected != null && selection.isEmpty()) // this line requires deselecting first to navigate around canvas
                    selection.add(previouslySelected)
            } else {
                if (mode is ToolMode) {
                    when (partialArgList!!.nextArgType) {
                        PartialArgList.ArgType.XYPoint -> {
                            val newArg = PartialArgList.Arg.XYPoint.fromOffset(visiblePosition)
                            partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = false)
                        }
                        PartialArgList.ArgType.CircleIndex -> {
                            selectCircle(circles, visiblePosition)?.let { circleIndex ->
                                val newArg = PartialArgList.Arg.CircleIndex(circleIndex)
                                partialArgList = partialArgList!!.addArg(newArg, confirmThisArg = true)
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
        if (submode !is SubMode.None) {
            // drag handle
            when (val h = handleConfig) {
                is HandleConfig.SingleCircle -> {
                    recordCommand(Command.CHANGE_RADIUS)
                    val center = circles[h.ix].center
                    val r = (absolute(centroid) - center).getDistance()
                    circles[h.ix] = circles[h.ix].copy(radius = r.toDouble())
                }
                is HandleConfig.SeveralCircles -> {
                    when (val sm = submode) {
                        is SubMode.Scale -> {
                            recordCommand(Command.SCALE)
                            val rect = getSelectionRect()
                            val scaleHandlePosition = rect.topRight
                            val center = rect.center
                            val centerToHandle = scaleHandlePosition - center
                            val centerToCurrent = centerToHandle + pan
                            val handleScale = centerToCurrent.getDistance()/centerToHandle.getDistance()
                            for (ix in selection) {
                                val circle = circles[ix]
                                val newOffset = (circle.center - center) * handleScale + center
                                circles[ix] = Circle(newOffset, handleScale * circle.radius)
                            }
                        }
                        is SubMode.Rotate -> {
                            recordCommand(Command.ROTATE)
                            val center = sm.center
                            val centerToCurrent = absolute(centroid) - center
                            val centerToPreviousHandle = centerToCurrent - pan
                            val angle = centerToPreviousHandle.angleDeg(centerToCurrent)
//                            println(angle)
//                            rotationIndicatorPosition = centerToHandle.rotateBy(angle) + center
                            submode = sm.copy(angle = sm.angle + angle)
                            for (ix in selection) {
                                val circle = circles[ix]
                                val newOffset = (circle.center - center).rotateBy(angle) + center
                                circles[ix] = Circle(newOffset, circle.radius)
                            }
                        }
                        else -> Unit
                    }
                }
                else -> Unit
            }
        } else if (mode == SelectionMode.Drag && selection.isNotEmpty() && showCircles) {
            // move + scale radius
            recordCommand(Command.MOVE)
            val ix = selection.single()
            val circle = circles[ix]
            val newCenter = circle.center + pan
            circles[ix] = Circle(newCenter, zoom * circle.radius)
        } else if (mode == SelectionMode.Multiselect && selection.isNotEmpty() && showCircles) {
            if (selection.size == 1) { // move + scale radius
                recordCommand(Command.MOVE)
                val ix = selection.single()
                val circle = circles[ix]
                val newCenter = circle.center + pan
                circles[ix] = Circle(newCenter, zoom * circle.radius)
            } else if (selection.size > 1) { // scale radius & position
                recordCommand(Command.MOVE)
                val c = absolute(centroid)
                for (ix in selection) {
                    val circle = circles[ix]
                    val newOffset = (circle.center - c).rotateBy(rotationAngle) * zoom + c + pan
                    circles[ix] = Circle(newOffset, zoom * circle.radius)
                }
            }
        } else {
            if (mode is ToolMode &&
                partialArgList!!.currentArgType == PartialArgList.ArgType.XYPoint
            ) {
                val newArg = PartialArgList.Arg.XYPoint.fromOffset(centroid)
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
        require(argList != null && argList.isFull && argList.isValid && argList.lastArgIsConfirmed)
        require(toolMode is ToolMode && toolMode.signature == argList.signature)
        when (toolMode) {
            ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> completeCircleByCenterAndRadius()
            ToolMode.CIRCLE_BY_3_POINTS -> completeCircleBy3Points()
            ToolMode.CIRCLE_INVERSION -> completeCircleInversion()
        }
    }

    private fun completeCircleByCenterAndRadius() {
        val argList = partialArgList!!
        val (center, radiusPoint) = argList.args.map {
            absolute((it as PartialArgList.Arg.XYPoint).toOffset())
        }
        val newCircle = Circle(
            center,
            radius = (radiusPoint - center).getDistance()
        )
        createNewCircle(newCircle, switchToSelectionMode = false)
        partialArgList = PartialArgList(argList.signature)
    }

    private fun completeCircleBy3Points() {
        val argList = partialArgList!!
        val points = argList.args.map {
            absolute((it as PartialArgList.Arg.XYPoint).toOffset())
        }
        try {
            val newCircle = Circle.by3Points(
                points[0], points[1], points[2]
            )
            createNewCircle(newCircle, switchToSelectionMode = false)
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        } finally {
            partialArgList = PartialArgList(argList.signature)
        }
    }

    private fun completeCircleInversion() {
        val argList = partialArgList!!
        val (circle0Index, invertingCircleIndex) = argList.args
            .map { (it as PartialArgList.Arg.CircleIndex).index }
        val newCircle = Circle.invert(circles[invertingCircleIndex], circles[circle0Index])
        createNewCircle(newCircle, switchToSelectionMode = false)
        partialArgList = PartialArgList(argList.signature)
    }

    fun toolAction(tool: EditClusterTool) {
//        println("toolAction($tool)")
        when (tool) {
            EditClusterTool.Drag -> switchToMode(SelectionMode.Drag)
            EditClusterTool.Multiselect -> switchToMode(SelectionMode.Multiselect)
            EditClusterTool.FlowSelect -> TODO()
            EditClusterTool.ToggleSelectAll -> toggleSelectAll()
            EditClusterTool.Region -> switchToMode(SelectionMode.Region)
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
            EditClusterTool.Multiselect -> mode == SelectionMode.Multiselect
            EditClusterTool.ToggleSelectAll -> selection.containsAll(circles.indices.toSet())
            EditClusterTool.Region -> mode == SelectionMode.Region
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
        val circles: List<Circle>,
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
        const val EPSILON = 10f
        const val ZOOM_INCREMENT = 1.05f // == +5%
        const val HISTORY_SIZE = 100
    }
}

/** ixs = indices of circles to which the handle is attached */
@Immutable
sealed class HandleConfig(open val ixs: List<Ix>) {
    data class SingleCircle(val ix: Ix): HandleConfig(listOf(ix))
    data class SeveralCircles(override val ixs: List<Ix>): HandleConfig(ixs)
}

sealed interface SubMode {
    data object None : SubMode
    data class Scale(val center: Offset) : SubMode
    data class Rotate(val center: Offset, val angle: Double = 0.0) : SubMode
}

/** params for create/copy/delete animations */
@Immutable
sealed interface CircleAnimation {
    val circles: List<Circle>
    data class Entrance(override val circles: List<Circle>) : CircleAnimation
    data class ReEntrance(override val circles: List<Circle>) : CircleAnimation
    data class Exit(override val circles: List<Circle>) : CircleAnimation
}

/** used for grouping UiState changes into batches for history keeping */
enum class Command {
    MOVE,
    CHANGE_RADIUS, SCALE,
    ROTATE,
    DUPLICATE, DELETE,
    CREATE,
    SELECT_REGION,
    /** records canvas translations and scaling */
    CHANGE_POV,
}