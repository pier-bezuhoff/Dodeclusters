package ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import core.geometry.CircleOrLine
import core.geometry.ImaginaryCircle
import core.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.add_circle
import dodeclusters.composeapp.generated.resources.cancel
import dodeclusters.composeapp.generated.resources.collapse
import dodeclusters.composeapp.generated.resources.collapse_down
import dodeclusters.composeapp.generated.resources.collapse_left
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.save_prompt_after_blank_description
import dodeclusters.composeapp.generated.resources.three_dots_in_angle_brackets
import dodeclusters.composeapp.generated.resources.tool_arg_input_prompt
import dodeclusters.composeapp.generated.resources.tool_arg_parameter_adjustment_prompt
import domain.LoadingState
import domain.ProgressState
import domain.io.DdcSharing
import domain.io.LookupData
import domain.io.OpenFileButton
import domain.io.SaveConfig
import domain.model.PartialArgList
import domain.model.SaveState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import ui.DisableableButton
import ui.LifecycleEvent
import ui.LoadingOverlay
import ui.OnOffButton
import ui.SimpleButton
import ui.SnackbarWithHighlightMarkdown
import ui.ThreeIconButton
import ui.TwoIconButton
import ui.WithTooltip
import ui.editor.dialogs.BiInversionDialog
import ui.editor.dialogs.BlendSettingsDialog
import ui.editor.dialogs.CircleExtrapolationDialog
import ui.editor.dialogs.CircleOrPointInterpolationDialog
import ui.editor.dialogs.ColorPickerDialog
import ui.editor.dialogs.DialogAction
import ui.editor.dialogs.DialogType
import ui.editor.dialogs.LabelInputDialog
import ui.editor.dialogs.LoxodromicMotionDialog
import ui.editor.dialogs.RotationDialog
import ui.editor.dialogs.SaveOptionsDialog
import ui.editor.dialogs.SavePromptDialog
import ui.theme.ColorTheme
import ui.theme.DodeclustersColors
import ui.theme.DodeclustersTheme
import ui.theme.adaptiveSizing
import ui.theme.extendedColorScheme
import ui.theme.isDarkTheme
import ui.tools.Category
import ui.tools.ITool
import ui.tools.Tool
import kotlin.math.max
import kotlin.math.min

