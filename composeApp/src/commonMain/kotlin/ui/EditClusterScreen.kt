package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animatePanBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import data.Circle
import data.Cluster
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.math.pow

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
            SelectionMode.SELECT_REGION,
            currentMode = selectionMode,
            painterResource("icons/select_region_mode.xml"),
            contentDescription = "select region mode",
            onChooseMode,
        )
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
    val circles = remember { mutableStateListOf<Circle>() }
    circles.addAll(listOf(
        Circle(100.0, 0.0, 50.0),
        Circle(0.0, 30.0, 10.0),
    ))
    val parts = remember { mutableStateListOf<Cluster.Part>() }
    val selection = remember { mutableStateListOf<Int>() }
    selection.add(0)
    var handle by remember { mutableStateOf(Handle.NONE) }

    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    // context menu for selection: scale/rot
    var offset by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .pointerInput(selection) {
                // add zoom handle for desktop
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    for (ix in selection) {
                        val circle = circles[ix]
                        val newCenter = circle.offset + pan
                        circles[ix] = Circle(newCenter, zoom*circle.radius)
                    }
                }
                detectDragGestures(
                    onDragStart = { position ->
                        1 // grab smth
                    },
                ) { change, dragAmount ->
                    // if grabbed smth, do things
                }
            }
            .fillMaxSize()
    ) {
        val canvasCenter = Offset(size.width/2f, size.height/2f)
        for (circle in circles) {
            drawCircle(
                color = Color.Black,
                radius = circle.radius.toFloat(),
                center = canvasCenter + circle.offset,
                style = Stroke(width = 2f)
            )
        }
        // handles
        if (selection.isEmpty()) {
            handle = Handle.NONE
        } else if (selection.size == 1) {
            handle = Handle.RADIUS
            val selectedCircle = circles[selection[0]]
            val right = canvasCenter + selectedCircle.offset + Offset(selectedCircle.radius.toFloat(), 0f)
            drawLine(
                color = Color.DarkGray,
                canvasCenter + selectedCircle.offset,
                right,
            )
            drawCircle(
                color = Color.Gray,
                radius = 7f,
                center = right
            )
        } else if (selection.size > 1) {
            handle = Handle.SCALE
            // todo
        }
    }
}

enum class Handle {
    NONE, RADIUS, SCALE, ROTATION
}