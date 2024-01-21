package ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import data.Circle
import data.Cluster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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

    private val commands = ArrayDeque<Command>(HISTORY_SIZE)
    // we group history by commands and record it only when the new command differs from the previous one
    // NOTE: history doesn't survive background app kill
    private val history = ArrayDeque<UiState>(HISTORY_SIZE)

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

    fun undo() {
        if (history.size > 1) {
            val previousState = history.removeLast()
            selection.clear()
            parts.clear()
            circles.clear()
            circles.addAll(previousState.circles)
            parts.addAll(previousState.parts)
            switchSelectionMode(previousState.selectionMode)
            selection.addAll(previousState.selection)
            commands.removeLast()
        }
    }

    private fun recordCommand(command: Command) {
        if (commands.lastOrNull() != command) {
            if (history.size == HISTORY_SIZE) {
                history.removeFirst()
                commands.removeFirst()
            }
            history.add(UiState.save(this))
            commands.add(command)
        }
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
        recordCommand(Command.COPY)
        val newOnes = circles.filterIndexed { ix, _ -> ix in selection }
        val oldSize = circles.size
        _decayingCircles.emit(
            DecayingCircles(selection.map { circles[it] }, Color.Blue)
        )
        circles.addAll(newOnes)
        selection.clear()
        selection.addAll(oldSize until (oldSize + newOnes.size))
        // copy parts that consist only of selected circles
    }

    suspend fun deleteCircles() {
        recordCommand(Command.DELETE)
        val targetIxs = selection.toSet()
        val whatsLeft = circles.filterIndexed { ix, _ -> ix !in targetIxs }
        _decayingCircles.emit(
            DecayingCircles(selection.map { circles[it] }, Color.Red)
        )
        selection.clear()
        circles.clear()
        circles.addAll(whatsLeft)
        // also delete them from parts
    }

    fun switchSelectionMode(newMode: SelectionMode) {
        if (selection.size > 1 && newMode == SelectionMode.Drag)
            selection.clear()
        if (selectionMode.value == SelectionMode.Multiselect && newMode == SelectionMode.Multiselect)
            selection.clear()
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
        val delimiters = circles.indices //selection.ifEmpty { circles.indices }
        val ins = delimiters.filter { ix -> circles[ix].checkPosition(position) < 0 }
        val outs = delimiters.filter { ix -> circles[ix].checkPosition(position) > 0 }
        val part = Cluster.Part(ins.toSet(), outs.toSet())
        if (!parts.any { part isObviouslyInside it }) {
            recordCommand(Command.SELECT_PART)
            parts.add(part)
            println("in: " + part.insides.joinToString() + "; out: " + part.outsides.joinToString())
        } else if (part in parts) {
            recordCommand(Command.SELECT_PART)
            parts.remove(part)
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
        // if on handle, select handle; otherwise empty d
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
                else -> {
                    // navigate canvas
//                    offset.value = (offset.value + centroid/scale.value) - (centroid/(scale.value*zoom) + pan/scale.value)
//                    scale.value *= zoom // MAYBE: add zoom slider for non-mobile
                }
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
        if (selectionMode.value.isSelectingCircles()) {
            selectCircle(circles, position)?.let { ix ->
                grabbedCircleIx.value = ix
                if (selectionMode.value == SelectionMode.Drag) {
                    selection.clear()
                    selection.add(ix)
                }
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
                    Circle(150.0, 50.0, 50.0),
                    Circle(50.0, 30.0, 10.0),
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