/**
 * @param[ddcFlow] external ddc requests (url params or android implicit intent)
 * @param[keyboardActions] used to pipe keyboard events, null means they will be caught using
 * `Modifier.onPreviewKeyEvent
 * @param[lifecycleEvents] emits SaveUiState events that prompt to autosave the current state,
 * mechanism, analogous to SavedStateHand
 * @param[ddcSharing] state-backed ddc-sharing implementation, presently only
 * supplied on Wasm after the request to register current user is answered
 * (null -> smol delay -> real implementation)
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun EditorScreenRoot(
    openSettings: () -> Unit,
    ddcFlow: SharedFlow<LoadingState<String>?> = MutableSharedFlow(),
    keyboardActions: SharedFlow<KeyboardAction>? = null,
    lifecycleEvents: SharedFlow<LifecycleEvent> = MutableSharedFlow(),
    ddcSharing: DdcSharing? = null,
    // MAYBE: hoist VM before NavDisplay for persistence?
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val isLandscape = MaterialTheme.adaptiveSizing.isLandscape
    val coroutineScope = rememberCoroutineScope()
    val dialogActions = keyboardActions?.mapNotNull {
        when (it) {
            KeyboardAction.CANCEL -> DialogAction.DISMISS
            KeyboardAction.CONFIRM -> DialogAction.CONFIRM
            else -> null
        }
    }?.shareIn(coroutineScope, SharingStarted.Eagerly, replay = 0)
    val vmRestoration by viewModel.restoration.collectAsStateWithLifecycle()
    val ddcContent: LoadingState<String>? by ddcFlow.collectAsStateWithLifecycle(null)
    val snackbarHostState = remember { SnackbarHostState() }
    EditorScreen(
        openMenu = {
            println("open menu")
            openSettings()
        },
        hidePanel = viewModel::hidePanel,
        showSaveOptionsDialog = { viewModel.toolAction(Tool.SaveCluster) },
        openNewBlank = viewModel::newBlank,
        loadFromYaml = { content, filename ->
            content?.let {
                viewModel.loadDdc(content, filename)
            }
        },
        undo = viewModel::undo,
        redo = viewModel::redo,
        isToolEnabled = viewModel::toolPredicate,
        isToolAlternativeEnabled = viewModel::toolAlternativePredicate,
        switchToCategory = { viewModel.switchToCategory(it, togglePanel = true) },
        selectTool = { viewModel.selectTool(it, togglePanel = true) },
        getColorsByMostUsed = viewModel::getColorsByMostUsed,
        snackbarHostState = snackbarHostState,
        toolbarState = viewModel.toolbarState,
        ddcContent = ddcContent,
        isLandscape = isLandscape,
        showUI = viewModel.showUI,
        showPanel = viewModel.showPanel,
        regionColor = viewModel.regionColor,
        backgroundColor = viewModel.backgroundColor,
        regionManipulationStrategy = viewModel.regionManipulationStrategy,
        partialArgList = viewModel.partialArgList,
        undoIsEnabled = viewModel.undoIsEnabled.value,
        redoIsEnabled = viewModel.redoIsEnabled.value,
        saveConfig = viewModel.saveConfig,
        openFileRequests = viewModel.openFileRequests,
        editorCanvas = {
            EditorCanvas(viewModel)
        },
        modifier = if (keyboardActions == null)
            // ig it's only for android w/ keyboard
            Modifier.handleKeyboardActions(viewModel::processKeyboardAction)
        else Modifier,
    )
    val extendedColorScheme = MaterialTheme.extendedColorScheme
    when (viewModel.openedDialog) {
        DialogType.REGION_FILL_COLOR_PICKER -> {
            ColorPickerDialog(
                parameters = viewModel.colorPickerParameters.copy(
                    currentColor = viewModel.regionColor,
                    usedColors = viewModel.getColorsByMostUsed(),
                ),
                onCancel = viewModel::dismissRegionColorPicker,
                onConfirm = viewModel::concludeRegionColorPicker,
                dialogActions = dialogActions,
            )
        }
        DialogType.BORDER_COLOR_PICKER -> {
            val initialColor = viewModel.getMostCommonBorderColorInSelection()
                ?: if (viewModel.objectSelection.all { viewModel.objects[it] is ImaginaryCircle })
                    DodeclustersColors.fadedRed.copy(alpha = 1f) // imaginary circle
                else
                    MaterialTheme.extendedColorScheme.highAccentColor // free real circle
            ColorPickerDialog(
                parameters = viewModel.colorPickerParameters.copy(
                    currentColor = initialColor,
                    usedColors = viewModel.getColorsByMostUsed(),
                ),
                onCancel = viewModel::closeDialog,
                onConfirm = viewModel::concludeBorderColorPicker,
                dialogActions = dialogActions,
            )
        }
        DialogType.FILL_COLOR_PICKER -> {
            val initialColor = viewModel.getMostCommonFillColorInSelection() ?: viewModel.regionColor
            ColorPickerDialog(
                parameters = viewModel.colorPickerParameters.copy(
                    currentColor = initialColor,
                    usedColors = viewModel.getColorsByMostUsed(),
                ),
                onCancel = viewModel::closeDialog,
                onConfirm = viewModel::concludeFillColorPicker,
                dialogActions = dialogActions,
            )
        }
        DialogType.BACKGROUND_COLOR_PICKER -> {
            val initialColor = viewModel.backgroundColor ?: MaterialTheme.colorScheme.background
            ColorPickerDialog(
                parameters = viewModel.colorPickerParameters.copy(
                    currentColor = initialColor,
                    usedColors = viewModel.getColorsByMostUsed(),
                ),
                onCancel = viewModel::closeDialog,
                onConfirm = viewModel::concludeBackgroundColorPicker,
                dialogActions = dialogActions,
            )
        }
        DialogType.CIRCLE_OR_POINT_INTERPOLATION -> {
            if (viewModel.partialArgList?.isFull == true) {
                val (startObject, endObject) = viewModel.partialArgList!!.args
                    .map {
                        viewModel.getArg(it)
                    }
                if (startObject != null && endObject != null) {
                    CircleOrPointInterpolationDialog(
                        startObject, endObject,
                        onCancel = viewModel::closeDialog,
                        onConfirm = viewModel::confirmDialogSelectedParameters,
                        defaults = viewModel.defaultInterpolationParameters,
                        dialogActions = dialogActions,
                    )
                }
            }
        }
        DialogType.CIRCLE_EXTRAPOLATION -> {
            if (viewModel.partialArgList?.isFull == true) {
                val (startCircle, endCircle) = viewModel.partialArgList!!.args
                    .map {
                        viewModel.getArg(it) as CircleOrLine
                    }
                CircleExtrapolationDialog(
                    startCircle, endCircle,
                    onDismissRequest = viewModel::resetCircleExtrapolation,
                    onConfirm = viewModel::completeCircleExtrapolation,
                    defaults = viewModel.defaultExtrapolationParameters,
                )
            }
        }
        DialogType.ROTATION -> {
            if (viewModel.partialArgList?.isFull == true) {
                RotationDialog(
                    onCancel = viewModel::closeDialog,
                    onConfirm = viewModel::confirmDialogSelectedParameters,
                    defaults = viewModel.defaultRotationParameters,
                    dialogActions = dialogActions,
                )
            }
        }
        DialogType.BI_INVERSION -> {
            if (viewModel.partialArgList?.isFull == true) {
                val (engine1, engine2) = viewModel.partialArgList!!.args
                    .drop(1)
                    .map { viewModel.getArg(it) as? CircleOrLine }
                if (engine1 != null && engine2 != null) {
                    BiInversionDialog(
                        engine1, engine2,
                        onCancel = viewModel::closeDialog,
                        onConfirm = viewModel::confirmDialogSelectedParameters,
                        defaults = viewModel.defaultBiInversionParameters,
                        dialogActions = dialogActions,
                    )
                }
            }
        }
        DialogType.LOXODROMIC_MOTION -> {
            if (viewModel.partialArgList?.isFull == true) {
                val (divergencePoint, convergencePoint) = viewModel.partialArgList!!.args
                    .drop(1)
                    .map { viewModel.getArg(it) as? Point }
                if (divergencePoint != null && convergencePoint != null) {
                    LoxodromicMotionDialog(
                        onCancel = viewModel::closeDialog,
                        onConfirm = viewModel::confirmDialogSelectedParameters,
                        defaults = viewModel.defaultLoxodromicMotionParameters,
                        dialogActions = dialogActions,
                    )
                }
            }
        }
        DialogType.SAVE_OPTIONS -> {
            SaveOptionsDialog(
                viewModel = viewModel,
                ddcSharing = ddcSharing,
                saveAsYaml = viewModel::saveAsYaml,
                exportAsSvg = { name ->
                    viewModel.exportAsSvg(
                        name = name,
                        extendedColorScheme = extendedColorScheme
                    )
                },
                onCancel = viewModel::closeDialog,
                onConfirm = viewModel::closeDialog,
                onSaved = { saveResult ->
                    viewModel.onSaveFinished(saveResult)
                },
                saveConfig = viewModel.saveConfig,
                saveRequests = viewModel.saveFileRequests,
                dialogActions = dialogActions,
            )
        }
        DialogType.SAVE_PROMPT -> {
            SavePromptDialog(
                description = stringResource(Res.string.save_prompt_after_blank_description),
                onCancel = viewModel::closeDialog,
                onDontSave = viewModel::openNewBlank,
                onSave = viewModel::requestSaveFileAs,
            )
        }
        DialogType.BLEND_SETTINGS -> {
            BlendSettingsDialog(
                currentOpacity = viewModel.regionsOpacity,
                currentBlendModeType = viewModel.regionsBlendModeType,
                onCancel = viewModel::closeDialog,
                onConfirm = viewModel::setBlendSettings,
                dialogActions = dialogActions,
            )
        }
        DialogType.LABEL_INPUT -> {
            LabelInputDialog(
                previousLabel = viewModel.objectSelection
                    .firstNotNullOfOrNull { viewModel.labels[it] }
                ,
                // for debug
//                details = "expr[${viewModel.selection.firstOrNull()}] = ${
//                    viewModel.selection.firstOrNull()?.let {
//                        viewModel.expressions.expressions[it]
//                    }
//                }",
                onCancel = viewModel::closeDialog,
                onConfirm = viewModel::setLabel,
                dialogActions = dialogActions,
            )
        }
        null -> {}
    }
    val density = LocalDensity.current
    LaunchedEffect(viewModel, density) {
        viewModel.setEpsilon(density)
    }
    LaunchedEffect(viewModel, ddcContent, vmRestoration) {
        when (vmRestoration) {
            ProgressState.COMPLETED -> {
                when (val content = ddcContent) {
                    null -> {}
                    is LoadingState.InProgress -> {}
                    is LoadingState.Completed<String> -> {
                        viewModel.loadDdc(content.result)
                    }
                    is LoadingState.Error -> {
                        println(content.exception.message ?: "Error")
                        content.exception.message?.let { message ->
                            viewModel.showSnackbarMessage(SnackbarMessage.PLACEHOLDER, message)
                        }
                    }
                }
            }
            else -> {}
        }
    }
    LaunchedEffect(viewModel, keyboardActions) {
        keyboardActions?.let {
            keyboardActions.collect { action ->
                viewModel.processKeyboardAction(action)
            }
        }
    }
    LaunchedEffect(viewModel, lifecycleEvents) {
        lifecycleEvents.collectLatest { action ->
            when (action) {
                LifecycleEvent.SaveUIState -> {
                    viewModel.cacheState()
                }
            }
        }
    }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.snackbarMessages.collectLatest { (message, formatArgs) ->
            val result = snackbarHostState.showSnackbar(
                message = getString(message.messageResource, *formatArgs),
                actionLabel = message.actionLabelResource?.let { getString(it) },
                withDismissAction = message.withDismissAction,
                duration = message.duration,
            )
            when (result) {
                SnackbarResult.ActionPerformed ->
                    viewModel.onSnackbarAction(message)
                SnackbarResult.Dismissed -> {}
            }
        }
    }
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = MaterialTheme.isDarkTheme
    LaunchedEffect(viewModel.backgroundColor, isDarkTheme, colorScheme) {
        // ts doesnt work often
        if (viewModel.backgroundColor == null ||
            isDarkTheme && viewModel.backgroundColor == DodeclustersColors.lightScheme.surface ||
            !isDarkTheme && viewModel.backgroundColor == DodeclustersColors.darkScheme.surface
        ) {
            viewModel.backgroundColor = colorScheme.surface
        }
    }
    preloadIcons()
}

@Composable
fun EditorScreen(
    openMenu: () -> Unit = {},
    hidePanel: () -> Unit = {},
    openNewBlank: () -> Unit = {},
    showSaveOptionsDialog: () -> Unit = {},
    loadFromYaml: (content: String?, filename: String?) -> Unit = { _, _ -> },
    undo: () -> Unit = {},
    redo: () -> Unit = {},
    isToolEnabled: (Tool) -> Boolean,
    isToolAlternativeEnabled: (Tool) -> Boolean,
    switchToCategory: (Category) -> Unit = {},
    selectTool: (Tool) -> Unit = {},
    getColorsByMostUsed: () -> List<Color>,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    toolbarState: ToolbarState,
    ddcContent: LoadingState<String>? = null,
    isLandscape: Boolean,
    showUI: Boolean,
    showPanel: Boolean,
    regionColor: Color,
    backgroundColor: Color?,
    regionManipulationStrategy: RegionManipulationStrategy,
    partialArgList: PartialArgList?,
    undoIsEnabled: Boolean,
    redoIsEnabled: Boolean,
    saveConfig: SaveConfig = SaveConfig(),
    openFileRequests: SharedFlow<Unit>? = null,
    modifier: Modifier = Modifier,
    editorCanvas: @Composable (BoxScope.() -> Unit),
) {
    Scaffold(
        // ig this may only be useful on android with kbd lol
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                SnackbarWithHighlightMarkdown(data,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    highlightColor = MaterialTheme.extendedColorScheme.highAccentColor,
                    actionContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    actionColor = MaterialTheme.colorScheme.onSecondary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        floatingActionButton = {
            if (!isLandscape && showUI) {
                // MAYBE: only inline with any WindowSizeClass is Expanded (i.e. non-mobile)
                FAB(
                    switchToCreateCategory = { switchToCategory(Category.Create) },
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) {
        Surface {
            Box(Modifier.drawBehind {
                backgroundColor?.let { backgroundColor ->
                    drawRect(backgroundColor, size = size)
                }
            }) {
                editorCanvas()
                if (showUI) {
                    ToolDescription(
                        tool = toolbarState.activeTool,
                        toolIsEnabled = isToolEnabled(toolbarState.activeTool),
                        regionManipulationStrategy = regionManipulationStrategy,
                        partialArgList = partialArgList,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    EditorTopBar(undoIsEnabled = undoIsEnabled, redoIsEnabled = redoIsEnabled, showSaveOptionsDialog = showSaveOptionsDialog, openNewBlank = openNewBlank, loadFromYaml = loadFromYaml, undo = undo, redo = redo, openMenu = openMenu, saveConfig = saveConfig, openFileRequests = openFileRequests,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                    if (isLandscape) {
                        ToolbarLandscape(toolbarState = toolbarState, showPanel = showPanel, regionColor = regionColor, hidePanel = hidePanel, isToolEnabled = isToolEnabled, isToolAlternativeEnabled = isToolAlternativeEnabled, switchToCategory = switchToCategory, selectTool = selectTool, getColorsByMostUsed = getColorsByMostUsed,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    } else {
                        ToolbarPortrait(toolbarState = toolbarState, showPanel = showPanel, regionColor = regionColor, hidePanel = hidePanel, isToolEnabled = isToolEnabled, isToolAlternativeEnabled = isToolAlternativeEnabled, switchToCategory = switchToCategory, selectTool = selectTool, getColorsByMostUsed = getColorsByMostUsed,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
                when (ddcContent) {
                    is LoadingState.InProgress ->
                        LoadingOverlay(ddcContent)
                    else -> {}
                }
            }
        }
    }
}

@Preview
@Composable
fun EditorScreenPreview() {
    DodeclustersTheme(ColorTheme.DARK) {
        val toolbarState = ToolbarState(
            activeCategory = Category.Drag,
            activeTool = Tool.Drag,
        )
        EditorScreen(
            toolbarState = toolbarState,
            showPanel = false, // cannot show panel when in drag category
            isLandscape = true,
            showUI = true,
            regionColor = Color.Blue,
            backgroundColor = null,
            regionManipulationStrategy = RegionManipulationStrategy.REPLACE,
            partialArgList = null,
            undoIsEnabled = true,
            redoIsEnabled = false,
            isToolEnabled = { false },
            isToolAlternativeEnabled = { false },
            getColorsByMostUsed = { emptyList() },
            editorCanvas = {
                Canvas(Modifier.fillMaxSize()) {}
            },
        )
    }
}

/** Loads all tool icons and caches them.
 * Otherwise icons only start being loaded when the corresponding category panel is open,
 * which is noticeable & jarring */
