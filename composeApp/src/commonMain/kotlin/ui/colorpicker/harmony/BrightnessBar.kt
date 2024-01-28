package ui.colorpicker.harmony

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.colorpicker.HsvColor

@Composable
internal fun BrightnessBar(
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
    currentColor: HsvColor
) {
    Slider(
        modifier = modifier,
        value = currentColor.value,
        onValueChange = {
            onValueChange(it)
        },
        colors = SliderDefaults.colors(
            activeTrackColor = MaterialTheme.colors.primary,
            thumbColor = MaterialTheme.colors.primary
        )
    )
}
