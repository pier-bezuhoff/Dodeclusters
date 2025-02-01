package domain.io

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.Uint8ClampedArray
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.url.URL
import kotlin.math.roundToInt

@Composable
actual fun SaveBitmapAsPngButton(
    saveData: SaveData<Result<ImageBitmap>>,
    buttonContent: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
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
    var deferredBitmap: CompletableDeferred<Result<ImageBitmap>>? by remember { mutableStateOf(null) }

    fun onConfirm() {
        openDialog = false
        coroutineScope.launch {
            val data = saveData.copy(name = screenshotName.text)
            try {
                val result = deferredBitmap?.await() ?: data.prepareContent(screenshotName.text)
                result.fold(
                    onSuccess = { bitmap ->
                        downloadBitmapAsPng(bitmap, data.filename)
                        onSaved(true)
                    },
                    onFailure = {
                        onSaved(false)
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onSaved(false)
            }
        }
    }

    Button(
        onClick = {
            openDialog = true
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
            if (deferredBitmap == null) {
                deferredBitmap = CompletableDeferred()
                coroutineScope.launch {
                    delay(20) // a bit of time to render dialog, kinda hacky
                    try {
                        // out screenshot saving is blocking UI...
                        val result = saveData.prepareContent(saveData.name)
                        deferredBitmap?.complete(result)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        deferredBitmap = null
                    }
                }
            }
        }
    }
}

// very slow
private fun downloadBitmapAsPng(bitmap: ImageBitmap, filename: String) {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    val context = canvas.getContext("2d") as CanvasRenderingContext2D
    val imageData = context.createImageData(bitmap.width.toDouble(), bitmap.height.toDouble())
    val pixelMap = bitmap.toPixelMap()

    // reference: https://hacks.mozilla.org/2011/12/faster-canvas-pixel-manipulation-with-typed-arrays/
    // reference: https://jsfiddle.net/andrewjbaker/Fnx2w/
    val buf = ArrayBuffer(imageData.data.length)
    val buf8 = Uint8ClampedArray(buf)
    val data = Uint32Array(buf)
    val isBigEndian = testBigEndian(buf, data)
    if (isBigEndian) { // rgba
        for (y in 0 until pixelMap.height)
            for (x in 0 until pixelMap.width) {
                val color = pixelMap[x, y]
                val red = (color.red * 255).roundToInt()
                val green = (color.green * 255).roundToInt()
                val blue = (color.blue * 255).roundToInt()
                val alpha = (color.alpha * 255).roundToInt()
//                val rgba = RGB(color.red, color.green, color.blue, color.alpha)
                val i = x + y * pixelMap.width
                data[i] =
                    (red shl 24) or (green shl 16) or (blue shl 8) or alpha
//                    (rgba.redInt shl 24) or (rgba.greenInt shl 16) or (rgba.blueInt shl 8) or rgba.alphaInt
            }
    } else { // little endian => abgr
        for (y in 0 until pixelMap.height)
            for (x in 0 until pixelMap.width) {
                val color = pixelMap[x, y]
                val red = (color.red * 255).roundToInt()
                val green = (color.green * 255).roundToInt()
                val blue = (color.blue * 255).roundToInt()
                val alpha = (color.alpha * 255).roundToInt()
//                val rgba = RGB(color.red, color.green, color.blue, color.alpha)
                val i = x + y * pixelMap.width
                data[i] =
                    (alpha shl 24) or (blue shl 16) or (green shl 8) or red
//                    (rgba.alphaInt shl 24) or (rgba.blueInt shl 16) or (rgba.greenInt shl 8) or rgba.redInt
            }
    }
    imageData.data.set(buf8)
    context.putImageData(imageData, 0.0, 0.0)

//    val dataUInt8 = imageData.data
//    // this aint fast man
//    for (y in 0 until pixelMap.height)
//        for (x in 0 until pixelMap.width) {
//            val color = pixelMap[x, y]
//            val rgba = RGB(color.red, color.green, color.blue, color.alpha)
//            // reference: https://developer.mozilla.org/en-US/docs/Web/API/CanvasRenderingContext2D/createImageData
//            // reference: https://developer.mozilla.org/en-US/docs/Web/API/ImageData
//            val i = (x + y * pixelMap.width) * 4
//            setMethodImplForUint8ClampedArray(dataUInt8, i + 0, rgba.redInt)
//            setMethodImplForUint8ClampedArray(dataUInt8, i + 1, rgba.greenInt)
//            setMethodImplForUint8ClampedArray(dataUInt8, i + 2, rgba.blueInt.coerceIn(0, 255))
//            setMethodImplForUint8ClampedArray(dataUInt8, i + 3, rgba.alphaInt)
//        }
//    context.putImageData(imageData, 0.0, 0.0)
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

// default one with bytes doesnt do sht: https://youtrack.jetbrains.com/issue/KT-24583/JS-Uint8ClampedArray-declaration-unusable
@Suppress("UNUSED_PARAMETER")
internal fun setMethodImplForUint8ClampedArray(obj: Uint8ClampedArray, index: Int, value: Int) {
    js("obj[index] = value;")
}

internal fun testBigEndian(buf: ArrayBuffer, data: Uint32Array): Boolean =
    js("""{
        data[1] = 0x0a0b0c0d;   
        buf[4] === 0x0a && buf[5] === 0x0b && buf[6] === 0x0c && buf[7] === 0x0d
    }""")