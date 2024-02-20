package data.io

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

actual fun parseDdc(content: String): Ddc {
//    Json.decodeFromString(Ddc.serializer(), content)
    val module = SerializersModule { // shoudnt be necessary cuz it's closed/sealed polymorphism
        polymorphic(Ddc.Token::class) {
            subclass(Ddc.Token.Cluster::class)
            subclass(Ddc.Token.Circle::class)
        }
    }
    val config = YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Tag
    )
    return Yaml(module, config).decodeFromString(Ddc.serializer(), content)
}