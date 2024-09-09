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
import data.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_prompt
import dodeclusters.composeapp.generated.resources.loxodromic_motion_hyperbolic_prompt
import dodeclusters.composeapp.generated.resources.loxodromic_motion_steps_prompt
import dodeclusters.composeapp.generated.resources.loxodromic_motion_title
import domain.TAU
import domain.formatDecimals
import org.jetbrains.compose.resources.stringResource
import ui.CancelButton
import ui.OkButton
import ui.hideSystemBars
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * @param[angle] total rotation angle in radians
 * @param[dilation] total `ln(R/r)`
 * @param[nSteps] number of intermediate steps (0 = result only)
 * */
data class LoxodromicMotionParameters(
    val angle: Float,
    val dilation: Float,
    val nSteps: Int,
)

data class DefaultLoxodromicMotionParameters(
    val angle: Float = TAU.toFloat()/2,
    val dilation: Float = 1.0f,
    val nSteps: Int = 5,
    val minAngle: Float = -TAU.toFloat(),
    val maxAngle: Float = TAU.toFloat(),
    val minDilation: Float = 0f,
    val maxDilation: Float = 7f,
    val minNSteps: Int = 0,
    val maxNSteps: Int = 20,
) {
    val params: LoxodromicMotionParameters = LoxodromicMotionParameters(angle, dilation, nSteps)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun LoxodromicMotionDialog(
    divergencePoint: Point,
    convergencePoint: Point,
    onDismissRequest: () -> Unit,
    onConfirm: (LoxodromicMotionParameters) -> Unit,
    defaults: DefaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(),
) {
    // angle slider
    // degree field
    // turn fraction conversion field
    // CW/CCW switch
    // hyperbolic slider + field
    // n steps slider
    val angleSliderState = remember { SliderState(
        value = defaults.angle,
        steps = 0,
        valueRange = defaults.minAngle .. defaults.maxAngle
    ) }
    val hyperbolicSliderState = remember { SliderState(
        value = defaults.dilation,
        steps = 0,
        valueRange = defaults.minDilation .. defaults.maxDilation
    ) }
    val stepsSliderState = remember { SliderState(
        value = defaults.nSteps.toFloat(),
        steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
        valueRange = defaults.minNSteps.toFloat() .. defaults.maxNSteps.toFloat()
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
            val fontSize =
                if (compactWidth) 14.sp
                else 24.sp
            Column(
                Modifier.padding(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Title()
                // TODO: modify into kbd editable fields
                // TODO: split sign from angle into CW/CCW switch
                AngleSliderText(angleSliderState)
                Slider(angleSliderState)
                HyperbolicSliderText(hyperbolicSliderState)
                Slider(hyperbolicSliderState)
                StepsSliderText(stepsSliderState)
                Slider(stepsSliderState)
                CancelOkRow(angleSliderState, hyperbolicSliderState, stepsSliderState, onDismissRequest, onConfirm, fontSize)
            }
        }
    }
}

@Composable
private fun Title(smallerFont: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.loxodromic_motion_title),
        modifier = modifier.padding(16.dp),
        style =
        if (smallerFont) MaterialTheme.typography.titleMedium
        else MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun AngleSliderText(
    angleSliderState: SliderState,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.loxodromic_motion_angle_prompt))
            append(":  ")
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            ) {
                val angleDeg = (angleSliderState.value * 180/PI).formatDecimals(1)
                append("${angleDeg}°")
            }
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun HyperbolicSliderText(
    hyperbolicSliderState: SliderState,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.loxodromic_motion_hyperbolic_prompt))
            append(":  ")
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            ) {
                val hyperbolicShift = (hyperbolicSliderState.value).formatDecimals(2)
                append(hyperbolicShift)
            }
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun StepsSliderText(
    stepsSliderState: SliderState,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.loxodromic_motion_steps_prompt))
            append(":  ")
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            ) {
                val steps = (stepsSliderState.value).roundToInt()
                append("$steps")
            }
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun CancelOkRow(
    angleSliderState: SliderState,
    hyperbolicSliderState: SliderState,
    stepsSliderState: SliderState,
    onDismissRequest: () -> Unit,
    onConfirm: (LoxodromicMotionParameters) -> Unit,
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
                LoxodromicMotionParameters(
                    angleSliderState.value,
                    hyperbolicSliderState.value,
                    stepsSliderState.value.roundToInt()
                )
            )
        })
    }
}
