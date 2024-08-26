package domain.io

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

// MAYBE: make it less platform-specific
data class LookupData(
    // no leading dot
    val extensions: Set<String>, // used for desktop
    val htmlFileInputAccept: String, // accept attribute of <input>
    val androidMimeType: String
)

@Composable
expect fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    lookupData: LookupData,
    modifier: Modifier = Modifier,
    onOpen: (content: String?) -> Unit
)