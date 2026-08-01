@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.app_name
import dodeclusters.composeapp.generated.resources.fetching_shared_error
import dodeclusters.composeapp.generated.resources.fetching_shared_progress
import dodeclusters.composeapp.generated.resources.loading_sample_error
import dodeclusters.composeapp.generated.resources.loading_sample_progress
import domain.LoadingState
import domain.io.DdcContent
import domain.io.DdcRepository
import domain.io.WebDdcSharing
import domain.settings.Settings
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.get
import org.w3c.dom.url.URL
import ui.LifecycleEvent
import ui.editor.KeyboardAction
import ui.theme.ColorTheme

object SearchParamKeys {
    const val THEME = "theme"
    // MAYBE: use "url#id" instead
    const val SHARED_ID = "shared"
    const val SHARE_PERM = "share_perm"
    const val SAMPLE = "sample"
}

/** Local storage namespace is shared within the domain, so it's better
 * to prefix keys with 'ddc-' */
object LocalStorageKeys {
    const val USER_ID = "ddc-user-id"
    /** Presently unused */
    const val SHARE_PERMISSION = "ddc-share-perm"
}

// NOTE: because Github Pages serves .wasm files with wrong mime type https://stackoverflow.com/a/54320709/7143065
//  to open in mobile/firefox use netlify version
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // example:
    // https://pier-bezuhoff.github.io/Dodeclusters?theme=dark&sample=apollonius
    val url = URL(window.location.href)
    val colorTheme: ColorTheme? = when (url.searchParams.get(SearchParamKeys.THEME)?.lowercase()) {
        "light" -> ColorTheme.LIGHT
        "dark" -> ColorTheme.DARK
        "auto" -> ColorTheme.AUTO
        else -> null
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
    val titleFlow: MutableStateFlow<String> = MutableStateFlow(
        "Dodeclusters" // &#1421; = ֍
    )
    coroutineScope.launch {
        titleFlow.collect { newTitle ->
            document.title = newTitle
        }
    }
    if (colorTheme != null) {
        coroutineScope.launch {
            WasmPlatform.settingsStore.update {
                (it ?: Settings()).copy(
                    colorTheme = colorTheme
                )
            }
        }
    } else { // init settingsStore.cache
        coroutineScope.launch(Dispatchers.Main.immediate) {
            WasmPlatform.settingsStore.get()
        }
    }
    val keyboardActions: MutableSharedFlow<KeyboardAction> = MutableSharedFlow()
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
        val sharedDdcFlow: StateFlow<LoadingState<DdcContent>?> = remember {
            flow {
                if (sharedId != null) {
                    val inProgressState = LoadingState.InProgress(
                        getString(Res.string.fetching_shared_progress, sharedId)
                    )
                    emit(inProgressState)
                    val ddcContentAndOwned = WebDdcSharing.fetchSharedDdc(sharedId).getOrNull()
                    println("finished fetching shared ddc @$sharedId, owned=${ddcContentAndOwned?.second}")
                    val endState = if (ddcContentAndOwned == null) {
                        LoadingState.Error(Error(
                            getString(Res.string.fetching_shared_error, sharedId)
                        ))
                    } else {
                        val (ddcContent, owned) = ddcContentAndOwned
                        WebDdcSharing.shared = Pair(sharedId, owned)
                        LoadingState.Completed(ddcContent)
                    }
                    emit(endState)
                } else {
                    emit(null)
                }
            }.stateIn(coroutineScope, SharingStarted.Eagerly, null)
        }
        val sampleDdcFlow: StateFlow<LoadingState<String>?> = remember {
            flow {
                if (sampleName != null) {
                    val inProgressState = LoadingState.InProgress(
                        getString(Res.string.loading_sample_progress, sampleName)
                    )
                    emit(inProgressState)
                    val ddcContent = DdcRepository.loadSampleClusterYaml(sampleName)
                    println("finished loading sample ddc $sampleName")
                    val endState = if (ddcContent == null)
                        LoadingState.Error(Error(getString(Res.string.loading_sample_error, sampleName)))
                    else
                        LoadingState.Completed(ddcContent)
                    emit(endState)
                } else {
                    emit(null)
                }
            }.stateIn(coroutineScope, SharingStarted.Eagerly, null)
        }
        val ddcFlow: StateFlow<LoadingState<String>?> = sharedDdcFlow.combine(sampleDdcFlow) { sharedDdc, sampleDdc ->
            sharedDdc ?: sampleDdc
        }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5_000), null)
        // idk if to make it flow
        val weHaveSharePerm: Boolean by produceState(
            WebDdcSharing.testSharePermission(),
            key1 = sharePerm,
        ) {
            if (sharePerm != null) { // unused
                localStorage.setItem(LocalStorageKeys.SHARE_PERMISSION, sharePerm)
                // clean url too assert dominance or smth
                val newUrl = URL(window.location.href)
                newUrl.searchParams.delete(SearchParamKeys.SHARE_PERM)
                window.history.pushState(null, "", newUrl.href)
            }
            val oldUserId = localStorage.getItem(LocalStorageKeys.USER_ID)
            if (oldUserId == null) {
                WebDdcSharing.registerUser()
                    .onSuccess { newUserId ->
                        localStorage.setItem(LocalStorageKeys.USER_ID, newUserId)
                        println("acquired share perm for $newUserId")
                        value = true
                    }
                    .onFailure {
                        println(it.message)
                    }
            } else {
                value = true
            }
        }
        App(
            titleFlow = titleFlow,
            ddcFlow = ddcFlow,
            keyboardActions = keyboardActions,
            lifecycleEvents = lifecycleEvents,
            ddcSharing = if (weHaveSharePerm) WebDdcSharing else null,
        )
        LaunchedEffect(titleFlow) {
            // we set localized title here
            val title = getString(Res.string.app_name)
            titleFlow.update { title }
        }
    }
}