@Suppress("ComposableNaming")
@Composable
private fun preloadIcons() {
    val categoryList = listOf(
        Category.Drag,
        Category.Multiselect,
        Category.Region,
        Category.Visibility,
        Category.Transform,
        Category.Create,
    )
    val toolList = categoryList
        .flatMap { it.tools }
        .plus(
            listOf(
                Tool.Expand, Tool.Shrink,
                Tool.BorderColor,
                Tool.MarkAsPhantoms,
                Tool.SwapDirection,
                Tool.Detach,
                Tool.Duplicate,
                Tool.Delete,
                Tool.DetailedAdjustment,
                Tool.InBetween,
                Tool.ReverseDirection,
                Tool.PngExport,
                Tool.SvgExport,
                Tool.InfinitePoint,
            )
        )
    for (tool in toolList) {
        painterResource(tool.icon)
        if (tool is ITool.BinaryToggle) {
            tool.disabledIcon?.let {
                painterResource(it)
            }
        }
    }
    for (resource in listOf(
        Tool.FillChessboardPattern.alternativeIcon,
        // from dialogs
        Res.drawable.confirm, Res.drawable.cancel,
        Res.drawable.collapse_down, Res.drawable.collapse_left,
        Res.drawable.add_circle, // color-picker:save=add
        // from canvas HUD
        Res.drawable.rotate_counterclockwise,
        Res.drawable.three_dots_in_angle_brackets,
    )) {
        painterResource(resource)
    }
}

