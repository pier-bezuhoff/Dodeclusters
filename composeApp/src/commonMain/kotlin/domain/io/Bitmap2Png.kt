package domain.io

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import ui.editor.EditorViewModel
import ui.editor.ScreenshotableCanvasParameters

@Composable
expect fun SaveBitmapAsPngButton(
    // not a good practice to pass VM around like this, it is used in ScreenshotableCanvas
    screenshotableCanvasParameters: ScreenshotableCanvasParameters,
    saveData: SaveData<Unit>,
    modifier: Modifier = Modifier,
    onSaved: (SaveResult) -> Unit = { },
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    shape: Shape = RoundedCornerShape(4.dp),
    buttonContent: @Composable () -> Unit,
)