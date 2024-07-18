package data

import dodeclusters.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

class ClusterRepository {

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadSampleClusterJson(
        sampleIndex: Int,
        onLoaded: (content: String?) -> Unit
    ) {
        if (sampleIndex in SAMPLE_PATHS.indices) {
            val r =  Res.readBytes(SAMPLE_PATHS[sampleIndex])
            val content = r.decodeToString()
            onLoaded(content)
        } else {
            onLoaded(null)
        }
    }

    companion object {
        val SAMPLE_PATHS = listOf("files/m-portrait.yml")
    }
}