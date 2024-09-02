@file:OptIn(ExperimentalMaterial3Api::class)

package ui.edit_cluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import data.geometry.CircleOrLine
import data.geometry.GeneralizedCircle
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.circle_extrapolation_left_prompt1
import dodeclusters.composeapp.generated.resources.circle_extrapolation_left_prompt2
import dodeclusters.composeapp.generated.resources.circle_extrapolation_right_prompt1
import dodeclusters.composeapp.generated.resources.circle_extrapolation_right_prompt2
import dodeclusters.composeapp.generated.resources.circle_extrapolation_title
import org.jetbrains.compose.resources.stringResource
import ui.CancelButton
import ui.OkButton
import ui.hideSystemBars
import ui.isLandscape
import kotlin.math.roundToInt

data class DefaultExtrapolationParameters(
    val nLeft: Int = 1,
    val nRight: Int = 1,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CircleExtrapolationDialog(
    startCircle: CircleOrLine,
    endCircle: CircleOrLine,
    onDismissRequest: () -> Unit,
    onConfirm: (nLeft: Int, nRight: Int) -> Unit,
    defaults: DefaultExtrapolationParameters = DefaultExtrapolationParameters(),
) {
    val maxCount = 20
    val start = GeneralizedCircle.fromGCircle(startCircle)
    val end = GeneralizedCircle.fromGCircle(endCircle)
    val leftSliderState = remember { SliderState(
        value = defaults.nLeft.toFloat(),
        steps = maxCount + 1,
        valueRange = 0f..maxCount.toFloat()
    ) }
    val rightSliderState = remember { SliderState(
        value = defaults.nRight.toFloat(),
        steps = maxCount + 1,
        valueRange = 0f..maxCount.toFloat()
    ) }
    val windowSizeClass = calculateWindowSizeClass()
    val compactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val compactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = !compactHeight)
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            if (windowSizeClass.isLandscape) {
                if (windowSizeClass.heightSizeClass <= WindowHeightSizeClass.Compact) // for mobile phones
                    CircleExtrapolationHorizontalCompact(leftSliderState, rightSliderState, onDismissRequest, onConfirm)
                else
                    CircleExtrapolationHorizontal(leftSliderState, rightSliderState, onDismissRequest, onConfirm)
            } else {
                CircleExtrapolationVertical(leftSliderState, rightSliderState, onDismissRequest, onConfirm, compactWidth)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircleExtrapolationHorizontalCompact(
    leftSliderState: SliderState,
    rightSliderState: SliderState,
    onDismissRequest: () -> Unit,
    onConfirm: (nLeft: Int, nRight: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Title(smallerFont = true, Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.fillMaxWidth(0.5f)) {
                LeftSliderText(leftSliderState)
                Slider(leftSliderState)
            }
            Column() {
                RightSliderText(rightSliderState)
                Slider(rightSliderState)
            }
        }
        CancelOkRow(leftSliderState, rightSliderState, onDismissRequest, onConfirm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircleExtrapolationHorizontal(
    leftSliderState: SliderState,
    rightSliderState: SliderState,
    onDismissRequest: () -> Unit,
    onConfirm: (nLeft: Int, nRight: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Title(modifier = Modifier.align(Alignment.CenterHorizontally))
        LeftSliderText(leftSliderState)
        Slider(leftSliderState)
        RightSliderText(rightSliderState)
        Slider(rightSliderState)
        CancelOkRow(leftSliderState, rightSliderState, onDismissRequest, onConfirm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircleExtrapolationVertical(
    leftSliderState: SliderState,
    rightSliderState: SliderState,
    onDismissRequest: () -> Unit,
    onConfirm: (nLeft: Int, nRight: Int) -> Unit,
    compactWidth: Boolean,
    modifier: Modifier = Modifier
) {
    val fontSize =
        if (compactWidth)
            14.sp
        else 24.sp
    Column(
        modifier.padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Title(modifier = Modifier.align(Alignment.CenterHorizontally))
        LeftSliderText(leftSliderState)
        Slider(leftSliderState)
        RightSliderText(rightSliderState)
        Slider(rightSliderState)
        CancelOkRow(leftSliderState, rightSliderState, onDismissRequest, onConfirm, fontSize)
    }
}

@Composable
private fun Title(smallerFont: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.circle_extrapolation_title),
        modifier = modifier.padding(16.dp),
        style =
            if (smallerFont) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun LeftSliderText(
    leftSliderState: SliderState,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.circle_extrapolation_left_prompt1))
            append(" ")
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.secondary,
//                                fontStyle = FontStyle.Italic
                )
            ) {
                append(stringResource(Res.string.circle_extrapolation_left_prompt2))
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
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun RightSliderText(
    rightSliderState: SliderState,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.circle_extrapolation_right_prompt1))
            append(" ")
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.secondary,
//                                fontStyle = FontStyle.Italic
                )
            ) {
                append(stringResource(Res.string.circle_extrapolation_right_prompt2))
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
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun CancelOkRow(
    leftSliderState: SliderState,
    rightSliderState: SliderState,
    onDismissRequest: () -> Unit,
    onConfirm: (nLeft: Int, nRight: Int) -> Unit,
    fontSize: TextUnit = 24.sp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        CancelButton(fontSize = fontSize, onDismissRequest = onDismissRequest)
        OkButton(fontSize = fontSize, onConfirm = {
            onConfirm(
                leftSliderState.value.roundToInt(),
                rightSliderState.value.roundToInt()
            )
        })
    }
}