@Composable
private fun FAB(
    switchToCreateCategory: () -> Unit,
) {
    // MAYBE: only inline with any WindowSizeClass is Expanded (i.e. non-mobile)
    val category = Category.Create
    FloatingActionButton(
        onClick = switchToCreateCategory,
        modifier =
            if (MaterialTheme.adaptiveSizing.isCompactVertically) Modifier
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

@Composable
private fun ToolDescription(
    tool: Tool,
    toolIsEnabled: Boolean,
    regionManipulationStrategy: RegionManipulationStrategy,
    partialArgList: PartialArgList?,
    modifier: Modifier = Modifier
) {
    val isCompact = MaterialTheme.adaptiveSizing.isCompact
    val isLandscape = MaterialTheme.adaptiveSizing.isLandscape
    val textStyle =
        if (isCompact) MaterialTheme.typography.bodySmall
        else MaterialTheme.typography.titleMedium
    val inputPrompt = stringResource(Res.string.tool_arg_input_prompt)
    val confirmParametersPrompt = stringResource(Res.string.tool_arg_parameter_adjustment_prompt)
    Column(
        modifier
            .offset(x = if (isLandscape) 100.dp else 0.dp) // offsetting left toolbar
            .fillMaxWidth(if (isCompact) 0.45f else 0.5f) // we cant specify max text length, so im doing this
    ) {
        Crossfade(Pair(tool, toolIsEnabled)) { (currentTool, currentToolIsEnabled) ->
            val description = when (currentTool) {
                Tool.Region, Tool.FlowFill ->
                    stringResource(currentTool.description) + " | " +
                        stringResource(regionManipulationStrategy.descriptionPostfixResource)
                is ITool.BinaryToggle ->
                    if (currentToolIsEnabled)
                        stringResource(currentTool.description)
                    else
                        stringResource(currentTool.disabledDescription)
                else -> stringResource(currentTool.description)
            }
//            val descriptionAnnotatedString = HighlightMarkdown.parse(
//                description,
//                highlightColor = MaterialTheme.extendedColorScheme.highAccentColor,
//            )
            Text(
                description,
                modifier
                    .padding(8.dp, 12.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        MaterialTheme.shapes.extraLarge,
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                        MaterialTheme.shapes.extraLarge,
                    )
                    .padding(16.dp, 8.dp)
                ,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = textStyle,
            )
        }
        val argDescriptions = (tool as? Tool.MultiArg)?.let {
            stringArrayResource(it.argDescriptions)
        }
        val number =
            if (partialArgList == null ||
                tool !is Tool.MultiArg ||
                partialArgList.isFull && !partialArgList.lastArgIsConfirmed
            )
                null
            else if (partialArgList.isFull && partialArgList.lastArgIsConfirmed)
                -1 // indicates expr-adj submode
            else if (partialArgList.lastArgIsConfirmed)
                min(partialArgList.args.size, tool.signature.argTypes.size - 1)
            else
                max(0, partialArgList.args.size - 1)
        AnimatedContent(Pair(tool, number)) { (currentTool, currentNumber) ->
            if (currentTool is Tool.MultiArg &&
                currentNumber != null &&
                argDescriptions != null &&
                argDescriptions.size > currentNumber
            ) {
                val argDescription =
                    if (currentNumber == -1) null
                else
                    argDescriptions[currentNumber]
                val argPrompt =
                    if (currentNumber == -1)
                        confirmParametersPrompt
                    else
                        "$inputPrompt: $argDescription"
//                val argPromptAnnotatedString = HighlightMarkdown.parse(
//                    argPrompt,
//                    highlightColor = MaterialTheme.extendedColorScheme.highAccentColor,
//                )
                if (!isCompact) {
                    Text(argPrompt,
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
private fun EditorTopBar(
    undoIsEnabled: Boolean,
    redoIsEnabled: Boolean,
    openNewBlank: () -> Unit,
    showSaveOptionsDialog: () -> Unit,
    loadFromYaml: (content: String?, filename: String?) -> Unit,
    undo: () -> Unit,
    redo: () -> Unit,
    openMenu: () -> Unit,
    saveConfig: SaveConfig,
    modifier: Modifier = Modifier,
    openFileRequests: SharedFlow<Unit>? = null,
) {
    val isCompact = MaterialTheme.adaptiveSizing.isCompact
    val iconModifier =
        if (isCompact) Modifier.padding(4.dp).size(30.dp)
        else Modifier.padding(8.dp, 4.dp).size(40.dp)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val toolbarHeight = if (isCompact) 48.dp else 64.dp
    // bad in portrait, fine in landscape
    Row(modifier
        // NOTE: i might be hallucinating but ive seen this break tooltip positioning, now it works tho (?)
        .offset(x = 24.dp, y = -24.dp) // leave only 1, bottom-left rounded corner
        .background(
            Brush.verticalGradient(
                0.3f to backgroundColor.copy(alpha = 1.0f),
                1f to backgroundColor.copy(alpha = 0.5f),
            ),
            MaterialTheme.shapes.extraLarge,
        )
        .padding(top = 24.dp, end = 24.dp) // offsets the corner-removing offset
        .height(toolbarHeight),
        Arrangement.End,
        Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Spacer(Modifier.width(16.dp))
            // MAYBE: button to create new [empty?] document
            //  (maybe only on wide-width screens)
            // TODO: move it to context slidesheet
            WithTooltip(stringResource(Tool.NewBlank.description)) {
                SimpleButton(
                    painterResource(Tool.NewBlank.icon),
                    stringResource(Tool.NewBlank.name),
                    iconModifier = iconModifier,
                    onClick = openNewBlank,
                )
            }
            WithTooltip(stringResource(Tool.SaveCluster.description)) {
                SimpleButton(
                    painterResource(Tool.SaveCluster.icon),
                    stringResource(Tool.SaveCluster.name),
                    iconModifier = iconModifier,
                    onClick = showSaveOptionsDialog
                )
            }
            WithTooltip(stringResource(Tool.OpenFile.description)) {
                OpenFileButton(
                    painterResource(Tool.OpenFile.icon),
                    stringResource(Tool.OpenFile.name),
                    LookupData.YAML.copy(directory = saveConfig.directory),
                    iconModifier = iconModifier,
                    openRequests = openFileRequests,
                    onOpen = loadFromYaml,
                )
            }
            VerticalDivider(Modifier
                .padding(horizontal = 8.dp)
                .fillMaxHeight(0.6f)
                .align(Alignment.CenterVertically)
            )
            WithTooltip(stringResource(Tool.Undo.description)) {
                DisableableButton(
                    painterResource(Tool.Undo.icon),
                    stringResource(Tool.Undo.name),
                    enabled = undoIsEnabled,
                    iconModifier = iconModifier,
                    onClick = undo
                )
            }
            WithTooltip(stringResource(Tool.Redo.description)) {
                DisableableButton(
                    painterResource(Tool.Redo.icon),
                    stringResource(Tool.Redo.name),
                    enabled = redoIsEnabled,
                    iconModifier = iconModifier,
                    onClick = redo
                )
            }
            VerticalDivider(Modifier
                .padding(horizontal = 8.dp)
                .fillMaxHeight(0.6f)
                .align(Alignment.CenterVertically)
            )
            WithTooltip(stringResource(Tool.ToggleMenu.description)) {
                SimpleButton(
                    painterResource(Tool.ToggleMenu.icon),
                    stringResource(Tool.ToggleMenu.name),
                    iconModifier = iconModifier,
                    onClick = openMenu,
                )
            }
        }
    }
}

@Composable
private fun ToolbarPortrait(
    toolbarState: ToolbarState,
    showPanel: Boolean,
    regionColor: Color,
    hidePanel: () -> Unit,
    isToolEnabled: (Tool) -> Boolean,
    isToolAlternativeEnabled: (Tool) -> Boolean,
    switchToCategory: (Category) -> Unit,
    selectTool: (Tool) -> Unit,
    getColorsByMostUsed: () -> List<Color>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        AnimatedContent(
            Pair(toolbarState.activeCategory, showPanel),
            transitionSpec = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
                    .togetherWith(slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start))
            }
        ) { (activeCategory, showPanel) ->
            if (showPanel) {
                HorizontalPanel(
                    activeCategory = activeCategory,
                    regionColor = regionColor,
                    isToolEnabled = isToolEnabled,
                    isToolAlternativeEnabled = isToolAlternativeEnabled,
                    selectTool = selectTool,
                    getColorsByMostUsed = getColorsByMostUsed,
                    hidePanel = hidePanel,
                    modifier = Modifier.align(Alignment.Start)
                )
            }
        }
        BottomToolbar(
            toolbarState = toolbarState,
            regionColor = regionColor,
            isToolEnabled = isToolEnabled,
            switchToCategory = switchToCategory,
            selectTool = selectTool,
            modifier = Modifier.align(Alignment.Start),
        )
    }
}

@Composable
private fun ToolbarLandscape(
    toolbarState: ToolbarState,
    showPanel: Boolean,
    regionColor: Color,
    hidePanel: () -> Unit,
    isToolEnabled: (Tool) -> Boolean,
    isToolAlternativeEnabled: (Tool) -> Boolean,
    switchToCategory: (Category) -> Unit,
    selectTool: (Tool) -> Unit,
    getColorsByMostUsed: () -> List<Color>,
    modifier: Modifier = Modifier,
) {
    Row(modifier,
        horizontalArrangement = Arrangement.Start
    ) {
        LeftToolbar(
            toolbarState = toolbarState,
            regionColor = regionColor,
            isToolEnabled = isToolEnabled,
            switchToCategory = switchToCategory,
            selectTool = selectTool,
            modifier = Modifier
//            .zIndex(1f)
                .align(Alignment.CenterVertically),
        )
        AnimatedContent(
            Pair(toolbarState.activeCategory, showPanel),
            transitionSpec = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
                    .togetherWith(slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start))
            }
        ) { (activeCategory, showPanel) ->
            if (showPanel) {
                VerticalPanel(
                    activeCategory = activeCategory,
                    regionColor = regionColor,
                    isToolEnabled = isToolEnabled,
                    isToolAlternativeEnabled = isToolAlternativeEnabled,
                    selectTool = selectTool,
                    getColorsByMostUsed = getColorsByMostUsed,
                    hidePanel = hidePanel,
                    modifier = Modifier.align(Alignment.Top)
                )
            }
        }
    }
}

@Composable
private fun BottomToolbar(
    toolbarState: ToolbarState,
    regionColor: Color,
    isToolEnabled: (Tool) -> Boolean,
    switchToCategory: (Category) -> Unit,
    selectTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = MaterialTheme.adaptiveSizing.isCompact
//    val scrollState = rememberScrollState()
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val toolbarSize =
        if (isCompact) 48.dp
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
            listOf(Category.Drag, Category.Multiselect, Category.Region).forEach {
                CategoryButton(it, toolbarState, regionColor, isToolEnabled, switchToCategory, selectTool)
            }
            Spacer(Modifier.size(12.dp, 0.dp))
            VerticalDivider(Modifier
                .fillMaxHeight(0.7f)
                .align(Alignment.CenterVertically)
            )
            listOf(Category.Visibility, Category.Colors, Category.Transform).forEach {
                CategoryButton(it, toolbarState, regionColor, isToolEnabled, switchToCategory, selectTool)
            }
        }
    }
}

