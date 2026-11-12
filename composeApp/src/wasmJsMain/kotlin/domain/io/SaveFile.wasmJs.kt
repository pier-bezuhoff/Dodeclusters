@file:OptIn(ExperimentalWasmJsInterop::class)

package domain.io

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.name
import dodeclusters.composeapp.generated.resources.ok
import domain.collectLatestWithLifecycle
import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

@Composable
actual fun SaveFileProcessor(
    saveData: SaveData<String>,
    saveRequests: Flow<SaveRequest>,
    onSaved: (SaveResult) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var dialogIsOpen by remember { mutableStateOf(false) }
    var nameTFV by remember { mutableStateOf(TextFieldValue(
        text = saveData.name,
        // important to insert the cursor AT THE END
        selection = TextRange(saveData.name.length),
    )) }
    val textFieldFocusRequester = remember { FocusRequester() }
    if (dialogIsOpen) {
        // BUG: for some only-god-knows-why reason when i try to use
        //  non-hard-coded or maybe longer strings here, on Android/Chrome
        //  when the text field gains focus ALL texts in the app
        //  become invisible...
        //  And this might have to do with some race condition based
        //  on number of words/characters displayed at the same time
        //  as if there is a cap...
        // ALSO: try hiding root dialog, maybe it's because of double-dialog setup
        SaveFileDialog(
            nameTFV = nameTFV,
            focusRequester = textFieldFocusRequester,
            onNameChange = { nameTFV = it },
            onDismissRequest = { dialogIsOpen = false },
            tryToDownload = {
                coroutineScope.launch {
                    tryToDownload(
                        saveData = saveData,
                        name = nameTFV.text,
                        closeDialog = { dialogIsOpen = false },
                        onSaved = onSaved,
                    )
                }
            },
        )
        DisposableEffect(dialogIsOpen) {
            textFieldFocusRequester.requestFocus()
            onDispose { }
        }
    }
    saveRequests.collectLatestWithLifecycle { saveRequest ->
        when (saveRequest) {
            SaveRequest.SAVE_AS -> {
                dialogIsOpen = true
            }
            SaveRequest.QUICK_SAVE -> coroutineScope.launch {
                tryToDownload(
                    saveData = saveData,
                    name = nameTFV.text,
                    closeDialog = { dialogIsOpen = false },
                    onSaved = onSaved,
                )
            }
        }
    }
}

private suspend fun tryToDownload(
    saveData: SaveData<String>,
    name: String,
    closeDialog: () -> Unit,
    onSaved: (SaveResult) -> Unit,
) {
    val data = saveData.copy(name = name)
    try {
        val content = withContext(Dispatchers.Default) {
            data.prepareContent(name)
        }
        downloadTextFileAsBlob(data.filename, content)
        onSaved(SaveResult.Success(
            filename = data.filename,
        ))
    } catch (e: Exception) {
        onSaved(SaveResult.Failure(
            filename = data.filename,
            error = e.message,
        ))
    }
    closeDialog()
}

@Composable
private fun SaveFileDialog(
    nameTFV: TextFieldValue,
    focusRequester: FocusRequester,
    onNameChange: (TextFieldValue) -> Unit,
    onDismissRequest: () -> Unit,
    tryToDownload: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties()
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column {
                OutlinedTextField(
                    value = nameTFV,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(Res.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions( // smart ass enter capturing
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { tryToDownload() }
                    ),
                    modifier = Modifier
                        .padding(24.dp)
                        .onKeyEvent {
                            if (it.key == Key.Enter) {
                                tryToDownload()
                                true
                            } else false
                        }.focusRequester(focusRequester),
                )
                Button(
                    onClick = { tryToDownload() },
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.End)
                    ,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
                ) {
                    Icon(
                        painterResource(Res.drawable.confirm),
                        stringResource(Res.string.ok)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(
                        stringResource(Res.string.ok),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

// showSaveFilePicker() is still experimental, cmon js bros...

// saves properly with given filename
private fun downloadTextFileAsBlob(filename: String, content: String) {
    val blobContent = JsArray<JsAny?>()
    blobContent[0] = content.toJsString()
    // Q: why text/plain and not application/yaml or smth else?
    val file = Blob(blobContent, BlobPropertyBag("text/plain"))
    val a = document.createElement("a") as HTMLAnchorElement
    val url = URL.createObjectURL(file)
    a.href = url
    a.download = filename
    document.body?.appendChild(a) // append/remove is required for firefox (allegedly)
    a.click()
    document.body?.removeChild(a)
    URL.revokeObjectURL(url)
}

// global js function
external fun encodeURIComponent(str: String): String

// saves properly with a given filename
private fun downloadTextFileAsUri(filename: String, content: String) {
    val contentType = "data:application/octet-stream"
    val uriContent = contentType + ";charset=utf-8," + encodeURIComponent(content)
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = uriContent
    a.download = filename
    document.createEvent("MouseEvent").let { event ->
        event.initEvent("click", bubbles = true, cancelable = true)
        a.dispatchEvent(event)
    }
}