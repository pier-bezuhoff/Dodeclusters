package com.pierbezuhoff.dodeclusters

import App
import android.app.RecoverableSecurityException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
            var ddcContent by remember { mutableStateOf<String?>(null) }
            val recoveryLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult> =
                rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    it.data?.let { intent ->
                        getContentFromExternalImplicitIntent(intent)
                    }
                }
            if (intent.action in setOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT))
                ddcContent = getContentFromExternalImplicitIntent(intent, recoveryLauncher)
            App(ddcContent = ddcContent)
        }
    }

    /** check AndroidManifest for inputs */
    private fun getContentFromExternalImplicitIntent(
        intent: Intent,
        recoveryLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>? = null
    ): String? {
        var content: String? = null
        intent.data?.let { uri ->
            println("incoming Uri.path: ${uri.path}")
            if (setOf(".ddc", ".yaml", ".yml")
                    .any { uri.path?.endsWith(it, ignoreCase = true) == true }
            ) {
                try {
                    // BUG: fails to actually open Google Drive intent (silently), with uri.path = "/enc=encoded=..."
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
//                            startIntentSenderForResult(
//                                intentSender, 42,
//                                null, 0, 0, 0, null
//                            )
                    } else
                        e.printStackTrace()
                } catch (e: FileNotFoundException) {
                    // BUG: breaks when trying to open a .ddc/.yaml via Dodeclusters from
                    //  Total Commander (permission denied)
                    e.printStackTrace()
                }
            }
        }
        return content
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}