@Composable
private fun LeftToolbar(
    toolbarState: ToolbarState,
    regionColor: Color,
    isToolEnabled: (Tool) -> Boolean,
    switchToCategory: (Category) -> Unit,
    selectTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = MaterialTheme.adaptiveSizing.isCompact
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val toolbarSize =
        if (isCompact) 48.dp
        else 64.dp
    Column(
        modifier
            .background(
                Brush.horizontalGradient(
                    0f to backgroundColor,
                    1f to backgroundColor.copy(alpha = 0.7f),
                ),
                MaterialTheme.shapes.extraLarge,
            )
            .width(toolbarSize)
        ,
        Arrangement.Top,
        Alignment.CenterHorizontally
    ) {
        val dividerPaddings =
            if (isCompact) PaddingValues(vertical = 6.dp)
            else PaddingValues(top = 12.dp) // every CategoryButton already has 12dp high spacer on the top
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (isCompact) {
                Spacer(Modifier.height(6.dp))
            }
            listOf(Category.Drag, Category.Multiselect, Category.Region).forEach {
                CategoryButton(it, toolbarState, regionColor, isToolEnabled, switchToCategory, selectTool)
            }
            HorizontalDivider(Modifier
                .padding(dividerPaddings)
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally)
            )
            listOf(Category.Visibility, Category.Colors, Category.Transform).forEach {
                CategoryButton(it, toolbarState, regionColor, isToolEnabled, switchToCategory, selectTool)
            }
            HorizontalDivider(Modifier
                .padding(dividerPaddings)
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally)
            )
            CategoryButton(Category.Create, toolbarState, regionColor, isToolEnabled, switchToCategory, selectTool,
//                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(
                if (isCompact) 6.dp else 12.dp
            ))
        }
    }
}

