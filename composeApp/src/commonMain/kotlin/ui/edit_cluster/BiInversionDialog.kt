package ui.edit_cluster

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import data.geometry.CirclePencilType
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.angle_in_degrees_placeholder
import dodeclusters.composeapp.generated.resources.bi_inversion_angle_prompt
import dodeclusters.composeapp.generated.resources.bi_inversion_n_steps_placeholder
import dodeclusters.composeapp.generated.resources.bi_inversion_speed_prompt
import dodeclusters.composeapp.generated.resources.bi_inversion_steps_prompt
import dodeclusters.composeapp.generated.resources.bi_inversion_title
import dodeclusters.composeapp.generated.resources.degrees_suffix
import dodeclusters.composeapp.generated.resources.stub
import domain.degrees
import domain.expressions.BiInversionParameters
import domain.radians
import ui.DialogTitle
import ui.FloatTextField
import ui.IntTextField
import ui.PreTextFieldLabel
import ui.hideSystemBars
import ui.isCompact
import kotlin.math.roundToInt

@Immutable
data class DefaultBiInversionParameters(
    val speed: Double = 1.0,
    val nSteps: Int = 1,
    val reverseSecondEngine: Boolean = false,
    val minSpeed: Float = 0f,
    val maxSpeed: Float = 10f,
    val minAngle: Float = 0f,
    val maxAngle: Float = 360f,
//    val minDilation: Float = 0.01f,
//    val maxDilation: Float = 100.0f,
    val minNSteps: Int = 1,
    val maxNSteps: Int = 20,
) {
    val speedRange = minSpeed .. maxSpeed
    val stepsRange = minNSteps.toFloat() .. maxNSteps.toFloat()
    val angleRange = minAngle .. maxAngle
    val params = BiInversionParameters(
        speed = speed,
        nSteps = nSteps,
        reverseSecondEngine = reverseSecondEngine
    )

    constructor(parameters: BiInversionParameters) : this(
        speed = parameters.speed,
        nSteps = parameters.nSteps,
        reverseSecondEngine = parameters.reverseSecondEngine
    )
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun BiInversionDialog(
    engine1: GCircle,
    engine2: GCircle,
    onDismissRequest: () -> Unit,
    onConfirm: (BiInversionParameters) -> Unit,
    defaults: DefaultBiInversionParameters = DefaultBiInversionParameters(),
) {
    val engine1GC = GeneralizedCircle.fromGCircle(engine1)
    val engine2GC0 = GeneralizedCircle.fromGCircle(engine2)
    val reverseSecondEngine = engine1GC.scalarProduct(engine2GC0) < 0
    val engine2GC = if (reverseSecondEngine) -engine2GC0 else engine2GC0
    val pencil = engine1GC.calculatePencilType(engine2GC)
    val inversiveAngle = 2.0*engine1GC.inversiveAngle(engine2GC) // angle or log-dilation
    val showAngleSlider = pencil == CirclePencilType.ELLIPTIC
    var nSteps by remember(defaults) { mutableStateOf(defaults.nSteps) }
    var speed: Double by remember(defaults) { mutableStateOf(defaults.speed) }
    val dAngle: Float = (speed * inversiveAngle).degrees
    val isCompact = calculateWindowSizeClass().isCompact
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            val fontSize =
                if (isCompact) 14.sp
                else 24.sp
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState())
                ,
                horizontalAlignment = Alignment.Start
            ) {
                DialogTitle(Res.string.bi_inversion_title, smallerFont = isCompact)
                Row {
                    PreTextFieldLabel(Res.string.bi_inversion_speed_prompt, smallerFont = isCompact)
                    FloatTextField(
                        value = speed.toFloat(),
                        onNewValue = { speed = it.toDouble() },
                        nFractionalDigits = 4,
                    )
                }
                Slider(
                    value = speed.toFloat(),
                    onValueChange = { speed = it.toDouble() },
                    modifier = Modifier,
                    valueRange = defaults.speedRange,
                )
                if (showAngleSlider) {
                    Row {
                        PreTextFieldLabel(Res.string.bi_inversion_angle_prompt, smallerFont = isCompact)
                        FloatTextField(
                            value = dAngle,
                            onNewValue = { speed = it.radians / inversiveAngle },
                            placeholderStringResource = Res.string.angle_in_degrees_placeholder,
                            suffixStringResource = Res.string.degrees_suffix,
                            nFractionalDigits = 2,
                        )
                    }
                    Slider(
                        value = dAngle,
                        onValueChange = { speed = it.radians / inversiveAngle },
                        modifier = Modifier,
                        valueRange = defaults.angleRange,
                    )
                }
                Row {
                    PreTextFieldLabel(Res.string.bi_inversion_steps_prompt, smallerFont = isCompact)
                    IntTextField(
                        value = nSteps,
                        onNewValue = { nSteps = it },
                        placeholderStringResource = Res.string.bi_inversion_n_steps_placeholder,
                    )
                }
                Slider(
                    value = nSteps.toFloat(),
                    onValueChange = { nSteps = it.roundToInt() },
                    valueRange = defaults.stepsRange,
                    steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
                )
                ui.CancelOkRow(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { onConfirm(BiInversionParameters(speed, nSteps, reverseSecondEngine)) },
                    fontSize = fontSize
                )
            }
        }
    }
}
