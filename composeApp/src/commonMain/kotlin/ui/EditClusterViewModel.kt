package ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import data.Circle
import data.Cluster
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.absoluteValue
import kotlin.math.pow

// NOTE: waiting for decompose 3.0-stable for a real VM impl
// MAYBE: use state flows in vM + in ui convert to state with .collectAsStateWithLifecycle
// TODO: history of Cluster snapshots for undo
// i'm using external coroutineScope for invoking suspend's
class EditClusterViewModel(
    cluster: Cluster = Cluster.SAMPLE
) {
    val selectionMode = mutableStateOf<SelectionMode>(SelectionMode.Drag)
    val circles = mutableStateListOf(*cluster.circles.toTypedArray())
    val parts = mutableStateListOf(*cluster.parts.toTypedArray())
    /** indices of selected circles */
    val selection = mutableStateListOf<Int>()

    val handle = derivedStateOf { // depends on selectionMode & selection
        when (selectionMode.value) {
            is SelectionMode.Drag ->
                if (selection.isEmpty()) null
                else Handle.Radius(selection.single())
            is SelectionMode.Multiselect -> when {
                selection.isEmpty() -> null
                selection.size == 1 -> Handle.Radius(selection.single())
                selection.size > 1 -> Handle.Scale(selection)
                else -> Unit // never
            }
            is SelectionMode.SelectRegion -> null
        }
    }
    val handleIsDown = mutableStateOf(false)
    val grabbedCircleIx = mutableStateOf<Int?>(null)

    // tagged & grouped gap buffer
    private val commands = ArrayDeque<Command>(HISTORY_SIZE)
    private val redoCommands = ArrayDeque<Command>(HISTORY_SIZE)
    // we group history by commands and record it only when the new command differs from the previous one
    // NOTE: history doesn't survive background app kill
    private val history = ArrayDeque<UiState>(HISTORY_SIZE)
    private val redoHistory = ArrayDeque<UiState>(HISTORY_SIZE)

    private val _decayingCircles = MutableSharedFlow<DecayingCircles>()
    val decayingCircles = _decayingCircles.asSharedFlow()

//    val offset = mutableStateOf(Offset.Zero) // pre-scale offset
//    val scale = mutableStateOf(1f)

    init {
        recordCommand(Command.CREATE)
    }

    // navigation
    fun saveAndGoBack() {}
    fun cancelAndGoBack() {}

    fun saveAsJSON(): String {
        val cluster = Cluster(
            circles, parts, true, Color.Cyan, Color.Cyan
        )
        return Json.encodeToString(Cluster.serializer(), cluster)
    }

    fun loadFromJSON(json: String) {
        try {
            val cluster = Json.decodeFromString(Cluster.serializer(), json)
            selection.clear()
            parts.clear()
            circles.clear()
            circles.addAll(cluster.circles)
            parts.addAll(cluster.parts)
        } catch (e: SerializationException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    fun undo() {
        if (history.size > 1) {
            val previousState = history.removeLast()
            val previousCommand = commands.removeLast()
            selection.clear()
            parts.clear()
            circles.clear()
            circles.addAll(previousState.circles)
            parts.addAll(previousState.parts)
            switchSelectionMode(previousState.selectionMode, noAlteringShortcuts = true)
            selection.addAll(previousState.selection)
            redoCommands.addFirst(previousCommand)
            redoHistory.addFirst(previousState)
        }
    }

    fun redo() {
        if (redoHistory.isNotEmpty()) {
            val nextState = redoHistory.removeFirst()
            val nextCommand = redoCommands.removeFirst()
            selection.clear()
            parts.clear()
            circles.clear()
            circles.addAll(nextState.circles)
            parts.addAll(nextState.parts)
            switchSelectionMode(nextState.selectionMode, noAlteringShortcuts = true)
            selection.addAll(nextState.selection)
            commands.addLast(nextCommand)
            history.addLast(nextState)
        }
    }

    private fun recordCommand(command: Command) {
        if (commands.lastOrNull() != command) {
            if (history.size == HISTORY_SIZE) {
                history.removeFirst()
                commands.removeFirst()
            }
            history.addLast(UiState.save(this))
            commands.addLast(command)
        }
        redoCommands.clear()
        redoHistory.clear()
    }

    suspend fun createNewCircle() {
        recordCommand(Command.CREATE)
        val newOne = Circle(200.0, 200.0, 50.0)
        _decayingCircles.emit(
            DecayingCircles(listOf(newOne), Color.Green)
        )
        circles.add(newOne)
        selection.clear()
        selection.add(circles.size - 1)
    }

    suspend fun copyCircles() {
        if (selectionMode.value.isSelectingCircles()) {
            recordCommand(Command.COPY)
            val newOnes = circles.filterIndexed { ix, _ -> ix in selection }
            val oldSize = circles.size
            _decayingCircles.emit(
                DecayingCircles(selection.map { circles[it] }, Color.Blue)
            )
            circles.addAll(newOnes)
            val newParts = parts.filter {
                selection.containsAll(it.insides) && selection.containsAll(it.outsides)
            }.map { part ->
                Cluster.Part(
                    insides = part.insides.map { it + newOnes.size }.toSet(),
                    outsides = part.outsides.map { it + newOnes.size }.toSet()
                )
            }
            parts.addAll(newParts)
            selection.clear()
            selection.addAll(oldSize until (oldSize + newOnes.size))
        }
    }

    suspend fun deleteCircles() {
        fun reindexingMap(originalIndices: IntRange, deletedIndices: Set<Int>): Map<Int, Int> {
            val re = mutableMapOf<Int, Int>()
            var shift = 0
            for (ix in originalIndices) {
                if (ix in deletedIndices)
                    shift += 1
                else
                    re[ix] = ix - shift
            }
            return re
        }
        if (selectionMode.value.isSelectingCircles()) {
            recordCommand(Command.DELETE)
            val whatsGone = selection.toSet()
            val whatsLeft = circles.filterIndexed { ix, _ -> ix !in whatsGone }
            _decayingCircles.emit(
                DecayingCircles(selection.map { circles[it] }, Color.Red)
            )
            val oldParts = parts.toList()
            val reindexing = reindexingMap(circles.indices, whatsGone)
            selection.clear()
            parts.clear()
            circles.clear()
            circles.addAll(whatsLeft)
            parts.addAll(
                oldParts
                    .map { (ins, outs) ->
                        Cluster.Part(
                            insides = ins.minus(whatsGone).map { reindexing[it]!! }.toSet(),
                            outsides = outs.minus(whatsGone).map { reindexing[it]!! }.toSet()
                        )
                    }
                    .filter { (ins, outs) -> ins.isNotEmpty() || outs.isNotEmpty() }
            )
        }
    }

    fun switchSelectionMode(newMode: SelectionMode, noAlteringShortcuts: Boolean = false) {
        if (selection.size > 1 && newMode == SelectionMode.Drag)
            selection.clear()
        if (selectionMode.value == SelectionMode.Multiselect && newMode == SelectionMode.Multiselect) {
            if (selection.isEmpty())
                selection.addAll(circles.indices)
            else
                selection.clear()
        } else if (selectionMode.value == SelectionMode.SelectRegion && newMode == SelectionMode.SelectRegion &&
            !noAlteringShortcuts
        ) {
            if (parts.isEmpty()) {
                recordCommand(Command.SELECT_PART)
                // select interlacing, todo: proper 2^n -> even # of 1's -> {0101001} -> parts
                parts.add(Cluster.Part(emptySet(), circles.indices.toSet()))
            } else {
                recordCommand(Command.SELECT_PART)
                parts.clear()
            }
        }
        selectionMode.value = newMode
    }

    fun selectPoint(targets: List<Offset>, position: Offset): Int? =
        targets.mapIndexed { ix, offset -> ix to (offset - position).getDistance() }
            .filter { (_, distance) -> distance <= SELECTION_EPSILON }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (ix, _) -> ix }

    fun selectCircle(targets: List<Circle>, position: Offset): Int? =
        targets.mapIndexed { ix, circle ->
            ix to ((circle.offset - position).getDistance() - circle.radius).absoluteValue
        }
            .filter { (_, distance) -> distance <= SELECTION_EPSILON }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (ix, _) -> ix }

    fun reselectCircleAt(position: Offset) {
        selectCircle(circles, position)?.let { ix ->
            selection.clear()
            selection.add(ix)
        } ?: selection.clear()
    }

    fun reselectCirclesAt(position: Offset) {
        selectCircle(circles, position)?.let { ix ->
            if (ix in selection)
                selection.remove(ix)
            else
                selection.add(ix)
        }
    }

    fun reselectRegionAt(position: Offset) {
        val delimiters = circles.indices
        val ins = delimiters
            .filter { ix -> circles[ix].checkPosition(position) < 0 }
            .sortedBy { ix -> circles[ix].radius }
        val excessiveIns = ins.indices.filter { ix ->
            (0 until ix).any { smallerRIx -> // being inside imposes constraint on radii
                circles[smallerRIx] isInside circles[ix]
            }
        } // NOTE: these do not take into account more complex "intersection is always inside x" type relationships
        val outs = delimiters
            .filter { ix -> circles[ix].checkPosition(position) > 0 }
            .sortedByDescending { ix -> circles[ix].radius }
        val excessiveOuts = outs.indices.filter { ix ->
            (0 until ix).any { largerRIx ->
                circles[ix] isInside circles[largerRIx]
            }
        }
        val part0 = Cluster.Part(ins.toSet(), outs.toSet())
        val part = Cluster.Part(
            insides = ins.toSet().minus(excessiveIns.toSet()),
            outsides = outs.toSet().minus(excessiveOuts.toSet())
        )
        // BUG: still breaks (doesnt remove proper parts) after several additions & deletions
        val outerParts = parts.filter { part isObviouslyInside it || part0 isObviouslyInside it  }
        if (outerParts.isEmpty()) {
            recordCommand(Command.SELECT_PART)
            parts.add(part)
            println("added $part")
        } else {
            if (part in parts) {
                recordCommand(Command.SELECT_PART)
                parts.remove(part)
                println("removed $part")
            } else {
                recordCommand(Command.SELECT_PART)
                parts.removeAll(outerParts)
                println("removed parts [${outerParts.joinToString(prefix = "\n", separator = ";\n")}]")
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

    // NOTE: to create proper reduce(xor):
    // 2^(# circles) -> binary -> filter even number of 1 -> to parts
    // MAYBE: move to Cluster methods
    fun part2path(part: Cluster.Part): Path {
        fun circle2path(circle: Circle): Path =
            Path().apply {
                addOval(
                    Rect(
                        center = circle.offset,
                        radius = circle.radius.toFloat()
                    )
                )
            }

        val circleInsides = part.insides.map { circles[it] }
        val insidePath: Path? = circleInsides
            .map { circle2path(it) }
            .reduceOrNull { acc: Path, anotherPath: Path ->
                Path.combine(PathOperation.Intersect, path1 = acc, path2 = anotherPath)
            }
        val circleOutsides = part.outsides.map { circles[it] }
        return if (insidePath == null) {
            circleOutsides.map { circle2path(it) }
                // TODO: encapsulate as a separate tool, otherwise it's not deselectable after new circles are added
                // NOTE: reduce(xor) on outsides = makes binary interlacing pattern
                // not what one would expect
                .reduceOrNull { acc: Path, anotherPath: Path ->
                    Path.combine(PathOperation.Xor, acc, anotherPath)
                } ?: Path()
        } else if (part.outsides.isEmpty())
            insidePath
        else
            circleOutsides.fold(insidePath) { acc: Path, circleOutside: Circle ->
                Path.combine(PathOperation.Difference, acc, circle2path(circleOutside))
            }
    }


    // pointer input callbacks
    fun onTap(position: Offset) {
        // select circle(s)/region
        when (selectionMode.value) {
            SelectionMode.Drag -> reselectCircleAt(position)
            SelectionMode.Multiselect -> reselectCirclesAt(position)
            SelectionMode.SelectRegion -> reselectRegionAt(position)
        }
    }

    fun onDown(position: Offset) {
        // no need for onUp since all actions occur after onDown
        handleIsDown.value = when (val h = handle.value) {
            is Handle.Radius -> {
                val circle = circles[h.ix]
                val right = circle.offset + Offset(circle.radius.toFloat(), 0f)
                selectPoint(listOf(right), position) != null
            }
            is Handle.Scale -> {
                val topRight = getSelectionRect().topRight
                selectPoint(listOf(topRight), position) != null
            }
            else -> false // other handles are multiselect's rotate
        }
    }

    // MAYBE: handle key arrows as panning
    fun onPanZoom(pan: Offset, centroid: Offset, zoom: Float) {
        if (handleIsDown.value) {
            // drag handle
            when (val h = handle.value) {
                is Handle.Radius -> {
                    recordCommand(Command.CHANGE_RADIUS)
                    val center = circles[h.ix].offset
                    val r = (centroid - center).getDistance()
                    circles[h.ix] = circles[h.ix].copy(radius = r.toDouble())
                }
                is Handle.Scale -> {
                    recordCommand(Command.SCALE)
                    val selectionRect = getSelectionRect()
                    val topRight = selectionRect.topRight
                    val center = selectionRect.center
                    val centerToTopRight = topRight - center
                    val centerToCurrent = centerToTopRight + pan
                    val handleScale = centerToCurrent.getDistance()/centerToTopRight.getDistance()
                    for (ix in selection) {
                        val circle = circles[ix]
                        val newOffset = (circle.offset - center) * handleScale + center
                        circles[ix] = Circle(newOffset, handleScale * circle.radius)
                    }
                }
                else -> Unit // other handles are multiselect's rotate potentially
            }
        } else {
            when (selectionMode.value) {
                SelectionMode.Drag -> if (selection.isNotEmpty()) { // move + scale radius
                    recordCommand(Command.MOVE)
                    val ix = selection.single()
                    val circle = circles[ix]
                    val newCenter = circle.offset + pan
                    circles[ix] = Circle(newCenter, zoom * circle.radius)
                }
                SelectionMode.Multiselect -> {
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
                            val newOffset = (circle.offset - centroid) * zoom + centroid + pan
                            circles[ix] = Circle(newOffset, zoom * circle.radius)
                        }
                    }
                }
                else -> {}
            }
        }

    }

    fun onVerticalScroll(yDelta: Float) {
        val zoom = (1.01f).pow(yDelta)
        when (selectionMode.value) {
            SelectionMode.Drag -> if (selection.isNotEmpty()) { // move + scale radius
                recordCommand(Command.CHANGE_RADIUS)
                val ix = selection.single()
                val circle = circles[ix]
                circles[ix] = circle.copy(radius = zoom * circle.radius)
            }

            SelectionMode.Multiselect -> {
                if (selection.size == 1) { // move + scale radius
                    recordCommand(Command.CHANGE_RADIUS)
                    val ix = selection.single()
                    val circle = circles[ix]
                    circles[ix] = circle.copy(radius = zoom * circle.radius)
                } else if (selection.size > 1) { // scale radius & position
                    recordCommand(Command.SCALE)
                    val center = getSelectionRect().center
                    for (ix in selection) {
                        val circle = circles[ix]
                        val newOffset = (circle.offset - center) * zoom + center
                        circles[ix] = Circle(newOffset, zoom * circle.radius)
                    }
                }
            }
            else -> {}
        }
    }

    fun onLongDragStart(position: Offset) {
        // draggables = circles
        selectCircle(circles, position)?.let { ix ->
            grabbedCircleIx.value = ix
            if (selectionMode.value == SelectionMode.Drag) {
                selection.clear()
                selection.add(ix)
            }
        } ?: run {
            if (true || selectionMode.value == SelectionMode.SelectRegion) {
                // TODO: select part
                // drag bounding circles
            }
        }
    }

    fun onLongDrag(delta: Offset) {
        // if grabbed smth, do things (regardless of selection)
        grabbedCircleIx.value?.let {
            recordCommand(Command.MOVE)
            val circle = circles[it]
            circles[it] = Circle(circle.offset + delta, circle.radius)
        }
    }

    fun onLongDragCancel() {
        grabbedCircleIx.value = null
    }

    fun onLongDragEnd() {
        grabbedCircleIx.value = null
    }

    @Serializable
    data class UiState(
        val selectionMode: SelectionMode,
        val circles: List<Circle>,
        val parts: List<Cluster.Part>,
        val selection: List<Int>, // circle indices
    ) {
        companion object {
            val DEFAULT = UiState(
                SelectionMode.Drag,
                circles = listOf(
                    Circle(200.0, 200.0, 100.0),
                    Circle(250.0, 200.0, 100.0),
                    Circle(200.0, 250.0, 100.0),
                    Circle(250.0, 250.0, 100.0),
                ),
                parts = emptyList(),
                selection = listOf(0)
            )

            fun restore(uiState: UiState): EditClusterViewModel =
                EditClusterViewModel(
                    Cluster(uiState.circles.toList(), uiState.parts.toList(), true, Color.Black, Color.Black)
                ).apply {
                    selectionMode.value = uiState.selectionMode
                    selection.addAll(uiState.selection)
                }

            fun save(viewModel: EditClusterViewModel): UiState =
                with (viewModel) {
                    UiState(selectionMode.value, circles.toList(), parts.toList(), selection.toList())
                }
        }
    }

    companion object {
        const val SELECTION_EPSILON = 20f
        const val HISTORY_SIZE = 100

        val VMSaver = Saver<EditClusterViewModel, String>(
            save = { viewModel ->
                Json.encodeToString(UiState.serializer(), UiState.save(viewModel))
            },
            restore = { s ->
                UiState.restore(Json.decodeFromString(UiState.serializer(), s))
            }
        )
    }
}

// TODO: 2-phase mode: circle by center and radius
// and 2-phase mode: line by 2 points
@Serializable
sealed class SelectionMode {
    fun isSelectingCircles(): Boolean =
        this in setOf(Drag, Multiselect)

    @Serializable
    data object Drag : SelectionMode()
    @Serializable
    data object Multiselect : SelectionMode()
    @Serializable
    data object SelectRegion : SelectionMode()
}

/** ixs = indices of circles to which the handle is attached */
sealed class Handle(open val ixs: List<Int>) {
    data class Radius(val ix: Int): Handle(listOf(ix))
    data class Scale(override val ixs: List<Int>): Handle(ixs)
//    data class Rotation(override val ixs: List<Int>): Handle(ixs)
}

/** params for copy/delete animations */
data class DecayingCircles(
    val circles: List<Circle>,
    val color: Color
)

/** used for grouping UiState changes into batches for history keeping */
enum class Command {
    MOVE, CHANGE_RADIUS, SCALE, COPY, DELETE, CREATE, SELECT_PART
}
