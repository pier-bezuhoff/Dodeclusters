package ui.editor

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
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
import dodeclusters.composeapp.generated.resources.new_blank_name
import dodeclusters.composeapp.generated.resources.new_document
import dodeclusters.composeapp.generated.resources.ok
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.save_name
import dodeclusters.composeapp.generated.resources.set_selection_as_tool_arg_prompt
import dodeclusters.composeapp.generated.resources.three_dots_in_angle_brackets
import dodeclusters.composeapp.generated.resources.tool_arg_input_prompt
import dodeclusters.composeapp.generated.resources.tool_arg_parameter_adjustment_prompt
import domain.PartialArgList
import domain.io.DdcRepository
import domain.io.LookupData
import domain.io.OpenFileButton
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
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
import ui.isCompact
import ui.isLandscape
import ui.theme.DodeclustersColors
import ui.theme.extendedColorScheme
import ui.tools.Category
import ui.tools.ITool
import ui.tools.Tool
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun EditorScreen(
    sampleName: String? = null,
    ddcContent: String? = null,
    keyboardActions: SharedFlow<KeyboardAction>? = null,
    lifecycleEvents: SharedFlow<LifecycleEvent>? = null,
) {
    val windowSizeClass = calculateWindowSizeClass()
    val isLandscape = windowSizeClass.isLandscape
    val compactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    val compact = windowSizeClass.isCompact
    val ddcRepository = DdcRepository
    val coroutineScope = rememberCoroutineScope()
    val dialogActions = keyboardActions?.mapNotNull {
        when (it) {
            KeyboardAction.CANCEL -> DialogAction.DISMISS
            KeyboardAction.CONFIRM -> DialogAction.CONFIRM
            else -> null
        }
    }?.shareIn(coroutineScope, SharingStarted.Eagerly, replay = 0)
    val viewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.Factory
    )
    val snackbarHostState = remember { SnackbarHostState() } // hangs windows/chrome
    viewModel.setEpsilon(LocalDensity.current)
    Scaffold(
        // ig this may only be useful on android with kbd lol
        modifier = if (keyboardActions == null)
            Modifier.handleKeyboardActions(viewModel::processKeyboardAction)
        else Modifier
        ,
        snackbarHost = { SnackbarHost(snackbarHostState) { data ->
            Snackbar(data,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } },
        floatingActionButton = {
            if (!isLandscape && viewModel.showUI) {
                // MAYBE: only inline with any WindowSizeClass is Expanded (i.e. non-mobile)
                val category = Category.Create
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
        Surface {
            Box(Modifier
                .drawBehind {
                    viewModel.backgroundColor?.let { backgroundColor ->
                        drawRect(backgroundColor, size = size)
                    }
                }
            ) {
                EditorCanvas(viewModel)
                if (viewModel.showUI) {
                    ToolDescription(
                        tool = viewModel.toolbarState.activeTool,
                        toolIsEnabled = viewModel.toolPredicate(viewModel.toolbarState.activeTool),
                        partialArgList = viewModel.partialArgList,
                        isLandscape = isLandscape,
                        compact = compact,
                        showSelectionAsArgPrompt = viewModel.showPromptToSetActiveSelectionAsToolArg,
                        setSelectionAsArg = viewModel::setActiveSelectionAsToolArg,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    EditorTopBar(
                        compact = compact,
                        undoIsEnabled = viewModel.undoIsEnabled.value,
                        redoIsEnabled = viewModel.redoIsEnabled.value,
                        showSaveOptionsDialog = { viewModel.toolAction(Tool.SaveCluster) },
                        openNewBlank = viewModel::openNewBlankConstellation,
                        loadFromYaml = { content ->
                            content?.let {
                                viewModel.loadDdc(content)
                            }
                        },
                        undo = viewModel::undo,
                        redo = viewModel::redo,
                        modifier = Modifier.align(Alignment.TopEnd),
                        openFileRequests = viewModel.openFileRequests,
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
    when (viewModel.openedDialog) {
        DialogType.REGION_COLOR_PICKER -> {
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
        DialogType.CIRCLE_COLOR_PICKER -> {
            val initialColor = viewModel.getMostCommonCircleColorInSelection()
                ?: if (viewModel.selection.all { viewModel.objects[it] is ImaginaryCircle })
                    DodeclustersColors.fadedRed.copy(alpha = 1f) // imaginary circle
                else
                    MaterialTheme.extendedColorScheme.highAccentColor // free real circle
            ColorPickerDialog(
                parameters = viewModel.colorPickerParameters.copy(
                    currentColor = initialColor,
                    usedColors = viewModel.getColorsByMostUsed(),
                ),
                onCancel = viewModel::dismissCircleColorPicker,
                onConfirm = viewModel::concludeCircleColorPicker,
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
                onCancel = viewModel::dismissBackgroundColorPicker,
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
                saveAsYaml = viewModel::saveAsYaml,
                exportAsSvg = viewModel::exportAsSvg,
                onCancel = viewModel::closeDialog,
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
                },
                dialogActions = dialogActions,
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
                previousLabel = viewModel.selection
                    .firstNotNullOfOrNull { viewModel.objectLabels[it] }
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
    LaunchedEffect(ddcContent, sampleName, ddcRepository) {
        viewModel.viewModelScope.launch {
            viewModel.restoreFromDisk()
            if (ddcContent != null) {
                println("loading external ddc...")
                viewModel.loadDdc(ddcContent)
            } else if (sampleName != null) {
                val content = ddcRepository.loadSampleClusterYaml(sampleName)
                if (content != null) {
                    viewModel.loadDdc(content)
                }
            }
        }
    }
    LaunchedEffect(keyboardActions) {
        keyboardActions?.let {
            keyboardActions.collect { action ->
                viewModel.processKeyboardAction(action)
            }
        }
    }
    LaunchedEffect(lifecycleEvents) {
        lifecycleEvents?.let {
            // NOTE: technically it's better to call .flowWithLifecycle before .collect
            //  specifically on Android
            lifecycleEvents.collect { action ->
                when (action) {
                    LifecycleEvent.SaveUIState -> {
                        viewModel.cacheState()
                    }
                }
            }
        }
    }
    // NOTE: using getString(resource) here hangs windows/chrome for some reason
    //  ticket: https://youtrack.jetbrains.com/issue/CMP-6930/Using-getString-method-causing-JsException
    val snackbarMessageStrings = SnackbarMessage.entries.associateWith {
        stringResource(it.stringResource)
    }
    LaunchedEffect(snackbarHostState, snackbarMessageStrings) {
        viewModel.snackbarMessages.collectLatest { (message, postfix) ->
            // with this setup string interpolation with args is not possible
            val s = snackbarMessageStrings[message] + postfix
            snackbarHostState.showSnackbar(s, duration = message.duration)
//            // MAYBE: move on-selection action prompt here instead
        }
    }
    preloadIcons()
}

/** Loads all tool icons and caches them.
 * Otherwise icons only start being loaded when the corresponding category panel is open,
 * which is noticeable & jarring */
@Composable
fun preloadIcons() {
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
                Tool.PickCircleColor,
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
fun ToolDescription(
    tool: Tool,
    toolIsEnabled: Boolean,
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
        Crossfade(Pair(tool, toolIsEnabled)) { (currentTool, currentToolIsEnabled) ->
            val description = when (currentTool) {
                is ITool.BinaryToggle ->
                    if (currentToolIsEnabled)
                        stringResource(currentTool.description)
                    else
                        stringResource(currentTool.disabledDescription)
                else -> stringResource(currentTool.description)
            }
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
        val inputPrompt = stringResource(Res.string.tool_arg_input_prompt)
        val argDescriptions = (tool as? Tool.MultiArg)?.let {
            stringArrayResource(it.argDescriptions)
        }
        val confirmParametersPrompt = stringResource(Res.string.tool_arg_parameter_adjustment_prompt)
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
        AnimatedContent(Triple(tool, number, showSelectionAsArgPrompt)) { (currentTool, currentNumber, currentShowPrompt) ->
            if (currentTool is Tool.MultiArg &&
                currentNumber != null &&
                argDescriptions != null &&
                argDescriptions.size > currentNumber
            ) {
                val argDescription =
                    if (currentNumber == -1) null
                else
                    argDescriptions[currentNumber]
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
                        // NOTE: this functionality is non-obvious
                        Text(
                            "$selectionAsArgPrompt: $argDescription?",
                            Modifier.padding(4.dp, 4.dp),
                            textDecoration = TextDecoration.Underline,
                            style = textStyle,
                        )
                        Icon(
                            painterResource(Res.drawable.confirm),
                            stringResource(Res.string.ok),
                            Modifier.padding(start = 8.dp)
                        )
                    }
                } else if (!compact) {
                    Text(
                        if (currentNumber == -1) confirmParametersPrompt else "$inputPrompt: $argDescription",
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
fun EditorTopBar(
    compact: Boolean,
    undoIsEnabled: Boolean,
    redoIsEnabled: Boolean,
//    saveAsYaml: (name: String) -> String,
//    exportAsSvg: (name: String) -> String,
//    exportAsPng: suspend () -> Result<ImageBitmap>,
    openNewBlank: () -> Unit,
    showSaveOptionsDialog: () -> Unit,
    loadFromYaml: (content: String?) -> Unit,
    undo: () -> Unit,
    redo: () -> Unit,
    modifier: Modifier = Modifier,
    openFileRequests: SharedFlow<Unit>? = null,
) {
    val iconModifier =
        if (compact) Modifier.padding(4.dp).size(30.dp)
        else Modifier.padding(8.dp, 4.dp).size(40.dp)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val toolbarHeight = if (compact) 48.dp else 64.dp
    // bad in portrait, fine in landscape
//    SimpleToolButtonWithTooltip(
//        Tool.ToggleMenu,
//        Modifier
//            .offset(y = 8.dp)
//        ,
//        iconModifier = iconModifier,
//        contentColor = MaterialTheme.colorScheme.secondary,
//        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
//    ) {
//        println("hi")
//    }
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
            WithTooltip(stringResource(Res.string.new_blank_name)) {
                SimpleButton(
                    painterResource(Res.drawable.new_document),
                    stringResource(Res.string.new_blank_name),
                    onClick = openNewBlank,
                )
            }
            WithTooltip(stringResource(Res.string.save_name)) {
                SimpleButton(
                    painterResource(Tool.SaveCluster.icon),
                    stringResource(Tool.SaveCluster.name),
                    modifier = iconModifier,
                    iconModifier = iconModifier,
                    onClick = showSaveOptionsDialog
                )
            }
            WithTooltip(stringResource(Tool.OpenFile.description)) {
                OpenFileButton(
                    painterResource(Tool.OpenFile.icon),
                    stringResource(Tool.OpenFile.name),
                    LookupData.YAML,
                    modifier = iconModifier,
                    openRequests = openFileRequests,
                    onOpen = loadFromYaml,
                )
            }
            WithTooltip(stringResource(Tool.Undo.description)) {
                DisableableButton(
                    painterResource(Tool.Undo.icon),
                    stringResource(Tool.Undo.name),
                    undoIsEnabled,
                    iconModifier,
                    onClick = undo
                )
            }
            WithTooltip(stringResource(Tool.Redo.description)) {
                DisableableButton(
                    painterResource(Tool.Redo.icon),
                    stringResource(Tool.Redo.name),
                    redoIsEnabled,
                    iconModifier,
                    onClick = redo
                )
            }
        }
    }
}

@Composable
private fun ToolbarPortrait(viewModel: EditorViewModel, compact: Boolean, modifier: Modifier = Modifier) {
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
private fun ToolbarLandscape(viewModel: EditorViewModel, compact: Boolean, modifier: Modifier = Modifier) {
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
    viewModel: EditorViewModel,
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
            CategoryButton(viewModel, Category.Drag, compact = compact)
            CategoryButton(viewModel, Category.Multiselect, compact = compact)
            CategoryButton(viewModel, Category.Region, compact = compact)
            Spacer(Modifier.size(12.dp, 0.dp))
            VerticalDivider(Modifier
                .fillMaxHeight(0.7f)
                .align(Alignment.CenterVertically)
            )
            CategoryButton(viewModel, Category.Visibility, compact = compact)
            CategoryButton(viewModel, Category.Colors, compact = compact)
            CategoryButton(viewModel, Category.Transform, compact = compact)
        }
    }
}

@Composable
private fun LeftToolbar(
    viewModel: EditorViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val toolbarSize =
        if (compact) 48.dp
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
            if (compact) PaddingValues(vertical = 6.dp)
            else PaddingValues(top = 12.dp) // every CategoryButton already has 12dp high spacer on the top
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (compact)
                Spacer(Modifier.height(6.dp))
            CategoryButton(viewModel, Category.Drag, compact = compact)
            CategoryButton(viewModel, Category.Multiselect, compact = compact)
            CategoryButton(viewModel, Category.Region, compact = compact)
            HorizontalDivider(Modifier
                .padding(dividerPaddings)
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally)
            )
            CategoryButton(viewModel, Category.Visibility, compact = compact)
            CategoryButton(viewModel, Category.Colors, compact = compact)
            CategoryButton(viewModel, Category.Transform, compact = compact)
            HorizontalDivider(Modifier
                .padding(dividerPaddings)
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally)
            )
            CategoryButton(
                viewModel, Category.Create, compact = compact,
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
    viewModel: EditorViewModel,
    category: Category,
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
    activeCategory: Category,
    compact: Boolean,
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
    val toolModifier =
        if (compact) Modifier.padding(4.dp).size(30.dp)
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
    compact: Boolean,
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
                    name = name,
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
                    name = name,
                    modifier = modifier,
                    contentColor = tint,
                    onClick = callback
                )
            }
            is ITool.BinaryToggle -> {
                if (tool.disabledIcon == null) {
                    OnOffButton(
                        iconPainter = icon,
                        name = name,
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
                        name = name,
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