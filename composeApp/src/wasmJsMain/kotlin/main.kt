@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.fetching_shared_error
import dodeclusters.composeapp.generated.resources.fetching_shared_progress
import dodeclusters.composeapp.generated.resources.loading_sample_error
import dodeclusters.composeapp.generated.resources.loading_sample_progress
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
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
            initialValue = null,
            key1 = sharedId,
        ) {
            if (sharedId != null) {
                value = LoadingState.InProgress(
                    getString(Res.string.fetching_shared_progress, sharedId)
                )
                val ddcContentAndOwned = WebDdcSharing.fetchSharedDdc(sharedId)
                println("finished fetching shared ddc @$sharedId, owned=${ddcContentAndOwned?.second}")
                value = if (ddcContentAndOwned == null) {
                    LoadingState.Error(Error(getString(Res.string.fetching_shared_error, sharedId)))
                } else {
                    val (ddcContent, owned) = ddcContentAndOwned
                    WebDdcSharing.shared = Pair(sharedId, owned)
                    LoadingState.Completed(ddcContent)
                }
            }
        }
        val sampleDdcContent: LoadingState<String>? by produceState<LoadingState<String>?>(
            initialValue = null,
            key1 = sampleName,
        ) {
            if (sampleName != null) {
                value = LoadingState.InProgress(
                    getString(Res.string.loading_sample_progress, sampleName)
                )
                val ddcContent = DdcRepository.loadSampleClusterYaml(sampleName)
                println("finished loading sample ddc $sampleName")
                value = if (ddcContent == null)
                    LoadingState.Error(Error(getString(Res.string.loading_sample_error, sampleName)))
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
                // clean url too assert dominance or smth
                val newUrl = URL(window.location.href)
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