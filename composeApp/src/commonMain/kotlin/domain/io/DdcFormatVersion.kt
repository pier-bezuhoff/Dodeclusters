package domain.io

import Platform
import androidx.compose.runtime.Immutable
import domain.cluster.ClusterV1
import domain.recoverCatchingOnly
import domain.runCatchingOnly
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Immutable
enum class DdcFormatVersion {
    CLUSTER_V1_JSON,
    YAML_V1,
    YAML_V2,
    YAML_V3,
    YAML_V4,
    YAML_V5,
}

object DdcFormat {
    val permissiveJsonDdcSerializingSettings = Json {
        isLenient = true
        ignoreUnknownKeys = true // enables backward compatibility to a certain level
    }

    /**
     * Attempts to parse Ddc formats, newest-to-oldest
     *
     * NOTE: fails on unrecognized types
     */
    suspend inline fun tryParsingDdc(
        content: String,
        crossinline onDdc5: (DdcV5) -> Unit,
        crossinline onDdc4: (DdcV4) -> Unit,
        crossinline onDdc3: (DdcV3) -> Unit,
        crossinline onDdc2: (DdcV2) -> Unit,
        crossinline onDdc1: (DdcV1) -> Unit,
        crossinline onClusterV1: (ClusterV1) -> Unit,
        crossinline onFail: () -> Unit,
    ) {
        withContext(Platform.getCurrent().dispatcherIO) {
            runCatchingOnly({ it is IllegalArgumentException || it is SerializationException }) {
                val ddc5 = DdcParser.parseDdcV5(content)
                onDdc5(ddc5)
            }.recoverCatchingOnly({ it is IllegalArgumentException || it is SerializationException }) { e ->
                println("Failed to parse DdcV5->yaml, falling back to DdcV4->yaml")
                e.printStackTrace()
                val ddc4 = DdcParser.parseDdcV4(content)
                onDdc4(ddc4)
            }.recoverCatchingOnly({ it is IllegalArgumentException || it is SerializationException }) { e ->
                println("Failed to parse DdcV4->yaml, falling back to DdcV3->yaml")
                e.printStackTrace()
                val ddc3 = DdcParser.parseDdcV3(content)
                onDdc3(ddc3)
            }.recoverCatchingOnly({ it is IllegalArgumentException || it is SerializationException }) { e ->
                println("Failed to parse DdcV3->yaml, falling back to DdcV2->yaml")
                e.printStackTrace()
                val ddc2 = DdcParser.parseDdcV2(content)
                onDdc2(ddc2)
            }.recoverCatchingOnly({ it is IllegalArgumentException || it is SerializationException }) { e ->
                println("Failed to parse DdcV2->yaml, falling back to DdcV1->yaml")
                e.printStackTrace()
                val ddc1 = DdcParser.parseDdcV1(content)
                onDdc1(ddc1)
            }.recoverCatchingOnly({ it is IllegalArgumentException || it is SerializationException }) { e ->
                println("Failed to parse DdcV1->yaml, falling back to ClusterV1->json")
                e.printStackTrace()
                val cluster: ClusterV1 = permissiveJsonDdcSerializingSettings
                    .decodeFromString(ClusterV1.serializer(), content)
                onClusterV1(cluster)
            }.onFailure { e ->
                println("Failed to parse ClusterV1->json")
                e.printStackTrace()
                onFail()
            }
        }
    }
}
