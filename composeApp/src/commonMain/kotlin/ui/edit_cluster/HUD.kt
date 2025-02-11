package ui.edit_cluster

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dodeclusters.composeapp.generated.resources.stub
import dodeclusters.composeapp.generated.resources.three_dots_in_angle_brackets
import domain.expressions.InterpolationParameters
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.OnOffButton
import ui.SimpleButton
import ui.SimpleToolButton
import ui.edit_cluster.dialogs.DefaultInterpolationParameters
import ui.theme.extendedColorScheme
import ui.tools.EditClusterTool
import kotlin.math.roundToInt

@Composable
fun BoxScope.LockedCircleSelectionContextActions(
    canvasSize: IntSize,
    toolAction: (EditClusterTool) -> Unit,
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
        if (showDirectionArrows) {
            SimpleToolButton(
                EditClusterTool.SwapDirection,
                bottomMidModifier,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = toolAction
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.CircleInterpolationInterface(
    updateParameters: (InterpolationParameters) -> Unit,
    openDetailsDialog: () -> Unit,
    defaults: DefaultInterpolationParameters,
) {
    val minCount = defaults.minCircleCount
    val maxCount = defaults.maxCircleCount
    val coDirected = false
    val sliderState = remember { SliderState(
        value = defaults.nInterjacents.toFloat(),
        steps = maxCount - minCount - 1, // only counts intermediates
        valueRange = minCount.toFloat()..maxCount.toFloat()
    ) }
    var interpolateInBetween by remember { mutableStateOf(defaults.inBetween) }
    val params = InterpolationParameters(
        sliderState.value.roundToInt(),
        if (coDirected) !interpolateInBetween else interpolateInBetween
    )
    LaunchedEffect(params) {
        // MAYBE: instead of this just use VM.paramsFlow
        updateParameters(params)
    }
    Column() {
        SimpleToolButton(EditClusterTool.DetailedAdjustment) {
            openDetailsDialog()
            // on confirm from dialog reset this submode
        }
        OnOffButton(
            painterResource(EditClusterTool.InBetween.icon),
            stringResource(EditClusterTool.InBetween.name),
            isOn = interpolateInBetween
        ) {
            interpolateInBetween = !interpolateInBetween // triggers params upd => triggers VM.updParams
        }
        Row {
            Icon( // icon for the slider
                painterResource(Res.drawable.three_dots_in_angle_brackets),
                stringResource(Res.string.stub)
            )
            Slider(sliderState)
            SimpleButton(
                painterResource(Res.drawable.confirm),
                stringResource(Res.string.ok_name)
            ) {
                // complete submode
            }
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

