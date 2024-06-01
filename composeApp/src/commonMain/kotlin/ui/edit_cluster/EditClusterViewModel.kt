package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import data.Circle
import data.CircleF
import data.Cluster
import data.ColorCssSerializer
import data.OffsetSerializer
import data.io.Ddc
import data.io.parseDdc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import domain.angleDeg
import domain.rotateBy
import ui.theme.DodeclustersColors
import ui.tools.EditClusterTool
import kotlin.math.absoluteValue
import kotlin.math.pow

/** circle index in vm.circles or cluster.circles */
typealias Ix = Int

// NOTE: waiting for decompose 3.0-stable for a real VM impl
@Stable
class EditClusterViewModel(
    /** NOT a viewModelScope, just a rememberCS from the screen composable */
    private val coroutineScope: CoroutineScope,
    cluster: Cluster = Cluster.SAMPLE
) {
    private val _mode = mutableStateOf<Mode>(SelectionMode.Drag)
    val mode: Mode by _mode
    val circles = mutableStateListOf(*cluster.circles.toTypedArray())
    val parts = mutableStateListOf(*cluster.parts.toTypedArray())
    /** indices of selected circles */
    val selection = mutableStateListOf<Ix>() // MAYBE: when circles are hidden select parts instead

    /** currently selected color */
    var regionColor by mutableStateOf(DodeclustersColors.lightPurple)
    var showCircles by mutableStateOf(true)
    /** which style to use when drawing parts: true = stroke, false = fill */
    var showWireframes by mutableStateOf(false)
    /** applies to [SelectionMode.Region]:
     * only use circles present in the [selection] to determine which parts to fill */
    var restrictRegionsToSelection by mutableStateOf(true)

    val circleSelectionIsActive by derivedStateOf {
        showCircles && selection.isNotEmpty() && mode.isSelectingCircles()
    }
    val handleConfig = derivedStateOf { // depends on selectionMode & selection
        when (mode) {
            is SelectionMode.Drag ->
                if (selection.isEmpty()) null
                else HandleConfig.SingleCircle(selection.single())
            is SelectionMode.Multiselect -> when {
                selection.isEmpty() -> null
                selection.size == 1 -> HandleConfig.SingleCircle(selection.single())
                selection.size > 1 -> HandleConfig.SeveralCircles(selection)
                else -> Unit // never
            }
            is SelectionMode.Region -> null
            else -> null
        }
    }
    private var grabbedHandle: Handle? = null
    private var grabbedCircleIx: Ix? = null // only used for long-drag
    private var rotationAnchor: Offset? = null
    var rotationIndicatorPosition: Offset? by mutableStateOf(null)

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

    private val _decayingCircles = MutableSharedFlow<DecayingCircles>()
    val decayingCircles = _decayingCircles.asSharedFlow()

    var showColorPickerDialog by mutableStateOf(false)

    val canvasSize = mutableStateOf(IntSize.Zero) // used when saving best-center
    val translation = mutableStateOf(Offset.Zero) // pre-scale offset
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
        if (canvasSize.value == IntSize.Zero) {
            null
        } else {
            val visibleCenter = Offset(canvasSize.value.width/2f, canvasSize.value.height/2f)
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
        translation.value = -Offset(
            ddc.bestCenterX?.let { it - canvasSize.value.width/2f } ?: 0f,
            ddc.bestCenterY?.let { it - canvasSize.value.height/2f } ?: 0f
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
        translation.value = Offset.Zero
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
        rotationIndicatorPosition = null
        selection.clear()
        parts.clear()
        circles.clear()
        circles.addAll(state.circles)
        parts.addAll(state.parts)
        switchToMode(state.mode, noAlteringShortcuts = true)
        selection.clear() // switch can populate it
        selection.addAll(state.selection)
    }

    /** let s_i := history[i], c_i := commands[i]
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
            rotationIndicatorPosition = null
    }

    fun createNewCircle(
        newCircle: Circle = Circle(absolute(Offset(200f, 200f)), 50.0),
        switchToSelectionMode: Boolean = true
    ) = coroutineScope.launch {
        if (newCircle.radius > 0.0) {
            recordCommand(Command.CREATE)
            println("createNewCircle $newCircle")
            showCircles = true
            circles.add(newCircle)
            if (switchToSelectionMode && !mode.isSelectingCircles())
                switchToMode(SelectionMode.Drag)
            selection.clear()
            selection.add(circles.size - 1)
            _decayingCircles.emit(
                DecayingCircles(listOf(newCircle.toCircleF()), Color.Green)
            )
        }
    }

    fun duplicateCircles() = coroutineScope.launch {
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
            _decayingCircles.emit(
                DecayingCircles(copiedCircles.map { it.toCircleF() }, Color.Blue)
            )
        }
    }

    fun deleteCircles() = coroutineScope.launch {
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
            val whatsLeft = circles.filterIndexed { ix, _ -> ix !in whatsGone }
            val oldParts = parts.toList()
            val reindexing = reindexingMap(circles.indices, whatsGone)
            _decayingCircles.emit(
                DecayingCircles(selection.map { circles[it].toCircleF() }, Color.Red)
            )
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
        }
    }

    fun switchToMode(newMode: Mode, noAlteringShortcuts: Boolean = false) {
        val new = when (newMode) {
            is SelectionMode.Multiselect.Default -> SelectionMode.Multiselect.Default.redirect
            else -> newMode // TODO: smarter defaulting integrated with tool ADT
        }
        if (selection.size > 1 && new == SelectionMode.Drag)
            selection.clear()
        if (mode is SelectionMode.Multiselect && new is SelectionMode.Multiselect && !noAlteringShortcuts) {
            if (selection.isEmpty())
                selection.addAll(circles.indices)
            else
                selection.clear()
        } else if (mode == SelectionMode.Region && new == SelectionMode.Region && !noAlteringShortcuts) {
            if (parts.isEmpty()) {
                recordCommand(Command.SELECT_REGION)
                // select interlacing, todo: proper 2^n -> even # of 1's -> {0101001} -> parts
                parts.add(Cluster.Part(emptySet(), circles.indices.toSet(), regionColor))
            } else {
                recordCommand(Command.SELECT_REGION)
                parts.clear()
            }
        }
        if (new is CreationMode) {
            selection.clear()
            showCircles = true
        }
        _mode.value = new
    }

    fun absolute(visiblePosition: Offset): Offset =
        visiblePosition - translation.value

    fun visible(position: Offset): Offset =
        position + translation.value

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
            ix to ((circle.offset - position).getDistance() - circle.radius).absoluteValue
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

    fun toggleSelectAll() {
        if (!selection.containsAll(circles.indices.toSet())) {
            selection.clear()
            selection.addAll(circles.indices)
        } else {
            selection.clear()
        }
    }

    fun toggleShowCircles() {
        showCircles = !showCircles
    }

    fun toggleRestrictRegionsToSelection() {
        restrictRegionsToSelection = !restrictRegionsToSelection
    }

    private fun scaleSelection(zoom: Float) {
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
                        val newOffset = (circle.offset - center) * zoom + center
                        circles[ix] = Circle(newOffset, zoom * circle.radius)
                    }
                }
            }
    }

    // pointer input callbacks
    fun onTap(position: Offset) {
        // select circle(s)/region
        if (showCircles) {
            when (mode) {
                SelectionMode.Drag -> {
                    val clickedDeleteHandle = if (selection.isNotEmpty()) {
                        val circle = circles[selection.single()]
                        val bottom = circle.offset + Offset(0f, circle.radius.toFloat())
                        isCloseEnoughToSelect(bottom, position)
                    } else false
                    if (clickedDeleteHandle)
                        deleteCircles()
                    else
                        reselectCircleAt(position)
                }

                is SelectionMode.Multiselect -> {
                    val clickedDeleteHandle = if (selection.isNotEmpty()) {
                        val deleteHandlePosition = getSelectionRect().bottomCenter
                        isCloseEnoughToSelect(deleteHandlePosition, position)
                    } else false
                    if (clickedDeleteHandle)
                        deleteCircles()
                    else { // (re)-select part
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
        when (val m = mode) {
            is CreationMode.CircleByCenterAndRadius.Center ->
                (visiblePosition ?: m.center)?.let { c ->
                    _mode.value = CreationMode.CircleByCenterAndRadius.Radius(center = c)
                }
            is CreationMode.CircleByCenterAndRadius.Radius ->
                (visiblePosition ?: m.radiusPoint)?.let { rP ->
                    val newCircle = Circle(
                        absolute(m.center),
                        radius = (absolute(rP) - absolute(m.center)).getDistance()
                    )
                    createNewCircle(newCircle, switchToSelectionMode = false)
//                    println("cr new c #${circles.size}")
                    _mode.value = CreationMode.CircleByCenterAndRadius.Center()
                }
            is CreationMode.CircleBy3Points -> when (m.phase) {
                1 -> _mode.value = m.copy(2, listOf(visiblePosition ?: m.points[0]))
                2 -> _mode.value = m.copy(3, listOf(m.points[0], visiblePosition ?: m.points[1]))
                3 -> {
                    try {
                        val newCircle = Circle.by3Points(
                            absolute(m.points[0]), absolute(m.points[1]),
                            absolute(visiblePosition ?: m.points[2])
                        )
                        createNewCircle(newCircle, switchToSelectionMode = false)
                    } catch (e: NumberFormatException) {
                        e.printStackTrace()
                    } finally {
                        _mode.value = CreationMode.CircleBy3Points()
                    }
                }
            }
            else -> {}
        }
    }

    fun onDown(visiblePosition: Offset) {
        rotationAnchor = null
        rotationIndicatorPosition = null
        // no need for onUp since all actions occur after onDown
        if (showCircles) {
            grabbedHandle = when (val h = handleConfig.value) {
                is HandleConfig.SingleCircle -> {
                    val circle = circles[h.ix]
                    val radiusHandlePosition = circle.offset + Offset(circle.radius.toFloat(), 0f)
                    Handle.SCALE.takeIf {
                        isCloseEnoughToSelect(radiusHandlePosition, visiblePosition)
                    }
                }
                is HandleConfig.SeveralCircles -> {
                    val rect = getSelectionRect()
                    val scaleHandlePosition = rect.topRight
                    if (isCloseEnoughToSelect(scaleHandlePosition, visiblePosition))
                        Handle.SCALE
                    else {
                        val rotateHandlePosition = rect.bottomRight
                        if (isCloseEnoughToSelect(rotateHandlePosition, visiblePosition)) {
                            rotationAnchor = rect.center
                            rotationIndicatorPosition = rotateHandlePosition
                            Handle.ROTATE
                        } else null
                    }
                }
                else -> null
            }
            // NOTE: this enables drag-only behavior, you lose your selection when grabbing new circle
            if (DRAG_ONLY && grabbedHandle == null && mode == SelectionMode.Drag) {
                val previouslySelected = selection.firstOrNull()
                reselectCircleAt(visiblePosition)
                if (previouslySelected != null && selection.isEmpty()) // this line requires deselecting first to navigate around canvas
                    selection.add(previouslySelected)
            } else when (val m = mode) {
                is CreationMode.CircleByCenterAndRadius.Center ->
                    _mode.value = m.copy(center = visiblePosition)
                is CreationMode.CircleByCenterAndRadius.Radius ->
                    _mode.value = m.copy(radiusPoint = visiblePosition)

                is CreationMode.CircleBy3Points -> {
                    when (m.phase) {
                        1 -> _mode.value = m.copy(points = listOf(visiblePosition))
                        2 -> _mode.value = m.copy(points = listOf(m.points[0], visiblePosition))
                        3 -> _mode.value = m.copy(points = listOf(m.points[0], m.points[1], visiblePosition))
                    }
                }
                else -> {}
            }
        }
    }

    // MAYBE: handle key arrows as panning
    fun onPanZoomRotate(pan: Offset, centroid: Offset, zoom: Float, rotationAngle: Float) {
        if (grabbedHandle != null) {
            // drag handle
            when (val h = handleConfig.value) {
                is HandleConfig.SingleCircle -> {
                    recordCommand(Command.CHANGE_RADIUS)
                    val center = circles[h.ix].offset
                    val r = (absolute(centroid) - center).getDistance()
                    circles[h.ix] = circles[h.ix].copy(radius = r.toDouble())
                }
                is HandleConfig.SeveralCircles -> {
                    when (grabbedHandle) {
                        Handle.SCALE -> {
                            recordCommand(Command.SCALE)
                            val rect = getSelectionRect()
                            val scaleHandlePosition = rect.topRight
                            val center = rect.center
                            val centerToHandle = scaleHandlePosition - center
                            val centerToCurrent = centerToHandle + pan
                            val handleScale = centerToCurrent.getDistance()/centerToHandle.getDistance()
                            for (ix in selection) {
                                val circle = circles[ix]
                                val newOffset = (circle.offset - center) * handleScale + center
                                circles[ix] = Circle(newOffset, handleScale * circle.radius)
                            }
                        }
                        Handle.ROTATE -> {
                            recordCommand(Command.ROTATE)
                            val center = rotationAnchor!!
                            val centerToHandle = rotationIndicatorPosition!! - center
                            val centerToCurrent = centerToHandle + pan
                            val angle = centerToHandle.angleDeg(centerToCurrent)
//                            println(angle)
                            rotationIndicatorPosition = (rotationIndicatorPosition!! - center).rotateBy(angle) + center
                            for (ix in selection) {
                                val circle = circles[ix]
                                val newOffset = (circle.offset - center).rotateBy(angle) + center
                                circles[ix] = Circle(newOffset, circle.radius)
                            }
                        }
                        null -> {}
                    }
                }
                else -> Unit
            }
        } else if (mode == SelectionMode.Drag && selection.isNotEmpty() && showCircles) {
            // move + scale radius
            recordCommand(Command.MOVE)
            val ix = selection.single()
            val circle = circles[ix]
            val newCenter = circle.offset + pan
            circles[ix] = Circle(newCenter, zoom * circle.radius)
        } else if (mode is SelectionMode.Multiselect && selection.isNotEmpty() && showCircles) {
            if (selection.size == 1) { // move + scale radius
                recordCommand(Command.MOVE)
                val ix = selection.single()
                val circle = circles[ix]
                val newCenter = circle.offset + pan
                circles[ix] = Circle(newCenter, zoom * circle.radius)
            } else if (selection.size > 1) { // scale radius & position
                recordCommand(Command.MOVE)
                for (ix in selection) {
                    val circle = circles[ix]
                    val newOffset = (circle.offset - centroid).rotateBy(rotationAngle) * zoom + centroid + pan
                    circles[ix] = Circle(newOffset, zoom * circle.radius)
                }
            }
        } else {
            when (val m = mode) {
                is CreationMode.CircleByCenterAndRadius.Center ->
                    if (m.center == null)
                        _mode.value = m.copy(center = centroid)
                    else
                        _mode.value =
                            CreationMode.CircleByCenterAndRadius.Radius(m.center, centroid)
                is CreationMode.CircleByCenterAndRadius.Radius ->
                    _mode.value = m.copy(radiusPoint = centroid)
                is CreationMode.CircleBy3Points -> {
                    _mode.value = m.copy(
                        points = m.points.take(m.phase - 1).plusElement(centroid)
                    )
                }
                else -> {
//                    recordCommand(Command.CHANGE_POV)
                    translation.value = translation.value + pan // navigate canvas
                }
            }
        }
    }

    fun onVerticalScroll(yDelta: Float) {
        val zoom = (1.01f).pow(-yDelta)
        scaleSelection(zoom)
    }

    fun onLongDragStart(position: Offset) {
        // draggables = circles
        if (showCircles)
            selectCircle(circles, position)?.let { ix ->
                grabbedCircleIx = ix
                if (mode == SelectionMode.Drag) {
                    selection.clear()
                    selection.add(ix)
                }
            } ?: run {
                if (true || mode == SelectionMode.Region) {
                    // TODO: select part
                    // drag bounding circles
                }
            }
    }

    fun onLongDrag(delta: Offset) {
        // if grabbed smth, do things (regardless of selection)
        grabbedCircleIx?.let {
            recordCommand(Command.MOVE)
            val circle = circles[it]
            circles[it] = Circle(circle.offset + delta, circle.radius)
        } ?: onPanZoomRotate(delta, Offset.Zero, 1f, 0f)
    }

    fun onLongDragCancel() {
        grabbedCircleIx = null
    }

    fun onLongDragEnd() {
        grabbedCircleIx = null
    }

    fun processKeyboardAction(action: KeyboardAction) {
        when (action) {
            KeyboardAction.SELECT_ALL -> toggleSelectAll()
            KeyboardAction.DELETE -> deleteCircles()
            KeyboardAction.PASTE -> duplicateCircles()
            KeyboardAction.ZOOM_IN -> scaleSelection(ZOOM_INCREMENT)
            KeyboardAction.ZOOM_OUT -> scaleSelection(1/ZOOM_INCREMENT)
            KeyboardAction.UNDO -> undo()
            KeyboardAction.REDO -> redo()
            KeyboardAction.CANCEL -> when (val m = mode) { // reset creation mode
                is CreationMode -> switchToMode(m.startState)
                else -> Unit
            }
        }
    }

    @Stable
    fun toolAction(tool: EditClusterTool): Unit =
        when (tool) {
            EditClusterTool.Drag -> switchToMode(SelectionMode.Drag)
            EditClusterTool.Multiselect -> switchToMode(SelectionMode.Multiselect.Default)
            EditClusterTool.FlowSelect -> TODO()
            EditClusterTool.Region -> switchToMode(SelectionMode.Region)
            EditClusterTool.RestrictRegionToSelection -> toggleRestrictRegionsToSelection()
            EditClusterTool.ShowCircles -> toggleShowCircles()
            EditClusterTool.Palette -> showColorPickerDialog = true
            EditClusterTool.Delete -> {
                deleteCircles(); Unit
            }
            EditClusterTool.Duplicate -> {
                duplicateCircles(); Unit
            }
            EditClusterTool.ConstructCircleByCenterAndRadius -> switchToMode(CreationMode.CircleByCenterAndRadius.Center())
            EditClusterTool.ConstructCircleBy3Points -> switchToMode(CreationMode.CircleBy3Points())
        }

    /** Is [tool] enabled? */
    inline fun toolPredicate(tool: EditClusterTool): Boolean =
        when (tool) { // NOTE: i think this has to return State<Boolean> too work properly
            EditClusterTool.Drag -> mode is SelectionMode.Drag
            EditClusterTool.Multiselect -> mode is SelectionMode.Multiselect
            EditClusterTool.Region -> mode is SelectionMode.Region
            EditClusterTool.RestrictRegionToSelection -> restrictRegionsToSelection
            EditClusterTool.ShowCircles -> showCircles
            EditClusterTool.ConstructCircleByCenterAndRadius -> mode is CreationMode.CircleByCenterAndRadius
            EditClusterTool.ConstructCircleBy3Points -> mode is CreationMode.CircleBy3Points
            else -> true
        }

    @Serializable
    @Immutable
    data class UiState(
        val mode: Mode,
        val circles: List<Circle>,
        val parts: List<Cluster.Part>,
        val selection: List<Ix>, // circle indices
        @Serializable(OffsetSerializer::class)
        val translation: Offset,
    ) { // MAYBE: save translations & scaling and/or also keep such movements in history
        companion object {
            val DEFAULT = UiState(
                SelectionMode.Drag,
                circles = listOf(
                    Circle(200.0, 200.0, 100.0),
                    Circle(250.0, 200.0, 100.0),
                    Circle(200.0, 250.0, 100.0),
                    Circle(250.0, 250.0, 100.0),
                ),
                parts = listOf(Cluster.Part(setOf(0), setOf(1,2,3))),
                selection = listOf(0),
                translation = Offset(225f, 225f) + Offset(200f, 200f)
            )

            fun restore(coroutineScope: CoroutineScope, uiState: UiState): EditClusterViewModel =
                EditClusterViewModel(
                    coroutineScope,
                    Cluster(uiState.circles.toList(), uiState.parts.toList(), filled = true)
                ).apply {
                    _mode.value = uiState.mode
                    selection.addAll(uiState.selection)
//                    translation.value = Offset(100f, 0f) //uiState.translation
                    // this translation recovery dont work
                }

            fun save(viewModel: EditClusterViewModel): UiState =
                with (viewModel) {
                    UiState(
                        mode,
                        circles.toList(), parts.toList(), selection.toList(),
                        viewModel.translation.value
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
        /** In drag mode: enables simple drag&drop behavior that is otherwise only available after long press */
        const val DRAG_ONLY = true
        /** min tap/grab distance to select an object in dp */
        const val EPSILON = 10f
        const val ZOOM_INCREMENT = 1.05f // +5%
        const val HISTORY_SIZE = 100
    }
}

@Serializable
@Immutable
sealed interface Mode {
    fun isSelectingCircles(): Boolean =
        this is SelectionMode.Drag || this is SelectionMode.Multiselect
}

@Serializable
sealed interface SelectionMode : Mode {
    @Serializable
    data object Drag : SelectionMode
    @Serializable
    sealed interface Multiselect : SelectionMode {
        @Serializable
        /** null-like, default [Multiselect] mode implementation: either last used or global default */
        data object Default: Multiselect {
            val redirect = ByClick
        }
        @Serializable
        data object ByClick: Multiselect
        @Serializable
        data object ByFlow: Multiselect
    }
    @Serializable
    /** mode: select regions to create new [Cluster.Part]s */
    data object Region : SelectionMode
}

@Serializable
/** sub-modes of [SelectionMode.Multiselect] related to how new selection is added */
enum class MultiselectLogic {
    SYMMETRIC_DIFFIRENCE, ADD, SUBTRACT
}

@Serializable
sealed class CreationMode(open val phase: Int, val nPhases: Int): Mode {
    fun isTerminal(): Boolean =
        phase == nPhases

    sealed class CircleByCenterAndRadius(
        override val phase: Int,
    ) : CreationMode(phase, nPhases = 2) {
        // visible positions are used
        data class Center(val center: Offset? = null) : CircleByCenterAndRadius(phase = 1)
        data class Radius(val center: Offset, val radiusPoint: Offset? = null) : CircleByCenterAndRadius(phase = 2)
    }
    data class CircleBy3Points(
        override val phase: Int = 1,
        val points: List<Offset> = emptyList()
    ) : CreationMode(phase, nPhases = 3)
//    data class LineBy2Points(override val phase: Int) : CreationMode(phase, nPhases = 2)
}

val CreationMode.startState: CreationMode
    get() = when (this) {
        is CreationMode.CircleByCenterAndRadius -> CreationMode.CircleByCenterAndRadius.Center()
        is CreationMode.CircleBy3Points -> CreationMode.CircleBy3Points()
    }

/** ixs = indices of circles to which the handle is attached */
@Immutable
sealed class HandleConfig(open val ixs: List<Ix>) {
    data class SingleCircle(val ix: Ix): HandleConfig(listOf(ix))
    data class SeveralCircles(override val ixs: List<Ix>): HandleConfig(ixs)
}

enum class Handle {
    SCALE, ROTATE
}

/** params for create/copy/delete animations */
@Serializable
@Immutable
data class DecayingCircles(
    val circles: List<CircleF>,
    @Serializable(ColorCssSerializer::class)
    val color: Color
)

/** used for grouping UiState changes into batches for history keeping */
enum class Command {
    MOVE,
    CHANGE_RADIUS, SCALE,
    ROTATE,
    DUPLICATE, DELETE, CREATE,
    SELECT_REGION,
    /** records canvas translations and scaling */
    CHANGE_POV,
}
