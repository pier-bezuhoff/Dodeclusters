package domain.io

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.choose_name
import dodeclusters.composeapp.generated.resources.name
import dodeclusters.composeapp.generated.resources.ok_description
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

@Composable
actual fun SaveFileButton(
    saveData: SaveData<String>,
    buttonContent: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    onSaved: (success: Boolean?, filename: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var openDialog by remember { mutableStateOf(false) }
    var ddcName by remember { mutableStateOf(TextFieldValue(
        text = saveData.name,
        selection = TextRange(saveData.name.length) // important to insert cursor AT THE END
    )) }
    val textFieldFocusRequester = remember { FocusRequester() }

    fun onConfirm() {
        openDialog = false
        coroutineScope.launch {
            val data = saveData.copy(name = ddcName.text)
            try {
                downloadTextFile3(data.filename, data.prepareContent(ddcName.text))
                onSaved(true, data.filename)
            } catch (e: Exception) {
                onSaved(false, data.filename)
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
        // sus, AlertDialog is no supposed to allow non-specified parameters...
        AlertDialog(
            onDismissRequest = { openDialog = false },
            confirmButton = {
                TextButton(
                    onClick = ::onConfirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.primary,
                    )
                ) {
                    Text(stringResource(Res.string.ok_description))
                }
            },
            title = { Text(stringResource(Res.string.choose_name)) },
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            text = {
                OutlinedTextField(
                    value = ddcName,
                    onValueChange = { ddcName = it },
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
                    }.focusRequester(textFieldFocusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
            },
            textContentColor = MaterialTheme.colorScheme.onSurface,
        )
        LaunchedEffect(openDialog) {
            textFieldFocusRequester.requestFocus()
        }
    }
}

// showSaveFilePicker() is still experimental, cmon js bros...

// global js function
external fun encodeURIComponent(str: String): String

// saves as "download"
fun downloadTextFile1(content: String) {
    val contentType = "data:application/octet-stream"
    val uriContent = contentType + "," + encodeURIComponent(content)
    val newWindow = window.open(uriContent, "New document")
}

// saves as "download"
fun downloadTextFile2(content: String) {
    val contentType = "data:application/octet-stream"
    val uriContent = contentType + "," + encodeURIComponent(content)
    window.location.href = uriContent
}

// saves properly with given filename
fun downloadTextFile3(filename: String, content: String) {
    val blobContent = JsArray<JsAny?>()
    blobContent[0] = content.toJsString()
    // Q: why text/plain and not yaml mime or smth else?
    val file = Blob(blobContent, BlobPropertyBag("text/plain"))
    (document.createElement("a") as? HTMLAnchorElement)?.let { a ->
        val url = URL.Companion.createObjectURL(file)
        a.href = url
        a.download = filename
        document.body?.appendChild(a)
        a.click()
        document.body?.removeChild(a)
        URL.revokeObjectURL(url)
    }
}

// saves properly with given filename
fun downloadTextFile4(filename: String, content: String) {
    val contentType = "data:application/octet-stream"
    val uriContent = contentType + ";charset=utf-8," + encodeURIComponent(content)
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = uriContent
    a.download = filename
    (document.createEvent("MouseEvent") as? Event)?.let { event ->
        event.initEvent("click", bubbles = true, cancelable = true)
        a.dispatchEvent(event)
    } ?: run {
        a.click()
    }
}