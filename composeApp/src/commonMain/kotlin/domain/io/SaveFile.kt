package domain.io

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

@Immutable
data class SaveData<T>(
    /** file's name without extension */
    val name: String,
    /** no leading dot, empty string if no extension */
    val extension: String,
    val otherDisplayedExtensions: Set<String> = emptySet(),
    val mimeType: String = "*/*",
    val prepareContent: suspend (name: String) -> T,
) {
    val filename: String =
        if (extension.isBlank()) name
        else "$name.$extension"
}

@Composable
expect fun SaveFileButton(
    iconPainter: Painter,
    contentDescription: String,
    saveData: SaveData<String>,
    modifier: Modifier = Modifier,
    onSaved: (successful: Boolean) -> Unit = { }
)