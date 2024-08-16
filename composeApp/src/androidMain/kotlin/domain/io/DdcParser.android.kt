package domain.io

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Line
import domain.io.Ddc
import domain.io.OldDdc
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

actual fun parseDdc(content: String): Ddc {
    val module = SerializersModule { // shoudnt be necessary cuz it's closed/sealed polymorphism
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
    val config = YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
    return Yaml(module, config).decodeFromString(Ddc.serializer(), content)
}
actual fun parseOldDdc(content: String): OldDdc {
    val module = SerializersModule { // shoudnt be necessary cuz it's closed/sealed polymorphism
        polymorphic(OldDdc.Token::class) {
            subclass(OldDdc.Token.Cluster::class)
            subclass(OldDdc.Token.Circle::class)
        }
    }
    val config = YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
    return Yaml(module, config).decodeFromString(OldDdc.serializer(), content)
}
