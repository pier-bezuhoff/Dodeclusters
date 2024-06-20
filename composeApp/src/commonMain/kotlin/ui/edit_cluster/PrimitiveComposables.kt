package ui.edit_cluster

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

// MAYBE: pass a Modifier
@Composable
fun SimpleButton(
    iconPainter: Painter,
    name: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
    ) {
        Icon(iconPainter, contentDescription = name)
    }
}

@Composable
fun DisableableButton(
    iconPainter: Painter,
    name: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
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
    onClick: () -> Unit
) {
    IconToggleButton(
        checked = enabled,
        onCheckedChange = { onClick() },
    ) {
        Crossfade(enabled) { targetChecked ->
            Icon(
                if (targetChecked) iconPainter
                else disabledIconPainter,
                contentDescription = name
            )
        }
    }
//    Spacer(Modifier.fillMaxHeight().width(8.dp)) // horizontal margin
}

@Composable
fun OnOffButton(
    iconPainter: Painter,
    name: String,
    isOn: Boolean,
    onClick: () -> Unit
) {
    // Crossfade/AnimatedContent dont work for w/e reason (mb cuz VM is caught in the closure)
    val backgroundColor = if (isOn)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isOn)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    IconToggleButton(
        checked = isOn,
        onCheckedChange = { onClick() },
        modifier = Modifier.background(backgroundColor)
    ) {
        Icon(iconPainter, contentDescription = name, tint = contentColor)
    }
    // why not jusp h-padding?
    Spacer(Modifier.fillMaxHeight().width(8.dp)) // horizontal margin
}
