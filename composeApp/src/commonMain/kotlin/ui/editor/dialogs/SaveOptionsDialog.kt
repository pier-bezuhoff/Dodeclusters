package ui.editor.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.copy
import domain.LoadingState
import domain.io.DdcSharing
import domain.io.DdcV4
import domain.io.SaveBitmapAsPngButton
import domain.io.SaveData
import domain.io.SaveFileButton
import domain.io.SaveResult
import domain.io.SharedId
import domain.io.SharedIdAndOwnedStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.LoadingOverlay
import ui.editor.EditorViewModel
import ui.tools.Tool

// TODO:  distinct (fast) save and save as options
//  there is already saveRequests flow as arg of SaveFileButton
@Composable
fun SaveOptionsDialog(
    viewModel: EditorViewModel,
    ddcSharing: DdcSharing? = null,
    saveAsYaml: (name: String) -> String,
    exportAsSvg: (name: String) -> String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onSaved: (SaveResult) -> Unit,
    lastSaveResult: SaveResult? = null,
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var loadingShared: LoadingState<SharedIdAndOwnedStatus>? by remember { mutableStateOf(
        ddcSharing?.shared?.let { LoadingState.Completed(it) }
    ) }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            modifier = Modifier,
            shape = MaterialTheme.shapes.large,
        ) {
            Box {
                val buttonModifier = Modifier//.padding(4.dp)
                val iconModifier = Modifier.padding(end = 12.dp)
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .width(IntrinsicSize.Max)
                        .padding(16.dp)
                ) {
                    val rowModifier = Modifier.fillMaxWidth() //.padding(end = 8.dp)
                    val containerColor = MaterialTheme.colorScheme.surface
                    val contentColor = MaterialTheme.colorScheme.onSurface
                    if (ddcSharing != null) {
                        ShareNewButton(
                            modifier = buttonModifier,
                            rowModifier = rowModifier,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            coroutineScope = coroutineScope,
                            shareNew = { ddcSharing.shareNewDdc(saveAsYaml(DdcV4.DEFAULT_NAME)) },
                            setLoadingShared = { loadingShared = it },
                            setShared = { ddcSharing.shared = it },
                            closeDialog = onConfirm,
                        )
                        val shared = ddcSharing.shared
                        if (shared != null) {
                            val link = remember(shared) { ddcSharing.formatLink(shared.first) }
                            SharedLink(
                                link = link,
                                coroutineScope = coroutineScope,
                            )
                        }
                        if (shared?.second == true) {
                            OverwriteSharedButton(
                                modifier = buttonModifier,
                                rowModifier = rowModifier,
                                containerColor = containerColor,
                                contentColor = contentColor,
                                coroutineScope = coroutineScope,
                                overwriteShared = {
                                    ddcSharing.overwriteSharedDdc(
                                        sharedId = shared.first,
                                        content = saveAsYaml(DdcV4.DEFAULT_NAME)
                                    )
                                },
                                setLoadingShared = { loadingShared = it },
                                setShared = { ddcSharing.shared = it },
                                closeDialog = onConfirm,
                            )
                        }
                        HorizontalDivider()
                    }
                    // NOTE: optimize by starting to encode bitmap when the user is
                    //  shown name-choosing dialog
                    SaveFileButton(
                        saveData = SaveData( // name.yml
                            filename = lastSaveResult?.filename ?: Tool.SaveCluster.DEFAULT_FILENAME,
                            lastDir = lastSaveResult?.dir,
                            uri = lastSaveResult?.uri,
                            otherDisplayedExtensions = Tool.SaveCluster.otherDisplayedExtensions,
                            mimeType = Tool.SaveCluster.MIME_TYPE,
                            prepareContent = saveAsYaml
                        ),
                        buttonContent = {
                            Row(
                                modifier = rowModifier,
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                            uri = lastSaveResult?.uri,
                            mimeType = Tool.PngExport.MIME_TYPE,
                            prepareContent = { }
                        ),
                        buttonContent = {
                            Row(
                                modifier = rowModifier,
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                            uri = lastSaveResult?.uri,
                            mimeType = Tool.SvgExport.MIME_TYPE,
                            prepareContent = exportAsSvg
                        ),
                        buttonContent = {
                            Row(
                                modifier = rowModifier,
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                when (val loading = loadingShared) {
                    is LoadingState.InProgress ->
                        LoadingOverlay(loading)
                    else -> {}
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

@Composable
private fun ShareNewButton(
    modifier: Modifier,
    rowModifier: Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    containerColor: Color,
    contentColor: Color,
    coroutineScope: CoroutineScope,
    shareNew: suspend () -> SharedId?,
    setLoadingShared: (LoadingState<SharedIdAndOwnedStatus>?) -> Unit,
    setShared: (SharedIdAndOwnedStatus?) -> Unit,
    closeDialog: () -> Unit,
) {
    Button(
        onClick = {
            coroutineScope.launch {
                setLoadingShared(LoadingState.InProgress("Uploading new cluster..."))
                val sharedId = shareNew()
                if (sharedId == null) {
                    setLoadingShared(LoadingState.Error(Error("Failed to upload.")))
                } else {
                    val newShared = Pair(sharedId, true)
                    setLoadingShared(LoadingState.Completed(newShared))
                    setShared(newShared)
                    // we dont want to close immediately cuz the user needs to copy the link
//                    closeDialog()
                }
            }
        },
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = containerColor,
            contentColor = contentColor,
        )
    ) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Share online")
        }
    }
}

@Composable
private fun OverwriteSharedButton(
    modifier: Modifier,
    rowModifier: Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    containerColor: Color,
    contentColor: Color,
    coroutineScope: CoroutineScope,
    overwriteShared: suspend () -> SharedId?,
    setLoadingShared: (LoadingState<SharedIdAndOwnedStatus>?) -> Unit,
    setShared: (SharedIdAndOwnedStatus?) -> Unit,
    closeDialog: () -> Unit,
) {
    Button( // overwrite shared
        onClick = {
            coroutineScope.launch {
                setLoadingShared(
                    LoadingState.InProgress("Uploading and overwriting shared cluster")
                )
                val sharedId = overwriteShared()
                if (sharedId == null) {
                    setLoadingShared(LoadingState.Error(Error("Failed to overwrite.")))
                } else {
                    val newShared = Pair(sharedId, true)
                    setLoadingShared(LoadingState.Completed(newShared))
                    setShared(newShared)
                    closeDialog()
                }
            }
        },
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = containerColor,
            contentColor = contentColor,
        )
    ) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Overwrite shared")
        }
    }
}

@Composable
private fun SharedLink(
    link: String,
    coroutineScope: CoroutineScope,
) {
//    val clipboard = LocalClipboard.current // clipboard seems not finished for KMP
    val clipboardManager = LocalClipboardManager.current
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionContainer {
            Text(
                text = link,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Visible,
            ) // TODO: add link icon and copy-to-clipboard button
        }
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(text = link))
            },
            colors = IconButtonDefaults.iconButtonColors().copy(
                contentColor = MaterialTheme.colorScheme.secondary,
            )
        ) {
            Icon(painterResource(Res.drawable.copy), "Copy link")
        }
    }
}