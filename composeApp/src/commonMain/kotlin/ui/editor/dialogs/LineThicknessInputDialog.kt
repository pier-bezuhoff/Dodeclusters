package ui.editor.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.line_thickness
import dodeclusters.composeapp.generated.resources.line_thickness_dialog_title
import dodeclusters.composeapp.generated.resources.stub
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.painterResource
import ui.CancelOkRow
import ui.DialogTitle
import ui.FloatTextField
import ui.theme.ColorTheme
import ui.theme.DodeclustersTheme
import kotlin.math.ceil

private const val MIN_THICKNESS = 1f
private const val MAX_THICKNESS = 50f
private const val THICKNESS_DISCRETIZATION = 1f
private val THICKNESS_RANGE = MIN_THICKNESS .. MAX_THICKNESS
private val N_THICKNESS_DISCRETIZATION_STEPS =
    ceil((MAX_THICKNESS - MIN_THICKNESS)/THICKNESS_DISCRETIZATION).toInt()

// TODO: extract non-null default
// ideally we want live preview of thickness changes
@Composable
fun LineThicknessInputDialog(
    previousThickness: Float?,
    onCancel: () -> Unit = {},
    onConfirm: (newThickness: Float?) -> Unit = {},
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    var thickness: Float? by remember { mutableStateOf(previousThickness) }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        LineThicknessInputScreen(
            thickness = thickness,
            setThickness = { thickness = it },
            onCancel = onCancel,
            onOk = { onConfirm(thickness) },

        )
    }
    LaunchedEffect(dialogActions) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onConfirm(thickness)
            }
        }
    }
}

@Composable
private fun LineThicknessInputScreen(
    thickness: Float?,
    modifier: Modifier = Modifier,
    setThickness: (Float) -> Unit = {},
    onCancel: () -> Unit = {},
    onOk: () -> Unit = {},
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .padding(32.dp)
            ,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DialogTitle(Res.string.line_thickness_dialog_title)
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(Res.drawable.line_thickness), null)
                Spacer(Modifier.width(16.dp))
                FloatTextField(
                    value = thickness ?: 0f,
                    onNewValue = setThickness,
                    validateValue = { it > 0f },
                    modifier = Modifier
                        .widthIn(max = 100.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            // maybe log slider
            Slider(
                value = thickness ?: 0f,
                onValueChange = {
                    setThickness(it)
                },
                modifier = Modifier,
                valueRange = THICKNESS_RANGE,
                steps = N_THICKNESS_DISCRETIZATION_STEPS - 1,
            )
            CancelOkRow(
                onCancel = onCancel,
                onOk = onOk,
            )
        }
    }
}

@Preview
@Composable
private fun LineThicknessInputScreenPreview() {
    DodeclustersTheme(ColorTheme.DARK) {
        LineThicknessInputScreen(
            thickness = 2f,
        )
    }
}

