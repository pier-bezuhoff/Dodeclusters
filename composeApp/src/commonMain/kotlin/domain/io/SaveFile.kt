package domain.io

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.SharedFlow

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
    saveData: SaveData<String>,
    buttonContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    saveRequests: SharedFlow<Unit>? = null,
    onSaved: (success: Boolean?, filename: String?) -> Unit = { _, _ -> }
)