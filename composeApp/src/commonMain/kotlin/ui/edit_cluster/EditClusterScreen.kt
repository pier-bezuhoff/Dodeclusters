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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import data.geometry.CircleOrLine
import data.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.cancel
import dodeclusters.composeapp.generated.resources.collapse
import dodeclusters.composeapp.generated.resources.collapse_down
import dodeclusters.composeapp.generated.resources.collapse_left
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.copy
import dodeclusters.composeapp.generated.resources.delete_forever
import dodeclusters.composeapp.generated.resources.expand
import dodeclusters.composeapp.generated.resources.lock_open
import dodeclusters.composeapp.generated.resources.ok_name
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.save_name
import dodeclusters.composeapp.generated.resources.set_selection_as_tool_arg_prompt
import dodeclusters.composeapp.generated.resources.shrink
import dodeclusters.composeapp.generated.resources.three_dots_in_angle_brackets
import dodeclusters.composeapp.generated.resources.tool_arg_input_prompt
import domain.Arg
import domain.PartialArgList
import domain.io.DdcRepository
import domain.io.LookupData
import domain.io.OpenFileButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import ui.DisableableButton
import ui.LifecycleEvent
import ui.OnOffButton
import ui.SimpleButton
import ui.ThreeIconButton
import ui.TwoIconButton
import ui.WithTooltip
import ui.isCompact
import ui.isLandscape
import ui.theme.DodeclustersColors
import ui.theme.extendedColorScheme
import ui.tools.EditClusterCategory
import ui.tools.EditClusterTool
import ui.tools.Tool
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalComposeUiApi::class)
@Composable
fun EditClusterScreen(
    sampleName: String? = null,
    ddcContent: String? = null,
    keyboardActions: Flow<KeyboardAction>? = null,
    lifecycleEvents: Flow<LifecycleEvent>? = null,
) {
    val windowSizeClass = calculateWindowSizeClass()
    val isLandscape = windowSizeClass.isLandscape
    val compactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    val compact = windowSizeClass.isCompact
    val ddcRepository = DdcRepository
    val viewModel: EditClusterViewModel = viewModel(
        factory = EditClusterViewModel.Factory
    )
    val snackbarHostState = remember { SnackbarHostState() } // hangs windows/chrome
//    val snackbarMessage2string = preloadSnackbarMessages()
    viewModel.setEpsilon(LocalDensity.current)
    Scaffold(
//        modifier =
//        if (keyboardActions == null)
//            Modifier.handleKeyboardActions(viewModel::processKeyboardAction)
//        else Modifier
//        ,
        snackbarHost = { SnackbarHost(snackbarHostState) { data ->
            Snackbar(data,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } },
        floatingActionButton = {
            if (!isLandscape && viewModel.showUI) {
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
        floatingActionButtonPosition = FabPosition.End
    ) {
        Surface() {
            Box(Modifier
                .drawBehind {
                    viewModel.backgroundColor?.let { backgroundColor ->
                        drawRect(backgroundColor, size = size)
                    }
                }
            ) {
                EditClusterCanvas(viewModel)
                if (viewModel.showUI) {
                    ToolDescription(
                        tool = viewModel.toolbarState.activeTool,
                        partialArgList = viewModel.partialArgList,
                        isLandscape = isLandscape,
                        compact = compact,
                        showSelectionAsArgPrompt = viewModel.showPromptToSetActiveSelectionAsToolArg,
                        setSelectionAsArg = viewModel::setActiveSelectionAsToolArg,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    EditClusterTopBar(
                        compact = compact,
                        undoIsEnabled = viewModel.undoIsEnabled,
                        redoIsEnabled = viewModel.redoIsEnabled,
//                        saveAsYaml = viewModel::saveAsYaml,
//                        exportAsSvg = viewModel::exportAsSvg,
//                        exportAsPng = viewModel::saveScreenshot,
                        showSaveOptionsDialog = { viewModel.toolAction(EditClusterTool.SaveCluster) },
                        loadFromYaml = { content ->
                            content?.let {
                                viewModel.loadFromYaml(
                                    content
                                )
                            }
                        },
                        undo = viewModel::undo,
                        redo = viewModel::redo,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                    if (isLandscape)
                        ToolbarLandscape(
                            viewModel,
                            compact,
                            Modifier.align(Alignment.CenterStart)
                        )
                    else
                        ToolbarPortrait(
                            viewModel,
                            compact,
                            Modifier.align(Alignment.BottomStart)
                        )
                }
            }
        }
    }
    preloadIcons()
    when (viewModel.openedDialog) {
        DialogType.REGION_COLOR_PICKER -> {
            ColorPickerDialog(
                initialColor = viewModel.regionColor,
                onDismissRequest = viewModel::dismissRegionColorPicker,
                onConfirm = viewModel::setNewRegionColor
            )
        }
        DialogType.CIRCLE_COLOR_PICKER -> {
            val initialColor = viewModel.getMostCommonCircleColorInSelection()
                ?: MaterialTheme.extendedColorScheme.highAccentColor
            ColorPickerDialog(
                initialColor = initialColor,
                onDismissRequest = viewModel::dismissCircleColorPicker,
                onConfirm = viewModel::setNewCircleColor
            )
        }
        DialogType.BACKGROUND_COLOR_PICKER -> {
            val initialColor = viewModel.backgroundColor ?: MaterialTheme.colorScheme.background
            ColorPickerDialog(
                initialColor = initialColor,
                onDismissRequest = viewModel::dismissBackgroundColorPicker,
                onConfirm = viewModel::setNewBackgroundColor,
            )
        }
        DialogType.CIRCLE_INTERPOLATION -> {
            if (viewModel.partialArgList?.isFull == true) {
                val (startCircle, endCircle) = viewModel.partialArgList!!.args
                    .map {
                        viewModel.objects[(it as Arg.CircleIndex).index] as CircleOrLine
                    }
                CircleInterpolationDialog(
                    startCircle, endCircle,
                    onDismissRequest = viewModel::resetCircleInterpolation,
                    onConfirm = viewModel::completeCircleInterpolation,
                    defaults = viewModel.defaultInterpolationParameters
                )
            }
        }
        DialogType.CIRCLE_EXTRAPOLATION -> {
            if (viewModel.partialArgList?.isFull == true) {
                val (startCircle, endCircle) = viewModel.partialArgList!!.args
                    .map {
                        viewModel.objects[(it as Arg.CircleIndex).index] as CircleOrLine
                    }
                CircleExtrapolationDialog(
                    startCircle, endCircle,
                    onDismissRequest = viewModel::resetCircleExtrapolation,
                    onConfirm = viewModel::completeCircleExtrapolation,
                    defaults = viewModel.defaultExtrapolationParameters
                )
            }
        }
        DialogType.LOXODROMIC_MOTION -> {
            if (viewModel.partialArgList?.isFull == true) {
                val (divergencePoint, convergencePoint) = viewModel.partialArgList!!.args
                    .drop(1)
                    .map { it as Arg.Point }
                    .map { when (it) {
                        is Arg.Point.XY -> it.toPoint()
                        is Arg.Point.Index -> viewModel.objects[it.index] as? Point
                    } }
                if (divergencePoint != null && convergencePoint != null) {
                    LoxodromicMotionDialog(
                        divergencePoint, convergencePoint,
                        onDismissRequest = viewModel::resetLoxodromicMotion,
                        onConfirm = viewModel::completeLoxodromicMotion,
                        defaults = viewModel.defaultLoxodromicMotionParameters
                    )
                }
            }
        }
        DialogType.SAVE_OPTIONS -> {
            SaveOptionsDialog(
                viewModel = viewModel,
                saveAsYaml = viewModel::saveAsYaml,
                exportAsSvg = viewModel::exportAsSvg,
                onDismissRequest = viewModel::closeDialog,
                onConfirm = viewModel::closeDialog,
                onSavedStatus = { success, filename ->
                    // if success is null it means saving was canceled by the user
                    if (success != null) {
                        viewModel.queueSnackbarMessage(
                            if (success) SnackbarMessage.SUCCESSFUL_SAVE
                            else SnackbarMessage.FAILED_SAVE,
                            " $filename"
                        )
                    }
                }
            )
        }
        DialogType.BLEND_SETTINGS -> {
            BlendSettingsDialog(
                oldTransparency = viewModel.regionsTransparency,
                oldBlendMode = viewModel.regionsBlendMode,
                onDismissRequest = viewModel::closeDialog,
                onConfirm = viewModel::setBlendSettings
            )
        }
        null -> {}
    }
    LaunchedEffect(ddcContent, sampleName, viewModel) {
        if (ddcContent != null) {
            println("loading external ddc...")
            viewModel.loadFromYaml(ddcContent)
        } else if (sampleName != null) {
            val content = ddcRepository.loadSampleClusterYaml(sampleName)
            if (content != null) {
                viewModel.loadFromYaml(content)
            }
        }
    }
    LaunchedEffect(keyboardActions, viewModel) {
        keyboardActions?.let {
            keyboardActions.collect { action ->
                viewModel.processKeyboardAction(action)
            }
        }
    }
    LaunchedEffect(lifecycleEvents, viewModel) {
        lifecycleEvents?.let {
            lifecycleEvents.collect { action ->
                when (action) {
                    LifecycleEvent.SaveUIState -> {
                        viewModel.cacheState()
                    }
                }
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collectLatest { (message, postfix) ->
            println("snackbar: $message$postfix")
            // NOTE: using getString(resource) here hangs windows/chrome for some reason
            // TODO: can't seem to properly pre-load string resources on Web
            // with this setup string interpolation with args is not possible
//            val s = snackbarMessage2string[message]!! + postfix
//            snackbarHostState.showSnackbar(s, duration = message.duration)
//            // MAYBE: move on-selection action prompt here instead
        }
    }
}

/** Loads all tool icons and caches them.
 * Otherwise icons only start being loaded when the corresponding category panel is open,
 * which is noticeable */
@Composable
fun preloadIcons() {
    for (category in listOf(
        EditClusterCategory.Create, EditClusterCategory.Drag, EditClusterCategory.Multiselect, EditClusterCategory.Region, EditClusterCategory.Transform, EditClusterCategory.Visibility
    )) {
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
        EditClusterTool.PngExport.icon, EditClusterTool.SvgExport.icon,
        Res.drawable.confirm, Res.drawable.cancel, // from dialogs
        Res.drawable.collapse_down, Res.drawable.collapse_left,
        // from canvas HUD
        Res.drawable.expand, Res.drawable.shrink,
        Res.drawable.copy, Res.drawable.delete_forever, Res.drawable.lock_open,
        Res.drawable.rotate_counterclockwise,
        Res.drawable.three_dots_in_angle_brackets, EditClusterTool.DetailedAdjustment.icon, EditClusterTool.InBetween.icon,
    )) {
        painterResource(resource)
    }
}

@Composable
fun preloadSnackbarMessages(): Map<SnackbarMessage, String> {
    return SnackbarMessage.entries.associateWith {
        stringResource(it.stringResource).also { s -> it to s }
    }
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
    compact: Boolean,
    undoIsEnabled: Boolean,
    redoIsEnabled: Boolean,
//    saveAsYaml: (name: String) -> String,
//    exportAsSvg: (name: String) -> String,
//    exportAsPng: suspend () -> Result<ImageBitmap>,
    showSaveOptionsDialog: () -> Unit,
    loadFromYaml: (content: String?) -> Unit,
    undo: () -> Unit,
    redo: () -> Unit,
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
            WithTooltip(stringResource(Res.string.save_name)) {
                SimpleButton(
                    painterResource(saveCluster.icon),
                    stringResource(saveCluster.name),
                    modifier = iconModifier,
                    iconModifier = iconModifier,
                    onClick = showSaveOptionsDialog
                )
//                SaveFileButton(
//                    painterResource(saveCluster.icon),
//                    stringResource(saveCluster.name),
//                    saveData = SaveData(
//                        name = saveCluster.DEFAULT_NAME,
//                        extension = saveCluster.EXTENSION, // yml
//                        otherDisplayedExtensions = saveCluster.otherDisplayedExtensions,
//                        mimeType = saveCluster.MIME_TYPE,
//                        prepareContent = saveAsYaml
//                    ),
//                    modifier = iconModifier
//                ) {
//                    println(if (it) "saved" else "not saved")
//                }
            }
//            val pngExport = EditClusterTool.PngExport
//            WithTooltip(stringResource(pngExport.description)) {
//                SaveBitmapAsPngButton(
//                    painterResource(pngExport.icon),
//                    stringResource(pngExport.name),
//                    saveData = SaveData(
//                        name = pngExport.DEFAULT_NAME,
//                        extension = pngExport.EXTENSION,
//                        mimeType = pngExport.MIME_TYPE,
//                        prepareContent = { exportAsPng() }
//                    ),
//                    modifier = iconModifier
//                ) {
//                    println(if (it) "exported" else "not exported")
//                }
//                val svgExport = EditClusterTool.SvgExport
//                SaveFileButton(
//                    painterResource(svgExport.icon),
//                    stringResource(svgExport.name),
//                    saveData = SaveData(
//                        name = svgExport.DEFAULT_NAME,
//                        extension = svgExport.EXTENSION,
//                        mimeType = svgExport.MIME_TYPE,
//                        content = exportAsSvg
//                    ),
//                    modifier = iconModifier
//                ) {
//                    println(if (it) "exported" else "not exported")
//                }
//            }
            WithTooltip(stringResource(EditClusterTool.OpenFile.description)) {
                OpenFileButton(
                    painterResource(EditClusterTool.OpenFile.icon),
                    stringResource(EditClusterTool.OpenFile.name),
                    LookupData.YAML,
                    modifier = iconModifier,
                    onOpen = loadFromYaml,
                )
            }
            WithTooltip(stringResource(EditClusterTool.Undo.description)) {
                DisableableButton(
                    painterResource(EditClusterTool.Undo.icon),
                    stringResource(EditClusterTool.Undo.name),
                    undoIsEnabled,
                    iconModifier,
                    onClick = undo
                )
            }
            WithTooltip(stringResource(EditClusterTool.Redo.description)) {
                DisableableButton(
                    painterResource(EditClusterTool.Redo.icon),
                    stringResource(EditClusterTool.Redo.name),
                    redoIsEnabled,
                    iconModifier,
                    onClick = redo
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
            Pair(viewModel.toolbarState.activeCategory, viewModel.showPanel),
            transitionSpec = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
                    .togetherWith(slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start))
            }
        ) { (activeCategory, showPanel) ->
            if (showPanel) {
                HorizontalPanel(
                    activeCategory = activeCategory,
                    compact = compact,
                    regionColor = viewModel.regionColor,
                    isToolEnabled = viewModel::toolPredicate,
                    isToolAlternativeEnabled = viewModel::toolAlternativePredicate,
                    selectTool = viewModel::selectTool,
                    getColorsByMostUsed = viewModel::getColorsByMostUsed,
                    hidePanel = viewModel::hidePanel,
                    modifier = Modifier.align(Alignment.Start)
                )
            }
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
            Pair(viewModel.toolbarState.activeCategory, viewModel.showPanel),
            transitionSpec = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
                    .togetherWith(slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start))
            }
        ) { (activeCategory, showPanel) ->
            if (showPanel) {
                VerticalPanel(
                    activeCategory = activeCategory,
                    compact = compact,
                    regionColor = viewModel.regionColor,
                    isToolEnabled = viewModel::toolPredicate,
                    isToolAlternativeEnabled = viewModel::toolAlternativePredicate,
                    selectTool = viewModel::selectTool,
                    getColorsByMostUsed = viewModel::getColorsByMostUsed,
                    hidePanel = viewModel::hidePanel,
                    modifier = Modifier.align(Alignment.Top)
                )
            }
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
    val defaultTool = viewModel.toolbarState.getDefaultTool(category)
    if (!compact)
        Spacer(Modifier.size(12.dp, 12.dp))
    val categoryModifier = modifier
        .padding(4.dp)
        .size(
            if (compact) 36.dp
            else 40.dp
        )
    if (defaultTool == null) {
        require(category.icon != null) { "no category.icon or category.default specified" }
        val name = stringResource(category.name)
        WithTooltip(name) {
            SimpleButton(
                iconPainter = painterResource(category.icon),
                name = name,
                modifier = categoryModifier,
                iconModifier = categoryModifier,
            ) {
                viewModel.switchToCategory(category, togglePanel = true)
            }
        }
    } else {
        Crossfade(defaultTool) {
            ToolButton(
                tool = defaultTool,
                enabled = viewModel.toolPredicate(defaultTool),
                regionColor = viewModel.regionColor,
                tint = tint,
                modifier = categoryModifier,
            ) { tool ->
                viewModel.selectTool(tool, togglePanel = true)
            }
        }
    }
}

// MAYBE: just make individual panel for every category instead of generalization
@Composable
private fun HorizontalPanel(
    activeCategory: EditClusterCategory,
    compact: Boolean,
    regionColor: Color,
    isToolEnabled: (EditClusterTool) -> Boolean,
    isToolAlternativeEnabled: (EditClusterTool) -> Boolean,
    selectTool: (EditClusterTool) -> Unit,
    getColorsByMostUsed: () -> List<Color>,
    hidePanel: () -> Unit,
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
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            RoundedCornerShape(24.dp)
        )
        .padding(start = 24.dp),
    ) {
        Spacer(Modifier.width(8.dp))
        for (tool in activeCategory.tools) {
            ToolButton(
                tool = tool,
                enabled = isToolEnabled(tool),
                alternative = isToolAlternativeEnabled(tool),
                regionColor = regionColor,
                modifier = toolModifier,
                onClick = selectTool
            )
        }
        if (activeCategory is EditClusterCategory.Region) { // || category is EditClusterCategory.Colors) {
            VerticalDivider(Modifier
                .height(40.dp)
                .padding(horizontal = 8.dp)
                .align(Alignment.CenterVertically)
            )
            val colorsByMostUsed = getColorsByMostUsed()
            for (color in colorsByMostUsed) {
                AppliedColorButton(color, toolModifier, selectTool)
            }
        }
        // hide panel button
        WithTooltip(stringResource(Res.string.collapse)) {
            SimpleButton(
                painterResource(Res.drawable.collapse_down),
                stringResource(Res.string.collapse),
                toolModifier.padding(4.dp),
                onClick = hidePanel
            )
        }
    }
//    LaunchedEffect(viewModel.activeTool) {
//        scrollState.animateScrollTo(viewModel.activeCategory.tools) // probs?
//    }
}

@Composable
private fun VerticalPanel(
    activeCategory: EditClusterCategory,
    compact: Boolean,
    regionColor: Color,
    isToolEnabled: (EditClusterTool) -> Boolean,
    isToolAlternativeEnabled: (EditClusterTool) -> Boolean,
    selectTool: (EditClusterTool) -> Unit,
    getColorsByMostUsed: () -> List<Color>,
    hidePanel: () -> Unit,
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
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                RoundedCornerShape(24.dp)
            )
        ,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        for (tool in activeCategory.tools) {
            ToolButton(
                tool = tool,
                enabled = isToolEnabled(tool),
                alternative = isToolAlternativeEnabled(tool),
                regionColor = regionColor,
                modifier = toolModifier,
                onClick = selectTool
            )
        }
        if (activeCategory is EditClusterCategory.Region) { // || category is EditClusterCategory.Colors) {
            HorizontalDivider(Modifier
                .width(40.dp)
                .padding(vertical = 8.dp)
                .align(Alignment.CenterHorizontally)
            )
            val colorsByMostUsed = getColorsByMostUsed()
            for (color in colorsByMostUsed) {
                AppliedColorButton(color, toolModifier, selectTool)
            }
        }
        // hide panel button
        WithTooltip(stringResource(Res.string.collapse)) {
            SimpleButton(
                painterResource(Res.drawable.collapse_left),
                stringResource(Res.string.collapse),
                toolModifier.padding(4.dp),
                onClick = hidePanel
            )
        }
    }
}

