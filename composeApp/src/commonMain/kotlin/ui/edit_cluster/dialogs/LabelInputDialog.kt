package ui.edit_cluster.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.label_input_title
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ui.CancelOkRow
import ui.DialogTitle
import ui.hideSystemBars

@Composable
fun LabelInputDialog(
    previousLabel: String?,
    onCancel: () -> Unit,
    onConfirm: (newLabel: String?) -> Unit,
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    var label: String? by remember { mutableStateOf(previousLabel) }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .padding(32.dp)
                ,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DialogTitle(Res.string.label_input_title)
                LabelTextField(
                    label = label ?: "",
                    onNewLabel = { newLabel ->
                        label = newLabel.trim()
                            .ifBlank { null }
                    },
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                )
                CancelOkRow(
                    onDismissRequest = onCancel,
                    onConfirm = { onConfirm(label) },
                    fontSize = 18.sp,
                )
            }
        }
    }
    LaunchedEffect(dialogActions) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onConfirm(label)
            }
        }
    }
}

@Composable
private fun LabelTextField(
    label: String,
    onNewLabel: (String) -> Unit,
    placeholderStringResource: StringResource? = null,
    modifier: Modifier = Modifier,
) {
    var labelTFV by remember {
        mutableStateOf(TextFieldValue(label, TextRange(label.length)))
    }
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        labelTFV,
        onValueChange = { newTextFieldValue ->
            println(newTextFieldValue)
            labelTFV = newTextFieldValue
            val newLabel = newTextFieldValue.text
            if (newLabel != label) {
                onNewLabel(newLabel)
            }
        },
        modifier = modifier
            .focusRequester(focusRequester)
        ,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = placeholderStringResource?.let {
            { Text(stringResource(placeholderStringResource)) }
        },
        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = true),
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus(FocusDirection.Enter)
    }
}