package ui.edit_cluster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.ok_name
import dodeclusters.composeapp.generated.resources.steps_slider_name
import dodeclusters.composeapp.generated.resources.three_dots_in_angle_brackets
import domain.expressions.InterpolationParameters
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.OnOffButton
import ui.SimpleFilledButton
import ui.SimpleToolButton
import ui.TwoIconButton
import ui.edit_cluster.dialogs.DefaultInterpolationParameters
import ui.theme.extendedColorScheme
import ui.tools.EditClusterTool
import kotlin.math.roundToInt

// MAYBE: add tooltips to buttons

@Composable
fun BoxScope.LockedCircleSelectionContextActions(
    canvasSize: IntSize,
    toolAction: (EditClusterTool) -> Unit,
    toolPredicate: (EditClusterTool) -> Boolean,
    getMostCommonCircleColorInSelection: () -> Color?
) {
    with (ConcreteSelectionControlsPositions(canvasSize, LocalDensity.current)) {
        // duplicate & delete buttons
        SimpleToolButton(
            EditClusterTool.Duplicate,
            topRightUnderScaleModifier,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.PickCircleColor,
            halfBottomRightModifier,
            tint = getMostCommonCircleColorInSelection() ?: MaterialTheme.extendedColorScheme.highAccentColor,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.Delete,
            bottomRightModifier,
            onClick = toolAction
        )
        TwoIconButton(
            painterResource(EditClusterTool.MarkAsPhantoms.icon),
            painterResource(EditClusterTool.MarkAsPhantoms.disabledIcon),
            stringResource(EditClusterTool.MarkAsPhantoms.name),
            enabled = toolPredicate(EditClusterTool.MarkAsPhantoms),
            bottomMidModifier,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = { toolAction(EditClusterTool.MarkAsPhantoms) }
        )
        SimpleToolButton(
            EditClusterTool.Detach,
            bottomLeftModifier,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = toolAction
        )
    }
}

@Composable
fun BoxScope.CircleSelectionContextActions(
    canvasSize: IntSize,
    showDirectionArrows: Boolean,
    toolAction: (EditClusterTool) -> Unit,
    toolPredicate: (EditClusterTool) -> Boolean,
    getMostCommonCircleColorInSelection: () -> Color?,
) {
    // infinity button to the left-center
    // + a way to trigger a visual effect over it
//    SimpleButton(
//        painterResource(Res.drawable.infinity),
//        stringResource(Res.string.stub),
//        Modifier.align(Alignment.CenterStart)
//    ) {}
    with (ConcreteSelectionControlsPositions(canvasSize, LocalDensity.current)) {
        // expand & shrink buttons
        SimpleToolButton(
            EditClusterTool.Expand,
            Modifier
                .align(Alignment.TopStart)
                .then(topRightModifier)
            ,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.Shrink,
            scaleBottomRightModifier,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = toolAction
        )
        // duplicate & delete buttons
        SimpleToolButton(
            EditClusterTool.Duplicate,
            topRightUnderScaleModifier,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.PickCircleColor,
            halfBottomRightModifier,
            tint = getMostCommonCircleColorInSelection() ?: MaterialTheme.extendedColorScheme.highAccentColor,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.Delete,
            bottomRightModifier,
            onClick = toolAction
        )
        // TODO: make it make sense
        if (showDirectionArrows) {
            SimpleToolButton(
                EditClusterTool.SwapDirection,
                bottomMidModifier,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = toolAction
            )
        } else { // not the best way to go about it, but w/e
            TwoIconButton(
                painterResource(EditClusterTool.MarkAsPhantoms.icon),
                painterResource(EditClusterTool.MarkAsPhantoms.disabledIcon),
                stringResource(EditClusterTool.MarkAsPhantoms.name),
                enabled = toolPredicate(EditClusterTool.MarkAsPhantoms),
                bottomMidModifier,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = { toolAction(EditClusterTool.MarkAsPhantoms) }
            )
        }
    }
}

@Composable
fun PointSelectionContextActions(
    canvasSize: IntSize,
    selectionIsLocked: Boolean,
    toolAction: (EditClusterTool) -> Unit,
    toolPredicate: (EditClusterTool) -> Boolean,
) {
    val buttonShape = RoundedCornerShape(percent = 50)
    val buttonBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    with (ConcreteSelectionControlsPositions(canvasSize, LocalDensity.current)) {
        SimpleToolButton(
            EditClusterTool.Delete,
            // awkward position tbh
            bottomRightModifier.background(buttonBackground, buttonShape),
            onClick = toolAction
        )
        TwoIconButton(
            painterResource(EditClusterTool.MarkAsPhantoms.icon),
            painterResource(EditClusterTool.MarkAsPhantoms.disabledIcon),
            stringResource(EditClusterTool.MarkAsPhantoms.name),
            enabled = toolPredicate(EditClusterTool.MarkAsPhantoms),
            bottomMidModifier.background(buttonBackground, buttonShape),
            tint = MaterialTheme.colorScheme.secondary,
            onClick = { toolAction(EditClusterTool.MarkAsPhantoms) }
        )
        if (selectionIsLocked) {
            SimpleToolButton(
                EditClusterTool.Detach,
                halfBottomRightModifier.background(buttonBackground, buttonShape),
                tint = MaterialTheme.colorScheme.secondary,
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
fun BoxScope.InterpolationInterface(
    canvasSize: IntSize,
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
        valueRange = minCount.toFloat()..maxCount.toFloat()
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
    val buttonShape = RoundedCornerShape(percent = 50)
    val buttonBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    with (ConcreteSelectionControlsPositions(canvasSize, LocalDensity.current)) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            SimpleToolButton(
                EditClusterTool.DetailedAdjustment,
                topRightUnderScaleModifier
                    .background(buttonBackground, buttonShape)
                ,
                onClick = { openDetailsDialog() }
            )
            if (interpolateCircles) {
                OnOffButton(
                    painterResource(EditClusterTool.InBetween.icon),
                    stringResource(EditClusterTool.InBetween.name),
                    isOn = interpolateInBetween,
                    modifier = halfBottomRightModifier,
                    contentColor = MaterialTheme.colorScheme.secondary,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    containerColor = buttonBackground,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    interpolateInBetween = !interpolateInBetween
                    // triggers params upd => triggers VM.updParams
                }
            }
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

// TODO: move it somewhere else, this location is bad
@Composable
fun BoxScope.ArcPathContextActions(
    canvasSize: IntSize,
    toolAction: (EditClusterTool) -> Unit,
) {
    val (w, h) = canvasSize
    val verticalMargin = with (LocalDensity.current) {
        (h*SelectionControlsPositions.RELATIVE_VERTICAL_MARGIN).toDp()
    }
    Button(
        onClick = { toolAction(EditClusterTool.CompleteArcPath) },
        Modifier // NOTE: this position is not optimal, especially for desktop
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier
            .width(IntrinsicSize.Max)
            .selectableGroup()
        ) {
            RegionManipulationStrategy.entries.forEach { strategy ->
                Row(Modifier
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
                        color = if (strategy == currentStrategy)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

