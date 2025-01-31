package domain.io

import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

@Composable
actual fun SaveBitmapAsPngButton(
    iconPainter: Painter,
    contentDescription: String,
    saveData: SaveData<Result<ImageBitmap>>,
    modifier: Modifier,
    onSaved: (successful: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fileDialogIsOpen by remember { mutableStateOf(false) }
    var lastDir by remember { mutableStateOf<String?>(null) }
    IconButton(
        onClick = {
            fileDialogIsOpen = true
        },
        modifier = modifier,
    ) {
        Icon(iconPainter, contentDescription, modifier)
    }
    if (fileDialogIsOpen) {
        SaveFileDialog(
            defaultDir = lastDir,
            defaultFilename = saveData.filename,
            displayedExtensions = setOf(saveData.extension) + saveData.otherDisplayedExtensions
        ) { directory, filename ->
            fileDialogIsOpen = false
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    if (filename != null) {
                        if (directory != null)
                            lastDir = directory
                        val file = File(directory, filename)
                        saveData.prepareContent(file.nameWithoutExtension).fold(
                            onSuccess = { bitmap ->
                                saveBitmapToPngFile(bitmap, file)
                                onSaved(true)
                            },
                            onFailure = {
                                onSaved(false)
                            }
                        )
                    } else
                        onSaved(false)
                } catch (e: IOException) {
                    onSaved(false)
                }
            }
        }
    }
}

// works nicely
private fun saveBitmapToPngFile(bitmap: ImageBitmap, file: File) {
    val bufferedImage: BufferedImage = bitmap.toAwtImage()
    ImageIO.write(bufferedImage, "png", file)
    println("Image saved to: ${file.absolutePath}")
}