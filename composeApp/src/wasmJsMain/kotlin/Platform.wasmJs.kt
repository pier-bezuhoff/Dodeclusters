import kotlin.math.pow

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val tapRadius: Float = 10f

    override fun scrollToZoom(yDelta: Float): Float {
        val percent = 0.1f
        val zoom = (1f + percent/100f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = WasmPlatform()