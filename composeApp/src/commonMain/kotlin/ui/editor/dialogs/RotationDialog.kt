package ui.editor.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.angle_fraction_denominator_placeholder
import dodeclusters.composeapp.generated.resources.angle_fraction_denominator_suffix
import dodeclusters.composeapp.generated.resources.angle_fraction_numerator_placeholder
import dodeclusters.composeapp.generated.resources.angle_fraction_numerator_suffix
import dodeclusters.composeapp.generated.resources.angle_in_degrees_placeholder
import dodeclusters.composeapp.generated.resources.degrees_suffix
import dodeclusters.composeapp.generated.resources.n_steps_placeholder
import dodeclusters.composeapp.generated.resources.n_steps_prompt
import dodeclusters.composeapp.generated.resources.rotation_angle_prompt
import dodeclusters.composeapp.generated.resources.rotation_title
import dodeclusters.composeapp.generated.resources.stub
import domain.expressions.RotationParameters
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.compose.resources.stringResource
import ui.CancelOkRow
import ui.DialogTitle
import ui.FloatTextField
import ui.IntTextField
import ui.PreTextFieldLabel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * @param[angleDiscretization] angle step = 5 degrees
 */
@Immutable
@Serializable
data class DefaultRotationParameters(
    val angle: Float = 30f,
    val nSteps: Int = 1,
    val minAngle: Float = 0f,
    val maxAngle: Float = 360f,
    val angleDiscretization: Float = 5f,
    val minNSteps: Int = 1,
    val maxNSteps: Int = 50,
) {
    @Transient
    val angleRange = minAngle..maxAngle
    @Transient
    val nAngleDiscretizationSteps: Int = ceil((maxAngle - minAngle)/angleDiscretization).toInt()
    @Transient
    val stepsRange = minNSteps.toFloat()..maxNSteps.toFloat()
    @Transient
    val params = RotationParameters(
        angle = angle,
        nSteps = nSteps,
    )

    constructor(parameters: RotationParameters) : this(
        angle = parameters.angle,
        nSteps = parameters.nSteps,
    )
}

// TODO: rational (p/q*TAU) angle input for regular polygons
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun RotationDialog(
    onConfirm: (RotationParameters) -> Unit,
    onCancel: () -> Unit,
    defaults: DefaultRotationParameters = DefaultRotationParameters(),
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    /** per-step angle in degrees */
    var angle: Float by remember(defaults) { mutableStateOf(defaults.angle) }
    /** 'n' in <`n/d * `[domain.TAU]> angle representation in radians */
    var angleNumerator: Float by remember(defaults) { mutableStateOf(
        defaults.angle / 360f
    ) }
    /** 'd' in <`n/d * `[domain.TAU]> angle representation in radians */
    var angleDenominator: Int by remember(defaults) { mutableStateOf(1) }
    var nSteps by remember(defaults) { mutableStateOf(defaults.nSteps) }

    fun buildParameters(): RotationParameters =
        RotationParameters(angle, nSteps)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState())
                ,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DialogTitle(Res.string.rotation_title)
                Row {
                    PreTextFieldLabel(Res.string.rotation_angle_prompt)
                    FloatTextField(
                        value = angle,
                        onNewValue = {
                            angle = it
                            angleNumerator = angle * angleDenominator / 360f
                        },
                        placeholderStringResource = Res.string.angle_in_degrees_placeholder,
                        suffixStringResource = Res.string.degrees_suffix,
                        nFractionalDigits = 4,
                    )
                }
                Box(Modifier.size(8.dp))
                Row(
//                    Modifier.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FloatTextField(
                        value = angleNumerator,
                        onNewValue = {
                            angleNumerator = it
                            angle = (angleNumerator/angleDenominator) * 360f
                        },
                        placeholderStringResource = Res.string.angle_fraction_numerator_placeholder,
                        nFractionalDigits = 4,
                        modifier = Modifier
//                            .padding(horizontal = 8.dp)
                            .widthIn(max = 160.dp)
                    )
                    Text(
                        stringResource(Res.string.angle_fraction_numerator_suffix),
                        Modifier.padding(8.dp),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    IntTextField(
                        value = angleDenominator,
                        onNewValue = {
                            angleDenominator = it
                            angle = (angleNumerator/angleDenominator) * 360f
                        },
                        placeholderStringResource = Res.string.angle_fraction_denominator_placeholder,
//                        suffixStringResource = Res.string.angle_fraction_denominator_suffix,
                        valueValidator = { it != 0 },
                        modifier = Modifier
//                            .padding(horizontal = 8.dp)
                            .widthIn(max = 100.dp)
                    )
                    Text(
                        stringResource(Res.string.angle_fraction_denominator_suffix),
                        Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Slider(
                    value = angle,
                    onValueChange = {
                        angle = it
                        angleNumerator = angle * angleDenominator / 360f
                    },
                    modifier = Modifier,
                    valueRange = defaults.angleRange,
                    steps = defaults.nAngleDiscretizationSteps - 1,
                )
                HorizontalDivider(
                    Modifier.padding(24.dp)
                )
                Row {
                    PreTextFieldLabel(Res.string.n_steps_prompt)
                    IntTextField(
                        value = nSteps,
                        onNewValue = { nSteps = it },
                        placeholderStringResource = Res.string.n_steps_placeholder,
                    )
                }
                Slider(
                    value = nSteps.toFloat(),
                    onValueChange = { nSteps = it.roundToInt() },
                    valueRange = defaults.stepsRange,
                    steps = defaults.maxNSteps - defaults.minNSteps - 1, // only counts intermediates
                )
                CancelOkRow(
                    onCancel = onCancel,
                    onOk = { onConfirm(buildParameters()) },
                )
            }
        }
    }
    LaunchedEffect(dialogActions) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onConfirm(buildParameters())
            }
        }
    }
}

private fun approximateAsFraction(
    decimal: Float,
    threshold: Float = 1e-3f,
    potentialDenominators: Iterable<Int> = 1..20,
): Pair<Float, Int> {
    var numerator = decimal
    var denominator = 1
    for (denominatorCandidate in potentialDenominators) {
        val n = decimal * denominatorCandidate
        val h = abs(n - n.roundToInt())
        if (h < threshold) {
            numerator = n
            denominator = denominatorCandidate
            break
        }
    }
    return Pair(numerator, denominator)
}
