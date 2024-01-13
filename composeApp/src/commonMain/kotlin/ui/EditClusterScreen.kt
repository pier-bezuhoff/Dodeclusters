package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@Composable
fun EditClusterScreen() {
    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            EditClusterTopBar({}, {})
        },
        bottomBar = {
            EditClusterBottomBar({}, {}, {})
        },
    ) { inPaddings ->
        EditClusterContent(Modifier.padding(inPaddings))
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
    onNewCircle: () -> Unit,
    onCopyCircles: () -> Unit,
    onDeleteCircles: () -> Unit,
) {
    var selectionMode by remember { mutableStateOf(SelectionMode.DRAG) }
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
        ) { selectionMode = it }
        ModeToggle(
            SelectionMode.DRAG,
            currentMode = selectionMode,
            painterResource("icons/drag_mode_1_circle.xml"),
            contentDescription = "drag mode",
        ) { selectionMode = it }
        ModeToggle(
            SelectionMode.MULTISELECT,
            currentMode = selectionMode,
            painterResource("icons/multiselect_mode_3_intersecting_circles.xml"),
            contentDescription = "multiselect mode",
        ) { selectionMode = it }
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

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterContent(modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    // context menu for selection: scale/rot
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
        scale *= zoomChange
        rotation += rotationChange
        offset += offsetChange
    }
    Box(modifier.fillMaxSize()) {
        Text(
            "Hello Hi",
            modifier = modifier
        )
        Image(
            painterResource("circle.xml"),
            colorFilter = ColorFilter.tint(Color.Black),
            contentDescription = "circle",
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = state)
        )
    }
}