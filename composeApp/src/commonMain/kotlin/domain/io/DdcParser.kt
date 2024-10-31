package domain.io

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Line
import kotlinx.serialization.SerializationException
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private val ddcSerializationModule = SerializersModule { // shoudnt be necessary cuz it's closed/sealed polymorphism
    polymorphic(Ddc.Token::class) {
        subclass(Ddc.Token.Cluster::class)
        subclass(Ddc.Token.Circle::class)
        subclass(Ddc.Token.Line::class)
    }
    polymorphic(CircleOrLine::class) {
        subclass(Circle::class)
        subclass(Line::class)
    }
}

@Throws(SerializationException::class, IllegalArgumentException::class)
fun parseDdc(content: String): Ddc {
    val config = YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
    return Yaml(ddcSerializationModule, config).decodeFromString(Ddc.serializer(), content)
}

private val ddcV1SerializationModule = SerializersModule {
    polymorphic(DdcV1.Token::class) {
        subclass(DdcV1.Token.Cluster::class)
        subclass(DdcV1.Token.Circle::class)
    }
}

@Throws(SerializationException::class, IllegalArgumentException::class)
fun parseOldDdc(content: String): DdcV1 {
    val config = YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
    return Yaml(ddcV1SerializationModule, config)
        .decodeFromString(DdcV1.serializer(), content)
}
