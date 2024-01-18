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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.absoluteValue
import kotlin.math.pow

// NOTE: waiting for decompose 3.0-stable for a real VM impl
// MAYBE: use state flows in vM + in ui convert to state with .collectAsStateWithLifecycle
// TODO: history of Cluster snapshots for undo
class EditClusterViewModel(
    cluster: Cluster = Cluster.SAMPLE
) {
    val selectionMode = mutableStateOf(SelectionMode.DRAG)
    val circles = mutableStateListOf(*cluster.circles.toTypedArray())
    val parts = mutableStateListOf(*cluster.parts.toTypedArray())
    /** indices of selected circles */
    val selection = mutableStateListOf<Int>()

    val handle = derivedStateOf {
        when (selectionMode.value) {
            SelectionMode.DRAG ->
                if (selection.isEmpty()) null
                else Handle.Radius(selection.single())
            SelectionMode.MULTISELECT -> when {
                selection.isEmpty() -> null
                selection.size == 1 -> Handle.Radius(selection.single())
                // TODO: show rect + handle in a corner
                selection.size > 1 -> Handle.Scale(selection)
                else -> Unit // never
            }
            SelectionMode.SELECT_REGION -> null
        }
    }
    val handleIsDown = mutableStateOf(false)
    val grabbedCircleIx = mutableStateOf<Int?>(null)

    private val commands = ArrayDeque<Command>(HISTORY_SIZE)
    // we group history by commands and record it only when the new command differs from the previous one
    private val history = ArrayDeque<UiState>(HISTORY_SIZE)

    val offset = mutableStateOf(Offset.Zero) // pre-scale offset
    val scale = mutableStateOf(1f)

    // navigation
    fun saveAndGoBack() {}
    fun cancelAndGoBack() {}

    fun undo() {
        if (history.isNotEmpty()) {
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
            history.add(UiState.save(this))
            commands.add(command)
        }
    }

    fun newCircle() {
        val newOne = Circle(200.0, 200.0, 50.0)
        circles.add(newOne)
        selection.clear()
        selection.add(circles.size - 1)
        recordCommand(Command.CREATE)
    }

    fun copyCircles() {
        val newOnes = circles.filterIndexed { ix, _ -> ix in selection }
        val oldSize = circles.size
        circles.addAll(newOnes)
        selection.clear()
        selection.addAll(oldSize until (oldSize + newOnes.size))
        // copy parts that consist only of selected circles
        recordCommand(Command.COPY)
    }

    fun deleteCircles() {
        val targetIxs = selection.toSet()
        val whatsLeft = circles.filterIndexed { ix, _ -> ix !in targetIxs }
        selection.clear()
        circles.clear()
        circles.addAll(whatsLeft)
        // also delete them from parts
        recordCommand(Command.DELETE)
    }

    fun switchSelectionMode(newMode: SelectionMode) {
        if (selection.size > 1 && newMode == SelectionMode.DRAG)
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

    fun getSelectionRect(): Rect {
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
            SelectionMode.DRAG -> reselectCircleAt(position)
            SelectionMode.MULTISELECT -> reselectCirclesAt(position)
            SelectionMode.SELECT_REGION -> 3
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
            else -> false // other handles are multiselect's scale & rotate
        }
    }

    fun onPanZoom(pan: Offset, centroid: Offset, zoom: Float) {
        if (handleIsDown.value) {
            // drag handle
            when (val h = handle.value) {
                is Handle.Radius -> {
                    val center = circles[h.ix].offset
                    val r = (centroid - center).getDistance()
                    circles[h.ix] = circles[h.ix].copy(radius = r.toDouble())
                    recordCommand(Command.CHANGE_RADIUS)
                }
                is Handle.Scale -> {
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
                    recordCommand(Command.SCALE)
                }
                else -> Unit // other handles are multiselect's scale & rotate
            }
        } else {
            when (selectionMode.value) {
                SelectionMode.DRAG -> if (selection.isNotEmpty()) { // move + scale radius
                    val ix = selection.single()
                    val circle = circles[ix]
                    val newCenter = circle.offset + pan
                    circles[ix] = Circle(newCenter, zoom * circle.radius)
                    recordCommand(Command.MOVE)
                }
                SelectionMode.MULTISELECT -> {
                    if (selection.size == 1) { // move + scale radius
                        val ix = selection.single()
                        val circle = circles[ix]
                        val newCenter = circle.offset + pan
                        circles[ix] = Circle(newCenter, zoom * circle.radius)
                        recordCommand(Command.MOVE)
                    } else if (selection.size > 1) { // scale radius & position
                        for (ix in selection) {
                            val circle = circles[ix]
                            val newOffset = (circle.offset - centroid) * zoom + centroid + pan
                            circles[ix] = Circle(newOffset, zoom * circle.radius)
                        }
                        recordCommand(Command.MOVE)
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
//        when (selectionMode.value) {
//            SelectionMode.DRAG -> if (selection.isNotEmpty()) { // move + scale radius
//                val ix = selection.single()
//                val circle = circles[ix]
//                circles[ix] = circle.copy(radius = zoom * circle.radius)
//                recordCommand(Command.MOVE)
//            }
//
//            SelectionMode.MULTISELECT -> {
//                if (selection.size == 1) { // move + scale radius
//                    val ix = selection.single()
//                    val circle = circles[ix]
//                    circles[ix] = circle.copy(radius = zoom * circle.radius)
//                    recordCommand(Command.MOVE)
//                } else if (selection.size > 1) { // scale radius & position
//                    val center = getSelectionRect().center
//                    for (ix in selection) {
//                        val circle = circles[ix]
//                        val newOffset = (circle.offset - center) * zoom + center
//                        circles[ix] = Circle(newOffset, zoom * circle.radius)
//                    }
//                    recordCommand(Command.MOVE)
//                }
//            }
//
//            else -> {
//                // navigate canvas
////                    offset.value = (offset.value + centroid/scale.value) - (centroid/(scale.value*zoom) + pan/scale.value)
////                    scale.value *= zoom // MAYBE: add zoom slider for non-mobile
//            }
//        }
        if (selectionMode.value.isSelectingCircles() && selection.isNotEmpty()) {
            for (ix in selection) {
                val circle = circles[ix]
                val r = circle.radius * (1.01f).pow(yDelta)
                circles[ix] = circle.copy(radius = r)
            }
            recordCommand(Command.CHANGE_RADIUS)
        }
    }

    fun onLongDragStart(position: Offset) {
        // draggables = circles
        if (selectionMode.value.isSelectingCircles()) {
            selectCircle(circles, position)?.let { ix ->
                grabbedCircleIx.value = ix
                if (selectionMode.value == SelectionMode.DRAG) {
                    selection.clear()
                    selection.add(ix)
                }
            }
        }
    }

    fun onLongDrag(delta: Offset) {
        // if grabbed smth, do things (regardless of selection)
        grabbedCircleIx.value?.let {
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
                SelectionMode.DRAG,
                circles = listOf(
                    Circle(150.0, 50.0, 50.0),
                    Circle(50.0, 30.0, 10.0),
                ),
                parts = emptyList(),
                selection = listOf(0)
            )

            fun restore(uiState: UiState): EditClusterViewModel =
                EditClusterViewModel(
                    Cluster(uiState.circles, uiState.parts, true, Color.Black, Color.Black)
                ).apply {
                    selectionMode.value = uiState.selectionMode
                    selection.addAll(uiState.selection)
                }

            fun save(viewModel: EditClusterViewModel): UiState =
                with (viewModel) {
                    UiState(selectionMode.value, circles, parts, selection)
                }
        }
    }

    companion object {
        const val SELECTION_EPSILON = 20f
        const val HISTORY_SIZE = 5

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

sealed class Handle(open val ixs: List<Int>) {
    // ixs = indices of circles to which the handle is attached
    data class Radius(val ix: Int): Handle(listOf(ix))
    data class Scale(override val ixs: List<Int>): Handle(ixs)
    data class Rotation(override val ixs: List<Int>): Handle(ixs)
}

enum class Command {
    MOVE, CHANGE_RADIUS, SCALE, COPY, DELETE, CREATE
}
// save latest cmd
// and history of Cluster snapshots
// if new cmd != last cmd => push new Cluster state
