package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.cancel
import dodeclusters.composeapp.generated.resources.cancel_name
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.ok_description
import dodeclusters.composeapp.generated.resources.ok_name
import domain.formatDecimals
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.tools.Tool

@Composable
fun SimpleButton(
    iconPainter: Painter,
    name: String,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    containerColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = tint,
        ),
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Icon(
            iconPainter,
            contentDescription = name,
            modifier = iconModifier,
        )
    }
}

@Composable
inline fun <reified T> SimpleToolButton(
    tool: T,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    tint: Color =
        if (tool is Tool.Tinted) tool.tint
        else LocalContentColor.current,
    crossinline onClick: (tool: T) -> Unit
) where T : Tool = SimpleButton(
    iconPainter = painterResource(tool.icon),
    name = stringResource(tool.name),
    modifier = modifier,
    iconModifier = iconModifier,
    tint = tint,
    onClick = { onClick(tool) },
)

@Composable
fun SimpleFilledButton(
    iconPainter: Painter,
    name: String,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    contentColor: Color = LocalContentColor.current,
    containerColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Icon(
            iconPainter,
            contentDescription = name,
            modifier = iconModifier,
        )
    }
}

@Composable
fun DisableableButton(
    iconPainter: Painter,
    name: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors().copy(contentColor = tint),
        enabled = enabled,
    ) {
        Icon(
            iconPainter,
            contentDescription = name,
            // NOTE: using the same modifier for Icon can have
            //  drastic consequences
            modifier = modifier
        )
    }
}

@Composable
fun TwoIconButton(
    iconPainter: Painter,
    disabledIconPainter: Painter,
    name: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    IconToggleButton(
        checked = enabled,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = tint,
            disabledContentColor = tint,
            checkedContentColor = tint, // no need for color variation since we have a diff icon
        )
    ) {
        Icon(
            if (enabled) iconPainter
            else disabledIconPainter,
            contentDescription = name,
            modifier = iconModifier
        )
    }
}

/** 3 states:
 * 1. [enabled]=true & [alternative]=false
 * 2. [enabled]=true & [alternative]=true
 * 3. [enabled]=false & [alternative]={unspecified}
 * */
@Composable
fun ThreeIconButton(
    iconPainter: Painter,
    alternativeIconPainter: Painter,
    disabledIconPainter: Painter,
    name: String,
    enabled: Boolean,
    alternative: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    IconToggleButton(
        checked = enabled,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContentColor = tint // no need for color variation since we have a diff icon
        )
    ) {
        Icon(
            if (enabled) {
                if (alternative) alternativeIconPainter
                else iconPainter
            }
            else disabledIconPainter,
            contentDescription = name,
            modifier = modifier
        )
    }
}

@Composable
fun OnOffButton(
    iconPainter: Painter,
    name: String,
    isOn: Boolean,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    contentColor: Color = LocalContentColor.current,
    checkedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    disabledContentColor: Color = Color.Unspecified,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    checkedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    disabledContainerColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    OutlinedIconToggleButton(
        checked = isOn,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = IconButtonDefaults.outlinedIconToggleButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,//MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = checkedContainerColor,
            checkedContentColor = checkedContentColor,
            disabledContentColor = disabledContainerColor,
        ).let {
            if (disabledContentColor != Color.Unspecified)
                it.copy(disabledContentColor = disabledContentColor)
            else it
        },
        border = if (isOn) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Icon(
            iconPainter,
            contentDescription = name,
            modifier = iconModifier,
        )
    }
}

// BUG: on Android this triggers exit from the immersive mode
//  because of popup realization, more here:
// https://androidx.tech/artifacts/compose.material3/material3-android/1.3.0-source/androidMain/androidx/compose/material3/internal/BasicTooltip.android.kt.html
/**
 * @param[tooltipDuration] in milliseconds
 * */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    description: String,
    tooltipDuration: Long = 5_000,
    content: @Composable () -> Unit
) {
    // NOTE: ironically tooltips work much better on desktop/in browser than
    //  on android (since it requires hover vs long-press there)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(
            8.dp
        ),
        tooltip = {
            PlainTooltip(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            ) { Text(description) }
        },
        state = rememberMyTooltipState(tooltipDuration = tooltipDuration),
    ) {
        content()
    }
}

@Composable
fun OkButton(
    fontSize: TextUnit = 24.sp,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
) {
    Button(
        onClick = { onConfirm() },
        modifier = modifier.padding(4.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shape = CircleShape,
    ) {
        Icon(painterResource(Res.drawable.confirm), stringResource(Res.string.ok_description))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.ok_name), fontSize = fontSize)
    }
}