@Composable
fun CategoryButton(
    category: Category,
    toolbarState: ToolbarState,
    regionColor: Color,
    isToolEnabled: (Tool) -> Boolean,
    switchToCategory: (Category) -> Unit,
    selectTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val isCompact = MaterialTheme.adaptiveSizing.isCompact
    if (!isCompact)
        Spacer(Modifier.size(12.dp, 12.dp))
    val categoryModifier = modifier
        .padding(4.dp)
        .size(
            if (isCompact) 36.dp
            else 40.dp
        )
    val defaultTool = toolbarState.getDefaultTool(category)
    if (defaultTool == null) {
        require(category.icon != null) { "no category.icon or category.default specified" }
        val name = stringResource(category.name)
        WithTooltip(name) {
            SimpleButton(
                iconPainter = painterResource(category.icon),
                contentDescription = name,
                modifier = categoryModifier,
                iconModifier = categoryModifier,
                onClick = { switchToCategory(category) }
            )
        }
    } else {
        Crossfade(defaultTool) { currentDefaultTool ->
            ToolButton(
                tool = currentDefaultTool,
                enabled = isToolEnabled(currentDefaultTool),
                regionColor = regionColor,
                tint = tint,
                modifier = categoryModifier,
                onClick = { selectTool(it) }
            )
        }
    }
}

