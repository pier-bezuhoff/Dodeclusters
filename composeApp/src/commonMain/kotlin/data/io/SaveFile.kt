package data.io

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

data class SaveData(
    /** file's name without extension */
    val name: String,
    /** no leading dot, empty string if no extension */
    val extension: String,
    val content: (name: String) -> String,
) {
    val filename: String = if (extension.isBlank()) name else "$name.$extension"
}

@Composable
expect fun SaveFileButton(
    iconPainter: Painter,
    contentDescription: String,
    saveData: SaveData,
    exportSvgData: SaveData,
    modifier: Modifier = Modifier,
    onSaved: (successful: Boolean) -> Unit = { }
)