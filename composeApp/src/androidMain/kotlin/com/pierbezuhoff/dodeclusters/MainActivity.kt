package com.pierbezuhoff.dodeclusters

import App
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import data.io.readDdcFromUri
import java.io.FileNotFoundException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // this horrendous mess looks smart but is actually very stupid
            var ddcContent by remember { mutableStateOf<String?>(null) }
            val anchorUri = remember { mutableStateOf<Uri?>(null) }
            val altLauncher: ManagedActivityResultLauncher<Array<String>, Uri?> = rememberLauncherForActivityResult(
                contract = object : ActivityResultContracts.OpenDocument() {
                    override fun createIntent(context: Context, input: Array<String>): Intent {
                        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            super.createIntent(context, input)
                                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, anchorUri.value)
                        } else {
                            super.createIntent(context, input)
                        }
                    }
                }
            ) { uri ->
                uri?.let {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION // or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    ddcContent = getContentFromExternalImplicitIntent(uri, anchorUri)
                }
            }
            val recoveryLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult> =
                rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    it.data?.let { intent ->
                        ddcContent = intent.data?.let { uri -> getContentFromExternalImplicitIntent(uri, anchorUri, altLauncher) }
                    }
                }
            LaunchedEffect(Unit) {
                if (intent.action in setOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT))
                    ddcContent = intent.data?.let { getContentFromExternalImplicitIntent(it, anchorUri, altLauncher, recoveryLauncher) }
            }

            App(ddcContent = ddcContent)
        }
    }

    /** check AndroidManifest for inputs */
    private fun getContentFromExternalImplicitIntent(
        uri: Uri,
        anchorUri: MutableState<Uri?>,
        altLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>? = null,
        recoveryLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>? = null
    ): String? {
        anchorUri.value = uri
        var content: String? = null
        println("incoming Uri.path: ${uri.path}")
        if (setOf(".ddc", ".yaml", ".yml")
            .any { uri.path?.endsWith(it, ignoreCase = true) == true } || uri.path?.contains("encoded") == true
        ) {
            try {
                content = readDdcFromUri(applicationContext, uri)
            } catch (e: SecurityException) {
                // Q = API 29 = Android 10
                if (recoveryLauncher != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // TODO: test this
                    val recoverableSecurityException =
                        e as? RecoverableSecurityException ?: throw RuntimeException(e.message, e)
                    val intentSender =
                        recoverableSecurityException.userAction.actionIntent.intentSender
                    recoveryLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                } else {
                    e.printStackTrace()
                    altLauncher?.launch(arrayOf("application/*"))
                }
            } catch (e: FileNotFoundException) {
                // BUG: breaks when trying to open a .ddc/.yaml via Dodeclusters from
                //  Total Commander (permission denied)
                e.printStackTrace()
                altLauncher?.launch(arrayOf("application/*"))
            }
        } else {
            altLauncher?.launch(arrayOf("application/*"))
        }
        return content
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}