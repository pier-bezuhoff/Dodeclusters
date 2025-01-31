package domain.io

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.github.ajalt.colormath.model.RGB
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.choose_name
import dodeclusters.composeapp.generated.resources.name
import dodeclusters.composeapp.generated.resources.ok_description
import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.khronos.webgl.Uint8ClampedArray
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.url.URL
import kotlin.math.roundToInt

// it aint working and hangs on download
@Composable
actual fun SaveBitmapAsPngButton(
    iconPainter: Painter,
    contentDescription: String,
    saveData: SaveData<Result<ImageBitmap>>,
    modifier: Modifier,
    onSaved: (successful: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var openDialog by remember { mutableStateOf(false) }
    var screenshotName by remember { mutableStateOf(
        TextFieldValue(
        text = saveData.name,
        selection = TextRange(saveData.name.length) // important to insert cursor AT THE END
    )
    ) }
    val textFieldFocusRequester = remember { FocusRequester() }

    fun onConfirm() {
        openDialog = false
        coroutineScope.launch {
            val data = saveData.copy(name = screenshotName.text)
            try {
                data.prepareContent(screenshotName.text).fold(
                    onSuccess = { bitmap ->
                        downloadBitmapAsPng(bitmap, data.filename)
                        onSaved(true)
                    },
                    onFailure = {
                        onSaved(false)
                    }
                )
            } catch (e: Exception) {
                onSaved(false)
            }
        }
    }

    IconButton(
        onClick = {
            openDialog = true
        },
        modifier = modifier,
    ) {
        Icon(iconPainter, contentDescription, modifier)
    }
    if (openDialog) {
        AlertDialog(
            onDismissRequest = { openDialog = false },
            confirmButton = {
                TextButton(onClick = ::onConfirm) {
                    Text(stringResource(Res.string.ok_description))
                }
            },
            title = { Text(stringResource(Res.string.choose_name)) },
            text = {
                OutlinedTextField(
                    value = screenshotName,
                    onValueChange = { screenshotName = it },
                    label = { Text(stringResource(Res.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions( // smart ass enter capturing
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onConfirm() }
                    ),
                    modifier = Modifier.onKeyEvent {
                        if (it.key == Key.Enter) {
                            onConfirm()
                            true
                        } else false
                    }.focusRequester(textFieldFocusRequester)
                )
            },
        )
        LaunchedEffect(openDialog) {
            textFieldFocusRequester.requestFocus()
        }
    }
}

private fun downloadBitmapAsPng(bitmap: ImageBitmap, filename: String) {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    val context = canvas.getContext("2d") as CanvasRenderingContext2D
    val imageData = context.createImageData(bitmap.width.toDouble(), bitmap.height.toDouble())
    val pixelData = imageData.data
    val pixelMap = bitmap.toPixelMap()
    for (y in 0 until pixelMap.height)
        for (x in 0 until pixelMap.width) {
            val color = pixelMap[x, y]
            val rgba = RGB(color.red, color.green, color.blue, color.alpha)
            // reference: https://developer.mozilla.org/en-US/docs/Web/API/ImageData
            val i = (x + y * pixelMap.width) * 4
            setMethodImplForUint8ClampedArray(pixelData, i + 0, rgba.redInt)
            setMethodImplForUint8ClampedArray(pixelData, i + 1, rgba.greenInt)
            setMethodImplForUint8ClampedArray(pixelData, i + 2, rgba.blueInt.coerceIn(0, 255))
            setMethodImplForUint8ClampedArray(pixelData, i + 3, rgba.alphaInt)
//            pixelData[i] = rgba.redInt.toByte() // r
//            pixelData[i + 1] = rgba.greenInt.toByte() // g
//            pixelData[i + 2] = rgba.blueInt.coerceIn(0, 255).toByte() // b
//            pixelData[i + 3] = 255.toByte() //rgba.alphaInt.toByte() // a
        }
    context.putImageData(imageData, 0.0, 0.0)
    canvas.toBlob({ blob ->
        if (blob == null) {
            println("failed to Globglogabgalab")
        } else {
            val link = document.createElement("a") as HTMLAnchorElement
            link.href = URL.createObjectURL(blob)
            link.download = filename
            document.body?.appendChild(link)
            link.click() // downloads fully transparent png
            document.body?.removeChild(link)
            URL.revokeObjectURL(link.href)
        }
    }, "image/png")
}

// default one with bytes doesnt do sht
@Suppress("UNUSED_PARAMETER")
internal fun setMethodImplForUint8ClampedArray(obj: Uint8ClampedArray, index: Int, value: Int) {
    js("obj[index] = value;")
}
