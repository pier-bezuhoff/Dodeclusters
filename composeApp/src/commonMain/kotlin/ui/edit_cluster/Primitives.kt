package ui.edit_cluster

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp

@Composable
fun CategoryButton(
) {
    1
    Modifier.onPreviewKeyEvent {
        Key.Delete
        Key.Backspace
        it.isCtrlPressed
    }
}

// MAYBE: pass Modifier
@Composable
fun SimpleButton(
    iconPainter: Painter,
    name: String,
    highlighted: Boolean = false,
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
    disabled: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = !disabled
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
    highlighted: Boolean = false,
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
    enabled: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    // Crossfade/AnimatedContent dont work for w/e reason (mb cuz VM is caught in the closure)
    IconToggleButton(
        checked = enabled,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .background(
                if (enabled)
                    MaterialTheme.colors.primaryVariant
                else
                    MaterialTheme.colors.primary,
            )
    ) {
        Icon(iconPainter, contentDescription = name)
    }
    Spacer(Modifier.fillMaxHeight().width(8.dp)) // horizontal margin
}
