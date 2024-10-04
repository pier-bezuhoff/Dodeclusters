package ui.edit_cluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import data.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_direction_ccw
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_direction_cw
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_direction_prompt1
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_direction_prompt2
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_placeholder
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_prompt
import dodeclusters.composeapp.generated.resources.loxodromic_motion_angle_suffix
import dodeclusters.composeapp.generated.resources.loxodromic_motion_dilation_placeholder
import dodeclusters.composeapp.generated.resources.loxodromic_motion_hyperbolic_prompt
import dodeclusters.composeapp.generated.resources.loxodromic_motion_n_steps_placeholder
import dodeclusters.composeapp.generated.resources.loxodromic_motion_steps_prompt
import dodeclusters.composeapp.generated.resources.loxodromic_motion_title
import domain.expressions.LoxodromicMotionParameters
import domain.formatDecimals
import org.jetbrains.compose.resources.stringResource
import ui.CancelButton
import ui.OkButton
import ui.hideSystemBars
import kotlin.math.abs
import kotlin.math.roundToInt

data class DefaultLoxodromicMotionParameters(
    /** in degrees */
    val angle: Float = 270f,
    val dilation: Double = 1.0,
    val nSteps: Int = 10,
    val minAngle: Float = 0f,
    val maxAngle: Float = 360f,
    val minDilation: Double = 0.0,
    val maxDilation: Double = 7.0,
    val minNSteps: Int = 0,
    val maxNSteps: Int = 20,
) {
    val params: LoxodromicMotionParameters = LoxodromicMotionParameters(angle, dilation, nSteps)
    val angleRange = minAngle .. maxAngle
    val dilationRange = minDilation.toFloat() .. maxDilation.toFloat()
    val stepsRange = minNSteps.toFloat() .. maxNSteps.toFloat()

    constructor(parameters: LoxodromicMotionParameters) : this(
        angle = parameters.angle,
        dilation = parameters.dilation,
        nSteps = parameters.nSteps
    )
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun LoxodromicMotionDialog(
    divergencePoint: Point,
    convergencePoint: Point,
    onDismissRequest: () -> Unit,
    onConfirm: (LoxodromicMotionParameters) -> Unit,
    defaults: DefaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(),
) {
    // MAYBE: add turn fraction conversion field
    // TODO: make onConfirm read all fields
    //  possibly by hoisting all TFValues and updates here
    var angle by remember(defaults) { mutableStateOf(abs(defaults.angle)) }
    // true = CCW
    var angleDirection by remember(defaults) { mutableStateOf(defaults.angle >= 0.0) }
    var dilation by remember(defaults) { mutableStateOf(defaults.dilation) }
    var nSteps by remember(defaults) { mutableStateOf(defaults.nSteps) }
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
                Title(smallerFont = compactWidth)
                Row {
                    AngleSliderPrefix(smallerFont = compactWidth)
                    AngleTextField(angle, { angle = it })
                }
                Slider(angle, { angle = it },
                    valueRange = defaults.angleRange
                )
                RotationDirectionToggle(angleDirection, { angleDirection = it }, smallerFont = compactWidth)
                Row {
                    DilationPrefix(smallerFont = compactWidth)
                    DilationTextField(dilation, { dilation = it })
                }
                Slider(dilation.toFloat(), { dilation = it.toDouble() },
                    valueRange = defaults.dilationRange
                )
                Row {
                    StepsPrefix(smallerFont = compactWidth)
                    StepsTextField(nSteps, { nSteps = it })
                }
                Slider(nSteps.toFloat(), { nSteps = it.roundToInt() },
                    valueRange = defaults.stepsRange,
                    steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
                )
                CancelOkRow(angle, angleDirection, dilation, nSteps, onDismissRequest, onConfirm, fontSize)
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
private fun AngleSliderPrefix(
    smallerFont: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.loxodromic_motion_angle_prompt))
            append(":  ")
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style =
            if (smallerFont) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun AngleTextField(
    angle: Float,
    setAngle: (newAngle: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = angle.formatDecimals(1)
    var angleTFValue by remember(angle) {
        mutableStateOf(TextFieldValue(s, TextRange(s.length)))
    }
    fun updateSlider() {
        val newAngle = angleTFValue.text.toFloatOrNull()
        if (newAngle != null && newAngle != angle) {
            setAngle(newAngle)
        }
    }
    OutlinedTextField(
        angleTFValue,
        onValueChange = { newValue ->
            angleTFValue = newValue
        },
        modifier = modifier
            .onKeyEvent {
                if (it.key == Key.Enter) {
                    updateSlider()
                    true
                } else false
            }
        ,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(stringResource(Res.string.loxodromic_motion_angle_placeholder)) },
        suffix = { Text(stringResource(Res.string.loxodromic_motion_angle_suffix)) },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { updateSlider() }
        ),
        singleLine = true,
    )
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

@Composable
private fun DilationPrefix(
    smallerFont: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.loxodromic_motion_hyperbolic_prompt))
            append(":  ")
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style =
            if (smallerFont) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun DilationTextField(
    dilation: Double,
    setDilation: (newDilation: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = dilation.formatDecimals(2)
    var dilationTFValue by remember(dilation) {
        mutableStateOf(TextFieldValue(s, TextRange(s.length)))
    }
    fun updateSlider() {
        val newDilation = dilationTFValue.text.toDoubleOrNull()
        if (newDilation != null && newDilation != dilation) {
            setDilation(newDilation)
        }
    }
    OutlinedTextField(
        dilationTFValue,
        onValueChange = { newValue ->
            dilationTFValue = newValue
        },
        modifier = modifier
            .onKeyEvent {
                if (it.key == Key.Enter) {
                    updateSlider()
                    true
                } else false
            }
        ,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(stringResource(Res.string.loxodromic_motion_dilation_placeholder)) },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { updateSlider() }
        ),
        singleLine = true,
    )
}

@Composable
private fun StepsPrefix(
    smallerFont: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.loxodromic_motion_steps_prompt))
            append(":  ")
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style =
            if (smallerFont) MaterialTheme.typography.bodySmall
            else MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun StepsTextField(
    nSteps: Int,
    setNSteps: (newNSteps: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = nSteps.toString()
    var stepsTFValue by remember(nSteps) {
        mutableStateOf(TextFieldValue(s, TextRange(s.length)))
    }
    fun updateSlider() {
        val newNSteps = stepsTFValue.text.toIntOrNull()
        if (newNSteps != null && newNSteps != nSteps && newNSteps >= 0) {
            setNSteps(newNSteps)
        }
    }
    OutlinedTextField(
        stepsTFValue,
        onValueChange = { newValue ->
            stepsTFValue = newValue
        },
        modifier = modifier
            .onKeyEvent {
                if (it.key == Key.Enter) {
                    updateSlider()
                    true
                } else false
            }
        ,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(stringResource(Res.string.loxodromic_motion_n_steps_placeholder)) },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { updateSlider() }
        ),
        singleLine = true,
    )
}

@Composable
private fun CancelOkRow(
    angle: Float,
    angleDirection: Boolean,
    dilation: Double,
    nSteps: Int,
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
                    if (angleDirection) angle
                    else -angle,
                    dilation,
                    nSteps
                )
            )
        })
    }
}
