import kotlin.math.pow

class WasmPlatform: Platform {
    val underlyingPlatform: UnderlyingPlatform = detectUnderlyingPlatform()
    override val name: String = "Web with Kotlin/Wasm under $underlyingPlatform"
    override val fileSeparator: Char =
        if (underlyingPlatform == UnderlyingPlatform.WINDOWS)
            '\\'
        else '/'
    override val tapRadius: Float = 10f
    override val maxCircleRadius: Float = 1e5f

    override fun scrollToZoom(yDelta: Float): Float {
        val percent = 0.1f
        val zoom = (1f + percent/100f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = WasmPlatform()

/** The platform on which the browser is being run */
enum class UnderlyingPlatform {
    LINUX,
    WINDOWS,
    MAC,
}

fun getAppVersion(): String =
    js("navigator.appVersion")

// reference: https://stackoverflow.com/a/35246221/7143065
fun detectUnderlyingPlatform(): UnderlyingPlatform {
    val appVersion = getAppVersion()
    return when {
        appVersion.contains("Win", ignoreCase = true) ->
            UnderlyingPlatform.WINDOWS
        appVersion.contains("Mac", ignoreCase = true) ->
            UnderlyingPlatform.MAC
        else -> UnderlyingPlatform.LINUX
    }
}