// MAYBE: just make individual panel for every category instead of generalization
@Composable
private fun HorizontalPanel(
    activeCategory: Category,
    regionColor: Color,
    isToolEnabled: (Tool) -> Boolean,
    isToolAlternativeEnabled: (Tool) -> Boolean,
    selectTool: (Tool) -> Unit,
    getColorsByMostUsed: () -> List<Color>,
    hidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // shown on the top of the bottom toolbar
    // scrollable lazy row, w = wrap content
    // can be shown or hidden with a collapse button at the end
    require(activeCategory.tools.size > 1)
    val isCompact = MaterialTheme.adaptiveSizing.isCompact
    val toolModifier =
        if (isCompact) Modifier.padding(4.dp).size(30.dp)
        else Modifier
    // scrollable row + highlight selected tool
    val scrollState = rememberScrollState()
    // mb wrap in a surface
    Row(modifier = modifier
        .horizontalScroll(scrollState)
        .offset(x = -24.dp) // hide round corners to the left
        .background(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.shapes.extraLarge,
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
        if (activeCategory is Category.Region) { // || category is Category.Colors) {
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
    activeCategory: Category,
    regionColor: Color,
    isToolEnabled: (Tool) -> Boolean,
    isToolAlternativeEnabled: (Tool) -> Boolean,
    selectTool: (Tool) -> Unit,
    getColorsByMostUsed: () -> List<Color>,
    hidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // shown on the top of the bottom toolbar
    // scrollable lazy row, w = wrap content
    // can be shown or hidden with a collapse button at the end
    require(activeCategory.tools.size > 1)
    val isCompact = MaterialTheme.adaptiveSizing.isCompact
    val toolModifier =
        if (isCompact) Modifier.padding(4.dp).size(30.dp)
        else Modifier
    // scrollable row + highlight selected tool
    val scrollState = rememberScrollState()
    Column(
        modifier
            .padding(start = 8.dp)
            .verticalScroll(scrollState)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                MaterialTheme.shapes.extraLarge,
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
        if (activeCategory is Category.Region) {
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

/**
 * All-included [tool]-type multiplexer
 * @param[regionColor] only used for Palette color
 * @param[alternative] only used for [ITool.TernaryToggle] in [ThreeIconButton], e.g.
 * the chessboard toggle
 */
@Composable
fun ToolButton(
    tool: Tool,
    enabled: Boolean,
    alternative: Boolean = false,
    regionColor: Color,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier.padding(4.dp),
    onClick: (Tool) -> Unit,
) {
    val icon = painterResource(tool.icon)
    val name = stringResource(tool.name)
    val description = when (tool) {
        is ITool.TernaryToggle ->
            if (!enabled)
                stringResource(tool.disabledDescription)
            else if (alternative)
                stringResource(tool.alternativeDescription)
            else
                stringResource(tool.description)
        is ITool.BinaryToggle ->
            if (enabled)
                stringResource(tool.description)
            else
                stringResource(tool.disabledDescription)
        else -> stringResource(tool.description)
    }
    val callback = { onClick(tool) }
    WithTooltip(description) {
        when (tool) {
            Tool.Palette -> {
                PaletteButton(regionColor, modifier, callback)
            }
            is Tool.AppliedColor -> {
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
            is ITool.TernaryToggle -> {
                ThreeIconButton(
                    iconPainter = icon,
                    alternativeIconPainter = painterResource(tool.alternativeIcon),
                    disabledIconPainter = painterResource(tool.disabledIcon),
                    contentDescription = name,
                    enabled = enabled,
                    alternative = alternative,
                    modifier = modifier,
                    contentColor = tint,
                    onClick = callback
                )
            }
            is ITool.InstantAction -> {
                SimpleButton(
                    iconPainter = icon,
                    contentDescription = name,
                    modifier = modifier,
                    contentColor = tint,
                    onClick = callback
                )
            }
            is ITool.BinaryToggle -> {
                if (tool.disabledIcon == null) {
                    OnOffButton(
                        iconPainter = icon,
                        contentDescription = name,
                        isOn = enabled,
                        modifier = modifier,
                        iconModifier = modifier,
                        contentColor = tint,
                        onClick = callback
                    )
                } else {
                    TwoIconButton(
                        iconPainter = icon,
                        disabledIconPainter = painterResource(tool.disabledIcon!!),
                        contentDescription = name,
                        enabled = enabled,
                        modifier = modifier,
                        iconModifier = modifier,
                        contentColor = tint,
                        onClick = callback
                    )
                }
            }
//            else -> never("$tool")
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
            painterResource(Tool.Palette.icon),
            contentDescription = stringResource(Tool.Palette.name),
            modifier = modifier,
        )
    }
}

@Composable
fun AppliedColorButton(
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (Tool.AppliedColor) -> Unit,
) {
    val tool = Tool.AppliedColor(color)
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