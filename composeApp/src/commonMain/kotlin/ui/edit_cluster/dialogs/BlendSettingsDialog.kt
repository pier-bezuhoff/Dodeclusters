@file:OptIn(ExperimentalMaterial3Api::class)

package ui.edit_cluster.dialogs

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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.blend_settings_opacity_prompt
import dodeclusters.composeapp.generated.resources.blend_settings_title
import domain.BlendModeType
import domain.formatDecimals
import org.jetbrains.compose.resources.stringResource
import ui.CancelOkRow
import ui.DialogTitle
import ui.LabelColonBigValue
import ui.component1
import ui.component2
import ui.hideSystemBars

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun BlendSettingsDialog(
    currentOpacity: Float,
    currentBlendModeType: BlendModeType,
    onDismissRequest: () -> Unit,
    onConfirm: (newOpacity: Float, newBlendModeType: BlendModeType) -> Unit,
) {
    val sliderState = remember { SliderState(
        value = currentOpacity,
        valueRange = 0f .. 1f,
    ) }
    var blendModeType by remember { mutableStateOf(currentBlendModeType) }
    val (widthClass, heightClass) = calculateWindowSizeClass()
    val compactHeight = heightClass == WindowHeightSizeClass.Compact
    val okFontSize =
        if (widthClass == WindowWidthSizeClass.Compact)
            18.sp
        else 24.sp
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
                Modifier.verticalScroll(rememberScrollState())
                ,
                horizontalAlignment = Alignment.Start
            ) {
                DialogTitle(Res.string.blend_settings_title, smallerFont = false, Modifier.align(Alignment.CenterHorizontally))
                LabelColonBigValue(
                    value = sliderState.value.formatDecimals(3, showTrailingZeroes = false),
                    labelResource = Res.string.blend_settings_opacity_prompt
                )
                Slider(sliderState, Modifier.padding(16.dp))
                // MAYBE: add explanatory label "Blend mode:"
                Column(Modifier.selectableGroup()) {
                    BlendModeType.entries.forEach { blendModeTypeVariant ->
                        Row(Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = blendModeType == blendModeTypeVariant,
                                onClick = { blendModeType = blendModeTypeVariant },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = blendModeType == blendModeTypeVariant,
                                onClick = null // null recommended for accessibility with screen readers
                            )
                            Text(
                                text = stringResource(blendModeTypeVariant.nameStringResource),
                                modifier = Modifier.padding(start = 16.dp),
                                color =
                                    if (blendModeType == blendModeTypeVariant)
                                        MaterialTheme.colorScheme.primary
                                    else Color.Unspecified,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                CancelOkRow(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { onConfirm(sliderState.value, blendModeType) },
                    fontSize = okFontSize
                )
            }
        }
    }
}