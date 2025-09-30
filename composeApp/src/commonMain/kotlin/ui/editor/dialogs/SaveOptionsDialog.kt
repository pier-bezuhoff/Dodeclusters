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
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.editor.EditorViewModel
import ui.tools.Tool

@Composable
fun SaveOptionsDialog(
    viewModel: EditorViewModel,
    saveAsYaml: (name: String) -> String,
    exportAsSvg: (name: String) -> String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onSavedStatus: (success: Boolean?, filename: String?) -> Unit,
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
                val saveCluster = Tool.SaveCluster
                // NOTE: optimize by starting to encode bitmap when user is
                //  shown name-choosing dialog
                SaveFileButton(
                    saveData = SaveData(
                        name = saveCluster.DEFAULT_NAME,
                        extension = saveCluster.EXTENSION, // yml
                        otherDisplayedExtensions = saveCluster.otherDisplayedExtensions,
                        mimeType = saveCluster.MIME_TYPE,
                        prepareContent = saveAsYaml
                    ),
                    buttonContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                            Icon(painterResource(saveCluster.icon), stringResource(saveCluster.name), iconModifier)
                            Text(stringResource(saveCluster.description))
                        }
                    },
                    modifier = buttonModifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                ) { success, filename ->
                    println(if (success == true) "YAML saved" else "YAML not saved")
                    onSavedStatus(success, filename)
                    onConfirm()
                }
                // for one reason or another png export is quite slow on Web (desktop is quite fast, mobile is unimplemented)
                val pngExport = Tool.PngExport
                SaveBitmapAsPngButton(
                    viewModel = viewModel,
                    saveData = SaveData(
                        name = pngExport.DEFAULT_NAME,
                        extension = pngExport.EXTENSION,
                        mimeType = pngExport.MIME_TYPE,
                        prepareContent = { }
                    ),
                    buttonContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                            Icon(painterResource(pngExport.icon), stringResource(pngExport.name), iconModifier)
                            Text(stringResource(pngExport.description))
                        }
                    },
                    modifier = buttonModifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                ) { success, filename ->
                    println(if (success == true) "PNG exported" else "PNG not exported")
                    onSavedStatus(success, filename)
                    onConfirm()
                }
                val svgExport = Tool.SvgExport
                SaveFileButton(
                    saveData = SaveData(
                        name = svgExport.DEFAULT_NAME,
                        extension = svgExport.EXTENSION,
                        mimeType = svgExport.MIME_TYPE,
                        prepareContent = exportAsSvg
                    ),
                    buttonContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                            Icon(painterResource(svgExport.icon), stringResource(svgExport.name), iconModifier)
                            Text(stringResource(svgExport.description))
                        }
                    },
                    modifier = buttonModifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                ) { success, filename ->
                    println(if (success == true) "SVG exported" else "SVG not exported")
                    onSavedStatus(success, filename)
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