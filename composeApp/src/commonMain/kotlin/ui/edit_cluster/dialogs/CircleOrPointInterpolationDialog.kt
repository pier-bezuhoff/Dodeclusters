@file:OptIn(ExperimentalMaterial3Api::class)

package ui.edit_cluster.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import data.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt1
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt2_variant1
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt2_variant2
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt3
import dodeclusters.composeapp.generated.resources.circle_interpolation_prompt
import dodeclusters.composeapp.generated.resources.circle_interpolation_title
import domain.expressions.InterpolationParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.stringResource
import ui.CancelOkRow
import ui.DialogTitle
import ui.LabelColonBigValue
import ui.component1
import ui.component2
import ui.hideSystemBars
import kotlin.math.roundToInt

@Immutable
data class DefaultInterpolationParameters(
    val nInterjacents: Int = 1,
    val inBetween: Boolean = true,
    val minCircleCount: Int = 1,
    val maxCircleCount: Int = 20
) {
    val nInterjacentsRange = minCircleCount.toFloat() .. maxCircleCount.toFloat()

    val params = InterpolationParameters(nInterjacents, inBetween)

    constructor(parameters: InterpolationParameters) : this(
        nInterjacents = parameters.nInterjacents,
        inBetween = parameters.inBetween,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CircleOrPointInterpolationDialog(
    startCircle: GCircle,
    endCircle: GCircle,
    onConfirm: (InterpolationParameters) -> Unit,
    onCancel: () -> Unit,
    defaults: DefaultInterpolationParameters = DefaultInterpolationParameters(),
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    val start = GeneralizedCircle.fromGCircle(startCircle)
    val end = GeneralizedCircle.fromGCircle(endCircle)
    val coDirected = start.scalarProduct(end) >= 0
    val minCount = defaults.minCircleCount
    val maxCount = defaults.maxCircleCount
    val sliderState = remember { SliderState(
        value = defaults.nInterjacents.toFloat(),
        steps = maxCount - minCount - 1, // only counts intermediates
        valueRange = defaults.nInterjacentsRange
    ) }
    val hideInBetweenToggle = startCircle is Point // && endCircle is Point
    var interpolateInBetween by remember { mutableStateOf(defaults.inBetween) }
    val (widthClass, heightClass) = calculateWindowSizeClass()
    val compactHeight = heightClass == WindowHeightSizeClass.Compact
    val okFontSize =
        if (widthClass == WindowWidthSizeClass.Compact)
            18.sp
        else 24.sp
    val onConfirm0 = { onConfirm(
        InterpolationParameters(
            nInterjacents = sliderState.value.roundToInt(),
            inBetween = interpolateInBetween,
            complementary = if (coDirected) !interpolateInBetween else interpolateInBetween
        )
    ) }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = !compactHeight),
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier
                .padding(16.dp)
            ,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            if (heightClass == WindowHeightSizeClass.Compact) {
                CircleInterpolationHorizontalCompact(
                    sliderState, interpolateInBetween,
                    hideInBetweenToggle = hideInBetweenToggle,
                    setInterpolateInBetween = { interpolateInBetween = it },
                    onDismissRequest = onCancel,
                    onConfirm = onConfirm0
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    DialogTitle(Res.string.circle_interpolation_title,
                        smallerFont = false,
                        Modifier.align(Alignment.CenterHorizontally)
                    )
                    LabelColonBigValue(
                        value = sliderState.value.roundToInt().toString(),
                        labelResource = Res.string.circle_interpolation_prompt
                    )
                    Slider(sliderState, Modifier.padding(16.dp))
                    if (!hideInBetweenToggle) {
                        InsideOutsideToggle(
                            interpolateInBetween,
                            setInterpolateInBetween = {
                                interpolateInBetween = it
                            }
                        )
                    }
                    CancelOkRow(
                        onDismissRequest = onCancel,
                        onConfirm = onConfirm0,
                        fontSize = okFontSize
                    )
                }
            }
        }
    }
    LaunchedEffect(dialogActions) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onConfirm0()
            }
        }
    }
}

@Composable
private fun CircleInterpolationHorizontalCompact(
    sliderState: SliderState,
    interpolateInBetween: Boolean,
    hideInBetweenToggle: Boolean,
    setInterpolateInBetween: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.Start
    ) {
        DialogTitle(Res.string.circle_interpolation_title,
            smallerFont = true,
            Modifier.align(Alignment.CenterHorizontally)
        )
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(0.5f)) {
                LabelColonBigValue(
                    value = sliderState.value.roundToInt().toString(),
                    labelResource = Res.string.circle_interpolation_prompt
                )
                Slider(sliderState, Modifier.padding(
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ))
            }
            if (!hideInBetweenToggle) {
                Column {
                    InsideOutsideToggle(interpolateInBetween, setInterpolateInBetween)
                }
            }
        }
        CancelOkRow(onDismissRequest, onConfirm, fontSize = 18.sp)
    }
}

@Composable
private fun InsideOutsideToggle(
    interpolateInBetween: Boolean,
    setInterpolateInBetween: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = interpolateInBetween,
            onCheckedChange = { setInterpolateInBetween(it) },
            modifier = Modifier.padding(8.dp)
        )
        Text(
            buildAnnotatedString {
                append(stringResource(Res.string.circle_interpolation_in_between_prompt1))
                append(" ")
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )) {
                    append(
                        if (interpolateInBetween)
                            stringResource(Res.string.circle_interpolation_in_between_prompt2_variant1)
                        else
                            stringResource(Res.string.circle_interpolation_in_between_prompt2_variant2))
                }
                append(" ")
                append(stringResource(Res.string.circle_interpolation_in_between_prompt3))
            },
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}