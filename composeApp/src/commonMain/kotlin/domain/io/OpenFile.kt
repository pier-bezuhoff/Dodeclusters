package domain.io

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

// MAYBE: move into ui layer
@Composable
expect fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onOpen: (content: String?) -> Unit
)