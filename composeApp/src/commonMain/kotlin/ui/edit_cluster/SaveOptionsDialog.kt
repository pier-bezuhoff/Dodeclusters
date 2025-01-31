package ui.edit_cluster

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
            val iconModifier = Modifier.padding(8.dp, 4.dp).size(40.dp)
            Column(
                Modifier.padding(12.dp)
            ) {
                val rowModifier = Modifier.padding(end = 8.dp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                    val saveCluster = EditClusterTool.SaveCluster
                    SaveFileButton(
                        painterResource(saveCluster.icon),
                        stringResource(saveCluster.name),
                        saveData = SaveData(
                            name = saveCluster.DEFAULT_NAME,
                            extension = saveCluster.EXTENSION, // yml
                            otherDisplayedExtensions = saveCluster.otherDisplayedExtensions,
                            mimeType = saveCluster.MIME_TYPE,
                            prepareContent = saveAsYaml
                        ),
                        modifier = iconModifier
                    ) {
                        println(if (it) "YAML saved" else "YAML not saved")
                        onConfirm()
                    }
                    Text(stringResource(saveCluster.description))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                    val pngExport = EditClusterTool.PngExport
                    SaveBitmapAsPngButton(
                        painterResource(pngExport.icon),
                        stringResource(pngExport.name),
                        saveData = SaveData(
                            name = pngExport.DEFAULT_NAME,
                            extension = pngExport.EXTENSION,
                            mimeType = pngExport.MIME_TYPE,
                            prepareContent = { exportAsPng() }
                        ),
                        modifier = iconModifier
                    ) {
                        println(if (it) "PNG exported" else "PNG not exported")
                        onConfirm()
                    }
                    Text(stringResource(pngExport.description))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
                    val svgExport = EditClusterTool.SvgExport
                    SaveFileButton(
                        painterResource(svgExport.icon),
                        stringResource(svgExport.name),
                        saveData = SaveData(
                            name = svgExport.DEFAULT_NAME,
                            extension = svgExport.EXTENSION,
                            mimeType = svgExport.MIME_TYPE,
                            prepareContent = exportAsSvg
                        ),
                        modifier = iconModifier
                    ) {
                        println(if (it) "SVG exported" else "SVG not exported")
                        onConfirm()
                    }
                    Text(stringResource(svgExport.description))
                }
            }
        }
    }
}