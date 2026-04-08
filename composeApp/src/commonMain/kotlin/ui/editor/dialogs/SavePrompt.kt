package ui.editor.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.dont_save
import dodeclusters.composeapp.generated.resources.save
import dodeclusters.composeapp.generated.resources.save_prompt_title
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.stringResource
import ui.DialogTitle

@Composable
fun SavePromptDialog(
    description: String,
    onCancel: () -> Unit,
    onDontSave: () -> Unit,
    onSave: () -> Unit,
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            modifier = Modifier,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
//                DialogTitle(Res.string.save_prompt_title,
//                    modifier = Modifier.align(Alignment.CenterHorizontally),
//                )
                Text(description,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    TextButton(
                        onClick = onDontSave,
                        modifier = Modifier.padding(4.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = CircleShape,
                    ) {
                        Text(stringResource(Res.string.dont_save))
                    }
                    TextButton(
                        onClick = onSave,
                        modifier = Modifier.padding(4.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = CircleShape,
                    ) {
                        Text(stringResource(Res.string.save))
                    }
                }
            }
        }
    }
    LaunchedEffect(dialogActions) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onSave()
            }
        }
    }
}