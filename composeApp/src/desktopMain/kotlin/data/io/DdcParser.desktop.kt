package data.io

import com.charleskorn.kaml.Yaml

actual fun parseDdc(content: String): Ddc =
    Yaml.default.decodeFromString(Ddc.serializer(), content)