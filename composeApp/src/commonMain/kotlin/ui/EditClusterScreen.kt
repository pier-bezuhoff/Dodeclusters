package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import data.Circle
import data.Cluster
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.math.absoluteValue
import kotlin.math.pow

@Composable
fun EditClusterScreen() {
    val selectionMode = remember { mutableStateOf(SelectionMode.DRAG) }

    val actionsScope = rememberCoroutineScope()
    val newCircles = MutableSharedFlow<Unit>()
    val copyCircles = MutableSharedFlow<Unit>()
    val deleteCircles = MutableSharedFlow<Unit>()

    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            EditClusterTopBar({}, {})
        },
        bottomBar = {
            EditClusterBottomBar(
                selectionMode,
                { actionsScope.launch { newCircles.emit(Unit) } },
                { actionsScope.launch { copyCircles.emit(Unit) } },
                { actionsScope.launch { deleteCircles.emit(Unit) } },
            )
        },
    ) { inPaddings ->
        // collect the 3 action flows inside
        EditClusterContent(selectionMode, Modifier.padding(inPaddings))
    }
}

@Composable
fun EditClusterTopBar(
    onSaveAndGoBack: () -> Unit,
    onCancelAndGoBack: () -> Unit,
) {
    TopAppBar(
        title = { Text("Edit cluster") },
        navigationIcon = {
            IconButton(onClick = onSaveAndGoBack) {
                Icon(Icons.Default.Done, contentDescription = "Done")
            }
        },
        actions = {
            IconButton(onClick = onCancelAndGoBack) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        }
    )
}

enum class SelectionMode {
    SELECT_REGION, DRAG, MULTISELECT;

