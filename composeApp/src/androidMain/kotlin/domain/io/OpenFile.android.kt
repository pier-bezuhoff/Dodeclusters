package domain.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import domain.collectLatestWithLifecycle
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException

@Composable
actual fun OpenFileButton(
    lookupData: LookupData,
    modifier: Modifier,
    openRequests: SharedFlow<Unit>?,
    onOpen: (content: String?, filename: String?) -> Unit,
    iconContent: @Composable (() -> Unit),
) {
    val context = LocalContext.current
    // MAYBE: use OpenDocument instead of GetContent for persistent files only (no cloud etc)
    val launcher = rememberLauncherForActivityResult(
        contract = MultipleMimeTypeGetContentContract()
//        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        // Q: use launch as coroutine instead?
        val content: String? = uri?.let {
            readFromUri(context, uri)
        }
        onOpen(content, uri?.path)
    }
    IconButton(
        onClick = {
            launcher.launch(lookupData.androidMimeTypes.toTypedArray())
        },
        modifier = modifier,
        content = iconContent,
    )
    openRequests.collectLatestWithLifecycle {
        launcher.launch(lookupData.androidMimeTypes.toTypedArray())
    }
}

private class MultipleMimeTypeGetContentContract : ActivityResultContract<Array<String>, Uri?>() {

    private val getContentContract = ActivityResultContracts.GetContent()

    override fun createIntent(context: Context, input: Array<String>): Intent {
        val baseMimeType = "*/*"
        return getContentContract.createIntent(context, baseMimeType).apply {
            putExtra(Intent.EXTRA_MIME_TYPES, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return getContentContract.parseResult(resultCode, intent)
    }
}

fun readFromUri(context: Context, uri: Uri): String? {
    //val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    // NOTE: uncomment the following ONLY for ACTION_OPEN_DOCUMENT (ACTION_GET_CONTENT is NOT for persistable Uri's, only for temporary ones)
    // context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    try {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        }
    } catch (e: IOException) { // includes FileNotFoundException
        e.printStackTrace()
        return null
    }
}