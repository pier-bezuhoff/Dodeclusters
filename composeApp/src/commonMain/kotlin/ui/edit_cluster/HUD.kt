package ui.edit_cluster

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.expand
import dodeclusters.composeapp.generated.resources.ok_name
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.steps_slider_name
import dodeclusters.composeapp.generated.resources.three_dots_in_angle_brackets
import domain.angleDeg
import domain.expressions.BiInversionParameters
import domain.expressions.InterpolationParameters
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.RotationParameters
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.OnOffButton
import ui.SimpleFilledButton
import ui.SimpleToolButtonWithTooltip
import ui.TwoIconButtonWithTooltip
import ui.VerticalSlider
import ui.WithTooltip
import ui.edit_cluster.dialogs.DefaultBiInversionParameters
import ui.edit_cluster.dialogs.DefaultInterpolationParameters
import ui.edit_cluster.dialogs.DefaultLoxodromicMotionParameters
import ui.edit_cluster.dialogs.DefaultRotationParameters
import ui.theme.extendedColorScheme
import ui.tools.EditClusterTool
import kotlin.math.abs
import kotlin.math.acosh
import kotlin.math.asinh
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sinh

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.SelectionContextActions(
    concretePositions: ConcreteOnScreenPositions,
    scaleSliderPercentage: Float,
    rotationHandleAngle: Float,
    objectColor: Color?,
    showAdjustExprButton: Boolean,
    showOrientationToggle: Boolean,
    isLocked: Boolean,
    toolAction: (EditClusterTool) -> Unit,
    toolPredicate: (EditClusterTool) -> Boolean,
    onScale: (newScaleSliderPercentage: Float) -> Unit,
    onScaleFinished: () -> Unit,
    onRotate: (newRotationAngle: Float) -> Unit,
    onRotateStarted: (center: Offset) -> Unit,
    onRotateFinished: () -> Unit,
) {
    // rotate handle
    val buttonModifier = Modifier
        .padding(8.dp)
        .size(36.dp)
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
            SimpleToolButtonWithTooltip(
                EditClusterTool.Expand,
                tint = MaterialTheme.colorScheme.secondary,
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
            SimpleToolButtonWithTooltip(
                EditClusterTool.Shrink,
                tint = MaterialTheme.colorScheme.secondary,
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
                SimpleToolButtonWithTooltip(
                    EditClusterTool.AdjustExpr,
                    buttonModifier,
                    onClick = toolAction
                )
            }
            SimpleToolButtonWithTooltip(
                EditClusterTool.PickCircleColor,
                buttonModifier,
                tint = objectColor ?: MaterialTheme.extendedColorScheme.highAccentColor,
                onClick = toolAction
            )
            TwoIconButtonWithTooltip(
                painterResource(EditClusterTool.MarkAsPhantoms.icon),
                painterResource(EditClusterTool.MarkAsPhantoms.disabledIcon),
                description = stringResource(EditClusterTool.MarkAsPhantoms.description),
                disabledDescription = stringResource(EditClusterTool.MarkAsPhantoms.disabledDescription),
                name = stringResource(EditClusterTool.MarkAsPhantoms.name),
                enabled = toolPredicate(EditClusterTool.MarkAsPhantoms),
                modifier = buttonModifier,
                onClick = { toolAction(EditClusterTool.MarkAsPhantoms) }
            )
            if (showOrientationToggle) {
                SimpleToolButtonWithTooltip(
                    EditClusterTool.SwapDirection,
                    buttonModifier,
                    onClick = toolAction
                )
            }
            if (isLocked) {
                SimpleToolButtonWithTooltip(
                    EditClusterTool.Detach,
                    buttonModifier,
                    onClick = toolAction
                )
            }
            SimpleToolButtonWithTooltip(
                EditClusterTool.Duplicate,
                buttonModifier,
                onClick = toolAction
            )
            SimpleToolButtonWithTooltip(
                EditClusterTool.Delete,
                buttonModifier,
                onClick = toolAction
            )
        }
    }
}

