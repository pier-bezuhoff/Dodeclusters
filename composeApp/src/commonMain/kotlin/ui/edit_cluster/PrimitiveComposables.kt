package ui.edit_cluster

import androidx.compose.foundation.BasicTooltipDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
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
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
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
    onClick: () -> Unit
) {
    IconToggleButton(
        checked = enabled,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContentColor = LocalContentColor.current // no need for color variation since we have a diff icon
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
    onClick: () -> Unit
) {
    OutlinedIconToggleButton(
        checked = isOn,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = IconButtonDefaults.outlinedIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = if (isOn) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Icon(
            iconPainter,
            contentDescription = name,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    description: String,
    content: @Composable () -> Unit
) {
    // NOTE: ironically tooltips work much better on desktop/in browser than
    //  on android (since it requires hover vs long-press there)
    // i want to increase tooltip screen time but
    // it's hardcoded here: BasicTooltipDefaults.TooltipDuration
    // but i'd have to manually rewrite TooltipState implementation to specify custom one
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            ) { Text(description) }
        },
        state = rememberTooltipState()
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

val WindowSizeClass.isLandscape: Boolean get() =
    widthSizeClass == WindowWidthSizeClass.Expanded &&
    heightSizeClass <= WindowHeightSizeClass.Expanded ||
    widthSizeClass == WindowWidthSizeClass.Medium &&
    heightSizeClass <= WindowHeightSizeClass.Medium

operator fun WindowSizeClass.component1(): WindowWidthSizeClass =
    widthSizeClass

operator fun WindowSizeClass.component2(): WindowHeightSizeClass =
    heightSizeClass
