package ui.edit_cluster

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import data.ClusterRepository
import data.io.Ddc
import data.io.OpenFileButton
import data.io.SaveData
import data.io.SaveFileButton
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.center
import dodeclusters.composeapp.generated.resources.circle_3_points
import dodeclusters.composeapp.generated.resources.circled_region
import dodeclusters.composeapp.generated.resources.copy
import dodeclusters.composeapp.generated.resources.delete_forever
import dodeclusters.composeapp.generated.resources.drag_mode_1_circle
import dodeclusters.composeapp.generated.resources.edit_cluster_title
import dodeclusters.composeapp.generated.resources.invisible
import dodeclusters.composeapp.generated.resources.multiselect_mode_3_scattered_circles
import dodeclusters.composeapp.generated.resources.open_file
import dodeclusters.composeapp.generated.resources.open_file_name
import dodeclusters.composeapp.generated.resources.open_region
import dodeclusters.composeapp.generated.resources.palette
import dodeclusters.composeapp.generated.resources.redo
import dodeclusters.composeapp.generated.resources.redo_name
import dodeclusters.composeapp.generated.resources.rounded_square
import dodeclusters.composeapp.generated.resources.save
import dodeclusters.composeapp.generated.resources.save_cluster_name
import dodeclusters.composeapp.generated.resources.select_region_mode_intersection
import dodeclusters.composeapp.generated.resources.undo
import dodeclusters.composeapp.generated.resources.undo_name
import dodeclusters.composeapp.generated.resources.visible
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.theme.DodeclustersColors

// TODO: left & right toolbar for landscape orientation instead of top & bottom
@Composable
fun EditClusterScreen(
    sampleIndex: Int? = null,
    ddcContent: String? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val clusterRepository = remember { ClusterRepository() }
    val saver = remember { EditClusterViewModel.Saver(coroutineScope) }
    // TODO: test bg kills more
    val viewModel = rememberSaveable(saver = saver) {
        EditClusterViewModel.UiState.restore(coroutineScope, EditClusterViewModel.UiState.DEFAULT)
    }
    viewModel.setEpsilon(LocalDensity.current)

    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    println("FAB")
                    viewModel.switchSelectionMode(CreationMode.CircleByCenterAndRadius.Center())
                },
                backgroundColor = MaterialTheme.colors.secondary,
                contentColor = MaterialTheme.colors.onSecondary,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Add, "FAB create circle")
            }
        },

