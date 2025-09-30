@file:OptIn(ExperimentalWasmJsInterop::class)

import domain.settings.Settings
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ui.editor.EditorViewModel
import kotlin.math.pow

object WasmPlatform: Platform {
    val underlyingPlatform: UnderlyingPlatform = detectUnderlyingPlatform()
    override val name: String = "Web with Kotlin/Wasm under $underlyingPlatform"
    override val kind: PlatformKind = PlatformKind.WEB
    override val fileSeparator: Char =
        if (underlyingPlatform == UnderlyingPlatform.WINDOWS)
            '\\' // iirc Windows generally supports forward slash nowadays
        else '/'
    override val tapRadius: Float = 10f
    override val minCircleToCubicApproximationRadius: Float = 10_000f
    override val minCircleToLineApproximationRadius: Float = 100_000f
    override val lastStateStore: KStore<EditorViewModel.State> by lazy {
        storeOf(
            key = Platform.LAST_STATE_STORE_FILE_NAME,
            format = EditorViewModel.State.JSON_FORMAT,
        )
    }
    override val settingsStore: KStore<Settings> by lazy {
        storeOf(
            key = Platform.SETTINGS_STORE_FILE_NAME,
            format = Settings.JSON_FORMAT,
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveLastState(state: EditorViewModel.State) {
        GlobalScope.launch(Dispatchers.Default) { // MAYBE: another dispatcher is better
            lastStateStore.set(state)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveSettings(settings: Settings) {
        GlobalScope.launch(Dispatchers.Default) {
            settingsStore.set(settings)
        }
    }

    override fun scrollToZoom(yDelta: Float): Float {
        val percent = 0.1f
        val zoom = (1f + percent/100f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = WasmPlatform

/** The platform on which the browser is being run */
enum class UnderlyingPlatform {
    LINUX,
    WINDOWS,
    MAC,
}

// reference: https://stackoverflow.com/a/35246221/7143065
private fun detectUnderlyingPlatform(): UnderlyingPlatform {
    val appVersion = window.navigator.appVersion
    return when {
        appVersion.contains("Win", ignoreCase = true) ->
            UnderlyingPlatform.WINDOWS
        appVersion.contains("Mac", ignoreCase = true) ->
            UnderlyingPlatform.MAC
        else ->
            UnderlyingPlatform.LINUX
    }
}
