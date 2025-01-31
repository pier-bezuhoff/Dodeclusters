package domain.io

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun SaveBitmapAsPngButton(
    iconPainter: Painter,
    contentDescription: String,
    saveData: SaveData<Result<ImageBitmap>>,
    modifier: Modifier = Modifier,
    onSaved: (successful: Boolean) -> Unit = { }
)