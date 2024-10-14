import android.os.Build
import kotlin.math.pow

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val tapRadius: Float = 15f
    override val maxCircleRadius: Float = 10_000f

    // since this is triggered by mouse scroll, it is irrelevant to android
    override fun scrollToZoom(yDelta: Float): Float {
        val zoom = (1.01f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()