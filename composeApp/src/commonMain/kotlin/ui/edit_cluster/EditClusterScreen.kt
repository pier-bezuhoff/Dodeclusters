package ui.edit_cluster

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.contentColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.primarySurface
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
                    viewModel.selectCategory(EditClusterCategory.Create)
                },
                backgroundColor = MaterialTheme.colors.secondary,
                contentColor = MaterialTheme.colors.onSecondary,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Add, "FAB create circle")
            }
        },
    ) {
        Surface {
            Box {
                EditClusterCanvas(viewModel)
                EditClusterTopBar(viewModel)
//                EditClusterBottomBar(viewModel, modifier = Modifier.align(Alignment.BottomCenter))
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (viewModel.showPanel) { // use animated visibility instead
                        Panel(viewModel, Modifier.align(Alignment.Start))
                    }
                    BottomToolbar(viewModel, Modifier.align(Alignment.Start))
                }
            }
        }
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
    val backgroundColor = MaterialTheme.colors.primarySurface
    val contentColor = MaterialTheme.colors.contentColorFor(backgroundColor)
    TopAppBar(
        // TODO: hide title and make empty top bar space transparent
        title = { Text(stringResource(Res.string.edit_cluster_title), color = DodeclustersColors.black) },
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
        elevation = 4.dp,
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

@Composable
fun BottomToolbar(
    viewModel: EditClusterViewModel,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colors.primarySurface
    val contentColor = MaterialTheme.colors.contentColorFor(backgroundColor)
    BottomAppBar(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to backgroundColor.copy(alpha = 0.8f),
                1f to backgroundColor,
            )
        ),
        backgroundColor = backgroundColor.copy(alpha = 0.1f),
        contentColor = contentColor,
//        elevation = 4.dp,
    ) {
        // i dont like this anymore just make category bar and panels for each one by one
        CompositionLocalProvider(LocalContentAlpha provides 1f) {
            for ((i, category) in viewModel.categories.withIndex()) {
                // idk how to mark it, not always what's active due to tools' predicates
                val selected = i == viewModel.activeCategoryIndex
                val defaultTool = category.tools[viewModel.categoryDefaults[i]]
                val icon = category.icon ?: defaultTool.icon
                // use category.name and .icon somewhere else ig
                when (category) {
                    is EditClusterCategory.Create -> {} // FAB
                    else -> {
                        ToolButton(viewModel, defaultTool)
                        // TODO: use crossfade(defaultTool)
                        // TODO: second click should trigger category collapse i think
                    }
                }
            }
        }
    }
}

// slide up/down animations
// MAYBE: hoist VM upwards with callbacks
// MAYBE: just make individual panel for every category instead of generalization
/** [toolIndex]: index of the tool selected from within the category */
@Composable
fun Panel(
    viewModel: EditClusterViewModel,
    modifier: Modifier = Modifier
) {
    // shown on the top of the bottom toolbar
    // scrollable lazy row, w = wrap content
    // can be shown or hidden with a collapse button at the end
    require(viewModel.activeCategory.tools.size > 1)
    // scrollable row + highlight selected tool
    val scrollState = rememberScrollState()
    // mb wrap in a surface
    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .background(MaterialTheme.colors.primarySurface.copy(alpha = 0.1f)),
//            .background(Color.Transparent),
    ) {
        for (tool in viewModel.activeCategory.tools) {
            ToolButton(viewModel, tool)
        }
        if (viewModel.activeCategory is EditClusterCategory.Region) { // || category is EditClusterCategory.Colors) {
            val colorsByMostUsed = viewModel.parts
                .flatMap { part ->
                    part.borderColor?.let { listOf(part.fillColor, it) } ?: listOf(part.fillColor)
                }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { (_, count) -> count }
                .map { (color, _) -> color }
            // used colors button
        }
        // hide panel button
        SimpleButton(
            painterResource(Res.drawable.collapse_down),
            stringResource(Res.string.stub),
            onClick = { viewModel.showPanel = false }
        )
    }
    LaunchedEffect(viewModel.activeToolIndex) {
//        scrollState.animateScrollTo(viewModel.activeToolIndex) // probs?
    }
}

@Composable
fun ToolButton(
    viewModel: EditClusterViewModel,
    tool: EditClusterTool,
    onClick: () -> Unit = { viewModel.selectTool(tool) }
) {
    val icon = painterResource(tool.icon)
    val name = stringResource(tool.name)
    when (tool) {
        EditClusterTool.Delete -> { // red tint
            IconButton(
                onClick = onClick,
                enabled = viewModel.circleSelectionIsActive
            ) {
                Icon(
                    icon,
                    tint = EditClusterTool.Delete.tint.copy(alpha = LocalContentAlpha.current),
                    contentDescription = name
                )
            }
        }
        EditClusterTool.Palette -> {
            PaletteButton(viewModel.regionColor, onClick)
        }
        is Tool.ActionOnSelection -> {
            DisableableButton(
                icon, name,
                enabled = viewModel.circleSelectionIsActive,
                onClick
            )
        }
        is Tool.InstantAction -> {
            SimpleButton(icon, name, onClick)
        }
        is Tool.BinaryToggle -> {
            if (tool.disabledIcon == null) {
                OnOffButton(
                    icon, name,
                    isOn = viewModel.toolPredicate(tool),
                    onClick
                )
            } else {
                TwoIconButton(
                    icon,
                    disabledIconPainter = painterResource(tool.disabledIcon!!),
                    name,
                    enabled = viewModel.toolPredicate(tool),
                    onClick
                )
            }
        }
        else -> throw IllegalStateException("Never") // wont compile otherwise
    }
}

@Composable
fun PaletteButton(
    selectedColor: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(EditClusterTool.Palette.icon),
            contentDescription = stringResource(EditClusterTool.Palette.name)
        )
        Icon(
            painterResource(EditClusterTool.Palette.colorOutlineIcon),
            contentDescription = stringResource(EditClusterCategory.Colors.name),
            modifier = Modifier.size(56.dp), // nantoka nare (default icon size should be 48.dp)
            tint = selectedColor
        )
    }
}