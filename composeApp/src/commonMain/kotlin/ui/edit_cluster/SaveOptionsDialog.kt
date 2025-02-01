package ui.edit_cluster

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import domain.io.SaveBitmapAsPngButton
import domain.io.SaveData
import domain.io.SaveFileButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.hideSystemBars
import ui.tools.EditClusterTool

@Composable
fun SaveOptionsDialog(
    saveAsYaml: (name: String) -> String,
    exportAsSvg: (name: String) -> String,
    exportAsPng: suspend () -> Result<ImageBitmap>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier,
            shape = RoundedCornerShape(24.dp)
        ) {
            val buttonModifier = Modifier.padding(4.dp)
            val iconModifier = Modifier.padding(end = 8.dp)
            Column(
                Modifier.padding(4.dp)
            ) {
                val rowModifier = Modifier //.padding(end = 8.dp)
                val containerColor = MaterialTheme.colorScheme.surface
                val contentColor = MaterialTheme.colorScheme.onSurface
                val saveCluster = EditClusterTool.SaveCluster
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
                ) {
                    println(if (it) "YAML saved" else "YAML not saved")
                    onConfirm()
                }
                // for one reason or another png export is quite slow on Web (desktop is quite fast, mobile is unimplemented)
                val pngExport = EditClusterTool.PngExport
                SaveBitmapAsPngButton(
                    saveData = SaveData(
                        name = pngExport.DEFAULT_NAME,
                        extension = pngExport.EXTENSION,
                        mimeType = pngExport.MIME_TYPE,
                        prepareContent = { exportAsPng() }
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
                ) {
                    println(if (it) "PNG exported" else "PNG not exported")
                    onConfirm()
                }
                val svgExport = EditClusterTool.SvgExport
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
                ) {
                    println(if (it) "SVG exported" else "SVG not exported")
                    onConfirm()
                }
            }
        }
    }
}