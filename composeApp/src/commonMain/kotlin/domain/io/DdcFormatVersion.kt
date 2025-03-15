package domain.io

import androidx.compose.runtime.Immutable
import domain.cluster.ClusterV1
import domain.tryCatch2
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Immutable
enum class DdcFormatVersion {
    CLUSTER_V1_JSON,
    YAML_V1,
    YAML_V2,
    YAML_V3,
    YAML_V4,
}

val permissiveJsonDdcSerializingSettings = Json {
    isLenient = true
    ignoreUnknownKeys = true // enables backward compatibility to a certain level
}

/**
 * Attempts to parse Ddc formats, newest-to-oldest
 */
inline fun tryParseDdc(
    content: String,
    crossinline onDdc4: (DdcV4) -> Unit,
    crossinline onDdc3: (DdcV3) -> Unit,
    crossinline onDdc2: (DdcV2) -> Unit,
    crossinline onDdc1: (DdcV1) -> Unit,
    crossinline onClusterV1: (ClusterV1) -> Unit,
    crossinline onFail: () -> Unit,
) {
    // this looks ugly
    tryCatch2<SerializationException, IllegalArgumentException>(
        {
            val ddc4 = parseDdcV4(content)
            onDdc4(ddc4)
        },
        { e ->
            println("Failed to parse DdcV4->yaml, falling back to DdcV3->yaml")
            e.printStackTrace()
            tryCatch2<SerializationException, IllegalArgumentException>(
                {
                    val ddc3 = parseDdcV3(content)
                    onDdc3(ddc3)
                },
                { e ->
                    println("Failed to parse DdcV3->yaml, falling back to DdcV2->yaml")
                    e.printStackTrace()
                    tryCatch2<SerializationException, IllegalArgumentException>(
                        {
                            val ddc2 = parseDdcV2(content)
                            onDdc2(ddc2)
                        },
                        { e ->
                            println("Failed to parse DdcV2->yaml, falling back to DdcV1->yaml")
                            e.printStackTrace()
                            tryCatch2<SerializationException, IllegalArgumentException>(
                                {
                                    val ddc1 = parseDdcV1(content)
                                    onDdc1(ddc1)
                                },
                                { e ->
                                    println("Failed to parse DdcV1->yaml, falling back to ClusterV1->json")
                                    e.printStackTrace()
                                    tryCatch2<SerializationException, IllegalArgumentException>(
                                        {
                                            val cluster: ClusterV1 =
                                                permissiveJsonDdcSerializingSettings.decodeFromString(ClusterV1.serializer(), content)
                                            onClusterV1(cluster)
                                        },
                                        { e ->
                                            println("Failed to parse ClusterV1->json")
                                            e.printStackTrace()
                                            onFail()
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    )
}