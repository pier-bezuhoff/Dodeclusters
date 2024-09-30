package ui.edit_cluster

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import data.Cluster
import data.ClusterRepository
import domain.PartialArgList
import data.geometry.Circle
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.collapse
import dodeclusters.composeapp.generated.resources.collapse_down
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.copy
import dodeclusters.composeapp.generated.resources.delete_forever
import dodeclusters.composeapp.generated.resources.expand
import dodeclusters.composeapp.generated.resources.ku
import dodeclusters.composeapp.generated.resources.ok_name
import dodeclusters.composeapp.generated.resources.set_selection_as_tool_arg_prompt
import dodeclusters.composeapp.generated.resources.shrink
import dodeclusters.composeapp.generated.resources.tool_arg_input_prompt
import domain.Arg
import domain.io.LookupData
import domain.io.OpenFileButton
import domain.io.SaveData
import domain.io.SaveFileButton
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import ui.DisableableButton
import ui.OnOffButton
import ui.SimpleButton
import ui.TwoIconButton
import ui.WithTooltip
import ui.isCompact
import ui.isLandscape
import ui.theme.DodeclustersColors
import ui.tools.EditClusterCategory
import ui.tools.EditClusterTool
import ui.tools.Tool
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun EditClusterScreen(
    sampleIndex: Int? = null,
    ddcContent: String? = null,
    keyboardActions: Flow<KeyboardAction>? = null,
) {
    val windowSizeClass = calculateWindowSizeClass()
    val isLandscape = windowSizeClass.isLandscape
    val compactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    val compact = windowSizeClass.isCompact
    val coroutineScope = rememberCoroutineScope()
    val clusterRepository = remember { ClusterRepository() }
    val saver = remember { EditClusterViewModel.Saver(coroutineScope) }
    // TODO: test bg kills more
    val viewModel = rememberSaveable(saver = saver) {
        EditClusterViewModel.UiState.restore(coroutineScope, EditClusterViewModel.UiState.DEFAULT)
    }
    viewModel.setEpsilon(LocalDensity.current)
    Scaffold(
        modifier =
        if (keyboardActions == null)
            Modifier.handleKeyboardActions(viewModel::processKeyboardAction)
        else Modifier,
        floatingActionButton = {
            if (!isLandscape) { // inline in the LeftToolbar in landscape
                // MAYBE: only inline with any WindowSizeClass is Expanded (i.e. non-mobile)
                val category = EditClusterCategory.Create
                FloatingActionButton(
                    onClick = {
                        viewModel.switchToCategory(category, togglePanel = true)
                    },
                    modifier =
                    if (compactHeight) Modifier
                        .size(48.dp)
                        .offset(x = 8.dp, y = 16.dp)
                    else Modifier,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation()
                ) {
                    Icon(
                        Icons.Filled.Add,
                        stringResource(category.name),
                        Modifier
                            .padding(4.dp)
                            .size(40.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = if (isLandscape) FabPosition.Start else FabPosition.End
    ) {
        Surface {
            Box {
                EditClusterCanvas(viewModel)
                ToolDescription(
                    viewModel.activeTool,
                    viewModel.partialArgList,
                    isLandscape,
                    compact,
                    viewModel.showPromptToSetActiveSelectionAsToolArg,
                    viewModel::setActiveSelectionAsToolArg,
                    Modifier.align(Alignment.TopStart)
                )
                EditClusterTopBar(viewModel, compact, Modifier.align(Alignment.TopEnd))
                if (isLandscape)
                    ToolbarLandscape(viewModel, compact, Modifier.align(Alignment.CenterStart))
                else
                    ToolbarPortrait(viewModel, compact, Modifier.align(Alignment.BottomStart))
            }
        }
    }
    preloadIcons()
    if (viewModel.showColorPickerDialog) {
        ColorPickerDialog(
            initialColor = viewModel.regionColor,
            onDismissRequest = { viewModel.showColorPickerDialog = false },
            onConfirm = viewModel::selectRegionColor
        )
    }
    if (viewModel.showCircleInterpolationDialog && viewModel.partialArgList?.isFull == true) {
        val (startCircle, endCircle) = viewModel.partialArgList!!.args
            .map {
                viewModel.circles[(it as Arg.CircleIndex).index]!!
            }
        CircleInterpolationDialog(
            startCircle, endCircle,
            onDismissRequest = { viewModel.resetCircleInterpolation() },
            onConfirm = { params ->
                viewModel.completeCircleInterpolation(params)
            },
            defaults = viewModel.defaultInterpolationParameters
        )
    }
    if (viewModel.showCircleExtrapolationDialog && viewModel.partialArgList?.isFull == true) {
        val (startCircle, endCircle) = viewModel.partialArgList!!.args
            .map {
                viewModel.circles[(it as Arg.CircleIndex).index]!!
            }
        CircleExtrapolationDialog(
            startCircle, endCircle,
            onDismissRequest = { viewModel.resetCircleExtrapolation() },
            onConfirm = { params ->
                viewModel.completeCircleExtrapolation(params)
            },
            defaults = viewModel.defaultExtrapolationParameters
        )
    }
    if (viewModel.showLoxodromicMotionDialog && viewModel.partialArgList?.isFull == true) {
        val (divergencePoint, convergencePoint) = viewModel.partialArgList!!.args
            .drop(1)
            .map { it as Arg.Point }
            .map { when (it) {
                is Arg.Point.XY -> it.toPoint()
                is Arg.Point.Index -> viewModel.points[it.index]
            } }
        if (divergencePoint != null && convergencePoint != null) {
            LoxodromicMotionDialog(
                divergencePoint, convergencePoint,
                onDismissRequest = { viewModel.resetLoxodromicMotion() },
                onConfirm = { params ->
                    viewModel.completeLoxodromicMotion(params)
                },
                defaults = viewModel.defaultLoxodromicMotionParameters
            )
        }
    }
    LaunchedEffect(ddcContent, sampleIndex) {
        if (ddcContent != null) {
            println("loading external ddc")
            viewModel.loadFromYaml(ddcContent)
        } else if (sampleIndex != null) {
            clusterRepository.loadSampleClusterYaml(sampleIndex) { content ->
                if (content != null) {
                    viewModel.loadFromYaml(content)
                }
            }
        } else {
            viewModel.loadCluster(Cluster(
                listOf(
                    Circle(0.0, 0.0, 200.0),
                ),
//                parts = listOf(Cluster.Part(insides = setOf(0), outsides = emptySet(), fillColor = DodeclustersColors.secondaryDark/*primaryDark*/))
            ))
            viewModel.moveToDdcCenter(0f, 0f)
        }
    }
    LaunchedEffect(keyboardActions, viewModel) {
        keyboardActions?.let {
            keyboardActions.collect { action ->
                viewModel.processKeyboardAction(action)
            }
        }
    }
}

/** Loads all tool icons and caches them.
 * Otherwise icons only start being loaded when the corresponding category panel is open,
 * which is noticeable */
@Composable
fun preloadIcons() {
    for (category in listOf(EditClusterCategory.Create, EditClusterCategory.Drag, EditClusterCategory.Multiselect, EditClusterCategory.Region, EditClusterCategory.Transform, EditClusterCategory.Visibility)) {
        for (tool in category.tools) {
            painterResource(tool.icon)
            if (tool is Tool.BinaryToggle) {
                tool.disabledIcon?.let {
                    painterResource(it)
                }
            }
        }
    }
    for (resource in listOf(
        Res.drawable.collapse_down, Res.drawable.ku,
        Res.drawable.expand, Res.drawable.shrink, Res.drawable.copy, Res.drawable.delete_forever // from canvas HUD
    ))
        painterResource(resource)
}

@Composable
fun ToolDescription(
    tool: EditClusterTool,
    partialArgList: PartialArgList?,
    isLandscape: Boolean,
    compact: Boolean,
    showSelectionAsArgPrompt: Boolean,
    setSelectionAsArg: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textStyle =
        if (compact) MaterialTheme.typography.bodySmall
        else MaterialTheme.typography.titleMedium
    Column(
        modifier
            .offset(x = if (isLandscape) 100.dp else 0.dp) // offsetting left toolbar
            .fillMaxWidth(if (compact) 0.45f else 0.5f) // we cant specify max text length, so im doing this
    ) {
        Crossfade(tool) { currentTool ->
            Text(
                stringResource(currentTool.description),
                modifier
                    .padding(8.dp, 12.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp, 8.dp)
                ,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                style = textStyle,
            )
        }
        val inputPrompt = stringResource(Res.string.tool_arg_input_prompt)
        val argDescriptions = (tool as? EditClusterTool.MultiArg)?.let {
            stringArrayResource(it.argDescriptions)
        }
        val number =
            if (partialArgList == null || tool !is EditClusterTool.MultiArg)
                null
            else if (partialArgList.lastArgIsConfirmed)
                min(partialArgList.args.size, tool.signature.argTypes.size - 1)
            else
                max(0, partialArgList.args.size - 1)
        AnimatedContent(Triple(tool, number, showSelectionAsArgPrompt)) { (currentTool, currentNumber, currentShowPrompt) ->
            if (currentTool is EditClusterTool.MultiArg &&
                currentNumber != null &&
                argDescriptions != null &&
                argDescriptions.size > currentNumber
            ) {
                val argDescription = argDescriptions[currentNumber]
                val selectionAsArgPrompt = stringResource(Res.string.set_selection_as_tool_arg_prompt)
                if (currentShowPrompt) {
                    Button(
                        onClick = setSelectionAsArg,
                        Modifier.padding(8.dp),
                        colors = ButtonDefaults.buttonColors()
                            .copy(
                                contentColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            )
                    ) {
                        Text(
                            "$selectionAsArgPrompt: $argDescription",
                            Modifier.padding(4.dp, 4.dp),
                            textDecoration = TextDecoration.Underline,
                            style = textStyle,
                        )
                        Icon(
                            painterResource(Res.drawable.confirm),
                            stringResource(Res.string.ok_name),
                            Modifier.padding(start = 8.dp)
                        )
                    }
                } else if (!compact) {
                    Text(
                        "$inputPrompt: $argDescription",
                        Modifier.padding(24.dp, 4.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        textDecoration = TextDecoration.Underline,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun EditClusterTopBar(
    viewModel: EditClusterViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val iconModifier =
        if (compact) Modifier.padding(4.dp).size(30.dp)
        else Modifier.padding(8.dp, 4.dp).size(40.dp)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val toolbarHeight = if (compact) 48.dp else 64.dp
    Row(modifier
        // NOTE: i might be hallucinating but ive seen this break tooltip positioning, now it works tho (?)
        .offset(x = 24.dp, y = -(24).dp) // leave only 1, bottom-left rounded corner
        .background(
            Brush.verticalGradient(
                0.3f to backgroundColor.copy(alpha = 1.0f),
                1f to backgroundColor.copy(alpha = 0.5f),
            ),
            RoundedCornerShape(24.dp)
        )
        .padding(top = 24.dp, end = 24.dp) // offsets the corner-removing offset
        .height(toolbarHeight),
        Arrangement.End,
        Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Spacer(Modifier.width(16.dp))
            val saveCluster = EditClusterTool.SaveCluster
            WithTooltip(stringResource(saveCluster.description)) {
                SaveFileButton(
                    painterResource(saveCluster.icon),
                    stringResource(saveCluster.name),
                    saveData = SaveData(
                        name = saveCluster.defaultName,
                        extension = saveCluster.extension, // yml
                        otherDisplayedExtensions = saveCluster.otherDisplayedExtensions,
                        mimeType = saveCluster.mimeType,
                    ) { name ->
                        viewModel.saveAsYaml(name)
                    },
                    modifier = iconModifier
                ) {
                    println(if (it) "saved" else "not saved")
                }
            }
            val svgExport = EditClusterTool.SvgExport
            WithTooltip(stringResource(svgExport.description)) {
                SaveFileButton(
                    painterResource(svgExport.icon),
                    stringResource(svgExport.name),
                    saveData = SaveData(
                        name = svgExport.defaultName,
                        extension = svgExport.extension,
                        mimeType = svgExport.mimeType,
                    ) { name ->
                        viewModel.exportAsSvg(name)
                    },
                    modifier = iconModifier
                ) {
                    println(if (it) "exported" else "not exported")
                }
            }
            WithTooltip(stringResource(EditClusterTool.OpenFile.description)) {
                OpenFileButton(
                    painterResource(EditClusterTool.OpenFile.icon),
                    stringResource(EditClusterTool.OpenFile.name),
                    LookupData.YAML,
                    iconModifier
                ) { content ->
                    content?.let {
                        viewModel.loadFromYaml(content)
                    }
                }
            }
            WithTooltip(stringResource(EditClusterTool.Undo.description)) {
                DisableableButton(
                    painterResource(EditClusterTool.Undo.icon),
                    stringResource(EditClusterTool.Undo.name),
                    viewModel.undoIsEnabled,
                    iconModifier,
                    onClick = viewModel::undo
                )
            }
            WithTooltip(stringResource(EditClusterTool.Redo.description)) {
                DisableableButton(
                    painterResource(EditClusterTool.Redo.icon),
                    stringResource(EditClusterTool.Redo.name),
                    viewModel.redoIsEnabled,
                    iconModifier,
                    onClick = viewModel::redo
                )
            }
        }
    }
}

@Composable
private fun ToolbarPortrait(viewModel: EditClusterViewModel, compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        AnimatedContent(
            Pair(viewModel.activeCategory, viewModel.showPanel),
            transitionSpec = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
                    .togetherWith(slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start))
            }
        ) { (activeCategory, showPanel) ->
            if (showPanel)
                HorizontalPanel(viewModel, activeCategory, compact, Modifier.align(Alignment.Start))
        }
        BottomToolbar(viewModel, compact, Modifier.align(Alignment.Start))
    }
}

@Composable
private fun ToolbarLandscape(viewModel: EditClusterViewModel, compact: Boolean, modifier: Modifier = Modifier) {
    Row(modifier,
        horizontalArrangement = Arrangement.Start
    ) {
        LeftToolbar(viewModel, compact, Modifier
//            .zIndex(1f)
            .align(Alignment.CenterVertically)
        )
        AnimatedContent(
            Pair(viewModel.activeCategory, viewModel.showPanel),
            transitionSpec = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
                    .togetherWith(slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start))
            }
        ) { (activeCategory, showPanel) ->
            if (showPanel)
                VerticalPanel(viewModel, activeCategory, compact, Modifier.align(Alignment.Top))
        }
    }
}

@Composable
private fun BottomToolbar(
    viewModel: EditClusterViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
//    val scrollState = rememberScrollState()
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val toolbarSize =
        if (compact) 48.dp
        else 64.dp
    Row(modifier
//        .horizontalScroll(scrollState)
        .background(
            Brush.verticalGradient(
                0f to backgroundColor.copy(alpha = 0.7f),
                1f to backgroundColor,
            )
        )
        .fillMaxWidth()
        .height(toolbarSize),
        Arrangement.Start,
        Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            CategoryButton(viewModel, EditClusterCategory.Drag, compact = compact)
            CategoryButton(viewModel, EditClusterCategory.Multiselect, compact = compact)
            CategoryButton(viewModel, EditClusterCategory.Region, compact = compact)
            Spacer(Modifier.size(12.dp, 0.dp))
            VerticalDivider(Modifier
                .fillMaxHeight(0.7f)
                .align(Alignment.CenterVertically)
            )
            CategoryButton(viewModel, EditClusterCategory.Visibility, compact = compact)
            CategoryButton(viewModel, EditClusterCategory.Colors, compact = compact)
            CategoryButton(viewModel, EditClusterCategory.Transform, compact = compact)
        }
    }
}

@Composable
private fun LeftToolbar(
    viewModel: EditClusterViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
//    val scrollState = rememberScrollState()
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val toolbarSize =
        if (compact) 48.dp
        else 64.dp
    val cornerRadius = 24.dp
    Column(
        modifier
//            .offset(x = -cornerRadius)
            .background(
                Brush.horizontalGradient(
                    0f to backgroundColor,
                    1f to backgroundColor.copy(alpha = 0.7f),
                ),
                RoundedCornerShape(cornerRadius)
            )
//            .padding(start = cornerRadius)
            .width(toolbarSize)
        ,
        Arrangement.Top,
        Alignment.CenterHorizontally
    ) {
        val dividerPaddings =
            if (compact) PaddingValues(vertical = 6.dp)
            else PaddingValues(top = 12.dp) // every CategoryButton already has 12dp high spacer on the top
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (compact)
                Spacer(Modifier.height(6.dp))
            CategoryButton(viewModel, EditClusterCategory.Drag, compact = compact)
            CategoryButton(viewModel, EditClusterCategory.Multiselect, compact = compact)
            CategoryButton(viewModel, EditClusterCategory.Region, compact = compact)
            HorizontalDivider(Modifier
                .padding(dividerPaddings)
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally)
            )
            CategoryButton(viewModel, EditClusterCategory.Visibility, compact = compact)
            CategoryButton(viewModel, EditClusterCategory.Colors, compact = compact)
            CategoryButton(viewModel, EditClusterCategory.Transform, compact = compact)
            HorizontalDivider(Modifier
                .padding(dividerPaddings)
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally)
            )
            CategoryButton(
                viewModel, EditClusterCategory.Create, compact = compact,
//                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(
                if (compact) 6.dp else 12.dp
            ))
        }
    }
}

@Composable
fun CategoryButton(
    viewModel: EditClusterViewModel,
    category: EditClusterCategory,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val i = viewModel.categories.indexOf(category)
    val defaultTool = category.tools[viewModel.categoryDefaults[i]]
//    val icon = category.icon ?: defaultTool.icon
    if (!compact)
        Spacer(Modifier.size(12.dp, 12.dp))
    Crossfade(defaultTool) {
        ToolButton(
            viewModel,
            defaultTool,
            modifier
                .padding(4.dp)
                .size(
                    if (compact) 36.dp
                    else 40.dp
                )
            ,
            tint
        ) {
            viewModel.selectTool(defaultTool, togglePanel = true)
        }
    }
}

// MAYBE: hoist VM upwards with callbacks
// MAYBE: just make individual panel for every category instead of generalization
@Composable
private fun HorizontalPanel(
    viewModel: EditClusterViewModel,
    activeCategory: EditClusterCategory,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    // shown on the top of the bottom toolbar
    // scrollable lazy row, w = wrap content
    // can be shown or hidden with a collapse button at the end
    require(activeCategory.tools.size > 1)
    val toolModifier =
        if (compact) Modifier.padding(4.dp).size(30.dp)
        else Modifier
    // scrollable row + highlight selected tool
    val scrollState = rememberScrollState()
    // mb wrap in a surface
    Row(modifier = modifier
        .horizontalScroll(scrollState)
        .offset(x = (-24).dp) // hide round corners to the left
        .background(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            RoundedCornerShape(24.dp)
        )
        .padding(start = 24.dp),
    ) {
        Spacer(Modifier.width(8.dp))
        for (tool in activeCategory.tools) {
            ToolButton(viewModel, tool, toolModifier)
        }
        if (activeCategory is EditClusterCategory.Region) { // || category is EditClusterCategory.Colors) {
            VerticalDivider(Modifier
                .height(40.dp)
                .padding(horizontal = 8.dp)
                .align(Alignment.CenterVertically)
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
                ToolButton(viewModel, EditClusterTool.AppliedColor(color), toolModifier)
            }
        }
        // hide panel button
        WithTooltip(stringResource(Res.string.collapse)) {
            SimpleButton(
                painterResource(Res.drawable.collapse_down),
                stringResource(Res.string.collapse),
                toolModifier.padding(4.dp),
                onClick = { viewModel.showPanel = false }
            )
        }
    }
//    LaunchedEffect(viewModel.activeTool) {
//        scrollState.animateScrollTo(viewModel.activeCategory.tools) // probs?
//    }
}

@Composable
private fun VerticalPanel(
    viewModel: EditClusterViewModel,
    activeCategory: EditClusterCategory,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    // shown on the top of the bottom toolbar
    // scrollable lazy row, w = wrap content
    // can be shown or hidden with a collapse button at the end
    require(activeCategory.tools.size > 1)
    val toolModifier =
        if (compact) Modifier.padding(4.dp).size(30.dp)
        else Modifier
    // scrollable row + highlight selected tool
    val scrollState = rememberScrollState()
    Column(
        modifier
            .padding(start = 8.dp)
            .verticalScroll(scrollState)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                RoundedCornerShape(24.dp)
            )
        ,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        for (tool in activeCategory.tools) {
            ToolButton(viewModel, tool, toolModifier)
        }
        if (activeCategory is EditClusterCategory.Region) { // || category is EditClusterCategory.Colors) {
            HorizontalDivider(Modifier
                .width(40.dp)
                .padding(vertical = 8.dp)
                .align(Alignment.CenterHorizontally)
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
                ToolButton(viewModel, EditClusterTool.AppliedColor(color), toolModifier)
            }
        }
        // hide panel button
        WithTooltip(stringResource(Res.string.collapse)) {
            SimpleButton(
                painterResource(Res.drawable.ku),
                stringResource(Res.string.collapse),
                toolModifier.padding(4.dp),
                onClick = { viewModel.showPanel = false }
            )
        }
    }
}

