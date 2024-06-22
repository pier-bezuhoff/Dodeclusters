package ui.edit_cluster

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import data.ClusterRepository
import data.io.Ddc
import data.io.OpenFileButton
import data.io.SaveData
import data.io.SaveFileButton
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.collapse
import dodeclusters.composeapp.generated.resources.collapse_down
import dodeclusters.composeapp.generated.resources.edit_cluster_title
import dodeclusters.composeapp.generated.resources.open_file
import dodeclusters.composeapp.generated.resources.open_file_name
import dodeclusters.composeapp.generated.resources.redo
import dodeclusters.composeapp.generated.resources.redo_name
import dodeclusters.composeapp.generated.resources.save
import dodeclusters.composeapp.generated.resources.save_cluster_name
import dodeclusters.composeapp.generated.resources.undo
import dodeclusters.composeapp.generated.resources.undo_name
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
    Scaffold(
        // MAYBE: potentially lift it to window-level (desktop)
        // BUG: unfocused at the start on desktop
        modifier = Modifier.handleKeyboardActions(viewModel::processKeyboardAction),
        floatingActionButton = {
            val category = EditClusterCategory.Create
            FloatingActionButton(
                onClick = {
                    viewModel.switchToCategory(category, togglePanel = true)
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation()
            ) {
                Icon(Icons.Filled.Add, stringResource(category.name))
            }
        },
    ) {
        Surface {
            Box {
                EditClusterCanvas(viewModel)
                EditClusterTopBar(viewModel)
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
    if (viewModel.showColorPickerDialog) {
        ColorPickerDialog(
            initialColor = viewModel.regionColor,
            onDismissRequest = { viewModel.showColorPickerDialog = false },
            onConfirm = viewModel::selectRegionColor
        )
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

@OptIn(ExperimentalResourceApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditClusterTopBar(viewModel: EditClusterViewModel) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    TopAppBar(
        // TODO: hide title and make empty top bar space transparent
        title = { Text(stringResource(Res.string.edit_cluster_title)) },
        actions = {
            WithTooltip(stringResource(Res.string.save_cluster_name)) {
                SaveFileButton(painterResource(Res.drawable.save),
                    stringResource(Res.string.save_cluster_name),
                    saveDataProvider = {
                        SaveData(
                            Ddc.DEFAULT_NAME, Ddc.DEFAULT_EXTENSION, viewModel.saveAsYaml()
                        )
                    }
                ) {
                    println(if (it) "saved" else "not saved")
                }
            }
            WithTooltip(stringResource(Res.string.open_file_name)) {
                OpenFileButton(
                    painterResource(Res.drawable.open_file),
                    stringResource(Res.string.open_file_name)
                ) { content ->
                    content?.let {
                        viewModel.loadFromYaml(content)
                    }
                }
            }
            WithTooltip(stringResource(Res.string.undo_name)) {
                IconButton(onClick = viewModel::undo, enabled = viewModel.undoIsEnabled) {
                    Icon(painterResource(Res.drawable.undo), stringResource(Res.string.undo_name))
                }
            }
            WithTooltip(stringResource(Res.string.redo_name)) {
                IconButton(onClick = viewModel::redo, enabled = viewModel.redoIsEnabled) {
                    Icon(painterResource(Res.drawable.redo), stringResource(Res.string.redo_name))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors().copy(
            containerColor = backgroundColor.copy(alpha = 0.1f),
            titleContentColor = MaterialTheme.colorScheme.tertiary,
            actionIconContentColor = contentColor,
        ),
        modifier = Modifier.background( // transparency gradient
            Brush.verticalGradient(
                0f to backgroundColor,
                1f to backgroundColor.copy(alpha = 0.7f)
            )
        ),
    )
}

@Composable
fun BottomToolbar(
    viewModel: EditClusterViewModel,
    modifier: Modifier = Modifier
) {
    // too bright in light mode imo
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    BottomAppBar(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to backgroundColor.copy(alpha = 0.7f),
                1f to backgroundColor,
            )
        ),
        containerColor = backgroundColor.copy(alpha = 0.1f),
        contentColor = contentColor,
    ) {
        CategoryButton(viewModel, EditClusterCategory.Drag)
        CategoryButton(viewModel, EditClusterCategory.Multiselect)
        CategoryButton(viewModel, EditClusterCategory.Region)
        VerticalDivider(Modifier.fillMaxHeight(0.8f))
        CategoryButton(viewModel, EditClusterCategory.Visibility)
        CategoryButton(viewModel, EditClusterCategory.Colors)
        AttributesCategoryButton(viewModel)
    }
}

@Composable
fun CategoryButton(
    viewModel: EditClusterViewModel,
    category: EditClusterCategory
) {
    val i = viewModel.categories.indexOf(category)
    val defaultTool = category.tools[viewModel.categoryDefaults[i]]
//    val icon = category.icon ?: defaultTool.icon
    Crossfade(defaultTool) {
        ToolButton(viewModel, defaultTool, Modifier.padding(horizontal = 8.dp)) {
            viewModel.selectTool(defaultTool, togglePanel = true)
        }
    }
}

@Composable
fun AttributesCategoryButton(
    viewModel: EditClusterViewModel
) {
    // TODO: add tooltip
    val category = EditClusterCategory.Attributes
    SimpleButton(
        painterResource(category.icon!!),
        stringResource(category.name),
        Modifier.padding(horizontal = 8.dp)
    ) {
        if (!viewModel.mode.isSelectingCircles())
            viewModel.switchToMode(SelectionMode.Drag)
        viewModel.switchToCategory(category, togglePanel = true)
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
    Row(modifier = modifier
        .horizontalScroll(scrollState)
        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
    ) {
        for (tool in viewModel.activeCategory.tools) {
            ToolButton(viewModel, tool)
        }
        if (viewModel.activeCategory is EditClusterCategory.Region) { // || category is EditClusterCategory.Colors) {
            VerticalDivider(Modifier
                .height(48.dp)
                .padding(horizontal = 8.dp)
            )
            val colorsByMostUsed = viewModel.parts
                .flatMap { part ->
                    part.borderColor?.let { listOf(part.fillColor, it) } ?: listOf(part.fillColor)
                }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { (_, count) -> count }
                .map { (color, _) -> color }
            for (color in colorsByMostUsed) {
                ToolButton(viewModel, EditClusterTool.AppliedColor(color))
            }
            // used colors button
        }
        // hide panel button
        WithTooltip(stringResource(Res.string.collapse)) {
            SimpleButton(
                painterResource(Res.drawable.collapse_down),
                stringResource(Res.string.collapse),
                onClick = { viewModel.showPanel = false }
            )
        }
    }
    LaunchedEffect(viewModel.activeToolIndex) {
        scrollState.animateScrollTo(viewModel.activeToolIndex) // probs?
    }
}

@Composable
fun ToolButton(
    viewModel: EditClusterViewModel,
    tool: EditClusterTool,
    modifier: Modifier = Modifier.padding(4.dp),
    onClick: () -> Unit = { viewModel.selectTool(tool) }
) {
    val icon = painterResource(tool.icon)
    val name = stringResource(tool.name)
    val description = stringResource(tool.description)
    WithTooltip(description) {
        when (tool) {
            EditClusterTool.Delete -> { // red tint
                IconButton(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = viewModel.circleSelectionIsActive
                ) {
                    val alpha = if (viewModel.circleSelectionIsActive) 1f else 0.5f
                    Icon(
                        icon,
                        tint = EditClusterTool.Delete.tint.copy(alpha = alpha),
                        contentDescription = name
                    )
                }
            }

            EditClusterTool.Palette -> {
                PaletteButton(viewModel.regionColor, modifier, onClick)
            }

            is EditClusterTool.AppliedColor -> {
                IconButton(
                    onClick = onClick,
                    modifier = modifier,
                ) {
                    Icon(
                        icon,
                        tint = tool.color,
                        contentDescription = name
                    )
                }
            }

            is Tool.ActionOnSelection -> {
                DisableableButton(
                    icon, name,
                    enabled = viewModel.circleSelectionIsActive,
                    modifier,
                    onClick
                )
            }

            is Tool.InstantAction -> {
                SimpleButton(icon, name, modifier, onClick)
            }

            is Tool.BinaryToggle -> {
                if (tool.disabledIcon == null) {
                    OnOffButton(
                        icon, name,
                        isOn = viewModel.toolPredicate(tool),
                        modifier = modifier,
                        onClick = onClick
                    )
                } else {
                    TwoIconButton(
                        icon,
                        disabledIconPainter = painterResource(tool.disabledIcon!!),
                        name,
                        enabled = viewModel.toolPredicate(tool),
                        modifier,
                        onClick
                    )
                }
            }
            else -> throw IllegalStateException("Never: $tool")
        }
    }
}

@Composable
fun PaletteButton(
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors().copy(
            containerColor = selectedColor,
        )
    ) {
        Icon(
            painterResource(EditClusterTool.Palette.icon),
            contentDescription = stringResource(EditClusterTool.Palette.name)
        )
//        Icon(
//            painterResource(EditClusterTool.Palette.colorOutlineIcon),
//            contentDescription = stringResource(EditClusterCategory.Colors.name),
//            modifier = Modifier.size(64.dp), // nantoka nare (default icon size should be 48.dp),
//            // looks awkward & small in the browser & android
//            tint = selectedColor
//        )
    }
}