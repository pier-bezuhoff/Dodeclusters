@file:OptIn(FlowPreview::class)

package ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import core.geometry.Circle
import core.geometry.ImaginaryCircle
import core.geometry.Line
import core.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.arc_path_number_label
import dodeclusters.composeapp.generated.resources.circle_number_label
import dodeclusters.composeapp.generated.resources.close
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.expand
import dodeclusters.composeapp.generated.resources.imaginary_circle_number_label
import dodeclusters.composeapp.generated.resources.label_input_placeholder
import dodeclusters.composeapp.generated.resources.line_number_label
import dodeclusters.composeapp.generated.resources.line_thickness_input_placeholder
import dodeclusters.composeapp.generated.resources.ok
import dodeclusters.composeapp.generated.resources.point_number_label
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.selection_choices_title
import dodeclusters.composeapp.generated.resources.steps_slider_name
import dodeclusters.composeapp.generated.resources.three_dots_in_angle_brackets
import domain.angleDeg
import domain.expressions.BiInversionParameters
import domain.expressions.InterpolationParameters
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.RotationParameters
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.FloatTextField
import ui.OkButton
import ui.OnOffButton
import ui.SimpleFilledButton
import ui.SimpleToolButtonWithTooltip
import ui.StringTextFieldWithConfirmOnEnter
import ui.TwoIconButtonWithTooltip
import ui.VerticalSlider
import ui.WithTooltip
import ui.editor.dialogs.DefaultBiInversionParameters
import ui.editor.dialogs.DefaultInterpolationParameters
import ui.editor.dialogs.DefaultLoxodromicMotionParameters
import ui.editor.dialogs.DefaultRotationParameters
import ui.theme.ColorTheme
import ui.theme.DodeclustersColors
import ui.theme.DodeclustersTheme
import ui.theme.adaptiveSizing
import ui.theme.adaptiveTypography
import ui.theme.extendedColorScheme
import ui.tools.Tool
import kotlin.math.abs
import kotlin.math.acosh
import kotlin.math.asinh
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.time.Duration.Companion.milliseconds

// MAYBE: expanded mode with icon+label

private val buttonModifier = Modifier
    .padding(8.dp)
    .size(36.dp)

// 16.67 ms is 60 FPS
private val UPDATE_PARAMETERS_DEBOUNCE_DELTA = 15.milliseconds

@Composable
fun BoxScope.SelectionContextActions(
    concretePositions: ConcreteOnScreenPositions,
    scaleSliderPercentage: Float,
    rotationHandleAngle: Float,
    borderColor: Color,
    showAdjustExprButton: Boolean,
    showOrientationToggle: Boolean,
    isLocked: Boolean,
    toolAction: (Tool) -> Unit,
    toolPredicate: (Tool) -> Boolean,
    onScale: (newScaleSliderPercentage: Float) -> Unit,
    onScaleFinished: () -> Unit,
    onRotate: (newRotationAngle: Float) -> Unit,
    onRotateStarted: (center: Offset) -> Unit,
    onRotateFinished: () -> Unit,
) {
    // TODO: convert to arc-paths
    // rotate handle
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.secondary,
        activeTrackColor = MaterialTheme.colorScheme.secondary,
        activeTickColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTickColor = MaterialTheme.colorScheme.secondary,
    )
    val rotationHandleStripeColor = MaterialTheme.colorScheme.onSecondaryContainer
    // scale slider mid column is too far from the right
    with (concretePositions) {
        /** position of the grabbed rotation handle if it followed the cursor */
        var virtualRotationHandlePosition by remember { mutableStateOf(Offset.Unspecified) }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = (-67).dp,
                    y = with (density) {
                        positions.top.toDp() - halfSize
                    }
                )
            ,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SimpleToolButtonWithTooltip(Tool.Expand,
                contentColor = MaterialTheme.colorScheme.secondary,
                onClick = toolAction
            )
            VerticalSlider(
                scaleSliderPercentage,
                onScale,
                Modifier
                    .height(
                        with (density) {
                            (0.2f * size.height).toDp()
                        }
                    )
                ,
                trackModifier = Modifier.height(8.dp), // height is transposed into width
                thumbModifier = Modifier.height(24.dp),
                onValueChangeFinished = onScaleFinished,
                colors = sliderColors,
            )
            SimpleToolButtonWithTooltip(Tool.Shrink,
                contentColor = MaterialTheme.colorScheme.secondary,
                onClick = toolAction
            )
        }
        Box(
            rotationHandleModifier(rotationHandleAngle)
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .drawWithCache {
                    val mask = Path().apply {
                        addOval(Rect(Offset.Zero, size).deflate(0.1f*size.width))
                    }
                    onDrawWithContent {
                        this.drawContent()
                        rotate(-45f + positions.rotationHandle0Angle + rotationHandleAngle) {
                            clipPath(mask) {
                                val step = size.minDimension/4f
                                for (i in 1..7) {
                                    drawLine(
                                        rotationHandleStripeColor,
                                        Offset(0f, i*step),
                                        Offset(i*step, 0f),
                                        strokeWidth = 2f,
                                    )
                                }
                            }
                        }
                    }
                }
                .pointerHoverIcon(PointerIcon.Hand)
                .draggable2D(
                    rememberDraggable2DState { delta ->
                        virtualRotationHandlePosition += delta
                        val newAngle = positions.center
                            .angleDeg(positions.rotationHandle0, virtualRotationHandlePosition)
                        onRotate(newAngle)
                    },
                    onDragStarted = {
                        virtualRotationHandlePosition = positions.rotationHandle0
                        onRotateStarted(positions.center)
                    },
                    onDragStopped = {
                        onRotateFinished()
                    }
                )
        ) {}
    }
    Surface(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 4.dp)
        ,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
        ) {
            if (showAdjustExprButton) {
                SimpleToolButtonWithTooltip(Tool.AdjustExpr, buttonModifier, onClick = toolAction)
            }
            SimpleToolButtonWithTooltip(Tool.BorderColor,
                buttonModifier,
                contentColor = borderColor,
                onClick = toolAction
            )
//            SimpleToolButtonWithTooltip(Tool.SetLineThickness,
//                buttonModifier,
//                onClick = toolAction,
//            )
            // MAYBE: fill color here too
            TwoIconButtonWithTooltip(
                painterResource(Tool.MarkAsPhantoms.icon),
                painterResource(Tool.MarkAsPhantoms.disabledIcon),
                tooltip = stringResource(Tool.MarkAsPhantoms.description),
                disabledTooltip = stringResource(Tool.MarkAsPhantoms.disabledDescription),
                contentDescription = stringResource(Tool.MarkAsPhantoms.name),
                enabled = toolPredicate(Tool.MarkAsPhantoms),
                modifier = buttonModifier,
                onClick = { toolAction(Tool.MarkAsPhantoms) }
            )
            if (showOrientationToggle) {
                SimpleToolButtonWithTooltip(Tool.SwapDirection, buttonModifier, onClick = toolAction)
            }
            if (isLocked) {
                SimpleToolButtonWithTooltip(Tool.Detach, buttonModifier, onClick = toolAction)
            }
            SimpleToolButtonWithTooltip(Tool.Duplicate, buttonModifier, onClick = toolAction)
            SimpleToolButtonWithTooltip(Tool.Delete, buttonModifier, onClick = toolAction)
        }
    }
}

