package com.pierbezuhoff.dodeclusters

import App
import android.app.Activity
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import domain.io.readDdcFromUri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import setFilesDir
import ui.LifecycleEvent
import ui.theme.DodeclustersColors
import java.io.FileNotFoundException

class MainActivity : ComponentActivity() {
    private val lifecycleEvents: MutableSharedFlow<LifecycleEvent> =
        MutableSharedFlow(replay = 1)
    private val ddcFlow: MutableStateFlow<String?> = MutableStateFlow(null)
    private val anchorUriFlow: MutableStateFlow<Uri?> = MutableStateFlow(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!filesDir.exists())
            filesDir.createNewFile()
        setFilesDir(filesDir)
        // setup for full-screen, immersive mode
        // reference: https://developer.android.com/develop/ui/views/layout/immersive
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
            if (windowInsets.isVisible(WindowInsetsCompat.Type.navigationBars())
                || windowInsets.isVisible(WindowInsetsCompat.Type.statusBars())) {
//                println("some insets are visible -> hide them")
                if (true) { // when requested programmatically (ideally)
                    // Hide both the status bar and the navigation bar.
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                }
            } else {
//                println("no insets are visible")
                if (false) { // when requested programmatically
                    // Show both the status bar and the navigation bar.
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            ViewCompat.onApplyWindowInsets(view, windowInsets)
        }

        setContent {
            // this horrendous mess looks smart but is actually very stupid
//            var ddcContent by remember { mutableStateOf<String?>(null) }
            val ddcContent by ddcFlow.collectAsStateWithLifecycle()
            val anchorUri by anchorUriFlow.collectAsStateWithLifecycle()
            val altLauncher: ManagedActivityResultLauncher<Array<String>, Uri?> = rememberLauncherForActivityResult(
                contract = object : ActivityResultContracts.OpenDocument() {
                    override fun createIntent(context: Context, input: Array<String>): Intent {
                        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            super.createIntent(context, input)
                                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, anchorUri)
                        } else {
                            super.createIntent(context, input)
                        }
                    }
                }
            ) { uri ->
                uri?.let {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION // or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    val newDdcContent = getContentFromExternalImplicitIntent(uri)
                    if (newDdcContent != null)
                        ddcFlow.update { newDdcContent }
                }
            }
            val recoveryLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult> =
                rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    it.data?.let { intent ->
                        intent.data?.let { uri ->
                            val newDdcContent = getContentFromExternalImplicitIntent(uri, altLauncher)
                            if (newDdcContent != null)
                                ddcFlow.update { newDdcContent }
                        }
                    }
                }
            LaunchedEffect(Unit) {
                if (intent.action in setOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT))
                    intent.data?.let {
                        val newDdcContent = getContentFromExternalImplicitIntent(it, altLauncher, recoveryLauncher)
                        if (newDdcContent != null)
                            ddcFlow.update { newDdcContent }
                    }
            }
            val view = LocalView.current
            val isDarkTheme = isSystemInDarkTheme()
            val scheme =
                if (isDarkTheme) DodeclustersColors.darkScheme
                else DodeclustersColors.lightScheme
            val statusBarColor = scheme.primary.toArgb()
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    window.statusBarColor = statusBarColor
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isDarkTheme
                }
            }
            App(
                ddcContent = ddcContent,
                lifecycleEvents = lifecycleEvents,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // MAYBE: it's better to use suspend emit
        lifecycleEvents.tryEmit(LifecycleEvent.SaveUIState)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            val newDdcContent = getContentFromExternalImplicitIntent(uri, null)
            if (newDdcContent != null)
                ddcFlow.update { newDdcContent }
        }
    }

    /** check AndroidManifest for inputs */
    private fun getContentFromExternalImplicitIntent(
        uri: Uri,
        altLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>? = null,
        recoveryLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>? = null
    ): String? {
        anchorUriFlow.update { uri }
        var content: String? = null
        println("incoming Uri.path: ${uri.path} <- $uri")
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