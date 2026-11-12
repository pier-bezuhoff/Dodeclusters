package domain.io

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.tools.Tool

enum class SaveRequest {
    QUICK_SAVE, SAVE_AS
}

@Composable
expect fun SaveFileProcessor(
    saveData: SaveData<String>,
    saveRequests: Flow<SaveRequest>,
    onSaved: (SaveResult) -> Unit,
)

@Composable
fun SaveToolButton(
    saveTool: Tool,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier.fillMaxWidth(),
    iconModifier: Modifier = Modifier.padding(end = 12.dp),
    shape: Shape = RoundedCornerShape(4.dp),
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = containerColor,
            contentColor = contentColor,
        )
    ) {
        Row(
            modifier = rowModifier,
//            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(saveTool.icon),
                stringResource(saveTool.name),
                iconModifier
            )
            Text(stringResource(saveTool.description))
        }
    }
}