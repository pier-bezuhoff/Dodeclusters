package com.pierbezuhoff.dodeclusters

import App
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import data.io.readDdcFromUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App(ddcContent = catchExternalImplicitIntents())
        }
    }

    /** check AndroidManifest for inputs */
    private fun catchExternalImplicitIntents(): String? {
        var content: String? = null
        if (intent.action in setOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) {
            intent.data?.let { uri ->
                println("incoming Uri.path: ${uri.path}")
                if (setOf(".ddc", ".yaml", ".yml")
                        .any { uri.path?.endsWith(it, ignoreCase = true) == true }
                ) {
//                    try {
                        // BUG: breaks when trying to open it from Total Commander (permission denied)
                        //  fails to actually open Google Drive intent (silently)
                        content = readDdcFromUri(applicationContext, uri)
//                    } catch (e: Error) {
//                        e.printStackTrace()
//                    } catch (e: SecurityException) {
//                        e.printStackTrace()
//                    }
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