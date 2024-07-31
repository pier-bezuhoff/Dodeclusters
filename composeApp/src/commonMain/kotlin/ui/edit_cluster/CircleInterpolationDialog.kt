package ui.edit_cluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import data.geometry.CircleOrLine
import data.geometry.CirclePencilType
import data.geometry.GeneralizedCircle
import kotlin.math.roundToInt

data class DefaultInterpolationParameters(
    val nInterjacents: Int = 1,
    val inBetween: Boolean = true,
)

// TODO: localization
@OptIn(ExperimentalMaterial3Api::class)
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
    val showInsideOutsideToggle = false // broken
//        pencilType in setOf(CirclePencilType.ELLIPTIC, CirclePencilType.PARABOLIC)
    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
            ,
        ) {
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Pick the number of interpolation steps",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    buildAnnotatedString {
                        append("Number of circles in-between:  ")
                        withStyle(SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )) {
                            append("${sliderState.value.roundToInt()}")
                        }
                    }
                    ,
                    Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(sliderState)
                Text(
                    buildAnnotatedString {
                        append("Subdividing along ")
                        withStyle(SpanStyle(
                            color = MaterialTheme.colorScheme.secondary,
                            fontStyle = FontStyle.Italic
                        )) {
                            append("$pencilType")
                        }
                        append(" pencil")
                    },
                    Modifier.padding(8.dp).padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
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
                                append("Interpolate ")
                                withStyle(SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )) {
                                    append(if (interpolateInBetween) "in between" else "outside")
                                }
                                append(" the circles")
                            },
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    CancelButton(onDismissRequest = onDismissRequest)
                    OkButton(onConfirm = { onConfirm(sliderState.value.roundToInt(), interpolateInBetween) })
                }
            }
        }
    }
}