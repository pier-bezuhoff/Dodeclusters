package ui.editor.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import domain.io.SaveBitmapAsPngButton
import domain.io.SaveData
import domain.io.SaveFileButton
import domain.io.SaveResult
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.editor.EditorViewModel
import ui.tools.Tool

// TODO:  distinct (fast) save and save as options
@Composable
fun SaveOptionsDialog(
    viewModel: EditorViewModel,
    saveAsYaml: (name: String) -> String,
    exportAsSvg: (name: String) -> String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onSaved: (SaveResult) -> Unit,
    lastSaveResult: SaveResult? = null,
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
            val buttonModifier = Modifier//.padding(4.dp)
            val iconModifier = Modifier.padding(end = 12.dp)
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .padding(16.dp)
            ) {
                val rowModifier = Modifier.fillMaxWidth() //.padding(end = 8.dp)
                val containerColor = MaterialTheme.colorScheme.surface
                val contentColor = MaterialTheme.colorScheme.onSurface
                // NOTE: optimize by starting to encode bitmap when the user is
                //  shown name-choosing dialog
                SaveFileButton(
                    saveData = SaveData( // name.yml
                        filename = lastSaveResult?.filename ?: Tool.SaveCluster.DEFAULT_FILENAME,
                        lastDir = lastSaveResult?.dir,
                        otherDisplayedExtensions = Tool.SaveCluster.otherDisplayedExtensions,
                        mimeType = Tool.SaveCluster.MIME_TYPE,
                        prepareContent = saveAsYaml
                    ),
                    buttonContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                            Icon(
                                painterResource(Tool.SaveCluster.icon),
                                stringResource(Tool.SaveCluster.name),
                                iconModifier
                            )
                            Text(stringResource(Tool.SaveCluster.description))
                        }
                    },
                    modifier = buttonModifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                ) { saveResult ->
                    println(if (saveResult.isSuccess) "YAML saved" else "YAML not saved")
                    onSaved(saveResult)
                    onConfirm()
                }
                // for one reason or another png export is quite slow on Web (desktop is quite fast,
                // mobile is unimplemented)
                SaveBitmapAsPngButton(
                    viewModel = viewModel,
                    saveData = SaveData(
                        filename = lastSaveResult?.filename ?: Tool.PngExport.DEFAULT_FILENAME,
                        lastDir = lastSaveResult?.dir,
                        mimeType = Tool.PngExport.MIME_TYPE,
                        prepareContent = { }
                    ),
                    buttonContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                            Icon(painterResource(Tool.PngExport.icon), stringResource(Tool.PngExport.name), iconModifier)
                            Text(stringResource(Tool.PngExport.description))
                        }
                    },
                    modifier = buttonModifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                ) { saveResult ->
                    println(if (saveResult.isSuccess) "PNG exported" else "PNG not exported")
                    onSaved(saveResult)
                    onConfirm()
                }
                SaveFileButton(
                    saveData = SaveData(
                        filename = lastSaveResult?.filename ?: Tool.SvgExport.DEFAULT_FILENAME,
                        lastDir = lastSaveResult?.dir,
                        mimeType = Tool.SvgExport.MIME_TYPE,
                        prepareContent = exportAsSvg
                    ),
                    buttonContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                            Icon(
                                painterResource(Tool.SvgExport.icon),
                                stringResource(Tool.SvgExport.name),
                                iconModifier,
                            )
                            Text(stringResource(Tool.SvgExport.description))
                        }
                    },
                    modifier = buttonModifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                ) { saveResult ->
                    println(if (saveResult.isSuccess) "SVG exported" else "SVG not exported")
                    onSaved(saveResult)
                    onConfirm()
                }
            }
        }
    }
    LaunchedEffect(dialogActions) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onConfirm()
            }
        }
    }
}