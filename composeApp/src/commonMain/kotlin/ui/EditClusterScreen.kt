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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import data.Circle
import data.Cluster
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@Composable
fun EditClusterScreen() {
    var selectionMode by remember { mutableStateOf(SelectionMode.DRAG) }
    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            EditClusterTopBar({}, {})
        },
        bottomBar = {
            EditClusterBottomBar(selectionMode, { selectionMode = it }, {}, {}, {})
        },
    ) { inPaddings ->
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
    SELECT_REGION, DRAG, MULTISELECT
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterBottomBar(
    selectionMode: SelectionMode,
    onChooseMode: (SelectionMode) -> Unit,
    onNewCircle: () -> Unit,
    onCopyCircles: () -> Unit,
    onDeleteCircles: () -> Unit,
) {
    BottomAppBar {
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
            currentMode = selectionMode,
            painterResource("icons/drag_mode_1_circle.xml"),
            contentDescription = "drag mode",
            onChooseMode
        )
        ModeToggle(
            SelectionMode.MULTISELECT,
            currentMode = selectionMode,
            painterResource("icons/multiselect_mode_3_intersecting_circles.xml"),
            contentDescription = "multiselect mode",
            onChooseMode
        )
        ModeToggle(
            SelectionMode.SELECT_REGION,
            currentMode = selectionMode,
            painterResource("icons/select_region_mode.xml"),
            contentDescription = "select region mode",
            onChooseMode,
        )
    }
}

@Composable
fun ModeToggle(
    mode: SelectionMode,
    currentMode: SelectionMode,
    painter: Painter,
    contentDescription: String,
    onChooseMode: (SelectionMode) -> Unit
) {
    IconToggleButton(
        checked = currentMode == mode,
        onCheckedChange = {
            onChooseMode(mode)
        },
        modifier = Modifier
            .background(
                if (currentMode == mode)
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
    mode: SelectionMode,
    modifier: Modifier = Modifier
) {
    // adding viewModel impl is pain in the ass
    // waiting for decompose 3.0-stable
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
    var grabbed: GrabTarget? by remember { mutableStateOf(null) }

    var offset by remember { mutableStateOf(Offset.Zero) } // pre-scale offset
    var scale by remember { mutableStateOf(1f) }
    Canvas(
        modifier
            .reactiveCanvas(
                onTap = { position ->
                    // select circle(s)/region
                    when (mode) {
                        SelectionMode.DRAG -> {}
                        SelectionMode.MULTISELECT -> 2
                        SelectionMode.SELECT_REGION -> 3
                    }

                },
                onDown = { position ->
                    // no need for onUp since all actions occur after onDown
                    // if on handle, select handle; otherwise empty d
//                    handleIsDown = true // or false

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
                            else -> Unit
                        }
                    } else {
                        if (mode == SelectionMode.MULTISELECT && selection.isNotEmpty()) {
                            // transform em
                            for (ix in selection) {
                                val circle = circles[ix]
                                val newCenter = circle.offset + pan
                                circles[ix] = Circle(newCenter, zoom * circle.radius)
                            }
                        }
                        else { // only long-press drag in other modes
                            // navigate canvas
//                            offset = offset + pan // ought to be something more sophisticated
                            offset = (offset + centroid/scale) - (centroid/(scale*zoom) + pan/scale)
                            scale *= zoom
                        }
                    }

                },
                onDragStart = {
                    // draggables = circles
                    // grab circle or handle
                },
                onDrag = {
                    // if grabbed smth, do things
                },
                onDragEnd = {
                    // select what grabbed
                }
            )
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
//                translationX = offset.x, translationY = offset.y,
                translationX = -scale*offset.x, translationY = -scale*offset.y,
                transformOrigin = TransformOrigin(0f, 0f)
            )
            .fillMaxSize()
    ) {
        val canvasCenter = Offset(size.width/2f, size.height/2f)
        for (circle in circles) {
            drawCircle(
                color = Color.Black,
                radius = circle.radius.toFloat(),
                center = circle.offset,
                style = Stroke(width = 2f)
            )
        }
        // handles
        if (selection.isEmpty()) {
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
            // todo
        }
    }
}

sealed class Handle : GrabTarget() {
    // ixs = indices of circles to which the handle is attached
    data class Radius(val ix: Int): Handle()
    data class Scale(val ixs: List<Int>): Handle()
    data class Rotation(val ixs: List<Int>): Handle()
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