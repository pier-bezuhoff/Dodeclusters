package ui.edit_cluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import data.geometry.CircleOrLine
import data.geometry.CirclePencilType
import data.geometry.GeneralizedCircle
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt1
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt2_variant1
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt2_variant2
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt3
import dodeclusters.composeapp.generated.resources.circle_interpolation_prompt
import dodeclusters.composeapp.generated.resources.circle_interpolation_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

data class DefaultInterpolationParameters(
    val nInterjacents: Int = 1,
    val inBetween: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CircleInterpolationDialog(
    startCircle: CircleOrLine,
    endCircle: CircleOrLine,
    onDismissRequest: () -> Unit,
    onConfirm: (nInterjacents: Int, interpolateInBetween: Boolean) -> Unit,
    defaults: DefaultInterpolationParameters = DefaultInterpolationParameters(),
) {
    val start = GeneralizedCircle.fromGCircle(startCircle)
    val end = GeneralizedCircle.fromGCircle(endCircle)
    val pencilType = start.calculatePencilType(end)
    val sliderState = remember { SliderState(
        value = defaults.nInterjacents.toFloat(),
        steps = 20,
        valueRange = 1f..20f
    ) }
    var interpolateInBetween by remember { mutableStateOf(defaults.inBetween) }
    val showInsideOutsideToggle =
        pencilType == CirclePencilType.ELLIPTIC
    val (widthClass, heightClass) = calculateWindowSizeClass()
    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
            ,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(Res.string.circle_interpolation_title),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    buildAnnotatedString {
                        append(stringResource(Res.string.circle_interpolation_prompt))
                        append(":  ")
                        withStyle(SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )) {
                            append("${sliderState.value.roundToInt()}")
                        }
                    }
                    ,
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(sliderState, Modifier.padding(16.dp))
                if (showInsideOutsideToggle)
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = interpolateInBetween,
                            onCheckedChange = { interpolateInBetween = it },
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
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val fontSize =
                        if (widthClass == WindowWidthSizeClass.Compact)
                            18.sp
                        else 24.sp
                    CancelButton(fontSize = fontSize, onDismissRequest = onDismissRequest)
                    OkButton(fontSize = fontSize, onConfirm = { onConfirm(
                        sliderState.value.roundToInt(),
                        if (showInsideOutsideToggle) interpolateInBetween
                        else DefaultInterpolationParameters().inBetween
                    ) })
                }
            }
        }
    }
}