package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animatePanBy
import androidx.compose.foundation.gestures.awaitEachGesture
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

    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    // context menu for selection: scale/rot
    var offset by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .pointerInput(selection) {
                awaitEachGesture {
                    awaitPointerEvent().also { event ->
                        if (event.type == PointerEventType.Scroll) {
                            val verticalScroll = event.changes.map { it.scrollDelta.y }.sum()
                            val zoom = 1.05f.pow(verticalScroll)
                            event.changes.forEach { it.consume() }
                            for (ix in selection) {
                                val circle = circles[ix]
                                circles[ix] = circle.copy(radius = zoom*circle.radius)
                            }
                        }
                    }
                }
//                detectTransformGestures { centroid, pan, zoom, rotation ->
////                    println("$centroid, $pan, $zoom")
//                    for (ix in selection) {
//                        val circle = circles[ix]
//                        val newCenter = circle.offset + pan
//                        circles[ix] = Circle(newCenter, zoom*circle.radius)
//                    }
//                }
            }
            .fillMaxSize()
    ) {
        for (circle in circles) {
            drawCircle(
                color = Color.Black,
                radius = circle.radius.toFloat(),
                center = Offset(
                    size.width/2 + circle.x.toFloat(),
                    size.height/2 + circle.y.toFloat()
                ),
                style = Stroke(width = 2f)
            )

        }
    }
}