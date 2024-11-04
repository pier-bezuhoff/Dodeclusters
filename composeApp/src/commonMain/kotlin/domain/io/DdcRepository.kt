package domain.io

import dodeclusters.composeapp.generated.resources.Res
import getPlatform
import org.jetbrains.compose.resources.ExperimentalResourceApi

class DdcRepository {

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadSampleClusterYaml(
        sampleName: String,
    ): String? {
        val fileSeparator = getPlatform().fileSeparator
        val samplePath = sampleName2Path(sampleName)
        return if (samplePath != null) {
            val path = "files" + fileSeparator + samplePath.path
            println("loading sample @ $path")
            val r =  Res.readBytes(path)
            val content = r.decodeToString()
            content
        } else {
            null
        }
    }

    private fun sampleName2Path(sampleName: String): SamplePath? {
        val s = sampleName.trim(' ').lowercase()
        return when {
            s == "m" || s.toIntOrNull() == 0 -> SamplePath.M
            s == "apollonius" || s.toIntOrNull() == 1 -> SamplePath.APOLLONIUS
            else -> null
        }
    }
}

enum class SamplePath(val path: String) {
    M("m-portrait.yml"),
    APOLLONIUS("apollonius.yml"),
}