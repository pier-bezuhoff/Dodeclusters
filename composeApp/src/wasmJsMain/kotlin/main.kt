@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import domain.LoadingState
import domain.io.DdcRepository
import domain.io.SHARE_PERMISSION_KEY
import domain.io.USER_ID_KEY
import domain.io.WebDdcSharing
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.get
import org.w3c.dom.url.URL
import ui.LifecycleEvent
import ui.editor.KeyboardAction
import ui.theme.ColorTheme
import ui.theme.DEFAULT_COLOR_THEME

object SearchParamKeys {
    const val THEME = "theme"
    // MAYBE: use "url#id" instead
    const val SHARED_ID = "shared"
    const val SHARE_PERM = "share_perm"
    const val SAMPLE = "sample"
}

// NOTE: because Github Pages serves .wasm files with wrong mime type https://stackoverflow.com/a/54320709/7143065
//  to open in mobile/firefox use netlify version
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // example:
    // https://pier-bezuhoff.github.io/Dodeclusters?theme=dark&sample=apollonius
    val url = URL(window.location.href)
    val colorTheme: ColorTheme = when (url.searchParams.get(SearchParamKeys.THEME)?.lowercase()) {
        "light" -> ColorTheme.LIGHT
        "dark" -> ColorTheme.DARK
        "auto" -> ColorTheme.AUTO
        else -> DEFAULT_COLOR_THEME
    }
    val sharePerm: String? = url.searchParams.get(SearchParamKeys.SHARE_PERM)
    val sharedId: String? = url.searchParams.get(SearchParamKeys.SHARED_ID)
    val sampleName: String? = url.searchParams.get(SearchParamKeys.SAMPLE)
    val lifecycleEvents: MutableSharedFlow<LifecycleEvent> = MutableSharedFlow(replay = 1)
    document.addEventListener("visibilitychange") {
        if (document["hidden"] == true.toJsBoolean()) {
            lifecycleEvents.tryEmit(LifecycleEvent.SaveUIState)
        }
    }
    val coroutineScope = CoroutineScope(Dispatchers.Default)
    val themeFlow = MutableStateFlow<ColorTheme>(colorTheme)
    val keyboardActions: MutableSharedFlow<KeyboardAction> = MutableSharedFlow(replay = 1)
    document.addEventListener("keydown") { event: Event ->
        (event as? KeyboardEvent)?.let { keyboardEvent ->
            val action = WebKeyboardActionMapping.event2action(keyboardEvent)
            if (action != null) {
                event.stopPropagation()
                coroutineScope.launch {
                    keyboardActions.emit(action)
                }
            }
        }
    }
    // cleanup plain text left as a placeholder/for search engines
    val loadingSpinner = document.getElementById("loading")
    loadingSpinner?.setAttribute("style", "display: none;")
    loadingSpinner?.remove()
    document.querySelector("h2")?.setAttribute("style", "display: none;")
    document.querySelector("h1")?.setAttribute("style", "display: none;")
    ComposeViewport(
        viewportContainerId = "compose-root",
        configure = {
            isA11YEnabled = false // for performance
        }
    ) {
        val sharedDdcContent: LoadingState<String>? by produceState<LoadingState<String>?>(
            if (sharedId == null) null
            else LoadingState.InProgress("Loading shared cluster '$sharedId'..."),
            key1 = sharedId,
        ) {
            if (sharedId != null) {
                val ddcContentAndOwned = WebDdcSharing.fetchSharedDdc(sharedId)
                println("fetched shared ddc $sharedId")
                value = if (ddcContentAndOwned == null)
                    LoadingState.Error(Error("Fetching shared resource '$sharedId' failed"))
                else
                    LoadingState.Completed(ddcContentAndOwned.first)
            }
        }
        val sampleDdcContent: LoadingState<String>? by produceState<LoadingState<String>?>(
            if (sampleName == null) null
            else LoadingState.InProgress("Loading sample cluster '$sampleName'..."),
            key1 = sampleName,
        ) {
            if (sampleName != null) {
                val ddcContent = DdcRepository.loadSampleClusterYaml(sampleName)
                println("loaded sample ddc $sampleName")
                value = if (ddcContent == null || true)
                    LoadingState.Error(Error("No sample '$sampleName' found"))
                else
                    LoadingState.Completed(ddcContent)
            }
        }
        val weHaveSharePerm: Boolean by produceState(
            WebDdcSharing.testSharePermission(),
            key1 = sharePerm,
        ) {
            if (sharePerm != null) {
                localStorage.setItem(SHARE_PERMISSION_KEY, sharePerm)
                val oldUserId = localStorage.getItem(USER_ID_KEY)
                if (oldUserId == null) {
                    // NOTE: the server doesn't verify share-perm validity atp
                    val newUserId = WebDdcSharing.registerUser()
                    if (newUserId != null) {
                        localStorage.setItem(USER_ID_KEY, newUserId)
                        println("acquired share perm for $newUserId")
                        // ideally we display it as a snackbar notice
                        value = true
                    }
                } else {
                    value = true
                }
                val newUrl = URL(window.location.href)
                // clean url too assert dominance or smth
                newUrl.searchParams.delete(SearchParamKeys.SHARE_PERM)
                window.history.pushState(null, "", newUrl.href)
            }
        }
        App(
            ddcContent = sharedDdcContent ?: sampleDdcContent,
            themeFlow = themeFlow,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
            ddcSharing = if (weHaveSharePerm) WebDdcSharing else null,
        )
    }
}