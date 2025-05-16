package domain.io

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.SharedFlow
import java.io.FileNotFoundException

@Composable
actual fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    lookupData: LookupData,
    modifier: Modifier,
    openRequests: SharedFlow<Unit>?,
    onOpen: (content: String?) -> Unit
) {
    val context = LocalContext.current
    // MAYBE: use OpenDocument instead of GetContent for persistent files only (no cloud etc)
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        // Q: use launch as coroutine instead?
        val content: String? = uri?.let {
            readDdcFromUri(context, uri)
        }
        onOpen(content)
    }
    IconButton(
        onClick = {
            // NOTE: "text/plain" doesnt work for custom extensions it seems
            launcher.launch(lookupData.androidMimeType)
//            launcher.launch("application/*") // casts a wide net, including .ddc, .yaml, ..., pdf..
//        launcher.launch("application/yaml") // bugged
        },
        modifier = modifier
    ) {
        Icon(iconPainter, contentDescription, modifier)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(openRequests) {
        openRequests
            ?.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            ?.collect {
                launcher.launch(lookupData.androidMimeType)
            }
    }
}

fun readDdcFromUri(context: Context, uri: Uri): String? {
    //val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    // NOTE: uncomment the following ONLY for ACTION_OPEN_DOCUMENT (ACTION_GET_CONTENT is NOT for persistable Uri's, only for temporary ones)
    // context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    try {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        }
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
        return null
    }
}