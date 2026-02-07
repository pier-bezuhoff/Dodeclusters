package domain.io

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import ui.editor.EditorViewModel

@Composable
expect fun SaveBitmapAsPngButton(
    // not a good practice to pass VM around like this, but this is the simplest way
    viewModel: EditorViewModel,
    saveData: SaveData<Unit>,
    buttonContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onSaved: (SaveResult) -> Unit = { }
)