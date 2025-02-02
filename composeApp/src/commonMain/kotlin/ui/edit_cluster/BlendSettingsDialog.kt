@file:OptIn(ExperimentalMaterial3Api::class)

package ui.edit_cluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.circle_interpolation_prompt
import dodeclusters.composeapp.generated.resources.circle_interpolation_title
import dodeclusters.composeapp.generated.resources.stub
import org.jetbrains.compose.resources.stringResource
import ui.CancelButton
import ui.OkButton
import ui.component1
import ui.component2
import ui.hideSystemBars
import kotlin.math.roundToInt

private val BLEND_MODES = mapOf(
    BlendMode.SrcOver to Res.string.stub,
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun BlendSettingsDialog(
    oldTransparency: Float,
    oldBlendMode: BlendMode,
    onDismissRequest: () -> Unit,
    onConfirm: (newTransparency: Float, newBlendMode: BlendMode) -> Unit,
) {
    val sliderState = remember { SliderState(
        value = oldTransparency,
        valueRange = 0f .. 1f,
    ) }
    var blendMode by remember { mutableStateOf(oldBlendMode) }
    val (widthClass, heightClass) = calculateWindowSizeClass()
    val compactHeight = heightClass == WindowHeightSizeClass.Compact
    val okFontSize =
        if (widthClass == WindowWidthSizeClass.Compact)
            18.sp
        else 24.sp
    val onConfirm0 = { onConfirm(sliderState.value, blendMode) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier
                .padding(16.dp)
            ,
            shape = RoundedCornerShape(24.dp)
        ) {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Title(smallerFont = false, Modifier.align(Alignment.CenterHorizontally))
                    SliderText(sliderState)
                    Slider(sliderState, Modifier.padding(16.dp))
                    Column(Modifier
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                    ) {
                        BLEND_MODES.entries.forEach { (blendModeVariant, name) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
//                                    .selectable(
//                                        selected = (text == selectedOption),
//                                        onClick = { onOptionSelected(text) },
//                                        role = Role.RadioButton
//                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
//                                RadioButton(
//                                    selected = (text == selectedOption),
//                                    onClick = null // null recommended for accessibility with screen readers
//                                )
//                                Text(
//                                    text = text,
//                                    style = MaterialTheme.typography.bodyLarge,
//                                    modifier = Modifier.padding(start = 16.dp)
//                                )
                            }
                        }
                    }
                    CancelOkRow(
                        onDismissRequest = onDismissRequest,
                        onConfirm = onConfirm0,
                        fontSize = okFontSize
                    )
                }
        }
    }
}

@Composable
private fun Title(smallerFont: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.stub),
        modifier = modifier.padding(16.dp),
        style =
        if (smallerFont) MaterialTheme.typography.titleMedium
        else MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun SliderText(sliderState: SliderState, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.stub))
            append(":  ")
            withStyle(
                SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            ) {
                append("${sliderState.value.roundToInt()}")
            }
        }
        ,
        modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun CancelOkRow(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    fontSize: TextUnit = 24.sp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        CancelButton(fontSize = fontSize, onDismissRequest = onDismissRequest)
        OkButton(fontSize = fontSize, onConfirm = onConfirm)
    }
}
