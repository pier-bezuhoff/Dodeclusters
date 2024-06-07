package ui.edit_cluster

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import dodeclusters.composeapp.generated.resources.collapse_down
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
import dodeclusters.composeapp.generated.resources.stub
import dodeclusters.composeapp.generated.resources.undo
import dodeclusters.composeapp.generated.resources.undo_name
import dodeclusters.composeapp.generated.resources.visible
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.theme.DodeclustersColors
import ui.tools.EditClusterCategory
import ui.tools.EditClusterTool
import ui.tools.Tool

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
        // MAYBE: potentially lift it to window-level (desktop)
        modifier = Modifier.handleKeyboardActions(viewModel::processKeyboardAction),
        scaffoldState = scaffoldState,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    println("FAB: open create panel")
                    viewModel.switchToMode(CreationMode.CircleByCenterAndRadius.Center())
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
        // TODO: hide title and make empty top bar space transparent
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
        modifier = Modifier.background( // transparency gradient
            Brush.verticalGradient(
                0f to backgroundColor,
                1f to backgroundColor.copy(alpha = 0.8f)
            )
        ),
        elevation = 0.dp,
    )
}

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
                SelectionMode.Multiselect,
                viewModel,
                painterResource(Res.drawable.multiselect_mode_3_scattered_circles),
                contentDescription = "multiselect mode",
                checkedPredicate = { viewModel.mode is SelectionMode.Multiselect }
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
            IconButton(onClick = {
                viewModel.showColorPickerDialog = true
            }) {
                Icon(painterResource(Res.drawable.palette), contentDescription = "choose color")
                Icon(
                    painterResource(Res.drawable.rounded_square),
                    contentDescription = "current color",
                    Modifier.size(56.dp), // nantoka nare (icon size should be 48.dp)
                    tint = viewModel.regionColor
                )
            }
            if (viewModel.showColorPickerDialog)
                ColorPickerDialog(
                    initialColor = viewModel.regionColor,
                    onDismissRequest = { viewModel.showColorPickerDialog = false },
                    onConfirm = { newColor ->
                        viewModel.showColorPickerDialog = false
                        viewModel.regionColor = newColor
                        viewModel.switchToMode(
                            SelectionMode.Region,
                            noAlteringShortcuts = true
                        )
                    }
                )
            IconButton(
                onClick = viewModel::duplicateCircles,
                enabled = viewModel.circleSelectionIsActive
            ) {
                Icon(painterResource(Res.drawable.copy), contentDescription = "copy circle(s)")
            }
            IconButton(
                onClick = viewModel::deleteCircles,
                enabled = viewModel.circleSelectionIsActive,
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
        }
    }
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
            viewModel.switchToMode(targetMode)
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

// move to VM
fun setupToolbar() {
    val categories: List<EditClusterCategory> = listOf(
        EditClusterCategory.Drag,
        EditClusterCategory.Multiselect,
        EditClusterCategory.Region,
        EditClusterCategory.Visibility,
        EditClusterCategory.Colors,
        EditClusterCategory.Attributes,
//        EditClusterCategory.Transform,
        EditClusterCategory.Create
    )
    val categoryIndices = categories.withIndex().associate { it.value to it.index }
    val tools = categories.flatMap { it.tools }.distinct()
    // this int list is to be persisted/preserved
    // category index -> tool index among category.tools
    val defaults: MutableList<Int> = categories.map { it.tools.indexOf(it.default) }.toMutableList()
    var category = categories.first()
    var toolIndex = defaults[categoryIndices[category]!!]
    val tool = category.tools[toolIndex]
    var showPanel = category.tools.size > 1
}

@Composable
fun BottomToolbar(
    categories: List<EditClusterCategory>,
    selectedCategoryIndex: Ix,
    onSelectCategory: (EditClusterCategory) -> Unit
) {
    val backgroundColor = MaterialTheme.colors.primary
    val contentColor = MaterialTheme.colors.onPrimary
    BottomAppBar(
        modifier = Modifier.background(
            Brush.verticalGradient(
                0f to backgroundColor.copy(alpha = 0.8f),
                1f to backgroundColor,
            )
        ),
        backgroundColor = backgroundColor.copy(alpha = 0.1f),
        contentColor = contentColor,
        elevation = 0.dp,
    ) {
        CompositionLocalProvider(LocalContentAlpha provides 1f) {
            for ((i, category) in categories.withIndex()) {
                val selected = i == selectedCategoryIndex
                when (category) {
                    is EditClusterCategory.Multiselect -> {
                        val icon = EditClusterTool.Multiselect.icon
                        val icon2 = EditClusterTool.FlowSelect.icon
                        OnOffButton(
                            painterResource(icon), stringResource(category.name), selected
                        ) { onSelectCategory(category) }
                    } // show normal|flow icon
                    is EditClusterCategory.Colors -> {
                        2
                    } // show colored outline + dialog
                    is EditClusterCategory.Create -> {} // FAB
                    else ->
                        OnOffButton(
                            painterResource(category.default.icon), stringResource(category.name), selected
                        ) { onSelectCategory(category) }
                }
            }
        }
    }
}

// MAYBE: hoist VM upwards with callbacks
@Composable
fun Panel(
    category: EditClusterCategory,
    toolIndex: Ix,
    viewModel: EditClusterViewModel,
    onSelectTool: (toolIndex: Ix) -> Unit,
    onHide: () -> Unit = {}
) {
    // shown on the top of the bottom toolbar
    // scrollable lazy row, w = wrap content
    // can be shown or hidden with a collapse button at the end
    require(category.tools.size > 1)
    // scrollable row + highlight selected tool
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        for ((ix, tool) in category.tools.withIndex()) {
            val highlighted = ix == toolIndex // i think this is already reflected by predicates
            val icon = painterResource(tool.icon)
            val name = stringResource(tool.name)
            val onClick = {
                viewModel.toolAction(tool)
                onSelectTool(ix)
            }
            when (tool) {
                is Tool.ActionOnSelection -> {
//                    if (tool is EditClusterTool.Delete) // red tint
                    DisableableButton(
                        icon, name,
                        disabled = !viewModel.circleSelectionIsActive,
                        onClick
                    )
                }
                is Tool.InstantAction -> {
//                    if (tool is EditClusterTool.Palette) // colored outline
                    SimpleButton(icon, name, onClick)
                }
                is Tool.BinaryToggle -> {
                    if (tool.disabledIcon == null)
                        OnOffButton(
                            icon, name,
                            isOn = viewModel.toolPredicate(tool),
                            onClick
                        )
                    else
                        TwoIconButton(
                            icon,
                            disabledIconPainter = painterResource(tool.disabledIcon!!),
                            name,
                            enabled = viewModel.toolPredicate(tool),
                            onClick
                        )
                }
                else -> throw IllegalStateException("Never") // wont compile otherwise
            }
        }
        if (category is EditClusterCategory.Region || category is EditClusterCategory.Colors) {
            // used colors button
        }
        // hide panel button
        SimpleButton(
            painterResource(Res.drawable.collapse_down),
            stringResource(Res.string.stub),
            onClick = onHide
        )
    }
}