    fun isSelectingCircles(): Boolean =
        this in setOf(DRAG, MULTISELECT)
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterBottomBar(
    selectionMode: MutableState<SelectionMode>,
    onNewCircle: () -> Unit,
    onCopyCircles: () -> Unit,
    onDeleteCircles: () -> Unit,
) {
    BottomAppBar {
        // flows for each action
        IconButton(onClick = onNewCircle) {
            Icon(Icons.Default.AddCircle, contentDescription = "new circle")
        }
        IconButton(onClick = onCopyCircles) {
            Icon(painterResource("icons/copy.xml"), contentDescription = "copy circle(s)")
        }
        IconButton(onClick = onDeleteCircles) {
            Icon(Icons.Default.Delete, contentDescription = "delete circle(s)")
        }
        Divider(
            Modifier
                .padding(8.dp)
                .fillMaxHeight()
                .width(4.dp)
        )
        // MAYBE: select regions within multiselect
        ModeToggle(
            SelectionMode.DRAG,
            selectionMode,
            painterResource("icons/drag_mode_1_circle.xml"),
            contentDescription = "drag mode",
        )
        ModeToggle(
            SelectionMode.MULTISELECT,
            selectionMode,
            painterResource("icons/multiselect_mode_3_intersecting_circles.xml"),
            contentDescription = "multiselect mode",
        )
        ModeToggle(
            SelectionMode.SELECT_REGION,
            selectionMode,
            painterResource("icons/select_region_mode.xml"),
            contentDescription = "select region mode",
        )
    }
}

@Composable
fun ModeToggle(
    mode: SelectionMode,
    currentMode: MutableState<SelectionMode>,
    painter: Painter,
    contentDescription: String,
) {
    IconToggleButton(
        checked = currentMode.value == mode,
        onCheckedChange = {
            currentMode.value = mode
        },
        modifier = Modifier
            .background(
                if (currentMode.value == mode)
                    MaterialTheme.colors.primaryVariant
                else
                    MaterialTheme.colors.primary,
            )
    ) {
        Icon(
            painter,
            contentDescription = contentDescription,
        )
    }
    Spacer(Modifier.fillMaxHeight().width(8.dp)) // horizontal margin
}


@Composable
fun EditClusterContent(
    mode: State<SelectionMode>,
    modifier: Modifier = Modifier
) {
    // NOTE: adding viewModel impl is pain in the ass
    // waiting for decompose 3.0-stable
    // (use state flows in vM + in ui convert to state with .collectAsStateWithLc)
    val circles = remember { mutableStateListOf<Circle>() }
    circles.addAll(listOf(
        Circle(150.0, 50.0, 50.0),
        Circle(50.0, 30.0, 10.0),
    ))
    val parts = remember { mutableStateListOf<Cluster.Part>() }
    // TODO: history of Cluster snapshots for undo
    // indices of selected circles
    val selection = remember { mutableStateListOf<Int>() }
    selection.add(0)
    var handle: Handle? by remember { mutableStateOf(null) }
    var handleIsDown: Boolean by remember { mutableStateOf(false) }
    var grabbedCircleIx: Int? by remember { mutableStateOf(null) }

    var offset by remember { mutableStateOf(Offset.Zero) } // pre-scale offset
    var scale by remember { mutableStateOf(1f) }

    val epsilon = 20f
    fun selectPoint(targets: List<Offset>, position: Offset): Int? =
        targets.mapIndexed { ix, offset -> ix to (offset - position).getDistance() }
            .filter { (_, distance) -> distance <= epsilon }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (ix, _) ->
//                println("selected point")
                ix
            }
    fun selectCircle(targets: List<Circle>, position: Offset): Int? =
        targets.mapIndexed { ix, circle ->
            ix to ((circle.offset - position).getDistance() - circle.radius).absoluteValue
        }
            .filter { (_, distance) -> distance <= epsilon }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (ix, _) ->
//                println("selected circle")
                ix
            }

    Canvas(
        modifier
            .reactiveCanvas(
                onTap = { position ->
                    // select circle(s)/region
                    when (mode.value) {
                        SelectionMode.DRAG -> {
                            selectCircle(circles, position)?.let { ix ->
                                selection.clear()
                                selection.add(ix)
                            } ?: selection.clear()
                        }
                        SelectionMode.MULTISELECT -> {
                            selectCircle(circles, position)?.let { ix ->
                                if (ix in selection)
                                    selection.remove(ix)
                                else
                                    selection.add(ix)
                            }
                        }
                        SelectionMode.SELECT_REGION -> 3
                    }
                },
                onDown = { position ->
                    // no need for onUp since all actions occur after onDown
                    // if on handle, select handle; otherwise empty d
                    handleIsDown = when (val h = handle) {
                        is Handle.Radius -> {
                            val circle = circles[h.ix]
                            val right = circle.offset + Offset(circle.radius.toFloat(), 0f)
                            selectPoint(listOf(right), position) != null
                        }
                        else -> false // other handles are multiselect's scale & rotate
                    }

                },
                onPanZoom = { pan, centroid, zoom ->
                    if (handleIsDown) {
                        // drag handle
                        when (val h = handle) {
                            is Handle.Radius -> {
                                val center = circles[h.ix].offset
                                val r = (centroid - center).getDistance()
                                circles[h.ix] = circles[h.ix].copy(radius = r.toDouble())
                            }
                            else -> Unit // other handles are multiselect's scale & rotate
                        }
                    } else {
                        if (mode.value.isSelectingCircles() && selection.isNotEmpty()) {
                            // transform em
                            for (ix in selection) {
                                val circle = circles[ix]
                                val newCenter = circle.offset + pan
                                circles[ix] = Circle(newCenter, zoom * circle.radius)
                            }
                        }
                        else { // only long-press drag in other modes
                            // navigate canvas
                            offset = (offset + centroid/scale) - (centroid/(scale*zoom) + pan/scale)
                            scale *= zoom // MAYBE: add zoom slider for non-mobile
                        }
                    }

                },
                onVerticalScroll = { yDelta ->
                    // maybe scale as onPanZoom
                    if (mode.value.isSelectingCircles() && selection.isNotEmpty()) {
                        for (ix in selection) {
                            val circle = circles[ix]
                            val r = circle.radius * (1.01f).pow(yDelta)
                            circles[ix] = circle.copy(radius = r)
                        }
                    }
                },
                onLongDragStart = { position ->
                    // draggables = circles
                    if (mode.value.isSelectingCircles()) {
                        selectCircle(circles, position)?.let { ix ->
                            grabbedCircleIx = ix
                            if (mode.value == SelectionMode.DRAG) {
                                selection.clear()
                                selection.add(ix)
                            }
                        }
                    }
                },
                onLongDrag = { delta ->
                    // if grabbed smth, do things (regardless of selection)
                    grabbedCircleIx?.let {
                        val circle = circles[it]
                        circles[it] = Circle(circle.offset + delta, circle.radius)
                    }
                },
                onLongDragCancel = {
                    grabbedCircleIx = null
                },
                onLongDragEnd = {
                    // MAYBE: select what grabbed
                    grabbedCircleIx = null
                }
            )
//            .graphicsLayer(
//                scaleX = scale, scaleY = scale,
//                translationX = -scale*offset.x, translationY = -scale*offset.y,
//                transformOrigin = TransformOrigin(0f, 0f)
//            )
            .fillMaxSize()
    ) {
        for (circle in circles) {
            drawCircle(
                color = Color.Black,
                radius = circle.radius.toFloat(),
                center = circle.offset,
                style = Stroke(width = 2f)
            )
        }
        // handles
        if (selection.isEmpty() || !mode.value.isSelectingCircles()) {
            handle = null
        } else if (selection.size == 1) {
            handle = Handle.Radius(selection.single())
            val selectedCircle = circles[selection[0]]
            val right = selectedCircle.offset + Offset(selectedCircle.radius.toFloat(), 0f)
            drawLine(
                color = Color.DarkGray,
                selectedCircle.offset,
                right,
            )
            drawCircle(
                color = Color.Gray,
                radius = 7f,
                center = right
            )
        } else if (selection.size > 1) {
            handle = Handle.Scale(selection)
            // TODO: show rect + handle in a corner
        }
    }
}

sealed class Handle(open val ixs: List<Int>) : GrabTarget() {
    // ixs = indices of circles to which the handle is attached
    data class Radius(val ix: Int): Handle(listOf(ix))
    data class Scale(override val ixs: List<Int>): Handle(ixs)
    data class Rotation(override val ixs: List<Int>): Handle(ixs)
}

sealed class GrabTarget {
    data class CircleN(val ix: Int) : GrabTarget()
}

enum class Command {
    MOVE, CHANGE_RADIUS, SCALE, COPY, DELTE, CREATE
}
// save latest cmd
// and history of Cluster snapshots
// if new cmd != last cmd => push new Cluster state