// MAYBE: add scale/rotation handles if multiple are selected
// only points
@Composable
fun BoxScope.PointContextActions(
//    objectColor: Color?,
    showAdjustExprButton: Boolean,
    isLocked: Boolean,
    toolAction: (EditClusterTool) -> Unit,
    toolPredicate: (EditClusterTool) -> Boolean,
) {
    val buttonModifier = Modifier
        .padding(8.dp)
        .size(36.dp)
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
                SimpleToolButtonWithTooltip(
                    EditClusterTool.AdjustExpr,
                    buttonModifier,
                    onClick = toolAction
                )
            }
            TwoIconButtonWithTooltip(
                painterResource(EditClusterTool.MarkAsPhantoms.icon),
                painterResource(EditClusterTool.MarkAsPhantoms.disabledIcon),
                description = stringResource(EditClusterTool.MarkAsPhantoms.description),
                disabledDescription = stringResource(EditClusterTool.MarkAsPhantoms.disabledDescription),
                name = stringResource(EditClusterTool.MarkAsPhantoms.name),
                enabled = toolPredicate(EditClusterTool.MarkAsPhantoms),
                modifier = buttonModifier,
                onClick = { toolAction(EditClusterTool.MarkAsPhantoms) }
            )
            if (isLocked) {
                SimpleToolButtonWithTooltip(
                    EditClusterTool.Detach,
                    buttonModifier,
                    onClick = toolAction
                )
            }
            SimpleToolButtonWithTooltip(
                EditClusterTool.Delete,
                buttonModifier,
                onClick = toolAction
            )
        }
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
    val params = InterpolationParameters(
        nInterjacents = sliderState.value.roundToInt(),
        inBetween = interpolateInBetween,
        complementary =
        if (interpolateCircles) {
            if (circlesAreCoDirected) !interpolateInBetween else interpolateInBetween
        } else interpolateInBetween
    )
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
                Box(topRightUnderScaleModifier) {
                    WithTooltip(
                        if (interpolateInBetween)
                            stringResource(EditClusterTool.InBetween.description)
                        else
                            stringResource(EditClusterTool.InBetween.disabledDescription)
                    ) {
                        OnOffButton(
                            painterResource(EditClusterTool.InBetween.icon),
                            stringResource(EditClusterTool.InBetween.name),
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
            SimpleToolButtonWithTooltip(
                EditClusterTool.DetailedAdjustment,
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
                sliderState,
                horizontalSliderModifier
                    .width(horizontalSliderWidth)
                ,
                colors = sliderColors,
            )
            SimpleFilledButton(
                painterResource(Res.drawable.confirm),
                stringResource(Res.string.ok_name),
                bottomRightModifier,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = confirmParameters
            )
        }
    }
    val coroutineScope = rememberCoroutineScope()
    key(params) { // this feels hacky, `key(params)` serves only a semantic purpose btw
        coroutineScope.launch {
            updateParameters(params)
        }
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
    val params = RotationParameters(
        angle = round( // we round cuz all angles coming from the slider are supposed to be integers
            if (rotateClockwise)
                -angleSliderState.value
            else angleSliderState.value
        ),
        nSteps = stepsSliderState.value.roundToInt(),
    )
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
            ReverseDirectionToggle(
                isOn = rotateClockwise,
                positionModifier = topRightUnderScaleModifier,
                containerColor = buttonBackground,
            ) {
                rotateClockwise = !rotateClockwise
                // triggers params upd => triggers VM.updParams
            }
            SimpleToolButtonWithTooltip(
                EditClusterTool.DetailedAdjustment,
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
                painterResource(Res.drawable.confirm),
                stringResource(Res.string.ok_name),
                bottomRightModifier,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = confirmParameters
            )
        }
    }
    val coroutineScope = rememberCoroutineScope()
    key(params) { // this feels hacky, `key(params)` serves only a semantic purpose btw
        coroutineScope.launch {
            updateParameters(params)
        }
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
    val k1 = remember {  1.0 / sinh(k2 / 2.0) }
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
    val params = BiInversionParameters(
        speed = (if (negateSpeed) -1 else +1) * k1*sinh(k2*speedSliderState.value),
        nSteps = stepsSliderState.value.roundToInt(),
        reverseSecondEngine = defaults.reverseSecondEngine,
    )
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
                positionModifier = topRightUnderScaleModifier,
                containerColor = buttonBackground,
            ) {
                negateSpeed = !negateSpeed
                // triggers params upd => triggers VM.updParams
            }
            SimpleToolButtonWithTooltip(
                EditClusterTool.DetailedAdjustment,
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
                painterResource(Res.drawable.confirm),
                stringResource(Res.string.ok_name),
                bottomRightModifier,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = confirmParameters
            )
        }
    }
    val coroutineScope = rememberCoroutineScope()
    key(params) { // this feels hacky, `key(params)` serves only a semantic purpose btw
        coroutineScope.launch {
            updateParameters(params)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoxodromicMotionInterface(
    concretePositions: ConcreteOnScreenPositions,
    defaults: DefaultLoxodromicMotionParameters,
    updateParameters: (LoxodromicMotionParameters) -> Unit,
    openDetailsDialog: () -> Unit,
    confirmParameters: () -> Unit,
) {
    // equivalent to swapping the order of engines
    var reverseDirection by remember { mutableStateOf(false) }
    val angleSliderState = remember { SliderState(
        value = defaults.anglePerStep,
        valueRange = defaults.angleRange
    ) }
    val dilationSliderState = remember { SliderState(
        value = defaults.dilationPerStep.toFloat(),
        valueRange = defaults.dilationRange
    ) }
    val stepsSliderState = remember { SliderState(
        value = defaults.nTotalSteps.toFloat(),
        steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
        valueRange = defaults.stepsRange
    ) }
    val params = LoxodromicMotionParameters.fromDifferential(
        anglePerStep = (if (reverseDirection) -1 else +1) * angleSliderState.value,
        dilationPerStep = (if (reverseDirection) -1 else +1) * dilationSliderState.value.toDouble(),
        nTotalSteps = stepsSliderState.value.roundToInt(),
    )
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
            ReverseDirectionToggle(
                isOn = reverseDirection,
                positionModifier = topRightUnderScaleModifier,
                containerColor = buttonBackground,
            ) {
                reverseDirection = !reverseDirection
            }
            SimpleToolButtonWithTooltip(
                EditClusterTool.DetailedAdjustment,
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
                painterResource(Res.drawable.confirm),
                stringResource(Res.string.ok_name),
                bottomRightModifier,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = confirmParameters
            )
        }
    }
    val coroutineScope = rememberCoroutineScope()
    key(params) { // this feels hacky, `key(params)` serves only a semantic purpose btw
        coroutineScope.launch {
            updateParameters(params)
        }
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
        WithTooltip(stringResource(EditClusterTool.ReverseDirection.description)) {
            OnOffButton(
                painterResource(EditClusterTool.ReverseDirection.icon),
                stringResource(EditClusterTool.ReverseDirection.name),
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

// TODO: move it somewhere else, this location is bad
@Composable
fun BoxScope.ArcPathContextActions(
    canvasSize: IntSize,
    toolAction: (EditClusterTool) -> Unit,
) {
    val (w, h) = canvasSize
    val verticalMargin = with (LocalDensity.current) {
        (h*OnScreenPositions.RELATIVE_VERTICAL_MARGIN).toDp()
    }
    Button(
        onClick = { toolAction(EditClusterTool.CompleteArcPath) },
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
            painterResource(EditClusterTool.CompleteArcPath.icon),
            stringResource(EditClusterTool.CompleteArcPath.name),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(EditClusterTool.CompleteArcPath.description),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun BoxScope.RegionManipulationStrategySelector(
    currentStrategy: RegionManipulationStrategy,
    setStrategy: (RegionManipulationStrategy) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Row(
                    Modifier
                        .selectable(
                            selected = (strategy == currentStrategy),
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
                        selected = (strategy == currentStrategy),
                        onClick = null, // null recommended for accessibility with screen readers
                        colors = RadioButtonDefaults.colors().copy(
                            selectedColor = MaterialTheme.colorScheme.secondary,
                        )
                    )
                    Text(
                        text = stringResource(strategy.stringResource),
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1f),
                        color =
                            if (strategy == currentStrategy)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

