package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// MAYBE: pass a Modifier
@Composable
fun SimpleButton(
    iconPainter: Painter,
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            iconPainter,
            contentDescription = name,
            modifier = Modifier,
            tint = tint
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
            if (enabled) iconPainter
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
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    OutlinedIconToggleButton(
        checked = isOn,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = IconButtonDefaults.outlinedIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = tint,//MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = if (isOn) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Icon(
            iconPainter,
            contentDescription = name,
            modifier = modifier,
        )
    }
}

// BUG: on Android this triggers exit from the immersive mode
//  because of popup realization, more here:
// https://androidx.tech/artifacts/compose.material3/material3-android/1.3.0-source/androidMain/androidx/compose/material3/internal/BasicTooltip.android.kt.html
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    description: String,
    /** in milliseconds */
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
        modifier = modifier.padding(8.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
    ) {
        Icon(painterResource(Res.drawable.confirm), stringResource(Res.string.ok_description))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.ok_name), fontSize = fontSize)
    }
}

@Composable
fun CancelButton(
    fontSize: TextUnit = 24.sp,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
) {
    OutlinedButton(
        onClick = { onDismissRequest() },
        modifier = modifier.padding(8.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
    ) {
        Icon(painterResource(Res.drawable.cancel), stringResource(Res.string.cancel_name))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.cancel_name), fontSize = fontSize)
    }
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
                    Constraints(
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

val WindowSizeClass.isLandscape: Boolean get() =
    widthSizeClass == WindowWidthSizeClass.Expanded &&
    heightSizeClass <= WindowHeightSizeClass.Expanded ||
    widthSizeClass == WindowWidthSizeClass.Medium &&
    heightSizeClass < WindowHeightSizeClass.Medium
// (Medium, Medium) is the size in portrait tablet browser

val WindowSizeClass.isCompact: Boolean get() =
    widthSizeClass == WindowWidthSizeClass.Compact ||
    heightSizeClass == WindowHeightSizeClass.Compact

operator fun WindowSizeClass.component1(): WindowWidthSizeClass =
    widthSizeClass

operator fun WindowSizeClass.component2(): WindowHeightSizeClass =
    heightSizeClass
