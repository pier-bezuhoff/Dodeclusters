package data.io

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

// MAYBE: move into ui layer
@Composable
expect fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    onOpen: (content: String?) -> Unit
)