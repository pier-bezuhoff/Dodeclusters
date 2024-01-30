package data.io

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun OpenFileButton(
    iconPainter: Painter,
    contentDescription: String,
    onOpen: (content: String?) -> Unit
) {
    val context = LocalContext.current
    // MAYBE: use OpenDocument instead of GetContent for persistent files only (no cloud etc)
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        val content: String? = uri?.let {
//            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            // NOTE: uncomment the following ONLY for ACTION_OPEN_DOCUMENT (ACTION_GET_CONTENT is NOT for persistable Uri's, only for temporary ones)
            // context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            }
        }
        onOpen(content)
    }
    IconButton(onClick = {
        launcher.launch("*/*") // NOTE: "text/plain" doesnt work for custom extensions it seems
//        launcher.launch("text/plain")
    }) {
        Icon(iconPainter, contentDescription)
    }
}