// all-included multiplexer
/**
 * @param[regionColor] only used for Palette color
 * */
@Composable
fun ToolButton(
    tool: EditClusterTool,
    enabled: Boolean,
    alternative: Boolean = false,
    regionColor: Color,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier.padding(4.dp),
    onClick: (EditClusterTool) -> Unit,
) {
    val icon = painterResource(tool.icon)
    val name = stringResource(tool.name)
    val description = stringResource(tool.description)
    val callback = { onClick(tool) }
    WithTooltip(description) {
        when (tool) {
            EditClusterTool.Palette -> {
                PaletteButton(regionColor, modifier, callback)
            }
            is EditClusterTool.AppliedColor -> {
                IconButton(
                    onClick = callback,
                    modifier = modifier,
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = name,
                        modifier = modifier,
                        tint = tool.color,
                    )
                }
            }
            is EditClusterTool.FillChessboardPattern -> {
                ThreeIconButton(
                    iconPainter = icon,
                    alternativeIconPainter = painterResource(tool.alternativeEnabledIcon),
                    disabledIconPainter = painterResource(tool.disabledIcon!!),
                    name = name,
                    enabled = enabled,
                    alternative = alternative,
                    modifier = modifier,
                    tint = tint,
                    onClick = callback
                )
            }
            is Tool.InstantAction -> {
                SimpleButton(
                    iconPainter = icon,
                    name = name,
                    modifier = modifier,
                    tint = tint,
                    onClick = callback
                )
            }
            is Tool.BinaryToggle -> {
                if (tool.disabledIcon == null) {
                    OnOffButton(
                        iconPainter = icon,
                        name = name,
                        isOn = enabled,
                        modifier = modifier,
                        tint = tint,
                        onClick = callback
                    )
                } else {
                    TwoIconButton(
                        iconPainter = icon,
                        disabledIconPainter = painterResource(tool.disabledIcon!!),
                        name = name,
                        enabled = enabled,
                        modifier = modifier,
                        tint = tint,
                        onClick = callback
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

@Composable
fun AppliedColorButton(
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (EditClusterTool.AppliedColor) -> Unit,
) {
    val tool = EditClusterTool.AppliedColor(color)
    val icon = painterResource(tool.icon)
    val name = stringResource(tool.name)
    val description = stringResource(tool.description)
    WithTooltip(description) {
        IconButton(
            onClick = { onClick(tool) },
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
}