@Composable
fun CancelButton(
    fontSize: TextUnit = 24.sp,
    noText: Boolean = false,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
) {
    OutlinedButton(
        onClick = { onDismissRequest() },
        modifier = modifier.padding(4.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shape = CircleShape,
    ) {
        Icon(painterResource(Res.drawable.cancel), stringResource(Res.string.cancel_name))
        if (!noText) {
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.cancel_name), fontSize = fontSize)
        }
    }
}

@Composable
fun CancelOkRow(
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

@Composable
fun DialogTitle(
    titleStringResource: StringResource,
    smallerFont: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(titleStringResource),
        modifier = modifier.padding(16.dp),
        style =
        if (smallerFont) MaterialTheme.typography.titleMedium
        else MaterialTheme.typography.titleLarge,
    )
}

@Composable
fun PreTextFieldLabel(
    stringResource: StringResource,
    smallerFont: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(stringResource))
            append(":  ")
        },
        modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
        style =
            if (smallerFont) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun LabelColonBigValue(
    value: String,
    labelResource: StringResource,
    modifier: Modifier = Modifier
) {
    Text(
        buildAnnotatedString {
            append(stringResource(labelResource))
            append(":  ")
            withStyle(
                SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            ) {
                append(value)
            }
        }
        ,
        modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun FloatTextField(
    value: Float,
    onNewValue: (newValue: Float) -> Unit,
    placeholderStringResource: StringResource? = null,
    suffixStringResource: StringResource? = null,
    nFractionalDigits: Int = 2,
    modifier: Modifier = Modifier
) {
    val s = value.formatDecimals(nFractionalDigits, showTrailingZeroes = false)
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(s, TextRange(s.length)))
    }
    OutlinedTextField(
        textFieldValue,
        onValueChange = { newTextFieldValue ->
            textFieldValue = newTextFieldValue
            val updatedValue = textFieldValue.text.toFloatOrNull()
            if (updatedValue != null && updatedValue != value) {
                onNewValue(updatedValue)
            }
        },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = placeholderStringResource?.let { { Text(stringResource(placeholderStringResource)) } },
        suffix = suffixStringResource?.let { { Text(stringResource(suffixStringResource)) } },
        isError = textFieldValue.text.toFloatOrNull()?.let { false } ?: true,
        singleLine = true,
    )
}

@Composable
fun DoubleTextField(
    value: Double,
    onNewValue: (newValue: Double) -> Unit,
    placeholderStringResource: StringResource? = null,
    suffixStringResource: StringResource? = null,
    nFractionalDigits: Int = 2,
    modifier: Modifier = Modifier
) {
    val s = value.formatDecimals(nFractionalDigits, showTrailingZeroes = false)
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(s, TextRange(s.length)))
    }
    OutlinedTextField(
        textFieldValue,
        onValueChange = { newTextFieldValue ->
            textFieldValue = newTextFieldValue
            val updatedValue = textFieldValue.text.toDoubleOrNull()
            if (updatedValue != null && updatedValue != value) {
                onNewValue(updatedValue)
            }
        },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = placeholderStringResource?.let { { Text(stringResource(placeholderStringResource)) } },
        suffix = suffixStringResource?.let { { Text(stringResource(suffixStringResource)) } },
        isError = textFieldValue.text.toDoubleOrNull()?.let { false } ?: true,
        singleLine = true,
    )
}

@Composable
fun IntTextField(
    value: Int,
    onNewValue: (newValue: Int) -> Unit,
    placeholderStringResource: StringResource? = null,
    suffixStringResource: StringResource? = null,
    valueValidator: (value: Int) -> Boolean = { it >= 0 },
    modifier: Modifier = Modifier
) {
    val s = value.toString()
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(s, TextRange(s.length)))
    }
    OutlinedTextField(
        textFieldValue,
        onValueChange = { newTextFieldValue ->
            textFieldValue = newTextFieldValue
            val newValue = textFieldValue.text.toIntOrNull()
            if (newValue != null && newValue != value && valueValidator(newValue)) {
                onNewValue(newValue)
            }
        },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = placeholderStringResource?.let { { Text(stringResource(placeholderStringResource)) } },
        suffix = suffixStringResource?.let { { Text(stringResource(suffixStringResource)) } },
        isError = textFieldValue.text.toIntOrNull()?.let { !valueValidator(it) } ?: true,
        singleLine = true,
    )
}

// reference: https://stackoverflow.com/a/71129399
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalSlider(
    sliderState: SliderState,
    modifier: Modifier = Modifier, // often .weight(1f)
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
) {
    Slider(
        sliderState,
        modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }.layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints( // transposed constraints
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            } //.size(w,h)
        ,
        enabled = enabled,
        colors = colors,
    )
}

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f .. 1f,
    steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors(),
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }.layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints( // transposed constraints
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            } //.size(w,h)
        ,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
    )
}