@Composable
private fun ContextActionsWrapper(
    content: @Composable (BoxScope.(ConcreteOnScreenPositions) -> Unit),
) {
    var intSize by remember { mutableStateOf(
        IntSize(1000, 2000)
    ) }
    val density = LocalDensity.current
    DodeclustersTheme(ColorTheme.DARK) {
        Surface {
            Box {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { newSize ->
                            intSize = newSize
                        }
                ) {}
                content(ConcreteOnScreenPositions(intSize.toSize(), density))
            }
        }
    }
}

@Preview
@Composable
private fun SelectionContextActionsPreview() {
    ContextActionsWrapper { positions ->
        SelectionContextActions(
            positions,
            50f,
            rotationHandleAngle = 0f,
            borderColor = Color.Blue,
            showAdjustExprButton = true,
            showOrientationToggle = true,
            isLocked = false,
            toolAction = {},
            toolPredicate = { true },
            onScale = {},
            onScaleFinished = {},
            onRotate = {},
            onRotateStarted = {},
            onRotateFinished = {},
        )
    }
}

// only points
@Composable
fun BoxScope.PointContextActions(
    pointColor: Color,
    showAdjustExprButton: Boolean,
    isLocked: Boolean,
    label: String?,
    toolAction: (Tool) -> Unit,
    toolPredicate: (Tool) -> Boolean,
    setLabel: (String) -> Unit,
    dismissLabelInput: () -> Unit,
) {
    val labelInputIsActive = toolPredicate(Tool.SetLabel)
    Surface(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 8.dp)
        ,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
        ) {
            if (showAdjustExprButton) {
                SimpleToolButtonWithTooltip(Tool.AdjustExpr, buttonModifier, onClick = toolAction)
            }
            SimpleToolButtonWithTooltip(Tool.PointColor,
                buttonModifier,
                contentColor = pointColor,
                onClick = toolAction
            )
            Row(
                Modifier.background(color =
                    if (labelInputIsActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    else Color.Transparent,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box { // popup container
                    if (labelInputIsActive) {
                        LabelInputPopup(
                            previousLabel = label,
                            setLabel = setLabel,
                            dismiss = dismissLabelInput,
                        )
                    }
                }
                SimpleToolButtonWithTooltip(Tool.SetLabel,
                    buttonModifier,
//                    containerColor =
//                        if (labelInputIsActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
//                        else Color.Unspecified,
                    onClick = toolAction
                )
            }
            TwoIconButtonWithTooltip(
                painterResource(Tool.MarkAsPhantoms.icon),
                painterResource(Tool.MarkAsPhantoms.disabledIcon),
                tooltip = stringResource(Tool.MarkAsPhantoms.description),
                disabledTooltip = stringResource(Tool.MarkAsPhantoms.disabledDescription),
                contentDescription = stringResource(Tool.MarkAsPhantoms.name),
                enabled = toolPredicate(Tool.MarkAsPhantoms),
                modifier = buttonModifier,
                onClick = { toolAction(Tool.MarkAsPhantoms) }
            )
            if (toolPredicate(Tool.MovePointToInfinity)) {
                SimpleToolButtonWithTooltip(Tool.MovePointToInfinity, buttonModifier, onClick = toolAction)
            }
            if (isLocked) {
                SimpleToolButtonWithTooltip(Tool.Detach, buttonModifier, onClick = toolAction)
            }
            SimpleToolButtonWithTooltip(Tool.Delete, buttonModifier, onClick = toolAction)
        }
    }
}