//        topBar = {
//            EditClusterTopBar(viewModel)
//        },
//        bottomBar = {
//        },
    ) { inPaddings ->
        Surface {
            Box {
                EditClusterCanvas(viewModel)//, Modifier.padding(inPaddings))
                EditClusterTopBar(viewModel)
                EditClusterBottomBar(viewModel, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    coroutineScope.launch {
        if (ddcContent != null) {
            println("loading external ddc")
            viewModel.loadFromYaml(ddcContent)
        } else if (sampleIndex != null) {
            clusterRepository.loadSampleClusterJson(sampleIndex) { json ->
                if (json != null) {
                    viewModel.loadFromJson(json)
                }
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterTopBar(viewModel: EditClusterViewModel) {
    val backgroundColor = MaterialTheme.colors.primary
    val contentColor = MaterialTheme.colors.onPrimary
    TopAppBar(
        title = { Text(stringResource(Res.string.edit_cluster_title), color = DodeclustersColors.black) },
        navigationIcon = {
//            IconButton(onClick = viewModel::saveAndGoBack) {
//                Icon(Icons.Default.Done, contentDescription = "Done")
//            }
        },
        actions = {
            CompositionLocalProvider(
                LocalContentAlpha provides 1f,
//                LocalContentColor provides Color.White
            ) {
                SaveFileButton(painterResource(Res.drawable.save), stringResource(Res.string.save_cluster_name),
                    saveDataProvider = { SaveData(
                        Ddc.DEFAULT_NAME, Ddc.DEFAULT_EXTENSION, viewModel.saveAsYaml()) }
                ) {
                    println(if (it) "saved" else "not saved")
                }
                OpenFileButton(painterResource(Res.drawable.open_file), stringResource(Res.string.open_file_name)) { content ->
                    content?.let {
                        viewModel.loadFromYaml(content)
                    }
                }
                IconButton(onClick = viewModel::undo, enabled = viewModel.undoIsEnabled) {
                    Icon(painterResource(Res.drawable.undo), stringResource(Res.string.undo_name))
                }
                IconButton(onClick = viewModel::redo, enabled = viewModel.redoIsEnabled) {
                    Icon(painterResource(Res.drawable.redo), stringResource(Res.string.redo_name))
                }
//            IconButton(onClick = viewModel::cancelAndGoBack) {
//                Icon(Icons.Default.Close, contentDescription = "Cancel")
//            }
            }
        },
        backgroundColor = backgroundColor.copy(alpha = 0.1f),
        contentColor = contentColor,
        modifier = Modifier.background(
            Brush.verticalGradient(
                0f to backgroundColor,
                1f to backgroundColor.copy(alpha = 0.8f)
            )
        ),
        elevation = 0.dp,
    )
}

// TODO: can overflow on mobile, make it scrollable LazyRow or smth
@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterBottomBar(viewModel: EditClusterViewModel, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colors.primary
    val contentColor = MaterialTheme.colors.onPrimary
    BottomAppBar(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to backgroundColor.copy(alpha = 0.8f),
                1f to backgroundColor,
            )
        ),
        backgroundColor = backgroundColor.copy(alpha = 0.1f),
        contentColor = contentColor,
        elevation = 0.dp,
    ) {
        CompositionLocalProvider(
            LocalContentAlpha provides 1f,
//            LocalContentColor provides Color.White,
        ) {
            ModeToggle(
                SelectionMode.Drag,
                viewModel,
                painterResource(Res.drawable.drag_mode_1_circle),
                contentDescription = "drag mode",
            )
            // MAYBE: select regions within multiselect
            ModeToggle(
                SelectionMode.Multiselect.Default,
                viewModel,
                painterResource(Res.drawable.multiselect_mode_3_scattered_circles),
                contentDescription = "multiselect mode",
            )
            ModeToggle(
                SelectionMode.Region,
                viewModel,
                painterResource(Res.drawable.select_region_mode_intersection),
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
                painterResource(Res.drawable.visible), painterResource(Res.drawable.invisible),
                "make circles invisible"
            ) {
                viewModel.showCircles = !viewModel.showCircles
            }
            BinaryToggle(
                viewModel.restrictRegionsToSelection,
                painterResource(Res.drawable.circled_region), painterResource(Res.drawable.open_region),
                "restrict regions to selection"
            ) {
                viewModel.restrictRegionsToSelection = !viewModel.restrictRegionsToSelection
            }
            var showColorPickerDialog by remember { mutableStateOf(false) }
            IconButton(onClick = {
                showColorPickerDialog = true
            }) {
                Icon(painterResource(Res.drawable.palette), contentDescription = "choose color")
                Icon(
                    painterResource(Res.drawable.rounded_square),
                    contentDescription = "current color",
                    Modifier.size(56.dp), // nantoka nare (icon size should be 48.dp)
                    tint = viewModel.regionColor
                )
            }
            if (showColorPickerDialog)
                ColorPickerDialog(
                    initialColor = viewModel.regionColor,
                    onDismissRequest = { showColorPickerDialog = false },
                    onConfirm = { newColor ->
                        showColorPickerDialog = false
                        viewModel.regionColor = newColor
                        viewModel.switchSelectionMode(
                            SelectionMode.Region,
                            noAlteringShortcuts = true
                        )
                    }
                )
            IconButton(
                onClick = viewModel::copyCircles,
                enabled = viewModel.copyAndDeleteAreEnabled
            ) {
                Icon(painterResource(Res.drawable.copy), contentDescription = "copy circle(s)")
            }
            IconButton(
                onClick = viewModel::deleteCircles,
                enabled = viewModel.copyAndDeleteAreEnabled,
            ) {
                Icon(
                    painterResource(Res.drawable.delete_forever),
                    tint = Color(1f, 0.5f, 0.5f).copy(alpha = LocalContentAlpha.current),
                    contentDescription = "delete circle(s)"
                )
            }
            ModeToggle<CreationMode.CircleByCenterAndRadius>(
                CreationMode.CircleByCenterAndRadius.Center(), viewModel,
                painterResource(Res.drawable.center), "circle by center & radius",
            )
            ModeToggle<CreationMode.CircleBy3Points>(
                CreationMode.CircleBy3Points(), viewModel,
                painterResource(Res.drawable.circle_3_points), "circle by 3 points"
            )
//        IconButton(onClick = viewModel::createNewCircle) {
//            Icon(Icons.Default.AddCircle, contentDescription = "create new circle")
//        }
        }
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
