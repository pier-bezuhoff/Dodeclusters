package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.BottomAppBar
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import data.ClusterRepository
import data.io.OpenFileButton
import data.io.SaveData
import data.io.SaveFileButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import ui.colorpicker.ClassicColorPicker
import ui.colorpicker.HsvColor
import ui.colorpicker.harmony.ColorHarmonyMode
import ui.colorpicker.harmony.HarmonyColorPicker

// TODO: left & right toolbar for landscape orientation instead of top & bottom
@Composable
fun EditClusterScreen(sampleIndex: Int? = null) {
    val coroutineScope = rememberCoroutineScope() // NOTE: potentially pass coroutineScope into VMSaver
    val clusterRepository = remember { ClusterRepository() }
    val viewModel = rememberSaveable(saver = EditClusterViewModel.VMSaver) {
        EditClusterViewModel.UiState.restore(EditClusterViewModel.UiState.DEFAULT)
    }

    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            EditClusterTopBar(coroutineScope, viewModel)
        },
        bottomBar = {
            EditClusterBottomBar(coroutineScope, viewModel)
        },
    ) { inPaddings ->
        Surface {
            EditClusterCanvas(coroutineScope, viewModel, Modifier.padding(inPaddings))
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
fun EditClusterTopBar(
    coroutineScope: CoroutineScope,
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

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterBottomBar(
    coroutineScope: CoroutineScope,
    viewModel: EditClusterViewModel,
) {
    BottomAppBar {
        // MAYBE: select regions within multiselect
        ModeToggle(
            SelectionMode.Drag,
            viewModel,
            painterResource("icons/drag_mode_1_circle.xml"),
            contentDescription = "drag mode",
        )
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
        IconToggleButton(
            checked = viewModel.showCircles.value,
            onCheckedChange = {
                viewModel.showCircles.value = !viewModel.showCircles.value
            },
        ) {
            Icon(
                if (viewModel.showCircles.value) painterResource("icons/visible.xml")
                else painterResource("icons/invisible.xml"),
                "Make circles invisible"
            )
        }
        Spacer(Modifier.fillMaxHeight().width(8.dp)) // horizontal margin
        Divider( // modes <-> tools divider
            Modifier
                .padding(8.dp)
                .fillMaxHeight()
                .width(4.dp)
        )
        // TODO: make it into new mode: circle by center and radius + add line by 2 points
        var showColorPickerDialog by remember { mutableStateOf(false) }
        IconButton(onClick = {
            showColorPickerDialog = true
        }) {
            Icon(painterResource("icons/palette.xml"), contentDescription = "choose color")
        }
        if (showColorPickerDialog)
            ColorPickerDialog(
                onDismissRequest = { showColorPickerDialog = false },
                onConfirmation = {
                    showColorPickerDialog = false
                    println(it)
                }
            )
        IconButton(
            onClick = { coroutineScope.launch { viewModel.createNewCircle() } },
        ) {
            Icon(Icons.Default.AddCircle, contentDescription = "create new circle")
        }
        IconButton(
            onClick = { coroutineScope.launch { viewModel.copyCircles() } },
            enabled = viewModel.copyAndDeleteAreEnabled
        ) {
            Icon(painterResource("icons/copy.xml"), contentDescription = "copy circle(s)")
        }
        IconButton(
            onClick = { coroutineScope.launch { viewModel.deleteCircles() } },
            enabled = viewModel.copyAndDeleteAreEnabled
        ) {
            Icon(Icons.Default.Delete, contentDescription = "delete circle(s)")
        }
    }
}

@Composable
fun MultiselectPanel() {
    LazyRow(Modifier.fillMaxWidth()) {
        // actions:
        // select all circles
        // deselect all circles
        // rectangular selection
    }
}

@Composable
fun RegionsPanel() {
    // switch:
    // binary chessboard selection
    // actions:
    // deselect all regions
    // choose color via color picker
    // <several most common colors to choose from>
}

@Composable
fun VisibilityPanel() {
    // switches:
    // circle visibility
    // regions are filled/wireframes
}

@Composable
fun CreationPanel() {
    // selectable creation mode:
    // circle by center & radius
    // circle by 3 points
    // line by 2 points
}

@Composable
fun ModeToggle(
    mode: SelectionMode,
    viewModel: EditClusterViewModel,
    painter: Painter,
    contentDescription: String,
) {
    IconToggleButton(
        checked = viewModel.selectionMode == mode,
        onCheckedChange = {
            viewModel.switchSelectionMode(mode)
        },
        modifier = Modifier
            .background(
                if (viewModel.selectionMode == mode)
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
fun ColorPickerDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: (Color) -> Unit,
) {
    var color by remember { mutableStateOf(HsvColor.from(Color.Cyan)) }
    Dialog(onDismissRequest = { onDismissRequest() }) {
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
                    TextButton(
                        onClick = { onDismissRequest() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.h5)
                    }
                    OutlinedButton(
                        onClick = { onConfirmation(color.toColor()) },
                        modifier = Modifier.padding(8.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colors.primary),
                        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
                    ) {
                        Text("Confirm", style = MaterialTheme.typography.h5)
                    }
                }
            }
        }
    }
}