import kotlin.math.pow

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val tapRadius: Float = 10f
    override val maxCircleRadius: Float = 1e5f

    override fun scrollToZoom(yDelta: Float): Float {
        val percent = 2.5f
        val zoom = (1f + percent/100f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = JVMPlatform()