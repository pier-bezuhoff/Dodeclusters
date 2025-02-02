package domain.io

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import ui.edit_cluster.EditClusterViewModel
import ui.edit_cluster.ScreenshotableCanvas
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

@Composable
actual fun SaveBitmapAsPngButton(
    viewModel: EditClusterViewModel,
    saveData: SaveData<Unit>,
    buttonContent: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    onSaved: (success: Boolean?, filename: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fileDialogIsOpen by remember { mutableStateOf(false) }
    var lastDir by remember { mutableStateOf<String?>(null) }
    val bitmapFlow: MutableSharedFlow<ImageBitmap> = remember { MutableSharedFlow(replay = 1) }
    val bitmapState: State<ImageBitmap?> = bitmapFlow.collectAsState(null)
    Button(
        onClick = {
            fileDialogIsOpen = true
        },
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = containerColor,
            contentColor = contentColor,
        )
    ) {
        buttonContent()
    }
    if (fileDialogIsOpen) {
        if (bitmapState.value == null) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ScreenshotableCanvas(viewModel, bitmapFlow)
            }
        } else {
            SaveFileDialog(
                defaultDir = lastDir,
                defaultFilename = saveData.filename,
                displayedExtensions = setOf(saveData.extension) + saveData.otherDisplayedExtensions
            ) { directory, filename ->
                fileDialogIsOpen = false
                coroutineScope.launch(Dispatchers.IO) {
                    if (filename != null) {
                        if (directory != null)
                            lastDir = directory
                        val file = File(directory, filename)
                        bitmapFlow.collect { bitmap ->
                            try {
                                saveBitmapToPngFile(bitmap, file)
                                onSaved(true, file.absolutePath)
                            } catch (e: IOException) {
                                onSaved(false, file.absolutePath)
                            } finally {
                                coroutineScope.cancel()
                            }
                        }
                    } else
                        onSaved(null, null)
                }
            }
        }
    }
}

// works nicely
private fun saveBitmapToPngFile(bitmap: ImageBitmap, file: File) {
    val bufferedImage: BufferedImage = bitmap.toAwtImage()
    ImageIO.write(bufferedImage, "png", file)
    println("Image saved to ${file.absolutePath}")
}