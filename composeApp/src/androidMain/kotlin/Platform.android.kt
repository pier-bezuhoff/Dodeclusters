import android.os.Build
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.io.files.Path
import ui.edit_cluster.EditClusterViewModel
import java.io.File
import kotlin.math.pow

object AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val fileSeparator: Char = '/'
    override val tapRadius: Float = 15f
    override val maxCircleRadius: Float = 7_000f
    lateinit var filesDir: Path
    override val lastStateStore: KStore<EditClusterViewModel.State> by lazy {
        storeOf(file = Path(filesDir, Platform.LAST_STATE_STORE_FILE_NAME + ".json"))
    }

    // reference: https://stackoverflow.com/a/75734381/7143065
    @OptIn(DelicateCoroutinesApi::class)
    override fun saveLastState(state: EditClusterViewModel.State) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                lastStateStore.set(state)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // since this is triggered by mouse scroll, it is irrelevant to android
    override fun scrollToZoom(yDelta: Float): Float {
        val zoom = (1.01f).pow(-yDelta)
        return zoom
    }
}

actual fun getPlatform(): Platform = AndroidPlatform

fun setFilesDir(filesDir: File) {
    AndroidPlatform.filesDir = Path(filesDir.absolutePath)
}