@Composable
private fun BoxScope.LabelInputPopup(
    previousLabel: String?,
    setLabel: (String) -> Unit,
    dismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(previousLabel ?: "") }
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(
                x = anchorBounds.right - popupContentSize.width,
                y = anchorBounds.top,
            )
        },
        onDismissRequest = dismiss,
        properties = PopupProperties(
            focusable = true,
        ),
    ) {
        Surface(
            Modifier.padding(end = 8.dp),
            shape = MaterialTheme.shapes.large,
            contentColor = MaterialTheme.colorScheme.secondary,
            tonalElevation = 4.dp,
        ) {
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StringTextFieldWithConfirmOnEnter(
                    value = label,
                    onValueChange = {
                        label = it
                        setLabel(it)
                    },
                    onConfirm = dismiss,
                    color = MaterialTheme.colorScheme.secondary,
                    placeholderStringResource = Res.string.label_input_placeholder,
                    captureFocus = true,
                    confirmOnEnter = true,
                )
                OkButton(
                    noText = true,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = dismiss,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PointContextActionsPreview() {
    ContextActionsWrapper {
        PointContextActions(
            pointColor = Color.Blue,
            showAdjustExprButton = true,
            isLocked = false,
            label = null,
            toolAction = {},
            toolPredicate = { false },
            setLabel = {},
            dismissLabelInput = {},
        )
    }
}

@Composable
fun BoxScope.ArcPathContextActions(
    someAreClosed: Boolean,
    showAdjustExprButton: Boolean,
    isLocked: Boolean,
    mostCommonBorderColor: Color?,
    mostCommonFillColor: Color?,
    lineThickness: Float?,
    toolAction: (Tool) -> Unit,
    toolPredicate: (Tool) -> Boolean,
    setLineThickness: (Float) -> Unit,
    dismissLineThicknessInput: () -> Unit,
    // grabbed midpoint <- submode
) {
    // scale/rotate handles
    // swap direction?
    // layer up/down
    // style = path effect
    val defaultBorderColor = MaterialTheme.extendedColorScheme.highAccentColor
    val defaultFillColor = MaterialTheme.colorScheme.surface.copy(alpha = 1.0f)
    val lineThicknessInputIsActive = toolPredicate(Tool.SetLineThickness)
    Surface(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 8.dp)
        ,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
        ) {
            if (showAdjustExprButton) {
                SimpleToolButtonWithTooltip(Tool.AdjustExpr, buttonModifier, onClick = toolAction)
            }
            val borderColor = mostCommonBorderColor ?: defaultBorderColor
            SimpleToolButtonWithTooltip(Tool.BorderColor, buttonModifier,
                contentColor = borderColor,
                onClick = toolAction
            )
            val fillColor = mostCommonFillColor ?: defaultFillColor
            if (someAreClosed) {
                SimpleToolButtonWithTooltip(Tool.FillColor,
                    buttonModifier,
                    contentColor = fillColor,
                    onClick = toolAction
                )
            }
            Row(
                Modifier.background(color =
                    if (lineThicknessInputIsActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    else Color.Transparent,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box { // popup container
                    if (lineThicknessInputIsActive) {
                        LineThicknessInputPopup(
                            previousLineThickness = lineThickness,
                            setLineThickness = setLineThickness,
                            dismiss = dismissLineThicknessInput,
                        )
                    }
                }
                SimpleToolButtonWithTooltip(Tool.SetLineThickness,
                    buttonModifier,
                    onClick = toolAction,
                )
            }
            // close/cut loop button
            if (isLocked) {
                SimpleToolButtonWithTooltip(Tool.Detach, buttonModifier, onClick = toolAction)
            }
            SimpleToolButtonWithTooltip(Tool.Duplicate, buttonModifier, onClick = toolAction)
            SimpleToolButtonWithTooltip(Tool.Delete, buttonModifier, onClick = toolAction)
        }
    }
}

private const val MIN_THICKNESS = 1f
private const val MAX_THICKNESS = 50f
private const val THICKNESS_DISCRETIZATION = 1f
private val THICKNESS_RANGE = MIN_THICKNESS .. MAX_THICKNESS
private val N_THICKNESS_DISCRETIZATION_STEPS =
    ceil((MAX_THICKNESS - MIN_THICKNESS)/THICKNESS_DISCRETIZATION).toInt()

@Composable
private fun BoxScope.LineThicknessInputPopup(
    previousLineThickness: Float?,
    setLineThickness: (Float) -> Unit,
    dismiss: () -> Unit,
) {
    var thickness by remember { mutableStateOf(previousLineThickness ?: 1f) }
    // separate var so that tf doesnt re-format user input
    var textFieldThickness by remember { mutableStateOf(previousLineThickness ?: 1f) }
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.secondary,
        activeTrackColor = MaterialTheme.colorScheme.secondary,
        activeTickColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTickColor = MaterialTheme.colorScheme.secondary,
    )
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(
                x = anchorBounds.right - popupContentSize.width,
                y = anchorBounds.top,
            )
        },
        onDismissRequest = dismiss,
        properties = PopupProperties(
            focusable = true,
        ),
    ) {
        Surface(
            Modifier.padding(end = 8.dp),
            shape = MaterialTheme.shapes.large,
            contentColor = MaterialTheme.colorScheme.secondary,
            tonalElevation = 4.dp,
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Slider(
                    value = thickness,
                    onValueChange = {
                        thickness = it
                        textFieldThickness = it
                        setLineThickness(it)
                    },
                    modifier = Modifier.widthIn(max = 300.dp),
                    valueRange = THICKNESS_RANGE,
                    steps = N_THICKNESS_DISCRETIZATION_STEPS - 1,
                    colors = sliderColors,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatTextField(
                        value = textFieldThickness,
                        onValueChange = {
                            thickness = it
                            setLineThickness(it)
                        },
                        validateValue = { it >= 0f },
                        onConfirm = dismiss,
//                        modifier = Modifier.widthIn(max = 200.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        placeholderStringResource = Res.string.line_thickness_input_placeholder,
                        captureFocus = true,
                        confirmOnEnter = true,
                        nFractionalDigits = 3,
                    )
                    OkButton(
                        noText = true,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = dismiss,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ArcPathContextActionsPreview() {
    ContextActionsWrapper {
        ArcPathContextActions(
            someAreClosed = true,
            showAdjustExprButton = true,
            isLocked = false,
            mostCommonBorderColor = Color.Gray,
            mostCommonFillColor = Color.Yellow,
            lineThickness = null,
            toolAction = {},
            toolPredicate = { false },
            setLineThickness = {},
            dismissLineThicknessInput = {},
        )
    }
}

@Composable
fun BoxScope.SelectionChoices(
    choices: List<Submode.SelectionChoices.Choice>,
    selectChoice: (indexAmongChoices: Int?) -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.Center)
        ,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 12.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
            ,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(24.dp)) // manually balancing close-icon to center the title
                Text(stringResource(Res.string.selection_choices_title), style = MaterialTheme.adaptiveTypography.body)
                IconButton(
                    onClick = { selectChoice(null) },
                ) {
                    Icon(painterResource(Res.drawable.close), "close", Modifier.size(18.dp))
                }
            }
            choices.forEachIndexed { i, choice ->
                val label = when (choice.objectOrArcPath) {
                    is Circle -> stringResource(Res.string.circle_number_label, choice.index)
                    is Line -> stringResource(Res.string.line_number_label, choice.index)
                    is ImaginaryCircle -> stringResource(Res.string.imaginary_circle_number_label, choice.index)
                    is Point -> stringResource(Res.string.point_number_label, choice.index)
                    null -> stringResource(Res.string.arc_path_number_label, choice.index)
                }
                TextButton(
                    onClick = { selectChoice(i) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border =
                        if (i == 0)
                            // salad green is the default selection color
                            BorderStroke(2.dp, DodeclustersColors.strongSalad)
//                                BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                        else null,
                ) {
                    Text(
                        text = label,
                        color = choice.borderColor ?: choice.fillColor ?: MaterialTheme.extendedColorScheme.highAccentColor,
                        style = MaterialTheme.adaptiveTypography.label,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SelectionChoicesPreview() {
    ContextActionsWrapper {
        SelectionChoices(
            choices = listOf(
                Submode.SelectionChoices.Choice(
                    0, Circle(0.0, 0.0, 1.0),
                    Color.Red, null
                ),
                Submode.SelectionChoices.Choice(
                    1, Line(1.0, 0.0, 1.0),
                    Color.Green, null
                ),
            ),
            selectChoice = {},
        )
    }
}

/**
 * @param[interpolateCircles] as opposed to interpolating points
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterpolationInterface(
    concretePositions: ConcreteOnScreenPositions,
    interpolateCircles: Boolean,
    circlesAreCoDirected: Boolean,
    defaults: DefaultInterpolationParameters,
    updateParameters: (InterpolationParameters) -> Unit,
    openDetailsDialog: () -> Unit,
    confirmParameters: () -> Unit,
) {
    val minCount = defaults.minCircleCount
    val maxCount = defaults.maxCircleCount
    val sliderState = remember { SliderState(
        value = defaults.nInterjacents.toFloat(),
        steps = maxCount - minCount - 1, // only counts intermediates
        valueRange = defaults.nInterjacentsRange
    ) }
    var interpolateInBetween by remember { mutableStateOf(defaults.inBetween) }
    val buttonShape = CircleShape
    val buttonBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.secondary,
        activeTrackColor = MaterialTheme.colorScheme.secondary,
        activeTickColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTickColor = MaterialTheme.colorScheme.secondary,
    )
    with (concretePositions) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            if (interpolateCircles) {
                Box(rightUnderVerticalSliderModifier) {
                    WithTooltip(
                        if (interpolateInBetween)
                            stringResource(Tool.InBetween.description)
                        else
                            stringResource(Tool.InBetween.disabledDescription)
                    ) {
                        OnOffButton(
                            painterResource(Tool.InBetween.icon),
                            stringResource(Tool.InBetween.name),
                            isOn = interpolateInBetween,
                            contentColor = MaterialTheme.colorScheme.secondary,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = buttonBackground,
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            interpolateInBetween = !interpolateInBetween
                            // triggers params upd => triggers VM.updParams
                        }
                    }
                }
            }
            SimpleToolButtonWithTooltip(Tool.DetailedAdjustment,
                Modifier.background(buttonBackground, buttonShape),
                positionModifier = halfBottomRightModifier,
                onClick = { openDetailsDialog() }
            )
            Icon(
                painterResource(Res.drawable.three_dots_in_angle_brackets),
                stringResource(Res.string.steps_slider_name),
                preHorizontalSliderModifier.padding(vertical = 12.dp),
            )
            Slider(
                sliderState,
                horizontalSliderModifier.width(horizontalSliderWidth),
                colors = sliderColors,
            )
            SimpleFilledButton(
                iconPainter = painterResource(Res.drawable.confirm),
                contentDescription = stringResource(Res.string.ok),
                modifier = bottomRightModifier,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = confirmParameters
            )
        }
    }
    val interpolationParametersFlow = remember(interpolateCircles, circlesAreCoDirected) {
        snapshotFlow {
            InterpolationParameters(
                nInterjacents = sliderState.value.roundToInt(),
                inBetween = interpolateInBetween,
                complementary =
                    if (interpolateCircles) {
                        if (circlesAreCoDirected)
                            !interpolateInBetween
                        else
                            interpolateInBetween
                    } else {
                        interpolateInBetween
                    }
            )
        }
            .distinctUntilChanged()
            .debounce(UPDATE_PARAMETERS_DEBOUNCE_DELTA)
    }
    LaunchedEffect(interpolationParametersFlow, updateParameters) {
        interpolationParametersFlow.collect { params ->
            updateParameters(params)
        }
    }
}

@Preview
@Composable
private fun InterpolationInterfacePreview() {
    ContextActionsWrapper { positions ->
        InterpolationInterface(
            concretePositions = positions,
            interpolateCircles = true,
            circlesAreCoDirected = true,
            defaults = DefaultInterpolationParameters(),
            updateParameters = {},
            openDetailsDialog = {},
            confirmParameters = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationInterface(
    concretePositions: ConcreteOnScreenPositions,
    defaults: DefaultRotationParameters,
    updateParameters: (RotationParameters) -> Unit,
    openDetailsDialog: () -> Unit,
    confirmParameters: () -> Unit,
) {
    var rotateClockwise by remember { mutableStateOf(false) }
    val angleSliderState = remember { SliderState(
        value = defaults.angle,
        steps = defaults.nAngleDiscretizationSteps - 1,
        valueRange = defaults.angleRange,
    ) }
    val stepsSliderState = remember { SliderState(
        value = defaults.nSteps.toFloat(),
        steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
        valueRange = defaults.stepsRange
    ) }
    val buttonShape = CircleShape
    val buttonBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.secondary,
        activeTrackColor = MaterialTheme.colorScheme.secondary,
        activeTickColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTickColor = MaterialTheme.colorScheme.secondary,
    )
    with (concretePositions) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                painterResource(Res.drawable.rotate_counterclockwise),
                "angle slider",
                topRightModifier.padding(start = 12.dp),
            )
            VerticalSlider(
                angleSliderState,
                verticalSliderModifier.height(verticalSliderHeight),
                colors = sliderColors,
            )
            ReverseDirectionToggle(
                isOn = rotateClockwise,
                positionModifier = rightUnderVerticalSliderModifier,
                containerColor = buttonBackground,
            ) {
                rotateClockwise = !rotateClockwise
                // triggers params upd => triggers VM.updParams
            }
            SimpleToolButtonWithTooltip(Tool.DetailedAdjustment,
                Modifier.background(buttonBackground, buttonShape),
                positionModifier = halfBottomRightModifier,
                onClick = { openDetailsDialog() }
            )
            Icon(
                painterResource(Res.drawable.three_dots_in_angle_brackets),
                stringResource(Res.string.steps_slider_name),
                preHorizontalSliderModifier.padding(vertical = 12.dp),
            )
            Slider(
                stepsSliderState,
                horizontalSliderModifier.width(horizontalSliderWidth),
                colors = sliderColors,
            )
            SimpleFilledButton(
                iconPainter = painterResource(Res.drawable.confirm),
                contentDescription = stringResource(Res.string.ok),
                modifier = bottomRightModifier,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = confirmParameters
            )
        }
    }
    val rotationParametersFlow = remember { snapshotFlow {
        RotationParameters(
            angle = round( // we round cuz all angles coming from the slider are supposed to be integers
                if (rotateClockwise)
                    360f - angleSliderState.value
                else angleSliderState.value
            ),
            nSteps = stepsSliderState.value.roundToInt(),
        )
    }
        .distinctUntilChanged()
        .debounce(UPDATE_PARAMETERS_DEBOUNCE_DELTA)
    }
    LaunchedEffect(rotationParametersFlow, updateParameters) {
        rotationParametersFlow.collect { params ->
            updateParameters(params)
        }
    }
}

@Preview
@Composable
private fun RotationInterfacePreview() {
    ContextActionsWrapper { positions ->
        RotationInterface(
            concretePositions = positions,
            defaults = DefaultRotationParameters(),
            updateParameters = {},
            openDetailsDialog = {},
            confirmParameters = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiInversionInterface(
    concretePositions: ConcreteOnScreenPositions,
    defaults: DefaultBiInversionParameters,
    updateParameters: (BiInversionParameters) -> Unit,
    openDetailsDialog: () -> Unit,
    confirmParameters: () -> Unit,
) {
    // equivalent to swapping the order of engines
    var negateSpeed by remember { mutableStateOf(false) }
    // we use sinh for nicer range
    // sinh(x) ~ x on [0; 1] and sinh(x) ~ exp(x)/2 on [1; +inf]
    // f(x) := k1 * sinh(k2 * x) such that
    // f(0) = 0; f(1/2) = 1; f(1) = max-speed
    val k2 = remember { 2.0 * acosh(defaults.maxSpeed / 2.0) }
    val k1 = remember { 1.0 / sinh(k2 / 2.0) }
    val minVisibleSpeedValue = remember { (asinh(defaults.minSpeed/k1)/k2).toFloat() }
    val maxVisibleSpeedValue = remember { (asinh(defaults.maxSpeed/k1)/k2).toFloat() }
    val speedSliderState = remember { SliderState(
        value = (asinh(abs(defaults.speed)/k1)/k2).toFloat(),
        valueRange = minVisibleSpeedValue .. maxVisibleSpeedValue
    ) }
    val stepsSliderState = remember { SliderState(
        value = defaults.nSteps.toFloat(),
        steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
        valueRange = defaults.stepsRange
    ) }
    val buttonShape = CircleShape
    val buttonBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.secondary,
        activeTrackColor = MaterialTheme.colorScheme.secondary,
        activeTickColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTickColor = MaterialTheme.colorScheme.secondary,
    )
    with (concretePositions) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                painterResource(Res.drawable.rotate_counterclockwise),
                "speed slider",
                topRightModifier
                    .padding(start = 12.dp)
            )
            VerticalSlider(
                speedSliderState,
                verticalSliderModifier
                    .height(verticalSliderHeight)
                ,
                colors = sliderColors,
            )
            ReverseDirectionToggle(
                isOn = negateSpeed,
                positionModifier = rightUnderVerticalSliderModifier,
                containerColor = buttonBackground,
            ) {
                negateSpeed = !negateSpeed
                // triggers params upd => triggers VM.updParams
            }
            SimpleToolButtonWithTooltip(Tool.DetailedAdjustment,
                Modifier.background(buttonBackground, buttonShape),
                positionModifier = halfBottomRightModifier,
                onClick = { openDetailsDialog() }
            )
            Icon(
                painterResource(Res.drawable.three_dots_in_angle_brackets),
                stringResource(Res.string.steps_slider_name),
                preHorizontalSliderModifier.padding(vertical = 12.dp),
            )
            Slider(
                stepsSliderState,
                horizontalSliderModifier.width(horizontalSliderWidth),
                colors = sliderColors,
            )
            SimpleFilledButton(
                painterResource(Res.drawable.confirm),
                stringResource(Res.string.ok),
                bottomRightModifier,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = confirmParameters
            )
        }
    }
    val biInversionParametersFlow = remember(defaults) {
        snapshotFlow {
            BiInversionParameters(
                speed = (if (negateSpeed) -1 else +1) * k1*sinh(k2*speedSliderState.value),
                nSteps = stepsSliderState.value.roundToInt(),
                reverseSecondEngine = defaults.reverseSecondEngine,
            )
        }
            .distinctUntilChanged()
            .debounce(UPDATE_PARAMETERS_DEBOUNCE_DELTA)
    }
    LaunchedEffect(biInversionParametersFlow, updateParameters) {
        biInversionParametersFlow.collect { params ->
            updateParameters(params)
        }
    }
}

@Preview
@Composable
private fun BiInversionInterfacePreview() {
    ContextActionsWrapper { positions ->
        BiInversionInterface(
            concretePositions = positions,
            defaults = DefaultBiInversionParameters(),
            updateParameters = {},
            openDetailsDialog = {},
            confirmParameters = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoxodromicMotionInterface(
    concretePositions: ConcreteOnScreenPositions,
    defaults: DefaultLoxodromicMotionParameters,
    updateParameters: (LoxodromicMotionParameters) -> Unit,
    updateBidirectionality: (forwardAndBackward: Boolean) -> Unit,
    openDetailsDialog: () -> Unit,
    confirmParameters: () -> Unit,
) {
    // equivalent to swapping the order of engines
    var reverseDirection by remember { mutableStateOf(false) }
    // we maintain state here cuz VM.defaults isn't state...
    var bidirectional by remember { mutableStateOf(defaults.bidirectional) }
    val angleSliderState = remember { SliderState(
        value = defaults.anglePerStep,
        valueRange = defaults.angleRange,
    ) }
    val dilationSliderState = remember { SliderState(
        value = defaults.dilationPerStep.toFloat(),
        valueRange = defaults.dilationRange,
    ) }
    val stepsSliderState = remember { SliderState(
        value = defaults.nTotalSteps.toFloat(),
        steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
        valueRange = defaults.stepsRange,
    ) }
    val buttonShape = CircleShape
    val buttonBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.secondary,
        activeTrackColor = MaterialTheme.colorScheme.secondary,
        activeTickColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondary,
        inactiveTickColor = MaterialTheme.colorScheme.secondary,
    )
    with (concretePositions) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                painterResource(Res.drawable.expand),
                "dilation slider",
                topMidModifier
                    .padding(start = 12.dp)
            )
            VerticalSlider(
                dilationSliderState,
                verticalSlider2Modifier
                    .height(verticalSliderHeight)
                ,
                colors = sliderColors,
            )
            Icon(
                painterResource(Res.drawable.rotate_counterclockwise),
                "angle slider",
                topRightModifier
                    .padding(start = 12.dp)
            )
            VerticalSlider(
                angleSliderState,
                verticalSliderModifier
                    .height(verticalSliderHeight)
                ,
                colors = sliderColors,
            )
            Box(midUnderVerticalSliderModifier) {
                WithTooltip(
                    if (bidirectional)
                        stringResource(Tool.BidirectionalSpiral.description)
                    else
                        stringResource(Tool.BidirectionalSpiral.disabledDescription)
                ) {
                    OnOffButton(
                        painterResource(Tool.BidirectionalSpiral.icon),
                        stringResource(Tool.BidirectionalSpiral.name),
                        isOn = bidirectional,
                        contentColor = MaterialTheme.colorScheme.secondary,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        containerColor = buttonBackground,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        bidirectional = !bidirectional
                        updateBidirectionality(bidirectional)
                    }
                }
            }
            ReverseDirectionToggle(
                isOn = reverseDirection,
                positionModifier = rightUnderVerticalSliderModifier,
                containerColor = buttonBackground,
            ) {
                reverseDirection = !reverseDirection
            }
            SimpleToolButtonWithTooltip(
                Tool.DetailedAdjustment,
                Modifier
                    .background(buttonBackground, buttonShape)
                ,
                positionModifier = halfBottomRightModifier,
                onClick = { openDetailsDialog() }
            )
            Icon(
                painterResource(Res.drawable.three_dots_in_angle_brackets),
                stringResource(Res.string.steps_slider_name),
                preHorizontalSliderModifier
                    .padding(vertical = 12.dp)
            )
            Slider(
                stepsSliderState,
                horizontalSliderModifier
                    .width(horizontalSliderWidth)
                ,
                colors = sliderColors
            )
            SimpleFilledButton(
                iconPainter = painterResource(Res.drawable.confirm),
                contentDescription = stringResource(Res.string.ok),
                modifier = bottomRightModifier,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = confirmParameters
            )
        }
    }
    val loxodromicMotionParametersFlow = remember {
        snapshotFlow {
            LoxodromicMotionParameters.fromDifferential(
                anglePerStep = (if (reverseDirection) -1 else +1) * angleSliderState.value,
                dilationPerStep = (if (reverseDirection) -1 else +1) * dilationSliderState.value.toDouble(),
                nTotalSteps = stepsSliderState.value.roundToInt(),
            )
        }
            .distinctUntilChanged()
            .debounce(UPDATE_PARAMETERS_DEBOUNCE_DELTA)
    }
    LaunchedEffect(loxodromicMotionParametersFlow, updateParameters) {
        loxodromicMotionParametersFlow.collect { params ->
            updateParameters(params)
        }
    }
}

@Preview
@Composable
private fun LoxodromicMotionInterfacePreview() {
    ContextActionsWrapper { positions ->
        LoxodromicMotionInterface(
            concretePositions = positions,
            defaults = DefaultLoxodromicMotionParameters(),
            updateParameters = {},
            updateBidirectionality = {},
            openDetailsDialog = {},
            confirmParameters = {},
        )
    }
}

@Composable
private fun ReverseDirectionToggle(
    isOn: Boolean,
    positionModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
    onClick: () -> Unit,
) {
    Box(positionModifier) {
        WithTooltip(stringResource(Tool.ReverseDirection.description)) {
            OnOffButton(
                painterResource(Tool.ReverseDirection.icon),
                stringResource(Tool.ReverseDirection.name),
                isOn = isOn,
                modifier = modifier,
                contentColor = MaterialTheme.colorScheme.secondary,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                containerColor = containerColor,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun BoxScope._PartialArcPathContextActions() {
    // +scale/rotate handles
    // border color
    // if closed: fill color

    // when vertex is focused:
    // delete

    // when midpoint is focused
    // *textual*
    // smoothen arc start
    // smoothen arc end
    // straighten arc
    // if non-free: unpin
}

// TODO: move it somewhere else, this location is bad
@Composable
fun BoxScope.PartialArcPathContextActions(
    canvasSize: IntSize,
    toolAction: (Tool) -> Unit,
) {
    val (w, h) = canvasSize
    val verticalMargin = with (LocalDensity.current) {
        (h*OnScreenPositions.RELATIVE_VERTICAL_MARGIN).toDp()
    }
    Button(
        onClick = { toolAction(Tool.CompleteArcPath) },
        // NOTE: this position is not optimal, especially for desktop
        Modifier
            .align(Alignment.BottomEnd)
            .offset(y = -verticalMargin)
        ,
        colors = ButtonDefaults.buttonColors()
            .copy(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
    ) {
        Icon(
            painterResource(Tool.CompleteArcPath.icon),
            stringResource(Tool.CompleteArcPath.name),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(Tool.CompleteArcPath.description),
            style = MaterialTheme.adaptiveTypography.label
        )
    }
}

@Preview
@Composable
private fun PartialArcPathContextActionsPreview() {
    ContextActionsWrapper { positions ->
        PartialArcPathContextActions(
            canvasSize = positions.size.let {
                IntSize(it.width.roundToInt(), it.height.roundToInt())
            },
            toolAction = {},
        )
    }
}

@Composable
private fun RegionManipulationStrategyChoice(
    strategy: RegionManipulationStrategy,
    isActive: Boolean,
    iconOnly: Boolean,
    setStrategy: (RegionManipulationStrategy) -> Unit,
) {
    val description = stringResource(strategy.descriptionResource)
    val color =
        if (isActive)
            MaterialTheme.colorScheme.onSecondaryContainer
        else
            MaterialTheme.colorScheme.onSurface
    if (iconOnly) {
        WithTooltip(
            description,
            modifier = Modifier
                .selectable(
                    selected = isActive,
                    onClick = { setStrategy(strategy) },
                    role = Role.RadioButton
                ).padding(8.dp)
        ) {
            Icon(painterResource(strategy.iconResource),
                contentDescription = description,
                modifier = Modifier,
                tint = color,
            )
        }
    } else {
        Row(
            Modifier
                .selectable(
                    selected = isActive,
                    onClick = { setStrategy(strategy) },
                    role = Role.RadioButton
                )
                .height(56.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
            ,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isActive,
                onClick = null, // null recommended for accessibility with screen readers
                colors = RadioButtonDefaults.colors().copy(
                    selectedColor = MaterialTheme.colorScheme.secondary,
                )
            )
            Text(
                text = description,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
                color = color,
                style = MaterialTheme.adaptiveTypography.body,
            )
        }
    }
}

@Composable
fun BoxScope.RegionManipulationStrategySelector(
    currentStrategy: RegionManipulationStrategy,
    setStrategy: (RegionManipulationStrategy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconOnly =
        MaterialTheme.adaptiveSizing.windowSizeClass.widthSizeClass < WindowWidthSizeClass.Expanded
    Surface(
        modifier = modifier.align(Alignment.CenterEnd),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier
            .width(IntrinsicSize.Max)
            .selectableGroup()
        ) {
            RegionManipulationStrategy.entries.forEach { strategy ->
                RegionManipulationStrategyChoice(
                    strategy = strategy,
                    isActive = currentStrategy == strategy,
                    iconOnly = iconOnly,
                    setStrategy = setStrategy,
                )
            }
        }
    }
}

@Preview
@Composable
private fun RegionManipulationStrategySelectorPreview() {
    ContextActionsWrapper {
        RegionManipulationStrategySelector(
            currentStrategy = RegionManipulationStrategy.ADD,
            setStrategy = {},
        )
    }
}

@Composable
fun BoxScope.InfinitePointInput(
    toolAction: (Tool.InfinitePoint) -> Unit,
) {
    SimpleToolButtonWithTooltip(Tool.InfinitePoint,
        positionModifier = Modifier.align(Alignment.CenterEnd),
        // the idea is that it would be colored the same as a normal point
        containerColor = MaterialTheme.extendedColorScheme.highAccentColor,
        contentColor = MaterialTheme.colorScheme.surface,
        onClick = toolAction,
    )
}