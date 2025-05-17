package ui.edit_cluster.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.angle_in_degrees_placeholder
import dodeclusters.composeapp.generated.resources.degrees_suffix
import dodeclusters.composeapp.generated.resources.n_steps_placeholder
import dodeclusters.composeapp.generated.resources.n_steps_prompt
import dodeclusters.composeapp.generated.resources.rotation_angle_prompt
import dodeclusters.composeapp.generated.resources.rotation_title
import domain.expressions.RotationParameters
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ui.CancelOkRow
import ui.DialogTitle
import ui.FloatTextField
import ui.IntTextField
import ui.PreTextFieldLabel
import ui.hideSystemBars
import ui.isCompact
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

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun RotationDialog(
    onConfirm: (RotationParameters) -> Unit,
    onCancel: () -> Unit,
    defaults: DefaultRotationParameters = DefaultRotationParameters(),
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    var angle: Float by remember(defaults) { mutableStateOf(defaults.angle) }
    var nSteps by remember(defaults) { mutableStateOf(defaults.nSteps) }
    val isCompact = calculateWindowSizeClass().isCompact
    val fontSize =
        if (isCompact) 14.sp
        else 24.sp
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState())
                ,
                horizontalAlignment = Alignment.Start
            ) {
                DialogTitle(Res.string.rotation_title, smallerFont = isCompact)
                Row {
                    PreTextFieldLabel(Res.string.rotation_angle_prompt, smallerFont = isCompact)
                    FloatTextField(
                        value = angle,
                        onNewValue = { angle = it },
                        placeholderStringResource = Res.string.angle_in_degrees_placeholder,
                        suffixStringResource = Res.string.degrees_suffix,
                        nFractionalDigits = 4,
                    )
                }
                Slider(
                    value = angle,
                    onValueChange = { angle = it },
                    modifier = Modifier,
                    valueRange = defaults.angleRange,
                    steps = defaults.nAngleDiscretizationSteps - 1,
                )
                Row {
                    PreTextFieldLabel(Res.string.n_steps_prompt, smallerFont = isCompact)
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
                    onDismissRequest = onCancel,
                    onConfirm = { onConfirm(RotationParameters(angle, nSteps)) },
                    fontSize = fontSize
                )
            }
        }
    }
    LaunchedEffect(dialogActions) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onConfirm(RotationParameters(angle, nSteps))
            }
        }
    }
}
