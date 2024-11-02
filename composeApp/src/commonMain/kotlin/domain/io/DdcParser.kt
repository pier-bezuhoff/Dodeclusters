package domain.io

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerializationException

val Yaml4DdcV3 = Yaml(
    configuration = YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
)

val Yaml4DdcV2 = Yaml(
    configuration = YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
)

val Yaml4DdcV1 = Yaml(
    configuration = YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
)

// TODO: coroutines
@Throws(SerializationException::class, IllegalArgumentException::class)
fun parseDdcV3(content: String): DdcV3 =
    Yaml4DdcV3.decodeFromString(DdcV3.serializer(), content)

@Throws(SerializationException::class, IllegalArgumentException::class)
fun parseDdcV2(content: String): DdcV2 =
    Yaml4DdcV2.decodeFromString(DdcV2.serializer(), content)

@Throws(SerializationException::class, IllegalArgumentException::class)
fun parseDdcV1(content: String): DdcV1 =
    Yaml4DdcV1.decodeFromString(DdcV1.serializer(), content)
