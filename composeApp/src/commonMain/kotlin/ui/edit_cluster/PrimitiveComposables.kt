package ui.edit_cluster

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

// MAYBE: pass a Modifier
@Composable
fun SimpleButton(
    iconPainter: Painter,
    name: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(iconPainter, contentDescription = name)
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
        Icon(iconPainter, contentDescription = name)
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
            contentDescription = name
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
        Icon(iconPainter, contentDescription = name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    description: String,
    content: @Composable () -> Unit
) {
    // i want to increase tooltip screen time but cant find where
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