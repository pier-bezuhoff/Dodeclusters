package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max

@Composable
fun EditClusterScreen() {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = rememberSaveable(saver = EditClusterViewModel.VMSaver) {
        EditClusterViewModel.UiState.restore(EditClusterViewModel.UiState.DEFAULT)
    }

    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            EditClusterTopBar(viewModel)
        },
        bottomBar = {
            EditClusterBottomBar(viewModel)
        },
    ) { inPaddings ->
        // collect the 3 action flows inside
        Surface {
            EditClusterContent(viewModel, Modifier.padding(inPaddings))
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterTopBar(
    viewModel: EditClusterViewModel
) {
    TopAppBar(
        title = { Text("Edit cluster") },
        navigationIcon = {
//            IconButton(onClick = viewModel::saveAndGoBack) {
//                Icon(Icons.Default.Done, contentDescription = "Done")
//            }
        },
        actions = {
            IconButton(onClick = viewModel::undo) {
                Icon(painterResource("icons/undo.xml"), contentDescription = "Undo")
            }
//            IconButton(onClick = viewModel::cancelAndGoBack) {
//                Icon(Icons.Default.Close, contentDescription = "Cancel")
//            }
        }
    )
}

enum class CreateAction {
    CIRCLE_BY_CENTER_AND_RADIUS,
//    CIRCLE_BY_3_POINTS,
    LINE_BY_2_POINTS
}

data class CreateActionParams(
    val icon: ImageVector,
    val contentDescription: String,
    val callback: () -> Unit,
)

@OptIn(ExperimentalResourceApi::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditClusterBottomBar(
    viewModel: EditClusterViewModel,
) {
    // create x = a new mode
    var createMenuIsExpanded by remember { mutableStateOf(false) }
    var createAction by remember { mutableStateOf(CreateAction.CIRCLE_BY_CENTER_AND_RADIUS) }
    val createActionParams = remember { mapOf(
        CreateAction.CIRCLE_BY_CENTER_AND_RADIUS to CreateActionParams(Icons.Default.AddCircle, "Circle by center and radius") { println("c1"); viewModel.newCircle() },
//        CreateAction.CIRCLE_BY_3_POINTS to CreateActionParams(Icons.Default.AccountCircle, "Circle by 3 points") { println("c2") },
        CreateAction.LINE_BY_2_POINTS to CreateActionParams(Icons.Default.ArrowForward, "Line by 2 points") { println("l1") },
    ) }
    BottomAppBar {
        Box {
            IconButton(
                onClick = {
//                        createActionParams[createAction]!!.callback
                    createMenuIsExpanded = true
                }
            ) {
                Icon(createActionParams[createAction]!!.icon, contentDescription = createActionParams[createAction]!!.contentDescription)
            }
            DropdownMenu(
                expanded = createMenuIsExpanded,
                onDismissRequest = {
                    createMenuIsExpanded = false
                },
            ) {
                CreateAction.entries.forEach { action ->
                    val (icon, contentDescription, callback) = createActionParams[action]!!
                    DropdownMenuItem(
                        onClick = {
                            createAction = action
                            createMenuIsExpanded = false
                            callback()
                        },
//                        Modifier.wrapContentWidth(),
                    ) {
                        Row() {
                            Icon(icon, contentDescription = contentDescription)
                            Text(contentDescription, Modifier.padding(4.dp), overflow = TextOverflow.Visible, softWrap = false, maxLines = 1)
                        }
                    }
                }
            }
        }

        IconButton(onClick = viewModel::copyCircles) {
            Icon(painterResource("icons/copy.xml"), contentDescription = "copy circle(s)")
        }
        IconButton(onClick = viewModel::deleteCircles) {
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
            viewModel,
            painterResource("icons/drag_mode_1_circle.xml"),
            contentDescription = "drag mode",
        )
        ModeToggle(
            SelectionMode.MULTISELECT,
            viewModel,
            painterResource("icons/multiselect_mode_3_scattered_circles.xml"),
            contentDescription = "multiselect mode",
        )
        ModeToggle(
            SelectionMode.SELECT_REGION,
            viewModel,
            painterResource("icons/select_region_mode.xml"),
            contentDescription = "select region mode",
        )
    }
}

@Composable
fun ModeToggle(
    mode: SelectionMode,
    viewModel: EditClusterViewModel,
    painter: Painter,
    contentDescription: String,
) {
    IconToggleButton(
        checked = viewModel.selectionMode.value == mode,
        onCheckedChange = {
            viewModel.switchSelectionMode(mode)
        },
        modifier = Modifier
            .background(
                if (viewModel.selectionMode.value == mode)
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
    viewModel: EditClusterViewModel,
    modifier: Modifier = Modifier
) {
    val circleStroke = remember { Stroke(width = 2f) }
    val circleFill = remember { Fill }
    val dottedStroke = remember { Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    ) }
    val backgroundColor = Color.White
    val selectionLinesColor = Color.Gray
    val selectionMarkingsColor = Color.DarkGray // center-radius line / bounding rect of selection
    val handleColor = Color.Gray
    val handleRadius = 8f
    Canvas(
        modifier.fillMaxSize()
            // selection hatching lines, 45deg
            .drawBehind {
                val (w, h) = size
                val maxDimension = max(w, h)
                val halfNLines = 200 // not all of them are visible, since we are simplifying to a square
                val nLines = 2*halfNLines
                for (i in 0 until halfNLines) {
                    val x = (i+1).toFloat()/halfNLines*maxDimension
                    val y = (i+1).toFloat()/halfNLines*maxDimension
                    drawLine(selectionLinesColor, start = Offset(0f, y), end = Offset(x, 0f))
                    drawLine(selectionLinesColor, start = Offset(x, maxDimension), end = Offset(maxDimension, y))
                }
            }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) // crucial for proper blending
    ) {
        drawRect(backgroundColor)
        // overlay w/ selected circles
        for (ix in viewModel.selection) {
            val circle = viewModel.circles[ix]
            drawCircle( // alpha = where selection lines are shown
                color = Color.Black,
                radius = circle.radius.toFloat(),
                center = circle.offset,
                style = circleFill,
                blendMode = BlendMode.DstOut, // dst out = erasze the BG rectangle => show hatching thats drawn behind it
            )
        }
    }
    Canvas(
        modifier
            .reactiveCanvas(
                onTap = viewModel::onTap,
                onDown = viewModel::onDown,
                onPanZoom = viewModel::onPanZoom,
                onVerticalScroll = viewModel::onVerticalScroll,
                onLongDragStart = viewModel::onLongDragStart,
                onLongDrag = viewModel::onLongDrag,
                onLongDragCancel = viewModel::onLongDragCancel,
                onLongDragEnd = viewModel::onLongDragEnd,
            )
            // this VVV aint good
//            .graphicsLayer(
//                scaleX = scale, scaleY = scale,
//                translationX = -scale*offset.x, translationY = -scale*offset.y,
//                transformOrigin = TransformOrigin(0f, 0f)
//            )
            .fillMaxSize()
    ) {
        for (circle in viewModel.circles) {
            drawCircle(
                color = Color.Black,
                radius = circle.radius.toFloat(),
                center = circle.offset,
                style = circleStroke,
            )
        }
        // handles
        when (viewModel.handle.value) {
            is Handle.Radius -> {
                val selectedCircle = viewModel.circles[viewModel.selection.single()]
                val right = selectedCircle.offset + Offset(selectedCircle.radius.toFloat(), 0f)
                drawLine(
                    color = selectionMarkingsColor,
                    selectedCircle.offset,
                    right,
                )
                drawCircle(
                    color = handleColor,
                    radius = handleRadius,
                    center = right
                )
            }
            is Handle.Scale -> {
                val selectionRect = viewModel.getSelectionRect()
                drawRect(
                    color = selectionMarkingsColor,
                    topLeft = selectionRect.topLeft,
                    size = selectionRect.size,
                    style = dottedStroke,
                )
                drawCircle(
                    color = handleColor,
                    radius = handleRadius,
                    center = selectionRect.topRight,
                )
            }
        }
    }
}
