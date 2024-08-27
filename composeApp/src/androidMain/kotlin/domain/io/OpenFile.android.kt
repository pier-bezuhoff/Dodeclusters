package domain.io

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    lookupData: LookupData,
    modifier: Modifier,
    onOpen: (content: String?) -> Unit
) {
    val context = LocalContext.current
    // MAYBE: use OpenDocument instead of GetContent for persistent files only (no cloud etc)
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
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
//        launcher.launch("application/yaml")
        },
        modifier = modifier
    ) {
        Icon(iconPainter, contentDescription, modifier)
    }
}

fun readDdcFromUri(context: Context, uri: Uri): String? {
    //val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    // NOTE: uncomment the following ONLY for ACTION_OPEN_DOCUMENT (ACTION_GET_CONTENT is NOT for persistable Uri's, only for temporary ones)
    // context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        inputStream.bufferedReader()
            .readText()
    }
}