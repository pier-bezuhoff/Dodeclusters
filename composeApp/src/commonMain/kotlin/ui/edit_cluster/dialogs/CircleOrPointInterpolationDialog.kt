@file:OptIn(ExperimentalMaterial3Api::class)

package ui.edit_cluster.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
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
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import data.geometry.Line
import data.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt1
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt2_variant1
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt2_variant2
import dodeclusters.composeapp.generated.resources.circle_interpolation_in_between_prompt3
import dodeclusters.composeapp.generated.resources.circle_interpolation_prompt
import dodeclusters.composeapp.generated.resources.circle_interpolation_title
import domain.expressions.InterpolationParameters
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.compose.resources.stringResource
import ui.CancelOkRow
import ui.DialogTitle
import ui.IntTextField
import ui.PreTextFieldLabel
import ui.theme.adaptiveTypography
import kotlin.math.roundToInt

@Immutable
@Serializable
data class DefaultInterpolationParameters(
    val nInterjacents: Int = 1,
    val inBetween: Boolean = true,
    val minCircleCount: Int = 1,
    val maxCircleCount: Int = 20
) {
    @Transient
    val nInterjacentsRange = minCircleCount.toFloat() .. maxCircleCount.toFloat()
    @Transient
    val params = InterpolationParameters(nInterjacents, inBetween)

    constructor(parameters: InterpolationParameters) : this(
        nInterjacents = parameters.nInterjacents,
        inBetween = parameters.inBetween,
    )
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CircleOrPointInterpolationDialog(
    startCircle: GCircle,
    endCircle: GCircle,
    onConfirm: (InterpolationParameters) -> Unit,
    onCancel: () -> Unit,
    defaults: DefaultInterpolationParameters = DefaultInterpolationParameters(),
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    val start = GeneralizedCircle.fromGCircle(startCircle)
    val end = GeneralizedCircle.fromGCircle(endCircle)
    val coDirected = start.scalarProduct(end) >= 0
    var nInterjacents by remember(defaults) { mutableStateOf(defaults.nInterjacents) }
    val hideInBetweenToggle =
        startCircle is Point ||
        startCircle is Line && endCircle is Line && startCircle.isCollinearTo(endCircle)
    var interpolateInBetween by remember { mutableStateOf(defaults.inBetween) }
    val windowSizeClass = calculateWindowSizeClass()
    val compactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact

    fun buildParameters(): InterpolationParameters =
        InterpolationParameters(
            nInterjacents = nInterjacents,
            inBetween = interpolateInBetween,
            complementary = when {
                hideInBetweenToggle -> false
                coDirected -> !interpolateInBetween
                else -> interpolateInBetween
            }
        )
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = !compactHeight),
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
            ,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            if (windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact) {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    DialogTitle(
                        Res.string.circle_interpolation_title,
                        Modifier.align(Alignment.CenterHorizontally)
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(0.5f)) {
                            Row {
                                PreTextFieldLabel(Res.string.circle_interpolation_prompt)
                                IntTextField(
                                    value = nInterjacents,
                                    onNewValue = { nInterjacents = it },
                                )
                            }
                            Slider(
                                value = nInterjacents.toFloat(),
                                onValueChange = { nInterjacents = it.roundToInt() },
                                valueRange = defaults.nInterjacentsRange,
                                steps = defaults.maxCircleCount - defaults.minCircleCount - 1, // only counts intermediates
                                modifier = Modifier.padding(
                                    top = 16.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                )
                                ,
                            )
                        }
                        if (!hideInBetweenToggle) {
                            Column {
                                InsideOutsideToggle(interpolateInBetween, { interpolateInBetween = it })
                            }
                        }
                    }
                    CancelOkRow(
                        onCancel = onCancel,
                        onOk = { onConfirm(buildParameters()) },
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    DialogTitle(
                        Res.string.circle_interpolation_title,
                        Modifier.align(Alignment.CenterHorizontally)
                    )
                    Row {
                        PreTextFieldLabel(Res.string.circle_interpolation_prompt)
                        IntTextField(
                            value = nInterjacents,
                            onNewValue = { nInterjacents = it },
                        )
                    }
                    Slider(
                        value = nInterjacents.toFloat(),
                        onValueChange = { nInterjacents = it.roundToInt() },
                        valueRange = defaults.nInterjacentsRange,
                        steps = defaults.maxCircleCount - defaults.minCircleCount - 1, // only counts intermediates
                        modifier = Modifier.padding(16.dp),
                    )
                    if (!hideInBetweenToggle) {
                        InsideOutsideToggle(
                            interpolateInBetween,
                            setInterpolateInBetween = {
                                interpolateInBetween = it
                            }
                        )
                    }
                    CancelOkRow(
                        onCancel = onCancel,
                        onOk = { onConfirm(buildParameters()) },
                    )
                }
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

@Composable
private fun InsideOutsideToggle(
    interpolateInBetween: Boolean,
    setInterpolateInBetween: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = interpolateInBetween,
            onCheckedChange = { setInterpolateInBetween(it) },
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
            style = MaterialTheme.adaptiveTypography.label
        )
    }
}