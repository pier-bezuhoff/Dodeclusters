package ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import data.ClusterRepository
import data.io.OpenFileButton
import data.io.SaveData
import data.io.SaveFileButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import ui.colorpicker.ClassicColorPicker
import ui.colorpicker.HsvColor

// TODO: left & right toolbar for landscape orientation instead of top & bottom
@Composable
fun EditClusterScreen(sampleIndex: Int? = null) {
    val coroutineScope = rememberCoroutineScope()
    val clusterRepository = remember { ClusterRepository() }
    val saver = remember { EditClusterViewModel.Saver(coroutineScope) }
    // test bg kills more
    val viewModel = rememberSaveable(saver = saver) {
        EditClusterViewModel.UiState.restore(coroutineScope, EditClusterViewModel.UiState.DEFAULT)
    }
    viewModel.setEpsilon(LocalDensity.current)

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
        Surface {
            EditClusterCanvas(viewModel, Modifier.padding(inPaddings))
        }
    }

    coroutineScope.launch {
        if (sampleIndex != null) {
            clusterRepository.loadSampleClusterJson(sampleIndex) { json ->
                if (json != null) {
                    viewModel.loadFromJSON(json)
                }
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterTopBar(viewModel: EditClusterViewModel) {
    TopAppBar(
        title = { Text("Edit cluster") },
        navigationIcon = {
//            IconButton(onClick = viewModel::saveAndGoBack) {
//                Icon(Icons.Default.Done, contentDescription = "Done")
//            }
        },
        actions = {
            SaveFileButton(painterResource("icons/save.xml"), "Save",
                saveDataProvider = { SaveData(
                    "cluster", "ddc", viewModel.saveAsJSON()) }
            ) {
                println(if (it) "saved" else "not saved")
            }
            OpenFileButton(painterResource("icons/open_file.xml"), "Open file") { content ->
                content?.let {
                    viewModel.loadFromJSON(content)
                }
            }
            IconButton(onClick = viewModel::undo, enabled = viewModel.undoIsEnabled) {
                Icon(painterResource("icons/undo.xml"), contentDescription = "Undo")
            }
            IconButton(onClick = viewModel::redo, enabled = viewModel.redoIsEnabled) {
                Icon(painterResource("icons/redo.xml"), contentDescription = "Redo")
            }
//            IconButton(onClick = viewModel::cancelAndGoBack) {
//                Icon(Icons.Default.Close, contentDescription = "Cancel")
//            }
        }
    )
}

// TODO: can overflow on mobile, make it scrollable LazyRow or smth
@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterBottomBar(viewModel: EditClusterViewModel) {
    BottomAppBar {
        ModeToggle(
            SelectionMode.Drag,
            viewModel,
            painterResource("icons/drag_mode_1_circle.xml"),
            contentDescription = "drag mode",
        )
        // MAYBE: select regions within multiselect
        ModeToggle(
            SelectionMode.Multiselect,
            viewModel,
            painterResource("icons/multiselect_mode_3_scattered_circles.xml"),
            contentDescription = "multiselect mode",
        )
        ModeToggle(
            SelectionMode.SelectRegion,
            viewModel,
            painterResource("icons/select_region_mode_intersection.xml"),
            contentDescription = "select region mode",
        )
        Divider( // modes <-> tools divider
            Modifier
                .padding(8.dp)
                .fillMaxHeight()
                .width(4.dp)
        )
        BinaryToggle(
            viewModel.showCircles,
            painterResource("icons/visible.xml"), painterResource("icons/invisible.xml"),
            "make circles invisible"
        ) {
            viewModel.showCircles = !viewModel.showCircles
        }
        var showColorPickerDialog by remember { mutableStateOf(false) }
        IconButton(onClick = {
            showColorPickerDialog = true
        }) {
            Icon(painterResource("icons/palette.xml"), contentDescription = "choose color")
            Icon(painterResource("icons/rounded_square.xml"), contentDescription = "current color",
                Modifier.size(56.dp), // nantoka nare (icon size should be 48.dp)
                tint = viewModel.regionColor
            )
        }
        if (showColorPickerDialog)
            ColorPickerDialog(
                initialColor = viewModel.regionColor,
                onDismissRequest = { showColorPickerDialog = false },
                onConfirmation = { newColor ->
                    showColorPickerDialog = false
                    viewModel.regionColor = newColor
                    viewModel.switchSelectionMode(SelectionMode.SelectRegion, noAlteringShortcuts = true)
                }
            )
        IconButton(
            onClick = viewModel::copyCircles,
            enabled = viewModel.copyAndDeleteAreEnabled
        ) {
            Icon(painterResource("icons/copy.xml"), contentDescription = "copy circle(s)")
        }
        IconButton(
            onClick = viewModel::deleteCircles,
            enabled = viewModel.copyAndDeleteAreEnabled,
        ) {
            Icon(
                Icons.Default.Delete,
                tint = Color(1f, 0.5f, 0.5f).copy(alpha = LocalContentAlpha.current),
                contentDescription = "delete circle(s)"
            )
        }
        ModeToggle<CreationMode.CircleByCenterAndRadius>(
            CreationMode.CircleByCenterAndRadius.Center(), viewModel,
            painterResource("icons/center.xml"), "circle by center & radius",
        )
        ModeToggle<CreationMode.CircleBy3Points>(
            CreationMode.CircleBy3Points(), viewModel,
            painterResource("icons/circle_3_points.xml"), "circle by 3 points"
        )
//        IconButton(onClick = viewModel::createNewCircle) {
//            Icon(Icons.Default.AddCircle, contentDescription = "create new circle")
//        }
    }
}

@Composable
fun Panel() {
    // shown on the top of the bottom toolbar
    // scrollable lazy row, w = wrap content
    // can be shown or hidden with a collapse button at the end
}

@Composable
fun MultiselectPanel() {
    // actions:
    // select all circles
    // deselect all circles
    // rectangular selection
    // <collapse panel>
}

@Composable
fun RegionsPanel() {
    // switch:
    // binary chessboard selection
    // actions:
    // deselect all regions
    // choose color via color picker
    // <several most common colors to choose from>
    // <collapse panel>
}

@Composable
fun VisibilityPanel() {
    // switches:
    // circle visibility
    // regions are filled/wireframes
    // <collapse panel>
}

@Composable
fun CreationPanel() {
    // selectable creation mode:
    // circle by center & radius
    // circle by 3 points
    // line by 2 points
    // <collapse panel>
}

@Composable
fun TransformToolsPanel() {
    // default: move/drag
    // scale
    // rotate
    // inverse
}

@Composable
inline fun <reified M: Mode> ModeToggle(
    targetMode: M,
    viewModel: EditClusterViewModel,
    painter: Painter,
    contentDescription: String,
    checkedPredicate: () -> Boolean = { viewModel.mode is M }
) {
    // Crossfade/AnimatedContent dont work for w/e reason (mb cuz VM is caught in the closure)
    IconToggleButton(
        checked = checkedPredicate(),
        onCheckedChange = {
            viewModel.switchSelectionMode(targetMode)
        },
        modifier = Modifier
            .background(
                if (checkedPredicate())
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
fun BinaryToggle(
    checked: Boolean,
    checkedPainter: Painter,
    uncheckedPainter: Painter,
    contentDescription: String,
    onCheckChange: (Boolean) -> Unit,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckChange,
    ) {
        Crossfade(checked) { targetChecked ->
            Icon(
                if (targetChecked) checkedPainter
                else uncheckedPainter,
                contentDescription
            )
        }
    }
    Spacer(Modifier.fillMaxHeight().width(8.dp)) // horizontal margin
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismissRequest: () -> Unit,
    onConfirmation: (Color) -> Unit,
) {
    var color by remember { mutableStateOf(HsvColor.from(initialColor)) }
    Dialog(onDismissRequest = {
//        onDismissRequest()
        onConfirmation(color.toColor()) // thats how it be
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(0.9f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Choose color",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.h4,
                )
//                HarmonyColorPicker(
//                    Modifier
//                        .fillMaxHeight(0.7f)
//                        .padding(16.dp),
//                    harmonyMode = ColorHarmonyMode.ANALOGOUS,
//                    color = color
//                ) { color = it }
                ClassicColorPicker(
                    Modifier
                        .fillMaxHeight(0.7f)
                        .padding(16.dp),
                    color = color,
                    showAlphaBar = false,
                ) { color = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    OutlinedButton(
                        onClick = { onDismissRequest() },
                        modifier = Modifier.padding(8.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colors.primary),
                        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
                    ) {
                        Icon(painterResource("icons/cancel.xml"), "cancel")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Cancel", fontSize = 24.sp)
                    }
                    Button(
                        onClick = { onConfirmation(color.toColor()) },
                        modifier = Modifier.padding(8.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colors.primary),
                        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
                    ) {
                        Icon(painterResource("icons/confirm.xml"), "confirm")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("OK", fontSize = 24.sp)
                    }
                }
            }
        }
    }
}