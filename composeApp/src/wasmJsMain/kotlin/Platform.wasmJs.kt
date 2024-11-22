import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ui.edit_cluster.EditClusterViewModel
import kotlin.math.pow

object WasmPlatform: Platform {
    val underlyingPlatform: UnderlyingPlatform = detectUnderlyingPlatform()
    override val name: String = "Web with Kotlin/Wasm under $underlyingPlatform"
    override val kind: PlatformKind = PlatformKind.WEB
    override val fileSeparator: Char =
        if (underlyingPlatform == UnderlyingPlatform.WINDOWS)
            '\\'
        else '/'
    override val tapRadius: Float = 10f
    override val maxCircleRadius: Float = 1e5f
    override val lastStateStore: KStore<EditClusterViewModel.State> by lazy {
        storeOf(key = Platform.LAST_STATE_STORE_FILE_NAME)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun saveLastState(state: EditClusterViewModel.State) {
        GlobalScope.launch(Dispatchers.Default) { // MAYBE: another dispatcher is better
            lastStateStore.set(state)
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

private fun getAppVersion(): String =
    js("navigator.appVersion")

// reference: https://stackoverflow.com/a/35246221/7143065
private fun detectUnderlyingPlatform(): UnderlyingPlatform {
    val appVersion = getAppVersion()
    return when {
        appVersion.contains("Win", ignoreCase = true) ->
            UnderlyingPlatform.WINDOWS
        appVersion.contains("Mac", ignoreCase = true) ->
            UnderlyingPlatform.MAC
        else -> UnderlyingPlatform.LINUX
    }
}
