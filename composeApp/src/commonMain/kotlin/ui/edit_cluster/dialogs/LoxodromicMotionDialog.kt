package ui.edit_cluster.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.angle_in_degrees_placeholder
import dodeclusters.composeapp.generated.resources.degrees_suffix
import dodeclusters.composeapp.generated.resources.dilation_placeholder
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_direction_ccw
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_direction_cw
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_direction_prompt1
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_direction_prompt2
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_prompt
import dodeclusters.composeapp.generated.resources.loxodromic_motion_hyperbolic_prompt
import dodeclusters.composeapp.generated.resources.loxodromic_motion_title
import dodeclusters.composeapp.generated.resources.n_steps_placeholder
import dodeclusters.composeapp.generated.resources.n_steps_prompt
import domain.expressions.LoxodromicMotionParameters
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.stringResource
import ui.CancelOkRow
import ui.DialogTitle
import ui.DoubleTextField
import ui.FloatTextField
import ui.IntTextField
import ui.PreTextFieldLabel
import ui.hideSystemBars
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * NOTE: [anglePerStep], [dilationPerStep], [nTotalSteps] are parametrized
 *  differently cmp. to [LoxodromicMotionParameters]
 * @param[anglePerStep] rotational angular speed per step, in degrees
 * @param[dilationPerStep] hyperbolic speed in `ln(r2/r1)` per step
 * @param[nTotalSteps] total number of loxodromic steps
 * @param[forwardAndBackward] construct both divergence->convergence and
 * convergence->divergence spirals, [nTotalSteps] each
 */
@Immutable
data class DefaultLoxodromicMotionParameters(
    val anglePerStep: Float = 15f,
    val dilationPerStep: Double = 0.1,
    val nTotalSteps: Int = 20,
    val minAngle: Float = -60f,
    val maxAngle: Float = 60f,
    val minDilation: Double = -1.0,
    val maxDilation: Double = 1.0,
    val minNSteps: Int = 1,
    val maxNSteps: Int = 50,
    val forwardAndBackward: Boolean = false,
) {
    val params: LoxodromicMotionParameters = LoxodromicMotionParameters.fromDifferential(
        anglePerStep, dilationPerStep, nTotalSteps
    )
    val angleRange = minAngle .. maxAngle
    val dilationRange = minDilation.toFloat() .. maxDilation.toFloat()
    val stepsRange = minNSteps.toFloat() .. maxNSteps.toFloat()

    constructor(parameters: LoxodromicMotionParameters) : this(
        anglePerStep = parameters.anglePerStep,
        dilationPerStep = parameters.dilationPerStep,
        nTotalSteps = parameters.nTotalSteps,
    )
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun LoxodromicMotionDialog(
    onConfirm: (LoxodromicMotionParameters) -> Unit,
    onCancel: () -> Unit,
    defaults: DefaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(),
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    // MAYBE: add turn fraction conversion field
    var angle by remember(defaults) { mutableStateOf(abs(defaults.anglePerStep)) }
    // true = CCW
    var angleDirection by remember(defaults) { mutableStateOf(defaults.anglePerStep >= 0.0) }
    var dilation by remember(defaults) { mutableStateOf(defaults.dilationPerStep) }
    var nSteps by remember(defaults) { mutableStateOf(defaults.nTotalSteps) }
    val windowSizeClass = calculateWindowSizeClass()
    val compactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val compactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = !compactHeight)
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            val fontSize =
                if (compactWidth) 14.sp
                else 24.sp
            Column(
                Modifier.padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                DialogTitle(Res.string.loxodromic_motion_title, smallerFont = compactWidth)
                Row {
                    PreTextFieldLabel(Res.string.loxodromic_motion_angle_prompt, smallerFont = compactWidth)
                    FloatTextField(
                        value = angle,
                        onNewValue = { angle = it },
                        placeholderStringResource = Res.string.angle_in_degrees_placeholder,
                        suffixStringResource = Res.string.degrees_suffix,
                        nFractionalDigits = 1,
                    )
                }
                Slider(angle, { angle = it },
                    valueRange = defaults.angleRange
                )
                RotationDirectionToggle(angleDirection, { angleDirection = it }, smallerFont = compactWidth)
                Row {
                    PreTextFieldLabel(Res.string.loxodromic_motion_hyperbolic_prompt, smallerFont = compactWidth)
                    DoubleTextField(
                        value = dilation,
                        onNewValue = { dilation = it },
                        placeholderStringResource = Res.string.dilation_placeholder,
                        nFractionalDigits = 3,
                    )
                }
                Slider(dilation.toFloat(), { dilation = it.toDouble() },
                    valueRange = defaults.dilationRange
                )
                Row {
                    PreTextFieldLabel(Res.string.n_steps_prompt, smallerFont = compactWidth)
                    IntTextField(
                        value = nSteps,
                        onNewValue = { nSteps = it },
                        placeholderStringResource = Res.string.n_steps_placeholder,
                    )
                }
                Slider(nSteps.toFloat(), { nSteps = it.roundToInt() },
                    valueRange = defaults.stepsRange,
                    steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
                )
                CancelOkRow(
                    onDismissRequest = onCancel,
                    onConfirm = {
                        onConfirm(
                            LoxodromicMotionParameters.fromDifferential(
                                if (angleDirection) angle else -angle,
                                dilation,
                                nSteps
                            )
                        )
                    },
                    fontSize = fontSize
                )
            }
        }
    }
    LaunchedEffect(dialogActions) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onConfirm(
                    LoxodromicMotionParameters.fromDifferential(
                        if (angleDirection) angle else -angle,
                        dilation,
                        nSteps
                    )
                )
            }
        }
    }
}

@Composable
private fun RotationDirectionToggle(
    /** true = CCW, false = CW */
    direction: Boolean,
    setDirection: (Boolean) -> Unit,
    smallerFont: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = direction,
            onCheckedChange = { setDirection(it) },
            modifier = Modifier.padding(8.dp)
        )
        Text(
            buildAnnotatedString {
                append(stringResource(Res.string.loxodromic_motion_angle_direction_prompt1))
                append(" ")
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )) {
                    append(
                        if (direction)
                            stringResource(Res.string.loxodromic_motion_angle_direction_ccw)
                        else
                            stringResource(Res.string.loxodromic_motion_angle_direction_cw)
                    )
                }
                append(" ")
                append(stringResource(Res.string.loxodromic_motion_angle_direction_prompt2))
            },
            modifier = Modifier.padding(8.dp),
            style =
                if (smallerFont) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.labelLarge
        )
    }
}
