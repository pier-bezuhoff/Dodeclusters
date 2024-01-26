package data

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.resource

class ClusterRepository {

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadSampleClusterJson(
        sampleIndex: Int,
        onLoaded: (json: String?) -> Unit
    ) {
        if (sampleIndex in SAMPLE_PATHS.indices) {
            val r = resource(SAMPLE_PATHS[sampleIndex])
            val json = r.readBytes().decodeToString()
            onLoaded(json)
        } else {
            onLoaded(null)
        }
    }

    companion object {
        val SAMPLE_PATHS = listOf("samples/m-portrait.ddc")
    }
}