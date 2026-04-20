package domain.io

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerializationException

object DdcParser {
    val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = true,
            strictMode = false,
            polymorphismStyle = PolymorphismStyle.Property
        )
    )

    @Throws(SerializationException::class, IllegalArgumentException::class)
    fun parseDdcV5(content: String): DdcV5 =
        DdcV5.YAML.decodeFromString(DdcV5.serializer(), content)

    @Throws(SerializationException::class, IllegalArgumentException::class)
    fun parseDdcV4(content: String): DdcV4 =
        yaml.decodeFromString(DdcV4.serializer(), content)

    @Throws(SerializationException::class, IllegalArgumentException::class)
    fun parseDdcV3(content: String): DdcV3 =
        yaml.decodeFromString(DdcV3.serializer(), content)

    @Throws(SerializationException::class, IllegalArgumentException::class)
    fun parseDdcV2(content: String): DdcV2 =
        yaml.decodeFromString(DdcV2.serializer(), content)

    @Throws(SerializationException::class, IllegalArgumentException::class)
    fun parseDdcV1(content: String): DdcV1 =
        yaml.decodeFromString(DdcV1.serializer(), content)
}