@Composable
fun ToolButton(
    viewModel: EditClusterViewModel,
    tool: EditClusterTool,
    modifier: Modifier = Modifier.padding(4.dp),
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit = { viewModel.selectTool(tool) }
) {
    val icon = painterResource(tool.icon)
    val name = stringResource(tool.name)
    val description = stringResource(tool.description)
    WithTooltip(description) {
        when (tool) {
            EditClusterTool.Delete -> { // unused
                IconButton(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = viewModel.circleSelectionIsActive
                ) {
                    val alpha = if (viewModel.circleSelectionIsActive) 1f else 0.5f
                    Icon(
                        icon,
                        contentDescription = name,
                        modifier = modifier,
                        tint = EditClusterTool.Delete.tint.copy(alpha = alpha),
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
                        contentDescription = name,
                        modifier = modifier,
                        tint = tool.color,
                    )
                }
            }

            is Tool.ActionOnSelection -> {
                DisableableButton(
                    icon, name,
                    enabled = viewModel.circleSelectionIsActive,
                    modifier,
                    tint,
                    onClick
                )
            }

            is Tool.InstantAction -> {
                SimpleButton(icon, name, modifier, tint, onClick = onClick)
            }

            is Tool.BinaryToggle -> {
                if (tool.disabledIcon == null) {
                    OnOffButton(
                        icon, name,
                        isOn = viewModel.toolPredicate(tool),
                        modifier = modifier,
                        tint = tint,
                        onClick = onClick
                    )
                } else {
                    TwoIconButton(
                        icon,
                        disabledIconPainter = painterResource(tool.disabledIcon!!),
                        name,
                        enabled = viewModel.toolPredicate(tool),
                        modifier,
                        tint,
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
    val iconColor =
        if (selectedColor.luminance() > 0.2) {
            DodeclustersColors.darkestGray
        } else DodeclustersColors.lightestWhite
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors().copy(
            containerColor = selectedColor,
            contentColor = iconColor
        )
    ) {
        Icon(
            painterResource(EditClusterTool.Palette.icon),
            contentDescription = stringResource(EditClusterTool.Palette.name),
            modifier = modifier,
        )
    }
}