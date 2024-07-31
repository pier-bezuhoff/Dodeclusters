package ui.edit_cluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import data.geometry.GeneralizedCircle
import kotlin.math.roundToInt

data class DefaultExtrapolationParameters(
    val nLeft: Int = 1,
    val nRight: Int = 1,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleExtrapolationDialog(
    startCircle: CircleOrLine,
    endCircle: CircleOrLine,
    onDismissRequest: () -> Unit,
    onConfirm: (nLeft: Int, nRight: Int) -> Unit,
    defaults: DefaultExtrapolationParameters = DefaultExtrapolationParameters(),
) {
    val start = GeneralizedCircle.fromGCircle(startCircle)
    val end = GeneralizedCircle.fromGCircle(endCircle)
    val leftSliderState = remember { SliderState(
        value = defaults.nLeft.toFloat(),
        steps = 11,
        valueRange = 0f..10f
    ) }
    val rightSliderState = remember { SliderState(
        value = defaults.nRight.toFloat(),
        steps = 11,
        valueRange = 0f..10f
    ) }
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
                    text = "Pick the number of extrapolation steps",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    buildAnnotatedString {
                        append("Number of circles to the ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.secondary,
//                                fontStyle = FontStyle.Italic
                            )
                        ) {
                            append("left of the first one")
                        }
                        append(":  ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("${leftSliderState.value.roundToInt()}")
                        }
                    }
                    ,
                    Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(leftSliderState)
                Text(
                    buildAnnotatedString {
                        append("Number of circles to the ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.secondary,
//                                fontStyle = FontStyle.Italic
                            )
                        ) {
                            append("right of the second one")
                        }
                        append(":  ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("${rightSliderState.value.roundToInt()}")
                        }
                    }
                    ,
                    Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(rightSliderState)
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    CancelButton(onDismissRequest = onDismissRequest)
                    OkButton(onConfirm = {
                        onConfirm(
                            leftSliderState.value.roundToInt(),
                            rightSliderState.value.roundToInt()
                        )
                    })
                }
            }
        }
    }
}
